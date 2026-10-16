package me.copimine.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

/** Renders the exact face stream compiled from the supplied guardian asset. */
final class ChameleonGuardianRenderer {
    private static final float MODEL_UNITS_PER_PIXEL = 1.0F / 16.0F;
    private static final float TEXTURE_UNITS_PER_PIXEL = 1.0F / UserEndBossModelData.TEXTURE_WIDTH;

    private ChameleonGuardianRenderer() {
    }

    static void render(MatrixStack matrices, VertexConsumer buffer, int light, int overlay, int color,
                       ChameleonGuardianGeometry.GuardianPose pose) {
        MatrixStack.Entry entry = matrices.peek();
        emitAll(ChameleonGuardianGeometry.load().faces(pose), vertex -> buffer
                .vertex(entry,
                        vertex.x() * MODEL_UNITS_PER_PIXEL,
                        renderModelY(vertex.y()),
                        vertex.z() * MODEL_UNITS_PER_PIXEL)
                .color(color)
                .texture(vertex.u() * TEXTURE_UNITS_PER_PIXEL, vertex.v() * TEXTURE_UNITS_PER_PIXEL)
                .overlay(overlay)
                .light(light)
                .normal(entry, vertex.normalX(), vertex.normalY(), vertex.normalZ()));
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
