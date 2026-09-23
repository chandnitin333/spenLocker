package com.spendlocker.ui;

import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-drawn donut (Canvas, not javafx.scene.chart.PieChart) matching the reference's own
 * inner/outer radius ratio and single-hue teal ramp, with a centered total + label.
 */
public class DonutChart extends StackPane {

    private final Canvas canvas = new Canvas();
    private final VBox centerText = new VBox(2);
    private final Label totalLabel = new Label();
    private final Label captionLabel = new Label();
    private final Tooltip tooltip = new Tooltip();

    // Hover hit-testing state, refreshed on every draw().
    private final List<double[]> segmentAngleRanges = new ArrayList<>();
    private final List<String> segmentNames = new ArrayList<>();
    private final List<Double> segmentValues = new ArrayList<>();
    private double hoverCx, hoverCy, hoverInnerR, hoverOuterR, hoverTotal;

    public DonutChart() {
        // A fixed 22px (title-2) label overflowed the inner hole and spilled onto the ring for
        // any large total (e.g. "₹1,33,66,989") — cap the width to the actual inner-circle
        // diameter (innerR is ~63% of outerR, which is itself ~half the component's own size)
        // and wrap/shrink instead of overlapping the colored segments.
        totalLabel.getStyleClass().add("donut-total");
        totalLabel.setWrapText(true);
        totalLabel.setTextAlignment(TextAlignment.CENTER);
        captionLabel.getStyleClass().add("text-caption");
        captionLabel.setWrapText(true);
        captionLabel.setTextAlignment(TextAlignment.CENTER);
        centerText.getChildren().addAll(totalLabel, captionLabel);
        centerText.setAlignment(javafx.geometry.Pos.CENTER);
        centerText.maxWidthProperty().bind(widthProperty().multiply(0.56));
        centerText.maxHeightProperty().bind(heightProperty().multiply(0.56));
        setPrefSize(200, 200);
        getChildren().addAll(canvas, centerText);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((o, a, b) -> draw(lastData));
        heightProperty().addListener((o, a, b) -> draw(lastData));
        canvas.setOnMouseMoved(e -> handleHover(e.getX(), e.getY()));
        canvas.setOnMouseExited(e -> tooltip.hide());
    }

    private void handleHover(double mx, double my) {
        if (segmentAngleRanges.isEmpty()) {
            tooltip.hide();
            return;
        }
        double dx = mx - hoverCx, dy = my - hoverCy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < hoverInnerR || dist > hoverOuterR) {
            tooltip.hide();
            return;
        }
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < 0) angle += 360;

        for (int i = 0; i < segmentAngleRanges.size(); i++) {
            double high = segmentAngleRanges.get(i)[0];
            double low = segmentAngleRanges.get(i)[1];
            if (isWithin(angle, low, high) || isWithin(angle - 360, low, high) || isWithin(angle + 360, low, high)) {
                double value = segmentValues.get(i);
                double pct = hoverTotal > 0 ? (value / hoverTotal) * 100 : 0;
                tooltip.setText(String.format("%s — %s (%.1f%%)", segmentNames.get(i),
                        com.spendlocker.util.MoneyFormat.currency(value), pct));
                Point2D screen = canvas.localToScreen(mx, my);
                if (screen != null) {
                    tooltip.show(canvas, screen.getX() + 12, screen.getY() + 12);
                }
                return;
            }
        }
        tooltip.hide();
    }

    private boolean isWithin(double a, double low, double high) {
        return a <= high && a >= low;
    }

    private LinkedHashMap<String, Double> lastData = new LinkedHashMap<>();

    public void setData(LinkedHashMap<String, Double> data, String totalText, String captionText) {
        this.lastData = data;
        totalLabel.setText(totalText);
        captionLabel.setText(captionText);
        draw(data);
    }

    private void draw(LinkedHashMap<String, Double> data) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        segmentAngleRanges.clear();
        segmentNames.clear();
        segmentValues.clear();
        if (w <= 0 || h <= 0 || data == null || data.isEmpty()) return;

        double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return;

        double cx = w / 2, cy = h / 2;
        double outerR = Math.min(w, h) / 2 - 4;
        double innerR = outerR * 0.63; // matches the reference's inner58/outer92 ratio
        double strokeWidth = outerR - innerR;
        double ringRadius = (outerR + innerR) / 2;
        hoverCx = cx;
        hoverCy = cy;
        hoverInnerR = innerR;
        hoverOuterR = outerR;
        hoverTotal = total;

        int n = data.size();
        int i = 0;
        double startAngle = 90; // 12 o'clock, matching the reference
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            double fraction = entry.getValue() / total;
            double sweep = fraction * 360;
            double gapDeg = n > 1 ? 1.5 : 0;

            gc.setStroke(shade(i, n));
            gc.setLineWidth(strokeWidth);
            gc.setLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
            gc.strokeArc(cx - ringRadius, cy - ringRadius, ringRadius * 2, ringRadius * 2,
                    startAngle - sweep, Math.max(sweep - gapDeg, 0), javafx.scene.shape.ArcType.OPEN);

            segmentAngleRanges.add(new double[] {startAngle, startAngle - sweep});
            segmentNames.add(entry.getKey());
            segmentValues.add(entry.getValue());

            startAngle -= sweep;
            i++;
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
    }

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
}
