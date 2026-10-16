import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Generates the small, native Minecraft entity UV sheets used by the optional
 * End Rift client visuals.  The output is intentionally deterministic: no
 * external download, runtime generation, or global vanilla replacement.
 */
public final class GenerateEndRiftMobTextures {
    private static final Color VOID = new Color(8, 5, 20, 255);
    private static final Color OBSIDIAN = new Color(20, 12, 42, 255);
    private static final Color MIDNIGHT = new Color(35, 17, 67, 255);
    private static final Color VIOLET = new Color(111, 29, 214, 255);
    private static final Color MAGENTA = new Color(214, 54, 255, 255);
    private static final Color LILAC = new Color(242, 153, 255, 255);
    private static final Color CYAN = new Color(101, 236, 255, 255);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private GenerateEndRiftMobTextures() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: GenerateEndRiftMobTextures <output-directory>");
        }
        File output = new File(args[0]);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Could not create output directory: " + output);
        }
        write(enderman(64, 32, false), new File(output, "end_rift_enderman.png"));
        write(enderman(64, 32, true), new File(output, "end_rift_elite.png"));
        write(guardian(), new File(output, "end_rift_guardian.png"));
        write(endermite(), new File(output, "end_rift_endermite.png"));
        write(shulker(), new File(output, "end_rift_shulker.png"));
    }

    private static BufferedImage enderman(int width, int height, boolean elite) {
        BufferedImage image = image(width, height);
        // Java's 64x32 humanoid UV layout: head, torso, arms, and legs.
        paintBox(image, 0, 8, 32, 8, elite);
        paintBox(image, 8, 0, 8, 8, elite);
        paintBox(image, 16, 0, 8, 8, elite);
        paintBox(image, 16, 20, 16, 12, elite);
        paintBox(image, 32, 20, 8, 12, elite);
        paintBox(image, 40, 20, 8, 12, elite);
        paintBox(image, 48, 20, 8, 12, elite);
        paintBox(image, 56, 20, 8, 12, elite);
        paintBox(image, 36, 16, 16, 4, elite);
        paintBox(image, 40, 16, 8, 4, elite);
        paintBox(image, 48, 16, 8, 4, elite);
        paintBox(image, 0, 16, 16, 4, elite);
        paintBox(image, 0, 20, 8, 12, elite);
        paintBox(image, 8, 20, 8, 12, elite);
        drawEndermanFace(image, elite);
        return image;
    }

    private static BufferedImage guardian() {
        BufferedImage image = enderman(64, 32, true);
        // The official boss gets a restrained crown/heart mark on the same
        // native Enderman UV sheet; no separate model or global override is
        // needed.
        rune(image, 21, 21, 7, 10, LILAC);
        rect(image, 23, 24, 3, 3, CYAN);
        rect(image, 10, 10, 1, 2, LILAC);
        rect(image, 13, 10, 1, 2, LILAC);
        return image;
    }

    private static void drawEndermanFace(BufferedImage image, boolean elite) {
        int glow = elite ? 1 : 0;
        rect(image, 10, 10, 1, 2, CYAN);
        rect(image, 13, 10, 1, 2, CYAN);
        rect(image, 11, 10 + glow, 1, 1, MAGENTA);
        rect(image, 14, 10 + glow, 1, 1, MAGENTA);
        rune(image, 9, 9, 7, 7, elite ? MAGENTA : VIOLET);
        rune(image, 22, 21, 5, 8, elite ? MAGENTA : VIOLET);
        rune(image, 41, 21, 5, 9, elite ? MAGENTA : VIOLET);
        rune(image, 1, 21, 5, 9, elite ? MAGENTA : VIOLET);
        if (elite) {
            rect(image, 23, 23, 2, 2, LILAC);
            rect(image, 43, 25, 1, 2, CYAN);
            rect(image, 3, 26, 1, 2, CYAN);
        }
    }

    private static BufferedImage endermite() {
        BufferedImage image = image(32, 16);
        // The vanilla endermite sheet is a compact segmented worm atlas.
        rect(image, 0, 0, 32, 16, VOID);
        for (int segment = 0; segment < 4; segment++) {
            int x = segment * 8;
            rect(image, x + 1, 1, 6, 14, segment % 2 == 0 ? OBSIDIAN : MIDNIGHT);
            rect(image, x + 2, 2, 4, 2, VIOLET);
            rect(image, x + 2, 11, 4, 2, VIOLET);
            rect(image, x + 3, 4, 2, 6, new Color(49, 22, 90, 255));
            rect(image, x + (segment % 2 == 0 ? 2 : 5), 5, 1, 3, MAGENTA);
        }
        rect(image, 2, 5, 1, 1, CYAN);
        rect(image, 5, 5, 1, 1, CYAN);
        rect(image, 10, 6, 2, 1, LILAC);
        rect(image, 18, 5, 2, 1, LILAC);
        rect(image, 26, 6, 2, 1, LILAC);
        return image;
    }

    private static BufferedImage shulker() {
        BufferedImage image = image(64, 64);
        rect(image, 0, 0, 64, 64, VOID);
        // Shulker shell panels and lid use the native 64x64 UV atlas.
        rect(image, 0, 0, 16, 16, OBSIDIAN);
        rect(image, 16, 0, 16, 16, MIDNIGHT);
        rect(image, 32, 0, 16, 16, OBSIDIAN);
        rect(image, 48, 0, 16, 16, MIDNIGHT);
        rect(image, 0, 16, 64, 16, MIDNIGHT);
        rect(image, 0, 32, 64, 16, OBSIDIAN);
        rect(image, 0, 48, 64, 16, MIDNIGHT);
        for (int x = 1; x < 64; x += 8) {
            rect(image, x, 1, 1, 14, VIOLET);
            rect(image, x + 2, 17, 2, 12, VIOLET);
            rect(image, x + 5, 34, 1, 12, MAGENTA);
            rect(image, x + 1, 50, 2, 12, VIOLET);
        }
        rune(image, 25, 19, 14, 11, MAGENTA);
        rune(image, 27, 36, 10, 9, VIOLET);
        rect(image, 30, 23, 4, 4, LILAC);
        rect(image, 31, 24, 2, 2, CYAN);
        return image;
    }

    private static BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, TRANSPARENT.getRGB());
            }
        }
        return image;
    }

    private static void paintBox(BufferedImage image, int x, int y, int width, int height, boolean elite) {
        rect(image, x, y, width, height, elite ? MIDNIGHT : OBSIDIAN);
        if (width > 2 && height > 2) {
            rect(image, x + 1, y + 1, Math.max(1, width / 4), height - 2, VOID);
            rect(image, x + width - Math.max(1, width / 5) - 1, y + 1,
                    Math.max(1, width / 5), height - 2, MIDNIGHT);
        }
    }

    private static void rune(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int i = 0; i < width; i++) {
            if (i % 3 != 1) {
                rect(image, x + i, y + (i * 2) % Math.max(1, height), 1, 1, color);
            }
        }
        rect(image, x + width / 2, y, 1, height, color);
        rect(image, x, y + height / 2, width, 1, color);
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
