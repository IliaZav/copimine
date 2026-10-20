package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/** Bounded shell and sparse interior arcs, centred on the server sphere carrier. */
public final class RitualSphereMesh {
    public static final float RADIUS = 2.35F;
    public static final int SPHERE_MODEL_DATA = 830019;
    public static final int SHIELD_MODEL_DATA = 830021;
    private static final List<Quad> QUADS = build();
    private static final List<Quad> SHIELD = buildShield();
    private RitualSphereMesh() { }
    public static List<Quad> quads() { return QUADS; }
    public static List<Quad> shieldQuads() { return SHIELD; }
    public static boolean visibleFrom(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(x*x + y*y + z*z);
    }
    /** Only an outside camera sees the existing membrane; an inside camera sees arcs. */
    public static boolean shellVisibleFrom(double x, double y, double z) {
        return visibleFrom(x, y, z) && x*x + y*y + z*z > (RADIUS + 0.12)*(RADIUS + 0.12);
    }
    /** Low-opacity centre and a readable edge, never an opaque outside wall. */
    public static int surfaceAlpha(double normalDotView) {
        if (!Double.isFinite(normalDotView)) return 0;
        double grazing = 1.0D - Math.max(0.0D, Math.min(1.0D, normalDotView));
        return (int)Math.round(65.0D + 115.0D * grazing * grazing);
    }
    /** Thin peripheral traces only, with a clear aim cone, lower HUD area and near plane. */
    public static int interiorAlpha(Quad quad, InteriorView view) {
        if (quad == null || view == null || !visibleFrom(view.x(), view.y(), view.z())) return 0;
        double forwardLength = Math.sqrt(view.forwardX()*view.forwardX()
                + view.forwardY()*view.forwardY() + view.forwardZ()*view.forwardZ());
        double upLength = Math.sqrt(view.upX()*view.upX() + view.upY()*view.upY() + view.upZ()*view.upZ());
        if (!Double.isFinite(forwardLength) || !Double.isFinite(upLength)
                || forwardLength < 1e-6 || upLength < 1e-6) return 0;
        double x = (quad.first().x()+quad.second().x()+quad.third().x()+quad.fourth().x())/4.0;
        double y = (quad.first().y()+quad.second().y()+quad.third().y()+quad.fourth().y())/4.0;
        double z = (quad.first().z()+quad.second().z()+quad.third().z()+quad.fourth().z())/4.0;
        double dx = x-view.x(), dy = y-view.y(), dz = z-view.z();
        double distance = Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (!Double.isFinite(distance) || distance < 0.35) return 0;
        double patchRadius = 0;
        for (Point p : List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
            double px = p.x()-x, py = p.y()-y, pz = p.z()-z;
            patchRadius = Math.max(patchRadius, Math.sqrt(px*px+py*py+pz*pz));
        }
        // A conservative direction bound protects the whole ribbon, including
        // the middle of each face, rather than just its centre or corner points.
        double padding = Math.min(1.0, 2*patchRadius/distance);
        double forward = (dx*view.forwardX()+dy*view.forwardY()+dz*view.forwardZ())/(distance*forwardLength);
        double up = (dx*view.upX()+dy*view.upY()+dz*view.upZ())/(distance*upLength);
        if (!Double.isFinite(padding) || !Double.isFinite(forward) || !Double.isFinite(up)
                || forward <= 0) return 0;
        double nearestForward = Math.min(1.0, forward+padding);
        double clearAim = Math.cos(Math.toRadians(12));
        double fullTrace = Math.cos(Math.toRadians(24));
        if (nearestForward >= clearAim
                || (up-padding < -0.12 && nearestForward > Math.cos(Math.toRadians(40)))) return 0;
        double aimFade = Math.min(1.0, (clearAim-nearestForward)/(clearAim-fullTrace));
        double nearFade = Math.min(1.0, (distance-0.35)/0.30);
        return (int)Math.round(64*aimFade*nearFade);
    }
    private static List<Quad> build() {
        List<Quad> result = new ArrayList<>(512);
        for (int latitude = 0; latitude < 16; latitude++) {
            for (int longitude = 0; longitude < 32; longitude++) {
                result.add(new Quad(point(latitude, longitude), point(latitude + 1, longitude),
                        point(latitude + 1, longitude + 1), point(latitude, longitude + 1)));
            }
        }
        return List.copyOf(result);
    }
    private static Point point(int latitude, int longitude) {
        double phi = -Math.PI/2 + latitude * Math.PI/16;
        double theta = longitude * Math.PI/16;
        return new Point((float)(RADIUS*Math.cos(phi)*Math.cos(theta)),
                (float)(RADIUS*Math.sin(phi)), (float)(RADIUS*Math.cos(phi)*Math.sin(theta)),
                longitude / 32.0F, 1.0F - latitude / 16.0F);
    }
    /** Short upper/side traces stay peripheral as the captive turns or looks up. */
    public static List<Quad> interiorQuads(InteriorView view) {
        if (view == null || !visibleFrom(view.x(), view.y(), view.z())
                || view.x()*view.x()+view.y()*view.y()+view.z()*view.z() >= (RADIUS-0.02)*(RADIUS-0.02)) {
            return List.of();
        }
        double forwardLength = Math.sqrt(view.forwardX()*view.forwardX()
                + view.forwardY()*view.forwardY() + view.forwardZ()*view.forwardZ());
        if (!Double.isFinite(forwardLength) || forwardLength < 1e-6) return List.of();
        double fx = view.forwardX()/forwardLength, fy = view.forwardY()/forwardLength, fz = view.forwardZ()/forwardLength;
        double dot = fx*view.upX()+fy*view.upY()+fz*view.upZ();
        double ux = view.upX()-fx*dot, uy = view.upY()-fy*dot, uz = view.upZ()-fz*dot;
        double upLength = Math.sqrt(ux*ux+uy*uy+uz*uz);
        if (!Double.isFinite(upLength) || upLength < 1e-6) return List.of();
        InteriorView normalized = new InteriorView(view.x(), view.y(), view.z(), fx, fy, fz,
                ux/upLength, uy/upLength, uz/upLength);
        List<Quad> result = new ArrayList<>(48);
        for (int segment = 0; segment < 64; segment++) {
            if (segment % 4 == 3) continue;
            double a = segment*Math.PI/64, b = (segment+0.8)*Math.PI/64;
            result.add(new Quad(interiorPoint(normalized, a, 0.984, 0, 0),
                    interiorPoint(normalized, a, 1.016, 0, 1),
                    interiorPoint(normalized, b, 1.016, 1, 1),
                    interiorPoint(normalized, b, 0.984, 1, 0)));
        }
        return List.copyOf(result);
    }
    private static Point interiorPoint(InteriorView view, double angle, double scale, float u, float v) {
        double horizontal = Math.tan(Math.toRadians(32))*Math.cos(angle)*scale;
        double vertical = Math.tan(Math.toRadians(20))*Math.sin(angle)*scale;
        double dx = view.forwardX()+(view.forwardY()*view.upZ()-view.forwardZ()*view.upY())*horizontal+view.upX()*vertical;
        double dy = view.forwardY()+(view.forwardZ()*view.upX()-view.forwardX()*view.upZ())*horizontal+view.upY()*vertical;
        double dz = view.forwardZ()+(view.forwardX()*view.upY()-view.forwardY()*view.upX())*horizontal+view.upZ()*vertical;
        double length = Math.sqrt(dx*dx+dy*dy+dz*dz);
        dx /= length; dy /= length; dz /= length;
        // Intersect the camera ray with the real world sphere. The contour can
        // follow the view without moving any vertex away from the prison shell.
        double projection = view.x()*dx+view.y()*dy+view.z()*dz;
        double distance = -projection+Math.sqrt(projection*projection+RADIUS*RADIUS
                - view.x()*view.x()-view.y()*view.y()-view.z()*view.z());
        return new Point((float)(view.x()+distance*dx), (float)(view.y()+distance*dy),
                (float)(view.z()+distance*dz), u, v);
    }
    private static List<Quad> buildShield() {
        List<Quad> result = new ArrayList<>(48);
        float[] widths = {0.12F, 0.42F, 0.60F, 0.62F, 0.62F, 0.57F, 0.40F};
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 8; col++) {
                result.add(new Quad(shieldPoint(row, col, widths), shieldPoint(row+1, col, widths),
                        shieldPoint(row+1, col+1, widths), shieldPoint(row, col+1, widths)));
            }
        }
        return List.copyOf(result);
    }
    private static Point shieldPoint(int row, int col, float[] widths) {
        float x = (col/4F - 1F)*widths[row];
        return new Point(x, -0.8F+row*(1.6F/6F), x*x*0.32F, col/8F, 1-row/6F);
    }
    public record Point(float x, float y, float z, float u, float v) { }
    public record Quad(Point first, Point second, Point third, Point fourth) { }
    public record InteriorView(double x, double y, double z, double forwardX, double forwardY, double forwardZ,
                               double upX, double upY, double upZ) { }
}
