package com.spendlocker.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
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

    public DonutChart() {
        totalLabel.getStyleClass().add("title-2");
        captionLabel.getStyleClass().add("text-caption");
        centerText.getChildren().addAll(totalLabel, captionLabel);
        centerText.setAlignment(javafx.geometry.Pos.CENTER);
        setPrefSize(200, 200);
        getChildren().addAll(canvas, centerText);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((o, a, b) -> draw(lastData));
        heightProperty().addListener((o, a, b) -> draw(lastData));
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
        if (w <= 0 || h <= 0 || data == null || data.isEmpty()) return;

        double total = data.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return;

        double cx = w / 2, cy = h / 2;
        double outerR = Math.min(w, h) / 2 - 4;
        double innerR = outerR * 0.63; // matches the reference's inner58/outer92 ratio
        double strokeWidth = outerR - innerR;
        double ringRadius = (outerR + innerR) / 2;

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
