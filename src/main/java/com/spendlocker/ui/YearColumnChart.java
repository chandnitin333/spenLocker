package com.spendlocker.ui;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;

/** One bar per year — brass for years already banked (past), teal for current/future —
 *  matching the reference's "Interest by year" chart exactly. */
public class YearColumnChart extends javafx.scene.layout.Region {

    private static final Color BRASS = Color.web("#8A6115");
    private static final Color TEAL = Color.web("#2C7A6E");
    private static final Color LABEL_COLOR = Color.web("#556661");
    private static final Color VALUE_COLOR = Color.web("#0D2B2A");

    private final Canvas canvas = new Canvas();
    private List<String> yearLabels = List.of();
    private List<String> subLabels = List.of();
    private List<Double> values = List.of();
    private List<Boolean> past = List.of();
    private String currencySymbol = "";
    private final Tooltip tooltip = new Tooltip();
    private double hoverPaddingSide;
    private double hoverSlot;

    public YearColumnChart() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());
        canvas.setOnMouseMoved(e -> handleHover(e.getX(), e.getY()));
        canvas.setOnMouseExited(e -> tooltip.hide());
    }

    private void handleHover(double mx, double my) {
        if (values.isEmpty() || hoverSlot <= 0) {
            tooltip.hide();
            return;
        }
        int idx = (int) ((mx - hoverPaddingSide) / hoverSlot);
        if (idx < 0 || idx >= values.size()) {
            tooltip.hide();
            return;
        }
        tooltip.setText(String.format("%s · %s — %s",
                yearLabels.get(idx), subLabels.get(idx), com.spendlocker.util.MoneyFormat.currency(values.get(idx))));
        Point2D screen = canvas.localToScreen(mx, my);
        if (screen != null) {
            tooltip.show(canvas, screen.getX() + 12, screen.getY() + 12);
        }
    }

    public void setData(List<String> yearLabels, List<String> subLabels, List<Double> values, List<Boolean> past, String currencySymbol) {
        this.yearLabels = yearLabels;
        this.subLabels = subLabels;
        this.values = values;
        this.past = past;
        this.currencySymbol = currencySymbol;
        draw();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        if (values.isEmpty() || w <= 0 || h <= 0) return;

        double paddingSide = 8;
        double paddingTop = 26;
        double paddingBottom = 34;
        double chartW = w - paddingSide * 2;
        double chartH = h - paddingTop - paddingBottom;

        double maxValue = Math.max(values.stream().mapToDouble(Double::doubleValue).max().orElse(1), 1);
        double slot = chartW / values.size();
        double barWidth = Math.min(52, slot * 0.5);
        hoverPaddingSide = paddingSide;
        hoverSlot = slot;

        gc.setStroke(Color.web("#0D2B2A", 0.16));
        gc.setLineWidth(1);
        gc.setLineDashes(4, 3);
        gc.strokeLine(paddingSide, paddingTop + chartH, w - paddingSide, paddingTop + chartH);
        gc.setLineDashes(0);

        for (int i = 0; i < values.size(); i++) {
            double cx = paddingSide + slot * i + slot / 2;
            double barH = values.get(i) / maxValue * chartH;
            double top = paddingTop + (chartH - barH);
            gc.setFill(past.get(i) ? BRASS : TEAL);
            gc.fillRoundRect(cx - barWidth / 2, top, barWidth, Math.max(barH, 2), 4, 4);

            gc.setFill(VALUE_COLOR);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.BASELINE);
            gc.fillText(shortMoney(values.get(i)), cx, top - 8);

            gc.setFill(LABEL_COLOR);
            gc.fillText(yearLabels.get(i), cx, paddingTop + chartH + 16);
            gc.fillText(subLabels.get(i), cx, paddingTop + chartH + 30);
        }
    }

    private String shortMoney(double value) {
        if (value >= 1_00_00_000) return currencySymbol + String.format("%.2fCr", value / 1_00_00_000.0);
        if (value >= 1_00_000) return currencySymbol + String.format("%.2fL", value / 1_00_000.0);
        if (value >= 1_000) return currencySymbol + String.format("%.0fk", value / 1_000.0);
        return currencySymbol + String.format("%.0f", value);
    }
}
