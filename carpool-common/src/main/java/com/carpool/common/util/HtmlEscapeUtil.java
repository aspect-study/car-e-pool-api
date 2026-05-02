package com.carpool.common.util;

public final class HtmlEscapeUtil {

    private HtmlEscapeUtil() {}

    public static String escape(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}