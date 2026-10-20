import java.util.*;
import me.copimine.endevent.domain.WaveTwoEntityRecoveryPolicy;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

/** Real generation leases, group target selection and meaningful path refresh decisions. */
public final class WaveCombatCoordinatorTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args){
    testMeleeAttackAdmissionScalesWithPlayersAndReleasesSafely();
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
    check(WaveCombatCoordinator.chooseTarget(List.of(a,b),a,null,5,0,Map.of(a,3,b,2)).equals(a),
        "a squad must retain its live target while the load difference is only one fighter");
    check(WaveCombatCoordinator.chooseTarget(candidates,b,null,5,0,null).equals(b),"absent load snapshot preserves an eligible current target");
    check(WaveCombatCoordinator.chooseTarget(candidates,null,a,2,1,null).equals(d),"absent load snapshot preserves hunter role assignment");
    check(!WaveCombatCoordinator.refreshPath(500,1000,0.1,false,true),"small movement cannot spam pathfinder");
    check(WaveCombatCoordinator.refreshPath(500,1000,9,false,true),"large destination change invalidates path");
    check(WaveCombatCoordinator.refreshPath(500,1000,0,true,true),"objective or target change invalidates path");
    check(WaveCombatCoordinator.refreshPath(1001,1000,0,false,false),"failed paths may retry at bounded deadline");
    var tenPlayers=new ArrayList<UUID>();for(int i=10;i<20;i++)tenPlayers.add(new UUID(0,i));
    var tenLoads=new HashMap<UUID,Integer>();
    for(int i=0;i<40;i++){
      UUID assigned=WaveCombatCoordinator.chooseTarget(tenPlayers,null,null,5,i,tenLoads);
      check(assigned!=null,"every available fighter must receive a player assignment");
      tenLoads.merge(assigned,1,Integer::sum);
    }
    int minimum=tenLoads.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    int maximum=tenLoads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    check(tenLoads.size()==10&&maximum-minimum<=1,
        "ten-player squads must spread all active fighters without a reserve or duplicate assignment");
    for (int playerCount : List.of(2,5,6,10,20)) {
      var players = new ArrayList<UUID>();
      for (int i=0;i<playerCount;i++) players.add(new UUID(1,i));
      var fighters = new ArrayList<UUID>();
      for (int i=0;i<56;i++) fighters.add(new UUID(2,i));
      var plan = WaveCombatCoordinator.planSquads(fighters,players,Map.of(),5,null);
      check(plan.targets().size()==56,"every live fighter receives one squad assignment");
      check(plan.squads().size()==12,"56 fighters form bounded 3-5 member squads");
      for (var squad : plan.squads())
        check(squad.fighters().size()>=3&&squad.fighters().size()<=5,
          "large-wave squad size remains between three and five");
      var fighterLoads=new HashMap<UUID,Integer>();
      plan.targets().forEach((fighter,player)->fighterLoads.merge(player,1,Integer::sum));
      int loadMin=fighterLoads.values().stream().mapToInt(Integer::intValue).min().orElse(0);
      int loadMax=fighterLoads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      check(fighterLoads.size()==Math.min(playerCount,plan.squads().size())&&loadMax-loadMin<=5,
        "squads distribute pressure across available player groups without target churn");
      if (playerCount == 2)
        check(loadMax > 2,
          "assigned target load is squad routing and must not be mistaken for concurrent attacks");
      check(plan.squads().stream().allMatch(s->s.fighters().stream()
              .allMatch(f->plan.targets().get(f).equals(s.target()))),
          "squad members keep one shared target");
      var retained=WaveCombatCoordinator.planSquads(fighters,players,plan.targets(),5,null);
      check(retained.targets().equals(plan.targets()),"valid squad assignments stay stable on the next tick");
    }
    for (int playerCount : List.of(2,5,6,10,20)) {
      var attacks = new WaveCombatCoordinator();
      long attackGeneration = 6000L + playerCount;
      attacks.begin(attackGeneration);
      UUID firstOwner = new UUID(6,1), secondOwner = new UUID(6,2), victim = new UUID(6,3);
      var firstWindup = attacks.reserve(attackGeneration, firstOwner, victim, 10, 70);
      check(firstWindup != null, playerCount + " players permit the first major-ability wind-up");
      check(attacks.reserve(attackGeneration, secondOwner, victim, 11, 71) == null,
        playerCount + " players cannot overlap a second elite wind-up");
      attacks.release(firstWindup);
      check(attacks.reserve(attackGeneration, secondOwner, victim, 12, 72) != null,
        playerCount + " players admit a queued wind-up after the prior lease releases");
    }
    var markedPlayers=List.of(new UUID(3,1),new UUID(3,2),new UUID(3,3),new UUID(3,4));
    var markedPlan=WaveCombatCoordinator.planSquads(
      List.of(new UUID(4,1),new UUID(4,2),new UUID(4,3),new UUID(4,4),new UUID(4,5),new UUID(4,6)),
      markedPlayers,Map.of(),2,markedPlayers.get(0));
    long markedSquads=markedPlan.squads().stream().filter(s->s.target().equals(markedPlayers.get(0))).count();
    check(markedSquads==1&&markedPlan.squads().get(0).target().equals(markedPlayers.get(0))
            &&markedPlan.squads().subList(1,markedPlan.squads().size()).stream()
            .noneMatch(s->s.target().equals(markedPlayers.get(0))),
        "Wave 2 assigns one focus squad to the mark and excludes the mark from other squad pools");
    testPersistentSquadCoordinator();
    System.out.println("WaveCombatCoordinatorTest OK");
  }

  static void testMeleeAttackAdmissionScalesWithPlayersAndReleasesSafely() {
    check(WaveCombatCoordinator.maxMeleeAttackersPerPlayer(0) == 0,
        "no eligible players means no direct melee admissions");
    for (int playerCount : List.of(2, 5, 6, 10, 20)) {
      var attacks = new WaveCombatCoordinator();
      long attackGeneration = 5600L + playerCount;
      attacks.begin(attackGeneration);
      var players = ids(5700L + playerCount, playerCount);
      var allAdmissions = new ArrayList<WaveCombatCoordinator.MeleeAttackLease>();
      for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
        UUID target = players.get(playerIndex);
        check(WaveCombatCoordinator.maxMeleeAttackersPerPlayer(playerCount) == 2,
            playerCount + " players retain the approved two direct attackers per target");
        UUID firstAttacker = new UUID(5800L + playerCount, playerIndex * 10L);
        UUID secondAttacker = new UUID(5800L + playerCount, playerIndex * 10L + 1L);
        UUID thirdAttacker = new UUID(5800L + playerCount, playerIndex * 10L + 2L);
        UUID fourthAttacker = new UUID(5800L + playerCount, playerIndex * 10L + 3L);
        var first = attacks.reserveMeleeAttack(attackGeneration, firstAttacker, target, playerCount, 100L);
        var second = attacks.reserveMeleeAttack(attackGeneration, secondAttacker, target, playerCount, 100L);
        check(first != null && second != null,
            playerCount + " players admit two direct melee attacks per individual target");
        check(attacks.reserveMeleeAttack(attackGeneration, thirdAttacker, target,
                playerCount, 100L) == null,
            playerCount + " players block a third concurrent direct hit on one target");

        attacks.finishMeleeAttack(first, false);
        var replacement = attacks.reserveMeleeAttack(attackGeneration, thirdAttacker,
                target, playerCount, 100L);
        check(replacement != null,
            "a Bukkit-cancelled hit releases its reservation immediately");
        attacks.finishMeleeAttack(replacement, true);
        check(attacks.reserveMeleeAttack(attackGeneration, fourthAttacker, target,
                playerCount, 119L) == null,
            "accepted direct hits retain their slots through the 20-tick melee cooldown");
        var afterCooldown = attacks.reserveMeleeAttack(attackGeneration, fourthAttacker,
                target, playerCount, 120L);
        check(afterCooldown != null,
            "expired attack windows free their per-target slots exactly at 20 ticks");
        attacks.finishMeleeAttack(afterCooldown, true);
        allAdmissions.add(second);
        allAdmissions.add(replacement);
      }
      check(allAdmissions.size() == playerCount * 2,
          playerCount + " players scale total simultaneous melee admissions linearly per target");

      UUID target = players.get(0);
      UUID firstElite = new UUID(5900L + playerCount, 1L);
      UUID secondElite = new UUID(5900L + playerCount, 2L);
      check(attacks.reserve(attackGeneration, firstElite, target, 120L, 180L) != null,
          "melee admissions do not consume the independent global elite wind-up lease");
      check(attacks.reserve(attackGeneration, secondElite, target, 121L, 181L) == null,
          playerCount + " players still prevent overlapping elite windups");

      UUID removableAttacker = new UUID(5800L + playerCount, 6L);
      UUID replacementAttacker = new UUID(5800L + playerCount, 7L);
      var removableLease = attacks.reserveMeleeAttack(attackGeneration, removableAttacker,
          target, playerCount, 120L);
      check(removableLease != null,
          "a second live attacker fills the target's melee cap before entity cleanup");
      check(attacks.reserveMeleeAttack(attackGeneration, replacementAttacker,
              target, playerCount, 120L) == null,
          "the saturated target rejects a replacement until its live attacker is removed");
      attacks.remove(removableAttacker);
      var afterOwnerRemoval = attacks.reserveMeleeAttack(attackGeneration,
          replacementAttacker, target, playerCount, 120L);
      check(afterOwnerRemoval != null,
          "entity cleanup releases its active melee slot");
      attacks.cancelTarget(target);
      check(attacks.reserveMeleeAttack(attackGeneration,
              new UUID(5800L + playerCount, 5L), target, playerCount, 120L) != null,
          "target death or disconnect releases all of that player's melee slots");
      attacks.begin(attackGeneration + 1L);
      check(attacks.reserveMeleeAttack(attackGeneration, new UUID(5800L, 6L),
              target, playerCount, 121L) == null,
          "a stale generation cannot reacquire a melee admission");
      check(attacks.reserveMeleeAttack(attackGeneration + 1L, new UUID(5800L, 6L),
              target, playerCount, 121L) != null,
          "a new generation can admit a fresh melee attack immediately");
    }
  }

  static List<UUID> ids(long prefix, int count) {
    var result = new ArrayList<UUID>();
    for (int i=0;i<count;i++) result.add(new UUID(prefix, i));
    return result;
  }

  static void checkSameSquads(WaveCombatCoordinator.SquadPlan before,
                              WaveCombatCoordinator.SquadPlan after, String message) {
    check(before.squads().equals(after.squads()), message);
  }

  static void testPersistentSquadCoordinator() {
    int[] playerCounts = {2,5,6,10,20};
    int[] fighterCounts = {1,2,3,4,5,6,10,56};
    for (int playerCount : playerCounts) for (int fighterCount : fighterCounts) {
      long generation = 1000L + playerCount * 100L + fighterCount;
      var coordinator = new WaveCombatCoordinator();
      coordinator.begin(generation);
      var fighters = ids(2000L + fighterCount, fighterCount);
      var players = ids(3000L + playerCount, playerCount);
      var plan = coordinator.planSquads(generation, "wave-5|room-a|eligible",
          fighters, players, Map.of(), 5, null);
      int expectedSquads = Math.max(1, (fighterCount + 4) / 5);
      check(plan.squads().size() == expectedSquads, "initial pack count covers the roster");
      check(plan.targets().size() == fighterCount, "every live fighter receives one target");
      int packed = 0;
      var identities = new HashSet<Long>();
      for (var squad : plan.squads()) {
        packed += squad.fighters().size();
        check(squad.fighters().size() <= 5
                && (fighterCount < 3 || squad.fighters().size() >= 3),
            "initial packs use 3-5 members where possible");
        check(squad.identity() >= 0 && identities.add(squad.identity()),
            "every persistent squad has a distinct identity");
        check(squad.fighters().stream().allMatch(f -> squad.target().equals(plan.targets().get(f))),
            "all members of a persistent squad share its target");
      }
      check(packed == fighterCount, "initial squads contain the complete roster");
      var loads = new HashMap<UUID,Integer>();
      for (UUID player : players) loads.put(player, 0);
      plan.targets().values().forEach(player -> loads.merge(player, 1, Integer::sum));
      int minLoad = loads.values().stream().mapToInt(Integer::intValue).min().orElse(0);
      int maxLoad = loads.values().stream().mapToInt(Integer::intValue).max().orElse(0);
      check(maxLoad - minLoad <= 5, "initial target pressure stays within one full squad");
      var repeated = coordinator.planSquads(generation, "wave-5|room-a|eligible",
          fighters, players, plan.targets(), 5, null);
      checkSameSquads(plan, repeated, "repeated planning preserves identity, membership and targets");
    }

    var deathPlayers = ids(4000, 2);
    for (int deadIndex : new int[] {0, 8, 15}) {
      long generation = 4100L + deadIndex;
      var coordinator = new WaveCombatCoordinator();
      coordinator.begin(generation);
      var originalRoster = ids(4200, 16);
      String cohort = "wave-5|room-a|eligible";
      var before = coordinator.planSquads(generation, cohort,
          originalRoster, deathPlayers, Map.of(), 5, null);
      check(before.squads().size() == 4, "16 fighters begin in four squads");
      var survivors = new ArrayList<>(originalRoster);
      UUID dead = survivors.remove(deadIndex);
      var after = coordinator.planSquads(generation, cohort,
          survivors, deathPlayers, before.targets(), 5, null);
      check(!after.targets().containsKey(dead), "dead fighter assignments are pruned");
      check(after.squads().size() == before.squads().size(),
          "deaths do not collapse still-live squad identities");
      for (var oldSquad : before.squads()) {
        var retained = after.squads().stream()
            .filter(squad -> squad.identity() == oldSquad.identity()).findFirst().orElse(null);
        check(retained != null, "every nonempty squad retains its identity after a death");
        var expectedMembers = oldSquad.fighters().stream().filter(survivors::contains).toList();
        check(retained.fighters().equals(expectedMembers),
            "a death only prunes that member; it does not repartition the survivors");
        check(retained.target().equals(oldSquad.target()),
            "valid squad targets stay stable while pressure remains within one squad load");
      }
      check(after.squads().stream().allMatch(squad -> squad.fighters().size() <= 5),
          "death leftovers remain safely bounded even when a pack is undersized");
      var repeated = coordinator.planSquads(generation, cohort,
          survivors, deathPlayers, after.targets(), 5, null);
      checkSameSquads(after, repeated, "planning after a death remains stable on later ticks");
    }

    long rosterGeneration = 4300;
    var rosterCoordinator = new WaveCombatCoordinator();
    rosterCoordinator.begin(rosterGeneration);
    String rosterCohort = "wave-5|room-a|eligible";
    var sixFighters = ids(4400, 6);
    var twoPlayers = ids(4500, 2);
    var initial = rosterCoordinator.planSquads(rosterGeneration, rosterCohort,
        sixFighters, twoPlayers, Map.of(), 5, null);
    var fiveSurvivors = new ArrayList<>(sixFighters);
    fiveSurvivors.remove(1);
    var afterDeath = rosterCoordinator.planSquads(rosterGeneration, rosterCohort,
        fiveSurvivors, twoPlayers, initial.targets(), 5, null);
    check(afterDeath.squads().size() == 1 && afterDeath.squads().get(0).fighters().size() == 5,
        "a five-fighter remnant consolidates into one viable squad after a departure");
    var newFighter = new UUID(4400, 6);
    var withNewcomer = new ArrayList<>(fiveSurvivors);
    withNewcomer.add(newFighter);
    var afterJoin = rosterCoordinator.planSquads(rosterGeneration, rosterCohort,
        withNewcomer, twoPlayers, afterDeath.targets(), 5, null);
    check(afterJoin.targets().size() == 6 && afterJoin.targets().containsKey(newFighter),
        "a newcomer receives exactly one assignment after a previously consolidated roster grows");
    check(afterJoin.squads().stream().anyMatch(squad -> squad.identity() == afterDeath.squads().get(0).identity()),
        "a newcomer does not replace the identity of the surviving consolidated squad");
    check(fiveSurvivors.stream().allMatch(afterJoin.targets()::containsKey),
        "a newcomer does not displace any surviving fighter");

    var concurrentJoinCoordinator = new WaveCombatCoordinator();
    concurrentJoinCoordinator.begin(4301);
    var concurrentJoinFighters = ids(4410, 6);
    var beforeConcurrentJoin = concurrentJoinCoordinator.planSquads(4301, rosterCohort,
        concurrentJoinFighters, twoPlayers, Map.of(), 5, null);
    var concurrentJoinRoster = new ArrayList<>(concurrentJoinFighters);
    concurrentJoinRoster.remove(1);
    var replacementFighter = new UUID(4410, 6);
    concurrentJoinRoster.add(replacementFighter);
    var afterConcurrentJoin = concurrentJoinCoordinator.planSquads(4301, rosterCohort,
        concurrentJoinRoster, twoPlayers, beforeConcurrentJoin.targets(), 5, null);
    check(afterConcurrentJoin.squads().size() == 2
            && afterConcurrentJoin.squads().stream().allMatch(squad -> squad.fighters().size() == 3),
        "a replacement joining in the same roster update fills the undersized pack without collapsing identities");
    check(afterConcurrentJoin.squads().stream().anyMatch(squad -> squad.fighters().contains(replacementFighter)),
        "a replacement joins an available squad when the departure and arrival are observed together");

    var disconnected = rosterCoordinator.planSquads(rosterGeneration, rosterCohort,
        withNewcomer, List.of(twoPlayers.get(1)), afterJoin.targets(), 5, null);
    check(disconnected.targets().values().stream().allMatch(twoPlayers.get(1)::equals),
        "disconnected player targets are invalidated and reassigned");
    check(disconnected.squads().stream().map(WaveCombatCoordinator.Squad::identity).toList()
            .equals(afterJoin.squads().stream().map(WaveCombatCoordinator.Squad::identity).toList()),
        "a player-list change does not rebuild squad identities");
    var reconnected = rosterCoordinator.planSquads(rosterGeneration, rosterCohort,
        withNewcomer, twoPlayers, disconnected.targets(), 5, null);
    check(reconnected.targets().values().stream().allMatch(twoPlayers::contains),
        "reconnected roster receives only eligible targets");
    check(reconnected.squads().stream().map(WaveCombatCoordinator.Squad::identity).toList()
            .equals(disconnected.squads().stream().map(WaveCombatCoordinator.Squad::identity).toList()),
        "reconnect preserves the same stable squads");

    long unloadGeneration = 4350;
    var unloadCoordinator = new WaveCombatCoordinator();
    unloadCoordinator.begin(unloadGeneration);
    String unloadCohort = "wave-2|arena|eligible";
    var unloadRoster = ids(4450, 8);
    var beforeUnload = unloadCoordinator.planSquads(unloadGeneration, unloadCohort,
        unloadRoster, twoPlayers, Map.of(), 2, twoPlayers.get(0));
    var partiallyLoaded = unloadRoster.subList(0, 5);
    var restoredCohort = WaveTwoEntityRecoveryPolicy.coordinatorRoster(
        partiallyLoaded, Set.copyOf(unloadRoster));
    var duringPartialUnload = unloadCoordinator.planSquads(unloadGeneration, unloadCohort,
        restoredCohort, twoPlayers, beforeUnload.targets(), 2, twoPlayers.get(0));
    checkSameSquads(beforeUnload, duringPartialUnload,
        "an unloaded W2 UUID remains in its original squad and keeps its target");
    // A fully unloaded cohort does not invoke the planner. Its coordinator
    // state remains intact until the outstanding persisted UUID roster rejoins.
    var afterRejoin = unloadCoordinator.planSquads(unloadGeneration, unloadCohort,
        WaveTwoEntityRecoveryPolicy.coordinatorRoster(List.of(), Set.copyOf(unloadRoster)),
        twoPlayers, beforeUnload.targets(), 2, twoPlayers.get(0));
    checkSameSquads(beforeUnload, afterRejoin,
        "full chunk unload followed by rejoin preserves W2 squad membership and targets");

    var pressureCoordinator = new WaveCombatCoordinator();
    pressureCoordinator.begin(4600);
    var pressureRoster = ids(4700, 11);
    var overloadedTargets = new HashMap<UUID,UUID>();
    for (UUID fighter : pressureRoster) overloadedTargets.put(fighter, twoPlayers.get(0));
    var relieved = pressureCoordinator.planSquads(4600, "wave-5|room-a|eligible",
        pressureRoster, twoPlayers, overloadedTargets, 5, null);
    long movedGroups = relieved.squads().stream()
        .filter(squad -> !twoPlayers.get(0).equals(squad.target())).count();
    check(movedGroups > 0, "targets may move when existing pressure exceeds one squad load");
    var stableRelief = pressureCoordinator.planSquads(4600, "wave-5|room-a|eligible",
        pressureRoster, twoPlayers, relieved.targets(), 5, null);
    checkSameSquads(relieved, stableRelief, "pressure redistribution does not cause repeated churn");

    var isolation = new WaveCombatCoordinator();
    isolation.begin(4800);
    var isolationRoster = ids(4900, 6);
    var cohortA = isolation.planSquads(4800, "wave-5|room-a|eligible",
        isolationRoster, twoPlayers, Map.of(), 5, null);
    var cohortB = isolation.planSquads(4800, "wave-5|room-b|eligible",
        isolationRoster, twoPlayers, Map.of(), 5, null);
    check(cohortA.squads().stream().noneMatch(a -> cohortB.squads().stream()
            .anyMatch(b -> a.identity() == b.identity())),
        "room or eligibility cohort changes isolate squad state");
    var sameCohort = isolation.planSquads(4800, "wave-5|room-a|eligible",
        isolationRoster, twoPlayers, cohortA.targets(), 5, null);
    checkSameSquads(cohortA, sameCohort, "same generation and cohort reuse their stored squads");
    isolation.retainCohorts(4800, Set.of("wave-5|room-b|eligible"));
    var retainedCohortB = isolation.planSquads(4800, "wave-5|room-b|eligible",
        isolationRoster, twoPlayers, cohortB.targets(), 5, null);
    checkSameSquads(cohortB, retainedCohortB,
        "cohort reconciliation preserves state for cohorts still present");
    var recreatedCohortA = isolation.planSquads(4800, "wave-5|room-a|eligible",
        isolationRoster, twoPlayers, cohortA.targets(), 5, null);
    check(recreatedCohortA.squads().get(0).identity() != cohortA.squads().get(0).identity(),
        "cohort reconciliation discards state for a chamber or cohort that disappeared");
    boolean staleReconciliationRejected = false;
    try {
      isolation.retainCohorts(4799, Set.of());
    } catch (IllegalStateException expected) {
      staleReconciliationRejected = true;
    }
    check(staleReconciliationRejected, "stale generation cannot evict current cohort state");
    boolean staleGenerationRejected = false;
    try {
      isolation.planSquads(4799, "wave-5|room-a|eligible",
          isolationRoster, twoPlayers, cohortA.targets(), 5, null);
    } catch (IllegalStateException expected) {
      staleGenerationRejected = true;
    }
    check(staleGenerationRejected, "stale generation cannot access squad state");
    long previousIdentity = cohortA.squads().get(0).identity();
    isolation.begin(4801);
    var nextGeneration = isolation.planSquads(4801, "wave-5|room-a|eligible",
        isolationRoster, twoPlayers, Map.of(), 5, null);
    check(nextGeneration.squads().get(0).identity() != previousIdentity,
        "begin with a new generation clears old squad state");
    isolation.clear();
    isolation.begin(4802);
    var afterClear = isolation.planSquads(4802, "wave-5|room-a|eligible",
        isolationRoster, twoPlayers, Map.of(), 5, null);
    check(afterClear.squads().get(0).identity() != nextGeneration.squads().get(0).identity(),
        "clear removes stored squad identities");

    var bounded = new WaveCombatCoordinator();
    bounded.begin(5000);
    var smallRoster = ids(5100, 3);
    var first = bounded.planSquads(5000, "cohort-0", smallRoster, twoPlayers, Map.of(), 5, null);
    for (int i=1;i<=32;i++)
      bounded.planSquads(5000, "cohort-"+i, smallRoster, twoPlayers, Map.of(), 5, null);
    var evicted = bounded.planSquads(5000, "cohort-0", smallRoster, twoPlayers, first.targets(), 5, null);
    check(evicted.squads().get(0).identity() != first.squads().get(0).identity(),
        "cohort retention stays bounded and evicts the least-recently-used state");

    var markedCoordinator = new WaveCombatCoordinator();
    markedCoordinator.begin(5200);
    var markedFighters = ids(5300, 6);
    UUID marked = twoPlayers.get(0);
    var markedBefore = markedCoordinator.planSquads(5200, "wave-2|room-a|eligible",
        markedFighters, twoPlayers, Map.of(), 2, marked);
    long markedCount = markedBefore.squads().stream().filter(squad -> marked.equals(squad.target())).count();
    check(markedCount == 1, "Wave 2 keeps one of two squads on the marked player");
    var markedAfter = markedCoordinator.planSquads(5200, "wave-2|room-a|eligible",
        markedFighters, twoPlayers, markedBefore.targets(), 2, marked);
    checkSameSquads(markedBefore, markedAfter, "Wave 2 mark role survives repeated planning");

    var shrinkingCoordinator = new WaveCombatCoordinator();
    shrinkingCoordinator.begin(5250);
    var shrinkingFighters = ids(5350, 16);
    var shrinkingPlayers = ids(5450, 2);
    UUID shrinkingMark = shrinkingPlayers.get(0);
    String shrinkingCohort = "wave-2|room-a|eligible";
    var beforeShrink = shrinkingCoordinator.planSquads(5250, shrinkingCohort,
        shrinkingFighters, shrinkingPlayers, Map.of(), 2, shrinkingMark);
    var retiringSquad = beforeShrink.squads().get(3);
    var afterRosterShrink = new ArrayList<>(shrinkingFighters);
    afterRosterShrink.removeAll(retiringSquad.fighters().subList(1, retiringSquad.fighters().size()));
    var afterShrink = shrinkingCoordinator.planSquads(5250, shrinkingCohort,
        afterRosterShrink, shrinkingPlayers, beforeShrink.targets(), 2, shrinkingMark);
    check(afterShrink.squads().size() == 3,
        "roster shrink retires a redundant squad instead of leaving a one-member pack");
    check(afterShrink.squads().stream().allMatch(squad -> squad.fighters().size() >= 3
            && squad.fighters().size() <= 5),
        "roster shrink rebalances surviving packs into the usual three-to-five range");
    check(afterShrink.targets().size() == afterRosterShrink.size(),
        "rebalancing assigns every surviving fighter exactly once");
    check(afterRosterShrink.stream().allMatch(fighter ->
            beforeShrink.targets().get(fighter).equals(afterShrink.targets().get(fighter))),
        "rebalancing joins the retired marked pack to the same-target squad without target churn");
    check(afterShrink.squads().stream().filter(squad -> shrinkingMark.equals(squad.target())).count() == 1,
        "Wave 2 keeps one marked-player squad after pack rebalancing");
    var repeatedShrink = shrinkingCoordinator.planSquads(5250, shrinkingCohort,
        afterRosterShrink, shrinkingPlayers, afterShrink.targets(), 2, shrinkingMark);
    checkSameSquads(afterShrink, repeatedShrink,
        "a rebalanced roster remains stable on the next shared decision pass");

    var mergeRepairCoordinator = new WaveCombatCoordinator();
    mergeRepairCoordinator.begin(5252);
    var sixMergeFighters = ids(5352, 6);
    String mergeRepairCohort = "wave-5|room-a|eligible";
    var beforeMergeRepair = mergeRepairCoordinator.planSquads(5252, mergeRepairCohort,
        sixMergeFighters, shrinkingPlayers, Map.of(), 5, null);
    var afterTwoSquadDeaths = new ArrayList<>(sixMergeFighters);
    for (var squad : beforeMergeRepair.squads()) {
      afterTwoSquadDeaths.remove(squad.fighters().get(squad.fighters().size() - 1));
    }
    var afterMergeRepair = mergeRepairCoordinator.planSquads(5252, mergeRepairCohort,
        afterTwoSquadDeaths, shrinkingPlayers, beforeMergeRepair.targets(), 5, null);
    check(afterMergeRepair.squads().size() == 1
            && afterMergeRepair.squads().get(0).fighters().size() == 4,
        "merge repair combines undersized packs even when total fighters cannot fill every current squad");
    check(afterMergeRepair.targets().size() == afterTwoSquadDeaths.size(),
        "merge repair preserves one assignment per surviving fighter");

    var transferringCoordinator = new WaveCombatCoordinator();
    transferringCoordinator.begin(5255);
    var twentyW2Fighters = ids(5355, 20);
    String transferringCohort = "wave-2|room-a|eligible";
    var beforeTransfer = transferringCoordinator.planSquads(5255, transferringCohort,
        twentyW2Fighters, shrinkingPlayers, Map.of(), 2, shrinkingMark);
    var transferringRoster = new ArrayList<>(twentyW2Fighters);
    transferringRoster.removeAll(beforeTransfer.squads().get(3).fighters().subList(2, 5));
    var afterTransfer = transferringCoordinator.planSquads(5255, transferringCohort,
        transferringRoster, shrinkingPlayers, beforeTransfer.targets(), 2, shrinkingMark);
    check(afterTransfer.squads().size() == 4
            && afterTransfer.squads().stream().allMatch(squad -> squad.fighters().size() >= 3
                && squad.fighters().size() <= 5),
        "a full cohort rebalances a two-member pack from spare members in other packs");
    check(transferringRoster.stream().allMatch(fighter ->
            beforeTransfer.targets().get(fighter).equals(afterTransfer.targets().get(fighter))),
        "same-target transfers avoid target churn when no pack has room for a merge");

    var lostMarkedSquadCoordinator = new WaveCombatCoordinator();
    lostMarkedSquadCoordinator.begin(5260);
    var sixW2Fighters = ids(5360, 6);
    String lostMarkedCohort = "wave-2|room-a|eligible";
    var twoW2Squads = lostMarkedSquadCoordinator.planSquads(5260, lostMarkedCohort,
        sixW2Fighters, shrinkingPlayers, Map.of(), 2, shrinkingMark);
    var survivingUnmarkedPack = new ArrayList<>(sixW2Fighters);
    survivingUnmarkedPack.removeAll(twoW2Squads.squads().get(0).fighters());
    var afterMarkedPackDeath = lostMarkedSquadCoordinator.planSquads(5260, lostMarkedCohort,
        survivingUnmarkedPack, shrinkingPlayers, twoW2Squads.targets(), 2, shrinkingMark);
    check(afterMarkedPackDeath.squads().size() == 1
            && shrinkingMark.equals(afterMarkedPackDeath.squads().get(0).target()),
        "Wave 2 mark role is allocated among live squads after an earlier role disappears");
    checkSameSquads(afterMarkedPackDeath,
        lostMarkedSquadCoordinator.planSquads(5260, lostMarkedCohort,
            survivingUnmarkedPack, shrinkingPlayers, afterMarkedPackDeath.targets(), 2, shrinkingMark),
        "reallocated Wave 2 mark role remains stable on the next pass");

    var interiorRetirementCoordinator = new WaveCombatCoordinator();
    interiorRetirementCoordinator.begin(5261);
    var twentyMarkFighters = ids(5361, 20);
    String interiorRetirementCohort = "wave-2|room-a|eligible";
    var beforeInteriorRetirement = interiorRetirementCoordinator.planSquads(5261,
        interiorRetirementCohort, twentyMarkFighters, shrinkingPlayers, Map.of(), 2, shrinkingMark);
    check(beforeInteriorRetirement.squads().size() == 4,
        "a twenty-mob Wave 2 cohort creates four persistent squads");
    var afterInteriorRetirementRoster = new ArrayList<>(twentyMarkFighters);
    afterInteriorRetirementRoster.removeAll(beforeInteriorRetirement.squads().get(1).fighters());
    var afterInteriorRetirement = interiorRetirementCoordinator.planSquads(5261,
        interiorRetirementCohort, afterInteriorRetirementRoster, shrinkingPlayers,
        beforeInteriorRetirement.targets(), 2, shrinkingMark);
    check(afterInteriorRetirement.squads().size() == 3
            && afterInteriorRetirement.squads().stream()
                .filter(squad -> shrinkingMark.equals(squad.target())).count() == 1,
        "Wave 2 reallocates the one-in-three focus role by live squad rank after an interior squad retires");

    var spatialCoordinator = new WaveCombatCoordinator();
    spatialCoordinator.begin(5300);
    var spatialFighters = ids(5400, 20);
    var spatialPlayers = ids(5500, 10);
    var fighterPositions = new HashMap<UUID, WaveCombatCoordinator.Position>();
    var playerPositions = new HashMap<UUID, WaveCombatCoordinator.Position>();
    for (int i=0;i<20;i++) {
      boolean west = i % 2 == 0;
      fighterPositions.put(spatialFighters.get(i), new WaveCombatCoordinator.Position(
          west ? i % 3 : 80 + i % 3, 64, i % 4));
    }
    for (int i=0;i<8;i++)
      playerPositions.put(spatialPlayers.get(i), new WaveCombatCoordinator.Position(i % 4, 64, i % 3));
    for (int i=8;i<10;i++)
      playerPositions.put(spatialPlayers.get(i), new WaveCombatCoordinator.Position(80 + i, 64, 0));
    var spatialPlan = spatialCoordinator.planSquads(5300, "wave-5|arena|eligible",
        spatialFighters, spatialPlayers, Map.of(), 5, null, fighterPositions, playerPositions);
    for (var squad : spatialPlan.squads()) {
      boolean west = squad.fighters().stream().allMatch(fighter -> fighterPositions.get(fighter).x() < 40);
      boolean east = squad.fighters().stream().allMatch(fighter -> fighterPositions.get(fighter).x() >= 40);
      check(west || east, "spatial fighter squads remain assigned to their local formation");
      double targetX = playerPositions.get(squad.target()).x();
      check(west ? targetX < 40 : targetX >= 40,
          "spatial squads target the corresponding nearby player cluster");
    }
    check(spatialPlan.squads().stream().anyMatch(squad -> playerPositions.get(squad.target()).x() < 40)
            && spatialPlan.squads().stream().anyMatch(squad -> playerPositions.get(squad.target()).x() >= 40),
        "fighters are split across both physically separated player groups");

    UUID tieFighter = new UUID(1L, 1L);
    UUID tieFirst = new UUID(0L, 0L);
    UUID tieSecond = new UUID(0L, 2L);
    Map<UUID, WaveCombatCoordinator.Position> tieFighterPositions = Map.of(
        tieFighter, new WaveCombatCoordinator.Position(0, 64, 0));
    Map<UUID, WaveCombatCoordinator.Position> tiePlayerPositions = Map.of(
        tieSecond, new WaveCombatCoordinator.Position(0, 64, 0));
    var tieForwardCoordinator = new WaveCombatCoordinator();
    tieForwardCoordinator.begin(5400);
    var tieForwardPlan = tieForwardCoordinator.planSquads(5400, "wave-5|arena|tie",
        List.of(tieFighter), List.of(tieSecond, tieFirst), Map.of(), 5, null,
        tieFighterPositions, tiePlayerPositions);
    var tieReverseCoordinator = new WaveCombatCoordinator();
    tieReverseCoordinator.begin(5401);
    var tieReversePlan = tieReverseCoordinator.planSquads(5401, "wave-5|arena|tie",
        List.of(tieFighter), List.of(tieFirst, tieSecond), Map.of(), 5, null,
        tieFighterPositions, tiePlayerPositions);
    check(tieForwardPlan.squads().get(0).target().equals(tieFirst)
            && tieReversePlan.squads().get(0).target().equals(tieFirst),
        "equal spatial costs preserve UUID-ordered targeting independent of input order");
  }
}
