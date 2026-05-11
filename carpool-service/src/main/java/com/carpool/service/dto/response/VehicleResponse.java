package com.carpool.service.dto.response;

public record VehicleResponse(
        Long    id,
        String  model,
        String  color,
        String  plateNumber,
        Integer seatCapacity
) {}