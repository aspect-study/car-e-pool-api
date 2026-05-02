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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Admin approves it separately via admin API.
     */
    @Transactional
    public HubResponse suggestHub(SuggestHubRequest request, Long requestingUserId) {
        // Prevent duplicate suggestions for the same location
        if (hubRepository.existsByNameIgnoreCaseAndArea(request.name(), request.area())) {
            log.info("Hub already exists or pending: name={} area={}", request.name(), request.area());
            // Return the existing hub instead of creating a duplicate
            return hubRepository.searchActive(request.name()).stream()
                    .filter(h -> h.getArea().equalsIgnoreCase(request.area()))
                    .findFirst()
                    .map(mapper::toHubResponse)
                    .orElseGet(() -> createPendingHub(request, requestingUserId));
        }
        return createPendingHub(request, requestingUserId);
    }

    /**
     * Admin: approve a pending hub. Evicts hub cache so new hub appears
     * immediately in the active hub list.
     */
    @CacheEvict(value = "hubs", allEntries = true)
    @Transactional
    public HubResponse approveHub(Long hubId, String code) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new HubNotFoundException(hubId));

        hub.setCode(code.toUpperCase());
        hub.setStatus(HubStatus.ACTIVE);
        Hub saved = hubRepository.save(hub);

        log.info("Hub approved: id={} code={} name={}", saved.getId(), saved.getCode(), saved.getName());
        return mapper.toHubResponse(saved);
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

    // ── Internal helpers ─────────────────────────────────────────────────────

    private HubResponse createPendingHub(SuggestHubRequest request, Long userId) {
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
        return mapper.toHubResponse(saved);
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
