package com.carpool.web.controller;

import com.carpool.common.response.ApiResponse;
import com.carpool.service.dto.request.AddVehicleRequest;
import com.carpool.service.dto.response.VehicleResponse;
import com.carpool.service.vehicle.VehicleService;
import com.carpool.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Multi-vehicle management for drivers")
public class VehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "List my vehicles",
            description = """
                Returns all active vehicles registered by the authenticated driver.
                Ordered oldest-first (matches bot vehicle selection order).
                Returns empty list if no vehicles registered.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "List of registered vehicles")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Not authenticated")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> listMyVehicles(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
                vehicleService.getActiveVehiclesForUser(currentUser.getUserId())));
    }

    @Operation(summary = "Add a vehicle",
            description = """
                Registers a new vehicle for the authenticated driver.

                - Maximum **3 active vehicles** — adding a 4th soft-deletes the oldest (replace-oldest policy)
                - `plateNumber` is normalized to uppercase
                - `seatCapacity` defaults to **4** if omitted

                Returns 409 if the plate number is already registered by another user.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "Vehicle added")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Plate number already registered by another user")
    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> addVehicle(
            @Valid @RequestBody AddVehicleRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        VehicleResponse vehicle = vehicleService.addVehicle(
                currentUser.getUserId(),
                request.model(),
                request.color(),
                request.plateNumber(),
                request.seatCapacity());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(vehicle));
    }

    @Operation(summary = "Remove a vehicle",
            description = """
                Soft-deletes a specific vehicle owned by the authenticated driver.

                Returns 403 if the vehicle belongs to a different user.
                Returns 404 if the vehicle does not exist.
                """,
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Vehicle removed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "Vehicle belongs to a different user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "Vehicle not found")
    @DeleteMapping("/{vehicleId}")
    public ResponseEntity<ApiResponse<Void>> removeVehicle(
            @PathVariable Long vehicleId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        vehicleService.removeVehicle(vehicleId, currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
