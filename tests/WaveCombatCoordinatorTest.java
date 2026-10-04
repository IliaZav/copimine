import java.util.*;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

/** Real generation leases, group target selection and meaningful path refresh decisions. */
public final class WaveCombatCoordinatorTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    var c=new WaveCombatCoordinator(); c.begin(7);
    UUID a=new UUID(0,1),b=new UUID(0,2),d=new UUID(0,3),target=new UUID(0,4);
    var first=c.reserve(7,a,target,100,160);
    check(first!=null,"first major ability must be admitted");
    check(c.currentLease(7,100)==first,"flight must capture the exact reservation token");
    check(c.reserve(7,b,target,101,160)==null,"two elites cannot wind up simultaneously");
    check(!c.valid(first,8,102),"old generation cannot damage a target");
    c.cancelTarget(target); check(!c.valid(first,7,103),"death or quit must cancel its reservation");
    var second=c.reserve(7,b,a,104,164); check(second!=null,"target cancellation must free the slot");
    c.begin(8); check(!c.valid(second,8,105),"repeated launch invalidates queued attacks");
    var replacement=c.reserve(8,b,a,106,166); check(replacement!=null,"new wave can reserve immediately");
    c.release(second); check(c.valid(replacement,8,107),"stale release cannot cancel a new reservation");
    check(c.currentLease(7,107)==null,"old callbacks must not discover a new lease");
    check(c.reserve(8,d,a,167,227)!=null,"expired recovery must not leak its slot");
    c.remove(d); check(!c.busy(8,168),"entity cleanup releases its attack");
    check(c.reserve(7,a,b,169,220)==null,"old callback cannot reacquire a slot");
    check(c.reserve(8,a,b,170,170)==null,"zero lifetime is rejected");
    c.clear(); check(c.reserve(8,a,b,171,230)==null,"cleanup fences the controller");
    var candidates=List.of(a,b,d);
    Map<UUID,Integer> loads=new HashMap<>(); loads.put(a,6); loads.put(b,1); loads.put(d,0);
    check(WaveCombatCoordinator.chooseTarget(candidates,a,a,2,0,loads).equals(a),"hunt pursuer uses marked target");
    check(WaveCombatCoordinator.chooseTarget(candidates,a,a,2,1,loads).equals(d),"hunt blocker must select an ally");
    check(WaveCombatCoordinator.chooseTarget(List.of(a),null,a,2,1,loads).equals(a),"solo player must remain playable");
    check(WaveCombatCoordinator.chooseTarget(List.of(b,d),a,a,2,0,loads).equals(d),"dead marked target cannot remain selected");
    check(WaveCombatCoordinator.chooseTarget(candidates,a,null,5,0,loads).equals(d),"fog resumption distributes pressure");
    check(WaveCombatCoordinator.chooseTarget(candidates,b,null,5,0,null).equals(b),"absent load snapshot preserves an eligible current target");
    check(WaveCombatCoordinator.chooseTarget(candidates,null,a,2,1,null).equals(d),"absent load snapshot preserves hunter role assignment");
    check(!WaveCombatCoordinator.refreshPath(500,1000,0.1,false,true),"small movement cannot spam pathfinder");
    check(WaveCombatCoordinator.refreshPath(500,1000,9,false,true),"large destination change invalidates path");
    check(WaveCombatCoordinator.refreshPath(500,1000,0,true,true),"objective or target change invalidates path");
    check(WaveCombatCoordinator.refreshPath(1001,1000,0,false,false),"failed paths may retry at bounded deadline");
    System.out.println("WaveCombatCoordinatorTest OK");
  }
}
