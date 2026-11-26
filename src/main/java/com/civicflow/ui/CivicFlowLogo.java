package com.civicflow.ui;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.*;
import javafx.embed.swing.SwingFXUtils;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

/**
 * Original vector-style CIVICFLOW brand logo and matching desktop application icon.
 * Does not imitate any real government emblem or election commission seal.
 */
public final class CivicFlowLogo {

    private CivicFlowLogo() {
    }

    /**
     * Creates a scalable vector JavaFX Node depicting the CIVICFLOW emblem:
     * a rounded civic tile with a flowing ballot slot and precision checkmark ribbon.
     */
    public static Node createLogoMark(double size) {
        StackPane container = new StackPane();
        container.setMinSize(size, size);
        container.setPrefSize(size, size);
        container.setMaxSize(size, size);

        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.28);
        bg.setArcHeight(size * 0.28);
        bg.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2855D9")),
                new Stop(1, Color.web("#17A6A1"))));

        // Subtle geometric ballot box base line
        Rectangle slot = new Rectangle(size * 0.56, size * 0.09);
        slot.setArcWidth(size * 0.05);
        slot.setArcHeight(size * 0.05);
        slot.setFill(Color.web("#FFFFFF", 0.30));
        slot.setTranslateY(size * 0.22);

        // Crisp flowing checkmark / civic flow ribbon
        Polyline check = new Polyline(
                size * 0.24, size * 0.48,
                size * 0.42, size * 0.65,
                size * 0.76, size * 0.30
        );
        check.setStroke(Color.WHITE);
        check.setStrokeWidth(Math.max(2.2, size * 0.11));
        check.setStrokeLineCap(StrokeLineCap.ROUND);
        check.setStrokeLineJoin(StrokeLineJoin.ROUND);
        check.setFill(Color.TRANSPARENT);

        javafx.scene.Group vectorGroup = new javafx.scene.Group(bg, check);
        // Position slot centered near bottom
        slot.setX(size * 0.22);
        slot.setY(size * 0.72);
        slot.setTranslateY(0);
        vectorGroup.getChildren().add(1, slot);

        container.getChildren().add(vectorGroup);
        return container;
    }

    /**
     * Generates a high-DPI 64x64 application window icon matching the CIVICFLOW vector mark.
     */
    public static Image createAppIcon() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setPaint(new GradientPaint(0, 0, new java.awt.Color(40, 85, 217), s, s, new java.awt.Color(23, 166, 161)));
        g.fillRoundRect(2, 2, s - 4, s - 4, 18, 18);

        g.setColor(new java.awt.Color(255, 255, 255, 85));
        g.fillRoundRect(14, 46, 36, 6, 4, 4);

        g.setColor(java.awt.Color.WHITE);
        g.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath check = new GeneralPath();
        check.moveTo(16, 31);
        check.lineTo(27, 41);
        check.lineTo(48, 19);
        g.draw(check);

        g.dispose();
        return SwingFXUtils.toFXImage(img, null);
    }
}
