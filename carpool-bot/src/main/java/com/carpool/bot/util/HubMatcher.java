package com.carpool.bot.util;

import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.hub.HubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Fuzzy-matches user free-text input to a known Hub.
 *
 * Strategy (in order):
 *   1. Exact code match       — "SM_AURA" → SM Aura Premier
 *   2. Exact name match       — "SM Aura Premier" → SM Aura Premier
 *   3. Contains match         — "aura" → SM Aura Premier
 *   4. All-words match        — "sm aura" → all hubs containing both "sm" and "aura"
 *   5. Highest word-hit score — pick the hub with the most matching words
 *
 * Uses HubService.searchHubs() (cached) to get candidates,
 * then applies local scoring on the results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HubMatcher {

    private final HubService hubService;

    /**
     * Attempt to match free text to a hub.
     * Returns empty if no reasonable match found.
     */
    public Optional<HubResponse> match(String input) {
        if (input == null || input.isBlank()) return Optional.empty();

        String normalized = input.trim().toLowerCase();

        // Step 1+2+3: Let HubService handle the DB-side search (cached)
        List<HubResponse> candidates = hubService.searchHubs(normalized);

        if (candidates.isEmpty()) {
            log.debug("HubMatcher: no candidates for input='{}'", input);
            return Optional.empty();
        }

        if (candidates.size() == 1) {
            log.debug("HubMatcher: single match '{}' for input='{}'",
                    candidates.get(0).name(), input);
            return Optional.of(candidates.get(0));
        }

        // Step 4+5: Score candidates by word overlap
        String[] inputWords = normalized.split("\\s+");
        HubResponse best = candidates.stream()
                .max(Comparator.comparingInt(hub -> scoreHub(hub, inputWords)))
                .orElse(candidates.get(0));

        log.debug("HubMatcher: matched '{}' → '{}' (score={})",
                input, best.name(), scoreHub(best, inputWords));

        return Optional.of(best);
    }

    /**
     * Score a hub by counting how many input words appear in its name/area.
     * Higher = better match.
     */
    private int scoreHub(HubResponse hub, String[] inputWords) {
        String searchable = (hub.name() + " " + hub.area() + " " +
                (hub.code() != null ? hub.code() : "")).toLowerCase();
        int score = 0;
        for (String word : inputWords) {
            if (searchable.contains(word)) score++;
        }
        return score;
    }
}