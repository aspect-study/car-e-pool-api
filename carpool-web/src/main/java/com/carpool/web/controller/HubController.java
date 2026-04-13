package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.SuggestHubRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.hub.HubService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Get all active hubs",
            description = "Returns all admin-approved hubs ordered by area then name. " +
                    "Cached for 60 minutes. Public endpoint — no token required.")
    /**
     * GET /api/v1/hubs
     * Public — returns all active hubs (cached 60 min).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<HubResponse>>> getAllHubs() {
        return ResponseEntity.ok(ApiResponse.ok(hubService.getAllActiveHubs()));
    }

    @Operation(summary = "Search hubs by keyword",
            description = "Autocomplete search against hub name and area. " +
                    "Cached per keyword for 5 minutes. Public endpoint.")
    /**
     * GET /api/v1/hubs/search?q=BGC
     * Public — autocomplete for hub name/area (cached 5 min).
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HubResponse>>> searchHubs(
            @RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(hubService.searchHubs(q)));
    }

    @Operation(summary = "Suggest a new hub",
            description = "Driver suggests a pickup/dropoff location not yet in the system. " +
                    "Saved as PENDING and usable immediately on the current ride. " +
                    "Admin must approve before it appears in the public hub list.",
            security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "[Admin] Get pending hub suggestions",
            security = @SecurityRequirement(name = "bearerAuth"))
    /**
     * GET /api/v1/hubs/pending
     * Admin only — list all pending hub suggestions.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<HubResponse>>> getPendingHubs() {
        return ResponseEntity.ok(ApiResponse.ok(hubService.getPendingHubs()));
    }

    @Operation(summary = "[Admin] Approve a pending hub",
            security = @SecurityRequirement(name = "bearerAuth"))
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

    @Operation(summary = "[Admin] Reject a pending hub",
            security = @SecurityRequirement(name = "bearerAuth"))
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
