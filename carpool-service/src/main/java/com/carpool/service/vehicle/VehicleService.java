package com.carpool.service.vehicle;

import com.carpool.common.exception.InvalidRideStateException;
import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.User;
import com.carpool.domain.entity.Vehicle;
import com.carpool.repository.UserRepository;
import com.carpool.repository.VehicleRepository;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.dto.response.VehicleResponse;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final int MAX_VEHICLES = 3;

    private final UserRepository    userRepository;
    private final VehicleRepository vehicleRepository;
    private final EntityMapper      mapper;

    @Transactional(readOnly = true)
    public List<VehicleResponse> getActiveVehiclesForUser(Long userId) {
        return vehicleRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Add a vehicle for a user. If the user already has MAX_VEHICLES active vehicles,
     * the oldest one is soft-deleted first (replace-oldest policy).
     * The user's legacy vehicle fields on User entity are kept in sync.
     */
    @Transactional
    public VehicleResponse addVehicle(Long userId, String model, String color,
                                      String plate, Integer seatCapacity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String normalizedPlate = plate.trim().toUpperCase();

        vehicleRepository.findActiveByPlateForOtherUser(normalizedPlate, userId)
                .ifPresent(v -> {
                    throw new InvalidRideStateException(
                            "Plate number " + normalizedPlate +
                            " is already registered by another user. " +
                            "If this belongs to you, please report it to the admin.");
                });

        List<Vehicle> active = vehicleRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);

        if (active.size() >= MAX_VEHICLES) {
            Vehicle oldest = active.get(0);
            oldest.setDeletedAt(Instant.now());
            vehicleRepository.save(oldest);
            log.info("Replaced oldest vehicle: vehicleId={} userId={}", oldest.getId(), userId);
        }

        Vehicle vehicle = Vehicle.builder()
                .user(user)
                .model(model.trim())
                .color(color != null && !color.isBlank() ? color.trim() : null)
                .plateNumber(normalizedPlate)
                .seatCapacity(seatCapacity != null ? seatCapacity : 4)
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle added: vehicleId={} userId={} plate={}", saved.getId(), userId, normalizedPlate);

        // Keep legacy User fields in sync so hasVehicleInfo() and REST /me still work
        user.setCarModel(saved.getModel());
        user.setCarColor(saved.getColor());
        user.setPlateNumber(saved.getPlateNumber());
        userRepository.save(user);

        return toResponse(saved);
    }

    /**
     * Soft-delete a specific vehicle owned by the given user.
     */
    @Transactional
    public void removeVehicle(Long vehicleId, Long userId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new InvalidRideStateException("Vehicle not found."));

        if (!vehicle.getUser().getId().equals(userId)) {
            throw new InvalidRideStateException("You can only remove your own vehicles.");
        }

        if (vehicle.getDeletedAt() != null) {
            return; // already removed
        }

        vehicle.setDeletedAt(Instant.now());
        vehicleRepository.save(vehicle);
        log.info("Vehicle removed: vehicleId={} userId={}", vehicleId, userId);
    }

    @Transactional(readOnly = true)
    public boolean hasVehicleInfo(Long userId) {
        return vehicleRepository.existsByUserIdAndDeletedAtIsNull(userId);
    }

    // ── Legacy methods — kept for REST API backward compatibility ─────────────

    /**
     * REST PATCH /me/vehicle — treated as add (with replace-oldest if at limit).
     * Returns UserResponse so the REST contract is unchanged.
     */
    @Transactional
    public UserResponse updateVehicle(Long userId, String carColor,
                                      String carModel, String plateNumber) {
        addVehicle(userId, carModel, carColor, plateNumber, 4);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return mapper.toUserResponse(user);
    }

    /**
     * REST DELETE /me/vehicle — soft-deletes all active vehicles and clears User fields.
     */
    @Transactional
    public void clearVehicle(Long userId) {
        vehicleRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
                .forEach(v -> {
                    v.setDeletedAt(Instant.now());
                    vehicleRepository.save(v);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setCarModel(null);
        user.setCarColor(null);
        user.setPlateNumber(null);
        userRepository.save(user);

        log.info("All vehicles cleared: userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private VehicleResponse toResponse(Vehicle v) {
        return new VehicleResponse(v.getId(), v.getModel(), v.getColor(),
                v.getPlateNumber(), v.getSeatCapacity());
    }
}