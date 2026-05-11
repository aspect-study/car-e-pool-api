package com.carpool.repository;

import com.carpool.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long userId);

    @Query("SELECT v FROM Vehicle v WHERE v.plateNumber = :plate AND v.deletedAt IS NULL AND v.user.id != :excludeUserId")
    Optional<Vehicle> findActiveByPlateForOtherUser(@Param("plate") String plate,
                                                     @Param("excludeUserId") Long excludeUserId);

    boolean existsByUserIdAndDeletedAtIsNull(Long userId);
}