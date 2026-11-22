package com.bw.jAncestryChart.render;

import com.bw.jAncestryChart.model.MediaReference;
import com.bw.jAncestryChart.model.Person;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, crops and shrinks portrait images once. Formats that {@link ImageIO} cannot read
 * (for example HEIC) are remembered as missing and drawn as a placeholder.
 */
public final class PortraitCache {

    private static final int MAX_SIZE = 192;

    private final Map<MediaReference, Optional<BufferedImage>> images = new ConcurrentHashMap<>();

    /** The prepared image, or {@code null} if there is none or it cannot be read. Loads on first use. */
    public BufferedImage get(MediaReference ref) {
        if (ref == null) {
            return null;
        }
        return images.computeIfAbsent(ref, PortraitCache::load).orElse(null);
    }

    /** Loads all portraits of the given people (call this off the event thread). */
    public void preload(Collection<Person> persons) {
        for (Person p : persons) {
            get(p.portrait());
        }
    }

    public void clear() {
        images.clear();
    }

    private static Optional<BufferedImage> load(MediaReference ref) {
        try {
            BufferedImage source = ImageIO.read(ref.file().toFile());
            if (source == null) {
                return Optional.empty();
            }
            return Optional.of(shrink(crop(source, ref.crop()), MAX_SIZE));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static BufferedImage crop(BufferedImage image, Rectangle crop) {
        if (crop == null) {
            return image;
        }
        int x = Math.max(0, Math.min(crop.x, image.getWidth() - 1));
        int y = Math.max(0, Math.min(crop.y, image.getHeight() - 1));
        int w = crop.width <= 0 ? image.getWidth() - x : Math.min(crop.width, image.getWidth() - x);
        int h = crop.height <= 0 ? image.getHeight() - y : Math.min(crop.height, image.getHeight() - y);
        return w < 2 || h < 2 ? image : image.getSubimage(x, y, w, h);
    }

    /** Scales down in halving steps for a smooth result. */
    static BufferedImage shrink(BufferedImage image, int maxSize) {
        int w = image.getWidth();
        int h = image.getHeight();
        double factor = Math.min(1.0, maxSize / (double) Math.max(w, h));
        int targetW = Math.max(1, (int) Math.round(w * factor));
        int targetH = Math.max(1, (int) Math.round(h * factor));
        BufferedImage current = image;
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2;
            h /= 2;
            current = resize(current, w, h);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
