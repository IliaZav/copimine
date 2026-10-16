package me.copimine.endevent.domain.wave7;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Private, bounded replica supplies. Never contains Bukkit stacks or executable metadata. */
public final class EchoLoadoutState {
    public enum Kind { EMPTY, MELEE, ARMOR, BOW, CROSSBOW, SHIELD, FOOD, GOLDEN_APPLE, AMMO, INERT, BLOCKED_CUSTOM }
    private static final Set<String> TIERS = Set.of("WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE");
    private static final Set<String> ARMOR = Set.of("LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "TURTLE");
    private static final Set<String> FOODS = Set.of("APPLE", "BREAD", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
            "COOKED_MUTTON", "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO", "CARROT", "GOLDEN_CARROT");
    private static final Map<String, Integer> ENCHANTMENTS = Map.ofEntries(
            Map.entry("sharpness",5), Map.entry("smite",5), Map.entry("bane_of_arthropods",5),
            Map.entry("knockback",2), Map.entry("fire_aspect",2), Map.entry("looting",3), Map.entry("sweeping_edge",3),
            Map.entry("power",5), Map.entry("punch",2), Map.entry("flame",1), Map.entry("infinity",1),
            Map.entry("piercing",4), Map.entry("multishot",1), Map.entry("quick_charge",3),
            Map.entry("protection",4), Map.entry("fire_protection",4), Map.entry("blast_protection",4),
            Map.entry("projectile_protection",4), Map.entry("feather_falling",4), Map.entry("thorns",3),
            Map.entry("respiration",3), Map.entry("aqua_affinity",1), Map.entry("depth_strider",3),
            Map.entry("frost_walker",2), Map.entry("soul_speed",3), Map.entry("swift_sneak",3),
            Map.entry("unbreaking",3), Map.entry("mending",1), Map.entry("binding_curse",1), Map.entry("vanishing_curse",1));
    public record Item(String material, int amount, int maximumDamage, int damage,
                       Map<String,Integer> enchantments, String name, boolean custom) {
        public Item {
            if (material == null || !material.matches("[A-Z][A-Z0-9_]{0,63}") || amount < 0 || amount > 64
                    || maximumDamage < 0 || maximumDamage > 10_000 || damage < 0 || damage > maximumDamage
                    || name == null || name.length() > 256 || enchantments == null || enchantments.size() > 32
                    || material.equals("AIR") != (amount == 0) || maximumDamage > 0 && amount != 1)
                throw new IllegalArgumentException("Invalid bounded Echo item descriptor");
            enchantments = Map.copyOf(enchantments);
            for (var entry : enchantments.entrySet()) {
                if (!safeEnchantment(entry.getKey(), entry.getValue()))
                    throw new IllegalArgumentException("Unsupported replica enchantment");
            }
            if (material.equals("AIR") && (maximumDamage != 0 || custom || !name.isEmpty() || !enchantments.isEmpty()))
                throw new IllegalArgumentException("Empty replica has metadata");
        }
        public static Item empty() { return new Item("AIR",0,0,0,Map.of(),"",false); }
        public Kind kind() { return classify(material, custom); }
        public boolean usable() { return kind() != Kind.EMPTY && kind() != Kind.INERT && kind() != Kind.BLOCKED_CUSTOM; }
    }
    public static boolean safeEnchantment(String key, int level) {
        return key != null && key.startsWith("minecraft:") && level > 0
                && level <= ENCHANTMENTS.getOrDefault(key.substring(10), 0);
    }
    public static Kind classify(String material, boolean custom) {
        if (material.equals("AIR")) return Kind.EMPTY;
        if (custom) return Kind.BLOCKED_CUSTOM;
        int split = material.indexOf('_');
        if (split > 0 && TIERS.contains(material.substring(0, split))
                && Set.of("SWORD", "AXE").contains(material.substring(split + 1))) return Kind.MELEE;
        if (split > 0 && ARMOR.contains(material.substring(0, split))
                && Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS").contains(material.substring(split + 1))) return Kind.ARMOR;
        return switch (material) {
            case "BOW" -> Kind.BOW;
            case "CROSSBOW" -> Kind.CROSSBOW;
            case "SHIELD" -> Kind.SHIELD;
            case "GOLDEN_APPLE" -> Kind.GOLDEN_APPLE;
            case "ARROW" -> Kind.AMMO;
            default -> FOODS.contains(material) ? Kind.FOOD : Kind.INERT;
        };
    }
    private final UUID event, duel, owner;
    private final long attempt;
    private final int selected;
    private final List<Item> frozen;
    private final int[] remaining, wear;
    private long revision;

    private EchoLoadoutState(UUID event, long attempt, UUID duel, UUID owner, int selected,
                              List<Item> items, int[] remaining, int[] wear, long revision) {
        if (event == null || duel == null || owner == null || attempt <= 0 || selected < 0 || selected > 8
                || items.size() != 42 || revision < 0 || revision == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid replica identity or bounds");
        this.event=event; this.attempt=attempt; this.duel=duel; this.owner=owner; this.selected=selected;
        frozen=List.copyOf(items); this.remaining=remaining.clone(); this.wear=wear.clone(); this.revision=revision;
        for (int slot=0;slot<42;slot++) {
            Item item=frozen.get(slot);
            if (this.remaining[slot]<0 || this.remaining[slot]>item.amount()
                    || this.wear[slot]<item.damage() || this.wear[slot]>item.maximumDamage())
                throw new IllegalArgumentException("Invalid remaining replica quantity or wear");
        }
        Item bonus=frozen.get(41);
        if (!bonus.equals(new Item("GOLDEN_APPLE",2,0,0,Map.of(),"",false)))
            throw new IllegalArgumentException("Replica allowance must be exactly two plain golden apples");
    }
    public static EchoLoadoutState create(UUID event, long attempt, UUID duel, UUID owner, List<Item> source, int selected) {
        if (source == null || source.size()!=41) throw new IllegalArgumentException("41 source slots required");
        var items=new ArrayList<Item>();
        for (Item item : source) {
            if (item==null) throw new IllegalArgumentException("Null source slot");
            items.add(new Item(item.material(),item.amount(),item.maximumDamage(),
                    item.usable()?0:item.damage(),item.enchantments(),item.name(),item.custom()));
        }
        items.add(new Item("GOLDEN_APPLE",2,0,0,Map.of(),"",false));
        int[] counts=new int[42],damage=new int[42];
        for (int i=0;i<42;i++) { counts[i]=items.get(i).amount(); damage[i]=items.get(i).damage(); }
        return new EchoLoadoutState(event,attempt,duel,owner,selected,items,counts,damage,0);
    }
    public Item item(int slot) {
        if (slot<0 || slot>=42) throw new IllegalArgumentException("Invalid replica slot");
        if (remaining[slot]==0) return Item.empty();
        Item item=frozen.get(slot);
        return new Item(item.material(),remaining[slot],item.maximumDamage(),wear[slot],item.enchantments(),item.name(),item.custom());
    }
    public int remaining(int slot) { return slot>=0 && slot<42 ? remaining[slot] : 0; }
    public int selectedSlot() { return selected; }
    public long revision() { return revision; }
    public int find(Kind kind) {
        if (kind==Kind.GOLDEN_APPLE && remaining[41]>0) return 41;
        for (int slot=0;slot<41;slot++) if (remaining[slot]>0 && frozen.get(slot).kind()==kind) return slot;
        return -1;
    }
    public boolean consume(int slot, int amount, long expectedRevision) {
        if (slot<0 || slot>=42 || amount<=0 || remaining[slot]<amount || revision!=expectedRevision
                || revision>=Long.MAX_VALUE-1) return false;
        Kind kind=frozen.get(slot).kind();
        if (kind!=Kind.FOOD && kind!=Kind.GOLDEN_APPLE && kind!=Kind.AMMO) return false;
        remaining[slot]-=amount; revision++; return true;
    }
    /** Caller supplies the durability outcome after native enchantment handling. */
    public boolean damage(int slot, int amount, long expectedRevision) {
        if (slot<0 || slot>=42 || amount<=0 || revision!=expectedRevision || remaining[slot]==0
                || revision>=Long.MAX_VALUE-1 || !frozen.get(slot).usable() || frozen.get(slot).maximumDamage()==0) return false;
        wear[slot]=(int)Math.min(frozen.get(slot).maximumDamage(),(long)wear[slot]+amount);
        if (wear[slot]==frozen.get(slot).maximumDamage()) remaining[slot]=0;
        revision++; return true;
    }
    /** Plain server-thread snapshot for existing atomic storage, never public evidence. */
    public Map<String,String> encode() {
        var map=new LinkedHashMap<String,String>();
        map.put("schema","1"); map.put("event",event.toString()); map.put("attempt",Long.toString(attempt));
        map.put("duel",duel.toString()); map.put("owner",owner.toString());
        map.put("selected",Integer.toString(selected)); map.put("revision",Long.toString(revision));
        for (int slot=0;slot<42;slot++) {
            String prefix="slot."+slot+"."; Item item=frozen.get(slot);
            map.put(prefix+"material",item.material()); map.put(prefix+"amount",Integer.toString(item.amount()));
            map.put(prefix+"maximum",Integer.toString(item.maximumDamage())); map.put(prefix+"damage",Integer.toString(item.damage()));
            map.put(prefix+"custom",Boolean.toString(item.custom()));
            map.put(prefix+"name",Base64.getEncoder().encodeToString(item.name().getBytes(StandardCharsets.UTF_8)));
            map.put(prefix+"enchant",item.enchantments().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(",")));
            map.put(prefix+"remaining",Integer.toString(remaining[slot])); map.put(prefix+"wear",Integer.toString(wear[slot]));
        }
        return Map.copyOf(map);
    }
    public static EchoLoadoutState restore(Map<String,String> encoded, UUID event, long attempt, UUID duel, UUID owner,
                                            long minimumRevision) {
        if (encoded==null || encoded.size()!=385 || minimumRevision<0)
            throw new IllegalArgumentException("Invalid bounded replica receipt");
        var input=new LinkedHashMap<>(encoded);
        if (!take(input,"schema").equals("1") || !take(input,"event").equals(event.toString())
                || !take(input,"attempt").equals(Long.toString(attempt)) || !take(input,"duel").equals(duel.toString())
                || !take(input,"owner").equals(owner.toString())) throw new IllegalArgumentException("Foreign replica receipt");
        int selected=Integer.parseInt(take(input,"selected")); long revision=Long.parseLong(take(input,"revision"));
        if (revision<minimumRevision) throw new IllegalArgumentException("Stale replica consumption receipt");
        var items=new ArrayList<Item>(); int[] counts=new int[42],wear=new int[42];
        for (int slot=0;slot<42;slot++) {
            String prefix="slot."+slot+"."; String material=take(input,prefix+"material");
            int amount=Integer.parseInt(take(input,prefix+"amount")),maximum=Integer.parseInt(take(input,prefix+"maximum"));
            int damage=Integer.parseInt(take(input,prefix+"damage")); String custom=take(input,prefix+"custom");
            if (!custom.equals("true")&&!custom.equals("false")) throw new IllegalArgumentException("Invalid custom flag");
            String name=new String(Base64.getDecoder().decode(take(input,prefix+"name")),StandardCharsets.UTF_8);
            var enchantments=new LinkedHashMap<String,Integer>(); String enchant=take(input,prefix+"enchant");
            if (!enchant.isEmpty()) for (String entry:enchant.split(",",-1)) {
                String[] pair=entry.split("=",-1);
                if (pair.length!=2 || enchantments.put(pair[0],Integer.parseInt(pair[1]))!=null)
                    throw new IllegalArgumentException("Invalid duplicate replica enchantment");
            }
            items.add(new Item(material,amount,maximum,damage,enchantments,name,custom.equals("true")));
            counts[slot]=Integer.parseInt(take(input,prefix+"remaining")); wear[slot]=Integer.parseInt(take(input,prefix+"wear"));
        }
        if (!input.isEmpty()) throw new IllegalArgumentException("Unknown replica receipt fields");
        return new EchoLoadoutState(event,attempt,duel,owner,selected,items,counts,wear,revision);
    }
    private static String take(Map<String,String> input,String key) {
        String value=input.remove(key);
        if (value==null || value.length()>1_024) throw new IllegalArgumentException("Missing or oversized replica receipt field");
        return value;
    }
}
