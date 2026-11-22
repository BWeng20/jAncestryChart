package com.bw.jAncestryChart.render;

import com.bw.jAncestryChart.layout.TreeLayout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a rendered chart to a PNG file. */
public final class TreeExporter {

    private TreeExporter() {
    }

    /** Renders {@code layout} at the given scale and writes it to {@code target}; returns the image. */
    public static BufferedImage writePng(TreeLayout layout, PortraitCache portraits, double scale, boolean showPortraits,
                                         Path target) throws IOException {
        BufferedImage image = new TreeRenderer(portraits)
                .renderImage(layout, scale, new TreeRenderer.Options(showPortraits));
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("No PNG writer available");
        }
        return image;
    }
}
