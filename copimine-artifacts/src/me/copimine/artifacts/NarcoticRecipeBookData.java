package me.copimine.artifacts;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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

   private static final Map<String, String> RECIPE_TITLES = Map.ofEntries(
      Map.entry("feta", "Феты"),
      Map.entry("kola", "Колы"),
      Map.entry("girion", "Гириона"),
      Map.entry("sbp", "СБП"),
      Map.entry("sos", "Соси"),
      Map.entry("drun", "Друна"),
      Map.entry("chups", "Чупса"),
      Map.entry("borshevik", "Борщевика")
   );
   private static volatile Map<String, BookData> activeBooks = BOOKS;

   private NarcoticRecipeBookData() {
   }

   public static BookData forItem(String itemId) {
      return activeBooks.get(itemId);
   }

   /** Load recipe tokens from the installed CopiMineNarcotics config. */
   public static synchronized void loadFromConfig(File configFile) {
      if (configFile == null || !configFile.isFile()) {
         throw new IllegalArgumentException("copimine-narcotics config.yml is missing");
      }
      YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
      ConfigurationSection items = config.getConfigurationSection("items");
      if (items == null) {
         throw new IllegalArgumentException("copimine-narcotics config has no items section");
      }
      Map<String, BookData> loaded = new LinkedHashMap<>();
      for (String catalogId : BOOKS.keySet()) {
         String narcoticId = catalogId.substring("narcotic_recipe_".length());
         ConfigurationSection item = items.getConfigurationSection(narcoticId);
         if (item == null) {
            throw new IllegalArgumentException("missing narcotic recipe: " + narcoticId);
         }
         List<String> rawRecipe = item.getStringList("recipe");
         if (rawRecipe.isEmpty()) {
            throw new IllegalArgumentException("empty narcotic recipe: " + narcoticId);
         }
         List<String> ingredientKeys = rawRecipe.stream()
               .map(NarcoticRecipeBookData::normalizeIngredient)
               .toList();
         loaded.put(catalogId, recipe(RECIPE_TITLES.getOrDefault(narcoticId, narcoticId), ingredientKeys));
      }
      activeBooks = Map.copyOf(loaded);
   }

   private static String normalizeIngredient(String raw) {
      if (raw == null || raw.isBlank()) {
         throw new IllegalArgumentException("blank narcotic ingredient");
      }
      String token = raw.trim().toUpperCase(Locale.ROOT);
      if (token.startsWith("MATERIAL:")) {
         token = token.substring("MATERIAL:".length());
      } else if (token.startsWith("POTION:")) {
         token = "POTION:" + token.substring("POTION:".length());
      } else {
         throw new IllegalArgumentException("unsupported narcotic ingredient: " + raw);
      }
      if (token.isBlank() || !INGREDIENT_NAMES.containsKey(token)) {
         throw new IllegalArgumentException("unlocalized narcotic ingredient: " + raw);
      }
      return token;
   }

   private static BookData recipe(String name, String... ingredientKeys) {
      return recipe(name, List.of(ingredientKeys));
   }

   private static BookData recipe(String name, List<String> keys) {
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
