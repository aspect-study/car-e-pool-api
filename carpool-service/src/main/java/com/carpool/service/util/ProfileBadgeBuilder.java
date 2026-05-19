package com.carpool.service.util;

import com.carpool.service.dto.response.ProfileStatsResponse;

public final class ProfileBadgeBuilder {

    public static String buildPassengerBadge(ProfileStatsResponse stats, String ratingLabel) {
        StringBuilder sb = new StringBuilder();
        String role = stats.roleLabel();
        if (role != null && role.startsWith("👋")) {
            sb.append(role);
        } else {
            sb.append("✅ ").append(role != null ? role : "Passenger");
            if (stats.passengerCompleted() != null && stats.passengerCompleted() > 0) {
                sb.append(" | ").append(stats.passengerCompleted()).append(" rides done");
            }
        }
        sb.append(" | Since ").append(stats.memberSince());
        if (ratingLabel != null && !ratingLabel.isBlank()) {
            sb.append("\n").append(ratingLabel);
        }
        return sb.toString();
    }

    private ProfileBadgeBuilder() {}
}