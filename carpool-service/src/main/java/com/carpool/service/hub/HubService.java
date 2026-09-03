package com.carpool.service.hub;

import com.carpool.common.exception.HubNotFoundException;
import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.Hub;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.HubStatus;
import com.carpool.repository.HubAliasRepository;
import com.carpool.repository.HubRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.SuggestHubRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubService {

    private final HubRepository  hubRepository;
    private final UserRepository userRepository;
    private final HubAliasRepository  hubAliasRepository;
    private final EntityMapper   mapper;

    /**
     * Once the PENDING queue reaches this size, every pending hub is
     * auto-approved, skipping manual review.
     */
    private static final int AUTO_APPROVE_QUEUE_SIZE = 10;

    /**
     * All active hubs — cached 60 minutes. This is the primary hub list
     * shown to drivers when creating a ride.
     */
    @Cacheable(value = "hubs", key = "'all-active'")
    @Transactional(readOnly = true)
    public List<HubResponse> getAllActiveHubs() {
        return hubRepository.findByStatusOrderByAreaAscNameAsc(HubStatus.ACTIVE)
                .stream()
                .map(mapper::toHubResponse)
                .toList();
    }

    /**
     * Autocomplete search — cached per keyword, 5 minutes.
     */
    @Cacheable(value = "hub-search", key = "#keyword.toLowerCase()")
    @Transactional(readOnly = true)
    public List<HubResponse> searchHubs(String keyword) {
        return hubRepository.searchActive(keyword)
                .stream()
                .map(mapper::toHubResponse)
                .toList();
    }

    /**
     * Driver suggests a new hub not yet in the system.
     * Hub is saved as PENDING and immediately returned to the driver
     * so they can use it on their current ride creation.
     * Admin approves it separately via admin API — unless this suggestion
     * pushes the PENDING queue to {@link #AUTO_APPROVE_QUEUE_SIZE}, in which
     * case every pending hub (this one included) is auto-approved here.
     */
    @Caching(evict = {
            @CacheEvict(value = "hubs",       allEntries = true, condition = "#result.status().name() == 'ACTIVE'"),
            @CacheEvict(value = "hub-search", allEntries = true, condition = "#result.status().name() == 'ACTIVE'")
    })
    @Transactional
    public HubResponse suggestHub(SuggestHubRequest request, Long requestingUserId) {
        Optional<Hub> existing = hubRepository.findFirstByNameIgnoreCaseAndArea(request.name(), request.area());

        Hub hub;
        if (existing.isEmpty()) {
            hub = createPendingHub(request, requestingUserId);
        } else {
            hub = existing.get();
            if (hub.getStatus() == HubStatus.ACTIVE) {
                return mapper.toHubResponse(hub);
            }
            if (hub.getStatus() == HubStatus.REJECTED) {
                hub.setStatus(HubStatus.PENDING);
                hub = hubRepository.save(hub);
                log.info("Re-suggesting rejected hub: id={} name={}", hub.getId(), hub.getName());
            }
            // Already PENDING — nothing to change, dedupe returns it as-is.
        }

        if (hubRepository.countByStatus(HubStatus.PENDING) >= AUTO_APPROVE_QUEUE_SIZE) {
            List<Hub> approved = bulkApprovePending();
            log.info("Auto-approved {} pending hubs — queue reached {}", approved.size(), AUTO_APPROVE_QUEUE_SIZE);
            Long hubId = hub.getId();
            hub = approved.stream().filter(h -> h.getId().equals(hubId)).findFirst().orElse(hub);
        }

        return mapper.toHubResponse(hub);
    }

    /**
     * Approves every currently pending hub — shared by the manual admin
     * bulk-approve endpoint and the queue-size auto-approve trigger above.
     * Hubs are saved one at a time so each generateUniqueCode call sees
     * prior codes from this batch (auto-flush before the findByCode query).
     * Uses a PESSIMISTIC_WRITE lock (findAllPendingForUpdate) so two
     * concurrent bulk-approval attempts serialize instead of racing on
     * generateUniqueCode()/the unique hubs.code constraint.
     */
    private List<Hub> bulkApprovePending() {
        List<Hub> pending = hubRepository.findAllPendingForUpdate();
        List<Hub> approved = new ArrayList<>();
        for (Hub hub : pending) {
            hub.setCode(generateUniqueCode(hub.getName()));
            hub.setStatus(HubStatus.ACTIVE);
            approved.add(hubRepository.save(hub));
        }
        return approved;
    }

    /**
     * Admin: approve a pending hub. Evicts hub cache so new hub appears
     * immediately in the active hub list.
     * If code is null or blank, auto-generates one from the hub name
     * and appends _2, _3, ... until it is unique.
     */
    @Caching(evict = {
            @CacheEvict(value = "hubs",       allEntries = true),
            @CacheEvict(value = "hub-search", allEntries = true)
    })
    @Transactional
    public HubResponse approveHub(Long hubId, String code) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new HubNotFoundException(hubId));

        String resolvedCode = (code != null && !code.isBlank())
                ? code.toUpperCase()
                : generateUniqueCode(hub.getName());

        hub.setCode(resolvedCode);
        hub.setStatus(HubStatus.ACTIVE);
        Hub saved = hubRepository.save(hub);

        log.info("Hub approved: id={} code={} name={}", saved.getId(), saved.getCode(), saved.getName());
        return mapper.toHubResponse(saved);
    }

    private String generateUniqueCode(String name) {
        String base = name.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (hubRepository.findByCode(base).isEmpty()) return base;

        int suffix = 2;
        while (true) {
            String candidate = base + "_" + suffix;
            if (hubRepository.findByCode(candidate).isEmpty()) return candidate;
            suffix++;
        }
    }

    /**
     * Admin: reject a pending hub suggestion.
     */
    @Transactional
    public void rejectHub(Long hubId) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new HubNotFoundException(hubId));
        hub.setStatus(HubStatus.REJECTED);
        hubRepository.save(hub);
        log.info("Hub rejected: id={} name={}", hubId, hub.getName());
    }

    @Transactional(readOnly = true)
    public List<HubResponse> getPendingHubs() {
        return hubRepository.findAllPending()
                .stream()
                .map(mapper::toHubResponse)
                .toList();
    }

    /**
     * Admin: approve every pending hub in one pass. See {@link #bulkApprovePending}.
     */
    @Caching(evict = {
            @CacheEvict(value = "hubs",       allEntries = true),
            @CacheEvict(value = "hub-search", allEntries = true)
    })
    @Transactional
    public List<HubResponse> approveAllPendingHubs() {
        List<Hub> approved = bulkApprovePending();
        log.info("Bulk-approved {} pending hubs", approved.size());
        return approved.stream().map(mapper::toHubResponse).toList();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Hub createPendingHub(SuggestHubRequest request, Long userId) {
        User suggester = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Hub hub = Hub.builder()
                .name(request.name())
                .area(request.area())
                .suggestedBy(suggester)
                .status(HubStatus.PENDING)
                .build();

        Hub saved = hubRepository.save(hub);
        log.info("Hub suggested: id={} name={} area={} by userId={}",
                saved.getId(), saved.getName(), saved.getArea(), userId);
        return saved;
    }

    /**
     * Get hub by ID — used by HubMatcher after alias lookup.
     */
    @Cacheable(value = "hubs", key = "'id-' + #hubId")
    @Transactional(readOnly = true)
    public HubResponse getHubById(Long hubId) {
        return hubRepository.findById(hubId)
                .map(mapper::toHubResponse)
                .orElseThrow(() -> new HubNotFoundException(hubId));
    }

    /**
     * All active hubs for Levenshtein fuzzy matching.
     * Same cache as getAllActiveHubs.
     */
    @Cacheable(value = "hubs", key = "'all-active'")
    @Transactional(readOnly = true)
    public List<HubResponse> getAllHubs() {
        return hubRepository.findByStatusOrderByAreaAscNameAsc(HubStatus.ACTIVE)
                .stream()
                .map(mapper::toHubResponse)
                .toList();
    }

    /**
     * Get recently used hubs for a user — combines driver and passenger history.
     * Used for quick picks in bot hub selection flow.
     * Not cached — must reflect latest activity.
     */
    @Transactional(readOnly = true)
    public List<HubResponse> getRecentHubsForUser(Long userId) {
        // Get hubs from both driver and passenger history
        List<Hub> driverHubs    = hubRepository.findRecentHubsByDriverId(userId);
        List<Hub> passengerHubs = hubRepository.findRecentHubsByPassengerId(userId);

        // Combine, deduplicate by ID, limit to 5
        return java.util.stream.Stream.concat(
                        driverHubs.stream(),
                        passengerHubs.stream())
                .collect(java.util.LinkedHashMap<Long, Hub>::new,
                        (map, hub) -> map.putIfAbsent(hub.getId(), hub),
                        java.util.LinkedHashMap::putAll)
                .values()
                .stream()
                .map(mapper::toHubResponse)
                .limit(5)
                .toList();
    }

    @Cacheable(value = "hub-aliases", key = "#alias.toLowerCase()")
    @Transactional(readOnly = true)
    public Optional<HubResponse> findByAlias(String alias) {
        return hubAliasRepository
                .findByAliasIgnoreCase(alias)
                .map(a -> mapper.toHubResponse(a.getHub()));
    }
}
