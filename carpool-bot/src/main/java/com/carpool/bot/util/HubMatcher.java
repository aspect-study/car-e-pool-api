package com.carpool.bot.util;

import com.carpool.repository.HubAliasRepository;
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
 *   1. Alias match            — "moa" → SM Mall of Asia
 *   2. Exact name match       — "SM Aura Premier" → SM Aura Premier
 *   3. Contains match         — "aura" → SM Aura Premier
 *   4. Word-score match       — "sm aura" → hub with most matching words
 *   5. Levenshtein ≤ 2        — "southmal" → SM Southmall
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HubMatcher {

    private final HubService         hubService;
    private final HubAliasRepository hubAliasRepository;

    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_LEVENSHTEIN = 2;

    /**
     * Attempt to match free text to a single hub.
     * Returns empty if no reasonable match found.
     */
    public Optional<HubResponse> match(String input) {
        if (input == null || input.isBlank()) return Optional.empty();

        // Minimum 3 characters required — single/double char input is too ambiguous
        if (input.trim().length() < 3) return Optional.empty();

        String normalized = input.trim().toLowerCase();

        // Layer 1: Alias match — "moa", "bgc", "atc", etc.
        Optional<HubResponse> aliasMatch = hubAliasRepository
                .findByAliasIgnoreCase(normalized)
                .map(alias -> hubService.getHubById(alias.getHub().getId()));
        if (aliasMatch.isPresent()) {
            log.debug("HubMatcher: alias match '{}' → '{}'", input, aliasMatch.get().name());
            return aliasMatch;
        }

        // Layer 2+3+4: DB-side search (cached in HubService)
        List<HubResponse> candidates = hubService.searchHubs(normalized);

        if (candidates.size() == 1) {
            log.debug("HubMatcher: single match '{}' for input='{}'",
                    candidates.get(0).name(), input);
            return Optional.of(candidates.get(0));
        }

        if (candidates.size() > 1) {
            String[] inputWords = normalized.split("\\s+");
            HubResponse best = candidates.stream()
                    .max(Comparator.comparingInt(hub -> scoreHub(hub, inputWords)))
                    .orElse(candidates.get(0));
            log.debug("HubMatcher: word-score match '{}' → '{}'", input, best.name());
            return Optional.of(best);
        }

        // Layer 5: Levenshtein fallback — typo tolerance
        List<HubResponse> all = hubService.getAllHubs();
        return all.stream()
                .filter(hub -> levenshtein(normalized, hub.name().toLowerCase()) <= MAX_LEVENSHTEIN)
                .min(Comparator.comparingInt(hub ->
                        levenshtein(normalized, hub.name().toLowerCase())))
                .map(hub -> {
                    log.debug("HubMatcher: levenshtein match '{}' → '{}'", input, hub.name());
                    return hub;
                });
    }

    /**
     * Returns up to MAX_SUGGESTIONS hubs that partially match the input.
     * Used to show suggestions when no exact match is found.
     */
    public List<HubResponse> suggest(String input) {
        if (input == null || input.isBlank()) return List.of();

        // Minimum 3 characters required
        if (input.trim().length() < 3) return List.of();

        String normalized = input.trim().toLowerCase();
        List<HubResponse> all = hubService.getAllHubs();
        String[] inputWords = normalized.split("\\s+");

        return all.stream()
                .filter(hub -> {
                    String searchable = (hub.name() + " " + hub.area() + " " +
                            (hub.code() != null ? hub.code() : "")).toLowerCase();
                    // Include if contains match OR levenshtein within threshold
                    return searchable.contains(normalized)
                            || levenshtein(normalized, hub.name().toLowerCase()) <= MAX_LEVENSHTEIN
                            || allWordsMatch(searchable, inputWords);
                })
                .sorted(Comparator.comparingInt((HubResponse hub) ->
                                levenshtein(normalized, hub.name().toLowerCase()))
                        .thenComparingInt(hub -> -scoreHub(hub, inputWords)))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private int scoreHub(HubResponse hub, String[] inputWords) {
        String searchable = (hub.name() + " " + hub.area() + " " +
                (hub.code() != null ? hub.code() : "")).toLowerCase();
        int score = 0;
        for (String word : inputWords) {
            if (searchable.contains(word)) score++;
        }
        return score;
    }

    private boolean allWordsMatch(String searchable, String[] words) {
        for (String word : words) {
            if (!searchable.contains(word)) return false;
        }
        return true;
    }

    /**
     * Levenshtein distance — edit distance between two strings.
     * Space-optimized O(n) implementation.
     */
    private int levenshtein(String a, String b) {
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] dp = new int[b.length() + 1];
        for (int i = 0; i <= b.length(); i++) dp[i] = i;

        for (int i = 1; i <= a.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int temp = dp[j];
                dp[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev
                        : 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
                prev = temp;
            }
        }
        return dp[b.length()];
    }
}