import me.copimine.endevent.domain.CreativeTestAdmissionPolicy;
import me.copimine.endevent.domain.EventPhase;

public final class CreativeTestAdmissionPolicyTest {
    public static void main(String[] args) {
        require(CreativeTestAdmissionPolicy.mayStart(
                        EventPhase.COLLECTING, true, false),
                "COLLECTING must admit a disposable local run");
        require(CreativeTestAdmissionPolicy.mayStart(
                        EventPhase.READY_FOR_PLAYERS, true, false),
                "READY_FOR_PLAYERS after restart must admit a disposable local run");
        require(!CreativeTestAdmissionPolicy.mayStart(
                        EventPhase.READY_FOR_PLAYERS, false, false),
                "an official reward roster must block the disposable run");
        require(!CreativeTestAdmissionPolicy.mayStart(
                        EventPhase.COLLECTING, true, true),
                "active official combat must block the disposable run");
        require(!CreativeTestAdmissionPolicy.mayStart(
                        EventPhase.WAVE_1, true, false),
                "combat phases must never admit the disposable run");
        require(!CreativeTestAdmissionPolicy.mayStart(
                        null, true, false),
                "null phase must fail closed");
        require(CreativeTestAdmissionPolicy.isIdlePhase(EventPhase.COLLECTING),
                "COLLECTING must be an idle phase");
        require(CreativeTestAdmissionPolicy.isIdlePhase(EventPhase.READY_FOR_PLAYERS),
                "READY_FOR_PLAYERS must be an idle phase");
        require(!CreativeTestAdmissionPolicy.isIdlePhase(EventPhase.BOSS_ACTIVE),
                "BOSS_ACTIVE must not be an idle phase");
        System.out.println("CreativeTestAdmissionPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
