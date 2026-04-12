package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.SuggestHubRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.hub.HubService;
import com.carpool.web.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubService hubService;

    /**
     * GET /api/v1/hubs
     * Public — returns all active hubs (cached 60 min).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<HubResponse>>> getAllHubs() {
        return ResponseEntity.ok(ApiResponse.ok(hubService.getAllActiveHubs()));
    }

    /**
     * GET /api/v1/hubs/search?q=BGC
     * Public — autocomplete for hub name/area (cached 5 min).
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HubResponse>>> searchHubs(
            @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(hubService.searchHubs(q)));
    }

    /**
     * POST /api/v1/hubs/suggest
     * Authenticated — driver suggests a new hub not yet in the system.
     */
    @PostMapping("/suggest")
    public ResponseEntity<ApiResponse<HubResponse>> suggestHub(
            @Valid @RequestBody SuggestHubRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        HubResponse hub = hubService.suggestHub(request, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(hub));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/hubs/pending
     * Admin only — list all pending hub suggestions.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<HubResponse>>> getPendingHubs() {
        return ResponseEntity.ok(ApiResponse.ok(hubService.getPendingHubs()));
    }

    /**
     * PATCH /api/v1/hubs/{id}/approve?code=BGC_HIGHSTREET
     * Admin only — approve a pending hub.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HubResponse>> approveHub(
            @PathVariable Long id,
            @RequestParam String code) {
        return ResponseEntity.ok(ApiResponse.ok(hubService.approveHub(id, code)));
    }

    /**
     * PATCH /api/v1/hubs/{id}/reject
     * Admin only — reject a pending hub.
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectHub(@PathVariable Long id) {
        hubService.rejectHub(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
