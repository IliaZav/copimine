package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Renders the exact face stream compiled from the supplied guardian asset. */
public final class ChameleonGuardianRenderer {
    private static final float MODEL_UNITS_PER_PIXEL = 1.0F / 16.0F;
    private static final float TEXTURE_UNITS_PER_PIXEL = 1.0F / UserEndBossModelData.TEXTURE_WIDTH;

    private ChameleonGuardianRenderer() {
    }

    public static void submit(OrderedSubmitNodeCollector collector, PoseStack matrices, RenderType renderType,
                              int light, int overlay, int color, RiftGuardianModel guardian) {
        // The collector only copies the matrix; it stores this lambda and runs it
        // at draw time (SubmitNodeCollection.submitCustomGeometry ->
        // CustomFeatureRenderer$Submit). One model instance is shared by every
        // entity a renderer type draws - all echo decoys are Pillagers on the
        // same renderer - so reading guardian.currentPose() inside the lambda
        // would give every decoy the pose of whichever body was submitted last.
        // Capture the immutable pose now, per submission.
        ChameleonGuardianGeometry.GuardianPose pose = guardian.currentPose();
        collector.submitCustomGeometry(matrices, renderType, (transform, buffer) ->
                emitAll(ChameleonGuardianGeometry.load().faces(pose), vertex -> buffer
                        .addVertex(transform,
                                vertex.x() * MODEL_UNITS_PER_PIXEL,
                                renderModelY(vertex.y()),
                                vertex.z() * MODEL_UNITS_PER_PIXEL)
                        .setColor(color)
                        .setUv(vertex.u() * TEXTURE_UNITS_PER_PIXEL, vertex.v() * TEXTURE_UNITS_PER_PIXEL)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(transform, vertex.normalX(), vertex.normalY(), vertex.normalZ())));
    }

    static void render(PoseStack matrices, VertexConsumer buffer, int light, int overlay, int color,
                       ChameleonGuardianGeometry.GuardianPose pose) {
        PoseStack.Pose entry = matrices.last();
        emitAll(ChameleonGuardianGeometry.load().faces(pose), vertex -> buffer
                .addVertex(entry,
                        vertex.x() * MODEL_UNITS_PER_PIXEL,
                        renderModelY(vertex.y()),
                        vertex.z() * MODEL_UNITS_PER_PIXEL)
                .setColor(color)
                .setUv(vertex.u() * TEXTURE_UNITS_PER_PIXEL, vertex.v() * TEXTURE_UNITS_PER_PIXEL)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(entry, vertex.normalX(), vertex.normalY(), vertex.normalZ()));
    }

    static float renderModelY(float importedY) {
        return importedY * MODEL_UNITS_PER_PIXEL
                + EndRiftBossWorldScalePolicy.modelOriginCorrectionY();
    }

    static void emit(ChameleonGuardianGeometry.Face face, VertexSink sink) {
        ChameleonGuardianGeometry.Vertex normal = face.normal();
        for (ChameleonGuardianGeometry.Vertex vertex : face.vertices()) {
            sink.vertex(new EmittedVertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v(),
                    normal.x(), normal.y(), normal.z()));
        }
    }

    static void emitAll(List<ChameleonGuardianGeometry.Face> faces, VertexSink sink) {
        for (ChameleonGuardianGeometry.Face face : faces) {
            emit(face, sink);
        }
    }

    @FunctionalInterface
    interface VertexSink {
        void vertex(EmittedVertex vertex);
    }

    record EmittedVertex(float x, float y, float z, float u, float v,
                         float normalX, float normalY, float normalZ) {
    }
}
