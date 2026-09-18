package me.copimine.client;

import java.util.Objects;

/**
 * Immutable, allocation-light pose returned by the client-only tentacle
 * animator.  Values are local to the rig; the server never uses this class to
 * decide contact, player locking or damage.
 */
public final class EndRiftTentaclePose {
    private EndRiftTentaclePose() {
    }

    public record BoneTransform(
            float translationX,
            float translationY,
            float translationZ,
            float pitch,
            float yaw,
            float roll,
            float scaleX,
            float scaleY,
            float scaleZ) {
        public static BoneTransform identity() {
            return new BoneTransform(0.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }

        public boolean isFinite() {
            return Float.isFinite(translationX) && Float.isFinite(translationY)
                    && Float.isFinite(translationZ) && Float.isFinite(pitch)
                    && Float.isFinite(yaw) && Float.isFinite(roll)
                    && Float.isFinite(scaleX) && Float.isFinite(scaleY)
                    && Float.isFinite(scaleZ) && scaleX > 0.0F
                    && scaleY > 0.0F && scaleZ > 0.0F;
        }
    }

    /** Local point used to align server locking with the visible claw cage. */
    public record Socket(float x, float y, float z) {
        public boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
        }
    }

    /** All articulated bones are deliberately explicit: no hidden map lookup. */
    public record TentaclePose(
            BoneTransform root,
            BoneTransform base,
            BoneTransform seg_01,
            BoneTransform seg_02,
            BoneTransform seg_03,
            BoneTransform seg_04,
            BoneTransform seg_05,
            BoneTransform tip,
            BoneTransform tip_claw_1,
            BoneTransform tip_claw_2,
            BoneTransform tip_claw_3,
            BoneTransform tip_claw_4,
            Socket grab_socket,
            float rigScale) {
        public TentaclePose {
            root = Objects.requireNonNull(root, "root");
            base = Objects.requireNonNull(base, "base");
            seg_01 = Objects.requireNonNull(seg_01, "seg_01");
            seg_02 = Objects.requireNonNull(seg_02, "seg_02");
            seg_03 = Objects.requireNonNull(seg_03, "seg_03");
            seg_04 = Objects.requireNonNull(seg_04, "seg_04");
            seg_05 = Objects.requireNonNull(seg_05, "seg_05");
            tip = Objects.requireNonNull(tip, "tip");
            tip_claw_1 = Objects.requireNonNull(tip_claw_1, "tip_claw_1");
            tip_claw_2 = Objects.requireNonNull(tip_claw_2, "tip_claw_2");
            tip_claw_3 = Objects.requireNonNull(tip_claw_3, "tip_claw_3");
            tip_claw_4 = Objects.requireNonNull(tip_claw_4, "tip_claw_4");
            grab_socket = Objects.requireNonNull(grab_socket, "grab_socket");
        }

        public static TentaclePose identity() {
            BoneTransform identity = BoneTransform.identity();
            return new TentaclePose(identity, identity, identity, identity, identity,
                    identity, identity, identity, identity, identity, identity, identity,
                    new Socket(0.0F, 4.75F, 0.0F), 1.0F);
        }

        public BoneTransform transform(String bone) {
            if (bone == null) {
                return BoneTransform.identity();
            }
            return switch (bone) {
                case "root" -> root;
                case "base" -> base;
                case "seg_01" -> seg_01;
                case "seg_02" -> seg_02;
                case "seg_03" -> seg_03;
                case "seg_04" -> seg_04;
                case "seg_05" -> seg_05;
                case "tip" -> tip;
                case "tip_claw_1" -> tip_claw_1;
                case "tip_claw_2" -> tip_claw_2;
                case "tip_claw_3" -> tip_claw_3;
                case "tip_claw_4" -> tip_claw_4;
                case "grab_socket" -> BoneTransform.identity();
                default -> BoneTransform.identity();
            };
        }

        public boolean isFinite() {
            return root.isFinite() && base.isFinite() && seg_01.isFinite()
                    && seg_02.isFinite() && seg_03.isFinite() && seg_04.isFinite()
                    && seg_05.isFinite() && tip.isFinite() && tip_claw_1.isFinite()
                    && tip_claw_2.isFinite() && tip_claw_3.isFinite()
                    && tip_claw_4.isFinite() && grab_socket.isFinite()
                    && Float.isFinite(rigScale) && rigScale > 0.0F;
        }

        public float socketX() {
            return grab_socket.x();
        }

        public float socketY() {
            return grab_socket.y();
        }

        public float socketZ() {
            return grab_socket.z();
        }
    }
}
