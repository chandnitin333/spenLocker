package com.spendlocker.util;

import javafx.scene.control.TableColumn;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Sizes table columns wide enough that their header text is never clipped — hardcoded pixel
 * guesses broke on different platforms/DPI/font rendering. Measures the actual header text with
 * the table header's font instead, so it's correct regardless of the device's screen resolution.
 */
public final class TableColumnUtil {

    private static final double HEADER_PADDING = 40; // sort arrow + cell insets on every platform

    private TableColumnUtil() {
    }

    /**
     * @param minDataWidth a floor for the column so short headers (e.g. "Type") still leave
     *                     room for their actual data, not just the header label.
     */
    public static void fitHeader(TableColumn<?, ?> column, double minDataWidth) {
        Text measure = new Text(column.getText());
        measure.setFont(Font.font(13));
        double headerWidth = measure.getLayoutBounds().getWidth() + HEADER_PADDING;
        double width = Math.max(headerWidth, minDataWidth);
        column.setPrefWidth(width);
        column.setMinWidth(headerWidth);
    }
}
