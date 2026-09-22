package com.spendlocker.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Monthly bars with a cumulative line overlaid — the same "runway" chart language used by the
 * Android companion app and the reference design: a flat bar series plus a running-total line
 * in a contrasting accent color, no gridlines or legend cluttering it. A plain Canvas (not a
 * BarChart+LineChart combo) because JavaFX has no native dual-series combo chart, and stacking
 * two independently-axed charts never lines up pixel-perfectly.
 */
public class RunwayChart extends javafx.scene.layout.Region {

    private static final Color BAR_COLOR = Color.web("#2C7A6E");
    private static final Color LINE_COLOR = Color.web("#8A6115");
    private static final Color DOT_FILL = Color.web("#F6F7F3");
    private static final Color LABEL_COLOR = Color.web("#556661");

    private final Canvas canvas = new Canvas();
    private List<String> labels = List.of();
    private List<Double> values = List.of();
    private String currencySymbol = "";

    public RunwayChart() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((obs, o, n) -> draw());
        heightProperty().addListener((obs, o, n) -> draw());
    }

    public void setData(List<String> labels, List<Double> values, String currencySymbol) {
        this.labels = labels;
        this.values = values;
        this.currencySymbol = currencySymbol;
        draw();
        installTooltips();
    }

    private void installTooltips() {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < values.size() && i < labels.size(); i++) {
            if (i > 0) summary.append("   ");
            summary.append(labels.get(i)).append(": ").append(currencySymbol)
                    .append(String.format("%,.0f", values.get(i)));
        }
        Tooltip.install(this, new Tooltip(summary.toString()));
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        if (values.isEmpty() || w <= 0 || h <= 0) return;

        double paddingSide = 8;
        double paddingTop = 10;
        double paddingBottom = 22;
        double chartW = w - paddingSide * 2;
        double chartH = h - paddingTop - paddingBottom;

        double maxValue = Math.max(values.stream().mapToDouble(Double::doubleValue).max().orElse(1), 1);
        double slot = chartW / values.size();
        double barWidth = slot * 0.45;

        double running = 0;
        double[] cumValues = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            running += values.get(i);
            cumValues[i] = running;
        }
        double maxCum = Math.max(java.util.Arrays.stream(cumValues).max().orElse(1), 1);

        double[] px = new double[values.size()];
        double[] py = new double[values.size()];

        gc.setFill(BAR_COLOR);
        for (int i = 0; i < values.size(); i++) {
            double cx = paddingSide + slot * i + slot / 2;
            double barH = values.get(i) / maxValue * chartH;
            double top = paddingTop + (chartH - barH);
            gc.fillRoundRect(cx - barWidth / 2, top, barWidth, barH, 4, 4);

            px[i] = cx;
            py[i] = paddingTop + chartH - (cumValues[i] / maxCum * chartH);

            if (i < labels.size()) {
                gc.setFill(LABEL_COLOR);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BASELINE);
                gc.fillText(labels.get(i), cx, h - 4);
                gc.setFill(BAR_COLOR);
            }
        }

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(2.2);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.beginPath();
        gc.moveTo(px[0], py[0]);
        for (int i = 1; i < px.length; i++) gc.lineTo(px[i], py[i]);
        gc.stroke();

        for (int i = 0; i < px.length; i++) {
            gc.setFill(DOT_FILL);
            gc.fillOval(px[i] - 4, py[i] - 4, 8, 8);
            gc.setStroke(LINE_COLOR);
            gc.setLineWidth(1.8);
            gc.strokeOval(px[i] - 4, py[i] - 4, 8, 8);
        }
    }
}
