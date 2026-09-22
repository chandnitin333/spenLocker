package com.spendlocker.util;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.Arrays;

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

    /**
     * Makes the given columns grow/shrink together to fill whatever width the table actually
     * has (proportional to each column's current preferred width) instead of sitting at fixed
     * pixel sizes that leave dead space on a wide screen or force a scrollbar on a narrow one
     * unnecessarily. {@code fixedWidthReserved} is the width already spoken for by columns NOT
     * passed here (e.g. a fixed-width, non-resizable Actions column) plus a small buffer for the
     * vertical scrollbar/borders. Each column's own minWidth (set by {@link #fitHeader}) still
     * wins if the proportional share would shrink it below its header/data floor — that's what
     * makes the horizontal scrollbar appear on a narrow window instead of clipping text.
     */
    public static void bindProportionalWidths(TableView<?> table, double fixedWidthReserved, TableColumn<?, ?>... columns) {
        double totalWeight = Arrays.stream(columns).mapToDouble(TableColumn::getPrefWidth).sum();
        for (TableColumn<?, ?> column : columns) {
            double weight = column.getPrefWidth() / totalWeight;
            column.prefWidthProperty().bind(
                    table.widthProperty().subtract(fixedWidthReserved).multiply(weight));
        }
    }
}
