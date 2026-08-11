import java.io.File;
import java.util.List;
import me.copimine.artifacts.NarcoticRecipeBookData;

public final class NarcoticRecipeBookConfigTest {
    private static final List<String> IDS = List.of(
            "feta", "kola", "girion", "sbp", "sos", "drun", "chups", "borshevik"
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("path to copimine-narcotics/config.yml is required");
        }
        NarcoticRecipeBookData.loadFromConfig(new File(args[0]));
        for (String id : IDS) {
            NarcoticRecipeBookData.BookData data =
                    NarcoticRecipeBookData.forItem("narcotic_recipe_" + id);
            check(data != null, "missing loaded recipe book: " + id);
            check(data.pages().size() == 2, "loaded book must have exactly two pages: " + id);
            check(data.ingredientKeys().stream().noneMatch(token -> token.startsWith("material:")
                    || token.startsWith("potion:")), "raw config prefix leaked: " + id);
        }
        check(NarcoticRecipeBookData.forItem("narcotic_recipe_zhuzevo") == null,
                "zhuzevo must remain without a recipe book");
        System.out.println("NarcoticRecipeBookConfigTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
