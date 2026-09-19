package com.plexon.spawners.breaking;

import java.util.Locale;

public enum NonSilkRewardMode {
    ESSENCE,
    CUSTOM_ITEM,
    ESSENCE_AND_CUSTOM_ITEM,
    NONE;

    public static NonSilkRewardMode parse(final String value, final NonSilkRewardMode fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            return fallback;
        }
    }

    public boolean awardsEssence() {
        return this == ESSENCE || this == ESSENCE_AND_CUSTOM_ITEM;
    }

    public boolean awardsCustomItem() {
        return this == CUSTOM_ITEM || this == ESSENCE_AND_CUSTOM_ITEM;
    }
}
