package com.bw.jAncestryChart.render;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

public class TrunkShapeGenerator {

    public static Path2D createTrunkShape(List<Point2D> spine, double baseWidth, double tipWidth) {
        int n = spine.size();
        if (n < 2) return new Path2D.Double();

        Point2D[] left = new Point2D[n];
        Point2D[] right = new Point2D[n];

        for (int i = 0; i < n; i++) {
            Point2D curr = spine.get(i);
            Point2D prev = (i > 0) ? spine.get(i - 1) : curr;
            Point2D next = (i < n - 1) ? spine.get(i + 1) : curr;

            // Tangent vector
            double tx = next.getX() - prev.getX();
            double ty = next.getY() - prev.getY();
            double len = Math.hypot(tx, ty);
            if (len == 0) {
                tx = 0;
                ty = -1; // fallback upward
            } else {
                tx /= len;
                ty /= len;
            }

            // Perpendicular normal (-ty, tx)
            double nx = -ty;
            double ny = tx;

            // Taper width along spine (t goes from 0.0 at base to 1.0 at tip)
            double t = (double) i / (n - 1);
            double halfWidth = 0.5 * (baseWidth * Math.pow(1.0 - t, 0.85) + tipWidth * t);

            // Optional root flare near the bottom (t < 0.15)
            if (t < 0.15) {
                double flare = Math.pow((0.15 - t) / 0.15, 2) * (baseWidth * 0.25);
                halfWidth += flare;
            }

            left[i] = new Point2D.Double(curr.getX() + nx * halfWidth, curr.getY() + ny * halfWidth);
            right[i] = new Point2D.Double(curr.getX() - nx * halfWidth, curr.getY() - ny * halfWidth);
        }

        // Build closed outer polygon
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(left[0].getX(), left[0].getY());

        // Go up the left side
        for (int i = 1; i < n; i++) {
            shape.lineTo(left[i].getX(), left[i].getY());
        }

        // Go down the right side
        for (int i = n - 1; i >= 0; i--) {
            shape.lineTo(right[i].getX(), right[i].getY());
        }

        shape.closePath();
        return shape;
    }
}
