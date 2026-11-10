package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/** Three rolling, depth-tested smoke layers. Visual only: no entities or collision. */
public final class BlackFogChamberMesh {
    public record Point(float x, float y, float z, int color) {}
    public record Quad(Point a, Point b, Point c, Point d) {}
    private BlackFogChamberMesh() {}

    public static List<Quad> build(float minX, float minZ, float maxX, float maxZ,
                                   float floor, float strength, double seconds) {
        if (!(maxX > minX && maxZ > minZ) || maxX - minX > 3 || maxZ - minZ > 3
                || !Double.isFinite(seconds) || !Float.isFinite(floor) || strength <= 0) return List.of();
        List<Quad> quads = new ArrayList<>();
        int acrossX = Math.min(8, Math.max(2, (int) Math.ceil((maxX-minX) * 2)));
        int acrossZ = Math.min(8, Math.max(2, (int) Math.ceil((maxZ-minZ) * 2)));
        for (int layer = 0; layer < 3; layer++) {
            for (int side = 0; side < 5; side++) {
                int uCount = side < 2 || side == 4 ? acrossX : acrossZ;
                int vCount = side == 4 ? acrossZ : 8;
                for (int u = 0; u < uCount; u++) for (int v = 0; v < vCount; v++) {
                    Point a = point(side, layer, (float)u/uCount, (float)v/vCount, minX,minZ,maxX,maxZ,floor,strength,seconds);
                    Point b = point(side, layer, (float)(u+1)/uCount, (float)v/vCount, minX,minZ,maxX,maxZ,floor,strength,seconds);
                    Point c = point(side, layer, (float)(u+1)/uCount, (float)(v+1)/vCount, minX,minZ,maxX,maxZ,floor,strength,seconds);
                    Point d = point(side, layer, (float)u/uCount, (float)(v+1)/vCount, minX,minZ,maxX,maxZ,floor,strength,seconds);
                    quads.add(new Quad(a,b,c,d));
                }
            }
        }
        return List.copyOf(quads);
    }

    private static Point point(int side, int layer, float u, float v, float minX, float minZ,
                               float maxX, float maxZ, float floor, float strength, double seconds) {
        float x, y, z;
        float expansion = 0.04F + layer * 0.18F;
        if (side == 4) {
            x = minX - 0.6F + (maxX-minX+1.2F)*u;
            z = minZ - 0.6F + (maxZ-minZ+1.2F)*v;
            y = floor + BlackFogVolumePolicy.CHAMBER_HEIGHT + expansion;
        } else {
            x = side < 2 ? minX+(maxX-minX)*u : (side == 2 ? minX : maxX);
            z = side >= 2 ? minZ+(maxZ-minZ)*u : (side == 0 ? minZ : maxZ);
            y = floor - 0.15F + (BlackFogVolumePolicy.FOG_HEIGHT+0.15F)*v;
        }
        double billow = 0.5 + 0.25*Math.sin(x*2.2+z*1.4+y*1.7+seconds*.5)
                + 0.25*Math.sin(x*.9-z*2.1+y*2.4-seconds*.35);
        float displacement = expansion + (float)billow * 0.12F;
        if (side == 4) y += (float)billow * 0.12F;
        else {
            double dx=x-(minX+maxX)*.5, dz=z-(minZ+maxZ)*.5;
            double length=Math.max(.001,Math.hypot(dx,dz));
            x += (float)(dx/length)*displacement; z += (float)(dz/length)*displacement;
        }
        int alpha=Math.min(255,Math.max(0,Math.round((250-layer*8)*Math.min(1,strength))));
        int shade=2+(int)(billow*5);
        return new Point(x,y,z,(alpha<<24)|(shade<<16)|((shade+1)<<8)|(shade+3));
    }
}
