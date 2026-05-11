package com.carpool.bot.util;

public enum ButtonStyle {

    PRIMARY,             // BLUE
    SUCCESS,             // GREEN
    DANGER;               // RED

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
