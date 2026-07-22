package com.carpool.service.dto.request;

import jakarta.validation.constraints.AssertTrue;

/**
 * Request to change a ride's origin and/or destination hub.
 * A null field means "keep the current hub". At least one must be provided.
 */
public record UpdateRouteRequest(
        Long originHubId,
        Long destinationHubId
) {
    @AssertTrue(message = "Provide originHubId and/or destinationHubId")
    public boolean isAtLeastOneProvided() {
        return originHubId != null || destinationHubId != null;
    }
}
