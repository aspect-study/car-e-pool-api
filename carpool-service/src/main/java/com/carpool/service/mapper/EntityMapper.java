package com.carpool.service.mapper;

import com.carpool.domain.entity.*;
import com.carpool.service.dto.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Central MapStruct mapper.
 * componentModel="spring" makes it a Spring bean — injectable via @Autowired.
 *
 * MapStruct generates the implementation at compile time — zero reflection,
 * zero runtime overhead compared to ModelMapper.
 */
@Mapper(componentModel = "spring")
public interface EntityMapper {

    UserResponse toUserResponse(User user);

    HubResponse toHubResponse(Hub hub);

    WaypointResponse toWaypointResponse(RideWaypoint waypoint);

    VehicleResponse toVehicleResponse(Vehicle vehicle);

    @Mapping(target = "waypoints", source = "waypoints")
    @Mapping(target = "driverAvgRating", ignore = true)
    @Mapping(target = "vehicle", source = "vehicle")
    RideResponse toRideResponse(Ride ride);

    @Mapping(target = "rideId",          source = "ride.id")
    @Mapping(target = "ride",            source = "ride")
    @Mapping(target = "passengerMessage", source = "passengerMessage")
    @Mapping(target = "expiresAt",        source = "expiresAt")
    BookingResponse toBookingResponse(Booking booking);
}
