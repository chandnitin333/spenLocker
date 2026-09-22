package com.spendlocker.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/** The reference design's "hstrip" KPI row: hairline-divided cells, no per-cell card/shadow —
 *  shared by Dashboard, Expenses, and Investments so each screen's own KPI strip matches. */
public final class KpiStrip {

    private KpiStrip() {
    }

    public static HBox strip(VBox... cells) {
        HBox strip = new HBox(1, cells);
        strip.getStyleClass().add("hstrip");
        strip.setFillHeight(true);
        return strip;
    }

    public static VBox cell(String label, String value, String caption) {
        return cell(label, value, caption, false);
    }

    /** urgent colors the value text red (the reference's "Back within 30 days" treatment). */
    public static VBox cell(String label, String value, String caption, boolean urgent) {
        VBox box = base(label, value, urgent);
        if (caption != null) {
            Label captionLabel = new Label(caption);
            captionLabel.getStyleClass().add("text-caption");
            box.getChildren().add(captionLabel);
        }
        return box;
    }

    /** A tinted pill note (green/red) instead of a plain caption — for ROI/gain-style values. */
    public static VBox cellWithTag(String label, String value, String tagText, boolean positive) {
        VBox box = base(label, value, false);
        Label tag = new Label(tagText);
        tag.getStyleClass().addAll("tag", positive ? "positive" : "negative");
        box.getChildren().add(tag);
        return box;
    }

    private static VBox base(String label, String value, boolean urgent) {
        Label labelText = new Label(label.toUpperCase(Locale.ROOT));
        labelText.getStyleClass().add("hcell-label");
        Label valueText = new Label(value);
        valueText.getStyleClass().add("hcell-value");

        VBox cell = new VBox(4, labelText, valueText);
        cell.getStyleClass().add("hcell");
        if (urgent) cell.getStyleClass().add("urgent");
        cell.setPadding(new Insets(14, 16, 14, 16));
        cell.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }
}
