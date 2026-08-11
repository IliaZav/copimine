package me.copimine.artifacts;

import java.util.List;
import java.util.Map;

/** Immutable, testable page data for the official narcotic recipe fragments. */
public final class NarcoticRecipeBookData {
   private static final Map<String, String> INGREDIENT_NAMES = Map.ofEntries(
      Map.entry("WHITE_DYE", "Белый краситель"),
      Map.entry("GLOWSTONE_DUST", "Светокаменная пыль"),
      Map.entry("RABBIT_FOOT", "Кроличья лапка"),
      Map.entry("POTION:WEAKNESS", "Зелье слабости"),
      Map.entry("SUGAR", "Сахар"),
      Map.entry("DIAMOND", "Алмаз"),
      Map.entry("JUNGLE_LEAVES", "Листья тропического дерева"),
      Map.entry("SLIME_BLOCK", "Блок слизи"),
      Map.entry("TURTLE_SCUTE", "Щиток черепахи"),
      Map.entry("EMERALD", "Изумруд"),
      Map.entry("GOLD_INGOT", "Золотой слиток"),
      Map.entry("GOLDEN_CARROT", "Золотая морковь"),
      Map.entry("STRING", "Нить"),
      Map.entry("BONE", "Кость"),
      Map.entry("IRON_BLOCK", "Железный блок"),
      Map.entry("GHAST_TEAR", "Слеза гаста"),
      Map.entry("AMETHYST_BLOCK", "Аметистовый блок"),
      Map.entry("END_ROD", "Стержень Края"),
      Map.entry("IRON_INGOT", "Железный слиток"),
      Map.entry("BLUE_STAINED_GLASS", "Синее стекло"),
      Map.entry("POTION:SPEED", "Зелье скорости"),
      Map.entry("COCOA_BEANS", "Какао-бобы"),
      Map.entry("IRON_NUGGET", "Железный самородок"),
      Map.entry("LARGE_FERN", "Высокий папоротник"),
      Map.entry("DRIED_KELP_BLOCK", "Блок сушёной ламинарии"),
      Map.entry("SUGAR_CANE", "Сахарный тростник")
   );

   private static final Map<String, BookData> BOOKS = Map.ofEntries(
      Map.entry("narcotic_recipe_feta", recipe("Феты", "WHITE_DYE", "GLOWSTONE_DUST", "RABBIT_FOOT", "POTION:WEAKNESS")),
      Map.entry("narcotic_recipe_kola", recipe("Колы", "SUGAR", "DIAMOND", "JUNGLE_LEAVES")),
      Map.entry("narcotic_recipe_girion", recipe("Гириона", "SLIME_BLOCK", "TURTLE_SCUTE", "EMERALD")),
      Map.entry("narcotic_recipe_sbp", recipe("СБП", "GOLD_INGOT", "GOLDEN_CARROT", "STRING")),
      Map.entry("narcotic_recipe_sos", recipe("Соси", "BONE", "IRON_BLOCK", "GHAST_TEAR")),
      Map.entry("narcotic_recipe_drun", recipe("Друна", "AMETHYST_BLOCK", "END_ROD", "IRON_INGOT")),
      Map.entry("narcotic_recipe_chups", recipe("Чупса", "BLUE_STAINED_GLASS", "POTION:SPEED", "COCOA_BEANS", "IRON_NUGGET")),
      Map.entry("narcotic_recipe_borshevik", recipe("Борщевика", "LARGE_FERN", "DRIED_KELP_BLOCK", "SUGAR_CANE"))
   );

   private NarcoticRecipeBookData() {
   }

   public static BookData forItem(String itemId) {
      return BOOKS.get(itemId);
   }

   private static BookData recipe(String name, String... ingredientKeys) {
      List<String> keys = List.of(ingredientKeys);
      List<String> names = keys.stream().map(INGREDIENT_NAMES::get).toList();
      if (names.stream().anyMatch(nameValue -> nameValue == null)) {
         throw new IllegalStateException("Unknown recipe ingredient for " + name);
      }

      String firstPage = "\n\n\n\n       Рецепт " + name + "\n\n\n\n";
      String secondPage = "\n\n" + String.join(" + ", names) + "\n";
      return new BookData(List.of(firstPage, secondPage), keys);
   }

   public record BookData(List<String> pages, List<String> ingredientKeys) {
      public BookData {
         pages = List.copyOf(pages);
         ingredientKeys = List.copyOf(ingredientKeys);
      }
   }
}
