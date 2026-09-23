package com.spendlocker.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * Monthly bars with a cumulative line overlaid — the same "runway" chart language used by the
 * Android companion app and the reference design: a flat bar series plus a running-total line
 * in a contrasting accent color, with muted 0/50%/100% gridlines and short-money axis labels
 * matching the reference's own scale reading. A plain Canvas (not a BarChart+LineChart combo)
 * because JavaFX has no native dual-series combo chart, and stacking two independently-axed
 * charts never lines up pixel-perfectly.
 */
public class RunwayChart extends javafx.scene.layout.Region {

    private static final Color BAR_COLOR = Color.web("#2C7A6E");
    private static final Color LINE_COLOR = Color.web("#8A6115");
    private static final Color DOT_FILL = Color.web("#F6F7F3");
    private static final Color LABEL_COLOR = Color.web("#556661");
    private static final Color GRID_COLOR = Color.web("#0D2B2A", 0.16);

    private final Canvas canvas = new Canvas();
    private List<String> labels = List.of();
    private List<Double> values = List.of();
    private List<Color> barColors = null;
    private String currencySymbol = "";
    private double hoverPaddingLeft;
    private double hoverSlot;
    private java.util.function.IntConsumer onBarHover;
    private Runnable onHoverExit;
    private int hoveredIndex = -1;

    public RunwayChart() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((obs, o, n) -> draw());
        heightProperty().addListener((obs, o, n) -> draw());
        canvas.setOnMouseMoved(e -> handleHover(e.getX()));
        canvas.setOnMouseExited(e -> {
            hoveredIndex = -1;
            if (onHoverExit != null) onHoverExit.run();
        });
    }

    /** Fires on every month column the mouse moves over (index into the arrays passed to
     *  setData), and once more when the mouse leaves — the reference's own hover-to-read
     *  behavior: show that month's value while hovering, restore the default readout on exit. */
    public void setOnBarHover(java.util.function.IntConsumer onBarHover, Runnable onHoverExit) {
        this.onBarHover = onBarHover;
        this.onHoverExit = onHoverExit;
    }

    private void handleHover(double mouseX) {
        if (values.isEmpty() || hoverSlot <= 0) return;
        int idx = (int) ((mouseX - hoverPaddingLeft) / hoverSlot);
        if (idx < 0 || idx >= values.size()) {
            if (hoveredIndex != -1) {
                hoveredIndex = -1;
                if (onHoverExit != null) onHoverExit.run();
            }
            return;
        }
        if (idx != hoveredIndex) {
            hoveredIndex = idx;
            if (onBarHover != null) onBarHover.accept(idx);
        }
    }

    public void setData(List<String> labels, List<Double> values, String currencySymbol) {
        setData(labels, values, currencySymbol, null);
    }

    /** barColors, when given, overrides the default teal per bar (e.g. urgency coloring:
     *  red/amber/teal by days-to-maturity) — one entry per value, or null for the default. */
    public void setData(List<String> labels, List<Double> values, String currencySymbol, List<Color> barColors) {
        this.labels = labels;
        this.values = values;
        this.currencySymbol = currencySymbol;
        this.barColors = barColors;
        draw();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        if (values.isEmpty() || w <= 0 || h <= 0) return;

        double paddingLeft = 46;
        double paddingRight = 8;
        double paddingTop = 10;
        double paddingBottom = 22;
        double chartW = w - paddingLeft - paddingRight;
        double chartH = h - paddingTop - paddingBottom;

        double maxValue = Math.max(values.stream().mapToDouble(Double::doubleValue).max().orElse(1), 1);
        double slot = chartW / values.size();
        double barWidth = slot * 0.45;
        hoverPaddingLeft = paddingLeft;
        hoverSlot = slot;

        // Reference-style gridlines at 0/50%/100% with short-money axis labels.
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.CENTER);
        for (double fraction : new double[] {0, 0.5, 1.0}) {
            double y = paddingTop + chartH - fraction * chartH;
            gc.strokeLine(paddingLeft, y, w - paddingRight, y);
            gc.setFill(LABEL_COLOR);
            gc.fillText(fraction == 0 ? "0" : shortMoney(maxValue * fraction), paddingLeft - 6, y);
        }

        double running = 0;
        double[] cumValues = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            running += values.get(i);
            cumValues[i] = running;
        }
        double maxCum = Math.max(java.util.Arrays.stream(cumValues).max().orElse(1), 1);

        double[] px = new double[values.size()];
        double[] py = new double[values.size()];

        for (int i = 0; i < values.size(); i++) {
            double cx = paddingLeft + slot * i + slot / 2;
            double barH = values.get(i) / maxValue * chartH;
            double top = paddingTop + (chartH - barH);
            gc.setFill(barColors != null && i < barColors.size() ? barColors.get(i) : BAR_COLOR);
            gc.fillRoundRect(cx - barWidth / 2, top, barWidth, barH, 4, 4);

            px[i] = cx;
            py[i] = paddingTop + chartH - (cumValues[i] / maxCum * chartH);

            if (i < labels.size()) {
                gc.setFill(LABEL_COLOR);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BASELINE);
                gc.fillText(labels.get(i), cx, h - 4);
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

    /** Indian-style short money: Cr / L / k thresholds, matching the reference's axis labels. */
    private String shortMoney(double value) {
        if (value >= 1_00_00_000) return currencySymbol + String.format("%.2fCr", value / 1_00_00_000.0);
        if (value >= 1_00_000) return currencySymbol + String.format("%.2fL", value / 1_00_000.0);
        if (value >= 1_000) return currencySymbol + String.format("%.0fk", value / 1_000.0);
        return currencySymbol + String.format("%.0f", value);
    }
}
