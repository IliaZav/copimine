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
            BoneTransform seg_06,
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
            seg_06 = Objects.requireNonNull(seg_06, "seg_06");
            tip = Objects.requireNonNull(tip, "tip");
            tip_claw_1 = Objects.requireNonNull(tip_claw_1, "tip_claw_1");
            tip_claw_2 = Objects.requireNonNull(tip_claw_2, "tip_claw_2");
            tip_claw_3 = Objects.requireNonNull(tip_claw_3, "tip_claw_3");
            tip_claw_4 = Objects.requireNonNull(tip_claw_4, "tip_claw_4");
            grab_socket = Objects.requireNonNull(grab_socket, "grab_socket");
        }

        public static TentaclePose identity() {
            BoneTransform identity = BoneTransform.identity();
            return new TentaclePose(identity, identity, identity, identity, identity, identity,
                    identity, identity, identity, identity, identity, identity, identity,
                    new Socket(0.0F, 6.3125F, 0.0F), 1.0F);
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
                case "seg_06" -> seg_06;
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
                    && seg_05.isFinite() && seg_06.isFinite() && tip.isFinite()
                    && tip_claw_1.isFinite()
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

        /** Blend a render-only transition without changing server marker timing. */
        public static TentaclePose lerp(TentaclePose from, TentaclePose to, float amount) {
            TentaclePose start = from == null ? identity() : from;
            TentaclePose end = to == null ? identity() : to;
            float t = Float.isFinite(amount) ? Math.max(0.0F, Math.min(1.0F, amount)) : 1.0F;
            return new TentaclePose(
                    blend(start.root(), end.root(), t),
                    blend(start.base(), end.base(), t),
                    blend(start.seg_01(), end.seg_01(), t),
                    blend(start.seg_02(), end.seg_02(), t),
                    blend(start.seg_03(), end.seg_03(), t),
                    blend(start.seg_04(), end.seg_04(), t),
                    blend(start.seg_05(), end.seg_05(), t),
                    blend(start.seg_06(), end.seg_06(), t),
                    blend(start.tip(), end.tip(), t),
                    blend(start.tip_claw_1(), end.tip_claw_1(), t),
                    blend(start.tip_claw_2(), end.tip_claw_2(), t),
                    blend(start.tip_claw_3(), end.tip_claw_3(), t),
                    blend(start.tip_claw_4(), end.tip_claw_4(), t),
                    new Socket(mix(start.socketX(), end.socketX(), t),
                            mix(start.socketY(), end.socketY(), t),
                            mix(start.socketZ(), end.socketZ(), t)),
                    mix(start.rigScale(), end.rigScale(), t));
        }

        private static BoneTransform blend(BoneTransform from, BoneTransform to, float t) {
            return new BoneTransform(
                    mix(from.translationX(), to.translationX(), t),
                    mix(from.translationY(), to.translationY(), t),
                    mix(from.translationZ(), to.translationZ(), t),
                    mixAngle(from.pitch(), to.pitch(), t),
                    mixAngle(from.yaw(), to.yaw(), t),
                    mixAngle(from.roll(), to.roll(), t),
                    mix(from.scaleX(), to.scaleX(), t),
                    mix(from.scaleY(), to.scaleY(), t),
                    mix(from.scaleZ(), to.scaleZ(), t));
        }

        private static float mixAngle(float from, float to, float amount) {
            float fullTurn = (float) (Math.PI * 2.0D);
            float delta = (to - from) % fullTurn;
            if (delta > Math.PI) {
                delta -= fullTurn;
            } else if (delta < -Math.PI) {
                delta += fullTurn;
            }
            return from + delta * amount;
        }

        private static float mix(float from, float to, float amount) {
            return from + (to - from) * amount;
        }
    }
}
