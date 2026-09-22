package com.spendlocker.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The reference design's "concentration" strip: a single flat bar, segments sized by value and
 * shaded with one hue (172, teal) ramped from dark (largest share) to light (smallest) instead
 * of a rainbow, plus a legend for the top 3 entries and a "N in all" count — ported from
 * wealth-book.html's renderConcentration() exactly (same hue/saturation and lightness range).
 */
public class ConcentrationBar extends VBox {

    private final HBox bar = new HBox(1.5);
    private final FlowPane legend = new FlowPane(14, 6);

    public ConcentrationBar() {
        super(10);
        setPrefWidth(300);
        setMinWidth(260);
        bar.setPrefHeight(13);
        bar.setMinHeight(13);
        bar.setMaxHeight(13);
        bar.setPrefWidth(300);
        getChildren().addAll(bar, legend);
    }

    public void setData(LinkedHashMap<String, Double> data, String unitLabelPlural, Consumer<String> onSegmentClick) {
        bar.getChildren().clear();
        legend.getChildren().clear();

        double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
        int n = data.size();
        if (n == 0 || total <= 0) {
            Label empty = new Label("No data yet");
            empty.getStyleClass().add("text-caption");
            legend.getChildren().add(empty);
            return;
        }

        int i = 0;
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            String name = entry.getKey();
            double value = entry.getValue();
            double fraction = value / total;
            String color = toHex(shade(i, n));
            boolean clickable = onSegmentClick != null && !"Other".equals(name);

            Region segment = new Region();
            segment.setStyle("-fx-background-color: " + color + ";" + cornerRadius(i, n));
            segment.prefWidthProperty().bind(bar.widthProperty().multiply(fraction));
            HBox.setHgrow(segment, Priority.NEVER);
            Tooltip.install(segment, new Tooltip(String.format("%s — %s (%.1f%%)", name, money(value), fraction * 100)
                    + (clickable ? "\nClick to view" : "")));
            if (clickable) {
                segment.setCursor(javafx.scene.Cursor.HAND);
                segment.setOnMouseClicked(e -> onSegmentClick.accept(name));
            }
            bar.getChildren().add(segment);

            if (i < 3) {
                legend.getChildren().add(legendChip(name, color, fraction, clickable ? onSegmentClick : null));
            }
            i++;
        }

        Label countLabel = new Label(n + " " + unitLabelPlural + " in all");
        countLabel.getStyleClass().add("text-caption");
        legend.getChildren().add(countLabel);
    }

    private String cornerRadius(int i, int n) {
        if (n == 1) return "-fx-background-radius: 3;";
        if (i == 0) return "-fx-background-radius: 3 0 0 3;";
        if (i == n - 1) return "-fx-background-radius: 0 3 3 0;";
        return "-fx-background-radius: 0;";
    }

    /** hsl(172 34% L%), L ramped 25%->61% across the ranked entries — the reference's own formula. */
    private Color shade(int i, int n) {
        double lightness = 25 + (n <= 1 ? 0 : ((double) i / (n - 1)) * 36);
        return hsl(172, 34, lightness);
    }

    private Color hsl(double h, double s, double l) {
        s /= 100;
        l /= 100;
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return Color.color(r + m, g + m, b + m);
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private HBox legendChip(String name, String color, double fraction, Consumer<String> onClick) {
        Region swatch = new Region();
        swatch.getStyleClass().add("legend-swatch");
        swatch.setStyle("-fx-background-color: " + color + ";");
        Label text = new Label(String.format("%s %.0f%%", name, fraction * 100));
        text.getStyleClass().add("text-caption");
        HBox chip = new HBox(6, swatch, text);
        chip.setAlignment(Pos.CENTER_LEFT);
        if (onClick != null) {
            chip.setCursor(javafx.scene.Cursor.HAND);
            chip.setOnMouseClicked(e -> onClick.accept(name));
            Tooltip.install(chip, new Tooltip("Click to view"));
        }
        return chip;
    }

    private String money(double value) {
        return com.spendlocker.util.MoneyFormat.currency(value);
    }
}
