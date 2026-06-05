package com.carpool.web.integration;

import com.carpool.domain.entity.User;
import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.UserRepository;
import com.carpool.repository.VehicleRepository;
import com.carpool.service.dto.response.VehicleResponse;
import com.carpool.service.vehicle.VehicleService;
import com.carpool.common.exception.NotRideOwnerException;
import com.carpool.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Vehicle Integration")
class VehicleIntegrationTest extends BaseIntegrationTest {

    @Autowired private VehicleService    vehicleService;
    @Autowired private UserRepository    userRepository;
    @Autowired private VehicleRepository vehicleRepository;

    private User driver;
    private User otherDriver;

    private String plate;

    @BeforeEach
    void setUp() {
        long seed = System.currentTimeMillis();
        plate = "T" + (seed % 100000);

        driver = userRepository.save(User.builder()
                .telegramId(seed + 10).fullName("Driver A")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());

        otherDriver = userRepository.save(User.builder()
                .telegramId(seed + 11).fullName("Driver B")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE).build());
    }

    @Test
    @DisplayName("addVehicle — happy path returns VehicleResponse with plateNumber")
    void addVehicle_returnsResponse() {
        VehicleResponse response = vehicleService.addVehicle(
                driver.getId(), "Toyota Vios", "Silver", plate + "A", 4);

        assertThat(response.id()).isNotNull();
        assertThat(response.plateNumber()).isEqualTo(plate + "A");
        assertThat(response.model()).isEqualTo("Toyota Vios");
        assertThat(response.seatCapacity()).isEqualTo(4);
    }

    @Test
    @DisplayName("addVehicle — 4th vehicle soft-deletes oldest (replace-oldest policy)")
    void addVehicle_replaceOldestAtLimit() {
        vehicleService.addVehicle(driver.getId(), "Model A", null, plate + "1", 4);
        vehicleService.addVehicle(driver.getId(), "Model B", null, plate + "2", 4);
        vehicleService.addVehicle(driver.getId(), "Model C", null, plate + "3", 4);
        vehicleService.addVehicle(driver.getId(), "Model D", null, plate + "4", 4);

        List<VehicleResponse> active = vehicleService.getActiveVehiclesForUser(driver.getId());

        assertThat(active).hasSize(3);
        assertThat(active).noneMatch(v -> v.plateNumber().equals(plate + "1"));
        assertThat(active).anyMatch(v -> v.plateNumber().equals(plate + "4"));
    }

    @Test
    @DisplayName("addVehicle — duplicate plate for another user throws 400")
    void addVehicle_duplicatePlate_throwsBadRequest() {
        vehicleService.addVehicle(otherDriver.getId(), "Model X", null, plate + "D", 4);

        assertThatThrownBy(() ->
                vehicleService.addVehicle(driver.getId(), "Model Y", null, plate + "D", 4))
                .isInstanceOf(com.carpool.common.exception.CarpoolException.class);
    }

    @Test
    @DisplayName("getActiveVehiclesForUser — returns ordered list oldest-first")
    void getActiveVehiclesForUser_orderedOldestFirst() {
        vehicleService.addVehicle(driver.getId(), "First",  null, plate + "F", 4);
        vehicleService.addVehicle(driver.getId(), "Second", null, plate + "S", 4);

        List<VehicleResponse> list = vehicleService.getActiveVehiclesForUser(driver.getId());

        assertThat(list).hasSize(2);
        assertThat(list.get(0).plateNumber()).isEqualTo(plate + "F");
        assertThat(list.get(1).plateNumber()).isEqualTo(plate + "S");
    }

    @Test
    @DisplayName("removeVehicle — own vehicle returns cleanly")
    void removeVehicle_ownVehicle_succeeds() {
        VehicleResponse v = vehicleService.addVehicle(
                driver.getId(), "Model Z", null, plate + "R", 4);

        assertThatCode(() -> vehicleService.removeVehicle(v.id(), driver.getId()))
                .doesNotThrowAnyException();

        assertThat(vehicleService.getActiveVehiclesForUser(driver.getId())).isEmpty();
    }

    @Test
    @DisplayName("removeVehicle — other user's vehicle throws 403 NotRideOwnerException")
    void removeVehicle_otherUsersVehicle_throws403() {
        VehicleResponse v = vehicleService.addVehicle(
                otherDriver.getId(), "Other Model", null, plate + "O", 4);

        assertThatThrownBy(() -> vehicleService.removeVehicle(v.id(), driver.getId()))
                .isInstanceOf(NotRideOwnerException.class);
    }

    @Test
    @DisplayName("removeVehicle — non-existent vehicle throws 404")
    void removeVehicle_notFound_throws404() {
        assertThatThrownBy(() -> vehicleService.removeVehicle(99999L, driver.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
