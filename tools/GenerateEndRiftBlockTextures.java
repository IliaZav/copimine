import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Deterministic 16x16 block atlas for the real End Rift world blocks. */
public final class GenerateEndRiftBlockTextures {
    private static final Color VOID = new Color(6, 4, 17, 255);
    private static final Color OBSIDIAN = new Color(24, 12, 48, 255);
    private static final Color VIOLET = new Color(101, 25, 191, 255);
    private static final Color MAGENTA = new Color(213, 48, 247, 255);
    private static final Color LILAC = new Color(240, 145, 255, 255);
    private static final Color CYAN = new Color(90, 226, 255, 255);

    private GenerateEndRiftBlockTextures() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: GenerateEndRiftBlockTextures <output-directory>");
        }
        File output = new File(args[0]);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Could not create output directory: " + output);
        }
        write(core(), new File(output, "end_event_core.png"));
        write(charged(), new File(output, "end_event_core_charged.png"));
        write(rune(), new File(output, "end_event_rune.png"));
    }

    private static BufferedImage core() {
        BufferedImage image = filled(VOID);
        rect(image, 1, 1, 14, 14, OBSIDIAN);
        rect(image, 2, 2, 12, 12, new Color(33, 15, 69, 255));
        line(image, 1, 1, 14, 14, VIOLET);
        line(image, 14, 1, 1, 14, MAGENTA);
        line(image, 2, 13, 12, -10, new Color(49, 19, 105, 255));
        diamond(image, 8, 8, 5, MAGENTA);
        diamond(image, 8, 8, 3, LILAC);
        rect(image, 7, 7, 3, 3, VOID);
        rect(image, 8, 8, 1, 1, CYAN);
        rect(image, 4, 4, 1, 1, CYAN);
        rect(image, 11, 5, 1, 1, VIOLET);
        rect(image, 4, 11, 1, 1, MAGENTA);
        rect(image, 12, 12, 1, 1, CYAN);
        return image;
    }

    private static BufferedImage rune() {
        BufferedImage image = filled(new Color(10, 5, 25, 255));
        rect(image, 1, 1, 14, 14, new Color(27, 10, 58, 255));
        rect(image, 2, 2, 12, 12, new Color(39, 14, 79, 255));
        diamondOutline(image, 8, 8, 6, VIOLET);
        diamondOutline(image, 8, 8, 4, MAGENTA);
        line(image, 8, 3, 0, 10, LILAC);
        line(image, 3, 8, 10, 0, LILAC);
        rect(image, 7, 7, 3, 3, VOID);
        rect(image, 8, 8, 1, 1, CYAN);
        rect(image, 3, 3, 1, 1, CYAN);
        rect(image, 12, 3, 1, 1, MAGENTA);
        rect(image, 3, 12, 1, 1, MAGENTA);
        rect(image, 12, 12, 1, 1, CYAN);
        return image;
    }

    private static BufferedImage charged() {
        BufferedImage image = core();
        diamond(image, 8, 8, 4, new Color(143, 37, 235, 255));
        rect(image, 7, 7, 3, 3, LILAC);
        rect(image, 8, 8, 1, 1, CYAN);
        rect(image, 1, 7, 2, 1, CYAN);
        rect(image, 13, 8, 2, 1, CYAN);
        return image;
    }

    private static BufferedImage filled(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private static void diamond(BufferedImage image, int cx, int cy, int radius, Color color) {
        for (int y = -radius; y <= radius; y++) {
            int width = radius - Math.abs(y);
            for (int x = -width; x <= width; x++) {
                rect(image, cx + x, cy + y, 1, 1, color);
            }
        }
    }

    private static void diamondOutline(BufferedImage image, int cx, int cy, int radius, Color color) {
        for (int y = -radius; y <= radius; y++) {
            int width = radius - Math.abs(y);
            rect(image, cx - width, cy + y, 1, 1, color);
            rect(image, cx + width, cy + y, 1, 1, color);
        }
    }

    private static void line(BufferedImage image, int x, int y, int dx, int dy, Color color) {
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            rect(image, x, y, 1, 1, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int xx = x + Math.round(dx * step / (float) steps);
            int yy = y + Math.round(dy * step / (float) steps);
            rect(image, xx, yy, 1, 1, color);
        }
    }

    private static void rect(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int yy = Math.max(0, y); yy < Math.min(image.getHeight(), y + height); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(image.getWidth(), x + width); xx++) {
                image.setRGB(xx, yy, color.getRGB());
            }
        }
    }

    private static void write(BufferedImage image, File target) throws IOException {
        if (!ImageIO.write(image, "png", target)) {
            throw new IOException("PNG writer is unavailable for " + target);
        }
    }
}
