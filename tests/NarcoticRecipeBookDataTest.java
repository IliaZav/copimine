import me.copimine.artifacts.NarcoticRecipeBookData;

import java.util.List;

public final class NarcoticRecipeBookDataTest {
    private static final List<String> IDS = List.of(
            "feta", "kola", "girion", "sbp", "sos", "drun", "chups", "borshevik"
    );

    public static void main(String[] args) {
        for (String id : IDS) {
            NarcoticRecipeBookData.BookData data =
                    NarcoticRecipeBookData.forItem("narcotic_recipe_" + id);
            check(data != null, "missing book data: " + id);
            check(data.pages().size() == 2, "book must have two pages: " + id);
            check(data.pages().get(0).contains("Рецепт"), "page one has no title: " + id);
            check(data.pages().get(1).contains("+"), "page two has no ingredient separators: " + id);
            check(data.ingredientKeys().size() >= 3, "recipe is unexpectedly short: " + id);
            check(!data.pages().get(1).contains("material:"), "raw material token leaked: " + id);
            check(!data.pages().get(1).contains("potion:"), "raw potion token leaked: " + id);
        }

        NarcoticRecipeBookData.BookData chups =
                NarcoticRecipeBookData.forItem("narcotic_recipe_chups");
        check(chups.pages().get(0).contains("Рецепт Чупса"), "chups title is missing");
        check(chups.pages().get(1).contains("Синее стекло"), "chups material is not localized");
        check(NarcoticRecipeBookData.forItem("narcotic_recipe_zhuzevo") == null,
                "empty zhuzevo recipe must not have a book");
        System.out.println("NarcoticRecipeBookDataTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
