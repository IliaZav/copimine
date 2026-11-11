package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Wave7ModelAtlasCoverageTest {
    @Test void wardenBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = Wave7WardenModel.createLayer().bakeRoot();
        String geometry = fingerprint(root);
        new Wave7WardenModel(root);
        assertEquals(geometry, fingerprint(root), "UV repair must not move the authored mesh");
        check(root, "end_rift_wave7_warden.png");
    }
    @Test void archmageBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = Wave7ArchmageModel.createLayer().bakeRoot();
        String geometry = fingerprint(root);
        new Wave7ArchmageModel(root);
        assertEquals(geometry, fingerprint(root));
        check(root, "end_rift_wave7_archmage.png");
    }
    @Test void marksmanBodyFacesHavePaintUnderTheirActualBakedUvs() throws Exception {
        var root = RiftEventSkeletonModel.getTexturedModelData(RiftEventSkeletonModel.Variant.WAVE7_MARKSMAN).bakeRoot();
        String geometry = fingerprint(root);
        new RiftEventSkeletonModel(root, RiftEventSkeletonModel.Variant.WAVE7_MARKSMAN);
        assertEquals(geometry, fingerprint(root));
        check(root, "end_rift_wave7_marksman.png");
    }
    private String fingerprint(ModelPart root) {
        var result = new StringBuilder();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            result.append(path).append(index);
            for (var polygon : cube.polygons) {
                result.append(polygon.normal());
                for (var v : polygon.vertices()) result.append(v.x()).append(':').append(v.y()).append(':').append(v.z());
            }
        });
        return result.toString();
    }
    private void check(ModelPart root, String texture) throws Exception {
        BufferedImage image = ImageIO.read(getClass().getResourceAsStream("/assets/copimineclient/textures/entity/" + texture));
        var holes = new ArrayList<String>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.matches("head|body|left_arm|right_arm|left_leg|right_leg")) return;
            for (var polygon : cube.polygons) {
                float minU = 1, minV = 1, maxU = 0, maxV = 0;
                for (var v : polygon.vertices()) {
                    minU = Math.min(minU, v.u()); maxU = Math.max(maxU, v.u());
                    minV = Math.min(minV, v.v()); maxV = Math.max(maxV, v.v());
                }
                int total = 0, painted = 0;
                for (int y = (int) Math.floor(minV * image.getHeight()); y < Math.ceil(maxV * image.getHeight()); y++)
                    for (int x = (int) Math.floor(minU * image.getWidth()); x < Math.ceil(maxU * image.getWidth()); x++) {
                        total++;
                        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                                && (image.getRGB(x, y) >>> 24) >= 128) painted++;
                    }
                if (total > 0 && painted < total * .95) holes.add(path + " face " + painted + "/" + total
                        + " UV " + minU + "," + minV + ".." + maxU + "," + maxV);
            }
        });
        assertTrue(holes.isEmpty(), texture + " has disappearing body faces: " + holes);
    }
}
