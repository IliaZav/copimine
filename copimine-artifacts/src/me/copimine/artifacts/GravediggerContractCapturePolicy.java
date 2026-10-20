package me.copimine.artifacts;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Selects one eligible Gravedigger contract without discarding other instances. */
public final class GravediggerContractCapturePolicy {
   private GravediggerContractCapturePolicy() {
   }

   public enum Container {
      STORAGE,
      OFFHAND,
      ARMOR
   }

   public record Candidate<T>(Container container, int index, T stack) {
      public Candidate {
         Objects.requireNonNull(container, "container");
         Objects.requireNonNull(stack, "stack");
         if (container == Container.OFFHAND && index != 0) {
            throw new IllegalArgumentException("offhand candidate index must be zero");
         }
         if (container != Container.OFFHAND && index < 0) {
            throw new IllegalArgumentException("candidate index must be non-negative");
         }
      }
   }

   /**
    * Search in the established priority order: storage, offhand, then armor.
    * The predicate should verify both authenticity and whether this exact item
    * instance is still eligible for consumption.
    */
   public static <T> Candidate<T> firstEligible(
         List<T> storage,
         T offhand,
         List<T> armor,
         Predicate<? super T> eligible) {
      Objects.requireNonNull(storage, "storage");
      Objects.requireNonNull(armor, "armor");
      Objects.requireNonNull(eligible, "eligible");

      for (int index = 0; index < storage.size(); index++) {
         T stack = storage.get(index);
         if (stack != null && eligible.test(stack)) {
            return new Candidate<>(Container.STORAGE, index, stack);
         }
      }
      if (offhand != null && eligible.test(offhand)) {
         return new Candidate<>(Container.OFFHAND, 0, offhand);
      }
      for (int index = 0; index < armor.size(); index++) {
         T stack = armor.get(index);
         if (stack != null && eligible.test(stack)) {
            return new Candidate<>(Container.ARMOR, index, stack);
         }
      }
      return null;
   }
}
