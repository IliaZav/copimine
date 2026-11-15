package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every face the Wave 7 rigs bake must land on painted atlas texels.
 *
 * <p>A Minecraft cuboid samples six separate rectangles of its atlas, and the
 * rigs on this branch add authored parts on top of vanilla bodies (crown, chest
 * plate, back spines, shoulder shards, core, focus orb, robe). A face whose
 * rectangle falls on transparent texels reads to the player as a hole in the
 * model, which is exactly the bug the generator rewrite fixed.</p>
 *
 * <p>This test used to be unable to fail. Two reasons, both now closed:</p>
 * <ul>
 *   <li>it filtered parts by the bare names {@code head|body|left_arm|
 *       right_arm|left_leg|right_leg}, so every authored addition was skipped
 *       by construction -- the parts most likely to be mis-UVed were the only
 *       ones never examined;</li>
 *   <li>its geometry fingerprint omitted {@code u}/{@code v}, so it could not
 *       tell an untouched rig from one whose faces had all been moved onto
 *       somebody else's texels.</li>
 * </ul>
 *
 * <p>The only faces excluded are those on a {@code rune} or {@code sigil} path,
 * because {@link EndRiftAtlasUvLayout#fit} deliberately leaves those
 * transparent -- that is the authored intent, not an oversight.</p>
 */
class Wave7ModelAtlasCoverageTest {
    /** Mirrors the single exception in {@link EndRiftAtlasUvLayout#fit}. */
    private static boolean isIntentionallyTransparent(String path) {
        return path.contains("rune") || path.contains("sigil");
    }

    @Test void wardenBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = Wave7WardenModel.createLayer().bakeRoot();
        var geometry = geometry(root);
        var uv = uv(root);
        new Wave7WardenModel(root);
        assertSameMesh(geometry, uv, root, "end_rift_wave7_warden.png");
        int faces = check(root, "end_rift_wave7_warden.png");
        assertTrue(faces >= 30, "the Warden rig baked only " + faces + " checked faces");
    }

    @Test void archmageBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = Wave7ArchmageModel.createLayer().bakeRoot();
        var geometry = geometry(root);
        var uv = uv(root);
        new Wave7ArchmageModel(root);
        assertSameMesh(geometry, uv, root, "end_rift_wave7_archmage.png");
        int faces = check(root, "end_rift_wave7_archmage.png");
        assertTrue(faces >= 20, "the Archmage rig baked only " + faces + " checked faces");
    }

    @Test void marksmanBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = RiftEventSkeletonModel
                .getTexturedModelData(RiftEventSkeletonModel.Variant.WAVE7_MARKSMAN).bakeRoot();
        var geometry = geometry(root);
        var uv = uv(root);
        new RiftEventSkeletonModel(root, RiftEventSkeletonModel.Variant.WAVE7_MARKSMAN);
        assertSameMesh(geometry, uv, root, "end_rift_wave7_marksman.png");
        int faces = check(root, "end_rift_wave7_marksman.png");
        assertTrue(faces >= 12, "the Marksman rig baked only " + faces + " checked faces");
    }

    /**
     * The constructor runs {@link EndRiftAtlasUvLayout#fit}, which repacks any
     * face painted below 95 %. All three atlases are fully opaque, so there is
     * nothing to repack: neither a vertex position nor a texture coordinate may
     * change. A difference here means either the atlas lost its material or a
     * face was silently moved onto another part's texels -- both are visible
     * defects in game, and neither offsets the other.
     */
    private void assertSameMesh(String geometry, String uv, ModelPart root, String texture) {
        assertEquals(geometry, geometry(root),
                texture + ": UV repair moved the authored mesh");
        assertEquals(uv, uv(root),
                texture + ": a face was repacked, so the atlas is no longer fully painted "
                        + "under the authored rig -- check the generator, not the rig");
    }

    /** Positions and normals only, so a texture repair cannot hide a moved vertex. */
    private String geometry(ModelPart root) {
        var result = new StringBuilder();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            result.append(path).append('#').append(index);
            for (var polygon : cube.polygons) {
                result.append(polygon.normal());
                for (var v : polygon.vertices()) {
                    result.append(v.x()).append(':').append(v.y()).append(':').append(v.z());
                }
            }
        });
        return result.toString();
    }

    /** Texture coordinates only, so a repack cannot hide behind unchanged vertices. */
    private String uv(ModelPart root) {
        var result = new StringBuilder();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            result.append(path).append('#').append(index);
            for (var polygon : cube.polygons) {
                for (var v : polygon.vertices()) result.append(v.u()).append(':').append(v.v());
            }
        });
        return result.toString();
    }

    /**
     * Visits every baked face except the intentionally transparent rune/sigil
     * planes, and requires its whole rectangle to be painted.
     *
     * @return how many faces were checked, so a rig that stops baking cubes
     *         cannot make this pass by examining nothing.
     */
    private int check(ModelPart root, String texture) throws Exception {
        BufferedImage image = ImageIO.read(getClass()
                .getResourceAsStream("/assets/copimineclient/textures/entity/" + texture));
        assertNotNull(image, "missing atlas " + texture);
        var holes = new ArrayList<String>();
        // A part whose every face is zero-area draws nothing at all, which is a
        // hole in the model even though no single face can be blamed for it.
        var drawableFaces = new java.util.LinkedHashMap<String, Integer>();
        var partsWithPaint = new java.util.HashSet<String>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            if (isIntentionallyTransparent(path)) return;
            for (var polygon : cube.polygons) {
                float minU = Float.POSITIVE_INFINITY, minV = minU;
                float maxU = Float.NEGATIVE_INFINITY, maxV = maxU;
                for (var v : polygon.vertices()) {
                    minU = Math.min(minU, v.u()); maxU = Math.max(maxU, v.u());
                    minV = Math.min(minV, v.v()); maxV = Math.max(maxV, v.v());
                }
                // Vanilla zero-depth boxes -- the Warden's tendrils and ribcage
                // are authored this way -- carry four edge faces whose UV
                // rectangle collapses to a line. Those quads have no area, so
                // they draw nothing and cannot be a visible hole; the two
                // full-size faces are still checked below.
                if (maxU <= minU || maxV <= minV) continue;
                String where = path + " face UV " + minU + "," + minV + ".." + maxU + "," + maxV;
                // A coordinate outside the atlas cannot sample paint at all;
                // report it by name rather than letting it clamp into a
                // misleadingly full rectangle.
                if (minU < 0 || minV < 0 || maxU > 1 || maxV > 1) {
                    holes.add(where + " lies outside the atlas");
                    continue;
                }
                int x0 = Math.max(0, (int) Math.floor(minU * image.getWidth()));
                int x1 = Math.min(image.getWidth(), (int) Math.ceil(maxU * image.getWidth()));
                int y0 = Math.max(0, (int) Math.floor(minV * image.getHeight()));
                int y1 = Math.min(image.getHeight(), (int) Math.ceil(maxV * image.getHeight()));
                int total = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
                if (total == 0) {
                    holes.add(where + " covers no texel at all");
                    continue;
                }
                drawableFaces.merge(path, 1, Integer::sum);
                int painted = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        if ((image.getRGB(x, y) >>> 24) >= 128) painted++;
                    }
                }
                if (painted < total * 0.95) {
                    holes.add(where + " painted only " + painted + "/" + total);
                } else {
                    partsWithPaint.add(path);
                }
            }
        });
        for (String part : drawableFaces.keySet()) {
            if (!partsWithPaint.contains(part)) {
                holes.add(part + " has no painted face, so the part is invisible");
            }
        }
        assertTrue(holes.isEmpty(), texture + " has faces with no paint under them: " + holes);
        int checked = 0;
        for (int count : drawableFaces.values()) checked += count;
        return checked;
    }
}