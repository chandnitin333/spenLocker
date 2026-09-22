package com.spendlocker.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/**
 * Days-until-due as a proportional ring — mirrors the reference design's SVG countdown rings
 * exactly: radius 12 in a 30x30 box, 3.5px stroke, arc swept clockwise from the top starting at
 * 4% of the circle so a same-day item still shows a sliver, full circle at 365+ days out.
 */
public class CountdownRing extends StackPane {

    private static final double RADIUS = 12;

    public CountdownRing(int daysLeft, Color color) {
        Circle track = new Circle(RADIUS);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(Color.web("#0D2B2A", 0.09));
        track.setStrokeWidth(3.5);

        double fraction = Math.max(0.04, Math.min(1, daysLeft / 365.0));
        Arc arc = new Arc(0, 0, RADIUS, RADIUS, 90, -360 * fraction);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(color);
        arc.setStrokeWidth(3.5);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        Label label = new Label(daysLeft <= 0 ? "!" : String.valueOf(daysLeft));
        label.setStyle("-fx-font-weight: 700; -fx-font-size: 10px;");
        label.setTextFill(color);

        setPrefSize(30, 30);
        setMaxSize(30, 30);
        setMinSize(30, 30);
        getChildren().addAll(track, arc, label);
    }
}
