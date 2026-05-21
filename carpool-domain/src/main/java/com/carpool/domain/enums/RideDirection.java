package com.carpool.domain.enums;

public enum RideDirection {
    HOME_TO_WORK, WORK_TO_HOME, OTHER;

    public String label() {
        return switch (this) {
            case HOME_TO_WORK -> "home-to-work";
            case WORK_TO_HOME -> "work-to-home";
            case OTHER        -> "other";
        };
    }
}
