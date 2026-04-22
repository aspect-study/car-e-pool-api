package com.carpool.service.vehicle;

import com.carpool.common.exception.UserNotFoundException;
import com.carpool.domain.entity.User;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.response.UserResponse;
import com.carpool.service.mapper.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final UserRepository userRepository;
    private final EntityMapper   mapper;

    /**
     * Save or update vehicle info for a user.
     * Overwrites existing vehicle info — single vehicle per user (Path 1).
     * plate_number has a unique index — duplicate plates will throw DataIntegrityViolationException.
     */
    @Transactional
    public UserResponse updateVehicle(Long userId, String carColor,
                                      String carModel, String plateNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newPlate = plateNumber.trim().toUpperCase();

        // Check if plate is already used by a DIFFERENT user
        // Same user updating their own plate is allowed
        userRepository.findByPlateNumber(newPlate).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new com.carpool.common.exception.InvalidRideStateException(
                        "Plate number " + newPlate + " is already registered by another user.");
            }
        });

        user.setCarColor(carColor != null ? carColor.trim() : null);
        user.setCarModel(carModel.trim());
        user.setPlateNumber(newPlate);

        User saved = userRepository.save(user);

        log.info("Vehicle updated: userId={} color={} model={} plate={}",
                userId, carColor, carModel, plateNumber);

        return mapper.toUserResponse(saved);
    }

    /**
     * Clear vehicle info for a user.
     * Used when driver explicitly removes their vehicle.
     */
    @Transactional
    public void clearVehicle(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setCarColor(null);
        user.setCarModel(null);
        user.setPlateNumber(null);

        userRepository.save(user);

        log.info("Vehicle cleared: userId={}", userId);
    }

    /**
     * Check if user has complete vehicle info saved.
     * Color is optional — model and plate are required.
     */
    @Transactional(readOnly = true)
    public boolean hasVehicleInfo(Long userId) {
        return userRepository.findById(userId)
                .map(User::hasVehicleInfo)
                .orElse(false);
    }
}