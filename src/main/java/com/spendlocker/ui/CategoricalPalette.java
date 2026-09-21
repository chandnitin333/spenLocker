package com.spendlocker.ui;

/**
 * Fixed-order categorical palette (blue, orange, aqua, yellow, magenta, green, violet, red).
 * Validated for adjacent-pair colorblind and normal-vision contrast; never cycle or
 * reassign a slot by rank — identity keeps its slot, and an overflow bucket ("Other")
 * gets a neutral gray rather than a 9th generated hue.
 */
public final class CategoricalPalette {

    private static final String[] LIGHT = {
        "#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#4a3aa7", "#e34948"
    };
    private static final String[] DARK = {
        "#3987e5", "#d95926", "#199e70", "#c98500", "#d55181", "#008300", "#9085e9", "#e66767"
    };
    private static final String OTHER_LIGHT = "#9a9a94";
    private static final String OTHER_DARK = "#7d7c76";

    private final boolean dark;

    public CategoricalPalette(boolean dark) {
        this.dark = dark;
    }

    public int size() {
        return LIGHT.length;
    }

    public String slot(int index) {
        String[] set = dark ? DARK : LIGHT;
        return set[index % set.length];
    }

    public String otherColor() {
        return dark ? OTHER_DARK : OTHER_LIGHT;
    }

    public String sequentialHue() {
        return slot(0);
    }
}
