package com.carpool.service.dto.response;

public record WaypointResponse(
        Long id,
        HubResponse hub,
        Integer sequenceOrder,
        Boolean isPickup,
        Boolean isDropoff
) {}
