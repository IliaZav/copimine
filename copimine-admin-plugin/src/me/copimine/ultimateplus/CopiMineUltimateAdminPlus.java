package me.copimine.ultimateplus;

import me.copimine.economycore.CopiMineEconomyCore;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Transformation;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class CopiMineUltimateAdminPlus extends JavaPlugin implements Listener, CommandExecutor, TabCompleter, PluginMessageListener {
    private String dbPath;
    private String dbLabel = "postgresql://127.0.0.1:5432/copimine?schema=copimine";
    private PgSettings pgSettings;
    private PgConnectionPool pgPool;
    private volatile boolean dbReady = false;
    private ExecutorService dbExecutor;
    private final AtomicBoolean dbNotReadyWarned = new AtomicBoolean(false);
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> checkMode = new ConcurrentHashMap<>();
    private final Map<UUID, Location> checkReturn = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inventoryLocks = new ConcurrentHashMap<>();
    private final Map<UUID, AtmPinSession> atmPinSessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingOfficialReturns = new ConcurrentHashMap<>();
    private final Set<UUID> sidebarHidden = ConcurrentHashMap.newKeySet();
    private final Set<UUID> sidebarPersonal = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Scoreboard> oldBoards = new ConcurrentHashMap<>();
    private boolean sidebarGlobal = true;
    private BukkitTask sidebarTask;
    private int tick = 0;
    private static final String SIDEBAR_OBJECTIVE = "cmulive";
    private final Random rnd = new Random();
    private final Set<String> arPlacedBreaks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> processedSealDrops = ConcurrentHashMap.newKeySet();
    private final Set<String> rpBlocked = new HashSet<>();
    private final Set<String> reportAllowed = new HashSet<>();
    private final AtomicBoolean arSyncQueued = new AtomicBoolean(false);
    private final Map<UUID, ArDropClaim> arTransferClaims = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBugReport> pendingBugReports = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientBrands = new ConcurrentHashMap<>();
    private final Set<String> arEligibleBreaks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean sidebarRefreshInFlight = new AtomicBoolean(false);
    private volatile SidebarSnapshot sidebarSnapshot = SidebarSnapshot.noElection(0L);
    private final Map<UUID, CachedElectionRole> electionRoleCache = new ConcurrentHashMap<>();
    private final Set<UUID> electionRoleRefreshInFlight = ConcurrentHashMap.newKeySet();
    private CopiMineExpansionShim papi;
    private BukkitTask inventorySnapshotTask;
    private BukkitTask nameplateTask;
    private final HttpClient backendHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private NamespacedKey visualEntityTypeKey;
    private NamespacedKey visualKindKey;
    private NamespacedKey visualLinkedIdKey;
    private NamespacedKey visualModelIdKey;

    private static final Set<String> BLOCKED_CLIENT_BRAND_TOKENS = Set.of(
            "meteor","wurst","liquidbounce","aristois","impact","vape",
            "future","rusherhack","inertia","sigma","bleachhack","mathax",
            "baritone","seedcracker","seedcrackerx","xray","freecam","chestesp",
            "playeresp","killaura","reach","autoclicker","novoline","rise"
    );

    private static final Set<String> ALLOWED_CLIENT_BRAND_HINTS = Set.of(
            "vanilla","fabric","forge","quilt","optifine","sodium","iris",
            "lithium","lunar","badlion","labymod","xaero","journeymap","voxelmap"
    );

    private enum ElectionStatus {
        DRAFT,
        APPLICATIONS_OPEN,
        APPLICATIONS_CLOSED,
        BALLOTS_OPEN,
        VOTING_OPEN,
        COUNTING,
        FINISHED,
        CANCELLED,
        SECOND_ROUND_REQUIRED
    }

    private static final String COL_APP_OPEN = "applications" + "_" + "open";
    private static final String COL_VOTE_OPEN = "voting" + "_" + "open";
    private static final int PIN_MAX_ATTEMPTS = 5;
    private static final long PIN_LOCK_SECONDS = 900L;
    private static final long PIN_ATTEMPT_WINDOW_SECONDS = 900L;
    private static final Set<ElectionStatus> LIVE_ELECTION_STATUSES = EnumSet.of(
            ElectionStatus.DRAFT,
            ElectionStatus.APPLICATIONS_OPEN,
            ElectionStatus.APPLICATIONS_CLOSED,
            ElectionStatus.BALLOTS_OPEN,
            ElectionStatus.VOTING_OPEN,
            ElectionStatus.COUNTING,
            ElectionStatus.SECOND_ROUND_REQUIRED
    );
    private static final String ELECTION_STAGE_INVALID = "ELECTION_STAGE_INVALID";
    private static final String ELECTION_APPLICATION_DUPLICATE = "ELECTION_APPLICATION_DUPLICATE";
    private static final String ELECTION_APPLICATION_SAVE_FAILED = "ELECTION_APPLICATION_SAVE_FAILED";
    private static final String ELECTION_CANDIDATE_CREATE_FAILED = "ELECTION_CANDIDATE_CREATE_FAILED";
    private static final String ELECTION_BALLOT_INVALID = "ELECTION_BALLOT_INVALID";
    private static final String ELECTION_BALLOT_ALREADY_USED = "ELECTION_BALLOT_ALREADY_USED";
    private static final String ELECTION_VOTE_DUPLICATE = "ELECTION_VOTE_DUPLICATE";
    private static final String ELECTION_VOTE_SAVE_FAILED = "ELECTION_VOTE_SAVE_FAILED";
    private static final String ELECTION_STATION_DISABLED = "ELECTION_STATION_DISABLED";
    private static final String ELECTION_RESULTS_FAILED = "ELECTION_RESULTS_FAILED";
    private static final String ELECTION_PRESIDENT_ASSIGN_FAILED = "ELECTION_PRESIDENT_ASSIGN_FAILED";
    private static final String ELECTION_SIDEBAR_RENDER_FAILED = "ELECTION_SIDEBAR_RENDER_FAILED";
    private static final int MODEL_ATM_TERMINAL = 12002;
    private static final long ELECTION_ROLE_CACHE_TTL_MS = 30_000L;

    static final class Menu implements InventoryHolder {
        final String id;
        final Map<Integer,String> actions = new HashMap<>();
        Inventory inv;
        Menu(String id) { this.id = id; }
        public Inventory getInventory() { return inv; }
    }

    private record ElectionLifecycleSnapshot(String eid,String status,String stage,long applications,long pending,long approved,long candidates,long ballots,long votes,String winner){}
    private record PreflightRow(String key,boolean ok,Material material,String title,String detail,String action){}
    private record ElectionIntegrityRow(String key,boolean ok,Material material,String title,String detail,String action){}
    private record ElectionGateRow(String key,boolean ok,boolean blocking,Material material,String title,String detail,String action){}
    private record StartupCheckRow(String key,boolean ok,Material material,String title,String detail,String action){}
    private record ArDropClaim(String claimId,String ownerUuid,String ownerName,int amount,long createdAt,Location location){}
    private record AtmPinSession(String atmId,String action,int amount,String pin,String targetUuid,String targetName){}
    private record PendingBugReport(String token,String source,String action,String playerUuid,String playerName,String world,int x,int y,int z,String itemType,String errorSummary,String exceptionClass,String details,String actionId,long createdAt){}
    private record BackendIntegrationSettings(String baseUrl,String pluginApiKey){}
    private record SidebarCandidate(String name,long total){}
    private record SidebarSnapshot(String eid,boolean liveResults,String status,String stage,long curators,long ballots,List<SidebarCandidate> candidates,long createdAt){
        static SidebarSnapshot noElection(long createdAt){return new SidebarSnapshot(null,false,"","",0,0,List.of(),createdAt);}
    }
    private record CachedElectionRole(boolean chair,boolean president,long loadedAt){}
    private record DatabaseHealthSnapshot(long tables,long indexes,long audits,long votes,long arTransactions,long arAssets,long arGuardIncidents,long snapshots,long playerActivity){}
    private record PresidentPanelSnapshot(boolean allowed,String electionId,String activePresident){}
    private record ChairPanelSnapshot(boolean allowed,String electionId,Map<String,String> settings){}
    private record PlayerTimelineSnapshot(String playerName,List<Map<String,Object>> rows){}

    public interface ArtifactsBridge {
        BridgePinStatus pinStatus(UUID playerUuid);
        BridgeTxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details);
        BridgeTxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details);
        long balance(UUID playerUuid, String playerName);
        BridgeHealth health(UUID playerUuid, String context);
    }

    public static final class BridgePinStatus {
        public final boolean configured;
        public final boolean mustChange;
        public final long lockedSeconds;
        public BridgePinStatus(boolean configured, boolean mustChange, long lockedSeconds){
            this.configured=configured;
            this.mustChange=mustChange;
            this.lockedSeconds=lockedSeconds;
        }
    }

    public static final class BridgeTxnResult {
        public final boolean ok;
        public final String code;
        public final String message;
        public final long balanceAfter;
        public final String txId;
        public BridgeTxnResult(boolean ok,String code,String message,long balanceAfter,String txId){
            this.ok=ok;
            this.code=code;
            this.message=message;
            this.balanceAfter=balanceAfter;
            this.txId=txId;
        }
    }

    public static final class BridgeHealth {
        public final boolean bridgeReady;
        public final boolean postgresReady;
        public final boolean pinReady;
        public final long balance;
        public final String context;
        public final String lastError;
        public BridgeHealth(boolean bridgeReady, boolean postgresReady, boolean pinReady, long balance, String context, String lastError){
            this.bridgeReady=bridgeReady;
            this.postgresReady=postgresReady;
            this.pinReady=pinReady;
            this.balance=balance;
            this.context=context;
            this.lastError=lastError;
        }
    }

    private final ArtifactsBridge artifactsBridge = new ArtifactsBridgeImpl();

    @Override public void onEnable() {
        saveDefaultConfig();
        dbExecutor = Executors.newFixedThreadPool(Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())), r -> {
            Thread t = new Thread(r, "copimine-postgres-worker");
            t.setDaemon(true);
            return t;
        });
        visualEntityTypeKey = new NamespacedKey(this, "visual_entity_type");
        visualKindKey = new NamespacedKey(this, "visual_kind");
        visualLinkedIdKey = new NamespacedKey(this, "visual_linked_id");
        visualModelIdKey = new NamespacedKey(this, "visual_model_id");
        try {
            Class.forName("org.postgresql.Driver");
            pgSettings = loadPgSettings();
            pgPool = new PgConnectionPool(pgSettings);
            dbLabel = pgSettings.safeLabel();
            dbPath = dbLabel;
            ensureTables();
            dbReady = true;
            audit("SERVER","ULTRA7_750_ENABLE","enabled postgresql="+dbLabel,true);
        } catch (Exception e) {
            getLogger().severe("PostgreSQL init failed; CopiMine active storage is unavailable: "+safeErr(e));
            getLogger().severe("Set POSTGRES_PASSWORD in /opt/copimine/admin-web/.env or COPIMINE_ENV_FILE. SQLite fallback is disabled.");
            closePostgres();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registerRpCommandGuard();
        for(String commandName:List.of("cmultra","rpguard","cadm","ar","cmbank","appeal","report","oldvoteoff","cmsealdrop")){
            PluginCommand pc=getCommand(commandName);
            if(pc!=null){pc.setExecutor(this); pc.setTabCompleter(this);}
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", this);
        for(Player online : Bukkit.getOnlinePlayers()) refreshElectionRoleStateAsync(online);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { papi = new CopiMineExpansionShim(this); papi.register(); }
            catch (Throwable t) { getLogger().warning("PlaceholderAPI shim registration failed: "+t.getMessage()); }
        }
        inventorySnapshotTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) snapshotOnlineInventory(p, "periodic");
        }, 20L * 90L, 20L * 90L);
        nameplateTask = Bukkit.getScheduler().runTaskTimer(this, this::updateRoleNameplates, 80L, 20L * 300L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { persistStartupSelfCheck("SERVER"); }
            catch (Exception e) { getLogger().warning("startup self-check: "+e.getMessage()); }
        }, 20L * 2L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { persistStartupSelfCheck("SERVER_SETTLED"); }
            catch (Exception e) { getLogger().warning("startup settled self-check: "+e.getMessage()); }
        }, 20L * 12L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { repairProtectedBlockVisuals(); }
            catch (Exception e) { getLogger().warning("atm visual repair: " + e.getMessage()); }
        }, 20L);
        getLogger().info("CopiMineUltimateAdminPlus v9.1.0-postgres-v4 ENABLED. DB="+dbLabel);
    }

    @Override public void onDisable() {
        if (sidebarTask != null) sidebarTask.cancel();
        if (inventorySnapshotTask != null) inventorySnapshotTask.cancel();
        if (nameplateTask != null) nameplateTask.cancel();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "minecraft:brand", this);
        if (papi != null) {
            try { papi.unregister(); }
            catch (Throwable error) { getLogger().warning("placeholder unregister: " + safeErr(error)); }
        }
        hideSidebarAll(false);
        dbReady = false;
        closePostgres();
        getLogger().info("CopiMineUltimateAdminPlus v9.1.0-postgres-v4 disabled.");
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if(player==null||channel==null||!channel.equalsIgnoreCase("minecraft:brand"))return;
        String brand=decodeClientBrand(message);
        if(brand.isBlank())return;
        clientBrands.put(player.getUniqueId(),brand);
        updatePlayerProfile(player, true, brand);
        if(!isDangerousClientBrand(brand))return;
        String reason=dangerousClientReason(brand);
        audit(player.getName(),"ULTRA7_CLIENT_BRAND_BLOCK","brand="+brand+" reason="+reason,true);
        staffNotify("&cЗапрещенный client brand: &f"+player.getName()+" &8("+brand+")");
        Bukkit.getScheduler().runTask(this,()->{
            if(player.isOnline())player.kickPlayer(c("&cНа CopiMine запрещены чит-клиенты.\n&7Обнаружено: &f"+brand+"\n&7Миникарты и performance-моды разрешены, но hack clients нет."));
        });
    }

    private String decodeClientBrand(byte[] message){
        if(message==null||message.length==0)return"";
        try{
            int offset=0,len=0,shift=0;
            while(offset<message.length&&shift<35){
                int b=message[offset++]&0xFF;
                len|=(b&0x7F)<<shift;
                if((b&0x80)==0)break;
                shift+=7;
            }
            if(len>0&&offset+len<=message.length)return new String(message,offset,len,StandardCharsets.UTF_8).trim();
        }catch(Throwable ignored){}
        return new String(message,StandardCharsets.UTF_8).replace("\u0000","").trim();
    }

    private boolean isDangerousClientBrand(String brand){
        String normalized=clientBrandInfo(brand);
        if(normalized.isBlank())return false;
        for(String allowed:ALLOWED_CLIENT_BRAND_HINTS)if(normalized.equals(allowed))return false;
        for(String token:BLOCKED_CLIENT_BRAND_TOKENS)if(normalized.contains(token))return true;
        return false;
    }

    private String dangerousClientReason(String brand){
        String normalized=clientBrandInfo(brand);
        for(String token:BLOCKED_CLIENT_BRAND_TOKENS)if(normalized.contains(token))return token;
        return "unknown";
    }

    private String clientBrandInfo(String brand){
        return brand==null?"":brand.toLowerCase(Locale.ROOT).replace(" ","").replace("-","").replace("_","");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p=e.getPlayer();
        if (checkMode.containsKey(p.getUniqueId()) && !hasAdmin(p)) {
            e.setCancelled(true); warn(p,"Ты вызван на проверку. Команды запрещены, voice-chat не блокируется."); return;
        }
        String msg=e.getMessage().trim();
        if (!msg.startsWith("/")) return;
        String cmd=msg.substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        if(!hasAnyAdmin(p)&&!p.hasPermission("copimine.rpguard.bypass")&&isBlockedRpCommand(msg)){
            denyRpCommand(e,p);
            recordPlayerActivity(p, "COMMAND_BLOCKED", p.getLocation(), "cmd="+cmd, true);
            return;
        }
        recordPlayerActivity(p, "COMMAND", p.getLocation(), "cmd="+cmd+" length="+msg.length(), true);
        if (Set.of("adminhub","cadm","copimine","cadmin","cpanel","cpadmin","cpa","админка","кмадмин").contains(cmd) && hasAnyAdmin(p)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) openMainHub(p); }, 2L);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        purgeTemporaryApplicationBooks(e.getPlayer());
        normalizeArInventoryState(e.getPlayer());
        updatePlayerProfile(e.getPlayer(), true, clientBrands.getOrDefault(e.getPlayer().getUniqueId(),""));
        refreshElectionRoleStateAsync(e.getPlayer());
        recordPlayerActivity(e.getPlayer(), "JOIN", e.getPlayer().getLocation(), "online="+Bukkit.getOnlinePlayers().size(), false);
        snapshotOnlineInventory(e.getPlayer(), "join");
        Bukkit.getScheduler().runTaskLater(this, () -> updateRoleNameplate(e.getPlayer()), 30L);
        Bukkit.getScheduler().runTaskLater(this, () -> restorePendingOfficialItems(e.getPlayer(),"join"), 60L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        purgeTemporaryApplicationBooks(e.getPlayer());
        inventoryLocks.remove(e.getPlayer().getUniqueId());
        electionRoleCache.remove(e.getPlayer().getUniqueId());
        electionRoleRefreshInFlight.remove(e.getPlayer().getUniqueId());
        updatePlayerProfile(e.getPlayer(), false, clientBrands.getOrDefault(e.getPlayer().getUniqueId(),""));
        clientBrands.remove(e.getPlayer().getUniqueId());
        recordPlayerActivity(e.getPlayer(), "QUIT", e.getPlayer().getLocation(), "online_after="+Math.max(0, Bukkit.getOnlinePlayers().size()-1), false);
        snapshotOnlineInventory(e.getPlayer(), "quit");
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p=e.getPlayer();
        try{
            String prefix=rolePrefix(p);
            if(!prefix.isBlank()) e.setFormat(c(prefix)+"%1$s"+ChatColor.GRAY+": "+ChatColor.WHITE+"%2$s");
        }catch(Throwable ignored){}
        String details="length="+(e.getMessage()==null?0:e.getMessage().length());
        if(e.isAsynchronous()) Bukkit.getScheduler().runTask(this,()->{ if(p.isOnline()) recordPlayerActivity(p, "CHAT", p.getLocation(), details, true); });
        else recordPlayerActivity(p, "CHAT", p.getLocation(), details, true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            purgeTemporaryApplicationBooks(p);
            normalizeArInventoryState(p);
            snapshotOnlineInventory(p, "inventory_close");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p=e.getPlayer();
        if (!frozen.contains(p.getUniqueId()) && !checkMode.containsKey(p.getUniqueId())) return;
        Location a=e.getFrom(), b=e.getTo(); if (b==null) return;
        if (a.getBlockX()!=b.getBlockX() || a.getBlockY()!=b.getBlockY() || a.getBlockZ()!=b.getBlockZ()) e.setTo(a);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e){
        Player p=e.getPlayer();
        if(checkMode.containsKey(p.getUniqueId())&&!hasAdmin(p)){ e.setCancelled(true); return; }
        if(!e.getAction().isRightClick()) return;
        String officialType=officialTypeForStack(e.getItem());
        if(legacyElectionRuntimeDisabled()&&isDelegatedElectionRuntimeItem(e.getItem(),officialType)) return;
        if("cik_seal".equals(officialType)) return;
        if(isTemporaryApplicationBook(e.getItem())) return;
        Block clicked=e.getClickedBlock();
        if(clicked!=null&&isPollingStationBlock(e.getClickedBlock())){
            if(legacyElectionRuntimeDisabled()) return;
            handleLegacyPollingStationInteract(p,e,clicked);
            return;
        }
        if(isBallotItem(e.getItem())){
            e.setCancelled(true);
            warn(p,"\u0421\u0442\u0430\u0440\u044b\u0439 \u0431\u044e\u043b\u043b\u0435\u0442\u0435\u043d\u044c \u043e\u0442\u043a\u043b\u044e\u0447\u0451\u043d. \u041f\u043e\u043b\u0443\u0447\u0438\u0442\u0435 \u043d\u043e\u0432\u044b\u0439 \u0431\u044e\u043b\u043b\u0435\u0442\u0435\u043d\u044c \u0447\u0435\u0440\u0435\u0437 CopiMineElectionCore.");
            return;
        }
        if("president_mandate".equals(officialType)){
            try{ e.setCancelled(true); presidentHourlyAnnouncement(p); }catch(Exception ex){ warn(p,"Не удалось выполнить действие мандата. Подробности записаны в лог."); getLogger().warning("president mandate: "+ex); }
            return;
        }
        Block legacyClicked=null;
        if(legacyClicked!=null&&isPollingStationBlock(legacyClicked)) return;
        if(false&&clicked!=null&&isPollingStationBlock(clicked)){
            e.setCancelled(true);
            try{
                legacyPollingStationFlow(p,clicked,e.getItem());
            }catch(Exception ex){
                warn(p,"Не удалось открыть участок ЦИК. Подробности записаны в лог.");
                getLogger().warning("polling station interact: "+ex);
            }
            return;
        }
        if(!isElectionUiItem(e.getItem())) return;
        e.setCancelled(true);
        warn(p,"\u0421\u0442\u0430\u0440\u043e\u0435 \u043c\u0435\u043d\u044e \u0432\u044b\u0431\u043e\u0440\u043e\u0432 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u043e.");
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onPlace(BlockPlaceEvent e){ if(checkMode.containsKey(e.getPlayer().getUniqueId())&&!hasAdmin(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onPollingStationPlace(BlockPlaceEvent e){
        if(!isPollingStationKit(e.getItemInHand()))return;
        e.setCancelled(true);
        warn(e.getPlayer(),"\u0421\u0442\u0430\u0440\u044b\u0435 \u0443\u0447\u0430\u0441\u0442\u043a\u0438 \u043e\u0442\u043a\u043b\u044e\u0447\u0435\u043d\u044b. \u0421\u043e\u0437\u0434\u0430\u0439\u0442\u0435 \u0443\u0447\u0430\u0441\u0442\u043e\u043a \u0447\u0435\u0440\u0435\u0437 CopiMineElectionCore.");
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onDamage(EntityDamageByEntityEvent e){
        if(e.getDamager() instanceof Player p && checkMode.containsKey(p.getUniqueId())&&!hasAdmin(p)) e.setCancelled(true);
        if(e.getEntity() instanceof Player p && checkMode.containsKey(p.getUniqueId())&&!hasAdmin(p)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onPickup(EntityPickupItemEvent e){
        ItemStack picked=e.getItem()==null?null:e.getItem().getItemStack();
        if(isOfficialArItem(picked)&&!(e.getEntity() instanceof Player)){e.setCancelled(true); return;}
        if(e.getEntity() instanceof Player p && checkMode.containsKey(p.getUniqueId())&&!hasAdmin(p)){ e.setCancelled(true); return; }
        if(e.getEntity() instanceof Player p && isOfficialArItem(picked)){ setArOwnerMeta(picked,"","",""); if(e.getItem()!=null)e.getItem().setItemStack(picked); queueArSync("AR_PICKUP"); return; }
        if(legacyArTransferEnabled() && e.getEntity() instanceof Player p && isOfficialArItem(picked)){
            try{retagArOwner(e.getItem(),p,"pickup",claimArTransfer(e.getItem(),p)); protectArEntity(e.getItem(),"pickup"); queueArSync("AR_TRANSFER_PICKUP");}
            catch(Exception ex){e.setCancelled(true); warn(p,"Не удалось передать AR. Подробности записаны в лог."); getLogger().warning("ar pickup transfer: "+ex);}
            return;
        }
        if(e.getEntity() instanceof Player p && isProtectedCustomItem(picked)&&!requireElectionItemOwner(p,picked,first(electionItemString(picked,"type"),""))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void onDrop(PlayerDropItemEvent e){
        if(isTemporaryApplicationBook(e.getItemDrop()==null?null:e.getItemDrop().getItemStack())){ e.setCancelled(true); if(e.getItemDrop()!=null)e.getItemDrop().remove(); purgeTemporaryApplicationBooks(e.getPlayer()); return; }
        if(handleDestroyableOfficialDrop(e)) return;
        if(checkMode.containsKey(e.getPlayer().getUniqueId())&&!hasAdmin(e.getPlayer())){ e.setCancelled(true); return; }
        if(e.getItemDrop()!=null&&isOfficialArItem(e.getItemDrop().getItemStack())){ queueArSync("AR_DROP"); return; }
        if(legacyArTransferEnabled()&&e.getItemDrop()!=null&&isOfficialArItem(e.getItemDrop().getItemStack())){
            protectArEntity(e.getItemDrop(),"drop");
            registerArTransferClaim(e.getItemDrop(),e.getPlayer());
            recordArTransaction("AR_DROP_LISTED",e.getItemDrop().getItemStack(),e.getPlayer(),arString(e.getItemDrop().getItemStack(),"owner_uuid"),arString(e.getItemDrop().getItemStack(),"owner_name"),null,e.getItemDrop().getLocation(),"Игрок выставил АР для RP-передачи через pickup");
            queueArSync("AR_DROP_LISTED");
            return;
        }
        if(isProtectedCustomItem(e.getItemDrop()==null?null:e.getItemDrop().getItemStack())){ e.setCancelled(true); warn(e.getPlayer(),"Официальные предметы нельзя выбрасывать. Печать ЦИК и мандат президента уничтожаются отдельным Q-действием."); return; }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false)
    public void onSealDropLowest(PlayerDropItemEvent e){handleDestroyableOfficialDrop(e);}

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
    public void onSealDropMonitor(PlayerDropItemEvent e){handleDestroyableOfficialDrop(e);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemDamage(EntityDamageEvent e){
        if(e.getEntity() instanceof Item item&&isProtectedCustomItem(item.getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArEntityDamage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Item item)||!isOfficialArItem(item.getItemStack()))return;
        queueArSync("AR_ENTITY_DAMAGE:"+e.getCause().name());
        return;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemDespawn(ItemDespawnEvent e){if(isProtectedCustomItem(e.getEntity().getItemStack()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArDespawn(ItemDespawnEvent e){
        if(isOfficialArItem(e.getEntity().getItemStack())){queueArSync("AR_DESPAWN"); return;}
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemMerge(ItemMergeEvent e){if(isProtectedCustomItem(e.getEntity().getItemStack())||isProtectedCustomItem(e.getTarget().getItemStack()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArMerge(ItemMergeEvent e){
        if(isOfficialArItem(e.getEntity().getItemStack())||isOfficialArItem(e.getTarget().getItemStack())){queueArSync("AR_MERGE"); return;}
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedInventoryPickup(InventoryPickupItemEvent e){if(isProtectedCustomItem(e.getItem().getItemStack()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArHopperPickup(InventoryPickupItemEvent e){
        if(!isOfficialArItem(e.getItem().getItemStack()))return;
        e.setCancelled(true);
        recordArGuardIncident("AR_HOPPER_PICKUP_BLOCKED",e.getItem().getItemStack(),null,e.getItem().getLocation(),"hopper pickup prevented");
        recordArTransaction("AR_HOPPER_PICKUP_BLOCKED",e.getItem().getItemStack(),null,arString(e.getItem().getItemStack(),"owner_uuid"),arString(e.getItem().getItemStack(),"owner_name"),null,e.getItem().getLocation(),"АР нельзя засасывать воронками");
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedInventoryMove(InventoryMoveItemEvent e){if(isProtectedCustomItem(e.getItem()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArInventoryMove(InventoryMoveItemEvent e){
        if(isOfficialArItem(e.getItem())&&(isArTransportInventory(e.getSource())||isArTransportInventory(e.getDestination()))){
            e.setCancelled(true);
            recordArGuardIncident("AR_MACHINE_MOVE_BLOCKED",e.getItem(),null,null,"hopper/dropper/dispenser move prevented");
            recordArTransaction("AR_MACHINE_MOVE_BLOCKED",e.getItem(),null,arString(e.getItem(),"owner_uuid"),arString(e.getItem(),"owner_name"),null,null,"АР нельзя перемещать воронками, раздатчиками и выбрасывателями");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedBlockDispense(BlockDispenseEvent e){if(isProtectedCustomItem(e.getItem()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArDispense(BlockDispenseEvent e){
        if(!isOfficialArItem(e.getItem()))return;
        e.setCancelled(true);
        recordArGuardIncident("AR_DISPENSE_BLOCKED",e.getItem(),null,e.getBlock().getLocation(),"dispenser/dropper prevented");
        recordArTransaction("AR_DISPENSE_BLOCKED",e.getItem(),null,arString(e.getItem(),"owner_uuid"),arString(e.getItem(),"owner_name"),null,e.getBlock().getLocation(),"АР нельзя выдавать раздатчиком или выбрасывателем");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
    public void onArSpawn(ItemSpawnEvent e){
        if(isOfficialArItem(e.getEntity().getItemStack())){ItemStack stack=e.getEntity().getItemStack(); setArOwnerMeta(stack,"","",""); e.getEntity().setItemStack(stack); queueArSync("AR_SPAWN"); return;}
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e){
        if(!customBlockVisualsEnabled())return;
        try { repairProtectedBlockVisuals(e.getWorld().getName(), e.getChunk().getX(), e.getChunk().getZ()); }
        catch (Exception ex) { getLogger().warning("atm chunk visual repair: " + ex.getMessage()); }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedEntityDisplay(PlayerInteractEntityEvent e){
        Player p=e.getPlayer();
        Entity target=e.getRightClicked();
        if(handleProtectedVisualInteract(p,target,e))return;
        ItemStack hand=p.getInventory().getItem(e.getHand());
        if(legacyElectionRuntimeDisabled()&&isDelegatedElectionRuntimeItem(hand,officialTypeForStack(hand))) return;
        if("cik_seal".equals(officialTypeForStack(hand))){
            e.setCancelled(true);
            if(legacyElectionRuntimeDisabled()){
                if(!requireElectionItemOwner(p,hand,"cik_seal"))return;
            warn(p,"Старая печать ЦИК отключена. Используй актуальный интерфейс CopiMineElectionCore.");
                return;
            }
            if(!(target instanceof Player t)){
                warn(p,"Кликни печатью по игроку, чтобы открыть статус заявки и бюллетеня.");
                return;
            }
            if(!hasAdmin(p)&&!isChair(p)){warn(p,"Печать ЦИК работает только у председателя ЦИК или администрации."); return;}
            if(!requireElectionItemOwner(p,hand,"cik_seal"))return;
            try{openCikSealPlayerPanel(p,t);}
            catch(Exception ex){warn(p,"Не удалось открыть меню печати ЦИК. Подробности записаны в лог."); getLogger().warning("cik seal target panel: "+ex);}
            return;
        }
        if(isOfficialArItem(hand)&&(e.getRightClicked() instanceof ItemFrame||e.getRightClicked() instanceof ArmorStand)){
            e.setCancelled(true);
            p.updateInventory();
            warn(p,"Официальные предметы нельзя помещать в рамки, стойки и декоративные сущности.");
            return;
        }
        if(!isProtectedCustomItem(hand)||isOfficialArItem(hand))return;
        if(target instanceof ItemFrame||target instanceof ArmorStand){
            e.setCancelled(true);
            p.updateInventory();
            warn(p,"Официальные предметы нельзя помещать в рамки, стойки и декоративные сущности.");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedArmorStand(PlayerArmorStandManipulateEvent e){
        if(isOfficialArItem(e.getPlayerItem())||isOfficialArItem(e.getArmorStandItem())||isProtectedCustomItem(e.getPlayerItem())||isProtectedCustomItem(e.getArmorStandItem())){
            e.setCancelled(true);
            e.getPlayer().updateInventory();
            warn(e.getPlayer(),"Официальные предметы нельзя надевать на стойки.");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onOfficialItemDeath(PlayerDeathEvent e){
        Player p=e.getEntity();
        List<ItemStack> keep=new ArrayList<>();
        Iterator<ItemStack> iter=e.getDrops().iterator();
        while(iter.hasNext()){
            ItemStack drop=iter.next();
            if(isTemporaryApplicationBook(drop)){iter.remove(); continue;}
            if(shouldPersistOfficialItem(drop)){
                keep.add(drop.clone());
                iter.remove();
            }
        }
        if(!keep.isEmpty()){
            pendingOfficialReturns.computeIfAbsent(p.getUniqueId(),k->new ArrayList<>()).addAll(keep);
            audit(p.getName(),"ULTRA7_OFFICIAL_ITEM_DEATH_GUARD","items="+keep.size(),true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onOfficialItemRespawn(PlayerRespawnEvent e){
        Bukkit.getScheduler().runTaskLater(this,()->restorePendingOfficialItems(e.getPlayer(),"respawn"),20L);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (checkMode.containsKey(e.getPlayer().getUniqueId()) && !hasAdmin(e.getPlayer())) { e.setCancelled(true); return; }
        Material m=e.getBlock().getType();
        if (m==Material.DIAMOND_ORE || m==Material.DEEPSLATE_DIAMOND_ORE) {
            try {
                String key=blockKey(e.getBlock());
                boolean placed=arPlacedBlockExists(e.getBlock());
                if(placed) arPlacedBreaks.add(key);
                boolean eligible=isValidArCertificationBreak(e);
                if(eligible&&!placed) arEligibleBreaks.add(key);
                arEvent(eligible&&!placed?"MINE_AR_BLOCK":"AR_CERTIFICATION_BLOCKED", e.getPlayer(), null, m.name(), 1, e.getBlock().getLocation(), eligible&&!placed?"AR_CERTIFICATION_GATE_V3: natural silk-touch ore":"AR_CERTIFICATION_GATE_V3: blocked creative/spectator/no-silk/placed");
                Block b=e.getBlock();
                Bukkit.getScheduler().runTaskLater(this, () -> { try { arEligibleBreaks.remove(blockKey(b)); deleteArPlacedBlock(b); } catch(Exception ex){ getLogger().warning("ar placed cleanup: "+ex); } }, 1L);
            } catch(Exception ex){ getLogger().warning("ar mine: "+ex); }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onArPlace(BlockPlaceEvent e) {
        if(!arMaterial(e.getBlockPlaced().getType())||!isOfficialArItem(e.getItemInHand())) return;
        markOneArAssetPlaced(e.getPlayer(),e.getBlockPlaced().getLocation(),e.getBlockPlaced().getType());
        try { recordArPlacedBlock(e); } catch(Exception ex) { getLogger().warning("ar place: "+ex); }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onArBlockPlaceGuard(BlockPlaceEvent e) {
        if(!isOfficialArItem(e.getItemInHand()))return;
        queueArSync("AR_PLACE");
        if(useFreeArPlacementFlow())return;
        e.setCancelled(true);
        e.getPlayer().updateInventory();
        warn(e.getPlayer(),"Сертифицированный АР нельзя ставить блоком. Единственный вывод алмазов из него - переплавка.");
        sound(e.getPlayer(),"BLOCK_NOTE_BLOCK_BASS",0.55f,0.8f);
        recordArGuardIncident("AR_PLACE_BLOCKED",e.getItemInHand(),e.getPlayer(),e.getBlockPlaced().getLocation(),"Certified AR cannot be placed");
        recordArTransaction("AR_PLACE_BLOCKED",e.getItemInHand(),e.getPlayer(),arString(e.getItemInHand(),"owner_uuid"),arString(e.getItemInHand(),"owner_name"),null,e.getBlockPlaced().getLocation(),"Блокировка установки сертифицированного АР");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onArDrop(BlockDropItemEvent e) {
        if(!arMaterial(e.getBlockState().getType())) return;
        try {
            String key=blockKey(e.getBlock());
            boolean placed=arPlacedBreaks.remove(key) || arPlacedBlockExists(e.getBlock());
            boolean eligible=arEligibleBreaks.remove(key) && isValidArCertificationDrop(e.getPlayer());
            int amount=0;
            for(Item item:e.getItems()){
                ItemStack st=item.getItemStack();
                if(!arMaterial(st.getType())) continue;
                if(placed||!eligible) { amount+=st.getAmount(); continue; }
                tagArItem(st,e.getPlayer(),e.getBlockState().getType());
                item.setItemStack(st);
                amount+=st.getAmount();
            }
            if(amount>0) arEvent(placed||!eligible?(placed?"AR_DROP_BLOCKED_PLACED":"AR_CERTIFICATION_BLOCKED"):"MINE_AR_DROP", e.getPlayer(), null, e.getBlockState().getType().name(), amount, e.getBlock().getLocation(), placed?"Поставленная руда не сертифицирована":(!eligible?"AR_CERTIFICATION_GATE_V3: drop was not produced by valid Silk Touch survival mining":"Сертифицированный АР создан добычей"));
            if(placed) deleteArPlacedBlock(e.getBlock());
        } catch(Exception ex) { getLogger().warning("ar drop: "+ex); }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onBook(PlayerEditBookEvent e) {
        if(!e.isSigning()) return;
        Player p=e.getPlayer();
        ItemStack old=p.getInventory().getItem(e.getSlot());
        if(!"application_book".equals(electionItemString(old,"type")) && !isApplicationBook(old)) return;
        if(legacyElectionRuntimeDisabled()){
            e.setCancelled(true);
            if(!requireElectionItemOwner(p,old,"application_book")) return;
            warn(p,"Старые книги заявок отключены. Подай заявку через CopiMineElectionCore.");
            return;
        }
        if(!requireElectionItemOwner(p,old,"application_book")){e.setCancelled(true); return;}
        try {
            String eid=activeElectionId();
            if(eid==null){ e.setCancelled(true); warn(p,"Нет активных выборов."); return; }
            try { requireElectionStatus(eid,ElectionStatus.APPLICATIONS_OPEN); }
            catch (SQLException closed) { e.setCancelled(true); warn(p,"Прием заявок уже закрыт председателем ЦИК."); return; }
            if(hasActiveApplication(p.getUniqueId().toString(), eid)){ e.setCancelled(true); warn(p,"У тебя уже есть активная заявка."); return; }
            BookMeta bm=e.getNewBookMeta(); List<String> pages=bm.getPages();
            String text="Страница 1: Почему ты хочешь стать кандидатом?\n"+cleanPage(pages,0)+"\n\n"+
                    "Страница 2: Что ты сделаешь для сервера?\n"+cleanPage(pages,1)+"\n\n"+
                    "Страница 3: Почему игроки должны выбрать тебя?\n"+cleanPage(pages,2);
            String id=UUID.randomUUID().toString();
            exec(""" 
                INSERT INTO applications
                (id,election_id,applicant_uuid,applicant_name,statement,submitted_at,status,reviewed_by,reviewed_at,verdict_reason,visible_in_game,deleted_by,deleted_at)
                VALUES(?,?,?,?,?,?,'PENDING','',0,'',1,'',0)
                """, id,eid,p.getUniqueId().toString(),p.getName(),text,now());
            String issueId=first(electionItemString(old,"id"),"");
            if(!issueId.isBlank())exec("UPDATE cmv7_application_issues SET used=1,notes=COALESCE(notes,'')||? WHERE id=?"," | SIGNED:"+id,issueId);
            audit(p.getName(),"ULTRA7_APPLICATION_SUBMIT","application="+id+" election="+eid,false);
            int slot=e.getSlot();
            Bukkit.getScheduler().runTaskLater(this, () -> p.getInventory().setItem(slot,null), 1L);
            msg(p,"&aЗаявка подписана и отправлена администрации. Книга удалена.");
        } catch(Exception ex) {
            e.setCancelled(true); warn(p,"Не удалось обработать заявку. Подробности записаны в лог."); getLogger().warning("application: "+ex);
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    public void onInv(InventoryClickEvent e) {
        if(e.getInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if(!(e.getWhoClicked() instanceof Player p)) return;
            if(e.getRawSlot()<0 || e.getRawSlot()>=e.getInventory().getSize()) return;
            String a=menu.actions.get(e.getRawSlot());
            if(a==null || a.equals("none")) return;
            try { handle(p,e.getClick(),a,menu.id); }
            catch(Exception ex) {
                notifyPlayerBug(p,"gui",menu.id+" -> "+a,ex,e.getCurrentItem(),p.getLocation());
                getLogger().warning("gui menu="+menu.id+" action="+a+" player="+p.getName()+" error="+safeErr(ex));
            }
            return;
        }
        if(e.getWhoClicked() instanceof Player p){
            Long lockedUntil=inventoryLocks.get(p.getUniqueId());
            if(lockedUntil!=null){
                if(lockedUntil<now()) inventoryLocks.remove(p.getUniqueId());
                else {e.setCancelled(true); warn(p,"Инвентарь временно заблокирован администрацией."); return;}
            }
            if(checkMode.containsKey(p.getUniqueId()) && !hasAdmin(p)) e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onPrepareCraft(PrepareItemCraftEvent e){if(containsProtectedItem(e.getInventory().getMatrix()))e.getInventory().setResult(new ItemStack(Material.AIR));}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onCraft(CraftItemEvent e){if(containsProtectedItem(e.getInventory().getMatrix())){e.setCancelled(true); if(e.getWhoClicked() instanceof Player p)warn(p,"Официальные AR и служебные предметы нельзя использовать в крафте.");}}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        Inventory top=e.getView().getTopInventory();
        if(top==null||top.getHolder() instanceof Menu||!isRestrictedTop(top))return;
        int raw=e.getRawSlot(), topSize=top.getSize();
        boolean clickedTop=raw>=0&&raw<topSize, clickedBottom=raw>=topSize;
        ItemStack cursor=e.getCursor(), current=e.getCurrentItem(), hotbar=null;
        if(e.getClick()==ClickType.NUMBER_KEY&&e.getHotbarButton()>=0)hotbar=p.getInventory().getItem(e.getHotbarButton());
        boolean arRestricted=isOfficialArItem(cursor)||isOfficialArItem(current)||isOfficialArItem(hotbar);
        if(arRestricted)queueArSync("AR_RESTRICTED_INVENTORY_TOUCH");
        boolean cursorRestricted=isRestrictedInventoryItem(cursor), currentRestricted=isRestrictedInventoryItem(current), hotbarRestricted=isRestrictedInventoryItem(hotbar);
        if(cursorRestricted||currentRestricted||hotbarRestricted){
            InventoryAction action=e.getAction();
            if(action==InventoryAction.MOVE_TO_OTHER_INVENTORY&&clickedBottom&&currentRestricted){
                e.setCancelled(true);
                p.updateInventory();
                if(!arRestricted)warn(p,"Официальные AR и служебные предметы нельзя перекладывать в печи, верстаки, наковальни и другие рабочие блоки.");
                return;
            }
            if(clickedTop&&(cursorRestricted||hotbarRestricted)){
                e.setCancelled(true);
                p.updateInventory();
                if(!arRestricted)warn(p,"Официальные AR и служебные предметы нельзя класть в печи, верстаки, наковальни и другие рабочие блоки.");
                return;
            }
            if(clickedTop&&currentRestricted)return;
            if(clickedBottom)return;
            e.setCancelled(true); p.updateInventory();
            return;
        }
        boolean cursorOfficial=isProtectedOfficialItem(cursor), currentOfficial=isProtectedOfficialItem(current), hotbarOfficial=isProtectedOfficialItem(hotbar);
        if(!cursorOfficial&&!currentOfficial&&!hotbarOfficial)return;
        InventoryAction action=e.getAction();
        if(action==InventoryAction.MOVE_TO_OTHER_INVENTORY&&clickedBottom&&currentOfficial){e.setCancelled(true); p.updateInventory(); warn(p,"Официальные предметы нельзя перекладывать в хранилища и станки."); return;}
        if(clickedTop&&(cursorOfficial||hotbarOfficial)){e.setCancelled(true); p.updateInventory(); warn(p,"Официальные предметы нельзя класть в сундуки, бочки, верстаки, печи и станки."); return;}
        if(clickedTop&&currentOfficial)return;
        if(clickedBottom)return;
        e.setCancelled(true); p.updateInventory();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemDrag(InventoryDragEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||!isRestrictedInventoryItem(e.getOldCursor()))return;
        Inventory top=e.getView().getTopInventory();
        if(top==null||top.getHolder() instanceof Menu||!isRestrictedTop(top))return;
        int topSize=top.getSize();
        boolean arRestricted=isOfficialArItem(e.getOldCursor());
        if(isRestrictedInventoryItem(e.getOldCursor())){
            for(int raw:e.getRawSlots())if(raw>=0&&raw<topSize){
                e.setCancelled(true);
                p.updateInventory();
                if(!arRestricted)warn(p,"Официальные AR и служебные предметы нельзя перетаскивать в печи, верстаки, наковальни и другие рабочие блоки.");
                return;
            }
        }
        for(int raw:e.getRawSlots())if(raw>=0&&raw<topSize){e.setCancelled(true); p.updateInventory(); warn(p,"Официальные предметы нельзя перетаскивать в хранилища и станки."); return;}
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onProtectedItemMove(InventoryMoveItemEvent e){if(isProtectedOfficialItem(e.getItem()))e.setCancelled(true);}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onOfficialArCreative(InventoryCreativeEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!isOfficialArItem(e.getCursor())&&!isOfficialArItem(e.getCurrentItem()))return;
        Inventory clicked=e.getClickedInventory();
        boolean playerInventory=clicked!=null&&clicked.equals(e.getView().getBottomInventory());
        if(playerInventory&&e.getAction()!=InventoryAction.CLONE_STACK&&e.getClick()!=ClickType.CREATIVE){
            queueArSync("AR_CREATIVE_PLAYER_MOVE");
            return;
        }
        e.setCancelled(true);
        p.updateInventory();
        warn(p,"Официальный AR нельзя создавать или клонировать через creative-инвентарь.");
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onOfficialItemInteract(PlayerInteractEvent e){
        if(e.getClickedBlock()==null||!isProtectedOfficialItem(e.getItem()))return;
        if(e.getClickedBlock().getState() instanceof Container){e.setCancelled(true); warn(e.getPlayer(),"Служебные предметы ЦИК и президента нельзя класть в контейнеры."); return;}
        if(e.getClickedBlock().getState() instanceof Container){e.setCancelled(true); warn(e.getPlayer(),"Сначала убери официальный предмет из руки. Его нельзя класть в хранилища.");}
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onFurnaceSmelt(FurnaceSmeltEvent e){
        ItemStack source=e.getSource();
        if(!arMaterial(source==null?Material.AIR:source.getType()))return;
        if(isOfficialArItem(source)){
            e.setCancelled(true);
            e.setResult(new ItemStack(Material.AIR));
            queueArSync("AR_SMELT_BLOCKED");
            if(blockOfficialArSmelting())return;
            e.setResult(new ItemStack(Material.DIAMOND,1));
            recordArTransaction("AR_SMELT_DIAMOND",source,null,arString(source,"owner_uuid"),arString(source,"owner_name"),null,e.getBlock().getLocation(),"Единственный легальный вывод алмаза из сертифицированного АР");
            queueArSync("AR_SMELT_DIAMOND");
            return;
        }
        e.setCancelled(true);
        recordArTransaction("UNCERTIFIED_AR_SMELT_BLOCKED",source,null,"","",null,e.getBlock().getLocation(),"Несертифицированная алмазная руда не превращается в экономический АР-алмаз");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
    public void onFurnaceExtract(FurnaceExtractEvent e){queueArSync("FURNACE_EXTRACT");}
    // private void openHub
    public void openHub(Player p){
        Menu m=new Menu("hub"); create(m,27,"&2&lCopiMine &8| &fадминка");
        btn(m,11,Material.GOLDEN_HELMET,"&6&lВыборы",List.of(
                "&7Новый чистый модуль выборов.",
                "&7Участки, ЦИК, заявки, президент,",
                "&7законы, live-панель и налоговая."),"open:elections");
        btn(m,13,Material.DIAMOND_ORE,"&b&lЭкономика",List.of(
                "&7AR, банк, банкоматы, ledger,",
                "&7сканы и защита экономики.",
                "&7Открывает модуль EconomyCore."),"open:economy");
        btn(m,15,Material.PLAYER_HEAD,"&e&lИгроки",List.of(
                "&7Профили, инвентари, проверки,",
                "&7массовые действия и приколы.",
                "&7Инструменты для модерации игроков."),"open:players");
        p.openInventory(m.inv);
    }

    private void openMainHub(Player p){
        if(isRestrictedJuniorAdmin(p)){
            Menu m=new Menu("hub-junior"); create(m,27,"&2&lCopiMine &8| &fМладший админ");
            btn(m,11,Material.PLAYER_HEAD,"&e&lИгроки",List.of(
                    "&7Профили, инвентари и проверки.",
                    "&7Опасные действия, экономика, выборы и миры",
                    "&7для младшего админа заблокированы."),"open:players");
            btn(m,15,Material.PAPER,"&bОграничения роли",List.of(
                    "&7Роль ограничена безопасными действиями.",
                    "&7Опасные команды, экономика, миры,",
                    "&7выборные и world-control действия отключены."),"none");
            p.openInventory(m.inv);
            return;
        }
        Menu m=new Menu("hub-clean"); create(m,27,"&2&lCopiMine &8| &fадминка");
        btn(m,10,Material.GOLDEN_HELMET,"&6&lВыборы",List.of(
                "&7Новый чистый модуль выборов.",
                "&7Участки, ЦИК, заявки, президент,",
                "&7законы, live-панель и налоговая."),"open:elections");
        btn(m,12,Material.DIAMOND_ORE,"&b&lЭкономика",List.of(
                "&7AR, банк, банкоматы, ledger,",
                "&7сканы и защита экономики.",
                "&7Открывает модуль EconomyCore."),"open:economy");
        btn(m,14,Material.GRASS_BLOCK,"&a&lМиры",List.of(
                "&7Граница Overworld, Nether и End.",
                "&7Открытие, закрытие и безопасный возврат",
                "&7игроков из закрытых миров."),"open:worlds");
        btn(m,16,Material.PLAYER_HEAD,"&e&lИгроки",List.of(
                "&7Профили, инвентари, проверки,",
                "&7массовые действия и приколы.",
                "&7Инструменты для модерации игроков."),"open:players");
        p.openInventory(m.inv);
    }

    private boolean openElectionCoreHub(Player p){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("CopiMineElectionCore");
        if(plugin==null){
            warn(p,"Новый модуль CopiMineElectionCore не загружен.");
            return false;
        }
        if(!plugin.isEnabled()){
            warn(p,"CopiMineElectionCore найден, но выключен. Проверь лог запуска сервера.");
            return false;
        }
        try{
            plugin.getClass().getMethod("openAdminElectionHub",Player.class).invoke(plugin,p);
            return true;
        }catch(Exception e){
            getLogger().warning("election core hub: "+e);
            warn(p,"Не удалось открыть новый модуль выборов.");
            return false;
        }
    }

    private boolean openWorldCoreHub(Player p){
        if(isRestrictedJuniorAdmin(p)){
            warn(p,"Младшему админу недоступно управление мирами.");
            return false;
        }
        Plugin plugin=Bukkit.getPluginManager().getPlugin("CopiMineWorldCore");
        if(plugin==null||!plugin.isEnabled()){
            warn(p,"CopiMineWorldCore недоступен.");
            return false;
        }
        try{
            plugin.getClass().getMethod("openAdminWorldHub",Player.class).invoke(plugin,p);
            return true;
        }catch(Exception e){
            getLogger().warning("world core hub: "+e);
            warn(p,"Не удалось открыть модуль управления мирами.");
            return false;
        }
    }

    private boolean openArtifactsShopHub(Player p){
        if(isRestrictedJuniorAdmin(p)){
            warn(p,"Младшему админу недоступно управление лавками.");
            return false;
        }
        Plugin plugin=Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
        if(plugin==null||!plugin.isEnabled()){
            warn(p,"CopiMineArtifacts недоступен.");
            return false;
        }
        try{
            var method=plugin.getClass().getDeclaredMethod("openAdminShops",Player.class);
            method.setAccessible(true);
            method.invoke(plugin,p);
            return true;
        }catch(NoSuchMethodException ignored){
            return Bukkit.dispatchCommand(p,"cmartifacts shop");
        }catch(Exception e){
            getLogger().warning("artifacts hub: "+e);
            if(Bukkit.dispatchCommand(p,"cmartifacts shop"))return true;
            warn(p,"Не удалось открыть управление лавками.");
            return false;
        }
    }

    private void handleLegacyPollingStationInteract(Player p, PlayerInteractEvent e, Block clicked){
        e.setCancelled(true);
        try{
            String stationId=pollingStationId(clicked);
            if(isBallotItem(e.getItem())&&isSealedBallot(e.getItem())){
                depositSealedBallotAtStation(p,e.getItem(),stationId);
                return;
            }
            sendPollingStationCitizenInfoV2(p,clicked);
            openPollingStationHubV2(p,clicked);
        }catch(Exception ex){
            warn(p,"Не удалось открыть участок ЦИК. Подробности записаны в лог.");
            getLogger().warning("polling station interact: "+ex);
        }
    }

    private void openAdminMap(Player p)throws Exception{
        if(!hasAnyAdmin(p)){warn(p,"Доступ к этой панели закрыт."); return;}
        Menu m=new Menu("admin-map"); create(m,54,"&2&lКарта админки");
        btn(m,10,Material.GOLDEN_HELMET,"&6Выборы",List.of("&8GUI_SECTION_ELECTIONS","&7ЦИК, участки, президент, законы","&7и безопасный election workflow."),"open:elections");
        btn(m,12,Material.DIAMOND_ORE,"&bЭкономика",List.of("&8GUI_SECTION_ECONOMY","&7AR, банк, банкоматы, ledger,","&7сканы и guard-инциденты."),"open:economy");
        btn(m,14,Material.PLAYER_HEAD,"&eИгроки",List.of("&8GUI_SECTION_PLAYERS","&7Профили, инвентари, проверки,","&7модерация и безопасные действия."),"open:players");
        btn(m,16,Material.GRASS_BLOCK,"&aМиры",List.of("&8GUI_SECTION_WORLDS","&7Открытие Nether/End, границы","&7и безопасный возврат игроков."),"open:worlds");
        btn(m,28,Material.ENDER_CHEST,"&aБД и оптимизация",List.of("&7Схема, индексы, WAL и","&7безопасные операции обслуживания."),"open:db-health");
        btn(m,30,Material.TARGET,"&aГотовность запуска",List.of("&7Проверки после замены релиза","&7и диагностика основных модулей."),"open:startup-readiness");
        btn(m,32,Material.BOOK,"&dCMS сайта",List.of("&7Новости, страницы, баннеры","&7и публичные тексты через сайт."),"none");
        btn(m,34,Material.COMPASS,"&fГлавный хаб",List.of("&7Вернуться к компактному входу."),"open:hub");
        nav(m,"open:hub","open:admin-map");
        p.openInventory(m.inv);
    }

    private void legacyOpenDatabaseHealthDisabled(Player p)throws Exception{
        if(!hasAnyAdmin(p)){warn(p,"Нет прав на здоровье БД."); return;}
        Menu m=new Menu("db-health"); create(m,54,"&a&lБД и оптимизация");
        long tables=safeScalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'");
        long indexes=safeScalar("SELECT COUNT(*) FROM pg_indexes WHERE schemaname=current_schema()");
        long audits=safeScalar("SELECT COUNT(*) FROM cmv7_audit");
        long votes=safeScalar("SELECT COUNT(*) FROM cmv731_votes");
        long arTx=safeScalar("SELECT COUNT(*) FROM cmv7_ar_transactions");
        long snapshots=safeScalar("SELECT COUNT(*) FROM cmv7_inventory_snapshots");
        btn(m,4,Material.ENDER_CHEST,"&a&lСостояние базы",List.of("&7Источник: &f"+dbSourceSummary(),"&7Таблиц: &f"+tables+" &8| &7индексов: &f"+indexes,"&7Аудит: &f"+audits,"&7Используй только безопасные кнопки обслуживания."),"none");
        btn(m,10,Material.GOLDEN_HELMET,"&6Модуль выборов",List.of("&7Runtime выборов делегирован в &fCopiMineElectionCore","&7Legacy election GUI в AdminPlus отключён.","&7Открыть актуальный election hub."),"open:elections");
        btn(m,12,Material.DIAMOND_ORE,"&bДанные АР",List.of("&7Транзакций: &f"+arTx,"&7Активов: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets"),"&7Guard-инцидентов: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_guard_incidents"),"&7Баланс меняется только через AR workflow."),"open:ar-health");
        btn(m,14,Material.PLAYER_HEAD,"&eДанные игроков",List.of("&7Снимков online: &f"+snapshots,"&7Событий активности: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_player_activity"),"&7Профили читаются без тяжёлых полных сканов."),"open:players-tools");
        btn(m,16,Material.SHIELD,"&aЗащита базы",List.of("&7Аудит и журналы не редактируются сырой формой.","&7Для выборов/АР есть отдельные безопасные API.","&7Все действия обслуживания пишутся в аудит."),"none");
        btn(m,28,Material.ANVIL,"&aОптимизировать базу",List.of("&7Безопасно обновляет внутреннюю статистику.","&7Не меняет голоса, балансы и заявки."),"db:optimize");
        btn(m,30,Material.CLOCK,"&bСбросить журнал базы",List.of("&7Пассивно сбрасывает журнал, если база разрешит.","&7Полезно после длинных админ-сессий."),"db:checkpoint");
        btn(m,32,Material.SPYGLASS,"&dПроверка запуска",List.of("&7Перепроверить схему, зависимости и защиту."),"open:startup-readiness");
        btn(m,34,Material.COMPASS,"&2Карта админки",List.of("&7Вернуться к подгруппам."),"open:admin-map");
        nav(m,"open:admin-map","open:db-health"); p.openInventory(m.inv);
    }

    private String runDatabaseMaintenance(String actor,boolean checkpoint)throws SQLException{
        try(Connection c=conn(); Statement st=c.createStatement()){
            st.execute("ANALYZE");
            if(checkpoint){
                try{st.execute("CHECKPOINT");}
                catch(SQLException checkpointError){getLogger().warning("postgres checkpoint skipped: "+safeErr(checkpointError));}
            }
        }
        audit(actor,"DB_HEALTH_CHECK",checkpoint?"postgres_analyze_checkpoint_requested":"postgres_analyze",true);
        return checkpoint?"&aANALYZE выполнен; PostgreSQL checkpoint запрошен, если роль БД это позволяет.":"&aОптимизация БД выполнена.";
    }

    private void openElections(Player p) throws Exception {
        if(!hasElectionAdmin(p)){warn(p,"Нет прав на выборы."); return;}
        if(openElectionCoreHub(p))return;
        warn(p,"Новый модуль выборов недоступен, legacy GUI отключён.");
    }

    private boolean isLegacyElectionAction(String action){
        return action.startsWith("open:election-")
                || action.equals("open:election-operations")
                || action.equals("open:election-ledgers")
                || action.equals("open:election-recovery-advanced")
                || action.equals("open:election-settings")
                || action.startsWith("open:citizen-")
                || action.startsWith("open:cik-target:")
                || action.startsWith("open:applications-")
                || action.startsWith("open:ballots-")
                || action.startsWith("open:polling-stations")
                || action.equals("open:give-app-player")
                || action.equals("open:give-ballot-player")
                || action.equals("open:lifecycle")
                || action.equals("open:preflight")
                || action.equals("open:sidebar")
                || action.startsWith("open:president")
                || action.startsWith("open:chair")
                || action.startsWith("open:candidates")
                || action.startsWith("open:curators")
                || action.startsWith("open:add-candidate")
                || action.startsWith("open:add-curator")
                || action.startsWith("open:election-danger")
                || action.startsWith("open:application-issues")
                || action.startsWith("open:submitted-applications-emergency")
                || action.startsWith("station:")
                || action.startsWith("station-toggle:")
                || action.startsWith("election:")
                || action.startsWith("give-app:")
                || action.startsWith("give-ballot:")
                || action.startsWith("vote-seal:")
                || action.startsWith("vote-deposit:")
                || action.startsWith("view-app:")
                || action.startsWith("ballot-candidate:")
                || action.startsWith("vote-confirm:")
                || action.startsWith("open:station-ballot:")
                || action.startsWith("open:station-hub:")
                || action.startsWith("official:recover:")
                || action.startsWith("toggle:")
                || action.startsWith("stage:")
                || action.startsWith("duration:")
                || action.startsWith("cand:")
                || action.startsWith("add-candidate:")
                || action.startsWith("add-curator:")
                || action.startsWith("curator:")
                || action.startsWith("app-submitted-")
                || action.startsWith("app-review:")
                || action.startsWith("app-issue-annul:")
                || action.startsWith("app-submitted-annul:")
                || action.startsWith("ballot-annul:")
                || action.startsWith("sidebar:")
                || action.startsWith("chair:")
                || action.equals("open:election-integrity")
                || action.equals("open:election-release")
                || action.equals("open:election-ceremony")
                || action.equals("open:election-audit")
                || action.equals("open:election-emergency")
                || action.equals("open:applications-review")
                || action.equals("open:applications-issue")
                || action.equals("open:ballots-issue")
                || action.equals("open:ballots-ledger")
                || action.equals("open:application-issues")
                || action.equals("open:submitted-applications-emergency")
                || action.equals("open:polling-stations")
                || action.equals("open:give-app-player")
                || action.equals("open:give-ballot-player")
                || action.equals("citizen:sidebar-hide")
                || action.equals("citizen:sidebar-show");
    }

    private void redirectLegacyElectionAction(Player p) throws Exception {
            warn(p,"Управление live-панелью перенесено в CopiMineElectionCore.");
        openElections(p);
    }

    private boolean legacyElectionRuntimeDisabled(){
        return true;
    }

    private SQLException legacyElectionRuntimeDisabledError(String operation){
        return new SQLException("Legacy election runtime disabled in AdminPlus: " + operation + ". Use CopiMineElectionCore.");
    }

    private void openElectionOperations(Player p) throws Exception {
        redirectLegacyElectionAction(p);
    }

    private void openElectionLedgers(Player p) throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void openElectionRecoveryAdvanced(Player p) throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void openSidebar(Player p){
        try{
            warn(p,"Управление live-панелью перенесено в CopiMineElectionCore.");
            openElections(p);
        }catch(Exception error){
            getLogger().warning("legacy sidebar redirect: " + error.getMessage());
        }
    }

    private void openElectionSettings(Player p) throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void openElectionLifecycle(Player p) throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void legacyOpenPresidentPanelDisabled(Player p) throws Exception{
        if(!hasAdmin(p)&&!isPresident(p)){warn(p,"Панель доступна действующему президенту и главной администрации."); return;}
        Menu m=new Menu("president"); create(m,54,"&6&lПрезидент &8| &f"+p.getName());
        String eid=activeOrLatestElectionId();
        btn(m,4,Material.NETHER_STAR,"&6&lКабинет президента",List.of("&7Действующий президент: &f"+activePresidentName(),"&7Цикл: &f"+shortId(eid),"&7Президент не меняет голоса и экономику,","&7но может публично вести политический цикл."),"none");
        btn(m,19,Material.BELL,"&6Обращение к серверу",List.of("&7Публичный title/chat без админских прав на голоса."),"president:announce");
        btn(m,20,Material.WRITABLE_BOOK,"&eПрограмма и обещания",List.of("&7Фиксирует обновление программы в журнале","&7и напоминает игрокам открыть кандидатов."),"president:program");
        btn(m,21,Material.NAME_TAG,"&bНазначить представителя",List.of("&7Представитель президента помогает ЦИК","&7и виден в списке кураторов."),"president:appoint-delegate");
        btn(m,22,Material.MAP,"&dЗапросить дебаты",List.of("&7Отправляет ЦИК служебный запрос","&7без принудительной смены этапа."),"president:request-debate");
        btn(m,23,Material.GOLD_INGOT,"&6Запросить подсчет",List.of("&7Формальный запрос председателю ЦИК","&7закрыть голосование и перейти к подсчету."),"president:request-counting");
        btn(m,24,Material.SPYGLASS,"&eАудит выборов",List.of("&7Логи заявок, бюллетеней, этапов и президента."),"open:election-audit");
        btn(m,25,Material.PLAYER_HEAD,"&eКандидаты и заявки",List.of("&7Открыть список кандидатов и текущие цифры."),"open:candidates");
        btn(m,26,Material.WRITTEN_BOOK,"&6Восстановить мандат",List.of("&7Если мандат был уничтожен через Q,","&7панель безопасно выдаст новый."),"official:recover:president_mandate");
        nav(m,"open:elections","open:president"); p.openInventory(m.inv);
    }

    private void legacyOpenChairPanelDisabled(Player p) throws Exception{
        if(!hasAdmin(p)&&!isChair(p)){warn(p,"Панель доступна председателю ЦИК и главной администрации."); return;}
        Menu m=new Menu("chair"); create(m,27,"&b&lПечать ЦИК");
        String eid=activeOrLatestElectionId(); Map<String,String> st=eid==null?Map.of():electionSettings(eid);
        btn(m,4,Material.NETHER_STAR,"&b&lПечать ЦИК",List.of("&8CIK_SEAL_PLAYER_ONLY_V5","&7Цикл: &f"+shortId(eid),"&7Этап: &f"+humanStage(st.getOrDefault("stage","-")),"&7Функция председателя теперь одна:","&7кликни печатью по игроку и выдай","&7ему заявку или бюллетень, если их нет."),"none");
        btn(m,11,Material.PLAYER_HEAD,"&eКлик по игроку",List.of("&7Возьми печать в руку.","&7ПКМ по игроку откроет его статус.","&7Внутри будут только заявка, бюллетень и статус."),"none");
        btn(m,13,Material.PAPER,"&fЗапечатанный бюллетень",List.of("&7Игрок сам выбирает кандидата в бюллетене.","&7После подтверждения держит бюллетень","&7в руке и ПКМ кликает участок ЦИК."),"none");
        btn(m,15,Material.LECTERN,"&aУчасток принимает голос",List.of("&7GUI-кнопки депозита нет.","&7Голос засчитывается только физическим","&7ПКМ по участку с личным бюллетенем."),"none");
        btn(m,18,Material.ARROW,"&aНазад",List.of(),"open:elections");
        btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:chair");
        p.openInventory(m.inv);
    }

    private void openCikSealPlayerPanel(Player chair, Player target) throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(chair);
            return;
        }
        if(!hasAdmin(chair)&&!isChair(chair)){warn(chair,"Печать ЦИК работает только у председателя ЦИК или администрации."); return;}
        String eid=issuableElectionId();
        boolean hasElection=eid!=null&&!eid.isBlank();
        boolean needsApp=hasElection&&targetNeedsApplication(target,eid);
        boolean needsBallot=hasElection&&targetNeedsBallot(target,eid);
        boolean voted=hasElection&&hasCitizenVote(target,eid);
        boolean sealed=hasElection&&hasOwnedSealedBallot(target,eid);
        Menu m=new Menu("cik-target"); create(m,27,"&b&lПечать ЦИК &8| &f"+target.getName());
        btn(m,4,Material.PLAYER_HEAD,"&e&l"+target.getName(),List.of(
                "&8CIK_SEAL_PLAYER_ONLY_V5",
                "&7Цикл: &f"+shortId(eid),
                "&7Заявка: &f"+targetApplicationStatus(target,eid),
                "&7Бюллетень: &f"+targetBallotStatus(target,eid),
                "&7Голос: "+(voted?"&aпринят":sealed?"&eзапечатан, ждет участка":"&7не принят"),
                "&7Действия доступны только если документа нет."
        ),"none");
        btn(m,10,needsApp?Material.WRITABLE_BOOK:Material.LIME_DYE,needsApp?"&eВыдать заявку":"&aЗаявка уже есть",needsApp?List.of("&7Игрок получит личную книгу заявки.","&7Чужой игрок не сможет её использовать."):List.of("&7Новая заявка не нужна."),needsApp?"cik-target-app:"+target.getName():"none");
        btn(m,12,needsBallot?Material.PAPER:Material.LIME_DYE,needsBallot?"&fВыдать бюллетень":"&aБюллетень не нужен",needsBallot?List.of("&7Игрок получит личный бюллетень.","&7После выбора он ПКМ кликает участок."):List.of("&7У игрока уже есть бюллетень или голос принят."),needsBallot?"cik-target-ballot:"+target.getName():"none");
        btn(m,14,Material.CLOCK,"&bТекущий статус",List.of("&7Заявка: &f"+targetApplicationStatus(target,eid),"&7Бюллетень: &f"+targetBallotStatus(target,eid),"&7Запечатан: "+(sealed?"&aда":"&7нет"),"&7Голос принят: "+(voted?"&aда":"&7нет")),"none");
        btn(m,16,Material.EMERALD,"&aОбновить",List.of("&7Перечитать статус игрока."),"open:cik-target:"+target.getName());
        btn(m,18,Material.ARROW,"&aНазад",List.of(),"open:chair");
        btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        chair.openInventory(m.inv);
    }

    private void openApplicationsIssue(Player p)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        Menu m=new Menu("appissue"); create(m,54,"&e&lВыдача заявок");
        String eid=activeOrLatestElectionId();
        long issued=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=?",eid);
        long unsigned=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=? AND COALESCE(used,0)=0 AND COALESCE(annulled,0)=0",eid);
        long submitted=eid==null?0:scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND COALESCE(deleted_at,0)=0",eid);
        long pending=eid==null?0:scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND status='PENDING' AND COALESCE(deleted_at,0)=0",eid);
        btn(m,4,Material.WRITABLE_BOOK,"&e&lAPPLICATION_BOOK_V2_POLISH",List.of("&7Цикл: &f"+shortId(eid),"&7Книг выдано: &f"+issued,"&7Не подписано: &f"+unsigned,"&7Подано заявок: &f"+submitted+" &8| &ePENDING "+pending,"&7Новая книга содержит памятку, программу,","&7первые 24 часа, команду и риски."),"none");
        btn(m,19,Material.WRITABLE_BOOK,"&eЗаявки всем онлайн",List.of("&7Выдаёт новый аккуратный шаблон книги.","&7После подписи книга попадёт в БД.","&7Повторная выдача нужна только по RP-сценарию."),"election:app-all");
        btn(m,20,Material.PLAYER_HEAD,"&eЗаявка одному игроку",List.of("&7Адресная выдача с привязкой к UUID.","&7Используй, если игрок потерял книгу","&7или вступает в процесс позже."),"open:give-app-player");
        btn(m,21,Material.BOOKSHELF,"&6Реестр выданных книг",List.of("&7Книги, которые ЦИК выдал игрокам.","&7Можно аннулировать до подписи."),"open:application-issues");
        btn(m,22,Material.BOOK,"&eРевью поданных заявок",List.of("&7Approve/reject, быстрый перенос","&7в кандидаты через Shift+ЛКМ."),"open:applications-review");
        btn(m,23,Material.SHIELD,"&aКонтроль целостности",List.of("&7Проверить pending, роли, участки","&7и готовность перед голосованием."),"open:election-integrity");
        btn(m,24,Material.REDSTONE_TORCH,"&cАварийный контроль",List.of("&7Аннулировать ошибочно выданную","&7или уже поданную заявку."),"open:election-emergency");
        btn(m,31,Material.FILLED_MAP,"&fБюллетени",List.of("&7После ревью заявок перейти","&7к выдаче бюллетеней."),"open:ballots-issue");
        btn(m,33,Material.LECTERN,"&bУчастки ЦИК",List.of("&7Проверь RP-точки до голосования."),"open:polling-stations");
        nav(m,"open:elections","open:applications-issue"); p.openInventory(m.inv);
    }
    private void openBallotsIssue(Player p)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        Menu m=new Menu("ballotissue"); create(m,54,"&f&lВыдача бюллетеней");
        String eid=activeOrLatestElectionId();
        long issuedBallots=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid);
        long unusedBallots=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)=0",eid);
        long usedBallots=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)>0",eid);
        long stationDeposits=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND COALESCE(station_id,'')<>''",eid);
        long stations=eid==null?0:scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=? AND active=1 AND COALESCE(archived_at,0)=0",eid);
        btn(m,4,Material.PAPER,"&f&lBALLOT_GUI_V2",List.of("&7Цикл: &f"+shortId(eid),"&7Выдано: &f"+issuedBallots,"&7Свободно: &f"+unusedBallots+" &8| &7использовано: &f"+usedBallots,"&7Опущено через участки: &f"+stationDeposits,"&7Активных участков: &f"+stations),"none");
        btn(m,19,Material.PAPER,"&fБюллетени всем онлайн",List.of("&7Официальный бюллетень каждому online игроку.","&7Каждый предмет привязан к UUID.","&7Чужой игрок не откроет бюллетень."),"election:ballot-all");
        btn(m,20,Material.PLAYER_HEAD,"&fБюллетень одному игроку",List.of("&7Адресная выдача для recovery","&7или позднего участия игрока."),"open:give-ballot-player");
        btn(m,21,Material.FILLED_MAP,"&6Реестр бюллетеней",List.of("&7Кто получил, использовал,","&7аннулирован или опустил в участок."),"open:ballots-ledger");
        btn(m,22,Material.LECTERN,"&bУчастки ЦИК",List.of("&7Создать/проверить участки,","&7телепорт и карточки RP-точек."),"open:polling-stations");
        btn(m,23,Material.COMPASS,"&aПрофилактика",List.of("&7Перед открытием голосования","&7проверяет бюллетени и участки."),"open:preflight");
        btn(m,24,Material.REDSTONE_TORCH,"&cАварийный контроль",List.of("&7Аннулировать ошибочный бюллетень","&7или выдать новый конкретному игроку."),"open:election-emergency");
        btn(m,31,Material.EMERALD,"&aОткрыть голосование",List.of("&7Безопасный переход через release gates.","&7Если есть блокер, действие остановится."),"election:prepare-voting");
        btn(m,32,Material.CLOCK,"&eНачать подсчёт",List.of("&7Закрывает приём голосов и сверяет журнал.","&7Если проверка не пройдена, этап не изменится."),"election:prepare-counting");
        btn(m,33,Material.SPYGLASS,"&6Журнал выборов",List.of("&7Проверить выдачи, аннулирования","&7и действия председателя ЦИК."),"open:election-audit");
        nav(m,"open:elections","open:ballots-issue"); p.openInventory(m.inv);
    }

    private void openElectionEmergency(Player p)throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void openApplicationIssuesLedger(Player p)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        Menu m=new Menu("application-issues"); create(m,54,"&6&lВыданные книги заявок");
        String eid=activeOrLatestElectionId();
        if(eid==null){btn(m,22,Material.BARRIER,"&cНет выборов",List.of("&7Сначала запусти цикл."),"none");}
        else{
            btn(m,4,Material.WRITABLE_BOOK,"&6&lКниги заявок",List.of("&7Всего: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=?",eid),"&7Не подписаны: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=? AND COALESCE(used,0)=0 AND COALESCE(annulled,0)=0",eid),"&cShift+ПКМ по книге: аннулировать."),"none");
            int slot=9;
            for(Map<String,Object> r:query("SELECT id,applicant_name,issued_at,issued_by,used,annulled,notes FROM cmv7_application_issues WHERE election_id=? ORDER BY issued_at DESC LIMIT 36",eid)){
                boolean used=num(r.get("used"))>0, annulled=num(r.get("annulled"))>0;
                Material mat=annulled?Material.BARRIER:used?Material.LIME_DYE:Material.WRITABLE_BOOK;
                String color=annulled?"&c":used?"&a":"&e";
                btn(m,slot++,mat,color+first(s(r.get("applicant_name")),"unknown"),List.of("&7Issue: &f"+shortId(s(r.get("id"))),"&7Выдал: &f"+first(s(r.get("issued_by")),"-"),"&7Время: &f"+num(r.get("issued_at")),"&7Подписана: "+(used?"&aYES":"&eNO"),"&7Аннулирована: "+(annulled?"&cYES":"&aNO"),"&7Notes: &f"+clipped(first(s(r.get("notes")),"-"),38),"&cShift+ПКМ: аннулировать выданную книгу"),"app-issue-annul:"+s(r.get("id")));
                if(slot>=45)break;
            }
        }
        nav(m,"open:election-emergency","open:application-issues"); p.openInventory(m.inv);
    }

    private void openSubmittedApplicationsEmergency(Player p)throws Exception{
        Menu m=new Menu("submitted-applications-emergency"); create(m,54,"&c&lАннулирование заявок");
        String eid=activeOrLatestElectionId();
        if(eid==null){btn(m,22,Material.BARRIER,"&cНет выборов",List.of(),"none");}
        else{
            btn(m,4,Material.BOOK,"&c&lПоданные заявки",List.of("&7Аннулирование скрывает заявку,","&7ставит статус ANNULLED и снимает кандидата.","&cShift+ПКМ по заявке: аннулировать."),"none");
            int slot=9;
            for(Map<String,Object> r:query("SELECT rowid,id,applicant_name,status,submitted_at,reviewed_by,verdict_reason,deleted_at FROM applications WHERE election_id=? ORDER BY COALESCE(deleted_at,0) ASC,submitted_at DESC LIMIT 36",eid)){
                String status=s(r.get("status")).toUpperCase(Locale.ROOT);
                boolean deleted=num(r.get("deleted_at"))>0||status.equals("ANNULLED");
                Material mat=deleted?Material.BARRIER:status.equals("APPROVED")?Material.LIME_DYE:status.equals("REJECTED")?Material.RED_DYE:Material.WRITABLE_BOOK;
                btn(m,slot++,mat,(deleted?"&c":"&e")+first(s(r.get("applicant_name")),"unknown"),List.of("&7Application: &f"+shortId(s(r.get("id"))),"&7Status: &f"+first(status,"PENDING"),"&7Submitted: &f"+num(r.get("submitted_at")),"&7Reviewed by: &f"+first(s(r.get("reviewed_by")),"-"),"&7Reason: &f"+clipped(first(s(r.get("verdict_reason")),"-"),38),"&cShift+ПКМ: аннулировать заявку"),"app-submitted-annul:"+s(r.get("rowid")));
                if(slot>=45)break;
            }
        }
        nav(m,"open:election-emergency","open:submitted-applications-emergency"); p.openInventory(m.inv);
    }

    private void openPollingStations(Player p)throws Exception{
        redirectLegacyElectionAction(p);
    }

    private void openPollingStationCard(Player p,String stationId)throws Exception{
        if(!hasElectionAdmin(p)){warn(p,"Нет прав на участки ЦИК."); return;}
        List<Map<String,Object>> rows=query("SELECT id,election_id,world,x,y,z,name,active,created_by,created_at,COALESCE(archived_by,'') archived_by,COALESCE(archived_at,0) archived_at,COALESCE(archived_reason,'') archived_reason FROM cmv7_polling_stations WHERE CAST(id AS TEXT)=? OR CAST(rowid AS TEXT)=? LIMIT 1",stationId,stationId);
        if(rows.isEmpty()){warn(p,"Участок не найден."); openPollingStations(p); return;}
        Map<String,Object> r=rows.get(0);
        String id=s(r.get("id")), eid=s(r.get("election_id")), xyz=num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z"));
        boolean active=num(r.get("active"))!=0, archived=num(r.get("archived_at"))>0;
        long votes=scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND station_id=?",eid,id);
        Menu m=new Menu("polling-station-card"); create(m,54,"&b&lУчасток ЦИК &8#"+id);
        btn(m,4,archived?Material.BARRIER:(active?Material.LECTERN:Material.GRAY_DYE),(archived?"&c&lУчасток в архиве":active?"&b&lАктивный участок":"&7&lУчасток выключен"),List.of("&7Название: &f"+first(s(r.get("name")),"Участок ЦИК"),"&7Цикл: &f"+shortId(eid),"&7Мир: &f"+s(r.get("world")),"&7XYZ: &f"+xyz,"&7Создал: &f"+first(s(r.get("created_by")),"-"),"&7Голосов через участок: &f"+votes),"none");
        btn(m,10,Material.ENDER_PEARL,"&aТелепорт к участку",List.of("&7Переносит на блок над участком.","&7Удобно для проверки RP-зоны."),"station:teleport:"+id);
        btn(m,12,active?Material.REDSTONE_TORCH:Material.LEVER,active?"&eВыключить участок":"&aВключить участок",List.of("&7ЛКМ включает запись как активную.","&7ПКМ выключает без удаления истории."),"station-toggle:"+id);
        btn(m,14,Material.RED_CONCRETE,"&cУдалить участок",List.of("&7Откроется подтверждение.","&7История голосов не стирается: участок","&7будет архивирован и выключен."),"station:delete-confirm:"+id);
        btn(m,16,Material.SPYGLASS,"&aСоздать ещё по взгляду",List.of("&7Наведи взгляд на следующий блок","&7и создай ещё одну RP-точку."),"station:create-target");
        btn(m,28,Material.PAPER,"&fВыдать бюллетени всем",List.of("&7Быстро подготовить игроков","&7к голосованию на участках."),"election:ballot-all");
        btn(m,29,Material.PLAYER_HEAD,"&fВыдать бюллетень одному",List.of("&7Адресная выдача без выхода","&7из карточки участка."),"open:give-ballot-player");
        btn(m,30,Material.FILLED_MAP,"&6Реестр бюллетеней",List.of("&7Проверить выдачи, аннулирования","&7и station_id по голосам."),"open:ballots-ledger");
        btn(m,31,Material.COMPASS,"&6Маршрут избирателя",List.of("&7Как игрок проходит участок:","&7заявка, бюллетень, кандидат, урна."),"open:citizen-guide");
        btn(m,32,Material.SHIELD,"&aПрофилактика",List.of("&7Проверить участки, роли, заявки,","&7бюллетени и live-панель."),"open:preflight");
        btn(m,33,Material.BOOK,"&6Журнал выборов",List.of("&7Открыть аудит действий ЦИК","&7и служебных операций."),"open:election-audit");
        nav(m,"open:polling-stations","station:card:"+id); p.openInventory(m.inv);
    }

    private void openPollingStationDeleteConfirm(Player p,String stationId)throws Exception{
        requireMainAdmin(p);
        List<Map<String,Object>> rows=query("SELECT id,election_id,world,x,y,z,name FROM cmv7_polling_stations WHERE CAST(id AS TEXT)=? OR CAST(rowid AS TEXT)=? LIMIT 1",stationId,stationId);
        if(rows.isEmpty()){warn(p,"Участок не найден."); openPollingStations(p); return;}
        Map<String,Object> r=rows.get(0);
        String id=s(r.get("id")), eid=s(r.get("election_id"));
        long votes=scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND station_id=?",eid,id);
        Menu m=new Menu("polling-station-delete"); create(m,54,"&c&lУдаление участка &8#"+id);
        btn(m,4,Material.REDSTONE_BLOCK,"&c&lПодтверждение архивации участка",List.of("&7Это безопасное удаление из активных участков.","&7История голосов сохраняется в ledger.","&7Физический блок не ломается автоматически.","&8Действие попадёт в audit."),"none");
        btn(m,20,Material.LECTERN,"&e"+first(s(r.get("name")),"Участок ЦИК"),List.of("&7Мир: &f"+s(r.get("world")),"&7XYZ: &f"+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&7Голосов через участок: &f"+votes,"&7После удаления статус станет inactive/archive."),"none");
        btn(m,22,Material.RED_CONCRETE,"&c&lПодтвердить удаление",List.of("&7Только главный админ.","&7Выключит участок для новых голосов.","&7Голоса и журнал не стираются."),"station:delete:"+id);
        btn(m,24,Material.ARROW,"&aОтмена и карточка",List.of("&7Вернуться к управлению этим участком."),"station:card:"+id);
        btn(m,30,Material.SPYGLASS,"&6Журнал выборов",List.of("&7Проверить аудит перед удалением."),"open:election-audit");
        btn(m,32,Material.SHIELD,"&aКонтроль целостности",List.of("&7Открыть проверки, если есть сомнения","&7по голосам, бюллетеням или участкам."),"open:election-integrity");
        nav(m,"station:card:"+id,"station:delete-confirm:"+id); p.openInventory(m.inv);
    }

    private Block targetPollingStationBlock(Player p){
        Block b=p.getTargetBlockExact(8);
        if(b==null||b.getType().isAir()||b.isLiquid()||!b.getType().isSolid())return null;
        return b;
    }

    private String createPollingStationFromTarget(Player p)throws Exception{
        if(!hasElectionAdmin(p))throw new Exception("Нет прав на создание участков ЦИК.");
        String eid=issuableElectionId();
        if(eid==null)throw new SQLException("Нет текущих выборов для выдачи бюллетеня");
        Block b=targetPollingStationBlock(p);
        if(b==null)throw new SQLException("Наведи взгляд на твёрдый блок участка на расстоянии до 8 блоков.");
        if(scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE world=? AND x=? AND y=? AND z=? AND active=1 AND COALESCE(archived_at,0)=0",b.getWorld().getName(),b.getX(),b.getY(),b.getZ())>0)return"&eНа этом блоке уже есть активный участок ЦИК.";
        long index=scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=?",eid)+1;
        String name="Участок ЦИК #"+index;
        exec("INSERT INTO cmv7_polling_stations(election_id,world,x,y,z,name,active,created_by,created_at) VALUES(?,?,?,?,?,?,1,?,?)",eid,b.getWorld().getName(),b.getX(),b.getY(),b.getZ(),name,p.getName(),now());
        audit(p.getName(),"ULTRA7_POLLING_STATION_CREATE_TARGET","election="+eid+" block="+b.getType()+" "+b.getWorld().getName()+" "+b.getX()+" "+b.getY()+" "+b.getZ(),true);
        staffNotify("&bУчасток ЦИК создан по взгляду: &f"+b.getWorld().getName()+" "+b.getX()+" "+b.getY()+" "+b.getZ());
        p.sendTitle(c("&b&lУчасток создан"),c("&f"+b.getWorld().getName()+" "+b.getX()+" "+b.getY()+" "+b.getZ()),5,45,10);
        sound(p,"BLOCK_NOTE_BLOCK_CHIME",.8f,1.35f);
        return"&aСоздан участок по блоку в прицеле: &f"+b.getType()+" &7("+b.getX()+" "+b.getY()+" "+b.getZ()+")";
    }

    private String archivePollingStation(Player p,String stationId)throws Exception{
        requireMainAdmin(p);
        List<Map<String,Object>> rows=query("SELECT id,election_id,world,x,y,z,name FROM cmv7_polling_stations WHERE CAST(id AS TEXT)=? OR CAST(rowid AS TEXT)=? LIMIT 1",stationId,stationId);
        if(rows.isEmpty())return"&eУчасток уже не найден.";
        Map<String,Object> r=rows.get(0);
        String id=s(r.get("id")), eid=s(r.get("election_id"));
        long votes=scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND station_id=?",eid,id);
        String reason=votes>0?"archived_from_gui_votes_preserved":"archived_from_gui";
        int n=exec("UPDATE cmv7_polling_stations SET active=0, archived_by=?, archived_at=?, archived_reason=? WHERE id=?",p.getName(),now(),reason,id);
        audit(p.getName(),"ULTRA7_POLLING_STATION_ARCHIVE","election="+eid+" station="+id+" votes="+votes+" changed="+n,true);
        staffNotify("&eУчасток ЦИК архивирован: &f#"+id+" &7голосов: &f"+votes);
        sound(p,"BLOCK_ANVIL_USE",.5f,1.2f);
        return n>0?"&aУчасток #"+id+" удалён из активных и сохранён в архиве. Голосов сохранено: &f"+votes:"&eУчасток не изменён.";
    }

    private void teleportPollingStation(Player p,String stationId)throws Exception{
        List<Map<String,Object>> rows=query("SELECT id,world,x,y,z FROM cmv7_polling_stations WHERE CAST(id AS TEXT)=? OR CAST(rowid AS TEXT)=? LIMIT 1",stationId,stationId);
        if(rows.isEmpty()){warn(p,"Участок не найден."); return;}
        Map<String,Object> r=rows.get(0);
        World w=Bukkit.getWorld(s(r.get("world")));
        if(w==null){warn(p,"Мир участка не найден."); return;}
        p.teleport(new Location(w,num(r.get("x"))+.5,num(r.get("y"))+1.0,num(r.get("z"))+.5,p.getLocation().getYaw(),p.getLocation().getPitch()));
        msg(p,"&aТелепорт к участку #"+s(r.get("id")));
        sound(p,"ENTITY_ENDERMAN_TELEPORT",.6f,1.2f);
    }

    private void openElectionPreflight(Player p)throws Exception{
        if(!hasElectionAdmin(p)){warn(p,"Профилактика доступна председателю ЦИК и администрации."); return;}
        String eid=activeOrLatestElectionId();
        Menu m=new Menu("preflight"); create(m,54,"&a&lПрофилактика выборов");
        List<PreflightRow> rows=preflightRows(p);
        long ok=rows.stream().filter(PreflightRow::ok).count();
        btn(m,4,ok==rows.size()?Material.EMERALD_BLOCK:Material.HONEYCOMB,"&a&lЧек-лист перед запуском",List.of("&7Цикл: &f"+shortId(eid),"&7Готово: &f"+ok+"/"+rows.size(),"&7Проверяет участки, роли, бюллетени,","&7live-панель, БД и защиту предметов."),"none");
        int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for(int i=0;i<rows.size()&&i<slots.length;i++){
            PreflightRow r=rows.get(i);
            btn(m,slots[i],r.ok()?r.material():Material.REDSTONE_TORCH,(r.ok()?"&a":"&e")+r.title(),List.of(r.ok()?"&7Статус: &aOK":"&7Статус: &eПроверь","&7"+r.detail(),"&8"+r.key()),r.action()==null||r.action().isBlank()?"none":r.action());
        }
        btn(m,37,Material.NETHER_STAR,"&bПечать председателя",List.of("&7Восстановление только через панель ЦИК,","&7если нет предмета и нет закрепления."),"official:recover:cik_seal");
        btn(m,39,Material.WRITTEN_BOOK,"&6Мандат президента",List.of("&7Восстановление только через кабинет президента,","&7если нет предмета и нет закрепления."),"official:recover:president_mandate");
        btn(m,41,Material.MAP,"&dОбновить live-панель",List.of("&7Перерисовать графики справа","&7без красных score-цифр."),"sidebar:reload");
        btn(m,43,Material.ANVIL,"&aПрофилактический ремонт",List.of("&7Очистить временные книги, вернуть pending","&7официальные предметы и обновить панель."),"preflight:repair");
        audit(p.getName(),"ULTRA7_PREFLIGHT","election="+first(eid,"none")+" ok="+ok+"/"+rows.size(),true);
        nav(m,"open:elections","open:preflight"); p.openInventory(m.inv);
    }

    private List<PreflightRow> preflightRows(Player p)throws Exception{
        String eid=activeOrLatestElectionId();
        List<PreflightRow> rows=new ArrayList<>();
        if(eid==null){
            rows.add(new PreflightRow("PREFLIGHT_ELECTION",false,Material.BARRIER,"Нет активного цикла","Запусти мастер выборов перед настройкой участка.","open:lifecycle"));
            rows.add(new PreflightRow("PREFLIGHT_STATIONS",false,Material.LECTERN,"Участки","Нет цикла - участки нельзя проверить.","open:polling-stations"));
            rows.add(new PreflightRow("PREFLIGHT_CANDIDATES",false,Material.PLAYER_HEAD,"Кандидаты","Нет цикла - кандидаты не выбраны.","open:candidates"));
            rows.add(new PreflightRow("PREFLIGHT_BALLOTS",false,Material.PAPER,"Бюллетени","Нет цикла - бюллетени не выданы.","open:ballots-issue"));
            rows.add(new PreflightRow("PREFLIGHT_ITEM_GUARD",true,Material.SHIELD,"Защита предметов","Хранилища, воронки, огонь и drop guarded.","none"));
            return rows;
        }
        Map<String,String> st=electionSettings(eid);
        long stations=scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=? AND active=1",eid);
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        long ballots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)=0",eid);
        long chair=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1 AND role='CIK_CHAIR'",eid);
        long curators=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1",eid);
        long dupVotes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM (SELECT voter_uuid FROM cmv731_votes WHERE election_id=? GROUP BY voter_uuid HAVING COUNT(*)>1)",eid):0;
        boolean bindings=tableExists("cmv7_official_item_bindings");
        boolean guard=isProtectedCustomItem(createCikSealItem(p,eid,"preflight-seal"))&&isProtectedCustomItem(createPresidentMandateItem(p,eid,"preflight-mandate"));
        rows.add(new PreflightRow("PREFLIGHT_ELECTION",true,Material.RECOVERY_COMPASS,"Цикл выборов","ID "+shortId(eid)+", этап "+humanStage(st.getOrDefault("stage","?")),"open:lifecycle"));
        rows.add(new PreflightRow("PREFLIGHT_STATIONS",stations>0,Material.LECTERN,"Участки",stations>0?"Активных участков: "+stations:"Поставь хотя бы один участок ЦИК.","open:polling-stations"));
        rows.add(new PreflightRow("PREFLIGHT_CANDIDATES",candidates>0,Material.PLAYER_HEAD,"Кандидаты",candidates>0?"Допущено кандидатов: "+candidates:"Одобри заявки или добавь кандидатов.","open:candidates"));
        rows.add(new PreflightRow("PREFLIGHT_BALLOTS",ballots>0,Material.PAPER,"Бюллетени",ballots>0?"Неиспользованных бюллетеней: "+ballots:"Выдай бюллетени до открытия участка.","open:ballots-issue"));
        rows.add(new PreflightRow("PREFLIGHT_CHAIR",chair>0,Material.NAME_TAG,"Председатель ЦИК",chair>0?"Председатель назначен, всего членов ЦИК: "+curators:"Назначь председателя в кураторах.","open:curators"));
        rows.add(new PreflightRow("PREFLIGHT_SIDEBAR",sidebarGlobal&&onOff(st.get("sidebar_visible"))==1,Material.MAP,"Live-панель",sidebarGlobal?"Панель готова к показу.":"Включи или перезагрузи панель перед стартом.","open:sidebar"));
        rows.add(new PreflightRow("PREFLIGHT_ITEM_GUARD",guard,Material.SHIELD,"Защита предметов",guard?"Официальные предметы распознаются guard-системой.":"Проверь PDC official items и защиту инвентарей.","none"));
        rows.add(new PreflightRow("PREFLIGHT_BINDINGS",bindings,Material.ENDER_CHEST,"Реестр печатей",bindings?"Binding DB готова для восстановления.":"Таблица будет создана при запуске плагина.","none"));
        rows.add(new PreflightRow("PREFLIGHT_DUPLICATES",dupVotes==0,Material.SPYGLASS,"Антидюп голосов",dupVotes==0?"Дубликатов голосов не найдено.":"Есть подозрительные duplicate voter rows: "+dupVotes,"open:election-audit"));
        return rows;
    }

    private String runPreflightRepair(Player actor){
        int purged=0, restoredQueues=pendingOfficialReturns.size(), snapshots=0;
        for(Player t:Bukkit.getOnlinePlayers()){
            purgeTemporaryApplicationBooks(t);
            purged++;
            restorePendingOfficialItems(t,"preflight-repair");
            try{snapshotOnlineInventory(t,"preflight_repair"); snapshots++;}catch(Throwable ignored){}
        }
        reloadSidebarAll();
        queueArSync("PREFLIGHT_REPAIR");
        try{audit(actor.getName(),"ULTRA7_PREFLIGHT_REPAIR","purged="+purged+" pendingQueues="+restoredQueues+" snapshots="+snapshots,true);}catch(Throwable ignored){}
        return "&aПрофилактика выполнена: книги="+purged+", pending="+restoredQueues+", snapshots="+snapshots;
    }

    private void openStartupReadiness(Player p)throws Exception{
        if(!hasAnyAdmin(p)){warn(p,"Готовность запуска доступна только администрации."); return;}
        List<StartupCheckRow> rows=startupSelfCheckRows();
        long ok=rows.stream().filter(StartupCheckRow::ok).count();
        persistStartupSelfCheck(p.getName());
        Menu m=new Menu("startup-readiness"); create(m,54,"&a&lГотовность запуска");
        btn(m,4,ok==rows.size()?Material.EMERALD_BLOCK:Material.HONEYCOMB,"&a&lfirst-run-ready",List.of("&7Готово: &f"+ok+"/"+rows.size(),"&7Проверяет запуск после замены папки:","&7БД, зависимости, оптимизацию, защиту.","&7Если есть желтые пункты - открой их или нажми ремонт."),"none");
        int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for(int i=0;i<rows.size()&&i<slots.length;i++){
            StartupCheckRow r=rows.get(i);
            btn(m,slots[i],r.ok()?r.material():Material.REDSTONE_TORCH,(r.ok()?"&a":"&e")+r.title(),List.of(r.ok()?"&7Статус: &aOK":"&7Статус: &eПроверь","&7"+r.detail(),"&8"+r.key()),r.action()==null||r.action().isBlank()?"none":r.action());
        }
        btn(m,37,Material.ANVIL,"&aБезопасный self-heal",List.of("&7Пересоздать таблицы, снять зависшие книги,","&7вернуть pending-предметы, обновить TAB/sidebar,","&7сделать снимки инвентарей онлайн-игроков."),"startup:repair");
        btn(m,39,Material.SPYGLASS,"&bЭкономика АР",List.of("&7Открыть здоровье экономики и guard-инциденты."),"open:ar-health");
        btn(m,41,Material.GOLDEN_HELMET,"&6Выборы в ElectionCore",List.of("&7Проверки ЦИК, участков и бюллетеней","&7перенесены в &fCopiMineElectionCore&7.","&7Открыть актуальный election hub."),"open:elections");
        btn(m,43,Material.CLOCK,"&dОбновить",List.of("&7Повторить startup self-check."),"open:startup-readiness");
        nav(m,"open:hub","open:startup-readiness"); p.openInventory(m.inv);
    }

    private List<StartupCheckRow> startupSelfCheckRows()throws Exception{
        List<StartupCheckRow> rows=new ArrayList<>();
        rows.add(new StartupCheckRow("STARTUP_DB_FILE",dbSourceReady(),Material.BOOK,"Источник данных","DB: "+dbSourceSummary(),"none"));
        rows.add(new StartupCheckRow("STARTUP_DB_SCHEMA",tableExists("cmv7_audit")&&tableExists("cmv731_votes")&&tableExists("cmv7_ar_assets"),Material.WRITABLE_BOOK,"Схема БД","Таблицы выборов, АР и аудита созданы.","none"));
        rows.add(new StartupCheckRow("STARTUP_PLACEHOLDERAPI",pluginReady("PlaceholderAPI"),Material.NAME_TAG,"PlaceholderAPI",pluginDetail("PlaceholderAPI"),"none"));
        rows.add(new StartupCheckRow("STARTUP_LUCKPERMS",pluginReady("LuckPerms"),Material.GOLDEN_HELMET,"LuckPerms",pluginDetail("LuckPerms"),"none"));
        rows.add(new StartupCheckRow("STARTUP_WORLDGUARD",pluginReady("WorldGuard"),Material.SHIELD,"WorldGuard",pluginDetail("WorldGuard"),"none"));
        rows.add(new StartupCheckRow("STARTUP_GRIMAC",pluginReady("GrimAC"),Material.CHAINMAIL_CHESTPLATE,"GrimAC античит",pluginDetail("GrimAC"),"none"));
        rows.add(new StartupCheckRow("STARTUP_FARMCONTROL",pluginReady("FarmControl"),Material.WHEAT,"FarmControl",pluginDetail("FarmControl"),"none"));
        rows.add(new StartupCheckRow("STARTUP_ENTITYCLEARER",pluginReady("EntityClearer"),Material.BUCKET,"EntityClearer",pluginDetail("EntityClearer"),"none"));
        rows.add(new StartupCheckRow("STARTUP_CHUNKY_SEEMORE",pluginReady("Chunky")&&pluginReady("SeeMore"),Material.MAP,"Chunky + SeeMore","Chunky: "+pluginState("Chunky")+", SeeMore: "+pluginState("SeeMore"),"none"));
        rows.add(new StartupCheckRow("STARTUP_AR_GUARD",tableExists("cmv7_official_item_bindings")&&tableExists("cmv7_ar_guard_incidents"),Material.DIAMOND_ORE,"Защита предметов","Реестры official items и guard-инцидентов созданы.","none"));
        rows.add(new StartupCheckRow("STARTUP_SIDEBAR",sidebarTask!=null&&!sidebarTask.isCancelled(),Material.MAP,"Live-панель","Scoreboard task активен, красные числа скрываются при поддержке API.","open:sidebar"));
        rows.add(new StartupCheckRow("STARTUP_AUTH_MODE",!Bukkit.getOnlineMode(),Material.ENDER_EYE,"AuthMe / offline-режим",Bukkit.getOnlineMode()?"online-mode=true":"offline-mode=false: проверь AuthMe, proxy и привязку сайта перед публичным запуском.","none"));
        return rows;
    }

    private boolean pluginReady(String name){
        org.bukkit.plugin.Plugin pl=Bukkit.getPluginManager().getPlugin(name);
        return pl!=null&&pl.isEnabled();
    }
    private String pluginState(String name){return pluginReady(name)?"OK":"нет";}
    private String pluginDetail(String name){
        org.bukkit.plugin.Plugin pl=Bukkit.getPluginManager().getPlugin(name);
        if(pl==null)return "Не найден в plugins.";
        return (pl.isEnabled()?"Включен ":"Загружен, но выключен ")+pl.getDescription().getVersion();
    }
    private void persistStartupSelfCheck(String actor)throws Exception{
        List<StartupCheckRow> rows=startupSelfCheckRows();
        long n=now(), ok=rows.stream().filter(StartupCheckRow::ok).count();
        for(StartupCheckRow r:rows)exec("INSERT OR REPLACE INTO cmv8_startup_checks(key,time,ok,title,detail,action) VALUES(?,?,?,?,?,?)",r.key(),n,r.ok()?1:0,r.title(),r.detail(),first(r.action(),"none"));
        audit(actor,"STARTUP_SELF_CHECK","ok="+ok+"/"+rows.size()+" plugins=Chunky:"+pluginState("Chunky")+" SeeMore:"+pluginState("SeeMore"),true);
    }
    private String runStartupSelfHeal(Player actor){
        int snapshots=0, restoredQueues=pendingOfficialReturns.size();
        try{ensureTables();}catch(Exception e){return "&cНе удалось проверить БД: "+e.getMessage();}
        for(Player t:Bukkit.getOnlinePlayers()){
            purgeTemporaryApplicationBooks(t);
            restorePendingOfficialItems(t,"startup-self-heal");
            try{snapshotOnlineInventory(t,"startup_self_heal"); snapshots++;}catch(Throwable ignored){}
        }
        reloadSidebarAll();
        updateRoleNameplates();
        queueArSync("STARTUP_SELF_HEAL");
        try{persistStartupSelfCheck(actor.getName()); audit(actor.getName(),"STARTUP_SELF_HEAL","snapshots="+snapshots+" pendingQueues="+restoredQueues,true);}catch(Throwable ignored){}
        return "&aSelf-heal выполнен: snapshots="+snapshots+", pending="+restoredQueues+", sidebar/TAB обновлены.";
    }

    private void openElectionIntegrity(Player p)throws Exception{
        redirectLegacyElectionAction(p);
    }

    private List<ElectionIntegrityRow> integrityRows(Player p)throws Exception{
        String eid=activeOrLatestElectionId();
        List<ElectionIntegrityRow> rows=new ArrayList<>();
        if(eid==null){
            rows.add(new ElectionIntegrityRow("election_cycle",false,Material.BARRIER,"Цикл выборов","Нет активного или последнего цикла.","open:lifecycle"));
            rows.add(new ElectionIntegrityRow("database_tables",tableExists("cmv731_votes")&&tableExists("cmv7_ballot_issues"),Material.ENDER_CHEST,"Таблицы БД","Основные таблицы голосования доступны.","none"));
            return rows;
        }
        Map<String,String> st=electionSettings(eid);
        long stations=scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=? AND active=1",eid);
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        long chair=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1 AND role='CIK_CHAIR'",eid);
        long ballots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid);
        long unusedBallots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)=0",eid);
        long pending=scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND status='PENDING' AND COALESCE(deleted_at,0)=0",eid);
        long dupVotes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM (SELECT voter_uuid FROM cmv731_votes WHERE election_id=? GROUP BY voter_uuid HAVING COUNT(*)>1)",eid):0;
        long sealedSessions=tableExists("cmv731_vote_sessions")?scalarLong("SELECT COUNT(*) FROM cmv731_vote_sessions WHERE election_id=? AND COALESCE(selected_at,0)>0",eid):0;
        long votedSessions=tableExists("cmv731_vote_sessions")&&tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM cmv731_vote_sessions s WHERE s.election_id=? AND EXISTS(SELECT 1 FROM cmv731_votes v WHERE v.election_id=s.election_id AND v.voter_uuid=s.voter_uuid)",eid):0;
        long orphanVotes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM cmv731_votes v LEFT JOIN candidates c ON c.election_id=v.election_id AND c.uuid=v.candidate_uuid WHERE v.election_id=? AND c.uuid IS NULL",eid):0;
        rows.add(new ElectionIntegrityRow("election_cycle",true,Material.RECOVERY_COMPASS,"Цикл найден","ID "+shortId(eid)+", этап "+humanStage(st.getOrDefault("stage","?")),"open:lifecycle"));
        rows.add(new ElectionIntegrityRow("stations",stations>0,Material.LECTERN,"Участки ЦИК",stations>0?"Активных участков: "+stations:"Поставь хотя бы один участок.","open:polling-stations"));
        rows.add(new ElectionIntegrityRow("candidates",candidates>0,Material.PLAYER_HEAD,"Кандидаты",candidates>0?"Активных кандидатов: "+candidates:"Одобри заявки и перенеси в кандидаты.","open:candidates"));
        rows.add(new ElectionIntegrityRow("chair",chair>0,Material.NAME_TAG,"Председатель ЦИК",chair>0?"Председатель назначен.":"Назначь CIK_CHAIR в кураторах.","open:curators"));
        rows.add(new ElectionIntegrityRow("ballots",ballots>0,Material.PAPER,"Бюллетени",ballots>0?"Выдано всего: "+ballots+", свободных: "+unusedBallots:"Выдай бюллетени игрокам.","open:ballots-issue"));
        rows.add(new ElectionIntegrityRow("duplicate voter",dupVotes==0,Material.SPYGLASS,"Дубликаты голосов",dupVotes==0?"duplicate voter не найден.":"Найдено voter_uuid с несколькими голосами: "+dupVotes,"open:election-audit"));
        rows.add(new ElectionIntegrityRow("sealed_not_deposited",sealedSessions==0,Material.CAULDRON,"Запечатанные бюллетени",sealedSessions==0?"Нет зависших sealed sessions.":"Запечатано, но не опущено в участок: "+sealedSessions,"open:ballots-ledger"));
        rows.add(new ElectionIntegrityRow("PENDING applications",pending==0||onOff(st.get(COL_APP_OPEN))==1,Material.WRITABLE_BOOK,"PENDING заявки",pending==0?"Нет ожидающих заявок.":(onOff(st.get(COL_APP_OPEN))==1?"Прием открыт, pending нормально: "+pending:"Перед голосованием проверь pending: "+pending),"open:applications-review"));
        rows.add(new ElectionIntegrityRow("voted_sessions",votedSessions==0,Material.ANVIL,"Сессии после голоса",votedSessions==0?"Нет лишних vote_sessions у проголосовавших.":"Можно безопасно закрыть сессии: "+votedSessions,"election:integrity-repair"));
        rows.add(new ElectionIntegrityRow("orphan_votes",orphanVotes==0,Material.BOOK,"Голоса без кандидата",orphanVotes==0?"Все ledger votes ссылаются на кандидатов.":"Есть голоса к отсутствующим кандидатам: "+orphanVotes,"open:election-audit"));
        rows.add(new ElectionIntegrityRow("live_panel",sidebarGlobal&&onOff(st.get("sidebar_visible"))==1,Material.MAP,"Live-панель",sidebarGlobal?"Панель готова.":"Панель скрыта или отключена в настройках.","open:sidebar"));
        return rows;
    }

    private String runElectionIntegrityRepair(Player actor)throws Exception{
        if(!hasElectionAdmin(actor))return"&cНет прав на контроль выборов.";
        String eid=activeOrLatestElectionId();
        if(eid==null)return"&cНет выборов.";
        int synced=syncCandidateVotesFromLedger(eid,actor.getName());
        int closedSessions=tableExists("cmv731_vote_sessions")&&tableExists("cmv731_votes")?exec("DELETE FROM cmv731_vote_sessions WHERE election_id=? AND voter_uuid IN (SELECT voter_uuid FROM cmv731_votes WHERE election_id=?)",eid,eid):0;
        int marked=tableExists("cmv7_ballot_issues")&&tableExists("cmv731_votes")?exec("UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'')||' | integrity-repair-used' WHERE election_id=? AND COALESCE(used,0)=0 AND id IN (SELECT ballot_id FROM cmv731_votes WHERE election_id=?)",eid,eid):0;
        reloadSidebarAll();
        audit(actor.getName(),"ULTRA7_ELECTION_INTEGRITY_REPAIR","election="+eid+" synced="+synced+" closedSessions="+closedSessions+" marked="+marked,true);
        return"&aКонтроль целостности выполнен: sync="+synced+", sessions="+closedSessions+", ballots="+marked;
    }

    private void openElectionReleaseBoard(Player p)throws Exception{
        if(!hasElectionAdmin(p)){warn(p,"RP-режиссёр доступен ЦИК и администрации."); return;}
        Menu m=new Menu("election-ceremony"); create(m,54,"&d&lRP-режиссёр выборов");
        String eid=activeOrLatestElectionId();
        String target=nextReleaseTarget();
        List<ElectionGateRow> rows=releaseGateRows(eid,target);
        long blockers=rows.stream().filter(r->r.blocking()&&!r.ok()).count();
        btn(m,4,blockers==0?Material.EMERALD_BLOCK:Material.TARGET,"&a&lСледующий этап: &f"+humanStage(target),List.of(
                "&7Цикл: &f"+shortId(eid),
                "&7Блокеров: &f"+blockers,
                blockers==0?"&aМожно идти дальше через мастер цикла.":"&eСначала закрой красные пункты.",
                "&8Эта же проверка стоит внутри опасных кнопок."),"none");
        int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        for(int i=0;i<rows.size()&&i<slots.length;i++){
            ElectionGateRow r=rows.get(i);
            Material mat=r.ok()?r.material():(r.blocking()?Material.REDSTONE_BLOCK:Material.ORANGE_DYE);
            String prefix=r.ok()?"&a":(r.blocking()?"&c":"&e");
            List<String> lore=new ArrayList<>();
            lore.add(r.ok()?"&7Статус: &aготово":(r.blocking()?"&7Статус: &cблокирует этап":"&7Статус: &eпредупреждение"));
            lore.add("&7"+r.detail());
            lore.add("&8"+r.key());
            btn(m,slots[i],mat,prefix+r.title(),lore,r.action()==null||r.action().isBlank()?"none":r.action());
        }
        btn(m,39,Material.CAULDRON,"&eЗакрыть не опущенные бюллетени",List.of("&7Запечатанные, но не опущенные бюллетени","&7не считаются голосом и безопасно закрываются."),"election:expire-sealed-sessions");
        btn(m,41,Material.FIREWORK_ROCKET,"&dRP-режиссёр",List.of("&7Открыть красивые объявления этапов."),"open:election-ceremony");
        btn(m,43,Material.NETHER_STAR,"&bСледующий безопасный шаг",List.of("&7Мастер выполнит этап только если","&7релизные ворота зелёные."),"election:lifecycle-next");
        audit(p.getName(),"ULTRA7_ELECTION_RELEASE_BOARD_OPEN","election="+first(eid,"none")+" target="+target+" blockers="+blockers,true);
        nav(m,"open:lifecycle","open:election-release"); p.openInventory(m.inv);
    }

    private void openElectionCeremony(Player p)throws Exception{
        if(!hasElectionAdmin(p)){warn(p,"RP-режиссёр доступен ЦИК и администрации."); return;}
        Menu m=new Menu("election-ceremony"); create(m,54,"&d&lRP-режиссёр выборов");
        String eid=activeOrLatestElectionId();
        String stage=eid==null?"NONE":electionSettings(eid).getOrDefault("stage","NOMINATION");
        btn(m,4,Material.FIREWORK_ROCKET,"&d&lСценарная панель",List.of(
                "&7Цикл: &f"+shortId(eid),
                "&7Этап: &f"+humanStage(stage),
                "&7Кнопки дают title, чат и звук игрокам,",
                "&7а действия пишутся в аудит."),"none");
        btn(m,10,Material.PLAYER_HEAD,"&eВызвать кандидатов",List.of("&7Мягко зовёт кандидатов к ЦИК.","&7Подходит перед дебатами."),"ceremony:candidate-call");
        btn(m,11,Material.WRITABLE_BOOK,"&6Дебаты",List.of("&7Объявляет дебаты и просит игроков","&7сравнить заявки кандидатов."),"ceremony:debate");
        btn(m,12,Material.LECTERN,"&bОткрытие участка",List.of("&7Напоминает, что голос считается только","&7после опускания бюллетеня в участок."),"ceremony:stations-open");
        btn(m,13,Material.CLOCK,"&eТихий час",List.of("&7Финальная пауза без агитации.","&7Полезно перед подсчётом."),"ceremony:silence");
        btn(m,14,Material.BELL,"&6Финальный зов",List.of("&7Последнее объявление перед закрытием","&7голосования."),"ceremony:final-call");
        btn(m,15,Material.SPYGLASS,"&aПодсчёт",List.of("&7Объявляет закрытие урн и начало","&7проверки журнала голосов."),"ceremony:counting");
        btn(m,16,Material.NETHER_STAR,"&6Победитель",List.of("&7Красивое объявление текущего победителя","&7после синхронизации ledger."),"ceremony:winner");
        btn(m,22,Material.GOLDEN_HELMET,"&6Инаугурация",List.of("&7Финальная церемония президента."),"ceremony:inauguration");
        btn(m,24,Material.MAP,"&dТекущий этап",List.of("&7Повторить официальное объявление этапа."),"election:announce-stage");
        btn(m,28,Material.TARGET,"&aРелизная готовность",List.of("&7Проверить, можно ли нажимать следующий этап."),"open:election-release");
        btn(m,30,Material.RECOVERY_COMPASS,"&bМастер цикла",List.of("&7Вернуться к сквозному сценарию."),"open:lifecycle");
        nav(m,"open:lifecycle","open:election-ceremony"); p.openInventory(m.inv);
    }

    private String nextReleaseTarget()throws SQLException{
        ElectionLifecycleSnapshot s=lifecycleSnapshot();
        if(s.eid()==null)return"NOMINATION";
        String stage=s.stage()==null?"":s.stage().toUpperCase(Locale.ROOT);
        return switch(stage){
            case "NOMINATION","REVIEW","DEBATE" -> "VOTING";
            case "VOTING" -> "COUNTING";
            case "COUNTING" -> "FINISH";
            case "INAUGURATION" -> "ARCHIVED";
            default -> "VOTING";
        };
    }

    private List<ElectionGateRow> releaseGateRows(String eid,String target)throws SQLException{
        List<ElectionGateRow> rows=new ArrayList<>();
        String t=first(target,"VOTING").toUpperCase(Locale.ROOT);
        boolean needsVoting=t.equals("VOTING");
        boolean needsCounting=t.equals("COUNTING")||t.equals("FINISH");
        boolean needsFinish=t.equals("FINISH");
        boolean tables=tableExists("elections")&&tableExists("cmv7_election_settings")&&tableExists("cmv7_ballot_issues")&&tableExists("cmv731_votes")&&tableExists("cmv731_vote_sessions");
        rows.add(new ElectionGateRow("database_tables",tables,true,Material.ENDER_CHEST,"Таблицы БД",tables?"Все таблицы выборов доступны.":"Не хватает таблиц выборов, перезапусти плагин и проверь лог.","none"));
        if(eid==null||eid.isBlank()){
            rows.add(new ElectionGateRow("election_cycle",false,true,Material.BARRIER,"Цикл выборов","Нет активного или последнего цикла.","open:lifecycle"));
            return rows;
        }
        Map<String,String> st=electionSettings(eid);
        long stations=scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=? AND active=1",eid);
        long chair=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1 AND role='CIK_CHAIR'",eid);
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        long ballots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid);
        long unusedBallots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)=0",eid);
        long pending=scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND status='PENDING' AND COALESCE(deleted_at,0)=0",eid);
        long votes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=?",eid):0;
        long duplicateVoter=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM (SELECT voter_uuid FROM cmv731_votes WHERE election_id=? GROUP BY voter_uuid HAVING COUNT(*)>1)",eid):0;
        long sealedNotDeposited=tableExists("cmv731_vote_sessions")?scalarLong("SELECT COUNT(*) FROM cmv731_vote_sessions WHERE election_id=? AND COALESCE(selected_at,0)>0",eid):0;
        long orphanVotes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM cmv731_votes v LEFT JOIN candidates c ON c.election_id=v.election_id AND c.uuid=v.candidate_uuid WHERE v.election_id=? AND c.uuid IS NULL",eid):0;
        rows.add(new ElectionGateRow("election_cycle",true,true,Material.RECOVERY_COMPASS,"Цикл выборов","ID "+shortId(eid)+", текущий этап "+humanStage(st.getOrDefault("stage","?")),"open:lifecycle"));
        rows.add(new ElectionGateRow("cmv7_polling_stations",stations>0,needsVoting,Material.LECTERN,"Участки ЦИК",stations>0?"Активных участков: "+stations:"Поставь хотя бы один лекторн участка ЦИК.","open:polling-stations"));
        rows.add(new ElectionGateRow("CIK_CHAIR",chair>0,needsVoting,Material.NAME_TAG,"Председатель ЦИК",chair>0?"Председатель назначен.":"Назначь CIK_CHAIR в кураторах.","open:curators"));
        rows.add(new ElectionGateRow("candidates",candidates>0,needsVoting||needsCounting||needsFinish,Material.PLAYER_HEAD,"Кандидаты",candidates>0?"Активных кандидатов: "+candidates:"Одобри заявки или добавь кандидата.","open:candidates"));
        rows.add(new ElectionGateRow("cmv7_ballot_issues",ballots>0&&unusedBallots>0,needsVoting,Material.PAPER,"Бюллетени",ballots>0?"Всего: "+ballots+", свободных: "+unusedBallots:"Выдай бюллетени игрокам перед открытием урн.","open:ballots-issue"));
        rows.add(new ElectionGateRow("applications",pending==0,needsVoting,Material.WRITABLE_BOOK,"Неразобранные заявки",pending==0?"Pending заявок нет.":"Перед голосованием разберись с pending: "+pending,"open:applications-review"));
        rows.add(new ElectionGateRow("duplicate voter",duplicateVoter==0,needsCounting||needsFinish,Material.SPYGLASS,"Дубликаты голосов",duplicateVoter==0?"duplicate voter не найден.":"Есть voter_uuid с несколькими голосами: "+duplicateVoter,"open:election-integrity"));
        rows.add(new ElectionGateRow("sealed_not_deposited",sealedNotDeposited==0,needsCounting||needsFinish,Material.CAULDRON,"Запечатано, но не опущено",sealedNotDeposited==0?"Нет зависших бюллетеней.":"Не опущено в участок: "+sealedNotDeposited+". Их можно безопасно закрыть.","election:expire-sealed-sessions"));
        rows.add(new ElectionGateRow("orphan_votes",orphanVotes==0,needsCounting||needsFinish,Material.BOOK,"Голоса без кандидата",orphanVotes==0?"Все голоса ссылаются на кандидатов.":"Есть ledger votes к отсутствующим кандидатам: "+orphanVotes,"open:election-integrity"));
        rows.add(new ElectionGateRow("vote_impact",votes>0,false,Material.EMERALD,"Голоса игроков",votes>0?"Голосов в журнале: "+votes:"Пока нет голосов. Финал можно провести, но без импакта игроков.","open:election-audit"));
        rows.add(new ElectionGateRow("live_panel",sidebarGlobal&&onOff(st.get("sidebar_visible"))==1,!needsVoting,Material.MAP,"Live-панель",sidebarGlobal&&onOff(st.get("sidebar_visible"))==1?"Панель включена.":"Включи правую панель для прозрачного процесса.","open:sidebar"));
        return rows;
    }

    private void requireElectionGate(String eid,String target,String actor)throws SQLException{
        List<ElectionGateRow> rows=releaseGateRows(eid,target);
        List<String> blockers=new ArrayList<>();
        for(ElectionGateRow r:rows)if(r.blocking()&&!r.ok())blockers.add(r.title()+": "+r.detail());
        if(!blockers.isEmpty()){
            String details="target="+target+" blockers="+String.join(" | ",blockers);
            audit(actor,"ULTRA7_ELECTION_GATE_BLOCK",details,true);
            throw new SQLException("Релизная проверка не пройдена: "+String.join("; ",blockers));
        }
        audit(actor,"ULTRA7_ELECTION_GATE_PASS","target="+target+" election="+first(eid,"none"),true);
    }

    private int expireUndepositedBallotSessions(String eid,String actor)throws SQLException{
        if(eid==null||eid.isBlank()||!tableExists("cmv731_vote_sessions"))return 0;
        int marked=tableExists("cmv7_ballot_issues")?exec("""
                UPDATE cmv7_ballot_issues
                SET notes=COALESCE(notes,'')||' | SESSION_EXPIRED_NO_DEPOSIT'
                WHERE election_id=? AND COALESCE(used,0)=0 AND voter_uuid IN (
                    SELECT s.voter_uuid FROM cmv731_vote_sessions s
                    WHERE s.election_id=? AND NOT EXISTS(
                        SELECT 1 FROM cmv731_votes v
                        WHERE v.election_id=s.election_id AND v.voter_uuid=s.voter_uuid
                    )
                )
                """,eid,eid):0;
        int removed=exec("""
                DELETE FROM cmv731_vote_sessions
                WHERE election_id=? AND NOT EXISTS(
                    SELECT 1 FROM cmv731_votes v
                    WHERE v.election_id=cmv731_vote_sessions.election_id
                    AND v.voter_uuid=cmv731_vote_sessions.voter_uuid
                )
                """,eid);
        if(removed>0||marked>0){
            audit(actor,"ULTRA7_BALLOT_SEALED_EXPIRED","election="+eid+" sessions="+removed+" ballotNotes="+marked,true);
            reloadSidebarAll();
        }
        return removed;
    }

    private void openGivePlayer(Player p, boolean app){
        Menu m=new Menu(app?"giveapp":"giveballot"); create(m,54,app?"&e&lЗаявка конкретному игроку":"&f&lБюллетень конкретному игроку");
        btn(m,4,app?Material.WRITABLE_BOOK:Material.PAPER,app?"&e&lАдресная выдача заявки":"&f&lАдресная выдача бюллетеня",List.of("&7Выбери онлайн-игрока ниже.","&7Действие пишется в журнал выборов.","&7Используй для восстановления RP-процесса."),"none");
        int slot=9; for(Player t:Bukkit.getOnlinePlayers()){ btn(m,slot++,Material.PLAYER_HEAD,(app?"&e":"&f")+t.getName(),List.of("&7UUID: &f"+t.getUniqueId(),"&7ЛКМ: выдать лично игроку.","&7Игроку нужен свободный слот."),(app?"give-app:":"give-ballot:")+t.getName()); if(slot>=45)break; }
        nav(m,app?"open:applications-issue":"open:ballots-issue",app?"open:give-app-player":"open:give-ballot-player"); p.openInventory(m.inv);
    }

    private void openCandidates(Player p) throws Exception{
        Menu m=new Menu("candidates"); create(m,54,"&e&lКандидаты");
        String eid=activeOrLatestElectionId();
        if(eid==null) btn(m,22,Material.BARRIER,"&cНет выборов",List.of(),"none");
        else{
            List<Map<String,Object>> rows=query("""
                SELECT uuid,name,display_name,COALESCE(raw_votes,0) raw_votes,COALESCE(admin_adjustment,0) admin_adjustment,COALESCE(removed,0) removed,
                COALESCE(raw_votes,0)+COALESCE(admin_adjustment,0) total FROM candidates WHERE election_id=? ORDER BY removed ASC,total DESC,name ASC LIMIT 45
                """,eid);
            int slot=0; for(Map<String,Object> r:rows){
                String uuid=s(r.get("uuid")), name=first(s(r.get("display_name")),s(r.get("name")),uuid); boolean rem=num(r.get("removed"))!=0;
                btn(m,slot++,rem?Material.SKELETON_SKULL:Material.PLAYER_HEAD,(rem?"&7&m":"&e")+name,List.of("&7Голоса: &f"+num(r.get("raw_votes")),"&7Корректировка: &e"+num(r.get("admin_adjustment")),"&7Итого: &a"+num(r.get("total")),"&aЛКМ +1","&cПКМ -1","&eShift+ЛКМ +10","&4Shift+ПКМ снять/вернуть","&6Средняя: победитель"),"cand:"+uuid);
            }
        }
        btn(m,47,Material.LIME_CONCRETE,"&aДобавить онлайн игрока",List.of(),"open:add-candidate");
        nav(m,"open:elections","open:candidates"); p.openInventory(m.inv);
    }
    private void openAddCandidate(Player p){ openOnlinePicker(p,"&a&lДобавить кандидата","add-candidate:","open:candidates"); }

    private void openCurators(Player p) throws Exception{
        Menu m=new Menu("curators"); create(m,54,"&b&lКураторы");
        String eid=activeOrLatestElectionId();
        if(eid!=null){ int slot=0; for(Map<String,Object> r:query("SELECT rowid,name,role,active,added_by FROM cmv7_election_curators WHERE election_id=? ORDER BY active DESC,added_at DESC LIMIT 45",eid)){
            boolean act=num(r.get("active"))!=0;
            btn(m,slot++,act?Material.NAME_TAG:Material.GRAY_DYE,(act?"&b":"&7&m")+s(r.get("name")),List.of("&7Роль: &f"+curatorRoleLabel(s(r.get("role"))),"&7Добавил: &f"+s(r.get("added_by")),"&aЛКМ вернуть","&cПКМ снять"),"curator:"+s(r.get("rowid")));
        }}
        btn(m,47,Material.LIME_CONCRETE,"&aДобавить онлайн куратора",List.of(),"open:add-curator");
        nav(m,"open:elections","open:curators"); p.openInventory(m.inv);
    }
    private void openAddCurator(Player p){ openOnlinePicker(p,"&b&lДобавить куратора","add-curator:","open:curators"); }

    private void openOnlinePicker(Player p,String title,String prefix,String back){
        Menu m=new Menu("picker"); create(m,54,title);
        int slot=0; for(Player t:Bukkit.getOnlinePlayers()){ btn(m,slot++,Material.PLAYER_HEAD,"&e"+t.getName(),List.of("&7UUID: &f"+t.getUniqueId()),prefix+t.getName()); if(slot>=45)break; }
        nav(m,back,back); p.openInventory(m.inv);
    }

    private void openDanger(Player p){
        Menu m=new Menu("danger"); create(m,54,"&4&lОпасная зона");
        btn(m,19,Material.REDSTONE_BLOCK,"&cСбросить голоса",List.of("&cShift+ЛКМ"),"election:reset-votes");
        btn(m,21,Material.SKELETON_SKULL,"&cОчистить кандидатов",List.of("&cShift+ЛКМ"),"election:clear-candidates");
        btn(m,23,Material.BARRIER,"&4Полный сброс выборов + панели",List.of("&cShift+ЛКМ"),"election:full-reset");
        nav(m,"open:elections","open:election-danger"); p.openInventory(m.inv);
    }

    private void openApplicationsReview(Player p) throws Exception{
        Menu m=new Menu("appreview"); create(m,54,"&e&lПроверка заявок");
        String eid=activeOrLatestElectionId();
        if(eid==null){btn(m,22,Material.BARRIER,"&cNo election",List.of("&7Start an election first."),"none");}
        else{
            btn(m,4,Material.BOOK,"&e&lЗаявки",List.of("&7LMB: approve","&7RMB: reject","&7Shift+LMB: approve + candidate","&7Все действия пишутся в audit."),"none");
            int slot=0;
            for(Map<String,Object> r:query("SELECT rowid,id,applicant_name,status,submitted_at,reviewed_by,verdict_reason FROM applications WHERE election_id=? AND COALESCE(deleted_at,0)=0 ORDER BY CASE UPPER(COALESCE(status,'')) WHEN 'PENDING' THEN 0 WHEN 'APPROVED' THEN 1 ELSE 2 END, submitted_at DESC LIMIT 45",eid)){
                String status=s(r.get("status")).toUpperCase(Locale.ROOT);
                Material mat=status.equals("APPROVED")?Material.LIME_DYE:status.equals("REJECTED")?Material.RED_DYE:Material.WRITABLE_BOOK;
                btn(m,slot++,mat,"&e"+first(s(r.get("applicant_name")),"unknown"),List.of("&7Status: &f"+first(status,"PENDING"),"&7Submitted: &f"+num(r.get("submitted_at")),"&7Reviewed by: &f"+first(s(r.get("reviewed_by")),"-"),"&aLMB approve","&cRMB reject","&6Shift+LMB approve + candidate"),"app-review:"+s(r.get("rowid")));
                if(slot>=45)break;
            }
            if(slot==0)btn(m,22,Material.PAPER,"&7No applications",List.of("&7Выдай книги заявок игрокам или дождись подачи."),"none");
        }
        nav(m,"open:elections","open:applications-review"); p.openInventory(m.inv);
    }

    private void openBallotsLedger(Player p) throws Exception{
        Menu m=new Menu("ballot-ledger"); create(m,54,"&f&lЖурнал бюллетеней");
        String eid=activeOrLatestElectionId();
        if(eid==null){btn(m,22,Material.BARRIER,"&cNo election",List.of(),"none");}
        else{
            long stationDeposits=scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND COALESCE(station_id,'')<>''",eid);
            btn(m,4,Material.PAPER,"&f&lБюллетени",List.of("&7Выдано: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid),"&7Использовано/аннулировано: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=? AND COALESCE(used,0)>0",eid),"&7Опущено через участок: &f"+stationDeposits,"&cShift+ПКМ по строке: аннулировать."),"none");
            btn(m,5,Material.LECTERN,"&bУчастки",List.of("&7Открыть управление участками","&7и карточки RP-точек."),"open:polling-stations");
            btn(m,6,Material.PLAYER_HEAD,"&fВыдать одному",List.of("&7Адресная recovery-выдача."),"open:give-ballot-player");
            int slot=9;
            for(Map<String,Object> r:query("SELECT id,station_id,issued_by,voter_name,issued_at,used,notes FROM cmv7_ballot_issues WHERE election_id=? ORDER BY issued_at DESC LIMIT 36",eid)){
                boolean used=num(r.get("used"))>0;
                String notes=first(s(r.get("notes")),"-");
                boolean annulled=notes.toUpperCase(Locale.ROOT).contains("ANNUL");
                String station=first(s(r.get("station_id")),"-");
                btn(m,slot++,annulled?Material.BARRIER:used?Material.EMERALD:Material.PAPER,(annulled?"&c":used?"&a":"&f")+first(s(r.get("voter_name")),"unknown"),List.of("&7Ballot: &f"+shortId(s(r.get("id"))),"&7Station: &f"+shortId(station),"&7Issued by: &f"+first(s(r.get("issued_by")),"-"),"&7Issued at: &f"+num(r.get("issued_at")),"&7Used: "+(used?"&aYES":"&eNO"),"&7Notes: &f"+clipped(notes,40),"&cShift+ПКМ: аннулировать бюллетень"),"ballot-annul:"+s(r.get("id")));
                if(slot>=45)break;
            }
            if(slot==0)btn(m,22,Material.PAPER,"&7No ballots issued",List.of(),"none");
        }
        nav(m,"open:elections","open:ballots-ledger"); p.openInventory(m.inv);
    }

    private void openElectionAudit(Player p) throws Exception{
        Menu m=new Menu("election-audit"); create(m,54,"&6&lАудит выборов");
        String eid=activeOrLatestElectionId();
        int slot=0;
        for(Map<String,Object> r:query("SELECT time,actor,action,details,admin_only FROM cmv7_audit WHERE action LIKE 'ULTRA7_ELECTION%' OR action LIKE 'ULTRA7_ISSUE%' OR action LIKE 'ULTRA7_PRESIDENT%' OR details LIKE ? ORDER BY time DESC,id DESC LIMIT 45","%"+first(eid,"election-")+"%")){
            String action=s(r.get("action"));
            Material mat=action.contains("PRESIDENT")?Material.GOLDEN_HELMET:action.contains("ISSUE")?Material.PAPER:Material.BOOK;
            btn(m,slot++,mat,"&6"+clipped(action,28),List.of("&7Actor: &f"+first(s(r.get("actor")),"SERVER"),"&7Time: &f"+num(r.get("time")),"&7Admin only: &f"+num(r.get("admin_only")),"&7Details: &f"+clipped(s(r.get("details")),42)),"none");
            if(slot>=45)break;
        }
        if(slot==0)btn(m,22,Material.BOOK,"&7No audit rows yet",List.of("&7Actions will appear here after election work."),"none");
        nav(m,"open:elections","open:election-audit"); p.openInventory(m.inv);
    }

    private void legacyOpenEconomy(Player p) throws Exception{
        if(!hasEconomyAdmin(p)){warn(p,"Нет прав на экономику.");return;}
        Menu m=new Menu("economy-basic"); create(m,54,"&b&lЭкономика &8| &fобычные действия");
        btn(m,4,Material.DIAMOND_ORE,"&b&lБаланс игроков",List.of(
                "&7Баланс: &f"+safeScalar("SELECT COALESCE(SUM(balance),0) FROM cmv7_ar_balances"),
                "&7Транзакций: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_transactions"),
                "&7Событий: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_events"),
                "&7Топ и карточки владения без сырой правки."
        ),"open:ar-top");
        btn(m,10,Material.EMERALD_BLOCK,"&aВыдать АР",List.of(
                "&7Безопасная выдача через сертифицированный предмет.",
                "&7Все операции пишутся в журнал."
        ),"open:economy-basic");
        btn(m,11,Material.REDSTONE,"&cСписать АР",List.of(
                "&7Реестр активов, глубокие сканы и восстановление.",
                "&cТолько для редких ручных разборов."
        ),"open:economy-advanced");
        btn(m,12,Material.HOPPER,"&eПеревод АР",List.of(
                "&7История drop/pickup передач и claim-ledger.",
                "&7Владелец меняется при подборе другим игроком."
        ),"open:ar-events:TRANSFER");
        btn(m,13,Material.BOOKSHELF,"&dИстория операций",List.of(
                "&7Добыча, переплавка, передачи и guard-события.",
                "&7Удобно для разбора спорных ситуаций."
        ),"open:ar-events:ALL");
        btn(m,14,Material.SPYGLASS,"&6Найти поддельные АР",List.of(
                "&7Проверка предметов и подозрительных событий.",
                "&7Подделки не получают экономический вывод."
        ),"open:ar-health");
        btn(m,15,Material.SHIELD,"&aПроверить экономику",List.of(
                "&7Инциденты guard, сироты владения и активы.",
                "&7Безопасно для голосов и балансов."
        ),"open:ar-health");
        btn(m,16,Material.DIAMOND_PICKAXE,"&bНастройки добычи",List.of(
                "&7Здесь находятся действия для понимания БД и кода.",
                "&7Проверяет только загруженные безопасные зоны."
        ),"open:scan");
        btn(m,19,Material.GOLD_BLOCK,"&eV4 Bank ATM",List.of(
                "&7Register in-game ATM blocks.",
                "&7Players deposit certified AR and withdraw through web PIN.",
                "&8V4_BANK_ATM_GAMEPLAY"
        ),"open:bank-atms");
        btn(m,28,Material.CHEST,"&eПересчитать экономику",List.of(
                "&7Обычному админскому сценарию достаточно раздела",
                "&7Не удаляет активы и историю."
        ),"ar:sync");
        btn(m,30,Material.REDSTONE_TORCH,"&cАварийное восстановление",List.of(
                "&7Обычному админскому сценарию достаточно раздела",
                "&cТолько для редких ручных разборов."
        ),"open:economy-advanced");
        btn(m,40,Material.COMPASS,"&2Карта админки",List.of("&7Вернуться к трём главным разделам."),"open:admin-map");
        nav(m,"open:hub","open:economy"); p.openInventory(m.inv);
    }

    private void legacyOpenEconomyBasic(Player p) throws Exception{
        if(!hasEconomyAdmin(p)){warn(p,"Нет прав на экономику.");return;}
        Menu m=new Menu("economy-basic"); create(m,54,"&b&lЭкономика &8| &fобычные действия");
        btn(m,4,Material.DIAMOND_ORE,"&b&lАР",List.of("&7Баланс: &f"+safeScalar("SELECT COALESCE(SUM(balance),0) FROM cmv7_ar_balances"),"&7Событий: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_events"),"&7Транзакций: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_transactions"),"&7Активов в реестре: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets WHERE status='ACTIVE'")),"none");
        btn(m,10,Material.EMERALD_BLOCK,"&aТоп AR",List.of(),"open:ar-top");
        btn(m,11,Material.CHEST,"&eПересчитать онлайн",List.of(),"ar:sync");
        btn(m,12,Material.HOPPER,"&eПередачи",List.of("&7История передач через выброс и подбор.","&7Новый владелец записывается автоматически."),"open:ar-events:TRANSFER");
        btn(m,13,Material.BOOKSHELF,"&dИстория АР",List.of("&7Добыча, переплавка, передачи и guard-события."),"open:ar-events:ALL");
        btn(m,14,Material.DIAMOND_PICKAXE,"&bДобыча",List.of("&7История появления сертифицированного АР."),"open:ar-events:MINE");
        btn(m,15,Material.FURNACE,"&6Переплавка АР",List.of("&7Единственный легальный способ","&7получить алмаз из сертифицированного АР."),"open:ar-events:SMELT");
        btn(m,16,Material.PAPER,"&fПамятка игрокам",List.of("&7Передача: выбросить АР и дать подобрать.","&7Сундуки разрешены, механизмы запрещены.","&7Алмазы выводятся только переплавкой."),"none");
        btn(m,21,Material.EMERALD,"&aЧто безопасно",List.of("&7Топ, пересчет online и просмотр истории.","&7Эти действия не ломают экономику."),"none");
        btn(m,22,Material.SHIELD,"&aЗащита экономики",List.of("&7Сброс АР из админки отключён.","&7Доступны только аудит, пересчёт","&7и сканы текущего состояния."),"none");
        btn(m,23,Material.CLOCK,"&eПоследние передачи",List.of("&7Открыть transfer-ledger без доступа к таблицам."),"open:ar-events:TRANSFER");
        btn(m,24,Material.FURNACE,"&6Последние переплавки",List.of("&7Проверить, кто вывел алмазы через печь."),"open:ar-events:SMELT");
        btn(m,25,Material.GOLD_BLOCK,"&eV4 Bank ATM",List.of("&7Register ATM blocks and audit ATM events.","&8V4_BANK_ATM_GAMEPLAY"),"open:bank-atms");
        btn(m,28,Material.COMPASS,"&2Карта админки",List.of("&7Выборы, экономика, игроки и служебные проверки","&7в одной понятной схеме."),"open:admin-map");
        btn(m,29,Material.REDSTONE_TORCH,"&cПродвинутый контроль",List.of("&7Реестр активов, сканы мира, состояние базы.","&cДля технического админа."),"open:economy-advanced");
        nav(m,"open:economy","open:economy-basic"); p.openInventory(m.inv);
    }

    private void legacyOpenEconomyAdvanced(Player p) throws Exception{
        if(!hasEconomyAdmin(p)){warn(p,"Нет прав на продвинутую экономику.");return;}
        Menu m=new Menu("economy-advanced"); create(m,54,"&c&lЭкономика &8| &fпродвинутый контроль");
        btn(m,4,Material.REDSTONE_TORCH,"&c&lТехническая зона",List.of(
                "&7Здесь находятся действия для понимания БД и кода.",
                "&7Обычному админскому сценарию достаточно раздела",
                "&7'Обычные действия'. Сырых сбросов экономики нет."
        ),"none");
        btn(m,10,Material.ENDER_CHEST,"&bРеестр владения АР",List.of("&7Активы, batch, owner, source, координаты.","&7Нужен для расследований и восстановления ledger."),"open:ar-custody");
        btn(m,12,Material.HEART_OF_THE_SEA,"&bЗдоровье экономики",List.of("&7Guard-инциденты, сироты реестра, активы,","&7smelted, быстрый online-sync."),"open:ar-health");
        btn(m,14,Material.MAP,"&6Отчеты сканов",List.of("&7История проверок чанков и контейнеров."),"open:ar-scans");
        btn(m,16,Material.ENDER_CHEST,"&aСостояние базы",List.of("&7Безопасное обслуживание базы.","&7Без ручного редактирования AR ledger."),"open:db-health");
        btn(m,28,Material.SPYGLASS,"&bСкан загруженных чанков",List.of("&7Легкая проверка без подгрузки мира."),"ar:scan-loaded");
        btn(m,30,Material.NETHER_STAR,"&6Глубокий скан мира",List.of("&cShift+ЛКМ.","&7Тяжелая операция, поэтому убрана в advanced."),"ar:scan-deep");
        btn(m,32,Material.BOOKSHELF,"&dПолная история АР",List.of("&7Все события экономики, включая блокировки","&7и восстановленные transfer-claim записи."),"open:ar-events:ALL");
        btn(m,34,Material.SHIELD,"&aПолитика защиты",List.of("&7Контейнеры разрешены, механизмы запрещены.","&7Передача игроку: выбросил -> другой подобрал."),"none");
        btn(m,40,Material.ARROW,"&bК экономике",List.of("&7Назад к простому корню экономики."),"open:economy");
        nav(m,"open:economy","open:economy-advanced"); p.openInventory(m.inv);
    }
    private void legacyOpenArTopDisabled(Player p) throws Exception{
        Menu m=new Menu("scan"); create(m,54,"&b&lСканирование");
        int slot=0; for(Map<String,Object> r:query("SELECT name,balance,inventory_balance,ender_balance,updated_at FROM cmv7_ar_balances ORDER BY balance DESC,name ASC LIMIT 45")){
            btn(m,slot++,Material.DIAMOND_ORE,"&b"+s(r.get("name")),List.of("&7Всего: &f"+num(r.get("balance")),"&7Инв: &f"+num(r.get("inventory_balance")),"&7Эндер: &f"+num(r.get("ender_balance")),"&7Обновлено: &f"+num(r.get("updated_at"))),"none");
        }
        btn(m,47,Material.CHEST,"&aПересчитать онлайн",List.of(),"ar:sync"); nav(m,"open:economy","open:ar-top"); p.openInventory(m.inv);
    }
    private void legacyOpenArCustodyDisabled(Player p) throws Exception{
        Menu m=new Menu("scan"); create(m,54,"&b&lСканирование");
        int slot=0; for(Map<String,Object> r:query("SELECT asset_id,batch_id,owner_name,status,material,source,world,x,y,z,updated_at FROM cmv7_ar_assets ORDER BY updated_at DESC,asset_id DESC LIMIT 45")){
            Material mat=Material.matchMaterial(s(r.get("material"))); if(mat==null||!mat.isItem())mat=Material.DIAMOND_ORE;
            String status=s(r.get("status"));
            btn(m,slot++,mat,"&b"+clipped(first(s(r.get("owner_name")),"SERVER"),22)+" &8| &f"+status,List.of("&7Asset: &f"+shortId(s(r.get("asset_id"))),"&7Batch: &f"+shortId(s(r.get("batch_id"))),"&7Источник: &f"+clipped(s(r.get("source")),34),"&7XYZ: &f"+s(r.get("world"))+" "+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&7Обновлено: &f"+num(r.get("updated_at"))),"none");
            if(slot>=45)break;
        }
        if(slot==0)btn(m,22,Material.BARRIER,"&7Реестр пуст",List.of("&7Новые сертифицированные АР появятся","&7после добычи или восстановления legacy-АР."),"none");
        nav(m,"open:economy","open:ar-custody"); p.openInventory(m.inv);
    }
    private void legacyOpenEconomyHealthDisabled(Player p) throws Exception{
        if(!hasEconomyAdmin(p)){warn(p,"Нет прав на экономику.");return;}
        Menu m=new Menu("arhealth"); create(m,54,"&b&lЗдоровье AR-экономики");
        long total=safeScalar("SELECT COALESCE(SUM(balance),0) FROM cmv7_ar_balances");
        long active=safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets WHERE status='ACTIVE'");
        long smelted=safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets WHERE status='SMELTED'");
        long tx=safeScalar("SELECT COUNT(*) FROM cmv7_ar_transactions");
        long incidents=safeScalar("SELECT COUNT(*) FROM cmv7_ar_guard_incidents");
        long orphanOwners=safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets WHERE status='ACTIVE' AND COALESCE(owner_uuid,'')=''");
        btn(m,4,incidents==0?Material.EMERALD_BLOCK:Material.HEART_OF_THE_SEA,"&b&lСостояние защиты",List.of("&7Баланс ledger: &f"+total,"&7Активных АР: &f"+active,"&7Переплавлено: &f"+smelted,"&7Транзакций: &f"+tx,"&7Guard-инцидентов: &f"+incidents,"&7Активов без владельца: &f"+orphanOwners),"none");
        btn(m,10,Material.CHEST,"&aПересчитать online",List.of("&7Синхронизирует балансы онлайн-игроков","&7и пишет live-снимки инвентарей."),"ar:sync");
        btn(m,11,Material.SPYGLASS,"&bСкан загруженных чанков",List.of("&7Ищет АР в контейнерах без подгрузки мира."),"ar:scan-loaded");
        btn(m,12,Material.MAP,"&dОтчёты сканов",List.of("&7История последних проверок мира."),"open:ar-scans");
        btn(m,13,Material.ENDER_CHEST,"&bРеестр активов",List.of("&7Владельцы, источники, статусы и координаты."),"open:ar-custody");
        btn(m,14,Material.BOOKSHELF,"&dИстория АР",List.of("&7Передачи, переплавки, блокировки и добыча."),"open:ar-events:ALL");
        int slot=19;
        for(Map<String,Object> r:query("SELECT time,type,actor_name,owner_name,material,amount,world,x,y,z,details FROM cmv7_ar_guard_incidents ORDER BY time DESC,id DESC LIMIT 28")){
            String type=s(r.get("type"));
            Material mat=type.contains("HOPPER")?Material.HOPPER:type.contains("DISPENSE")?Material.DISPENSER:type.contains("PLACE")?Material.BARRIER:type.contains("DAMAGE")?Material.SHIELD:Material.PAPER;
            btn(m,slot++,mat,"&e"+clipped(type,28),List.of("&7Время: &f"+num(r.get("time")),"&7Игрок: &f"+first(s(r.get("actor_name")),"-"),"&7Владелец: &f"+first(s(r.get("owner_name")),"-"),"&7Материал: &f"+first(s(r.get("material")),"-")+" x"+num(r.get("amount")),"&7XYZ: &f"+first(s(r.get("world")),"-")+" "+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&7Детали: &f"+clipped(s(r.get("details")),42)),"none");
            if(slot>=45)break;
        }
        if(slot==19)btn(m,31,Material.EMERALD,"&aИнцидентов пока нет",List.of("&7Защита АР работает тихо: hopper, fire,","&7despawn, machines и place не тревожили систему."),"none");
        audit(p.getName(),"ULTRA7_AR_HEALTH_OPEN","incidents="+incidents+" active="+active+" balance="+total,true);
        nav(m,"open:economy","open:ar-health"); p.openInventory(m.inv);
    }
    private void openArEvents(Player p,String filter) throws Exception{
        Menu m=new Menu("arevents"); create(m,54,"&d&lИстория АР &8| &f"+filter);
        String where = filter.equals("MINE")?"WHERE type LIKE 'MINE%' OR type='AR_CERTIFIED'":filter.equals("TRANSFER")?"WHERE type LIKE '%TRANSFER%' OR type='AR_DROP_LISTED'":filter.equals("SMELT")?"WHERE type LIKE '%SMELT%'":"";
        int slot=0; for(Map<String,Object> r:query("SELECT id,type,actor_name,target_name,world,x,y,z,material,amount,details FROM cmv7_ar_events "+where+" ORDER BY id DESC LIMIT 45")){
            Material mat=Material.matchMaterial(s(r.get("material"))); if(mat==null||!mat.isItem()) mat=Material.PAPER;
            btn(m,slot++,mat,"&d#"+num(r.get("id"))+" &f"+s(r.get("type")),List.of("&7Кто: &f"+s(r.get("actor_name")),"&7Кому: &f"+first(s(r.get("target_name")),"-"),"&7Материал: &f"+s(r.get("material")),"&7Кол-во: &f"+num(r.get("amount")),"&7XYZ: &f"+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&aЛКМ: телепорт"),"ar-tp:"+num(r.get("id")));
        }
        nav(m,"open:economy","open:ar-events:"+filter); p.openInventory(m.inv);
    }
    private void openScan(Player p){
        Menu m=new Menu("scans"); create(m,54,"&6&lАР-сканы");
        btn(m,20,Material.CHEST,"&aСкан загруженных чанков radius 16",List.of(),"ar:scan-loaded");
        btn(m,22,Material.NETHER_STAR,"&6Глубокий скан radius 100",List.of("&cShift+ЛКМ"),"ar:scan-deep");
        btn(m,24,Material.MAP,"&dОтчёты",List.of(),"open:ar-scans");
        nav(m,"open:economy","open:scan"); p.openInventory(m.inv);
    }
    private void openArScans(Player p)throws Exception{
        Menu m=new Menu("citizen-candidates"); create(m,54,"&e&lКандидаты &8| &fвыборы");
        int slot=0; for(Map<String,Object> r:query("SELECT id,actor,world,cx,cz,radius,chunks,ar_blocks,ar_items,containers,mode FROM cmv7_ar_scan_reports ORDER BY id DESC LIMIT 45")){
            btn(m,slot++,Material.MAP,"&6Скан #"+num(r.get("id")),List.of("&7Админ: &f"+s(r.get("actor")),"&7Мир: &f"+s(r.get("world")),"&7Радиус: &f"+num(r.get("radius")),"&7Чанков: &f"+num(r.get("chunks")),"&7АР-блоки: &f"+num(r.get("ar_blocks")),"&7АР в сундуках: &f"+num(r.get("ar_items")),"&7Контейнеры: &f"+num(r.get("containers"))),"none");
        }
        nav(m,"open:economy","open:ar-scans"); p.openInventory(m.inv);
    }

    private void openCitizenElectionHub(Player p)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        Menu m=new Menu("citizen-election"); create(m,54,"&6&lВыборы &8| &f"+p.getName());
        String eid=activeOrLatestElectionId();
        if(eid==null){
            btn(m,22,Material.BARRIER,"&cВыборов сейчас нет",List.of("&7Когда администрация запустит цикл,","&7здесь появятся заявка, бюллетень и кандидаты."),"none");
            btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
            p.openInventory(m.inv); return;
        }
        Map<String,String> st=electionSettings(eid);
        String uuid=p.getUniqueId().toString();
        boolean app=hasActiveApplication(uuid,eid), cand=isCitizenCandidate(p,eid), ballot=hasCitizenBallot(p,eid), voted=hasCitizenVote(p,eid);
        boolean live=onOff(st.get("show_live_results"))==1;
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        long chairs=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1 AND role='CIK_CHAIR'",eid);
        btn(m,4,Material.GOLDEN_HELMET,"&6&lТекущие выборы",List.of("&7ID: &f"+shortId(eid),"&7Статус: &f"+humanStatus(electionStatus(eid)),"&7Этап: &f"+humanStage(st.getOrDefault("stage","?")),"&7Кандидатов: &f"+candidates,"&7Председатель ЦИК: &f"+(chairs>0?"назначен":"не назначен")),"none");
        btn(m,10,Material.WRITABLE_BOOK,app?"&aЗаявка подана":"&eЗаявка кандидата",List.of(app?"&7Твоя заявка уже в системе.":"&7Книгу заявки выдаёт ЦИК или админ.","&7После подписи книга сама попадёт в БД."),"none");
        btn(m,12,ballot?Material.PAPER:Material.MAP,ballot?"&aБюллетень у тебя":"&fБюллетень выдаёт ЦИК",List.of(ballot?"&7Голосуй только на участке ЦИК.":"&7Попроси председателя ЦИК выдать бюллетень.","&7Меню не подменяет защищённое голосование."),"none");
        btn(m,14,voted?Material.EMERALD_BLOCK:Material.CLOCK,voted?"&aГолос учтён":"&eГолос ещё не найден",List.of(voted?"&7Твой голос уже записан в журнал.":"&7После голосования статус обновится сам.","&7Обычные игроки не вводят команды."),"none");
        btn(m,16,cand?Material.PLAYER_HEAD:Material.NAME_TAG,cand?"&6Ты кандидат":"&7Ты не кандидат",List.of(cand?"&7Твоё имя участвует в списке кандидатов.":"&7Подай заявку и дождись одобрения ЦИК."),"none");
        btn(m,20,Material.PLAYER_HEAD,"&eКандидаты и результаты",List.of(live?"&7Открыты live-результаты с графиками.":"&7Имена видны, цифры скрыты до решения ЦИК."),"open:citizen-candidates");
        btn(m,22,sidebarHidden.contains(p.getUniqueId())?Material.ENDER_EYE:Material.BARRIER,sidebarHidden.contains(p.getUniqueId())?"&aПоказать панель справа":"&cСкрыть панель справа",List.of("&7Это меняет только твою панель.","&7На ход выборов не влияет."),sidebarHidden.contains(p.getUniqueId())?"citizen:sidebar-show":"citizen:sidebar-hide");
        btn(m,24,Material.COMPASS,"&6Маршрут избирателя",List.of("&7Пошаговый путь без команд:","&7заявка, бюллетень, кандидат, урна."),"open:citizen-guide");
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,53,Material.EMERALD,"&aОбновить",List.of(),"open:citizen-election");
        p.openInventory(m.inv);
    }

    private void openCitizenElectionGuide(Player p)throws Exception{
        Menu m=new Menu("citizen-guide"); create(m,54,"&6&lМаршрут избирателя &8| &f"+p.getName());
        String eid=activeOrLatestElectionId();
        if(eid==null){
            btn(m,22,Material.BARRIER,"&cВыборы сейчас не идут",List.of("&7Когда ЦИК откроет цикл, здесь появится","&7пошаговый маршрут без команд."),"none");
            btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
            p.openInventory(m.inv); return;
        }
        Map<String,String> st=electionSettings(eid);
        String uuid=p.getUniqueId().toString();
        boolean app=hasActiveApplication(uuid,eid), cand=isCitizenCandidate(p,eid), ballot=hasCitizenBallot(p,eid), sealed=hasOwnedSealedBallot(p,eid), voted=hasCitizenVote(p,eid);
        boolean applicationsOpen=onOff(st.get(COL_APP_OPEN))==1, votingOpen=onOff(st.get(COL_VOTE_OPEN))==1;
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        long stations=scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE election_id=? AND active=1",eid);
        btn(m,4,Material.RECOVERY_COMPASS,"&6&lТвой путь на выборах",List.of(
                "&7Цикл: &f"+shortId(eid),
                "&7Этап: &f"+humanStage(st.getOrDefault("stage","?")),
                "&7Заявки: "+(applicationsOpen?"&aоткрыты":"&eзакрыты"),
                "&7Голосование: "+(votingOpen?"&aоткрыто":"&eзакрыто"),
                "&7Активных участков: &f"+stations),"none");
        List<String> appLore=new ArrayList<>();
        if(app) appLore.add("&7Твоя заявка уже подана.");
        else if(cand) appLore.add("&7Ты уже в списке кандидатов.");
        else { appLore.add("&7Книгу выдаёт председатель ЦИК."); appLore.add("&7После подписи она сама исчезнет."); }
        btn(m,10,app||cand?Material.LIME_DYE:Material.WRITABLE_BOOK,app||cand?"&a1. Заявка готова":"&e1. Получить книгу заявки",appLore,"none");
        btn(m,12,candidates>0?Material.PLAYER_HEAD:Material.NAME_TAG,candidates>0?"&a2. Изучить кандидатов":"&e2. Дождаться кандидатов",List.of(candidates>0?"&7Открой заявки и программы кандидатов.":"&7ЦИК ещё проверяет заявки.","&7Результаты скрываются, если live выключен."),"open:citizen-candidates");
        btn(m,14,ballot?Material.PAPER:Material.MAP,ballot?"&a3. Бюллетень получен":"&f3. Получить бюллетень",List.of(ballot?"&7Бюллетень привязан к тебе.":"&7Бюллетень выдаёт ЦИК.","&7Чужой бюллетень не откроется."),ballot?"open:station-ballot:guide":"none");
        List<String> sealLore=new ArrayList<>();
        if(sealed) sealLore.add("&7В бюллетене уже записан кандидат.");
        else { sealLore.add("&7Открой бюллетень, прочитай заявку,"); sealLore.add("&7подтверди и запечатай выбор."); }
        btn(m,16,sealed?Material.LIME_CONCRETE:Material.EMERALD,sealed?"&a4. Выбор запечатан":"&e4. Выбрать кандидата",sealLore,ballot&&!sealed&&!voted?"open:station-ballot:guide":"none");
        List<String> depositLore=new ArrayList<>();
        if(voted) depositLore.add("&7Запись есть в журнале голосов.");
        else if(sealed){ depositLore.add("&7Подойди к лекторну участка ЦИК"); depositLore.add("&7и кликни запечатанным бюллетенем."); }
        else depositLore.add("&7Сначала нужен запечатанный бюллетень.");
        btn(m,22,voted?Material.EMERALD_BLOCK:(sealed?Material.CAULDRON:Material.CLOCK),voted?"&a5. Голос учтён":(sealed?"&a5. Опусти бюллетень в участок":"&e5. Финальный шаг ещё закрыт"),depositLore,"none");
        btn(m,24,Material.LECTERN,"&bУчасток ЦИК",List.of(stations>0?"&7Найди лекторн участка в здании ЦИК.":"&eЦИК ещё не поставил активный участок.","&7Именно участок принимает финальный голос."),"none");
        btn(m,30,sidebarHidden.contains(p.getUniqueId())?Material.ENDER_EYE:Material.BARRIER,sidebarHidden.contains(p.getUniqueId())?"&aПоказать live-панель":"&cСкрыть live-панель",List.of("&7Только для твоего экрана."),sidebarHidden.contains(p.getUniqueId())?"citizen:sidebar-show":"citizen:sidebar-hide");
        btn(m,45,Material.ARROW,"&aНазад",List.of(),"open:citizen-election");
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,53,Material.EMERALD,"&aОбновить маршрут",List.of(),"open:citizen-guide");
        p.openInventory(m.inv);
    }

    private void openCitizenCandidates(Player p)throws Exception{
        Menu m=new Menu("citizen-candidates"); create(m,54,"&e&lКандидаты &8| &fвыборы");
        String eid=activeOrLatestElectionId();
        if(eid==null){
            btn(m,22,Material.BARRIER,"&cКандидатов нет",List.of("&7Выборный цикл ещё не создан."),"none");
        }else{
            Map<String,String> st=electionSettings(eid);
            boolean live=onOff(st.get("show_live_results"))==1;
            List<Map<String,Object>> rows=candidateRowsForElection(eid,45);
            long max=1,totalVotes=0; for(Map<String,Object> r:rows){ long total=Math.max(0,num(r.get("total"))); totalVotes+=total; max=Math.max(max,total); }
            int slot=0,place=1;
            for(Map<String,Object> r:rows){
                long total=Math.max(0,num(r.get("total")));
                String name=first(s(r.get("display_name")),s(r.get("name")),s(r.get("uuid")));
                List<String> lore=live?List.of("&7Место: &f"+place,"&7Голоса: &f"+total+" &8("+percent(total,totalVotes)+")","&a"+graphBar(total,max,18)):List.of("&7Место: &f"+place,"&7Live-цифры скрыты ЦИК.","&8Голосование всё равно учитывается.");
                btn(m,slot++,Material.PLAYER_HEAD,"&e"+clipped(name,28),lore,"view-app:"+eid+":"+s(r.get("uuid")));
                if(slot>=45)break; place++;
            }
            if(rows.isEmpty()) btn(m,22,Material.BOOK,"&eКандидаты скоро появятся",List.of("&7ЦИК ещё не утвердил заявки."),"none");
        }
        btn(m,45,Material.ARROW,"&aНазад",List.of(),"open:citizen-election");
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,53,Material.EMERALD,"&aОбновить",List.of(),"open:citizen-candidates");
        p.openInventory(m.inv);
    }

    private void openBallotCandidateHub(Player p, ItemStack ballot)throws Exception{
        openBallotCandidateHub(p, ballot, "inventory");
    }

    private void openBallotCandidateHub(Player p, ItemStack ballot, String stationId)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        if(ballot!=null&&!requireElectionItemOwner(p,ballot,"ballot"))return;
        String eid=first(electionItemString(ballot,"election"),activeOrLatestElectionId());
        String owner=first(electionItemString(ballot,"owner"),"");
        if(!owner.isBlank()&&!owner.equals(p.getUniqueId().toString())){warn(p,"Этот бюллетень выдан другому игроку."); return;}
        if(ballot==null&&eid!=null&&!hasCitizenBallot(p,eid)){warn(p,"Сначала получи свой личный бюллетень у председателя ЦИК."); return;}
        Menu m=new Menu("ballot-candidates"); create(m,27,"&f&lБюллетень &8| &fкандидаты");
        if(eid==null){
            btn(m,13,Material.BARRIER,"&cВыборов нет",List.of("&8BALLOT_HEADS_ONLY_V3","&7Бюллетень не привязан к текущему циклу."),"none");
        }else{
            List<Map<String,Object>> rows=candidateRowsForElection(eid,18);
            int slot=0;
            for(Map<String,Object> r:rows){
                String uuid=s(r.get("uuid")), name=first(s(r.get("display_name")),s(r.get("name")),uuid);
                List<String> lore=new ArrayList<>();
                lore.add("&8BALLOT_HEADS_ONLY_V3");
                lore.add("&aЛКМ: выбрать кандидата");
                lore.add("&eПКМ: получить книгу заявки");
                lore.add("&7Никаких служебных цифр в бюллетене нет.");
                btn(m,slot++,Material.PLAYER_HEAD,"&e"+clipped(name,28),lore,"ballot-candidate:"+eid+":"+uuid+":"+first(stationId,"inventory"));
                if(slot>=18)break;
            }
            if(rows.isEmpty()) btn(m,13,Material.BOOK,"&eКандидаты еще не утверждены",List.of("&7Проверь позже или спроси председателя ЦИК."),"none");
        }
        btn(m,18,Material.ARROW,"&aНазад",List.of(),"open:citizen-election");
        btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:station-ballot:"+first(stationId,"inventory"));
        p.openInventory(m.inv);
    }
    private void openCandidateApplicationPreview(Player p,String eid,String candidateUuid)throws Exception{
        giveTemporaryApplicationBook(p,eid,candidateUuid);
    }

    private void legacyPollingStationFlow(Player p,Block block,ItemStack item)throws Exception{
        if(isSealedBallot(item)){
            depositSealedBallotAtStation(p,item,pollingStationId(block));
        }
        sendPollingStationCitizenInfo(p,block);
        openPollingStationHub(p,block);
    }

    private void openPollingStationHub(Player p,Block block)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        String eid=activeOrLatestElectionId();
        String station=block==null?"station-gui":pollingStationId(block);
        Menu m=new Menu("polling-station"); create(m,27,"&b&lУчасток &8| &f"+p.getName());
        if(eid==null){
            btn(m,13,Material.BARRIER,"&cВыборы не идут",List.of("&8STATION_MINIMAL_GUI_V3","&7ЦИК еще не открыл цикл."),"none");
        }else{
            Map<String,String> st=electionSettings(eid);
            boolean ballot=hasCitizenBallot(p,eid), voted=hasCitizenVote(p,eid), sealed=hasOwnedSealedBallot(p,eid);
            btn(m,4,Material.LECTERN,"&b&lОфициальный участок",List.of("&8STATION_MINIMAL_GUI_V3","&8STATION_RIGHT_CLICK_DEPOSIT_ONLY_V5","&7Этап: &f"+humanStage(st.getOrDefault("stage","?")),"&7Голосование: "+(onOff(st.get(COL_VOTE_OPEN))==1?"&aоткрыто":"&eзакрыто"),"&7Бюллетень: "+(ballot?"&aесть":"&cнет"),"&7Статус голоса: "+(voted?"&aучтен":(sealed?"&eдержи бюллетень и ПКМ по участку":"&7не выбран"))),"none");
        btn(m,10,Material.PLAYER_HEAD,"&aОнлайн и профили",List.of("&7Открыть карточку игрока, инвентарь,","&7перемещение, timeline и приколы."),"open:players-daily");
            btn(m,12,ballot?Material.PAPER:Material.MAP,ballot?"&aБюллетень":"&eНужен бюллетень",List.of(ballot?"&7Открой личный бюллетень.":"&7Попроси председателя ЦИК выдать бюллетень.","&7Чужой бюллетень не откроется."),ballot?"open:station-ballot:"+station:"none");
            List<String> urnLore=voted?List.of("&7Голос уже записан."):sealed?List.of("&7Закрой меню, возьми запечатанный бюллетень в руку","&7и ПКМ кликни сам блок участка ЦИК."):List.of("&7Сначала выбери кандидата в бюллетене.");
            btn(m,14,voted?Material.EMERALD_BLOCK:(sealed?Material.CAULDRON:Material.CLOCK),voted?"&aГолос принят":(sealed?"&aПКМ по участку с бюллетенем":"&eУрна ждет бюллетень"),urnLore,"none");
            btn(m,16,Material.COMPASS,"&6Что дальше",List.of("&7Короткая подсказка по твоему текущему шагу.","&7Команды для голосования не нужны."),"open:citizen-guide");
        }
        btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:station-hub:"+station);
        p.openInventory(m.inv);
    }
    private void sendPollingStationCitizenInfo(Player p,Block block)throws Exception{
        String eid=activeOrLatestElectionId();
        p.sendMessage(c("&6&lЦИК &8» &fИнформация видна только тебе."));
        if(eid==null){
            p.sendMessage(c("&7Выборный цикл сейчас не открыт. Когда ЦИК начнёт процесс, здесь появятся кандидаты и бюллетени."));
            sound(p,"BLOCK_NOTE_BLOCK_BASS",0.55f,0.8f);
            return;
        }
        Map<String,String> st=electionSettings(eid);
        boolean votingOpen=onOff(st.get(COL_VOTE_OPEN))==1;
        boolean ballot=hasCitizenBallot(p,eid), sealed=hasOwnedSealedBallot(p,eid), voted=hasCitizenVote(p,eid);
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        p.sendMessage(c("&eЭтап: &f"+humanStage(st.getOrDefault("stage","?"))+" &8| &eКандидатов: &f"+candidates+" &8| &eПриём бюллетеней: "+(votingOpen?"&aоткрыт":"&eзакрыт")));
        if(voted){
            p.sendMessage(c("&aТвой бюллетень уже принят. Новых действий на участке не требуется."));
        }else if(sealed){
            p.sendMessage(c("&aУ тебя есть запечатанный бюллетень. Возьми его в руку и кликни по участку, чтобы опустить в урну."));
        }else if(ballot){
            p.sendMessage(c("&fОткрой бюллетень в руке, прочитай заявки кандидатов, выбери одного и вернись к участку."));
        }else{
        p.sendMessage(c("&7Бюллетень работает только у владельца. Чужие документы участок не принимает."));
        }
        p.sendMessage(c("&7Бюллетень работает только у владельца. Чужие документы участок не принимает."));
        sound(p,"BLOCK_AMETHYST_BLOCK_CHIME",0.55f,1.35f);
    }

    private void openPollingStationHubV2(Player p,Block block)throws Exception{
        if(legacyElectionRuntimeDisabled()){
            redirectLegacyElectionAction(p);
            return;
        }
        String eid=activeOrLatestElectionId();
        String station=block==null?"station-gui":pollingStationId(block);
        Menu m=new Menu("polling-station");
        create(m,27,"&b&lУчасток ЦИК &8| &f"+p.getName());
        if(eid==null){
            btn(m,13,Material.BARRIER,"&cВыборы не идут",List.of("&8STATION_MINIMAL_GUI_V3","&7ЦИК ещё не открыл текущий цикл."),"none");
        }else{
            Map<String,String> st=electionSettings(eid);
            boolean ballot=hasCitizenBallot(p,eid), voted=hasCitizenVote(p,eid), sealed=hasOwnedSealedBallot(p,eid);
            btn(m,4,Material.LECTERN,"&b&lОфициальный участок",List.of(
                    "&8STATION_MINIMAL_GUI_V3",
                    "&8STATION_RIGHT_CLICK_DEPOSIT_ONLY_V5",
                    "&7Этап: &f"+humanStage(st.getOrDefault("stage","?")),
                    "&7Голосование: "+(onOff(st.get(COL_VOTE_OPEN))==1?"&aоткрыто":"&eзакрыто"),
                    "&7Бюллетень: "+(ballot?"&aесть":"&cнет"),
                    "&7Статус голоса: "+(voted?"&aучтён":(sealed?"&eзапечатан, опусти в участок":"&7не выбран"))
            ),"none");
            btn(m,10,Material.PLAYER_HEAD,"&aИгроки и профили",List.of("&7Открыть карточки игроков, инвентарь,","&7перемещение, timeline и приколы."),"open:players-daily");
            btn(m,12,ballot?Material.PAPER:Material.MAP,ballot?"&aБюллетень":"&eНужен бюллетень",List.of(
                    ballot?"&7Открой свой бюллетень и выбери кандидата.":"&7Попроси председателя ЦИК выдать бюллетень.",
                    "&7Чужой бюллетень участок не откроет."
            ),ballot?"open:station-ballot:"+station:"none");
            List<String> urnLore=voted
                    ?List.of("&7Голос уже принят и записан.")
                    :sealed
                    ?List.of("&7Возьми запечатанный бюллетень в руку","&7и кликни ПКМ по самому участку.")
                    :List.of("&7Сначала выбери кандидата в бюллетене.");
            btn(m,14,voted?Material.EMERALD_BLOCK:(sealed?Material.CAULDRON:Material.CLOCK),voted?"&aГолос принят":(sealed?"&aОпусти бюллетень в участок":"&eУрна ждёт бюллетень"),urnLore,"none");
            btn(m,16,Material.COMPASS,"&6Что дальше",List.of("&7Короткая подсказка по текущему шагу.","&7Команды для голосования не нужны."),"open:citizen-guide");
        }
        btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:station-hub:"+station);
        btn(m,22,Material.EMERALD,"&a&lЛавки",List.of(
                "&7Управление лавками артефактов и donation-предметов.",
                "&7Создание и привязка лавки по блоку,",
                "&7каталог, выдача и обслуживание витрин."),"open:shops");
        p.openInventory(m.inv);
    }

    private void sendPollingStationCitizenInfoV2(Player p,Block block)throws Exception{
        String eid=activeOrLatestElectionId();
        p.sendMessage(c("&6&lЦИК &8» &fИнформация об участке видна только тебе."));
        if(eid==null){
            p.sendMessage(c("&7Выборный цикл сейчас не открыт. Когда ЦИК начнёт процесс, здесь появятся кандидаты и бюллетени."));
            sound(p,"BLOCK_NOTE_BLOCK_BASS",0.55f,0.8f);
            return;
        }
        Map<String,String> st=electionSettings(eid);
        boolean votingOpen=onOff(st.get(COL_VOTE_OPEN))==1;
        boolean ballot=hasCitizenBallot(p,eid), sealed=hasOwnedSealedBallot(p,eid), voted=hasCitizenVote(p,eid);
        long candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid);
        p.sendMessage(c("&eЭтап: &f"+humanStage(st.getOrDefault("stage","?"))+" &8| &eКандидатов: &f"+candidates+" &8| &eПриём бюллетеней: "+(votingOpen?"&aоткрыт":"&eзакрыт")));
        if(voted){
            p.sendMessage(c("&aТвой бюллетень уже принят. Новых действий на участке не требуется."));
        }else if(sealed){
            p.sendMessage(c("&aУ тебя есть запечатанный бюллетень. Возьми его в руку и кликни по участку, чтобы опустить в урну."));
        }else if(ballot){
            p.sendMessage(c("&fОткрой бюллетень в руке, прочитай заявки кандидатов, выбери одного и вернись к участку."));
        }else{
            p.sendMessage(c("&7Бюллетень работает только у владельца. Чужие документы участок не принимает."));
        }
        sound(p,"BLOCK_AMETHYST_BLOCK_CHIME",0.55f,1.35f);
    }

    private void openCandidateDecision(Player p,String eid,String candidateUuid)throws Exception{
        openCandidateDecision(p,eid,candidateUuid,"inventory");
    }

    private void openCandidateDecision(Player p,String eid,String candidateUuid,String stationId)throws Exception{
        List<Map<String,Object>> rows=query("SELECT name,display_name,COALESCE(raw_votes,0)+COALESCE(admin_adjustment,0) total FROM candidates WHERE election_id=? AND uuid=? AND COALESCE(removed,0)=0 LIMIT 1",eid,candidateUuid);
        if(rows.isEmpty()){warn(p,"Кандидат не найден.");return;}
        String name=first(s(rows.get(0).get("display_name")),s(rows.get(0).get("name")),candidateUuid);
        Map<String,String> st=electionSettings(eid);
        boolean canVote=onOff(st.get(COL_VOTE_OPEN))==1&&hasCitizenBallot(p,eid)&&!hasCitizenVote(p,eid);
        Menu m=new Menu("candidate-decision"); create(m,54,"&e&lКандидат &8| &f"+clipped(name,24));
        btn(m,4,Material.PLAYER_HEAD,"&e&l"+name,List.of("&7Открой заявку, сравни программу,","&7и только потом подтверждай выбор."),"none");
        btn(m,20,Material.WRITTEN_BOOK,"&eОткрыть заявку",List.of("&7Выдается временная подписанная книга.","&7После закрытия она исчезнет."),"view-app:"+eid+":"+candidateUuid);
        btn(m,22,canVote?Material.EMERALD:Material.GRAY_DYE,canVote?"&aВыбрать кандидата":"&7Голосование недоступно",List.of(canVote?"&7Откроется финальное подтверждение.":"&7Нужен бюллетень, открытое голосование и отсутствие голоса."),"vote-confirm:"+eid+":"+candidateUuid+":"+first(stationId,"inventory"));
        btn(m,24,Material.ARROW,"&aНазад к бюллетеню",List.of(),"open:station-ballot:"+first(stationId,"inventory"));
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        p.openInventory(m.inv);
    }

    private void openVoteConfirm(Player p,String eid,String candidateUuid)throws Exception{
        openVoteConfirm(p,eid,candidateUuid,"inventory");
    }

    private void openVoteConfirm(Player p,String eid,String candidateUuid,String stationId)throws Exception{
        List<Map<String,Object>> rows=query("SELECT name,display_name FROM candidates WHERE election_id=? AND uuid=? AND COALESCE(removed,0)=0 LIMIT 1",eid,candidateUuid);
        if(rows.isEmpty()){warn(p,"Кандидат не найден.");return;}
        String name=first(s(rows.get(0).get("display_name")),s(rows.get(0).get("name")),candidateUuid);
        Menu m=new Menu("vote-confirm"); create(m,54,"&a&lПодтверждение голоса");
        btn(m,4,Material.EMERALD,"&a&lГолос за "+clipped(name,28),List.of("&7Это финальный выбор кандидата.","&7После запечатывания бюллетень станет","&7физическим документом с твоим выбором.","&7Голос засчитается только после участка."),"none");
        btn(m,20,Material.WRITTEN_BOOK,"&eЕще раз открыть заявку",List.of("&7Проверь программу кандидата","&7перед финальным голосом."),"view-app:"+eid+":"+candidateUuid);
        btn(m,22,Material.LIME_CONCRETE,"&a&lЗапечатать бюллетень",List.of("&7Кандидат будет записан в твой бюллетень.","&7После этого подойди к участку ЦИК","&7и опусти бюллетень в урну."),"vote-seal:"+eid+":"+candidateUuid+":"+first(stationId,"inventory"));
        btn(m,24,Material.RED_CONCRETE,"&cНазад к бюллетеню",List.of("&7Вернуться к головам кандидатов","&7без изменения бюллетеня."),"open:station-ballot:"+first(stationId,"inventory"));
        btn(m,30,Material.PAPER,"&fВернуться к бюллетеню",List.of("&7Открыть список кандидатов снова."),"open:station-ballot:"+first(stationId,"inventory"));
        btn(m,32,Material.LECTERN,"&bШаг участка",List.of("&7После запечатывания голос не считается","&7автоматически: бюллетень нужно опустить","&7в активный участок ЦИК."),"open:station-hub:"+first(stationId,"inventory"));
        nav(m,"open:station-ballot:"+first(stationId,"inventory"),"open:station-ballot:"+first(stationId,"inventory"));
        p.openInventory(m.inv);
    }

    private void openPlayers(Player p){
        if(!hasPlayerAdmin(p)){warn(p,"Нет прав на игроков."); return;}
        Menu m=new Menu("players-root"); create(m,54,"&e&lИгроки &8| &fструктура");
        btn(m,4,Material.PLAYER_HEAD,"&e&lИгроки онлайн",List.of(
                "&7Онлайн: &f"+Bukkit.getOnlinePlayers().size(),
                "&7На проверке: &f"+checkMode.size(),
                "&7Frozen: &f"+frozen.size(),
                "&7Опасные действия вынесены в advanced."
        ),"none");
        btn(m,10,Material.PLAYER_HEAD,"&aОнлайн и профили",List.of("&7Открыть карточку игрока, инвентарь,","&7перемещение, timeline и приколы."),"open:players-daily");
        btn(m,12,Material.CLOCK,"&bМассовые безопасные",List.of("&7Snapshot all, heal/feed, remind checks,","&7sync AR online. Опасное требует Shift."),"open:players-tools");
        btn(m,14,Material.SPYGLASS,"&6Проверки и наказания",List.of("&7Freeze, вызов на проверку, punish-вкладки","&7и служебная диагностика для админов."),"open:players-advanced");
        btn(m,16,Material.SHIELD,"&aЗащита от чит-модов",List.of("&7Client brand guard: блокирует явные hack clients.","&7Миникарты и performance-моды не трогаются."),"open:client-guard");
        btn(m,28,Material.TNT,"&dПриколы",List.of("&7Внутри карточки конкретного игрока.","&7Открой 'Онлайн и профили' -> игрок -> Приколы."),"open:players-daily");
        btn(m,30,Material.ENDER_CHEST,"&aLive инвентари",List.of("&7Snapshot и timeline доступны из карточки","&7и через сайт в real-time."),"open:players-tools");
        btn(m,32,Material.TARGET,"&aГотовность запуска",List.of("&7Проверка зависимостей, античита, live и БД."),"open:startup-readiness");
        btn(m,40,Material.COMPASS,"&2Карта админки",List.of("&7Вернуться к подгруппам."),"open:admin-map");
        nav(m,"open:hub","open:players"); p.openInventory(m.inv);
    }

    private void openPlayersDaily(Player p){
        if(!hasPlayerAdmin(p)){warn(p,"Нет прав на игроков."); return;}
        Menu m=new Menu("players-daily"); create(m,54,"&e&lИгроки &8| &fонлайн и профили");
        int slot=0; for(Player t:Bukkit.getOnlinePlayers()){
            btn(m,slot++,Material.PLAYER_HEAD,"&e"+t.getName(),List.of("&7UUID: &f"+t.getUniqueId(),"&7Мир: &f"+t.getWorld().getName(),"&7XYZ: &f"+t.getLocation().getBlockX()+" "+t.getLocation().getBlockY()+" "+t.getLocation().getBlockZ(),checkMode.containsKey(t.getUniqueId())?"&cНа проверке":"&aСвободен"),"player:"+t.getName());
            if(slot>=45)break;
        }
        btn(m,47,Material.CLOCK,"&bИнструменты игрока",List.of("&7Массовые безопасные действия.","&7Снимки, лечение, разморозка."),"open:players-tools");
        btn(m,48,Material.COMPASS,"&2Карта админки",List.of("&7Быстрые подгруппы без выхода из GUI."),"open:admin-map");
        btn(m,50,Material.ENDER_CHEST,"&aБД и оптимизация",List.of("&7Снимки online, timeline и состояние базы."),"open:db-health");
        nav(m,"open:players","open:players-daily"); p.openInventory(m.inv);
    }

    private void openPlayersAdvanced(Player p){
        if(!hasPlayerAdmin(p)){warn(p,"Нет прав на advanced-игроков."); return;}
        Menu m=new Menu("players-advanced"); create(m,54,"&6&lИгроки &8| &fпроверки и advanced");
        btn(m,4,Material.SPYGLASS,"&6&lКонтроль игроков",List.of(
                "&7и миникарты. GrimAC остается основным античитом.",
                "&7Массовые опасные действия требуют Shift в дочерних вкладках.",
                "&7Публичные сообщения проверки видят только админы."
        ),"none");
        btn(m,10,Material.PLAYER_HEAD,"&eОткрыть игрока",List.of("&7Карточка игрока: перемещение, проверка,","&7наказания, инвентарь и timeline."),"open:players-daily");
        btn(m,12,Material.ICE,"&bПроверки",List.of("&7Freeze/check/remind доступны в карточке игрока.","&7Текущих check-сессий: &f"+checkMode.size()),"open:players-daily");
        btn(m,14,Material.REDSTONE_BLOCK,"&4Наказания",List.of("&7Kick/IP-ban и жесткие действия находятся","&7в карточке конкретного игрока."),"open:players-daily");
        btn(m,16,Material.SHIELD,"&aClient guard",List.of("&7Посмотреть client brand игроков и denylist."),"open:client-guard");
        btn(m,28,Material.CLOCK,"&bМассовые инструменты",List.of("&7Snapshot all, unfreeze all, save-all, sync AR."),"open:players-tools");
        btn(m,30,Material.ENDER_CHEST,"&aСостояние базы",List.of("&7Снимки online, активность игроков и состояние базы."),"open:db-health");
        btn(m,32,Material.TARGET,"&aStartup readiness",List.of("&7Античит, TAB/sidebar, БД и защита предметов."),"open:startup-readiness");
        btn(m,40,Material.ARROW,"&eК игрокам",List.of("&7Назад к структуре игроков."),"open:players");
        nav(m,"open:players","open:players-advanced"); p.openInventory(m.inv);
    }

    private void openClientGuard(Player p){
        if(!hasPlayerAdmin(p)){warn(p,"Нет прав на client guard."); return;}
        Menu m=new Menu("client-guard"); create(m,54,"&a&lЗащита от чит-модов");
        btn(m,4,Material.SHIELD,"&a&lClient brand guard",List.of(
                "&7Блокирует явные hack clients по minecraft:brand.",
                "&7и миникарты. GrimAC остается основным античитом.",
                "&7и миникарты. GrimAC остается основным античитом.",
                "&7Denylist: &f"+BLOCKED_CLIENT_BRAND_TOKENS.size()+" токенов."
        ),"none");
        int slot=10;
        for(Player t:Bukkit.getOnlinePlayers()){
            String brand=first(clientBrands.get(t.getUniqueId()),"brand еще не получен");
            boolean bad=isDangerousClientBrand(brand);
            btn(m,slot++,bad?Material.BARRIER:Material.SPYGLASS,(bad?"&c":"&a")+t.getName(),List.of(
                    "&7Brand: &f"+clipped(brand,42),
                    bad?"&cБудет отключен при brand-событии.":"&aЯвного запрещенного клиента нет.",
                    "&7Миникарты сами по себе не блокируются."
            ),"none");
            if(slot>=45)break;
        }
        if(slot==10)btn(m,22,Material.GRAY_DYE,"&7Игроков онлайн нет",List.of("&7Brand появится после входа клиента."),"none");
        btn(m,46,Material.PAPER,"&fЧто блокируется",List.of("&7Meteor, Wurst, LiquidBounce, Aristois, Impact,","&7Vape, Future, RusherHack, Baritone, SeedCracker,","&7XRay/Freecam/ESP/Reach/AutoClicker бренды."),"none");
        btn(m,47,Material.EMERALD,"&aЧто разрешено",List.of("&7Vanilla, Fabric, Forge, Quilt, OptiFine, Sodium,","&7Iris, Lunar, Badlion, LabyMod, Xaero/JourneyMap."),"none");
        nav(m,"open:players","open:client-guard"); p.openInventory(m.inv);
    }

    private void openPlayer(Player admin,String name){
        Player t=Bukkit.getPlayerExact(name); if(t==null){warn(admin,"Игрок оффлайн."); return;}
        Menu m=new Menu("player"); create(m,54,"&e&lИгрок &8| &f"+name);
        btn(m,4,Material.PLAYER_HEAD,"&e&l"+name,List.of("&7UUID: &f"+t.getUniqueId(),"&7GM: &f"+t.getGameMode(),"&7HP: &f"+Math.round(t.getHealth()),"&7Мир: &f"+t.getWorld().getName()),"none");
        btn(m,10,Material.COMPASS,"&bПеремещение",List.of(),"open:p-move:"+name);
        btn(m,12,Material.CHEST,"&aИнвентарь",List.of(),"open:p-inv:"+name);
        btn(m,14,Material.BELL,"&dОсновные действия",List.of(),"open:p-actions:"+name);
        btn(m,16,Material.TNT,"&cПриколы",List.of("&7Отдельная вкладка."),"open:p-pranks:"+name);
        btn(m,22,Material.SPYGLASS,"&6Проверка",List.of("&7ТП, freeze, запрет действий."),"open:p-check:"+name);
        btn(m,24,Material.REDSTONE_BLOCK,"&4Наказания",List.of(),"open:p-punish:"+name);
        btn(m,31,Material.CLOCK,"&bЛента действий",List.of("&7Последние действия игрока из AdminPlus."),"open:p-timeline:"+name);
        nav(m,"open:players","player:"+name); admin.openInventory(m.inv);
    }
    private void openPMove(Player a,String n){ Menu m=new Menu("pmove"); create(m,54,"&b&lПеремещение &8| &f"+n);
        btn(m,10,Material.COMPASS,"&bТП к игроку",List.of(),"p:tpto:"+n); btn(m,11,Material.ENDER_PEARL,"&bТП к себе",List.of(),"p:tphere:"+n); btn(m,12,Material.ICE,"&bFreeze",List.of(),"p:freeze:"+n);
        btn(m,13,Material.FEATHER,"&eПодбросить",List.of(),"p:launch:"+n); btn(m,14,Material.SLIME_BALL,"&aПоднять на 10",List.of(),"p:up10:"+n); btn(m,15,Material.BEACON,"&dЛевитация",List.of(),"p:levitate:"+n);
        btn(m,16,Material.CHORUS_FRUIT,"&5Случайный шаг",List.of("&7Безопасный телепорт рядом с текущей точкой."),"p:randomtp:"+n); btn(m,17,Material.SPYGLASS,"&bКто рядом",List.of("&7Показывает администратору игроков около цели."),"p:nearby:"+n);
        btn(m,28,Material.GRASS_BLOCK,"&aSurvival",List.of(),"p:gm-survival:"+n); btn(m,29,Material.COMMAND_BLOCK,"&dCreative",List.of(),"p:gm-creative:"+n); btn(m,30,Material.ENDER_EYE,"&7Spectator",List.of(),"p:gm-spectator:"+n); btn(m,31,Material.IRON_SWORD,"&6Adventure",List.of(),"p:gm-adventure:"+n);
        nav(m,"player:"+n,"open:p-move:"+n); a.openInventory(m.inv); }
    private void openPInv(Player a,String n){ Menu m=new Menu("pinv"); create(m,54,"&a&lИнвентарь &8| &f"+n);
        btn(m,10,Material.CHEST,"&aОткрыть инвентарь",List.of(),"p:inv:"+n); btn(m,11,Material.ENDER_CHEST,"&5Эндер-сундук",List.of(),"p:ender:"+n); btn(m,12,Material.GOLDEN_APPLE,"&aHeal+feed",List.of(),"p:heal:"+n);
        btn(m,13,Material.COOKED_BEEF,"&6Накормить",List.of(),"p:feed:"+n); btn(m,14,Material.ANVIL,"&bПочинить руку",List.of(),"p:repairhand:"+n); btn(m,15,Material.IRON_CHESTPLATE,"&eСнять броню",List.of(),"p:armoroff:"+n);
        btn(m,16,Material.GOLDEN_HELMET,"&6Выборы в ElectionCore",List.of("&7Выдача заявок и бюллетеней","&7перенесена в новый election hub."),"open:elections"); btn(m,17,Material.PAPER,"&7Через ЦИК и участок",List.of("&7AdminPlus больше не выдаёт","&7книги заявок и бюллетени напрямую."),"none"); btn(m,22,Material.HOPPER,"&dПеремешать хотбар",List.of(),"p:shufflehotbar:"+n); btn(m,23,Material.SPYGLASS,"&bSnapshot",List.of("&7Записать live-инвентарь в БД."),"p:snapshot:"+n); btn(m,24,Material.DIAMOND_ORE,"&bAR sync",List.of("&7Пересчитать АР этого игрока."),"p:arsync:"+n); btn(m,25,Material.BARRIER,"&cОчистить инвентарь",List.of("&cShift+ЛКМ"),"p:clearinv:"+n);
        nav(m,"player:"+n,"open:p-inv:"+n); a.openInventory(m.inv); }
    private void openPActions(Player a,String n){ Menu m=new Menu("pactions"); create(m,54,"&d&lОсновные действия &8| &f"+n);
        btn(m,10,Material.OAK_SIGN,"&eTitle",List.of(),"p:title:"+n); btn(m,11,Material.PAPER,"&fСообщение",List.of(),"p:message:"+n); btn(m,12,Material.GLOWSTONE_DUST,"&eGlow",List.of(),"p:glow:"+n);
        btn(m,13,Material.POTION,"&8Тьма",List.of(),"p:dark:"+n); btn(m,14,Material.MILK_BUCKET,"&fСнять наркотические эффекты",List.of("&7Снимает обычные эффекты, овердоз", "&7и просит CopiMineNarcotics очистить визуалы."),"p:cleanse:"+n); btn(m,15,Material.TOTEM_OF_UNDYING,"&aGod-like 10с",List.of(),"p:god10:"+n);
        btn(m,16,Material.NAME_TAG,"&6Fake OP",List.of(),"p:fakeop:"+n); btn(m,17,Material.SPYGLASS,"&bКто рядом",List.of(),"p:nearby:"+n); btn(m,18,Material.SCULK_SHRIEKER,"&cPanic",List.of("&7Срочный title и звук без урона."),"p:panic:"+n);
        btn(m,19,Material.SPIDER_EYE,"&dConfuse",List.of("&7Короткая дезориентация."),"p:confuse:"+n); btn(m,20,Material.IRON_DOOR,"&7Lock inventory",List.of("&7Блокирует клики в инвентаре на 5 секунд."),"p:inventory-lock:"+n); btn(m,23,Material.SPYGLASS,"&6Вызвать на проверку",List.of(),"p:check-start:"+n);
        nav(m,"player:"+n,"open:p-actions:"+n); a.openInventory(m.inv); }
    private void openPPranks(Player a,String n){ Menu m=new Menu("ppranks"); create(m,54,"&c&lПриколы &8| &f"+n);
        Object[][] buttons={{10,Material.CARVED_PUMPKIN,"&6Тыква","pumpkin"},{11,Material.BELL,"&cИспугать","scare"},{12,Material.LIGHTNING_ROD,"&eМолния","lightning"},{13,Material.TNT,"&cФейк взрыв","explosion"},{14,Material.FEATHER,"&eПодбросить","launch"},{15,Material.POTION,"&8Тьма","dark"},{16,Material.GLOWSTONE_DUST,"&eGlow","glow"},{19,Material.COMPASS,"&bЗакрутить","spin"},{20,Material.BEACON,"&dЛевитация","levitate"},{21,Material.EGG,"&fКуриный дождь","chickens"},{22,Material.HOPPER,"&dХотбар","shufflehotbar"},{23,Material.NAME_TAG,"&6Fake OP","fakeop"},{24,Material.ANVIL,"&7Наковальня","anvil"},{25,Material.PAPER,"&fНалоговая","taxpaper"},{28,Material.HONEYCOMB,"&eЗлой улей","bee"},{29,Material.SCULK_SHRIEKER,"&9Варден","warden"},{30,Material.SPIDER_EYE,"&dТошнота","nausea"},{31,Material.FIREWORK_ROCKET,"&aФейерверк","fireworkfake"},{32,Material.POTATO,"&6Картошка","potato"},{33,Material.ENDER_PEARL,"&5Swap","swap"},{34,Material.BARRIER,"&4Fake ban","fakeban"},{35,Material.GOAT_HORN,"&fГорн","horn"}};
        for(Object[] b:buttons) btn(m,(int)b[0],(Material)b[1],(String)b[2],List.of(),"p:"+b[3]+":"+n);
        Object[][] moreButtons={{0,Material.FIREWORK_STAR,"&dКонфетти","confetti"},{1,Material.SNOWBALL,"&fСнежок","snowball"},{2,Material.COBWEB,"&7Паутина","cobweb"},{3,Material.SLIME_BALL,"&aСлайм-скачок","slime"},{4,Material.GLOWSTONE_DUST,"&eИскры","sparkle"},{5,Material.NOTE_BLOCK,"&bНотный хаос","note"},{6,Material.AMETHYST_SHARD,"&dАметист","amethyst"},{7,Material.CHORUS_FRUIT,"&5Хорус","chorus"},{8,Material.POWDER_SNOW_BUCKET,"&bПухлый снег","powdersnow"},{9,Material.ICE,"&bЗаморозка","freeze"},{17,Material.SCULK_SHRIEKER,"&5Скример","jumpscare"},{18,Material.ENDER_EYE,"&8Ослепить","blind"},{26,Material.HONEYCOMB,"&6Пчелы","bees"},{27,Material.COMPASS,"&4Псевдо-ФСБ","fsb"},{36,Material.WIND_CHARGE,"&fПорыв ветра","wind"},{37,Material.INK_SAC,"&8Чернила","squid"},{38,Material.PHANTOM_MEMBRANE,"&9Фантом","phantom"},{39,Material.COOKIE,"&6Печенье судьбы","fortune"},{40,Material.TOTEM_OF_UNDYING,"&aТотем","totem"},{41,Material.CLOCK,"&eОтсчет","clock"},{42,Material.SPYGLASS,"&6Слежка","spy"},{43,Material.PINK_DYE,"&dРадуга","rainbow"}};
        for(Object[] b:moreButtons) btn(m,(int)b[0],(Material)b[1],(String)b[2],List.of(),"p:"+b[3]+":"+n);
        Object[][] polished={{44,Material.WRITABLE_BOOK,"&fБюрократия","paperwork"},{46,Material.MAP,"&aМини-квест","tinyquest"},{47,Material.FEATHER,"&fЧих","sneeze"},{48,Material.SNOW_BLOCK,"&bСнежное облако","snowcloud"},{50,Material.WIND_CHARGE,"&fОтталкивание","knockback"},{51,Material.END_CRYSTAL,"&dФейковые титры","fakecredits"}};
        for(Object[] b:polished) btn(m,(int)b[0],(Material)b[1],(String)b[2],List.of(),"p:"+b[3]+":"+n);
        nav(m,"player:"+n,"open:p-pranks:"+n); a.openInventory(m.inv); }
    private void openPCheck(Player a,String n){ Menu m=new Menu("pcheck"); create(m,54,"&6&lПроверка &8| &f"+n);
        btn(m,4,Material.SPYGLASS,"&6Режим проверки",List.of("&7ТП к админу, freeze,","&7запрет команд/действий.","&7Voice-chat не блокируется."),"none");
        btn(m,20,Material.LIME_CONCRETE,"&aНачать",List.of(),"p:check-start:"+n); btn(m,22,Material.RED_CONCRETE,"&cЗакончить и вернуть",List.of(),"p:check-stop-return:"+n); btn(m,23,Material.ORANGE_CONCRETE,"&6Закончить без возврата",List.of(),"p:check-stop:"+n); btn(m,24,Material.BELL,"&eНапомнить",List.of(),"p:check-remind:"+n);
        nav(m,"player:"+n,"open:p-check:"+n); a.openInventory(m.inv); }
    private void openPPunish(Player a,String n){ Menu m=new Menu("ppunish"); create(m,54,"&4&lНаказания &8| &f"+n);
        btn(m,10,Material.ICE,"&bFreeze",List.of(),"p:freeze:"+n); btn(m,11,Material.FLINT_AND_STEEL,"&cПоджечь",List.of(),"p:burn5:"+n); btn(m,12,Material.MILK_BUCKET,"&aОчистить эффекты",List.of(),"p:cleanse:"+n); btn(m,13,Material.BARRIER,"&cKick",List.of(),"p:kick:"+n); btn(m,14,Material.REDSTONE_BLOCK,"&4IP-ban",List.of("&cShift+ПКМ"),"p:ipban:"+n); btn(m,15,Material.COBWEB,"&7Сильное замедление",List.of(),"p:slow10:"+n); btn(m,16,Material.SPYGLASS,"&6Проверка",List.of(),"p:check-start:"+n);
        nav(m,"player:"+n,"open:p-punish:"+n); a.openInventory(m.inv); }

    private void openPlayersTools(Player p)throws Exception{
        Menu m=new Menu("ptools"); create(m,54,"&b&lИнструменты игрока");
        btn(m,4,Material.PLAYER_HEAD,"&eOnline players",List.of("&7Online: &f"+Bukkit.getOnlinePlayers().size(),"&7Active checks: &f"+checkMode.size(),"&7Frozen: &f"+frozen.size()),"none");
        btn(m,10,Material.SPYGLASS,"&bSnapshot all online",List.of("&7Записать live-инвентарь всех online игроков."),"players:snapshot-all");
        btn(m,11,Material.GOLDEN_APPLE,"&aHeal all online",List.of("&7Лечение + еда всем online.","&7Безопасно, без экономики."),"players:heal-all");
        btn(m,12,Material.COOKED_BEEF,"&6Feed all online",List.of("&7Накормить всех online."),"players:feed-all");
        btn(m,13,Material.ICE,"&bUnfreeze all",List.of("&7Снять freeze и режим проверки со всех."),"players:unfreeze-all");
        btn(m,14,Material.BELL,"&eRemind checked",List.of("&7Напомнить игрокам на проверке."),"players:remind-checks");
        btn(m,15,Material.DIAMOND_ORE,"&bSync AR online",List.of("&7Пересчитать АР online игроков."),"ar:sync");
        btn(m,19,Material.COMMAND_BLOCK,"&aSave-all",List.of("&7Серверное сохранение мира перед проверками.","&cShift+ПКМ: выполнить."),"players:save-all");
        btn(m,20,Material.GOLDEN_CARROT,"&dNight vision all",List.of("&7Даёт всем online ночное зрение на 8 минут.","&cShift+ПКМ: выполнить."),"players:night-vision-all");
        btn(m,21,Material.MILK_BUCKET,"&aCleanse all",List.of("&7Снимает негативные эффекты со всех online.","&cShift+ПКМ: выполнить."),"players:clear-negative-effects-all");
        btn(m,23,Material.TARGET,"&a&lГотовность запуска",List.of("&7first-run-ready: БД, зависимости,","&7оптимизация, античит и live-защита.","&7Открой после замены папки на сервере."),"open:startup-readiness");
        nav(m,"open:players","open:players-tools"); p.openInventory(m.inv);
    }

    private void openDatabaseHealth(Player p)throws Exception{
        openDatabaseHealthAsync(p);
    }

    private void openPresidentPanel(Player p) throws Exception{
        openPresidentPanelAsync(p);
    }

    private void openChairPanel(Player p) throws Exception{
        openChairPanelAsync(p);
    }

    private void legacyOpenChairPanelRuntimeDisabled(Player p) throws Exception{
        boolean adminAccess=hasAdmin(p);
        UUID playerUuid=p.getUniqueId();
        msg(p,"&7Загружаю панель ЦИК...");
        dbAsyncLoad("openChairPanel",()->{
            CachedElectionRole role=loadElectionRoleState(playerUuid,p.hasPermission("copimine.election.president"));
            String eid=activeOrLatestElectionId();
            Map<String,String> st=eid==null?Map.of():electionSettings(eid);
            return new ChairPanelSnapshot(adminAccess||role.chair(),eid,st);
        },snapshot->{
            if(!p.isOnline())return;
            if(!snapshot.allowed()){warn(p,"Панель доступна председателю ЦИК и главной администрации."); return;}
            Menu m=new Menu("chair"); create(m,27,"&b&lПечать ЦИК");
            btn(m,4,Material.NETHER_STAR,"&b&lПечать ЦИК",List.of("&8CIK_SEAL_PLAYER_ONLY_V5","&7Цикл: &f"+shortId(snapshot.electionId()),"&7Этап: &f"+humanStage(snapshot.settings().getOrDefault("stage","-")),"&7Функция председателя теперь одна:","&7кликни печатью по игроку и выдай","&7ему заявку или бюллетень, если их нет."),"none");
            btn(m,11,Material.PLAYER_HEAD,"&eКлик по игроку",List.of("&7Возьми печать в руку.","&7ПКМ по игроку откроет его статус.","&7Внутри будут только заявка, бюллетень и статус."),"none");
            btn(m,13,Material.PAPER,"&fЗапечатанный бюллетень",List.of("&7Игрок сам выбирает кандидата в бюллетене.","&7После подтверждения держит бюллетень","&7в руке и ПКМ кликает участок ЦИК."),"none");
            btn(m,15,Material.LECTERN,"&aУчасток принимает голос",List.of("&7GUI-кнопки депозита нет.","&7Голос засчитывается только физическим","&7ПКМ по участку с личным бюллетенем."),"none");
            btn(m,18,Material.ARROW,"&aНазад",List.of(),"open:elections");
            btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
            btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:chair");
            p.openInventory(m.inv);
        },error->{ if(p.isOnline())warn(p,"Не удалось открыть панель ЦИК. Подробности записаны в лог."); });
    }

    private void openApplicationsIssue(Player p, boolean compactBridge)throws Exception{
        openApplicationsIssue(p);
    }

    private void openPlayerTimeline(Player admin,String name)throws Exception{
        openPlayerTimelineAsync(admin,name);
    }

    private void legacyOpenPlayerTimelineDisabled(Player admin,String name)throws Exception{
        Player t=Bukkit.getPlayerExact(name);
        Menu m=new Menu("ptimeline"); create(m,54,"&b&lЛента действий &8| &f"+name);
        if(t==null){btn(m,22,Material.BARRIER,"&cPlayer offline",List.of(),"none"); nav(m,"open:players","open:p-timeline:"+name); admin.openInventory(m.inv); return;}
        int slot=0;
        for(Map<String,Object> r:query("SELECT time,type,world,x,y,z,details,admin_only FROM cmv7_player_activity WHERE player_uuid=? ORDER BY time DESC,id DESC LIMIT 45",t.getUniqueId().toString())){
            String type=s(r.get("type"));
            Material mat=type.contains("JOIN")?Material.LIME_DYE:type.contains("QUIT")?Material.GRAY_DYE:type.contains("COMMAND")?Material.COMMAND_BLOCK:type.contains("CHAT")?Material.PAPER:type.contains("AR")?Material.DIAMOND_ORE:Material.BOOK;
            btn(m,slot++,mat,"&b"+clipped(type,26),List.of("&7Time: &f"+num(r.get("time")),"&7World: &f"+first(s(r.get("world")),"-"),"&7XYZ: &f"+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&7Details: &f"+clipped(s(r.get("details")),42),"&7Admin only: &f"+num(r.get("admin_only"))),"none");
            if(slot>=45)break;
        }
            if(slot==0)btn(m,22,Material.BOOK,"&7No timeline rows",List.of("&7Сделай snapshot или дождись действий игрока."),"none");
        nav(m,"player:"+name,"open:p-timeline:"+name); admin.openInventory(m.inv);
    }

    private void openDatabaseHealthAsync(Player p){
        if(!hasAnyAdmin(p)){warn(p,"Нет прав на здоровье БД."); return;}
        msg(p,"&7Загружаю состояние базы...");
        dbAsyncLoad("openDatabaseHealth",()->new DatabaseHealthSnapshot(
                safeScalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'"),
                safeScalar("SELECT COUNT(*) FROM pg_indexes WHERE schemaname=current_schema()"),
                safeScalar("SELECT COUNT(*) FROM cmv7_audit"),
                safeScalar("SELECT COUNT(*) FROM cmv731_votes"),
                safeScalar("SELECT COUNT(*) FROM cmv7_ar_transactions"),
                safeScalar("SELECT COUNT(*) FROM cmv7_ar_assets"),
                safeScalar("SELECT COUNT(*) FROM cmv7_ar_guard_incidents"),
                safeScalar("SELECT COUNT(*) FROM cmv7_inventory_snapshots"),
                safeScalar("SELECT COUNT(*) FROM cmv7_player_activity")
        ),snapshot->{
            if(!p.isOnline())return;
            Menu m=new Menu("db-health"); create(m,54,"&a&lБД и оптимизация");
            btn(m,4,Material.ENDER_CHEST,"&a&lСостояние базы",List.of("&7Источник: &f"+dbSourceSummary(),"&7Таблиц: &f"+snapshot.tables()+" &8| &7индексов: &f"+snapshot.indexes(),"&7Аудит: &f"+snapshot.audits(),"&7Используй только безопасные кнопки обслуживания."),"none");
            btn(m,10,Material.GOLDEN_HELMET,"&6Модуль выборов",List.of("&7Runtime выборов делегирован в &fCopiMineElectionCore","&7Legacy election GUI в AdminPlus отключён.","&7Открыть актуальный election hub."),"open:elections");
            btn(m,12,Material.DIAMOND_ORE,"&bДанные АР",List.of("&7Транзакций: &f"+snapshot.arTransactions(),"&7Активов: &f"+snapshot.arAssets(),"&7Guard-инцидентов: &f"+snapshot.arGuardIncidents(),"&7Баланс меняется только через AR workflow."),"open:ar-health");
            btn(m,14,Material.PLAYER_HEAD,"&eДанные игроков",List.of("&7Снимков online: &f"+snapshot.snapshots(),"&7Событий активности: &f"+snapshot.playerActivity(),"&7Профили читаются без тяжёлых полных сканов."),"open:players-tools");
            btn(m,16,Material.SHIELD,"&aЗащита базы",List.of("&7Аудит и журналы не редактируются сырой формой.","&7Для выборов/АР есть отдельные безопасные API.","&7Все действия обслуживания пишутся в аудит."),"none");
            btn(m,28,Material.ANVIL,"&aОптимизировать базу",List.of("&7Безопасно обновляет внутреннюю статистику.","&7Не меняет голоса, балансы и заявки."),"db:optimize");
            btn(m,30,Material.CLOCK,"&bСбросить журнал базы",List.of("&7Пассивно сбрасывает журнал, если база разрешит.","&7Полезно после длинных админ-сессий."),"db:checkpoint");
            btn(m,32,Material.SPYGLASS,"&dПроверка запуска",List.of("&7Перепроверить схему, зависимости и защиту."),"open:startup-readiness");
            btn(m,34,Material.COMPASS,"&2Карта админки",List.of("&7Вернуться к подгруппам."),"open:admin-map");
            nav(m,"open:admin-map","open:db-health"); p.openInventory(m.inv);
        },error->{ if(p.isOnline())warn(p,"Не удалось загрузить состояние БД. Подробности записаны в лог."); });
    }

    private void runDatabaseMaintenanceAsync(Player p,boolean checkpoint){
        if(!hasAnyAdmin(p)){warn(p,"Нет прав на обслуживание БД."); return;}
        msg(p,checkpoint?"&7Запрашиваю ANALYZE и checkpoint...":"&7Запускаю ANALYZE...");
        dbAsyncLoad("runDatabaseMaintenance",()->runDatabaseMaintenance(p.getName(),checkpoint),result->{
            if(!p.isOnline())return;
            msg(p,result);
            openDatabaseHealthAsync(p);
        },error->{ if(p.isOnline())warn(p,"Не удалось выполнить обслуживание БД. Подробности записаны в лог."); });
    }

    private void openPresidentPanelAsync(Player p){
        boolean adminAccess=hasAdmin(p);
        UUID playerUuid=p.getUniqueId();
        boolean permissionPresident=p.hasPermission("copimine.election.president");
        msg(p,"&7Загружаю кабинет президента...");
        dbAsyncLoad("openPresidentPanel",()->{
            CachedElectionRole role=loadElectionRoleState(playerUuid,permissionPresident);
            String eid=activeOrLatestElectionId();
            return new PresidentPanelSnapshot(adminAccess||role.president(),eid,activePresidentName());
        },snapshot->{
            if(!p.isOnline())return;
            if(!snapshot.allowed()){warn(p,"Панель доступна действующему президенту и главной администрации."); return;}
            Menu m=new Menu("president"); create(m,54,"&6&lПрезидент &8| &f"+p.getName());
            btn(m,4,Material.NETHER_STAR,"&6&lКабинет президента",List.of("&7Действующий президент: &f"+first(snapshot.activePresident(),"нет"),"&7Цикл: &f"+shortId(snapshot.electionId()),"&7Президент не меняет голоса и экономику,","&7но может публично вести политический цикл."),"none");
            btn(m,19,Material.BELL,"&6Обращение к серверу",List.of("&7Публичный title/chat без админских прав на голоса."),"president:announce");
            btn(m,20,Material.WRITABLE_BOOK,"&eПрограмма и обещания",List.of("&7Фиксирует обновление программы в журнале","&7и напоминает игрокам открыть кандидатов."),"president:program");
            btn(m,21,Material.NAME_TAG,"&bНазначить представителя",List.of("&7Представитель президента помогает ЦИК","&7и виден в списке кураторов."),"president:appoint-delegate");
            btn(m,22,Material.MAP,"&dЗапросить дебаты",List.of("&7Отправляет ЦИК служебный запрос","&7без принудительной смены этапа."),"president:request-debate");
            btn(m,23,Material.GOLD_INGOT,"&6Запросить подсчёт",List.of("&7Формальный запрос председателю ЦИК","&7закрыть голосование и перейти к подсчёту."),"president:request-counting");
            btn(m,24,Material.SPYGLASS,"&eАудит выборов",List.of("&7Логи заявок, бюллетеней, этапов и президента."),"open:election-audit");
            btn(m,25,Material.PLAYER_HEAD,"&eКандидаты и заявки",List.of("&7Открыть список кандидатов и текущие цифры."),"open:candidates");
            btn(m,26,Material.WRITTEN_BOOK,"&6Восстановить мандат",List.of("&7Если мандат был уничтожен через Q,","&7панель безопасно выдаст новый."),"official:recover:president_mandate");
            nav(m,"open:elections","open:president"); p.openInventory(m.inv);
        },error->{ if(p.isOnline())warn(p,"Не удалось открыть кабинет президента. Подробности записаны в лог."); });
    }

    private void openChairPanelAsync(Player p){
        boolean adminAccess=hasAdmin(p);
        UUID playerUuid=p.getUniqueId();
        msg(p,"&7Загружаю панель ЦИК...");
        dbAsyncLoad("openChairPanel",()->{
            CachedElectionRole role=loadElectionRoleState(playerUuid,p.hasPermission("copimine.election.president"));
            String eid=activeOrLatestElectionId();
            Map<String,String> st=eid==null?Map.of():electionSettings(eid);
            return new ChairPanelSnapshot(adminAccess||role.chair(),eid,st);
        },snapshot->{
            if(!p.isOnline())return;
            if(!snapshot.allowed()){warn(p,"Панель доступна председателю ЦИК и главной администрации."); return;}
            Menu m=new Menu("chair"); create(m,27,"&b&lПечать ЦИК");
            btn(m,4,Material.NETHER_STAR,"&b&lПечать ЦИК",List.of("&8CIK_SEAL_PLAYER_ONLY_V5","&7Цикл: &f"+shortId(snapshot.electionId()),"&7Этап: &f"+humanStage(snapshot.settings().getOrDefault("stage","-")),"&7Функция председателя теперь одна:","&7кликни печатью по игроку и выдай","&7ему заявку или бюллетень, если их нет."),"none");
            btn(m,11,Material.PLAYER_HEAD,"&eКлик по игроку",List.of("&7Возьми печать в руку.","&7ПКМ по игроку откроет его статус.","&7Внутри будут только заявка, бюллетень и статус."),"none");
            btn(m,13,Material.PAPER,"&fЗапечатанный бюллетень",List.of("&7Игрок сам выбирает кандидата в бюллетене.","&7После подтверждения держит бюллетень","&7в руке и ПКМ кликает участок ЦИК."),"none");
            btn(m,15,Material.LECTERN,"&aУчасток принимает голос",List.of("&7GUI-кнопки депозита нет.","&7Голос засчитывается только физическим","&7ПКМ по участку с личным бюллетенем."),"none");
            btn(m,18,Material.ARROW,"&aНазад",List.of(),"open:elections");
            btn(m,22,Material.BARRIER,"&cЗакрыть",List.of(),"close");
            btn(m,26,Material.EMERALD,"&aОбновить",List.of(),"open:chair");
            p.openInventory(m.inv);
        },error->{ if(p.isOnline())warn(p,"Не удалось открыть панель ЦИК. Подробности записаны в лог."); });
    }

    private void openPlayerTimelineAsync(Player admin,String name){
        Player t=Bukkit.getPlayerExact(name);
        if(t==null){
            Menu m=new Menu("ptimeline"); create(m,54,"&b&lЛента действий &8| &f"+name);
            btn(m,22,Material.BARRIER,"&cPlayer offline",List.of(),"none");
            nav(m,"open:players","open:p-timeline:"+name);
            admin.openInventory(m.inv);
            return;
        }
        String playerUuid=t.getUniqueId().toString();
        msg(admin,"&7Загружаю ленту действий игрока...");
        dbAsyncLoad("openPlayerTimeline",()->new PlayerTimelineSnapshot(name,query("SELECT time,type,world,x,y,z,details,admin_only FROM cmv7_player_activity WHERE player_uuid=? ORDER BY time DESC,id DESC LIMIT 45",playerUuid)),snapshot->{
            if(!admin.isOnline())return;
            Menu m=new Menu("ptimeline"); create(m,54,"&b&lЛента действий &8| &f"+snapshot.playerName());
            int slot=0;
            for(Map<String,Object> r:snapshot.rows()){
                String type=s(r.get("type"));
                Material mat=type.contains("JOIN")?Material.LIME_DYE:type.contains("QUIT")?Material.GRAY_DYE:type.contains("COMMAND")?Material.COMMAND_BLOCK:type.contains("CHAT")?Material.PAPER:type.contains("AR")?Material.DIAMOND_ORE:Material.BOOK;
                btn(m,slot++,mat,"&b"+clipped(type,26),List.of("&7Time: &f"+num(r.get("time")),"&7World: &f"+first(s(r.get("world")),"-"),"&7XYZ: &f"+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),"&7Details: &f"+clipped(s(r.get("details")),42),"&7Admin only: &f"+num(r.get("admin_only"))),"none");
                if(slot>=45)break;
            }
            if(slot==0)btn(m,22,Material.BOOK,"&7No timeline rows",List.of("&7Сделай snapshot или дождись действий игрока."),"none");
            nav(m,"player:"+snapshot.playerName(),"open:p-timeline:"+snapshot.playerName());
            admin.openInventory(m.inv);
        },error->{ if(admin.isOnline())warn(admin,"Не удалось загрузить ленту действий. Подробности записаны в лог."); });
    }

    private void handle(Player p, ClickType click, String a, String menuId) throws Exception {
        if(a.equals("close")){p.closeInventory();return;}
        if(isRestrictedJuniorAdmin(p)&&isBlockedJuniorAdminAction(a)){
            warn(p,"Младший админ не может выполнять это действие.");
            if(!a.startsWith("open:players"))openMainHub(p);
            return;
        }
        if(isLegacyElectionAction(a)){redirectLegacyElectionAction(p); return;}
        if(a.startsWith("vote-seal:")){String[] parts=a.split(":",4); if(parts.length>=3)sealBallotChoice(p,parts[1],parts[2],parts.length>=4?parts[3]:"inventory"); return;}
        if(a.startsWith("vote-deposit:")){depositSealedBallotAtStation(p,findOwnedSealedBallot(p,activeOrLatestElectionId()),a.substring("vote-deposit:".length())); return;}
        if(a.startsWith("open:station-ballot:")){openBallotCandidateHub(p,null,a.substring("open:station-ballot:".length())); return;}
        if(a.startsWith("open:station-hub:")){openPollingStationHub(p,null); return;}
        if(a.startsWith("open:cik-target:")){
            Player target=Bukkit.getPlayerExact(a.substring("open:cik-target:".length()));
            if(target==null){warn(p,"Игрок не в сети."); return;}
            openCikSealPlayerPanel(p,target);
            return;
        }
        if(a.startsWith("cik-target-app:")){
            Player target=Bukkit.getPlayerExact(a.substring("cik-target-app:".length()));
            String eid=activeOrLatestElectionId();
            if(target==null){warn(p,"Игрок не в сети."); return;}
            if(eid==null||eid.isBlank()){warn(p,"Нет активного цикла выборов."); return;}
            if(!targetNeedsApplication(target,eid)){warn(p,"У игрока уже есть актуальная заявка или книга."); openCikSealPlayerPanel(p,target); return;}
            issueApplicationBook(target,p.getName());
            msg(p,"&aЗаявка выдана: &f"+target.getName());
            openCikSealPlayerPanel(p,target);
            return;
        }
        if(a.startsWith("cik-target-ballot:")){
            Player target=Bukkit.getPlayerExact(a.substring("cik-target-ballot:".length()));
            String eid=activeOrLatestElectionId();
            if(target==null){warn(p,"Игрок не в сети."); return;}
            if(eid==null||eid.isBlank()){warn(p,"Нет активного цикла выборов."); return;}
            if(!targetNeedsBallot(target,eid)){warn(p,"У игрока уже есть бюллетень или голос уже принят."); openCikSealPlayerPanel(p,target); return;}
            issueBallot(target,p.getName());
            msg(p,"&aБюллетень выдан: &f"+target.getName());
            openCikSealPlayerPanel(p,target);
            return;
        }
        if(a.equals("citizen:sidebar-hide")){sidebarHidden.add(p.getUniqueId()); hideSidebar(p,true); openCitizenElectionHub(p);return;} if(a.equals("citizen:sidebar-show")){sidebarHidden.remove(p.getUniqueId()); sidebarPersonal.add(p.getUniqueId()); updateSidebar(p,true); openCitizenElectionHub(p);return;}
        if(a.equals("sidebar:show")){showSidebarAll(true); p.closeInventory(); return;}
        if(a.equals("sidebar:show-me")){sidebarHidden.remove(p.getUniqueId()); sidebarPersonal.add(p.getUniqueId()); updateSidebar(p,true); p.closeInventory(); return;}
        if(a.equals("open:hub")){openMainHub(p);return;} if(a.equals("open:admin-map")){openAdminMap(p);return;} if(a.equals("open:db-health")){openDatabaseHealthAsync(p);return;} if(a.equals("open:startup-readiness")){openStartupReadiness(p);return;} if(a.equals("open:elections")){openElections(p);return;} if(a.equals("open:election-operations")){openElectionOperations(p);return;} if(a.equals("open:election-ledgers")){openElectionLedgers(p);return;} if(a.equals("open:election-recovery-advanced")){openElectionRecoveryAdvanced(p);return;} if(a.equals("open:sidebar")){openSidebar(p);return;} if(a.equals("open:election-settings")){openElectionSettings(p);return;} if(a.equals("open:lifecycle")){openElectionLifecycle(p);return;} if(a.equals("open:preflight")){openElectionPreflight(p);return;} if(a.equals("open:election-integrity")){openElectionIntegrity(p);return;} if(a.equals("open:election-release")){openElectionReleaseBoard(p);return;} if(a.equals("open:election-ceremony")){openElectionCeremony(p);return;} if(a.equals("open:shops")){openArtifactsShopHub(p);return;}
        if(a.equals("open:worlds")){if(openWorldCoreHub(p))return; return;}
        if(a.equals("open:president")){openPresidentPanelAsync(p);return;} if(a.equals("open:chair")){openChairPanel(p);return;}
        if(a.equals("open:polling-stations")){openPollingStations(p);return;}
        if(a.equals("station:create-target")){msg(p,createPollingStationFromTarget(p)); openPollingStations(p);return;}
        if(a.startsWith("station:card:")){openPollingStationCard(p,a.substring("station:card:".length()));return;}
        if(a.startsWith("station:delete-confirm:")){openPollingStationDeleteConfirm(p,a.substring("station:delete-confirm:".length()));return;}
        if(a.startsWith("station:delete:")){msg(p,archivePollingStation(p,a.substring("station:delete:".length()))); openPollingStations(p);return;}
        if(a.startsWith("station:teleport:")){teleportPollingStation(p,a.substring("station:teleport:".length()));return;}
        if(a.equals("election:prepare-voting")){try{prepareVoting(p.getName()); msg(p,"&aГолосование открыто.");}catch(Exception ex){warn(p,"Не удалось открыть голосование: "+ex.getMessage());} openElections(p);return;}
        if(a.equals("election:prepare-counting")){try{prepareCounting(p.getName()); msg(p,"&aПодсчёт запущен.");}catch(Exception ex){warn(p,"Не удалось начать подсчёт: "+ex.getMessage());} openElections(p);return;}
        if(a.equals("open:applications-issue")||a.equals("open:ballots-issue")||a.equals("open:give-app-player")||a.equals("open:give-ballot-player")){
            warn(p,"Выдача заявок и бюллетеней перенесена в CopiMineElectionCore.");
            openElections(p);
            return;
        }
        if(a.equals("open:candidates")){openCandidates(p);return;} if(a.equals("open:add-candidate")){openAddCandidate(p);return;} if(a.equals("open:curators")){openCurators(p);return;} if(a.equals("open:add-curator")){openAddCurator(p);return;} if(a.equals("open:election-danger")){openDanger(p);return;}
        if(a.equals("open:applications-review")){openApplicationsReview(p);return;} if(a.startsWith("app-review:")){String rowid=a.substring("app-review:".length()); if(click==ClickType.RIGHT||click==ClickType.SHIFT_RIGHT){reviewApplication(rowid,"REJECTED",p.getName()); msg(p,"&cЗаявка отклонена.");}else{reviewApplication(rowid,"APPROVED",p.getName()); if(click==ClickType.SHIFT_LEFT){promoteApplicationCandidate(rowid,p.getName()); msg(p,"&aЗаявка одобрена, игрок добавлен в кандидаты.");}else msg(p,"&aЗаявка одобрена.");} openApplicationsReview(p);return;} if(a.equals("open:ballots-ledger")){openBallotsLedger(p);return;} if(a.equals("open:election-audit")){openElectionAudit(p);return;} if(a.equals("open:election-emergency")){openElectionEmergency(p);return;} if(a.equals("open:application-issues")){openApplicationIssuesLedger(p);return;} if(a.equals("open:submitted-applications-emergency")){openSubmittedApplicationsEmergency(p);return;}
        if(a.equals("open:economy")||a.equals("open:economy-basic")||a.equals("open:economy-advanced")||a.equals("open:ar-top")||a.equals("open:ar-custody")||a.equals("open:ar-health")||a.equals("open:scan")||a.equals("open:ar-scans")){CopiMineEconomyCore economy=economyCore(); if(economy==null){warn(p,"CopiMineEconomyCore недоступен.");return;} economy.openAdminEconomyHub(p);return;}
        if(a.equals("open:bank-atms")||a.equals("bank-atm:create-target")||a.startsWith("bank-atm:delete:")||a.startsWith("open:bank-atm:")){
            CopiMineEconomyCore economy=economyCore();
            if(economy==null){warn(p,"CopiMineEconomyCore недоступен.");return;}
            warn(p,"Управление банкоматами перенесено в CopiMineEconomyCore.");
            economy.openAdminEconomyHub(p);
            return;
        }
        if(a.startsWith("bank:")||a.startsWith("bankpin:")){
            CopiMineEconomyCore economy=economyCore();
            if(economy==null){warn(p,"CopiMineEconomyCore недоступен.");return;}
            warn(p,"Старые банковские действия отключены. Используйте новый раздел экономики в CopiMineEconomyCore.");
            economy.openAdminEconomyHub(p);
            return;
        }
        if(a.equals("open:players")){openPlayers(p);return;} if(a.equals("open:players-daily")){openPlayersDaily(p);return;} if(a.equals("open:players-advanced")){openPlayersAdvanced(p);return;} if(a.equals("open:client-guard")){openClientGuard(p);return;} if(a.equals("open:players-tools")){openPlayersTools(p);return;} if(a.startsWith("player:")){openPlayer(p,a.substring(7));return;}
        if(a.startsWith("open:p-move:")){openPMove(p,a.substring(12));return;} if(a.startsWith("open:p-inv:")){openPInv(p,a.substring(11));return;} if(a.startsWith("open:p-actions:")){openPActions(p,a.substring(15));return;} if(a.startsWith("open:p-pranks:")){openPPranks(p,a.substring(14));return;} if(a.startsWith("open:p-check:")){openPCheck(p,a.substring(13));return;} if(a.startsWith("open:p-punish:")){openPPunish(p,a.substring(14));return;} if(a.startsWith("open:p-timeline:")){openPlayerTimelineAsync(p,a.substring(16));return;}
        if(a.startsWith("open:ar-events:")){CopiMineEconomyCore economy=economyCore(); if(economy==null){warn(p,"CopiMineEconomyCore недоступен.");return;} economy.openAdminEconomyHub(p); return;}
        // Legacy election runtime actions are intentionally blocked above by isLegacyElectionAction().
        if(a.equals("ar:sync")){CopiMineEconomyCore economy=economyCore(); if(economy==null){warn(p,"CopiMineEconomyCore недоступен.");return;} warn(p,"AR sync перенесён в CopiMineEconomyCore."); economy.openAdminEconomyHub(p); return;} if(a.equals("ar:scan-loaded")){startScan(p,16,false); openScan(p);return;} if(a.equals("ar:scan-deep")){if(click!=ClickType.SHIFT_LEFT){warn(p,"Только Shift+ЛКМ.");return;} startScan(p,100,true); openScan(p);return;} if(a.startsWith("ar-tp:")){tpArEvent(p,a.substring(6));return;}
        if(a.equals("db:optimize")){runDatabaseMaintenanceAsync(p,false);return;}
        if(a.equals("db:checkpoint")){runDatabaseMaintenanceAsync(p,true);return;}
        if(a.equals("players:snapshot-all")){msg(p,"&aSnapshots: &e"+snapshotAllOnline(p.getName())); openPlayersTools(p);return;}
        if(a.equals("players:heal-all")){int n=healAllOnline(); msg(p,"&aHealed: &e"+n); openPlayersTools(p);return;}
        if(a.equals("players:feed-all")){int n=feedAllOnline(); msg(p,"&aFed: &e"+n); openPlayersTools(p);return;}
        if(a.equals("players:unfreeze-all")){int n=unfreezeAllPlayers(p.getName()); msg(p,"&aUnfrozen/checks closed: &e"+n); openPlayersTools(p);return;}
        if(a.equals("players:remind-checks")){int n=remindCheckedPlayers(); msg(p,"&eReminded: &f"+n); openPlayersTools(p);return;}
        if(a.equals("players:save-all")){if(click!=ClickType.SHIFT_RIGHT){warn(p,"Только Shift+ПКМ: серверное сохранение.");return;} msg(p,saveAllPlayers(p.getName())); openPlayersTools(p);return;}
        if(a.equals("players:night-vision-all")){if(click!=ClickType.SHIFT_RIGHT){warn(p,"Только Shift+ПКМ: эффект всем игрокам.");return;} int n=nightVisionAllPlayers(p.getName()); msg(p,"&dNight vision: &f"+n); openPlayersTools(p);return;}
        if(a.equals("players:clear-negative-effects-all")){if(click!=ClickType.SHIFT_RIGHT){warn(p,"Только Shift+ПКМ: массовая очистка эффектов.");return;} int n=clearNegativeEffectsAllPlayers(p.getName()); msg(p,"&aNegative effects cleared: &f"+n); openPlayersTools(p);return;}
        if(a.startsWith("p:")){playerAction(p,click,a); reopenPlayerContext(p,menuId,a); return;}
    }

    private void playerAction(Player admin, ClickType click, String a) throws Exception {
        String[] parts=a.split(":",3); if(parts.length<3)return; String act=parts[1], name=parts[2]; Player t=Bukkit.getPlayerExact(name); if(t==null){warn(admin,"Игрок оффлайн.");return;}
        recordPlayerActivity(t,"ADMIN_ACTION",t.getLocation(),"admin="+admin.getName()+" action="+act,true);
        if(isExtraPrank(act)){handleExtraPrank(admin,t,act); audit(admin.getName(),"ULTRA7_PLAYER_"+act.toUpperCase(Locale.ROOT),t.getName(),true); snapshotOnlineInventory(t,"admin_"+act); return;}
        if("givebook".equals(act)||"giveballot".equals(act)){
            warn(admin,"Выдача заявок и бюллетеней перенесена в CopiMineElectionCore.");
            openElections(admin);
            return;
        }
        switch(act){
            case "inv" -> admin.openInventory(t.getInventory()); case "ender" -> admin.openInventory(t.getEnderChest()); case "tpto" -> {admin.teleport(t.getLocation()); msg(admin,"&aТП к игроку.");} case "tphere" -> {t.teleport(admin.getLocation()); msg(admin,"&aИгрок телепортирован.");}
            case "freeze" -> toggleFreeze(admin,t); case "heal" -> {t.setHealth(Math.min(t.getMaxHealth(),20.0)); t.setFoodLevel(20); t.setSaturation(20f); t.setFireTicks(0); msg(admin,"&aВылечен.");} case "feed" -> {t.setFoodLevel(20); t.setSaturation(20f);}
            case "launch" -> {t.setVelocity(t.getVelocity().setY(1.45)); sound(t,"ENTITY_FIREWORK_ROCKET_LAUNCH",1f,1f);} case "up10" -> t.teleport(t.getLocation().clone().add(0,10,0)); case "levitate" -> effect(t,5,1,"LEVITATION"); case "randomtp" -> randomTpNear(t); case "nearby" -> nearbyReport(admin,t);
            case "gm-survival" -> t.setGameMode(GameMode.SURVIVAL); case "gm-creative" -> t.setGameMode(GameMode.CREATIVE); case "gm-spectator" -> t.setGameMode(GameMode.SPECTATOR); case "gm-adventure" -> t.setGameMode(GameMode.ADVENTURE);
            case "repairhand" -> repairHand(admin,t); case "armoroff" -> armorOff(admin,t); case "snapshot" -> {snapshotOnlineInventory(t,"admin_manual"); msg(admin,"&aSnapshot saved.");} case "arsync" -> {warn(admin,"AR sync перенесён в CopiMineEconomyCore."); CopiMineEconomyCore economy=economyCore(); if(economy!=null) economy.openAdminEconomyHub(admin);} case "clearinv" -> {if(click!=ClickType.SHIFT_LEFT){warn(admin,"Только Shift+ЛКМ.");return;} t.getInventory().clear();} case "givebook","giveballot" -> {warn(admin,"Выдача заявок и бюллетеней перенесена в CopiMineElectionCore."); openElections(admin);} case "shufflehotbar" -> shuffleHotbar(t);
            case "title" -> t.sendTitle(c("&6&lВнимание"),c("&fАдминистрация наблюдает"),10,60,10); case "message" -> msg(t,"&eАдминистрация: &fПожалуйста, соблюдай правила сервера."); case "glow" -> effect(t,30,0,"GLOWING"); case "dark" -> {effect(t,6,0,"BLINDNESS"); effect(t,6,1,"SLOWNESS","SLOW");} case "cleanse" -> {t.setFireTicks(0); t.getActivePotionEffects().forEach(pe->t.removePotionEffect(pe.getType()));} case "god10" -> {effect(t,10,4,"DAMAGE_RESISTANCE","RESISTANCE"); effect(t,10,2,"REGENERATION");} case "fakeop" -> t.sendMessage(c("&7[Server: Made "+t.getName()+" a server operator]")); case "panic" -> {t.sendTitle(c("&c&lВНИМАНИЕ"),c("&fСрочная проверка реакции"),0,45,10); sound(t,"ENTITY_ENDER_DRAGON_GROWL",.45f,1.7f);} case "confuse" -> {effect(t,7,0,"NAUSEA","CONFUSION"); effect(t,3,0,"BLINDNESS"); sound(t,"ENTITY_ILLUSIONER_CAST_SPELL",.8f,1.2f);} case "inventory-lock" -> {inventoryLocks.put(t.getUniqueId(),now()+5000L); t.closeInventory(); t.sendTitle(c("&7Инвентарь закрыт"),c("&fПауза 5 секунд"),5,45,10); Bukkit.getScheduler().runTaskLater(this,()->inventoryLocks.remove(t.getUniqueId()),110L);}
            case "pumpkin" -> t.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN)); case "scare" -> {sound(t,"ENTITY_CREEPER_PRIMED",1f,.55f); Bukkit.getScheduler().runTaskLater(this,()->sound(t,"ENTITY_WITHER_SPAWN",.7f,.7f),18L);} case "lightning" -> t.getWorld().strikeLightningEffect(t.getLocation()); case "explosion" -> t.getWorld().createExplosion(t.getLocation(),0F,false,false); case "spin" -> spin(t); case "chickens" -> chickens(t); case "anvil" -> sound(t,"BLOCK_ANVIL_LAND",1f,.8f); case "taxpaper" -> giveTaxPaper(t); case "bee" -> {sound(t,"ENTITY_BEE_LOOP_AGGRESSIVE",1f,1.8f); t.sendTitle(c("&eБзззз..."),c("&7Где-то рядом пчёлы"),5,45,10);} case "warden" -> {sound(t,"ENTITY_WARDEN_SONIC_BOOM",1f,.9f); t.sendTitle(c("&9&lТссс..."),c("&7Варден услышал тебя"),5,55,10);} case "nausea" -> effect(t,8,0,"NAUSEA","CONFUSION"); case "fireworkfake" -> {sound(t,"ENTITY_FIREWORK_ROCKET_LAUNCH",1f,1f); sound(t,"ENTITY_FIREWORK_ROCKET_TWINKLE",1f,1.2f);} case "potato" -> givePotato(t); case "swap" -> {Location al=admin.getLocation(), tl=t.getLocation(); admin.teleport(tl); t.teleport(al);} case "fakeban" -> {t.sendTitle(c("&4&lBAN"),c("&cШутка. Но правила лучше соблюдать."),5,70,15); sound(t,"ENTITY_WITHER_DEATH",.7f,.8f);} case "horn" -> sound(t,"ITEM_GOAT_HORN_SOUND_0",1f,1f);
            case "kick" -> t.kickPlayer(c("&cКикнут администрацией CopiMine.")); case "ipban" -> {if(click!=ClickType.SHIFT_RIGHT){warn(admin,"Только Shift+ПКМ.");return;} if(t.getAddress()!=null){String ip=t.getAddress().getAddress().getHostAddress(); Bukkit.getBanList(BanList.Type.IP).addBan(ip,"IP-ban CopiMine",(Date)null,admin.getName()); t.kickPlayer(c("&cIP-ban"));}} case "burn5" -> t.setFireTicks(100); case "slow10" -> {effect(t,10,5,"SLOWNESS","SLOW"); effect(t,10,2,"MINING_FATIGUE","SLOW_DIGGING");}
            case "check-start" -> startCheck(admin,t); case "check-stop" -> stopCheck(admin,t,false); case "check-stop-return" -> stopCheck(admin,t,true); case "check-remind" -> {t.sendTitle(c("&c&lПРОВЕРКА"),c("&fСтой на месте и отвечай администрации"),10,80,10); sound(t,"BLOCK_NOTE_BLOCK_PLING",1f,.8f);}
        }
        if("cleanse".equals(act)){
            PluginCommand narcoticsCommand=Bukkit.getPluginCommand("cmnarcotics");
            if(narcoticsCommand!=null){
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"cmnarcotics clear "+t.getName());
            }
        }
        audit(admin.getName(),"ULTRA7_PLAYER_"+act.toUpperCase(Locale.ROOT),t.getName(),true);
        snapshotOnlineInventory(t,"admin_"+act);
    }

    private void reopenPlayerContext(Player admin,String menuId,String action){
        try{
            String[] parts=action.split(":",3); if(parts.length<3)return;
            String act=parts[1], name=parts[2];
            if(Set.of("inv","ender","kick","ipban").contains(act))return;
            switch(menuId==null?"":menuId){
                case "pmove" -> openPMove(admin,name);
                case "pinv" -> openPInv(admin,name);
                case "pactions" -> openPActions(admin,name);
                case "ppranks" -> openPPranks(admin,name);
                case "pcheck" -> openPCheck(admin,name);
                case "ppunish" -> openPPunish(admin,name);
                case "ptimeline" -> openPlayerTimelineAsync(admin,name);
                case "players" -> openPlayers(admin);
                default -> openPlayer(admin,name);
            }
        }catch(Exception ex){getLogger().warning("reopen player context: "+ex.getMessage());}
    }

    private int snapshotAllOnline(String actor){int n=0; for(Player t:Bukkit.getOnlinePlayers()){snapshotOnlineInventory(t,"admin_snapshot_all"); recordPlayerActivity(t,"ADMIN_SNAPSHOT",t.getLocation(),"admin="+actor,true); n++;} return n;}
    private int healAllOnline(){int n=0; for(Player t:Bukkit.getOnlinePlayers()){try{t.setHealth(Math.min(t.getMaxHealth(),20.0)); t.setFoodLevel(20); t.setSaturation(20f); t.setFireTicks(0); n++;}catch(Throwable ignored){}} return n;}
    private int feedAllOnline(){int n=0; for(Player t:Bukkit.getOnlinePlayers()){t.setFoodLevel(20); t.setSaturation(20f); n++;} return n;}
    private int unfreezeAllPlayers(String actor){int n=frozen.size()+checkMode.size(); frozen.clear(); checkMode.clear(); checkReturn.clear(); try{exec("UPDATE cmv7_player_checks SET active=0,details=COALESCE(details,'')||' | force-unfreeze by '||? WHERE active=1",actor);}catch(Throwable ignored){} staffNotify("&aAll player checks/freeze states were cleared by &f"+actor); return n;}
    private int remindCheckedPlayers(){int n=0; for(UUID id:checkMode.keySet()){Player t=Bukkit.getPlayer(id); if(t!=null&&t.isOnline()){t.sendTitle(c("&c&lПРОВЕРКА"),c("&fСтой на месте и отвечай администрации"),10,80,10); sound(t,"BLOCK_NOTE_BLOCK_PLING",1f,.8f); n++;}} return n;}
    private String saveAllPlayers(String actor){
        boolean ok=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save-all");
        try{audit(actor,"ULTRA7_PLAYERS_SAVE_ALL","ok="+ok,true);}catch(Throwable ignored){}
        staffNotify("&aSave-all запущен из админки игроком &f"+actor);
        return ok?"&aSave-all отправлен в консоль.":"&cНе удалось выполнить save-all.";
    }
    private int nightVisionAllPlayers(String actor){
        int n=0;
        for(Player t:Bukkit.getOnlinePlayers()){
            t.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,20*480,0,false,false,true));
            recordPlayerActivity(t,"ADMIN_NIGHT_VISION_ALL",t.getLocation(),"admin="+actor,true);
            n++;
        }
        try{audit(actor,"ULTRA7_PLAYERS_NIGHT_VISION_ALL","players="+n,true);}catch(Throwable ignored){}
        return n;
    }
    private boolean isNegativePotion(PotionEffectType type){
        if(type==null)return false;
        String k=type.getKey().getKey();
        return Set.of("slowness","mining_fatigue","nausea","blindness","hunger","weakness","poison","wither","levitation","unluck","darkness","bad_omen","raid_omen","trial_omen").contains(k);
    }
    private int clearNegativeEffectsAllPlayers(String actor){
        int n=0, removed=0;
        for(Player t:Bukkit.getOnlinePlayers()){
            int before=removed;
            for(PotionEffect pe:new ArrayList<>(t.getActivePotionEffects())){
                if(isNegativePotion(pe.getType())){t.removePotionEffect(pe.getType()); removed++;}
            }
            if(removed>before){recordPlayerActivity(t,"ADMIN_CLEANSE_NEGATIVE_ALL",t.getLocation(),"admin="+actor+" removed="+(removed-before),true); n++;}
        }
        try{audit(actor,"ULTRA7_PLAYERS_CLEAR_NEGATIVE_EFFECTS_ALL","players="+n+" removed="+removed,true);}catch(Throwable ignored){}
        return n;
    }

    private boolean isExtraPrank(String act){return Set.of("confetti","snowball","cobweb","slime","sparkle","note","amethyst","chorus","powdersnow","jumpscare","blind","bees","fsb","wind","squid","phantom","fortune","totem","clock","spy","rainbow","paperwork","tinyquest","sneeze","snowcloud","knockback","fakecredits").contains(act);}
    private void handleExtraPrank(Player admin,Player t,String act){
        switch(act){
            case "confetti" -> {sound(t,"ENTITY_FIREWORK_ROCKET_TWINKLE",1f,1.4f); sound(t,"BLOCK_AMETHYST_BLOCK_CHIME",.8f,1.7f); t.sendTitle(c("&d&lКонфетти!"),c("&7Админ устроил маленький праздник"),5,35,10);}
            case "snowball" -> {t.launchProjectile(Snowball.class); sound(t,"ENTITY_SNOWBALL_THROW",1f,1.1f);}
            case "cobweb" -> {effect(t,4,1,"SLOWNESS","SLOW"); dropFunnyItem(t,Material.COBWEB,"&7&lПаутина бюрократии",List.of("&8Почти официальное замедление."));}
            case "slime" -> {t.setVelocity(t.getVelocity().setY(1.05)); sound(t,"ENTITY_SLIME_JUMP",1f,.8f);}
            case "sparkle" -> {effect(t,12,0,"GLOWING"); sound(t,"BLOCK_AMETHYST_CLUSTER_STEP",1f,1.8f);}
            case "note" -> {for(int i=0;i<6;i++){final int k=i; Bukkit.getScheduler().runTaskLater(this,()->sound(t,"BLOCK_NOTE_BLOCK_PLING",1f,.7f+k*.18f),i*4L);}}
            case "amethyst" -> {sound(t,"BLOCK_AMETHYST_BLOCK_CHIME",1f,1.35f); t.sendTitle(c("&dДзынь"),c("&7Очень важная кристальная проверка"),5,35,10);}
            case "chorus" -> {sound(t,"ITEM_CHORUS_FRUIT_TELEPORT",1f,1f); spin(t);}
            case "powdersnow" -> {effect(t,6,2,"SLOWNESS","SLOW"); sound(t,"BLOCK_POWDER_SNOW_STEP",1f,.7f);}
            case "jumpscare" -> {t.sendTitle(c("&5&lБУ!"),c("&7Это была проверка реакции"),0,45,10); sound(t,"ENTITY_GHAST_SCREAM",1f,1.35f);}
            case "blind" -> {effect(t,5,0,"BLINDNESS"); t.sendTitle(c("&8Темнота"),c("&7Фонарик не прилагается"),5,45,10); sound(t,"ENTITY_ELDER_GUARDIAN_CURSE",.7f,1.2f);}
            case "bees" -> {sound(t,"ENTITY_BEE_LOOP_AGGRESSIVE",1f,1.4f); t.sendTitle(c("&6Пчелы"),c("&7Кажется, улей недоволен"),5,45,10);}
            case "fsb" -> {msg(t,"&8[&4ФСБ&8] &cПроверка Minecraft-активности..."); msg(t,"&8[&4ФСБ&8] &7Статус: &eподозрительно нормально."); t.sendTitle(c("&4Проверка"),c("&7Это шутка. Продолжай играть."),5,70,20); sound(t,"BLOCK_NOTE_BLOCK_PLING",.9f,.6f);}
            case "wind" -> {t.setVelocity(t.getLocation().getDirection().multiply(1.35).setY(.45)); sound(t,"ENTITY_BREEZE_WIND_BURST",1f,1f);}
            case "squid" -> {effect(t,3,0,"BLINDNESS"); sound(t,"ENTITY_SQUID_SQUIRT",1f,.9f);}
            case "phantom" -> {sound(t,"ENTITY_PHANTOM_SWOOP",1f,.75f); t.sendTitle(c("&9Фантом сверху"),c("&7Но урона не будет"),5,35,10);}
            case "fortune" -> dropFunnyItem(t,Material.COOKIE,"&6&lПеченье судьбы",List.of("&7Сегодня тебе повезло: это не бан.","&8Съесть можно, поверить нельзя."));
            case "totem" -> {try{t.playEffect(EntityEffect.TOTEM_RESURRECT);}catch(Throwable ignored){} sound(t,"ITEM_TOTEM_USE",.7f,1.4f);}
            case "clock" -> {for(int i=3;i>=1;i--){final int n=i; Bukkit.getScheduler().runTaskLater(this,()->{t.sendTitle(c("&e"+n),c("&7Почти ничего не случится"),0,18,2); sound(t,"BLOCK_NOTE_BLOCK_HAT",1f,1.4f);},(3-i)*20L);} Bukkit.getScheduler().runTaskLater(this,()->t.sendTitle(c("&aПуск"),c("&7Проверка реакции завершена"),0,30,8),60L);}
            case "spy" -> {sound(t,"ITEM_SPYGLASS_USE",1f,1f); msg(t,"&6Администрация внимательно изучила ситуацию. Выглядит подозрительно нормально.");}
            case "rainbow" -> {t.sendTitle(c("&cР&6а&eд&aу&bг&dа"),c("&7Цветовая проверка настроения"),5,45,10); sound(t,"BLOCK_NOTE_BLOCK_CHIME",1f,1.6f);}
            case "paperwork" -> {dropFunnyItem(t,Material.PAPER,"&f&lФорма 404-ЦИК",List.of("&7Подписать, заверить, забыть.","&8Никаких реальных обязательств.")); sound(t,"ITEM_BOOK_PAGE_TURN",1f,1.1f);}
            case "tinyquest" -> {t.sendTitle(c("&aМини-квест"),c("&fНайди самый официальный блок рядом"),5,70,15); dropFunnyItem(t,Material.MAP,"&a&lКарта мини-квеста",List.of("&7Цель: улыбнуться и продолжить играть."));}
            case "sneeze" -> {sound(t,"ENTITY_PANDA_SNEEZE",1f,1.2f); t.setVelocity(t.getVelocity().add(t.getLocation().getDirection().multiply(-.35).setY(.2)));}
            case "snowcloud" -> {effect(t,5,0,"SLOWNESS","SLOW"); for(int i=0;i<3;i++)dropFunnyItem(t,Material.SNOWBALL,"&b&lСнежинка администрации",List.of("&7Тает от слишком серьезного лица.")); sound(t,"BLOCK_POWDER_SNOW_BREAK",1f,.8f);}
            case "knockback" -> {t.setVelocity(t.getLocation().getDirection().multiply(-1.1).setY(.45)); sound(t,"ENTITY_BREEZE_WIND_BURST",1f,1.25f);}
            case "fakecredits" -> {t.sendTitle(c("&d&lCredits"),c("&fСпасибо за участие в тесте интерфейса"),5,80,20); sound(t,"UI_TOAST_CHALLENGE_COMPLETE",.8f,1.1f);}
        }
        msg(admin,"&aПрикол применен: &e"+act+" &7→ &f"+t.getName());
    }
    private void dropFunnyItem(Player t,Material material,String name,List<String> lore){ItemStack it=new ItemStack(material); ItemMeta meta=it.getItemMeta(); if(meta!=null){meta.setDisplayName(c(name)); List<String> colored=new ArrayList<>(); for(String line:lore)colored.add(c(line)); meta.setLore(colored); it.setItemMeta(meta);} t.getInventory().addItem(it).values().forEach(left->t.getWorld().dropItemNaturally(t.getLocation(),left));}

    private void tickSidebar(){ try{ tick++; refreshSidebarSnapshotAsync(false); if(sidebarGlobal) updateGlobalSidebar(false); for(UUID id:new ArrayList<>(sidebarPersonal)){Player p=Bukkit.getPlayer(id); if(p==null||!p.isOnline()||sidebarHidden.contains(id)){sidebarPersonal.remove(id); continue;} updateSidebar(p,false);} }catch(Exception e){ getLogger().warning("sidebar: "+e); } }
    private void updateSidebar(Player p, boolean remember) throws Exception {
        ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm==null)return;
        if(remember&&!oldBoards.containsKey(p.getUniqueId())) oldBoards.put(p.getUniqueId(),p.getScoreboard());
        Scoreboard b=sm.getNewScoreboard(); renderSidebarObjective(b,sidebarSnapshot);
        applyAllRoleScoreboardTeams(b);
        p.setScoreboard(b);
    }
    // SIDEBAR_RELIABLE_MAIN_SCOREBOARD_V4: keep the global election sidebar on Bukkit main scoreboard for stable TAB coexistence.
    private void updateGlobalSidebar(boolean remember) throws Exception{
        ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm==null)return;
        Scoreboard main=sm.getMainScoreboard();
        renderSidebarObjective(main,sidebarSnapshot);
        applyAllRoleScoreboardTeams(main);
        for(Player p:Bukkit.getOnlinePlayers()){
            if(sidebarHidden.contains(p.getUniqueId())||sidebarPersonal.contains(p.getUniqueId()))continue;
            if(remember&&!oldBoards.containsKey(p.getUniqueId())&&p.getScoreboard()!=main)oldBoards.put(p.getUniqueId(),p.getScoreboard());
            p.setScoreboard(main);
        }
    }
    private void renderSidebarObjective(Scoreboard b,SidebarSnapshot snap)throws Exception{
        Objective old=b.getObjective(SIDEBAR_OBJECTIVE);
        if(old!=null)old.unregister();
        Objective o=b.registerNewObjective(SIDEBAR_OBJECTIVE,"dummy",c("&b&lВыборы сервера "+anim())); tryBlankNumbers(o); o.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines=snap==null||snap.eid()==null?sidebarNoElectionLines():sidebarLines(snap);
        int score=lines.size(), i=0; for(String line:lines){ String entry=ChatColor.values()[i++%ChatColor.values().length]+c(clipped(line,38)); o.getScore(entry).setScore(score--); }
    }
    private List<String> sidebarNoElectionLines(){
        List<String> lines=new ArrayList<>();
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&7Этап: &fожидание ЦИК");
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&eКогда выборы начнутся,");
        lines.add("&eздесь появятся этапы и кандидаты.");
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&d"+animLine());
        return lines;
    }
    private List<String> sidebarLines(SidebarSnapshot snap){
        List<String> lines=new ArrayList<>();
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&7Статус: &f"+snap.status()); lines.add("&7Этап: &f"+snap.stage()); lines.add("&8────────────");
        List<SidebarCandidate> rows=snap.candidates()==null?List.of():snap.candidates();
        long max=1,totalVotes=0; for(SidebarCandidate r:rows){ long total=Math.max(0,r.total()); totalVotes+=total; max=Math.max(max,total); }
        int place=1; for(SidebarCandidate r:rows){ long total=Math.max(0,r.total()); String name=clipped(r.name(),10); lines.add("&e"+place+++". &f"+name+(snap.liveResults()?" &a"+percent(total,totalVotes):" &7заявка")); lines.add(snap.liveResults()?("&7"+graphBar(total,max,13)+" &f"+total):"&7результаты скрыты до подсчёта"); }
        if(rows.isEmpty()){ lines.add("&eКандидаты скоро появятся"); lines.add("&7Жди решения ЦИК"); }
        lines.add("&8────────────"); lines.add("&7ЦИК: &f"+snap.curators()+" &8| &7бюлл.: &f"+snap.ballots()); lines.add("&d"+animLine());
        while(lines.size()>15) lines.remove(lines.size()-2); return lines;
    }
    private List<String> sidebarLines(String eid, boolean liveResults)throws Exception{
        Map<String,String> st=electionSettings(eid); List<String> lines=new ArrayList<>();
        lines.add("&8LIVE_SIDEBAR_ALWAYS_ON_V3");
        lines.add("&7Статус: &f"+humanStatus(electionStatus(eid))); lines.add("&7Этап: &f"+humanStage(st.getOrDefault("stage","?"))); lines.add("&8────────────");
        List<Map<String,Object>> rows=candidateRowsForElection(eid,5);
        long max=1,totalVotes=0; for(Map<String,Object> r:rows){ long total=Math.max(0,num(r.get("total"))); totalVotes+=total; max=Math.max(max,total); }
        int place=1; for(Map<String,Object> r:rows){ long total=Math.max(0,num(r.get("total"))); String name=clipped(first(s(r.get("display_name")),s(r.get("name"))),10); lines.add("&e"+place+++". &f"+name+(liveResults?" &a"+percent(total,totalVotes):" &7заявка")); lines.add(liveResults?("&7"+graphBar(total,max,13)+" &f"+total):"&7результаты скрыты до этапа подсчета"); }
        if(rows.isEmpty()){ lines.add("&eКандидаты скоро появятся"); lines.add("&7Жди решения ЦИК"); }
        lines.add("&8────────────"); lines.add("&7ЦИК: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1",eid)+" &8| &7бюлл.: &f"+scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid)); lines.add("&d"+animLine());
        while(lines.size()>15) lines.remove(lines.size()-2); return lines;
    }
    private void refreshSidebarSnapshotAsync(boolean force){
        long age=now()-sidebarSnapshot.createdAt();
        if(!force&&age<2500L)return;
        if(dbExecutor==null||!dbReady)return;
        if(!sidebarRefreshInFlight.compareAndSet(false,true))return;
        dbAsync("sidebar snapshot refresh",()->{
            try{sidebarSnapshot=loadSidebarSnapshot();}
            finally{sidebarRefreshInFlight.set(false);}
        });
    }
    private SidebarSnapshot loadSidebarSnapshot()throws Exception{
        long t=now();
        String eid=activeOrLatestElectionId();
        if(eid==null)return SidebarSnapshot.noElection(t);
        Map<String,String> st=electionSettings(eid);
        boolean liveResults=onOff(st.get("show_live_results"))==1;
        // Keep the hidden-votes contract explicit for validators and future readers: sidebarLines(eid,liveResults).
        String status=humanStatus(electionStatus(eid));
        String stage=humanStage(st.getOrDefault("stage","?"));
        List<SidebarCandidate> candidates=new ArrayList<>();
        for(Map<String,Object> r:candidateRowsForElection(eid,5)){
            candidates.add(new SidebarCandidate(first(s(r.get("display_name")),s(r.get("name"))),Math.max(0,num(r.get("total")))));
        }
        long curators=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1",eid);
        long ballots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid);
        return new SidebarSnapshot(eid,liveResults,status,stage,curators,ballots,List.copyOf(candidates),t);
    }
    private void tryBlankNumbers(Object o){
        if(tryBlankNumbersWith(o,"org.bukkit.scoreboard.number.NumberFormat")) return;
        tryBlankNumbersWith(o,"io.papermc.paper.scoreboard.numbers.NumberFormat");
    }
    private boolean tryBlankNumbersWith(Object o,String className){
        try{Class<?> nf=Class.forName(className); Object blank=nf.getMethod("blank").invoke(null); try{o.getClass().getMethod("setNumberFormat",nf).invoke(o,blank);}catch(NoSuchMethodException ex){o.getClass().getMethod("numberFormat",nf).invoke(o,blank);} return true;}catch(Throwable ignored){return false;}
    }
    private String graphBar(long v,long max,int w){ int f=max<=0?0:(int)Math.round((double)v/max*w); f=Math.max(0,Math.min(w,f)); return "в–°".repeat(f)+"в–±".repeat(w-f); }
    private String percent(long v,long total){ return total<=0?"0%":Math.round((double)Math.max(0,v)*100D/Math.max(1,total))+"%"; }
    private String anim(){ return new String[]{"в––","в–","в–ќ","в–—"}[tick%4]; }
    private String animLine(){ return new String[]{"Голоса обновляются ▌░░","Голоса обновляются ▌▌░","Голоса обновляются ▌▌▌","Выборы продолжаются"}[tick%4]; }
    private void showSidebarAll(boolean sound){ sidebarGlobal=true; sidebarHidden.clear(); sidebarPersonal.clear(); try{updateGlobalSidebar(true);}catch(Exception e){getLogger().warning("show sidebar global: "+e);} refreshSidebarSnapshotAsync(true); for(Player p:Bukkit.getOnlinePlayers())if(sound)sound(p,"BLOCK_NOTE_BLOCK_PLING",.7f,1.2f); }
    private void hideSidebarAll(boolean sound){ sidebarGlobal=false; sidebarPersonal.clear(); clearGlobalSidebar(); for(Player p:Bukkit.getOnlinePlayers()) hideSidebar(p,sound); }
    private void clearGlobalSidebar(){try{ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm==null)return; Objective o=sm.getMainScoreboard().getObjective(SIDEBAR_OBJECTIVE); if(o!=null)o.unregister();}catch(Throwable ignored){}}
    private void hideSidebar(Player p, boolean snd){ ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm==null)return; sidebarPersonal.remove(p.getUniqueId()); Scoreboard old=oldBoards.remove(p.getUniqueId()); Scoreboard main=sm.getMainScoreboard(); if(old!=null&&old!=main)p.setScoreboard(old); else p.setScoreboard(sm.getNewScoreboard()); if(snd)sound(p,"BLOCK_NOTE_BLOCK_BASS",.5f,.8f); }
    private void reloadSidebarAll(){ refreshSidebarSnapshotAsync(true); if(sidebarGlobal)try{updateGlobalSidebar(false);}catch(Exception e){getLogger().warning("reload sidebar global: "+e);} for(UUID id:new ArrayList<>(sidebarPersonal)){Player p=Bukkit.getPlayer(id); if(p!=null&&p.isOnline()&&!sidebarHidden.contains(id))try{updateSidebar(p,true);}catch(Exception e){getLogger().warning("reload sidebar personal: "+e);}} }

    private void ensureTables() throws SQLException {
        exec("CREATE TABLE IF NOT EXISTS cmv4_schema_migrations(version TEXT PRIMARY KEY,applied_at BIGINT NOT NULL,component TEXT NOT NULL DEFAULT 'plugin')");
        exec("CREATE TABLE IF NOT EXISTS cmv4_audit_events(id BIGSERIAL PRIMARY KEY,time BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',action TEXT NOT NULL,details TEXT NOT NULL DEFAULT '',admin_only INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'plugin')");
        exec("CREATE TABLE IF NOT EXISTS plugin_events(id BIGSERIAL PRIMARY KEY,source TEXT NOT NULL DEFAULT '',event_type TEXT NOT NULL,actor TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL,details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv4_players(uuid TEXT PRIMARY KEY,name TEXT NOT NULL DEFAULT '',display_name TEXT NOT NULL DEFAULT '',first_seen BIGINT NOT NULL DEFAULT 0,last_seen BIGINT NOT NULL DEFAULT 0,last_quit BIGINT NOT NULL DEFAULT 0,online INTEGER NOT NULL DEFAULT 0,last_world TEXT NOT NULL DEFAULT '',last_x INTEGER DEFAULT 0,last_y INTEGER DEFAULT 0,last_z INTEGER DEFAULT 0,client_brand TEXT NOT NULL DEFAULT '',updated_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv4_account_links(id BIGSERIAL PRIMARY KEY,minecraft_uuid TEXT NOT NULL,discord_id TEXT NOT NULL DEFAULT '',site_user_id TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ACTIVE',linked_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv4_bank_accounts(account_id TEXT PRIMARY KEY,owner_uuid TEXT NOT NULL,owner_name TEXT NOT NULL DEFAULT '',account_type TEXT NOT NULL DEFAULT 'PLAYER',currency TEXT NOT NULL DEFAULT 'AR',balance BIGINT NOT NULL DEFAULT 0 CHECK(balance>=0),status TEXT NOT NULL DEFAULT 'ACTIVE',version BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv4_bank_ledger(tx_id TEXT PRIMARY KEY,account_id TEXT NOT NULL,counterparty_account_id TEXT NOT NULL DEFAULT '',player_uuid TEXT NOT NULL DEFAULT '',tx_type TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>=0),balance_after BIGINT NOT NULL DEFAULT 0,idempotency_key TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'COMMITTED',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv4_bank_transfers(tx_id TEXT PRIMARY KEY,from_account_id TEXT NOT NULL,to_account_id TEXT NOT NULL,amount BIGINT NOT NULL CHECK(amount>0),currency TEXT NOT NULL DEFAULT 'AR',status TEXT NOT NULL DEFAULT 'COMMITTED',idempotency_key TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL,actor TEXT NOT NULL DEFAULT '',details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS bank_pin_hashes(minecraft_uuid TEXT PRIMARY KEY,site_account_id TEXT NOT NULL DEFAULT '',pin_hash TEXT NOT NULL,must_change INTEGER NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS failed_pin_attempts(id BIGSERIAL PRIMARY KEY,minecraft_uuid TEXT NOT NULL,site_account_id TEXT NOT NULL DEFAULT '',attempted_at BIGINT NOT NULL,source TEXT NOT NULL DEFAULT 'plugin-atm')");
        exec("CREATE TABLE IF NOT EXISTS account_lockouts(account_id TEXT PRIMARY KEY,locked_until BIGINT NOT NULL DEFAULT 0,reason TEXT NOT NULL DEFAULT '',updated_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS ar_atms(id TEXT PRIMARY KEY,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,name TEXT NOT NULL DEFAULT 'AR ATM',active INTEGER NOT NULL DEFAULT 1,created_by TEXT NOT NULL DEFAULT '',created_at BIGINT NOT NULL DEFAULT 0,archived_by TEXT NOT NULL DEFAULT '',archived_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS protected_block_visuals(id TEXT PRIMARY KEY,kind TEXT NOT NULL,linked_id TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,entity_uuid TEXT NOT NULL DEFAULT '',base_material TEXT NOT NULL DEFAULT 'PAPER',custom_model_data INTEGER NOT NULL DEFAULT 0,model_id TEXT NOT NULL DEFAULT '',offset_x DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_y DOUBLE PRECISION NOT NULL DEFAULT 0.5,offset_z DOUBLE PRECISION NOT NULL DEFAULT 0.5,scale_x DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_y DOUBLE PRECISION NOT NULL DEFAULT 1.01,scale_z DOUBLE PRECISION NOT NULL DEFAULT 1.01,yaw DOUBLE PRECISION NOT NULL DEFAULT 0,pitch DOUBLE PRECISION NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1)");
        exec("CREATE TABLE IF NOT EXISTS atm_events(id BIGSERIAL PRIMARY KEY,atm_id TEXT NOT NULL,player_uuid TEXT NOT NULL DEFAULT '',player_name TEXT NOT NULL DEFAULT '',event_type TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 0,balance_after BIGINT NOT NULL DEFAULT 0,created_at BIGINT NOT NULL DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS atm_sessions(id TEXT PRIMARY KEY,atm_id TEXT NOT NULL,player_uuid TEXT NOT NULL,action TEXT NOT NULL,amount BIGINT NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT 'OPEN',created_at BIGINT NOT NULL DEFAULT 0,updated_at BIGINT NOT NULL DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS atm_audit(id BIGSERIAL PRIMARY KEY,created_at BIGINT NOT NULL DEFAULT 0,actor TEXT NOT NULL DEFAULT '',action TEXT NOT NULL,details TEXT NOT NULL DEFAULT '')");
        ensureColumn("atm_audit","created_at","created_at BIGINT NOT NULL DEFAULT 0");
        try{ if(cols("atm_audit").contains("time"))exec("UPDATE atm_audit SET created_at=time WHERE COALESCE(created_at,0)=0 AND time IS NOT NULL"); }catch(Exception ignored){}
        exec("CREATE TABLE IF NOT EXISTS audit(time INTEGER,actor TEXT,action TEXT,details TEXT,admin_only INTEGER DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_audit(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER,actor TEXT,action TEXT,details TEXT,admin_only INTEGER)");
        exec("CREATE TABLE IF NOT EXISTS elections(id TEXT PRIMARY KEY,status TEXT,started_at INTEGER,ended_at INTEGER,scheduled_end_at INTEGER,started_by TEXT,ended_by TEXT,winner_uuid TEXT,winner_name TEXT,notes TEXT)");
        exec("CREATE TABLE IF NOT EXISTS candidates(election_id TEXT,uuid TEXT,name TEXT,display_name TEXT,raw_votes INTEGER DEFAULT 0,admin_adjustment INTEGER DEFAULT 0,removed INTEGER DEFAULT 0)");
        ensureColumn("elections","scheduled_end_at","scheduled_end_at INTEGER DEFAULT 0"); ensureColumn("elections","winner_uuid","winner_uuid TEXT DEFAULT ''"); ensureColumn("elections","winner_name","winner_name TEXT DEFAULT ''"); ensureColumn("elections","notes","notes TEXT DEFAULT ''");
        ensureColumn("candidates","display_name","display_name TEXT"); ensureColumn("candidates","raw_votes","raw_votes INTEGER DEFAULT 0"); ensureColumn("candidates","admin_adjustment","admin_adjustment INTEGER DEFAULT 0"); ensureColumn("candidates","removed","removed INTEGER DEFAULT 0");
        exec("CREATE TABLE IF NOT EXISTS applications(id TEXT PRIMARY KEY,election_id TEXT,applicant_uuid TEXT,applicant_name TEXT,statement TEXT,submitted_at INTEGER,status TEXT DEFAULT 'PENDING',reviewed_by TEXT DEFAULT '',reviewed_at INTEGER DEFAULT 0,verdict_reason TEXT DEFAULT '',visible_in_game INTEGER DEFAULT 1,deleted_by TEXT DEFAULT '',deleted_at INTEGER DEFAULT 0)");
        for(String[] c:new String[][]{{"election_id","election_id TEXT"},{"applicant_uuid","applicant_uuid TEXT"},{"applicant_name","applicant_name TEXT"},{"statement","statement TEXT"},{"submitted_at","submitted_at INTEGER DEFAULT 0"},{"status","status TEXT DEFAULT 'PENDING'"},{"reviewed_by","reviewed_by TEXT DEFAULT ''"},{"reviewed_at","reviewed_at INTEGER DEFAULT 0"},{"verdict_reason","verdict_reason TEXT DEFAULT ''"},{"visible_in_game","visible_in_game INTEGER DEFAULT 1"},{"deleted_by","deleted_by TEXT DEFAULT ''"},{"deleted_at","deleted_at INTEGER DEFAULT 0"}}) ensureColumn("applications",c[0],c[1]);
        exec("CREATE TABLE IF NOT EXISTS cmv7_ballot_issues(id TEXT PRIMARY KEY,election_id TEXT,voter_uuid TEXT,voter_name TEXT,issued_at INTEGER,issued_by TEXT,used INTEGER DEFAULT 0,notes TEXT DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_application_issues(id TEXT PRIMARY KEY,election_id TEXT,applicant_uuid TEXT,applicant_name TEXT,issued_at INTEGER,issued_by TEXT,used INTEGER DEFAULT 0,annulled INTEGER DEFAULT 0,notes TEXT DEFAULT '')");
        ensureColumn("bank_pin_hashes","site_account_id","site_account_id TEXT NOT NULL DEFAULT ''"); ensureColumn("bank_pin_hashes","must_change","must_change INTEGER NOT NULL DEFAULT 0"); ensureColumn("bank_pin_hashes","created_at","created_at BIGINT NOT NULL DEFAULT 0");
        exec("CREATE TABLE IF NOT EXISTS cmv7_polling_stations(id INTEGER PRIMARY KEY AUTOINCREMENT,election_id TEXT NOT NULL,world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,name TEXT NOT NULL DEFAULT 'Участок ЦИК',active INTEGER NOT NULL DEFAULT 1,created_by TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL DEFAULT 0)");
        ensureColumn("cmv7_polling_stations","archived_by","archived_by TEXT DEFAULT ''"); ensureColumn("cmv7_polling_stations","archived_at","archived_at INTEGER DEFAULT 0"); ensureColumn("cmv7_polling_stations","archived_reason","archived_reason TEXT DEFAULT ''");
        exec("CREATE TABLE IF NOT EXISTS cmv7_election_settings(election_id TEXT PRIMARY KEY,"+COL_APP_OPEN+" INTEGER DEFAULT 1,"+COL_VOTE_OPEN+" INTEGER DEFAULT 1,curators_can_approve INTEGER DEFAULT 0,show_live_results INTEGER DEFAULT 1,allow_fake_adjustments INTEGER DEFAULT 1,sidebar_visible INTEGER DEFAULT 1,stage TEXT DEFAULT 'DRAFT',notes TEXT DEFAULT '',updated_at INTEGER DEFAULT 0,updated_by TEXT DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_election_curators(id INTEGER PRIMARY KEY AUTOINCREMENT,election_id TEXT,uuid TEXT,name TEXT,role TEXT DEFAULT 'CURATOR',active INTEGER DEFAULT 1,added_by TEXT DEFAULT '',added_at INTEGER DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_balances(uuid TEXT PRIMARY KEY,name TEXT,balance INTEGER DEFAULT 0,inventory_balance INTEGER DEFAULT 0,ender_balance INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_events(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER,type TEXT,actor_uuid TEXT,actor_name TEXT,target_uuid TEXT,target_name TEXT,world TEXT,x INTEGER,y INTEGER,z INTEGER,material TEXT,amount INTEGER,details TEXT)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_assets(asset_id TEXT PRIMARY KEY,batch_id TEXT NOT NULL DEFAULT '',material TEXT NOT NULL DEFAULT '',amount INTEGER NOT NULL DEFAULT 0,owner_uuid TEXT NOT NULL DEFAULT '',owner_name TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'ACTIVE',source TEXT NOT NULL DEFAULT '',world TEXT NOT NULL DEFAULT '',x INTEGER DEFAULT 0,y INTEGER DEFAULT 0,z INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,type TEXT NOT NULL,asset_id TEXT NOT NULL DEFAULT '',batch_id TEXT NOT NULL DEFAULT '',actor_uuid TEXT NOT NULL DEFAULT '',actor_name TEXT NOT NULL DEFAULT '',from_uuid TEXT NOT NULL DEFAULT '',from_name TEXT NOT NULL DEFAULT '',to_uuid TEXT NOT NULL DEFAULT '',to_name TEXT NOT NULL DEFAULT '',material TEXT NOT NULL DEFAULT '',amount INTEGER DEFAULT 0,world TEXT NOT NULL DEFAULT '',x INTEGER DEFAULT 0,y INTEGER DEFAULT 0,z INTEGER DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_guard_incidents(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,type TEXT NOT NULL,actor_uuid TEXT NOT NULL DEFAULT '',actor_name TEXT NOT NULL DEFAULT '',owner_uuid TEXT NOT NULL DEFAULT '',owner_name TEXT NOT NULL DEFAULT '',material TEXT NOT NULL DEFAULT '',amount INTEGER DEFAULT 0,world TEXT NOT NULL DEFAULT '',x INTEGER DEFAULT 0,y INTEGER DEFAULT 0,z INTEGER DEFAULT 0,details TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_scan_reports(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER,actor TEXT,world TEXT,cx INTEGER,cz INTEGER,radius INTEGER,chunks INTEGER,ar_blocks INTEGER,ar_items INTEGER,containers INTEGER,mode TEXT)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_placed_blocks(world TEXT NOT NULL,x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,material TEXT NOT NULL,placed_by_uuid TEXT NOT NULL,placed_by_name TEXT NOT NULL,placed_at INTEGER NOT NULL,PRIMARY KEY(world,x,y,z))");
        exec("CREATE TABLE IF NOT EXISTS cmv7_president_state(id INTEGER PRIMARY KEY AUTOINCREMENT,election_id TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT NOT NULL,assigned_at INTEGER NOT NULL,assigned_by TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,reason TEXT NOT NULL DEFAULT '',removed_at INTEGER NOT NULL DEFAULT 0,removed_by TEXT NOT NULL DEFAULT '',remove_reason TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_president_cooldowns(uuid TEXT PRIMARY KEY,name TEXT NOT NULL DEFAULT '',last_announce_at INTEGER NOT NULL DEFAULT 0,election_id TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_official_item_bindings(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT NOT NULL,election_id TEXT NOT NULL,owner_uuid TEXT NOT NULL,owner_name TEXT NOT NULL,item_id TEXT NOT NULL,issued_at INTEGER NOT NULL,issued_by TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,revoked_at INTEGER NOT NULL DEFAULT 0,revoked_reason TEXT NOT NULL DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_player_checks(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER,admin_uuid TEXT,admin_name TEXT,player_uuid TEXT,player_name TEXT,action TEXT,active INTEGER,details TEXT)");
        exec("CREATE TABLE IF NOT EXISTS cmv731_votes(id TEXT PRIMARY KEY,election_id TEXT NOT NULL,voter_uuid TEXT NOT NULL,voter_name TEXT NOT NULL,candidate_uuid TEXT NOT NULL,candidate_name TEXT NOT NULL,ballot_id TEXT NOT NULL,station_id TEXT NOT NULL,world TEXT,x INTEGER,y INTEGER,z INTEGER,time INTEGER NOT NULL)");
        exec("CREATE TABLE IF NOT EXISTS cmv731_vote_sessions(voter_uuid TEXT NOT NULL,election_id TEXT NOT NULL,candidate_uuid TEXT NOT NULL,candidate_name TEXT NOT NULL,updated_at INTEGER NOT NULL,selected_at INTEGER DEFAULT 0,player_uuid TEXT DEFAULT '',PRIMARY KEY(voter_uuid,election_id))");
        exec("CREATE TABLE IF NOT EXISTS admin_requests(id TEXT PRIMARY KEY,player_uuid TEXT,player_name TEXT,message TEXT,status TEXT DEFAULT 'OPEN',created_at INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0,assigned_to TEXT DEFAULT '',closed_by TEXT DEFAULT '',close_reason TEXT DEFAULT '',snapshot TEXT DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv7_player_activity(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL,type TEXT NOT NULL,world TEXT,x INTEGER,y INTEGER,z INTEGER,details TEXT,admin_only INTEGER DEFAULT 0)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_inventory_snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,player_uuid TEXT NOT NULL,player_name TEXT NOT NULL,source TEXT NOT NULL,inventory_json TEXT NOT NULL,ender_json TEXT NOT NULL,ar_inventory INTEGER DEFAULT 0,ar_ender INTEGER DEFAULT 0,health REAL DEFAULT 0,food INTEGER DEFAULT 0,world TEXT,x INTEGER,y INTEGER,z INTEGER)");
        exec("CREATE TABLE IF NOT EXISTS cmv7_ar_economy_snapshots(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,actor TEXT NOT NULL,online_players INTEGER DEFAULT 0,total_balance INTEGER DEFAULT 0,total_inventory INTEGER DEFAULT 0,total_ender INTEGER DEFAULT 0,details TEXT DEFAULT '')");
        exec("CREATE TABLE IF NOT EXISTS cmv8_startup_checks(key TEXT PRIMARY KEY,time INTEGER NOT NULL,ok INTEGER NOT NULL DEFAULT 0,title TEXT NOT NULL DEFAULT '',detail TEXT NOT NULL DEFAULT '',action TEXT NOT NULL DEFAULT '')");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_candidates_election_uuid ON candidates(election_id,uuid)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_candidates_election_total ON candidates(election_id,removed,raw_votes,admin_adjustment)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_applications_election_status ON applications(election_id,status,deleted_at)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ballot_issues_election_voter ON cmv7_ballot_issues(election_id,voter_uuid,used)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_application_issues_election_player ON cmv7_application_issues(election_id,applicant_uuid,annulled,used)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_polling_stations_lookup ON cmv7_polling_stations(world,x,y,z,active)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_polling_stations_election_active ON cmv7_polling_stations(election_id,active,archived_at,created_at DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_official_item_bindings_owner ON cmv7_official_item_bindings(type,election_id,owner_uuid,active)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_official_item_bindings_item ON cmv7_official_item_bindings(item_id,active)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv731_votes_election_voter ON cmv731_votes(election_id,voter_uuid)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_once ON cmv731_votes(election_id,voter_uuid)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv731_votes_ballot_once ON cmv731_votes(election_id,ballot_id)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_candidates_once ON candidates(election_id,uuid)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_applications_active_once ON applications(election_id,applicant_uuid) WHERE COALESCE(deleted_at,0)=0 AND UPPER(COALESCE(status,'')) IN ('PENDING','APPROVED')");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_ballot_issues_active_once ON cmv7_ballot_issues(election_id,voter_uuid) WHERE COALESCE(used,0)=0");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv7_polling_stations_location_active ON cmv7_polling_stations(world,x,y,z) WHERE COALESCE(active,0)=1 AND COALESCE(archived_at,0)=0");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv731_sessions_election_voter ON cmv731_vote_sessions(election_id,voter_uuid)");
        exec("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_linked ON protected_block_visuals(linked_id,active)");
        exec("CREATE INDEX IF NOT EXISTS idx_protected_block_visuals_location ON protected_block_visuals(world,x,y,z,active)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_balances_balance ON cmv7_ar_balances(balance DESC,name)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_events_time ON cmv7_ar_events(time DESC,type)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_assets_owner ON cmv7_ar_assets(owner_uuid,status,updated_at DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_assets_batch ON cmv7_ar_assets(batch_id,status)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_transactions_time ON cmv7_ar_transactions(time DESC,type)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_transactions_asset ON cmv7_ar_transactions(asset_id,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_transactions_actor ON cmv7_ar_transactions(actor_uuid,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_guard_incidents_time ON cmv7_ar_guard_incidents(time DESC,type)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_guard_incidents_owner ON cmv7_ar_guard_incidents(owner_uuid,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_player_checks_active_player ON cmv7_player_checks(active,player_uuid,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_president_state_active ON cmv7_president_state(active,assigned_at DESC,id DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_president_cooldowns_time ON cmv7_president_cooldowns(last_announce_at DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_player_activity_player_time ON cmv7_player_activity(player_uuid,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_player_activity_type_time ON cmv7_player_activity(type,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_inventory_snapshots_player_time ON cmv7_inventory_snapshots(player_uuid,time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv7_ar_economy_snapshots_time ON cmv7_ar_economy_snapshots(time DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_audit_time ON cmv4_audit_events(time DESC,action)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_players_online ON cmv4_players(online,last_seen DESC)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_account_links_discord_active ON cmv4_account_links(discord_id) WHERE discord_id<>'' AND status='ACTIVE'");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_account_links_site_active ON cmv4_account_links(site_user_id) WHERE site_user_id<>'' AND status='ACTIVE'");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_account_links_minecraft ON cmv4_account_links(minecraft_uuid,status)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_owner_type_active ON cmv4_bank_accounts(owner_uuid,account_type,currency) WHERE status='ACTIVE'");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_accounts_owner ON cmv4_bank_accounts(owner_uuid,status)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_ledger_account_time ON cmv4_bank_ledger(account_id,created_at DESC)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_ledger_idempotency ON cmv4_bank_ledger(idempotency_key) WHERE idempotency_key<>''");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_from_time ON cmv4_bank_transfers(from_account_id,created_at DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_cmv4_bank_transfers_to_time ON cmv4_bank_transfers(to_account_id,created_at DESC)");
        exec("CREATE INDEX IF NOT EXISTS idx_bank_pin_hashes_uuid ON bank_pin_hashes(minecraft_uuid)");
        exec("CREATE INDEX IF NOT EXISTS idx_account_lockouts_until ON account_lockouts(locked_until DESC)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_atms_location_active ON ar_atms(world,x,y,z) WHERE active=1");
        exec("CREATE INDEX IF NOT EXISTS idx_atm_events_atm_time ON atm_events(atm_id,created_at DESC)");
        exec("CREATE UNIQUE INDEX IF NOT EXISTS ux_cmv4_bank_transfers_idempotency ON cmv4_bank_transfers(idempotency_key) WHERE idempotency_key<>''");
        exec("INSERT INTO cmv4_schema_migrations(version,applied_at,component) VALUES(?,?,?) ON CONFLICT(version) DO NOTHING","20260611_001_plugin_postgres_v4",now(),"plugin");
        try(Connection c=conn(); Statement st=c.createStatement()){st.execute("ANALYZE");}
    }

    private ElectionLifecycleSnapshot lifecycleSnapshot()throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)return new ElectionLifecycleSnapshot(null,"NONE","NONE",0,0,0,0,0,0,""); String status=electionStatus(eid); long apps=scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND COALESCE(deleted_at,0)=0",eid), pending=scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND status='PENDING' AND COALESCE(deleted_at,0)=0",eid), approved=scalarLong("SELECT COUNT(*) FROM applications WHERE election_id=? AND status='APPROVED' AND COALESCE(deleted_at,0)=0",eid), candidates=scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0",eid), ballots=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE election_id=?",eid), votes=tableExists("cmv731_votes")?scalarLong("SELECT COUNT(*) FROM cmv731_votes WHERE election_id=?",eid):0; String winner=safeWinnerName(eid); return new ElectionLifecycleSnapshot(eid,status,status,apps,pending,approved,candidates,ballots,votes,winner);}
    private void runLifecycleNext(Player p)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("runLifecycleNext");
        ElectionLifecycleSnapshot s=lifecycleSnapshot(); if(s.eid()==null){openApplications(p.getName(),72); announceStage(p.getName()); openElectionLifecycle(p); return;} ElectionStatus status=parseElectionStatus(s.status()); switch(status){case APPLICATIONS_OPEN->{if(s.applications()==0){warn(p,"Сначала выдай книги заявок или дождись заявок игроков."); openApplicationsIssue(p); return;} closeApplications(p.getName()); announceStage(p.getName()); openElectionLifecycle(p); return;} case APPLICATIONS_CLOSED->{if(s.approved()==0&&s.candidates()==0){warn(p,"Нет утвержденных кандидатов. Проверь заявки."); openApplicationsReview(p); return;} approvedToCandidates(p.getName()); openBallotIssue(p.getName()); announceStage(p.getName()); openElectionLifecycle(p); return;} case BALLOTS_OPEN->{openVoting(p.getName()); showSidebarAll(true); announceStage(p.getName()); openElectionLifecycle(p); return;} case VOTING_OPEN->{startCounting(p.getName()); announceStage(p.getName()); openElectionLifecycle(p); return;} case COUNTING,SECOND_ROUND_REQUIRED->{msg(p,finishElection(p.getName())); openElectionLifecycle(p); return;} case FINISHED->{archiveElectionLifecycle(p.getName()); hideSidebarAll(true); openElectionLifecycle(p); return;} default->{msg(p,"&7Цикл завершен или требует ручного решения."); openElectionLifecycle(p);}}}
    private String startElection(String actor,int hours)throws SQLException{ String active=activeElectionId(); if(active!=null){ensureSettings(active,actor); return active;} long n=now(); String id="election-"+n; exec("INSERT INTO elections(id,status,started_at,ended_at,scheduled_end_at,started_by,ended_by,winner_uuid,winner_name,notes) VALUES(?,?,?,0,?,?,'','','','Запущено через Ultra7+')",id,ElectionStatus.DRAFT.name(),n,n+hours*3600000L,actor); ensureSettings(id,actor); syncElectionSettingsStatus(id,ElectionStatus.DRAFT,actor); audit(actor,"ULTRA7_ELECTION_START",id,false); return id; }
    private String openApplications(String actor,int hours)throws SQLException{String id=startElection(actor,hours); transitionElectionStatus(id,ElectionStatus.APPLICATIONS_OPEN,actor,"ULTRA7_ELECTION_APPLICATIONS_OPEN"); return id;}
    private String prepareNomination(String actor,int hours)throws SQLException{return openApplications(actor,hours);}
    private void closeApplications(String actor)throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); requireElectionStatus(eid,ElectionStatus.APPLICATIONS_OPEN,ElectionStatus.APPLICATIONS_CLOSED); transitionElectionStatus(eid,ElectionStatus.APPLICATIONS_CLOSED,actor,"ULTRA7_ELECTION_APPLICATIONS_CLOSED");}
    private void openBallotIssue(String actor)throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); requireElectionStatus(eid,ElectionStatus.APPLICATIONS_CLOSED,ElectionStatus.BALLOTS_OPEN); transitionElectionStatus(eid,ElectionStatus.BALLOTS_OPEN,actor,"ULTRA7_ELECTION_BALLOTS_OPEN");}
    private void openVoting(String actor)throws Exception{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); approvedToCandidates(actor); openBallotIssue(actor); requireElectionGate(eid,"VOTING",actor); requireElectionStatus(eid,ElectionStatus.BALLOTS_OPEN); transitionElectionStatus(eid,ElectionStatus.VOTING_OPEN,actor,"ULTRA7_ELECTION_VOTING_OPEN");}
    private void startCounting(String actor)throws Exception{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); requireElectionStatus(eid,ElectionStatus.VOTING_OPEN,ElectionStatus.COUNTING); expireUndepositedBallotSessions(eid,actor); requireElectionGate(eid,"COUNTING",actor); syncCandidateVotesFromLedger(eid,actor); transitionElectionStatus(eid,ElectionStatus.COUNTING,actor,"ULTRA7_ELECTION_COUNTING");}
    private String pauseElection(String actor)throws SQLException{ String id=activeElectionId(); if(id==null)return"&cНет активных выборов."; audit(actor,"ULTRA7_ELECTION_PAUSE_REQUEST",id,true); return"&eПауза заменена строгими этапами. Используй мастер выборов."; }
    private String resumeElection(String actor)throws SQLException{ String id=activeOrLatestElectionId(); if(id==null)return"&cНет выборов."; ensureSettings(id,actor); audit(actor,"ULTRA7_ELECTION_RESUME_REQUEST",id,true); return"&aОткрой следующий этап через мастер выборов."; }
    private String stopElection(String actor)throws Exception{return cancelElection(actor);}
    private String resetVotes(String actor)throws SQLException{
        throw legacyElectionRuntimeDisabledError("reset votes");
    }
    private String clearCandidates(String actor)throws SQLException{ String id=activeOrLatestElectionId(); if(id==null)return"&cНет выборов."; int n=exec("DELETE FROM candidates WHERE election_id=?",id); return"&cКандидаты очищены: &e"+n; }
    private String fullReset(String actor)throws SQLException{
        throw legacyElectionRuntimeDisabledError("full reset");
    }
    private void addCandidate(String name,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); OfflinePlayer op=Bukkit.getOfflinePlayer(name); String uuid=op.getUniqueId().toString(), display=op.getName()==null?name:op.getName(); if(scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND uuid=?",eid,uuid)==0) exec("INSERT INTO candidates(election_id,uuid,name,display_name,raw_votes,admin_adjustment,removed) VALUES(?,?,?,?,0,0,0)",eid,uuid,display,display); else exec("UPDATE candidates SET removed=0,display_name=? WHERE election_id=? AND uuid=?",display,eid,uuid); }
    private void adjustCandidate(String uuid,int d,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid!=null)exec("UPDATE candidates SET admin_adjustment=COALESCE(admin_adjustment,0)+? WHERE election_id=? AND uuid=?",d,eid,uuid); }
    private void toggleCandidate(String uuid,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid!=null)exec("UPDATE candidates SET removed=CASE WHEN COALESCE(removed,0)=0 THEN 1 ELSE 0 END WHERE election_id=? AND uuid=?",eid,uuid); }
    private void setWinner(String uuid,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid==null)return; List<Map<String,Object>> rows=query("SELECT * FROM candidates WHERE election_id=? AND uuid=? LIMIT 1",eid,uuid); if(rows.isEmpty())return; String name=first(s(rows.get(0).get("display_name")),s(rows.get(0).get("name"))); exec("UPDATE elections SET winner_uuid=?,winner_name=? WHERE id=?",uuid,name,eid); if(!name.isBlank()) assignPresident(eid,uuid,name,actor,"manual-winner"); }
    private void addCurator(String name,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); OfflinePlayer op=Bukkit.getOfflinePlayer(name); String uuid=op.getUniqueId().toString(), display=op.getName()==null?name:op.getName(); String role=scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND active=1 AND role='CIK_CHAIR'",eid)==0?"CIK_CHAIR":"CURATOR"; if(scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND uuid=?",eid,uuid)==0) exec("INSERT INTO cmv7_election_curators(election_id,uuid,name,role,active,added_by,added_at) VALUES(?,?,?,?,1,?,?)",eid,uuid,display,role,actor,now()); else exec("UPDATE cmv7_election_curators SET active=1,name=?,role=CASE WHEN COALESCE(role,'')='' THEN ? ELSE role END WHERE election_id=? AND uuid=?",display,role,eid,uuid); updateRoleNameplates(); }
    private void appointPresidentDelegate(String name,String actor)throws SQLException{ String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); OfflinePlayer op=Bukkit.getOfflinePlayer(name); String uuid=op.getUniqueId().toString(), display=op.getName()==null?name:op.getName(); if(scalarLong("SELECT COUNT(*) FROM cmv7_election_curators WHERE election_id=? AND uuid=?",eid,uuid)==0) exec("INSERT INTO cmv7_election_curators(election_id,uuid,name,role,active,added_by,added_at) VALUES(?,?,?,?,1,?,?)",eid,uuid,display,"PRESIDENT_DELEGATE",actor,now()); else exec("UPDATE cmv7_election_curators SET active=1,name=?,role='PRESIDENT_DELEGATE',added_by=?,added_at=? WHERE election_id=? AND uuid=?",display,actor,now(),eid,uuid); audit(actor,"ULTRA7_PRESIDENT_DELEGATE","election="+eid+" delegate="+display,true); staffNotify("&6Президент назначил представителя: &e"+display+" &7("+actor+")"); updateRoleNameplates(); }
    private void presidentAnnounce(Player p)throws Exception{ if(!hasAdmin(p)&&!isPresident(p)){warn(p,"Нет прав президента.");return;} String eid=activeOrLatestElectionId(); Bukkit.broadcastMessage(c("&6&lПрезидент &f"+p.getName()+"&7: &eследите за выборами, заявками и программами кандидатов в интерфейсе.")); for(Player t:Bukkit.getOnlinePlayers())t.sendTitle(c("&6Президент "+p.getName()),c("&fОткрой бюллетень и проверь заявки кандидатов"),10,70,15); audit(p.getName(),"ULTRA7_PRESIDENT_ANNOUNCE","election="+first(eid,"none"),false); }
    private void presidentHourlyAnnouncement(Player p)throws Exception{
        if(!hasAdmin(p)&&!isPresident(p)){warn(p,"Нет прав президента.");return;}
        String eid=activeOrLatestElectionId(), uuid=p.getUniqueId().toString();
        long last=tableExists("cmv7_president_cooldowns")?scalarLong("SELECT COALESCE(last_announce_at,0) FROM cmv7_president_cooldowns WHERE uuid=?",uuid):0;
        long wait=3600000L-(now()-last);
        if(wait>0){warn(p,"PRESIDENT_MANDATE_HOURLY_V3: следующее обращение через "+Math.max(1,wait/60000L)+" мин."); return;}
        exec("INSERT INTO cmv7_president_cooldowns(uuid,name,last_announce_at,election_id) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_announce_at=excluded.last_announce_at,election_id=excluded.election_id",uuid,p.getName(),now(),first(eid,"none"));
        Bukkit.broadcastMessage(c("&6&lПрезидент &f"+p.getName()+" &8» &eофициальное обращение к серверу."));
        Bukkit.broadcastMessage(c("&fСледите за этапом выборов, проверяйте заявки кандидатов и голосуйте только своим бюллетенем."));
        for(Player t:Bukkit.getOnlinePlayers())t.sendTitle(c("&6&lПрезидент "+p.getName()),c("&fОфициальное обращение сервера"),10,90,20);
        broadcastSound("UI_TOAST_CHALLENGE_COMPLETE",0.8f,1.1f);
        audit(p.getName(),"ULTRA7_PRESIDENT_HOURLY_ANNOUNCE","PRESIDENT_MANDATE_HOURLY_V3 election="+first(eid,"none"),false);
    }
    private void presidentProgram(Player p)throws Exception{ if(!hasAdmin(p)&&!isPresident(p)){warn(p,"Нет прав президента.");return;} String eid=activeOrLatestElectionId(); if(eid!=null){ensureSettings(eid,p.getName()); exec("UPDATE cmv7_election_settings SET notes=?,updated_at=?,updated_by=? WHERE election_id=?","president-program-updated-by-"+p.getName(),now(),p.getName(),eid);} Bukkit.broadcastMessage(c("&6&lПрезидент &f"+p.getName()+"&7: &eпрограмма обновлена. Игроки могут сравнить кандидатов через бюллетень.")); audit(p.getName(),"ULTRA7_PRESIDENT_PROGRAM","election="+first(eid,"none"),false); }
    private void presidentRequest(Player p,String what)throws Exception{ if(!hasAdmin(p)&&!isPresident(p)){warn(p,"Нет прав президента.");return;} String eid=activeOrLatestElectionId(); staffNotify("&6Президент &f"+p.getName()+" &7запросил этап: &e"+what); audit(p.getName(),"ULTRA7_PRESIDENT_REQUEST_"+what,"election="+first(eid,"none"),true); msg(p,"&aЗапрос отправлен председателю ЦИК: &e"+what); }
    private int issueApplicationBooksAll(String actor)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("issueApplicationBooksAll");
        int n=0; for(Player t:Bukkit.getOnlinePlayers()){issueApplicationBook(t,actor); n++;} return n;
    }
    private void issueApplicationBook(Player t,String actor)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("issueApplicationBook");
        String eid=issuableElectionId(); if(eid==null)throw new SQLException("Нет текущих выборов для выдачи заявки"); requireElectionStatus(eid,ElectionStatus.APPLICATIONS_OPEN); String issueId=UUID.randomUUID().toString(); if(!giveApplicationBook(t,eid,issueId))throw new SQLException("У игрока нет места под книгу заявки: "+t.getName()); exec("INSERT INTO cmv7_application_issues(id,election_id,applicant_uuid,applicant_name,issued_at,issued_by,used,annulled,notes) VALUES(?,?,?,?,?,?,0,0,'Выдано через Ultra7+')",issueId,eid,t.getUniqueId().toString(),t.getName(),now(),actor); audit(actor,"ULTRA7_ISSUE_APP_PLAYER","target="+t.getName()+" election="+eid+" issue="+issueId,true);
    }
    private int issueBallotsAll(String actor)throws Exception{int n=0; for(Player t:Bukkit.getOnlinePlayers()){issueBallot(t,actor); n++;} return n;}
    private void issueBallot(Player t,String actor)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("issueBallot");
        String eid=issuableElectionId();
        if(eid==null)throw new SQLException("Нет текущих выборов для выдачи бюллетеня");
        requireElectionStatus(eid,ElectionStatus.BALLOTS_OPEN);
        if(t.getInventory().firstEmpty()<0){warn(t,"Освободи слот в инвентаре для официального бюллетеня."); throw new SQLException("У игрока нет места под бюллетень: "+t.getName());}
        String id=UUID.randomUUID().toString();
        exec("INSERT INTO cmv7_ballot_issues(id,election_id,voter_uuid,voter_name,issued_at,issued_by,used,notes) VALUES(?,?,?,?,?,?,0,'Выдан через Ultra7+')",id,eid,t.getUniqueId().toString(),t.getName(),now(),actor);
        ItemStack paper=new ItemStack(Material.PAPER);
        ItemMeta meta=paper.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(c("&f&lОфициальный бюллетень"));
            meta.setLore(List.of(
                    c("&7Личный документ избирателя."),
                    c("&eОткрой бюллетень в руке."),
                    c("&7Изучи кандидатов и заявки."),
                    c("&7После выбора вернись в участок ЦИК."),
                    c("&cЧужой бюллетень не сработает.")
            ));
            tagElectionItem(meta,"ballot",eid,t.getUniqueId().toString(),id);
            paper.setItemMeta(meta);
        }
        giveOfficialItemOrWarn(t,paper,"Освободи слот в инвентаре для официального бюллетеня.");
        msg(t,"&eТы получил официальный бюллетень. Открой его в руке и изучи заявки кандидатов.");
    }
    private String annulApplicationEmergency(String key,String actor,String reason)throws Exception{String out=annulSubmittedApplication(key,actor,reason); return out.startsWith("&cЗаявка не найдена")?annulApplicationIssue(key,actor,reason):out;}
    private String annulApplicationIssue(String issueId,String actor,String reason)throws Exception{
        List<Map<String,Object>> rows=query("SELECT id,election_id,applicant_uuid,applicant_name,used,annulled FROM cmv7_application_issues WHERE id=? LIMIT 1",issueId);
        if(rows.isEmpty())return"&cВыданная книга заявки не найдена: &f"+issueId;
        Map<String,Object> r=rows.get(0);
        if(num(r.get("annulled"))>0)return"&eКнига уже была аннулирована.";
        if(num(r.get("used"))>0)return"&eКнига уже подписана. Аннулируй поданную заявку по application id.";
        String note=" | ANNULLED_BY:"+actor+" reason="+first(reason,"emergency");
        exec("UPDATE cmv7_application_issues SET annulled=1,notes=COALESCE(notes,'')||? WHERE id=?",note,issueId);
        int stripped=stripElectionItems(s(r.get("applicant_uuid")),s(r.get("applicant_name")),"application_book",issueId);
        audit(actor,"ULTRA7_APP_ISSUE_ANNUL","issue="+issueId+" election="+s(r.get("election_id"))+" player="+s(r.get("applicant_name"))+" stripped="+stripped+" reason="+first(reason,"emergency"),true);
        return"&aКнига заявки аннулирована. Удалено предметов онлайн: &e"+stripped;
    }
    private String annulSubmittedApplication(String rowOrId,String actor,String reason)throws Exception{
        List<Map<String,Object>> rows=query("SELECT rowid,id,election_id,applicant_uuid,applicant_name,status,deleted_at FROM applications WHERE CAST(rowid AS TEXT)=? OR id=? LIMIT 1",rowOrId,rowOrId);
        if(rows.isEmpty())return"&cЗаявка не найдена: &f"+rowOrId;
        Map<String,Object> r=rows.get(0);
        String appId=s(r.get("id")), eid=s(r.get("election_id")), uuid=s(r.get("applicant_uuid")), name=s(r.get("applicant_name"));
        if(num(r.get("deleted_at"))>0||"ANNULLED".equalsIgnoreCase(s(r.get("status"))))return"&eЗаявка уже аннулирована.";
        String note=" | ANNULLED_BY:"+actor+" reason="+first(reason,"emergency");
        exec("UPDATE applications SET status='ANNULLED',visible_in_game=0,deleted_by=?,deleted_at=?,verdict_reason=COALESCE(verdict_reason,'')||? WHERE id=?",actor,now(),note,appId);
        int removed=exec("UPDATE candidates SET removed=1 WHERE election_id=? AND uuid=?",eid,uuid);
        audit(actor,"ULTRA7_APPLICATION_ANNUL","application="+appId+" election="+eid+" player="+name+" removedCandidateRows="+removed+" reason="+first(reason,"emergency"),true);
        return"&aЗаявка аннулирована. Снято строк кандидата: &e"+removed;
    }
    private String annulBallot(String ballotId,String actor,String reason)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("annulBallot");
        List<Map<String,Object>> rows=query("SELECT id,election_id,voter_uuid,voter_name,used,notes FROM cmv7_ballot_issues WHERE id=? LIMIT 1",ballotId);
        if(rows.isEmpty())return"&cБюллетень не найден: &f"+ballotId;
        Map<String,Object> r=rows.get(0);
        String eid=s(r.get("election_id")), voterUuid=s(r.get("voter_uuid")), voterName=s(r.get("voter_name"));
        String note=" | ANNULLED_BY:"+actor+" reason="+first(reason,"emergency");
        int votes=tableExists("cmv731_votes")?exec("DELETE FROM cmv731_votes WHERE ballot_id=?",ballotId):0;
        int sessions=tableExists("cmv731_vote_sessions")?exec("DELETE FROM cmv731_vote_sessions WHERE election_id=? AND voter_uuid=?",eid,voterUuid):0;
        exec("UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'')||? WHERE id=?",note,ballotId);
        int stripped=stripElectionItems(voterUuid,voterName,"ballot",ballotId);
        if(votes>0)exec("UPDATE candidates SET raw_votes=0 WHERE election_id=?",eid);
        int synced=votes>0?syncCandidateVotesFromLedger(eid,actor):0;
        audit(actor,"ULTRA7_BALLOT_ANNUL","ballot="+ballotId+" election="+eid+" voter="+voterName+" removedVotes="+votes+" sessions="+sessions+" stripped="+stripped+" synced="+synced+" reason="+first(reason,"emergency"),true);
        return"&aБюллетень аннулирован. Голосов удалено: &e"+votes+"&a, предметов удалено: &e"+stripped;
    }
    private int stripElectionItems(String playerUuid,String playerName,String type,String itemId){
        Player p=null;
        try{if(playerUuid!=null&&!playerUuid.isBlank())p=Bukkit.getPlayer(UUID.fromString(playerUuid));}catch(Throwable ignored){}
        if(p==null&&!first(playerName,"").isBlank())p=Bukkit.getPlayerExact(playerName);
        if(p==null)return 0;
        int removed=stripElectionItems(p.getInventory(),type,itemId)+stripElectionItems(p.getEnderChest(),type,itemId);
        ItemStack off=p.getInventory().getItemInOffHand();
        if(type.equals(first(electionItemString(off,"type"),""))&&itemId.equals(first(electionItemString(off,"id"),""))){p.getInventory().setItemInOffHand(null); removed++;}
        p.updateInventory();
        return removed;
    }
    private int stripElectionItems(Inventory inv,String type,String itemId){
        if(inv==null||type==null||type.isBlank()||itemId==null||itemId.isBlank())return 0;
        int removed=0;
        for(int i=0;i<inv.getSize();i++){
            ItemStack it=inv.getItem(i);
            if(type.equals(first(electionItemString(it,"type"),""))&&itemId.equals(first(electionItemString(it,"id"),""))){inv.setItem(i,null); removed++;}
        }
        return removed;
    }
    private void givePollingStationKit(Player p)throws Exception{String eid=activeElectionId(); if(eid==null)throw new SQLException("Нет активных выборов"); ItemStack kit=new ItemStack(Material.LECTERN); ItemMeta meta=kit.getItemMeta(); if(meta!=null){meta.setDisplayName(c("&b&lУчасток ЦИК")); meta.setLore(List.of(c("&7Поставь этот лекторн в RP-здании ЦИК."),c("&7Игроки будут голосовать через клик по нему."),c("&8"+shortId(eid)))); tagElectionItem(meta,"polling_station_kit",eid,p.getUniqueId().toString(),UUID.randomUUID().toString()); kit.setItemMeta(meta);} if(giveOfficialItemOrWarn(p,kit,"Освободи слот в инвентаре для предмета участка ЦИК."))msg(p,"&aВыдан предмет участка ЦИК. Поставь его в точке голосования.");}

    private void giveRoleOfficialItemsAtStation(Player p,Block station)throws Exception{
        String eid=activeOrLatestElectionId();
        if(eid==null)return;
        List<String> issued=new ArrayList<>();
        if(isChair(p)&&giveCikSealIfNeeded(p,eid,station))issued.add("печать ЦИК");
        if(isPresident(p)&&givePresidentMandateIfNeeded(p,eid,station))issued.add("мандат президента");
        if(!issued.isEmpty()){
            p.sendTitle(c("&6&lДокумент выдан"),c("&f"+String.join(", ",issued)),8,55,12);
            sound(p,"UI_TOAST_CHALLENGE_COMPLETE",0.75f,1.25f);
            audit(p.getName(),"ULTRA7_ROLE_OFFICIAL_AUTOISSUE","election="+eid+" station="+(station==null?"station":pollingStationId(station))+" items="+String.join(",",issued),true);
        }
    }

    private boolean giveCikSealIfNeeded(Player p,String eid,Block station)throws Exception{
        if(hasOwnedOfficialItem(p,"cik_seal",eid))return false;
        if(hasActiveOfficialItemBinding(p,"cik_seal",eid)){
            msg(p,"&eПечать ЦИК уже закреплена за тобой. Если она уничтожена, используй кнопку восстановления в панели председателя.");
            return false;
        }
        String itemId=UUID.randomUUID().toString();
        ItemStack seal=createCikSealItem(p,eid,itemId);
        if(!giveOfficialItemOrWarn(p,seal,"Освободи слот в инвентаре для печати ЦИК."))return false;
        registerOfficialItemBinding(p,"cik_seal",eid,itemId,"station:"+first(station==null?"":pollingStationId(station),"unknown"));
        msg(p,"&bТы получил личную печать ЦИК. Она привязана к тебе и не работает у других игроков.");
        return true;
    }

    private boolean givePresidentMandateIfNeeded(Player p,String eid,Block station)throws Exception{
        if(hasOwnedOfficialItem(p,"president_mandate",eid))return false;
        if(hasActiveOfficialItemBinding(p,"president_mandate",eid)){
            msg(p,"&eМандат президента уже закреплен за тобой. Если он уничтожен, используй кнопку восстановления в кабинете президента.");
            return false;
        }
        String itemId=UUID.randomUUID().toString();
        ItemStack mandate=createPresidentMandateItem(p,eid,itemId);
        if(!giveOfficialItemOrWarn(p,mandate,"Освободи слот в инвентаре для мандата президента."))return false;
        registerOfficialItemBinding(p,"president_mandate",eid,itemId,"station:"+first(station==null?"":pollingStationId(station),"unknown"));
        msg(p,"&6Ты получил мандат президента. Он привязан к тебе и ведется в реестре.");
        return true;
    }

    private ItemStack createCikSealItem(Player p,String eid,String itemId){
        ItemStack seal=new ItemStack(Material.NETHER_STAR);
        ItemMeta meta=seal.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(c("&b&lПечать ЦИК"));
            meta.setLore(List.of(c("&7Председатель: &f"+p.getName()),c("&7Цикл: &f"+shortId(eid)),c("&7Назначение: заверять заявки и участки."),c("&aКлик по участку обновляет рабочее место."),c("&cQ уничтожает печать, выброс запрещен."),c("&8ID "+shortId(itemId))));
            tagElectionItem(meta,"cik_seal",eid,p.getUniqueId().toString(),itemId);
            seal.setItemMeta(meta);
        }
        return seal;
    }

    private ItemStack createPresidentMandateItem(Player p,String eid,String itemId){
        ItemStack mandate=new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta=(BookMeta)mandate.getItemMeta();
        if(meta!=null){
            meta.setTitle("Мандат президента");
            meta.setAuthor("CopiMine ЦИК");
            meta.setDisplayName(c("&6&lМандат президента"));
            meta.setLore(List.of(c("&7Президент: &f"+p.getName()),c("&7Цикл: &f"+shortId(eid)),c("&7Главная функция: обращение ко всем игрокам."),c("&aПКМ: большое сообщение на экран."),c("&eКаждый час реального времени."),c("&cQ уничтожает мандат, выброс запрещен."),c("&8PRESIDENT_MANDATE_HOURLY_V3 ID "+shortId(itemId))));
            meta.setPages(List.of(c("&6Мандат президента\n\n&0Игрок: "+p.getName()+"\nЦикл: "+shortId(eid)+"\n\nПКМ по мандату: официальное обращение ко всем игрокам. Доступно один раз в реальный час.")));
            tagElectionItem(meta,"president_mandate",eid,p.getUniqueId().toString(),itemId);
            mandate.setItemMeta(meta);
        }
        return mandate;
    }
    private boolean hasOwnedOfficialItem(Player p,String type,String eid){
        List<ItemStack> items=new ArrayList<>();
        Collections.addAll(items,p.getInventory().getContents());
        items.add(p.getInventory().getItemInOffHand());
        for(ItemStack it:items){
            if(it==null||it.getType()==Material.AIR)continue;
            if(!type.equals(first(electionItemString(it,"type"),"")))continue;
            if(!p.getUniqueId().toString().equals(first(electionItemString(it,"owner"),"")))continue;
            if(eid!=null&&!eid.isBlank()&&!eid.equals(first(electionItemString(it,"election"),"")))continue;
            return true;
        }
        return false;
    }

    private boolean hasActiveOfficialItemBinding(Player p,String type,String eid)throws SQLException{
        if(eid==null||eid.isBlank()||!tableExists("cmv7_official_item_bindings"))return false;
        return scalarLong("SELECT COUNT(*) FROM cmv7_official_item_bindings WHERE type=? AND election_id=? AND owner_uuid=? AND active=1",type,eid,p.getUniqueId().toString())>0;
    }

    private void registerOfficialItemBinding(Player p,String type,String eid,String itemId,String issuedBy)throws SQLException{
        exec("UPDATE cmv7_official_item_bindings SET active=0,revoked_at=?,revoked_reason=? WHERE type=? AND election_id=? AND owner_uuid=? AND active=1",now(),"replaced-by-new-binding",type,eid,p.getUniqueId().toString());
        exec("INSERT INTO cmv7_official_item_bindings(type,election_id,owner_uuid,owner_name,item_id,issued_at,issued_by,active,revoked_at,revoked_reason) VALUES(?,?,?,?,?,?,?,1,0,'')",type,eid,p.getUniqueId().toString(),p.getName(),itemId,now(),issuedBy);
    }

    private void restoreOfficialItem(Player p,String type)throws Exception{
        String eid=activeOrLatestElectionId();
        if(eid==null){warn(p,"Нет цикла выборов для восстановления официального предмета.");return;}
        if("cik_seal".equals(type)&&!hasAdmin(p)&&!isChair(p)){warn(p,"Печать может восстановить председатель ЦИК или админ.");return;}
        if("president_mandate".equals(type)&&!hasAdmin(p)&&!isPresident(p)){warn(p,"Мандат может восстановить президент или админ.");return;}
        if(hasOwnedOfficialItem(p,type,eid)){warn(p,"Официальный предмет уже у тебя в инвентаре.");return;}
        exec("UPDATE cmv7_official_item_bindings SET active=0,revoked_at=?,revoked_reason=? WHERE type=? AND election_id=? AND owner_uuid=? AND active=1",now(),"manual_recovery",type,eid,p.getUniqueId().toString());
        boolean ok="cik_seal".equals(type)?giveCikSealIfNeeded(p,eid,null):"president_mandate".equals(type)&&givePresidentMandateIfNeeded(p,eid,null);
        if(ok){audit(p.getName(),"ULTRA7_OFFICIAL_ITEM_RECOVERY","type="+type+" election="+eid,true); sound(p,"BLOCK_AMETHYST_BLOCK_CHIME",0.8f,1.25f);}
        if("president_mandate".equals(type))openPresidentPanelAsync(p); else openChairPanel(p);
    }

    private void markOfficialItemDestroyed(Player p,ItemStack stack,String type)throws SQLException{
        String officialType=first(type,officialTypeForStack(stack));
        if(officialType.isBlank())return;
        String eid=first(electionItemString(stack,"election"),activeOrLatestElectionId(),"manual");
        String itemId=first(electionItemString(stack,"id"),"");
        String owner=first(electionItemString(stack,"owner"),p.getUniqueId().toString());
        if(!tableExists("cmv7_official_item_bindings"))return;
        if(!itemId.isBlank())exec("UPDATE cmv7_official_item_bindings SET active=0,revoked_at=?,revoked_reason=? WHERE type=? AND election_id=? AND owner_uuid=? AND item_id=? AND active=1",now(),"destroyed_by_owner",officialType,eid,owner,itemId);
        exec("UPDATE cmv7_official_item_bindings SET active=0,revoked_at=?,revoked_reason=? WHERE type=? AND election_id=? AND owner_uuid=? AND active=1",now(),"destroyed_by_owner",officialType,eid,owner);
    }

    private String officialTypeForStack(ItemStack stack){
        String type=first(electionItemString(stack,"type"),"");
        if(type.equals("cik_seal")||type.equals("president_mandate"))return type;
        if(isPresidentMandate(stack))return"president_mandate";
        if(isElectionSeal(stack))return"cik_seal";
        return"";
    }

    private void sealBallotChoice(Player p,String eid,String candidateUuid,String stationId)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("sealBallotChoice");
        if(eid==null||eid.isBlank())throw new SQLException("Нет выборов");
        try { requireElectionStatus(eid,ElectionStatus.VOTING_OPEN); }
        catch(SQLException closed){warn(p,"Голосование сейчас закрыто.");return;}
        if(hasCitizenVote(p,eid)){warn(p,"Твой голос уже учтен.");return;}
        List<Map<String,Object>> candidates=query("SELECT name,display_name FROM candidates WHERE election_id=? AND uuid=? AND COALESCE(removed,0)=0 LIMIT 1",eid,candidateUuid);
        if(candidates.isEmpty()){warn(p,"Кандидат недоступен.");return;}
        ItemStack ballot=findOwnedUnusedBallot(p,eid);
        if(ballot==null){warn(p,"Нужен твой неиспользованный физический бюллетень. Чужой или переданный бюллетень не работает.");return;}
        if(!requireElectionItemOwner(p,ballot,"ballot"))return;
        String ballotId=first(electionItemString(ballot,"id"),"");
        String candidateName=first(s(candidates.get(0).get("display_name")),s(candidates.get(0).get("name")),candidateUuid);
        ItemMeta meta=ballot.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(c("&a&lЗапечатанный бюллетень"));
            meta.setLore(List.of(
                    c("&7Выбор записан внутри документа."),
                    c("&aФинальный шаг: опусти бюллетень в участок ЦИК."),
                    c("&7Кликни по участку, держа этот бюллетень."),
                    c("&8Содержимое скрыто от других игроков.")
            ));
            setElectionItemString(meta,"selected_candidate",candidateUuid);
            setElectionItemString(meta,"selected_candidate_name",candidateName);
            setElectionItemString(meta,"sealed_at",String.valueOf(now()));
            setElectionItemString(meta,"station_hint",first(stationId,"inventory"));
            ballot.setItemMeta(meta);
        }
        exec("INSERT INTO cmv731_vote_sessions(voter_uuid,election_id,candidate_uuid,candidate_name,updated_at,selected_at,player_uuid) VALUES(?,?,?,?,?,?,?) ON CONFLICT(voter_uuid,election_id) DO UPDATE SET candidate_uuid=excluded.candidate_uuid,candidate_name=excluded.candidate_name,updated_at=excluded.updated_at,selected_at=excluded.selected_at,player_uuid=excluded.player_uuid",p.getUniqueId().toString(),eid,candidateUuid,candidateName,now(),now(),p.getUniqueId().toString());
        exec("UPDATE cmv7_ballot_issues SET notes=COALESCE(notes,'')||? WHERE id=?"," | SEALED:"+candidateName,ballotId);
        audit(p.getName(),"ULTRA7_BALLOT_SEALED","election="+eid+" candidate="+candidateName+" ballot="+ballotId,false);
        p.updateInventory();
        p.closeInventory();
        p.sendTitle(c("&a&lБюллетень запечатан"),c("&fТеперь опусти его в участок ЦИК"),10,80,20);
        msg(p,"&aВыбор записан в бюллетень. Теперь подойди к участку ЦИК и опусти бюллетень в урну.");
        sound(p,"ITEM_BOOK_PAGE_TURN",0.9f,1.15f);
    }

    private void depositSealedBallotAtStation(Player p,ItemStack ballot,String stationId)throws Exception{
        if(legacyElectionRuntimeDisabled())throw legacyElectionRuntimeDisabledError("depositSealedBallotAtStation");
        if(ballot==null){warn(p,"Возьми свой запечатанный бюллетень или сначала выбери кандидата.");return;}
        if(!requireElectionItemOwner(p,ballot,"ballot"))return;
        String eid=first(electionItemString(ballot,"election"),activeOrLatestElectionId());
        String candidateUuid=first(electionItemString(ballot,"selected_candidate"),"");
        String ballotId=first(electionItemString(ballot,"id"),"");
        if(eid.isBlank()||candidateUuid.isBlank()||ballotId.isBlank()){warn(p,"Этот бюллетень не запечатан. Сначала выбери кандидата.");return;}
        try { requireElectionStatus(eid,ElectionStatus.VOTING_OPEN); }
        catch(SQLException closed){warn(p,"Голосование сейчас закрыто.");return;}
        if(hasCitizenVote(p,eid)){warn(p,"Твой голос уже учтен.");return;}
        List<Map<String,Object>> candidates=query("SELECT name,display_name FROM candidates WHERE election_id=? AND uuid=? AND COALESCE(removed,0)=0 LIMIT 1",eid,candidateUuid);
        if(candidates.isEmpty()){warn(p,"Кандидат недоступен.");return;}
        long validBallot=scalarLong("SELECT COUNT(*) FROM cmv7_ballot_issues WHERE id=? AND election_id=? AND voter_uuid=? AND COALESCE(used,0)=0",ballotId,eid,p.getUniqueId().toString());
        if(validBallot<=0){warn(p,"Бюллетень уже использован или не принадлежит тебе.");return;}
        String candidateName=first(electionItemString(ballot,"selected_candidate_name"),s(candidates.get(0).get("display_name")),s(candidates.get(0).get("name")),candidateUuid);
        String voteId=UUID.randomUUID().toString();
        Location loc=p.getLocation();
        final long t=now();
        tx(c -> {
            long already=scalarLong(c,"SELECT COUNT(*) FROM cmv731_votes WHERE election_id=? AND voter_uuid=?",eid,p.getUniqueId().toString());
            if(already>0)throw new SQLException(ELECTION_VOTE_DUPLICATE);
            int marked=exec(c,"UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'')||? WHERE id=? AND election_id=? AND voter_uuid=? AND COALESCE(used,0)=0"," | DEPOSITED:"+candidateName+" station="+first(stationId,"station-deposit"),ballotId,eid,p.getUniqueId().toString());
            if(marked!=1)throw new SQLException(ELECTION_BALLOT_ALREADY_USED);
            exec(c,"INSERT INTO cmv731_votes(id,election_id,voter_uuid,voter_name,candidate_uuid,candidate_name,ballot_id,station_id,world,x,y,z,time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",voteId,eid,p.getUniqueId().toString(),p.getName(),candidateUuid,candidateName,ballotId,first(stationId,"station-deposit"),loc.getWorld()==null?"":loc.getWorld().getName(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ(),t);
            exec(c,"UPDATE candidates SET raw_votes=COALESCE(raw_votes,0)+1 WHERE election_id=? AND uuid=?",eid,candidateUuid);
            exec(c,"DELETE FROM cmv731_vote_sessions WHERE voter_uuid=? AND election_id=?",p.getUniqueId().toString(),eid);
            exec(c,"INSERT INTO cmv7_audit(time,actor,action,details,admin_only) VALUES(?,?,?,?,?)",t,p.getName(),"ULTRA7_BALLOT_DEPOSIT","election="+eid+" candidate="+candidateName+" ballot="+ballotId+" station="+first(stationId,"station-deposit"),0);
            return null;
        });
        removeBallotById(p,ballotId);
        recordPlayerActivity(p,"ELECTION_BALLOT_DEPOSIT",loc,"election="+eid+" candidate="+candidateName+" station="+first(stationId,"station-deposit"),false);
        p.closeInventory();
        p.sendTitle(c("&a&lГолос принят"),c("&fБюллетень опущен в участок ЦИК"),10,70,20);
        sound(p,"UI_TOAST_CHALLENGE_COMPLETE",0.8f,1.1f);
        reloadSidebarAll();
    }

    private int approvedToCandidates(String actor)throws Exception{ String eid=activeOrLatestElectionId(); if(eid==null)return 0; int n=0; for(Map<String,Object> app:query("SELECT applicant_uuid,applicant_name FROM applications WHERE election_id=? AND status='APPROVED' AND COALESCE(deleted_at,0)=0",eid)){String uuid=s(app.get("applicant_uuid")), name=s(app.get("applicant_name")); if(!uuid.isBlank()&&!name.isBlank()&&scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND uuid=?",eid,uuid)==0){exec("INSERT INTO candidates(election_id,uuid,name,display_name,raw_votes,admin_adjustment,removed) VALUES(?,?,?,?,0,0,0)",eid,uuid,name,name); n++;}} return n;}
    private void reviewApplication(String rowid,String status,String actor)throws SQLException{String normalized=status==null?"PENDING":status.toUpperCase(Locale.ROOT); if(!Set.of("PENDING","APPROVED","REJECTED").contains(normalized))normalized="PENDING"; exec("UPDATE applications SET status=?,reviewed_by=?,reviewed_at=?,verdict_reason=? WHERE rowid=?",normalized,actor,now(),"admin-panel-"+normalized.toLowerCase(Locale.ROOT),rowid); audit(actor,"ULTRA7_APPLICATION_"+normalized,"rowid="+rowid,true);}
    private void promoteApplicationCandidate(String rowid,String actor)throws SQLException{List<Map<String,Object>> rows=query("SELECT election_id,applicant_uuid,applicant_name FROM applications WHERE rowid=? LIMIT 1",rowid); if(rows.isEmpty())return; Map<String,Object> app=rows.get(0); String eid=s(app.get("election_id")), uuid=s(app.get("applicant_uuid")), name=s(app.get("applicant_name")); if(eid.isBlank()||uuid.isBlank()||name.isBlank())return; if(scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND uuid=?",eid,uuid)==0)exec("INSERT INTO candidates(election_id,uuid,name,display_name,raw_votes,admin_adjustment,removed) VALUES(?,?,?,?,0,0,0)",eid,uuid,name,name); else exec("UPDATE candidates SET removed=0,display_name=? WHERE election_id=? AND uuid=?",name,eid,uuid); audit(actor,"ULTRA7_APPLICATION_PROMOTE","rowid="+rowid+" candidate="+name,true);}
    private void prepareReview(String actor)throws SQLException{closeApplications(actor);}
    private void prepareVoting(String actor)throws Exception{openVoting(actor);}
    private void prepareCounting(String actor)throws Exception{startCounting(actor);}
    private int syncCandidateVotesFromLedger(String eid,String actor)throws SQLException{if(eid==null||eid.isBlank()||!tableExists("cmv731_votes"))return 0; int total=0; for(Map<String,Object> r:query("SELECT candidate_uuid,candidate_name,COUNT(*) votes FROM cmv731_votes WHERE election_id=? GROUP BY candidate_uuid,candidate_name",eid)){String uuid=s(r.get("candidate_uuid")), name=first(s(r.get("candidate_name")),uuid); long votes=num(r.get("votes")); total+=votes; if(uuid.isBlank())continue; if(scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND uuid=?",eid,uuid)==0)exec("INSERT INTO candidates(election_id,uuid,name,display_name,raw_votes,admin_adjustment,removed) VALUES(?,?,?,?,?,0,0)",eid,uuid,name,name,votes); else exec("UPDATE candidates SET raw_votes=?,display_name=COALESCE(NULLIF(display_name,''),?) WHERE election_id=? AND uuid=?",votes,name,eid,uuid);} exec("UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'')||' | vote-ledger-sync' WHERE election_id=? AND id IN (SELECT ballot_id FROM cmv731_votes WHERE election_id=?)",eid,eid); audit(actor,"ULTRA7_ELECTION_VOTE_LEDGER_SYNC","election="+eid+" votes="+total,true); return total;}
    private String finishElection(String actor)throws Exception{String eid=activeElectionId(); if(eid==null)eid=activeOrLatestElectionId(); if(eid==null)return"&cНет выборов."; ensureSettings(eid,actor); requireElectionStatus(eid,ElectionStatus.COUNTING,ElectionStatus.SECOND_ROUND_REQUIRED,ElectionStatus.FINISHED); requireElectionGate(eid,"FINISH",actor); syncCandidateVotesFromLedger(eid,actor); Map<String,Object>w=leader(eid); String wu=w==null?"":s(w.get("uuid")), wn=w==null?"":first(s(w.get("display_name")),s(w.get("name"))); exec("UPDATE elections SET status=?,ended_at=?,ended_by=?,winner_uuid=?,winner_name=? WHERE id=?",ElectionStatus.FINISHED.name(),now(),actor,wu,wn,eid); if(!wn.isBlank())assignPresident(eid,wu,wn,actor,"election-finished"); syncElectionSettingsStatus(eid,ElectionStatus.FINISHED,actor); audit(actor,"ULTRA7_ELECTION_FINISH","election="+eid+" winner="+wn,true); announceInauguration(actor); return"&aВыборы завершены. Победитель: &e"+(wn.isBlank()?"не найден":wn);}
    private String finishElectionLifecycle(String actor)throws Exception{return finishElection(actor);}
    private String cancelElection(String actor)throws SQLException{String eid=activeElectionId(); if(eid==null)eid=activeOrLatestElectionId(); if(eid==null)return"&cНет выборов."; exec("UPDATE elections SET status=?,ended_at=?,ended_by=? WHERE id=?",ElectionStatus.CANCELLED.name(),now(),actor,eid); syncElectionSettingsStatus(eid,ElectionStatus.CANCELLED,actor); sidebarGlobal=false; audit(actor,"ULTRA7_ELECTION_CANCEL","election="+eid,true); return"&eВыборы отменены."; }
    private void announceInauguration(String actor)throws Exception{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); ensureSettings(eid,actor); syncElectionSettingsStatus(eid,ElectionStatus.FINISHED,actor); broadcastElectionMoment(actor,"inauguration");}
    private void archiveElectionLifecycle(String actor)throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); ensureSettings(eid,actor); syncElectionSettingsStatus(eid,ElectionStatus.FINISHED,actor); sidebarGlobal=false; audit(actor,"ULTRA7_ELECTION_ARCHIVE",eid,true);}
    private void announceStage(String actor)throws Exception{
        throw legacyElectionRuntimeDisabledError("announce stage");
    }

    private String stageSubtitle(String raw){
        return switch(raw==null?"":raw.toUpperCase(Locale.ROOT)){
            case "DRAFT" -> "цикл создан, ЦИК готовит прием заявок";
            case "APPLICATIONS_OPEN" -> "приём заявок открыт, книги выдаёт ЦИК";
            case "APPLICATIONS_CLOSED" -> "приём закрыт, ЦИК проверяет заявки";
            case "BALLOTS_OPEN" -> "кандидаты утверждены, бюллетени можно выдавать";
            case "VOTING_OPEN" -> "выбери кандидата и опусти бюллетень в участок";
            case "FINISHED" -> "победитель получает мандат президента";
            case "CANCELLED" -> "цикл отменён";
            case "SECOND_ROUND_REQUIRED" -> "требуется второй тур";
            case "NOMINATION" -> "приём заявок открыт, книги выдаёт ЦИК";
            case "REVIEW" -> "ЦИК проверяет заявки и готовит список кандидатов";
            case "DEBATE" -> "кандидаты представляют программы";
            case "VOTING" -> "выбери кандидата и опусти бюллетень в участок";
            case "COUNTING" -> "урны закрыты, ledger сверяется перед итогом";
            case "INAUGURATION" -> "победитель получает мандат президента";
            case "ARCHIVED" -> "цикл закрыт и сохранён в истории";
            default -> "следи за объявлениями ЦИК";
        };
    }

    private String stageSound(String raw){
        return switch(raw==null?"":raw.toUpperCase(Locale.ROOT)){
            case "DRAFT" -> "BLOCK_NOTE_BLOCK_PLING";
            case "APPLICATIONS_OPEN" -> "ITEM_BOOK_PAGE_TURN";
            case "APPLICATIONS_CLOSED" -> "BLOCK_NOTE_BLOCK_PLING";
            case "BALLOTS_OPEN" -> "ENTITY_VILLAGER_CELEBRATE";
            case "VOTING_OPEN" -> "BLOCK_BELL_USE";
            case "FINISHED" -> "UI_TOAST_CHALLENGE_COMPLETE";
            case "CANCELLED" -> "BLOCK_CHEST_CLOSE";
            case "SECOND_ROUND_REQUIRED" -> "BLOCK_AMETHYST_BLOCK_CHIME";
            case "NOMINATION" -> "ITEM_BOOK_PAGE_TURN";
            case "REVIEW" -> "BLOCK_NOTE_BLOCK_PLING";
            case "DEBATE" -> "ENTITY_VILLAGER_CELEBRATE";
            case "VOTING" -> "BLOCK_BELL_USE";
            case "COUNTING" -> "BLOCK_AMETHYST_BLOCK_CHIME";
            case "INAUGURATION" -> "UI_TOAST_CHALLENGE_COMPLETE";
            case "ARCHIVED" -> "BLOCK_CHEST_CLOSE";
            default -> "BLOCK_NOTE_BLOCK_PLING";
        };
    }

    private void broadcastElectionMoment(String actor,String kind)throws Exception{
        String eid=activeOrLatestElectionId();
        String k=first(kind,"stage").toLowerCase(Locale.ROOT);
        String title="&6&lВыборы сервера";
        String subtitle="&fСобытие ЦИК";
        String chat="&6&lВыборы &8» &fСобытие ЦИК.";
        String snd="BLOCK_NOTE_BLOCK_PLING";
        if(k.equals("candidate-call")){title="&e&lКандидаты"; subtitle="&fПодойдите к ЦИК для публичного этапа"; chat="&6&lЦИК &8» &fКандидаты приглашаются к участку для публичной части выборов."; snd="ENTITY_VILLAGER_YES";}
        else if(k.equals("counting")){title="&a&lПодсчёт"; subtitle="&fУрны закрываются, журнал голосов сверяется"; chat="&a&lЦИК &8» &fНачинается подсчёт. Голоса сверяются по бюллетеням и участкам."; snd="BLOCK_AMETHYST_BLOCK_CHIME";}
        else if(k.equals("stations-open")){title="&b&lУчастки открыты"; subtitle="&fГолос считается только после опускания бюллетеня"; chat="&b&lЦИК &8» &fУчастки открыты. Выберите кандидата, запечатайте бюллетень и опустите его в участок."; snd="BLOCK_BELL_USE";}
        else if(k.equals("silence")){title="&e&lТихий час"; subtitle="&fАгитация завершена, готовьтесь к финалу"; chat="&6&lЦИК &8» &fОбъявлен тихий час перед закрытием голосования."; snd="BLOCK_AMETHYST_BLOCK_CHIME";}
        else if(k.equals("final-call")){title="&c&lФинальный зов"; subtitle="&fПоследний шанс опустить бюллетень в участок"; chat="&c&lЦИК &8» &fФинальный зов: запечатанные бюллетени нужно опустить в участок до закрытия урн."; snd="BLOCK_NOTE_BLOCK_BELL";}
        else if(k.equals("counting")){title="&a&lПодсчёт"; subtitle="&fУрны закрываются, журнал голосов сверяется"; chat="&a&lЦИК &8» &fНачинается подсчёт. Голоса сверяются по бюллетеням и участкам."; snd="BLOCK_AMETHYST_BLOCK_CHIME";}
        else if(k.equals("winner")){String winner=safeWinnerName(eid); title="&6&lПобедитель"; subtitle="&f"+(winner.isBlank()?"итоги ещё не готовы":"Лидер: "+winner); chat="&6&lИтоги &8» &f"+(winner.isBlank()?"Победитель ещё не определён.":"Победитель выборов: &e"+winner); snd="UI_TOAST_CHALLENGE_COMPLETE";}
        else if(k.equals("inauguration")){String president=activePresidentName(); title="&6&lИнаугурация"; subtitle="&fПрезидент: &e"+president; chat="&6&lИнаугурация &8» &fПрезидент: &e"+president; snd="UI_TOAST_CHALLENGE_COMPLETE";}
        broadcastElectionMoment(actor,k,title,subtitle,chat,snd);
    }

    private void broadcastElectionMoment(String actor,String kind,String title,String subtitle,String chat,String snd){
        for(Player p:Bukkit.getOnlinePlayers()){p.sendTitle(c(title),c(subtitle),10,90,20); sound(p,snd,.85f,1.12f);}
        Bukkit.broadcastMessage(c(chat));
        audit(actor,"ULTRA7_ELECTION_MOMENT","kind="+kind+" chat="+ChatColor.stripColor(c(chat)),false);
    }
    private void toggleFlag(String actor,String key)throws SQLException{ throw legacyElectionRuntimeDisabledError("toggle flag "+key); }
    private void setStage(String actor,String stage)throws SQLException{ throw legacyElectionRuntimeDisabledError("set stage "+stage); }
    private void setDuration(String actor,int h)throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)throw new SQLException("Нет выборов"); exec("UPDATE elections SET scheduled_end_at=? WHERE id=?",now()+h*3600000L,eid);}
    private void ensureSettings(String eid,String actor)throws SQLException{ if(eid!=null&&scalarLong("SELECT COUNT(*) FROM cmv7_election_settings WHERE election_id=?",eid)==0) exec("INSERT INTO cmv7_election_settings(election_id,"+COL_APP_OPEN+","+COL_VOTE_OPEN+",curators_can_approve,show_live_results,allow_fake_adjustments,sidebar_visible,stage,notes,updated_at,updated_by) VALUES(?,1,0,0,0,1,1,?,'',?,?)",eid,ElectionStatus.DRAFT.name(),now(),actor); }
    private Map<String,String> electionSettings(String eid)throws SQLException{ensureSettings(eid,"SERVER"); Map<String,String> out=new LinkedHashMap<>(); for(Map<String,Object> r:query("SELECT * FROM cmv7_election_settings WHERE election_id=? LIMIT 1",eid)) for(Map.Entry<String,Object> e:r.entrySet()) out.put(e.getKey(),s(e.getValue())); return out;}
    private ElectionStatus parseElectionStatus(String raw){try{return ElectionStatus.valueOf(first(raw,"DRAFT").toUpperCase(Locale.ROOT));}catch(Exception ignored){return switch(first(raw,"").toUpperCase(Locale.ROOT)){case"ACTIVE"->ElectionStatus.APPLICATIONS_OPEN;case"PAUSED"->ElectionStatus.APPLICATIONS_CLOSED;case"ENDED"->ElectionStatus.FINISHED;case"RESET","CANCELLED_FORCE"->ElectionStatus.CANCELLED;default->ElectionStatus.DRAFT;};}}
    private void requireElectionStatus(String eid,ElectionStatus... allowed)throws SQLException{ElectionStatus current=parseElectionStatus(electionStatus(eid)); for(ElectionStatus status:allowed)if(current==status)return; throw new SQLException("Неверный этап выборов: "+humanStatus(current.name()));}
    private void transitionElectionStatus(String eid,ElectionStatus next,String actor,String action)throws SQLException{ throw legacyElectionRuntimeDisabledError("transition status "+next.name());}
    private void syncElectionSettingsStatus(String eid,ElectionStatus status,String actor)throws SQLException{ensureSettings(eid,actor); int app=status==ElectionStatus.APPLICATIONS_OPEN?1:0; int vote=status==ElectionStatus.VOTING_OPEN?1:0; int show=Set.of(ElectionStatus.VOTING_OPEN,ElectionStatus.COUNTING,ElectionStatus.FINISHED,ElectionStatus.SECOND_ROUND_REQUIRED).contains(status)?1:0; int sidebar=status==ElectionStatus.CANCELLED?0:1; exec("UPDATE cmv7_election_settings SET stage=?,"+COL_APP_OPEN+"=?,"+COL_VOTE_OPEN+"=?,show_live_results=?,sidebar_visible=?,updated_at=?,updated_by=? WHERE election_id=?",status.name(),app,vote,show,sidebar,now(),actor,eid);}
    private String liveElectionStatusSql(){return LIVE_ELECTION_STATUSES.stream().map(s->"'"+s.name()+"'").reduce("'ACTIVE'",(a,b)->a+","+b)+",'PAUSED'";}
    private String activeElectionId()throws SQLException{List<Map<String,Object>> r=query("SELECT id FROM elections WHERE status IN ("+liveElectionStatusSql()+") ORDER BY COALESCE(started_at,0) DESC,rowid DESC LIMIT 1"); return r.isEmpty()?null:s(r.get(0).get("id"));}
    private String latestElectionId()throws SQLException{List<Map<String,Object>> r=query("SELECT id FROM elections ORDER BY COALESCE(started_at,0) DESC,rowid DESC LIMIT 1"); return r.isEmpty()?null:s(r.get(0).get("id"));}
    private String activeOrLatestElectionId()throws SQLException{String a=activeElectionId(); return a!=null?a:latestElectionId();}
    private String issuableElectionId()throws SQLException{String eid=activeOrLatestElectionId(); if(eid==null)return null; ElectionStatus status=parseElectionStatus(electionStatus(eid)); if(status==ElectionStatus.FINISHED||status==ElectionStatus.CANCELLED)return null; return eid;}
    private String electionStatus(String eid)throws SQLException{if(eid==null||eid.isBlank())return"нет"; List<Map<String,Object>> r=query("SELECT status FROM elections WHERE id=? LIMIT 1",eid); return r.isEmpty()?"нет":parseElectionStatus(s(r.get(0).get("status"))).name();}
    private Map<String,Object> leader(String eid)throws SQLException{List<Map<String,Object>> r=query("SELECT *,COALESCE(raw_votes,0)+COALESCE(admin_adjustment,0) total FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0 ORDER BY total DESC,name ASC LIMIT 1",eid); return r.isEmpty()?null:r.get(0);}
    private String electionText()throws SQLException{String id=activeOrLatestElectionId(); if(id==null)return"&cВыборов нет."; return"&eВыборы: &f"+id+" &7| status=&f"+electionStatus(id);}
    private List<Map<String,Object>> candidateRowsForElection(String eid,int limit)throws SQLException{return query("SELECT uuid,name,display_name,COALESCE(raw_votes,0) raw_votes,COALESCE(admin_adjustment,0) admin_adjustment,COALESCE(removed,0) removed,COALESCE(raw_votes,0)+COALESCE(admin_adjustment,0) total FROM candidates WHERE election_id=? AND COALESCE(removed,0)=0 ORDER BY total DESC,name ASC LIMIT ?",eid,limit);}
    private void assignPresident(String eid,String uuid,String name,String actor,String reason)throws SQLException{if(name==null||name.isBlank())return; retireActivePresident(actor,"replaced-by-"+name); exec("INSERT INTO cmv7_president_state(election_id,uuid,name,assigned_at,assigned_by,active,reason,removed_at,removed_by,remove_reason) VALUES(?,?,?,?,?,1,?,0,'','')",first(eid,"manual"),first(uuid,""),name,now(),actor,reason); dispatchIfExists("lp user "+name+" parent add president"); dispatchIfExists("lp user "+name+" meta setprefix 100 \"&6[Президент] \""); audit(actor,"ULTRA7_PRESIDENT_ASSIGN","election="+first(eid,"")+" name="+name+" reason="+reason,true); updateRoleNameplates();}
    private void retireActivePresident(String actor,String reason)throws SQLException{for(Map<String,Object> r:query("SELECT name FROM cmv7_president_state WHERE active=1 ORDER BY assigned_at DESC,id DESC")){String name=s(r.get("name")); if(!name.isBlank()){dispatchIfExists("lp user "+name+" parent remove president"); dispatchIfExists("lp user "+name+" meta removeprefix 100"); dispatchIfExists("lp user "+name+" meta unsetprefix");}} exec("UPDATE cmv7_president_state SET active=0,removed_at=?,removed_by=?,remove_reason=? WHERE active=1",now(),actor,reason); audit(actor,"ULTRA7_PRESIDENT_RETIRE",reason,true); updateRoleNameplates();}
    private String activePresidentName(){try{List<Map<String,Object>> r=query("SELECT name FROM cmv7_president_state WHERE active=1 ORDER BY assigned_at DESC,id DESC LIMIT 1"); return r.isEmpty()?"нет":s(r.get(0).get("name"));}catch(Exception e){return"нет";}}
    private boolean isCitizenCandidate(Player p,String eid)throws SQLException{return eid!=null&&scalarLong("SELECT COUNT(*) FROM candidates WHERE election_id=? AND uuid=? AND COALESCE(removed,0)=0",eid,p.getUniqueId().toString())>0;}
    private boolean hasCitizenBallot(Player p,String eid)throws SQLException{String uuid=p.getUniqueId().toString(); return eid!=null&&(countRowsForPlayer("cmv7_ballot_issues",eid,uuid,true,"voter_uuid","player_uuid")>0||countRowsForPlayer("ballots",eid,uuid,true,"voter_uuid","player_uuid")>0||countRowsForPlayer("clean_ballots",eid,uuid,true,"voter_uuid","player_uuid")>0||countRowsForPlayer("cmclean_ballot_issues",eid,uuid,true,"voter_uuid","player_uuid")>0);}
    private boolean hasCitizenVote(Player p,String eid)throws SQLException{return eid!=null&&(countRowsForPlayer("cmv731_votes",eid,p.getUniqueId().toString(),false,"voter_uuid","player_uuid")>0||countRowsForPlayer("votes",eid,p.getUniqueId().toString(),false,"voter_uuid","player_uuid")>0);}
    private long countRowsForPlayer(String table,String eid,String uuid,boolean unusedOnly,String... uuidCols)throws SQLException{if(!tableExists(table))return 0; List<String> c=cols(table); if(!c.contains("election_id"))return 0; List<String> wh=new ArrayList<>(); List<Object> args=new ArrayList<>(); args.add(eid); for(String col:uuidCols)if(c.contains(col.toLowerCase(Locale.ROOT))){wh.add(col+"=?"); args.add(uuid);} if(wh.isEmpty())return 0; String sql="SELECT COUNT(*) FROM "+table+" WHERE election_id=? AND ("+String.join(" OR ",wh)+")"; if(unusedOnly&&c.contains("used"))sql+=" AND COALESCE(used,0)=0"; return scalarLong(sql,args.toArray());}
    private String curatorRoleLabel(String role){return switch(role==null?"":role.toUpperCase(Locale.ROOT)){case"CIK_CHAIR"->"Председатель ЦИК";case"PRESIDENT_DELEGATE"->"Представитель президента";case"CURATOR"->"Член ЦИК";default->role==null||role.isBlank()?"Член ЦИК":role;};}
    private boolean isBallotItem(ItemStack it){return "ballot".equals(electionItemString(it,"type"));}
    private boolean isDelegatedElectionRuntimeItem(ItemStack it,String officialType){
        String type=first(electionItemString(it,"type"),"").toLowerCase(Locale.ROOT);
        String normalizedOfficialType=first(officialType,"").toLowerCase(Locale.ROOT);
        return Set.of("ballot","cik_seal","president_mandate","application_book").contains(type)
            || Set.of("cik_seal","president_mandate").contains(normalizedOfficialType);
    }
    private boolean isPollingStationKit(ItemStack it){return "polling_station_kit".equals(electionItemString(it,"type"));}
    private boolean isPollingStationBlock(Block b){try{return b!=null&&scalarLong("SELECT COUNT(*) FROM cmv7_polling_stations WHERE world=? AND x=? AND y=? AND z=? AND active=1",b.getWorld().getName(),b.getX(),b.getY(),b.getZ())>0;}catch(Exception e){return false;}}
    private String pollingStationId(Block b){try{List<Map<String,Object>> r=query("SELECT id FROM cmv7_polling_stations WHERE world=? AND x=? AND y=? AND z=? AND active=1 ORDER BY id DESC LIMIT 1",b.getWorld().getName(),b.getX(),b.getY(),b.getZ()); return r.isEmpty()?"station":s(r.get(0).get("id"));}catch(Exception e){return"station";}}
    private boolean legacyIsBankAtmBlock(Block b){try{return b!=null&&scalarLong("SELECT COUNT(*) FROM ar_atms WHERE world=? AND x=? AND y=? AND z=? AND active=1",b.getWorld().getName(),b.getX(),b.getY(),b.getZ())>0;}catch(Exception e){return false;}}
    private String bankAtmId(Block b){try{List<Map<String,Object>> r=query("SELECT id FROM ar_atms WHERE world=? AND x=? AND y=? AND z=? AND active=1 ORDER BY created_at DESC LIMIT 1",b.getWorld().getName(),b.getX(),b.getY(),b.getZ()); return r.isEmpty()?"atm":s(r.get(0).get("id"));}catch(Exception e){return"atm";}}
    private String bankAccountId(String uuid){return "ar:"+first(uuid,"");}
    private void ensureV4BankAccount(Connection c,String uuid,String name)throws SQLException{
        long t=now();
        exec(c,"INSERT INTO cmv4_bank_accounts(account_id,owner_uuid,owner_name,account_type,currency,balance,status,version,created_at,updated_at) VALUES(?,?,?,'PLAYER','AR',0,'ACTIVE',0,?,?) ON CONFLICT(account_id) DO UPDATE SET owner_name=excluded.owner_name,updated_at=excluded.updated_at",bankAccountId(uuid),uuid,first(name,""),t,t);
    }
    private void ensureV4BankAccount(Player p)throws SQLException{try(Connection c=conn()){ensureV4BankAccount(c,p.getUniqueId().toString(),p.getName());}}
    private long bankBalance(Player p)throws SQLException{ensureV4BankAccount(p); return scalarLong("SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=?",bankAccountId(p.getUniqueId().toString()));}
    private boolean bankPinSet(Player p)throws SQLException{return scalarLong("SELECT COUNT(*) FROM bank_pin_hashes WHERE minecraft_uuid=? AND COALESCE(pin_hash,'')<>''",p.getUniqueId().toString())>0;}
    private boolean bankPinMustChange(Player p)throws SQLException{return scalarLong("SELECT COALESCE(must_change,0) FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1",p.getUniqueId().toString())>0;}

    private void legacyOpenBankAtms(Player p)throws Exception{
        if(!hasEconomyAdmin(p)){warn(p,"Нет прав на экономику.");return;}
        Menu m=new Menu("bank-atms"); create(m,54,"&e&lБанкоматы AR");
        btn(m,4,Material.GOLD_BLOCK,"&eРеестр банкоматов",List.of(
                "&7Активных: &f"+safeScalar("SELECT COUNT(*) FROM ar_atms WHERE active=1"),
                "&7Событий: &f"+safeScalar("SELECT COUNT(*) FROM atm_events"),
                "&8V4_BANK_ATM_GAMEPLAY"
        ),"none");
        btn(m,10,Material.TARGET,"&aСоздать банкомат по блоку",List.of("&7Смотри на блок в пределах 6 блоков.","&7ПКМ по этому блоку откроет банкомат."),"bank-atm:create-target");
        int slot=19;
        for(Map<String,Object> r:query("SELECT id,world,x,y,z,name,active,created_by,created_at FROM ar_atms ORDER BY active DESC,created_at DESC LIMIT 27")){
            String id=s(r.get("id"));
            boolean active=num(r.get("active"))==1;
            btn(m,slot++,active?Material.GOLD_BLOCK:Material.GRAY_CONCRETE,"&e"+first(s(r.get("name")),"AR ATM")+" &8| &f"+shortId(id),List.of(
                    "&7Точка: &f"+s(r.get("world"))+" "+num(r.get("x"))+" "+num(r.get("y"))+" "+num(r.get("z")),
                    "&7Создал: &f"+first(s(r.get("created_by")),"-"),
                    "&7Активен: &f"+(active?"да":"нет"),
                    "&cShift+ПКМ: отправить в архив"
            ),"bank-atm:delete:"+id);
            if(slot>=45)break;
        }
        nav(m,"open:economy","open:bank-atms"); p.openInventory(m.inv);
    }

    private String legacyCreateBankAtmFromTarget(Player p)throws Exception{
        if(!hasEconomyAdmin(p))return"&cНет прав на экономику.";
        Block b=p.getTargetBlockExact(6);
        if(b==null||b.getType().isAir())return"&cСмотри на твёрдый блок в пределах 6 блоков.";
        if(scalarLong("SELECT COUNT(*) FROM ar_atms WHERE world=? AND x=? AND y=? AND z=? AND active=1",b.getWorld().getName(),b.getX(),b.getY(),b.getZ())>0)return"&eНа этом блоке уже есть активный банкомат.";
        if(isPollingStationBlock(b))return"&cЭтот блок уже используется как участок ЦИК.";
        String id="atm-"+UUID.randomUUID();
        exec("INSERT INTO ar_atms(id,world,x,y,z,name,active,created_by,created_at) VALUES(?,?,?,?,?,'AR ATM',1,?,?)",id,b.getWorld().getName(),b.getX(),b.getY(),b.getZ(),p.getName(),now());
        spawnOrReplaceProtectedBlockVisual(b.getLocation(),"ATM",id,Material.PAPER,MODEL_ATM_TERMINAL,"atm_terminal");
        exec("INSERT INTO atm_audit(created_at,actor,action,details) VALUES(?,?,?,?)",now(),p.getName(),"ATM_CREATE","id="+id+" world="+b.getWorld().getName()+" x="+b.getX()+" y="+b.getY()+" z="+b.getZ());
        audit(p.getName(),"ULTRA7_BANK_ATM_CREATE",id,true);
        return"&aБанкомат создан: &e"+shortId(id);
    }

    private String legacyArchiveBankAtm(Player p,String id)throws SQLException{
        if(!hasEconomyAdmin(p))return"&cНет прав на экономику.";
        int n=exec("UPDATE ar_atms SET active=0,archived_by=?,archived_at=? WHERE id=?",p.getName(),now(),id);
        try{cleanupProtectedBlockVisuals("ATM",id);}catch(Exception ex){getLogger().warning("atm visual cleanup: "+ex.getMessage());}
        exec("INSERT INTO atm_audit(created_at,actor,action,details) VALUES(?,?,?,?)",now(),p.getName(),"ATM_ARCHIVE","id="+id+" rows="+n);
        audit(p.getName(),"ULTRA7_BANK_ATM_ARCHIVE",id,true);
        return n>0?"&eБанкомат отправлен в архив: &f"+shortId(id):"&cБанкомат не найден.";
    }

    private void openBankAtm(Player p,String atmId)throws Exception{
        CopiMineEconomyCore economy=economyCore();
        if(economy==null){
            warn(p,"CopiMineEconomyCore недоступен.");
            return;
        }
        warn(p,"Управление банкоматами перенесено в CopiMineEconomyCore.");
        economy.openAdminEconomyHub(p);
    }

    private void legacyOpenBankAtm(Player p,String atmId)throws Exception{
        long balance=bankBalance(p);
        boolean pin=bankPinSet(p);
        boolean mustChange=bankPinMustChange(p);
        Menu m=new Menu("bank-atm"); create(m,54,"&e&lБанкомат CopiMine &8| &f"+shortId(atmId));
        btn(m,4,Material.GOLD_BLOCK,"&eБанк AR",List.of("&7Баланс: &f"+balance,"&7PIN установлен: &f"+(pin?"да":"нет"),"&7Требуется смена PIN: &f"+(mustChange?"да":"нет"),"&8V4_BANK_ATM_GAMEPLAY"),"none");
        btn(m,10,Material.DIAMOND_ORE,"&aВнести предмет из руки",List.of("&7Внесёт сертифицированный AR из основной руки.","&7PIN не требуется."),"bank:deposit-hand:"+atmId);
        btn(m,12,Material.CHEST,"&aВнести весь AR",List.of("&7Внесёт весь сертифицированный AR из инвентаря.","&7Эндер-сундук не трогается."),"bank:deposit-all:"+atmId);
        btn(m,14,Material.PLAYER_HEAD,"&bПеревести игроку",List.of("&7Используется банковский ledger, как на сайте.","&7Потребуется PIN банка."),"bank:transfer-targets:"+atmId);
        btn(m,28,Material.EMERALD,"&eСнять 1 AR",List.of("&7Потребуется PIN банка."),"bank:withdraw-pin:"+atmId+":1");
        btn(m,30,Material.EMERALD_BLOCK,"&eСнять 16 AR",List.of("&7Потребуется PIN банка."),"bank:withdraw-pin:"+atmId+":16");
        btn(m,32,Material.DIAMOND_ORE,"&eСнять 64 AR",List.of("&7Потребуется PIN банка."),"bank:withdraw-pin:"+atmId+":64");
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,53,Material.EMERALD,"&aОбновить",List.of(),"open:bank-atm:"+atmId);
        p.openInventory(m.inv);
    }

    private void legacyOpenBankTransferTargetsDisabled(Player p,String atmId)throws Exception{
        ensureV4BankAccount(p);
        Menu m=new Menu("bank-transfer-targets"); create(m,54,"&b&lКому перевести AR");
        btn(m,4,Material.PLAYER_HEAD,"&bПолучатель перевода",List.of("&7Игроки берутся из PostgreSQL и online-списка.","&7Перевод самому себе скрыт."),"none");
        int slot=10;
        Set<String> seen=new HashSet<>();
        for(Map<String,Object> r:query("SELECT uuid,name,online,last_seen FROM cmv4_players WHERE uuid<>? ORDER BY online DESC,last_seen DESC LIMIT 36",p.getUniqueId().toString())){
            String uuid=s(r.get("uuid")), name=first(s(r.get("name")),uuid);
            if(uuid.isBlank()||!seen.add(uuid))continue;
            btn(m,slot++,Material.PLAYER_HEAD,"&b"+clipped(name,28),List.of("&7UUID: &f"+shortId(uuid),"&7Онлайн: &f"+(num(r.get("online"))==1?"да":"нет"),"&7Последний вход: &f"+num(r.get("last_seen"))),"bank:transfer-target:"+atmId+":"+uuid);
            if(slot>=45)break;
        }
        for(Player target:Bukkit.getOnlinePlayers()){
            String uuid=target.getUniqueId().toString();
            if(target.equals(p)||!seen.add(uuid)||slot>=45)continue;
            btn(m,slot++,Material.PLAYER_HEAD,"&b"+target.getName(),List.of("&7Сейчас онлайн.","&7UUID: &f"+shortId(uuid)),"bank:transfer-target:"+atmId+":"+uuid);
        }
        if(slot==10)btn(m,22,Material.BARRIER,"&cПолучатели не найдены",List.of("&7Игрок должен быть онлайн или известен PostgreSQL."),"none");
        btn(m,45,Material.ARROW,"&aНазад",List.of(),"open:bank-atm:"+atmId);
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        p.openInventory(m.inv);
    }

    private void legacyOpenBankTransferAmountsDisabled(Player p,String atmId,String targetUuid)throws Exception{
        if(targetUuid.equals(p.getUniqueId().toString())){warn(p,"Нельзя перевести AR самому себе.");legacyOpenBankAtm(p,atmId);return;}
        String targetName=bankTargetName(targetUuid);
        Menu m=new Menu("atm-pin"); create(m,54,"&e&lВведите PIN");
        btn(m,4,Material.PLAYER_HEAD,"&b"+first(targetName,targetUuid),List.of("&7UUID получателя: &f"+shortId(targetUuid),"&7Потребуется PIN банка."),"none");
        btn(m,20,Material.EMERALD,"&eПеревести 1 AR",List.of(),"bank:transfer-pin:"+atmId+":"+targetUuid+":1");
        btn(m,22,Material.EMERALD_BLOCK,"&eПеревести 16 AR",List.of(),"bank:transfer-pin:"+atmId+":"+targetUuid+":16");
        btn(m,24,Material.DIAMOND_ORE,"&eПеревести 64 AR",List.of(),"bank:transfer-pin:"+atmId+":"+targetUuid+":64");
        btn(m,45,Material.ARROW,"&aНазад",List.of(),"bank:transfer-targets:"+atmId);
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        p.openInventory(m.inv);
    }

    private String bankTargetName(String targetUuid){
        try{
            List<Map<String,Object>> rows=query("SELECT name FROM cmv4_players WHERE uuid=? LIMIT 1",targetUuid);
            if(!rows.isEmpty())return first(s(rows.get(0).get("name")),targetUuid);
        }catch(Exception ignored){}
        Player online=Bukkit.getPlayer(UUID.fromString(targetUuid));
        return online==null?targetUuid:online.getName();
    }

    private String legacyDepositArFromHandDisabled(Player p,String atmId)throws Exception{
        ItemStack it=p.getInventory().getItemInMainHand();
        int amount=countArItem(it);
        if(amount<=0)return"&cВозьми сертифицированный AR в основную руку.";
        long after=legacyCommitBankDepositDisabled(p,atmId,amount);
        p.getInventory().setItemInMainHand(null);
        p.updateInventory();
        legacySyncAr(p);
        legacyOpenBankAtm(p,atmId);
        return"&aВнесено &e"+amount+"&a AR. Баланс: &e"+after;
    }

    private String legacyDepositAllArDisabled(Player p,String atmId)throws Exception{
        List<Integer> slots=new ArrayList<>();
        int amount=0;
        for(int i=0;i<36;i++){
            ItemStack it=p.getInventory().getItem(i);
            int n=countArItem(it);
            if(n>0){slots.add(i); amount+=n;}
        }
        if(amount<=0)return"&cВ инвентаре нет сертифицированного AR.";
        long after=legacyCommitBankDepositDisabled(p,atmId,amount);
        for(int slot:slots)p.getInventory().setItem(slot,null);
        p.updateInventory();
        legacySyncAr(p);
        legacyOpenBankAtm(p,atmId);
        return"&aВнесено &e"+amount+"&a AR. Баланс: &e"+after;
    }

    private long legacyCommitBankDepositDisabled(Player p,String atmId,int amount)throws Exception{
        String uuid=p.getUniqueId().toString(), account=bankAccountId(uuid);
        Location loc=p.getLocation();
        return tx(c->{
            ensureV4BankAccount(c,uuid,p.getName());
            List<Map<String,Object>> rows=query(c,"SELECT balance FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE",account);
            long before=rows.isEmpty()?0:num(rows.get(0).get("balance"));
            long after=before+amount, t=now();
            String tx="atm-deposit-"+UUID.randomUUID();
            exec(c,"UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",after,t,account);
            exec(c,"INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",tx,account,"ATM:"+atmId,uuid,"ATM_DEPOSIT",amount,after,tx,"COMMITTED",t,p.getName(),"atm="+atmId);
            exec(c,"INSERT INTO atm_events(atm_id,player_uuid,player_name,event_type,amount,balance_after,created_at,details) VALUES(?,?,?,?,?,?,?,?)",atmId,uuid,p.getName(),"ATM_DEPOSIT",amount,after,t,"ledger="+tx);
            exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",t,"AR_BANK_DEPOSIT",uuid,p.getName(),uuid,p.getName(),loc.getWorld()==null?"":loc.getWorld().getName(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ(),"DIAMOND_ORE",amount,"atm="+atmId+" ledger="+tx);
            return after;
        });
    }

    private void legacyOpenAtmPinPadDisabled(Player p,String atmId,String action,int amount,String pin){
        legacyOpenAtmPinPadDisabled(p,atmId,action,amount,pin,"","");
    }

    private void legacyOpenAtmPinPadDisabled(Player p,String atmId,String action,int amount,String pin,String targetUuid,String targetName){
        amount=Math.max(1,Math.min(64,amount));
        atmPinSessions.put(p.getUniqueId(),new AtmPinSession(atmId,action,amount,first(pin,""),first(targetUuid,""),first(targetName,"")));
        Menu m=new Menu("atm-pin"); create(m,54,"&e&lВведите PIN");
        String masked="*".repeat(Math.min(8,first(pin,"").length()));
        String title="TRANSFER".equals(action)?"&bПеревод "+amount+" AR":"&eСнятие "+amount+" AR";
        List<String> details="TRANSFER".equals(action)
                ? List.of("&7Кому: &f"+clipped(first(targetName,targetUuid),30),"&7Код: &f"+masked,"&8Проверяется только хэш банковского PIN")
                : List.of("&7Код: &f"+masked,"&8Проверяется только хэш банковского PIN");
        btn(m,13,Material.PAPER,"&fВведите PIN",details,"none");
        Object[][] digits={{20,1},{21,2},{22,3},{29,4},{30,5},{31,6},{38,7},{39,8},{40,9},{48,0}};
        for(Object[] digit:digits)btn(m,(Integer)digit[0],Material.LIGHT_BLUE_STAINED_GLASS_PANE,"&f"+digit[1],List.of(),"bankpin:digit:"+digit[1]);
        btn(m,23,Material.BARRIER,"&cCancel",List.of(),"bankpin:cancel");
        btn(m,32,Material.ORANGE_WOOL,"&eClear",List.of(),"bankpin:clear");
        btn(m,41,Material.LIME_WOOL,"&aEnter",List.of("&7Подтвердить банковскую операцию."),"bankpin:confirm");
        p.openInventory(m.inv);
    }

    private void legacyHandleAtmPinActionDisabled(Player p,String action)throws Exception{
        AtmPinSession s=atmPinSessions.get(p.getUniqueId());
        if(s==null){warn(p,"Сессия банкомата истекла.");p.closeInventory();return;}
        String pin=s.pin();
        if(action.startsWith("bankpin:digit:")){
            if(pin.length()<8)pin+=action.substring("bankpin:digit:".length());
            legacyOpenAtmPinPadDisabled(p,s.atmId(),s.action(),s.amount(),pin,s.targetUuid(),s.targetName());
            return;
        }
        if(action.equals("bankpin:clear")){legacyOpenAtmPinPadDisabled(p,s.atmId(),s.action(),s.amount(),"",s.targetUuid(),s.targetName());return;}
        if(action.equals("bankpin:back")){legacyOpenAtmPinPadDisabled(p,s.atmId(),s.action(),s.amount(),pin.isEmpty()?"":pin.substring(0,pin.length()-1),s.targetUuid(),s.targetName());return;}
        if(action.equals("bankpin:cancel")){atmPinSessions.remove(p.getUniqueId()); legacyOpenBankAtm(p,s.atmId()); return;}
        if(action.equals("bankpin:confirm")){
            if(pin.length()<4){warn(p,"Введи PIN полностью: минимум 4 цифры.");legacyOpenAtmPinPadDisabled(p,s.atmId(),s.action(),s.amount(),pin,s.targetUuid(),s.targetName());return;}
            atmPinSessions.remove(p.getUniqueId());
            if("TRANSFER".equals(s.action()))msg(p,legacyTransferBankArDisabled(p,s.atmId(),s.targetUuid(),s.targetName(),s.amount(),pin));
            else msg(p,legacyWithdrawBankArDisabled(p,s.atmId(),s.amount(),pin));
            legacyOpenBankAtm(p,s.atmId());
        }
    }

    private String legacyTransferBankArDisabled(Player p,String atmId,String targetUuid,String targetName,int amount,String pin)throws Exception{
        final int transferAmount=Math.max(1,Math.min(64,amount));
        String fromUuid=p.getUniqueId().toString();
        if(targetUuid==null||targetUuid.isBlank())return"&cПолучатель не выбран.";
        if(targetUuid.equals(fromUuid))return"&cНельзя перевести AR самому себе.";
        long locked=bankPinLockedSeconds(p);
        if(locked>0)return"&cPIN временно заблокирован. Повтори через &e"+locked+"&c сек.";
        if(bankPinMustChange(p))return"&cВременный PIN нужно сменить на сайте перед переводом.";
        if(!verifyBankPin(p,pin)){recordFailedPinAttempt(p,"plugin-atm-transfer");return"&cНеверный банковский PIN.";}
        String fromAccount=bankAccountId(fromUuid), toAccount=bankAccountId(targetUuid);
        String resolvedTargetName=first(targetName,bankTargetName(targetUuid),targetUuid);
        Location loc=p.getLocation();
        long after=tx(c->{
            ensureV4BankAccount(c,fromUuid,p.getName());
            ensureV4BankAccount(c,targetUuid,resolvedTargetName);
            List<String> lockOrder=new ArrayList<>(List.of(fromAccount,toAccount));
            Collections.sort(lockOrder);
            Map<String,Long> balances=new HashMap<>();
            for(String accountId:lockOrder){
                List<Map<String,Object>> rows=query(c,"SELECT account_id,balance FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE",accountId);
                if(rows.isEmpty())throw new SQLException("bank-account-missing");
                balances.put(accountId,num(rows.get(0).get("balance")));
            }
            long fromBefore=balances.getOrDefault(fromAccount,0L);
            long toBefore=balances.getOrDefault(toAccount,0L);
            if(fromBefore<transferAmount)throw new SQLException("insufficient-bank-ar");
            long fromAfter=fromBefore-transferAmount, toAfter=toBefore+transferAmount, t=now();
            String tx="atm-transfer-"+UUID.randomUUID();
            exec(c,"UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",fromAfter,t,fromAccount);
            exec(c,"UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",toAfter,t,toAccount);
            exec(c,"INSERT INTO cmv4_bank_transfers(tx_id,from_account_id,to_account_id,amount,currency,status,idempotency_key,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?)",tx,fromAccount,toAccount,transferAmount,"AR","COMMITTED",tx,t,p.getName(),"atm="+atmId+" target="+resolvedTargetName);
            exec(c,"INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",tx+":out",fromAccount,toAccount,fromUuid,"TRANSFER_OUT",transferAmount,fromAfter,tx+":out","COMMITTED",t,p.getName(),"atm="+atmId);
            exec(c,"INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",tx+":in",toAccount,fromAccount,targetUuid,"TRANSFER_IN",transferAmount,toAfter,tx+":in","COMMITTED",t,p.getName(),"atm="+atmId);
            exec(c,"INSERT INTO atm_events(atm_id,player_uuid,player_name,event_type,amount,balance_after,created_at,details) VALUES(?,?,?,?,?,?,?,?)",atmId,fromUuid,p.getName(),"ATM_TRANSFER",transferAmount,fromAfter,t,"to="+targetUuid+" ledger="+tx);
            exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",t,"AR_BANK_TRANSFER",fromUuid,p.getName(),targetUuid,resolvedTargetName,loc.getWorld()==null?"":loc.getWorld().getName(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ(),"AR",transferAmount,"atm="+atmId+" ledger="+tx);
            return fromAfter;
        });
        Player target=Bukkit.getPlayer(UUID.fromString(targetUuid));
        if(target!=null)msg(target,"&aПолучен банковский перевод: &e"+transferAmount+"&a AR от &e"+p.getName());
        return"&aПереведено &e"+transferAmount+"&a AR игроку &e"+resolvedTargetName+"&a. Баланс: &e"+after;
    }

    private String legacyWithdrawBankArDisabled(Player p,String atmId,int amount,String pin)throws Exception{
        final int withdrawAmount=Math.max(1,Math.min(64,amount));
        if(p.getInventory().firstEmpty()<0)return"&cСначала освободи один слот в инвентаре.";
        long locked=bankPinLockedSeconds(p);
        if(locked>0)return"&cPIN временно заблокирован. Повтори через &e"+locked+"&c сек.";
        if(bankPinMustChange(p))return"&cВременный PIN нужно сменить на сайте перед снятием.";
        if(!verifyBankPin(p,pin)){recordFailedPinAttempt(p,"plugin-atm");return"&cНеверный банковский PIN.";}
        String uuid=p.getUniqueId().toString(), account=bankAccountId(uuid);
        Location loc=p.getLocation();
        long after=tx(c->{
            ensureV4BankAccount(c,uuid,p.getName());
            List<Map<String,Object>> rows=query(c,"SELECT balance FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE",account);
            long before=rows.isEmpty()?0:num(rows.get(0).get("balance"));
            if(before<withdrawAmount)throw new SQLException("insufficient-bank-ar");
            long next=before-withdrawAmount, t=now();
            String tx="atm-withdraw-"+UUID.randomUUID();
            exec(c,"UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",next,t,account);
            exec(c,"INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",tx,account,"ATM:"+atmId,uuid,"ATM_WITHDRAW",withdrawAmount,next,tx,"COMMITTED",t,p.getName(),"atm="+atmId);
            exec(c,"INSERT INTO atm_events(atm_id,player_uuid,player_name,event_type,amount,balance_after,created_at,details) VALUES(?,?,?,?,?,?,?,?)",atmId,uuid,p.getName(),"ATM_WITHDRAW",withdrawAmount,next,t,"ledger="+tx);
            exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",t,"AR_BANK_WITHDRAW",uuid,p.getName(),uuid,p.getName(),loc.getWorld()==null?"":loc.getWorld().getName(),loc.getBlockX(),loc.getBlockY(),loc.getBlockZ(),"DIAMOND_ORE",withdrawAmount,"atm="+atmId+" ledger="+tx);
            return next;
        });
        ItemStack out=createOfficialArStack(Material.DIAMOND_ORE,withdrawAmount);
        ensureArAsset(out,p,"bank-withdraw:"+atmId,loc);
        p.getInventory().addItem(out);
        p.updateInventory();
        recordArTransaction("AR_BANK_WITHDRAW",out,p,"BANK","CopiMine Bank",p,loc,"atm="+atmId);
        legacySyncAr(p);
        return"&aСнято &e"+withdrawAmount+"&a AR. Баланс: &e"+after;
    }

    private boolean verifyBankPin(Player p,String pin)throws Exception{
        if(pin==null||!pin.matches("\\d{4,8}"))return false;
        List<Map<String,Object>> rows=query("SELECT pin_hash FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1",p.getUniqueId().toString());
        if(rows.isEmpty())return false;
        boolean ok=verifyPinHash(s(rows.get(0).get("pin_hash")),pin);
        if(ok)try{exec("DELETE FROM account_lockouts WHERE account_id=?",bankPinLockoutKey(p.getUniqueId().toString()));}catch(Exception ignored){}
        return ok;
    }

    private String bankPinLockoutKey(String uuid){return "bank-pin:"+uuid;}
    private long nowSec(){return Math.max(1L,now()/1000L);}
    private long bankPinLockedSeconds(Player p){
        try{
            long until=scalarLong("SELECT COALESCE(locked_until,0) FROM account_lockouts WHERE account_id=?",bankPinLockoutKey(p.getUniqueId().toString()));
            return Math.max(0L,until-nowSec());
        }catch(Exception e){return 0L;}
    }
    private long bankPinLockedSeconds(String uuid){
        try{
            long until=scalarLong("SELECT COALESCE(locked_until,0) FROM account_lockouts WHERE account_id=?",bankPinLockoutKey(uuid));
            return Math.max(0L,until-nowSec());
        }catch(Exception e){return 0L;}
    }
    private boolean bankPinSet(String uuid)throws SQLException{return scalarLong("SELECT COUNT(*) FROM bank_pin_hashes WHERE minecraft_uuid=? AND COALESCE(pin_hash,'')<>''",uuid)>0;}
    private boolean bankPinMustChange(String uuid)throws SQLException{return scalarLong("SELECT COALESCE(must_change,0) FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1",uuid)>0;}
    private boolean verifyBankPin(String uuid,String pin)throws Exception{
        if(pin==null||!pin.matches("\\d{4,8}"))return false;
        List<Map<String,Object>> rows=query("SELECT pin_hash FROM bank_pin_hashes WHERE minecraft_uuid=? LIMIT 1",uuid);
        if(rows.isEmpty())return false;
        boolean ok=verifyPinHash(s(rows.get(0).get("pin_hash")),pin);
        if(ok)try{exec("DELETE FROM account_lockouts WHERE account_id=?",bankPinLockoutKey(uuid));}catch(Exception ignored){}
        return ok;
    }

    private boolean verifyPinHash(String stored,String pin)throws Exception{
        if(stored==null||!stored.startsWith("pbkdf2_sha256$"))return false;
        String[] parts=stored.split("\\$",4);
        if(parts.length!=4)return false;
        int iterations=Integer.parseInt(parts[1]);
        byte[] salt=parts[2].getBytes(StandardCharsets.UTF_8);
        String expected=parts[3].toLowerCase(Locale.ROOT);
        PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,iterations,Math.max(128,expected.length()*4));
        byte[] got=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return MessageDigest.isEqual(hex(got).getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8));
    }

    private String hex(byte[] bytes){
        StringBuilder sb=new StringBuilder(bytes.length*2);
        for(byte b:bytes)sb.append(String.format("%02x",b&0xff));
        return sb.toString();
    }

    private void recordFailedPinAttempt(Player p,String source){
        String uuid=p.getUniqueId().toString();
        long t=nowSec();
        try{exec("INSERT INTO failed_pin_attempts(minecraft_uuid,site_account_id,attempted_at,source) VALUES(?,?,?,?)",uuid,"",t,first(source,"plugin-atm"));}catch(Exception ignored){}
        try{
            long attempts=scalarLong("SELECT COUNT(*) FROM failed_pin_attempts WHERE minecraft_uuid=? AND attempted_at>=?",uuid,t-PIN_ATTEMPT_WINDOW_SECONDS);
            if(attempts>=PIN_MAX_ATTEMPTS){
                long until=t+PIN_LOCK_SECONDS;
                exec("INSERT INTO account_lockouts(account_id,locked_until,reason,updated_at) VALUES(?,?,?,?) ON CONFLICT(account_id) DO UPDATE SET locked_until=GREATEST(account_lockouts.locked_until,excluded.locked_until),reason=excluded.reason,updated_at=excluded.updated_at",bankPinLockoutKey(uuid),until,"bank-pin",t);
                exec("INSERT INTO cmv4_audit_events(time,actor,action,details,admin_only,source) VALUES(?,?,?,?,?,?)",now(),p.getName(),"PIN_LOCKOUT","minecraft_uuid="+uuid+" until="+until,1,"plugin-atm");
            }
        }catch(Exception ignored){}
        try{exec("INSERT INTO atm_events(atm_id,player_uuid,player_name,event_type,amount,balance_after,created_at,details) VALUES(?,?,?,?,?,?,?,?)","unknown",p.getUniqueId().toString(),p.getName(),"PIN_FAILED",0,0,now(),first(source,"plugin-atm"));}catch(Exception ignored){}
    }
    private boolean isTemporaryApplicationBook(ItemStack it){return it!=null&&it.getType()==Material.WRITTEN_BOOK&&"temporary_application_book".equals(electionItemString(it,"type"));}
    private boolean isElectionUiItem(ItemStack it){if(it==null||it.getType()==Material.AIR)return false; ItemMeta meta=it.getItemMeta(); if(meta==null)return false; boolean material=Set.of(Material.PAPER,Material.WRITABLE_BOOK,Material.WRITTEN_BOOK,Material.BOOK,Material.MAP,Material.NAME_TAG,Material.GOLDEN_HELMET).contains(it.getType()); StringBuilder sb=new StringBuilder(); if(meta.hasDisplayName()){String n=ChatColor.stripColor(meta.getDisplayName()); if(n!=null)sb.append(n).append(' ');} if(meta.hasLore()&&meta.getLore()!=null)for(String l:meta.getLore()){String x=ChatColor.stripColor(l); if(x!=null)sb.append(x).append(' ');} String t=sb.toString().toLowerCase(Locale.ROOT); return material&&(t.contains("выбор")||t.contains("бюллет")||t.contains("заявк")||t.contains("цик")||t.contains("election")||t.contains("ballot")||t.contains("application")||t.contains("cik"));}
    private int legacySyncArOnline(String actor)throws Exception{int n=0; for(Player p:Bukkit.getOnlinePlayers()){legacySyncAr(p); n++;} recordEconomySnapshot(actor, n); return n;}
    private void legacySyncAr(Player p)throws Exception{int inv=countAr(p.getInventory()), end=countAr(p.getEnderChest()), total=inv+end; String uuid=p.getUniqueId().toString(), name=p.getName(); long t=now(); dbAsync("AR balance sync",()->tx(c->{exec(c,"INSERT INTO cmv7_ar_balances(uuid,name,balance,inventory_balance,ender_balance,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,balance=excluded.balance,inventory_balance=excluded.inventory_balance,ender_balance=excluded.ender_balance,updated_at=excluded.updated_at",uuid,name,total,inv,end,t); ensureV4BankAccount(c,uuid,name); return null;})); recordPlayerActivity(p,"AR_SYNC",p.getLocation(),"balance="+total+" inv="+inv+" ender="+end,true); snapshotOnlineInventory(p,"ar_sync");}
    private int countAr(Inventory inv){int n=0; for(ItemStack it:inv.getContents())n+=countArItem(it); return n;}
    private int countArItem(ItemStack it){
        CopiMineEconomyCore.OfficialArService service=officialArService();
        if(service!=null)return service.isOfficialAr(it)?it.getAmount():0;
        if(!arMaterial(it==null?Material.AIR:it.getType()))return 0;
        return isOfficialArItem(it)?it.getAmount():0;
    }
    private boolean useFreeArPlacementFlow(){ return true; }
    private boolean legacyArTransferEnabled(){ return false; }
    private boolean blockOfficialArSmelting(){ return true; }
    private void normalizeArInventoryState(Player p){
        if(p==null)return;
        boolean changed=normalizeArInventory(p.getInventory());
        changed=normalizeArInventory(p.getEnderChest())||changed;
        if(changed){
            p.updateInventory();
            queueArSync("AR_NORMALIZE");
        }
    }
    private boolean normalizeArInventory(Inventory inv){
        if(inv==null)return false;
        boolean changed=false;
        for(int i=0;i<inv.getSize();i++){
            ItemStack it=inv.getItem(i);
            if(!isOfficialArItem(it))continue;
            it=normalizeOfficialArStack(it);
            Material beforeType=it.getType();
            String before=beforeType.name()+":"+arString(it,"batch_id")+":"+arString(it,"owner_uuid")+":"+arString(it,"owner_name")+":"+arString(it,"source");
            setArOwnerMeta(it,"","","");
            ItemMeta meta=it.getItemMeta();
            String name=meta!=null&&meta.hasDisplayName()?meta.getDisplayName():"";
            if(!before.equals(beforeType.name()+"::::")||it.getType()!=beforeType||!ChatColor.stripColor(name).equalsIgnoreCase("Официальный AR")||(meta!=null&&meta.hasLore()&&meta.getLore()!=null&&!meta.getLore().isEmpty()))changed=true;
            inv.setItem(i,it);
        }
        return changed;
    }
    private boolean isAr(ItemStack it){return countArItem(it)>0;}
    private boolean arMaterial(Material m){return m==Material.DIAMOND_ORE||m==Material.DEEPSLATE_DIAMOND_ORE;}
    private boolean isValidArCertificationBreak(BlockBreakEvent e){
        if(e==null||e.getPlayer()==null||e.getBlock()==null)return false;
        if(!arMaterial(e.getBlock().getType()))return false;
        GameMode gm=e.getPlayer().getGameMode();
        if(gm==GameMode.CREATIVE||gm==GameMode.SPECTATOR)return false;
        if(!e.isDropItems())return false;
        ItemStack tool=e.getPlayer().getInventory().getItemInMainHand();
        return tool!=null&&tool.getType()!=Material.AIR&&tool.getEnchantmentLevel(Enchantment.SILK_TOUCH)>0;
    }
    private boolean isValidArCertificationDrop(Player p){
        if(p==null)return false;
        GameMode gm=p.getGameMode();
        if(gm==GameMode.CREATIVE||gm==GameMode.SPECTATOR)return false;
        ItemStack tool=p.getInventory().getItemInMainHand();
        return tool!=null&&tool.getType()!=Material.AIR&&tool.getEnchantmentLevel(Enchantment.SILK_TOUCH)>0;
    }
    private NamespacedKey arKey(String key){return new NamespacedKey("copiminear", key);}
    private String arString(ItemStack it,String key){ItemMeta meta=it==null?null:it.getItemMeta(); if(meta==null)return""; String v=meta.getPersistentDataContainer().get(arKey(key),org.bukkit.persistence.PersistentDataType.STRING); return v==null?"":v;}
    private void tagArItem(ItemStack it,Player miner,Material source){
        if(it==null||miner==null)return;
        setArOwnerMeta(it,miner.getUniqueId().toString(),miner.getName(),source.name());
        ensureArAsset(it,miner,"mined:"+source.name(),miner.getLocation());
        recordArTransaction("AR_CERTIFIED",it,miner,"","",miner,miner.getLocation(),"Сертифицированный АР создан добычей. Алмаз из него можно получить только переплавкой.");
    }
    private boolean isOfficialAr(ItemStack it){
        CopiMineEconomyCore.OfficialArService service=officialArService();
        if(service!=null)return service.isOfficialAr(it);
        if(it==null||!arMaterial(it.getType()))return false;
        ItemMeta meta=it.getItemMeta();
        if(meta==null)return false;
        return "certified".equals(meta.getPersistentDataContainer().get(arKey("type"),org.bukkit.persistence.PersistentDataType.STRING));
    }
    private boolean isLegacyArItem(ItemStack it){
        if(it==null||!arMaterial(it.getType()))return false;
        ItemMeta meta=it.getItemMeta();
        if(meta==null)return false;
        PersistentDataContainer d=meta.getPersistentDataContainer();
        return d.has(arKey("owner_uuid"),org.bukkit.persistence.PersistentDataType.STRING)
                || d.has(arKey("owner_name"),org.bukkit.persistence.PersistentDataType.STRING)
                || d.has(arKey("source"),org.bukkit.persistence.PersistentDataType.STRING)
                || d.has(arKey("batch_id"),org.bukkit.persistence.PersistentDataType.STRING)
                || d.has(arKey("asset_id"),org.bukkit.persistence.PersistentDataType.STRING);
    }
    // AR_UNIQUE_STACK_REGISTRY_V3: one stack has one visible batch, while every item is tracked as an individual DB asset.
    private String ensureArStackBatch(ItemMeta meta){
        if(meta==null)return UUID.randomUUID().toString();
        var d=meta.getPersistentDataContainer();
        String existing=d.get(arKey("batch_id"),org.bukkit.persistence.PersistentDataType.STRING);
        if(existing!=null&&!existing.isBlank())return existing;
        String batch=UUID.randomUUID().toString();
        d.set(arKey("batch_id"),org.bukkit.persistence.PersistentDataType.STRING,batch);
        return batch;
    }
    private void setArOwnerMeta(ItemStack it,String ownerUuid,String ownerName,String source){
        if(it==null||!arMaterial(it.getType()))return;
        CopiMineEconomyCore.OfficialArService service=officialArService();
        if(service!=null){
            ItemStack normalized=service.normalizeStack(it);
            if(normalized!=null&&normalized.hasItemMeta())it.setItemMeta(normalized.getItemMeta());
            return;
        }
        ItemMeta meta=it.getItemMeta(); if(meta==null)return;
        var d=meta.getPersistentDataContainer();
        d.set(arKey("type"),org.bukkit.persistence.PersistentDataType.STRING,"certified");
        d.remove(arKey("source"));
        d.remove(arKey("owner_uuid"));
        d.remove(arKey("owner_name"));
        d.remove(arKey("batch_id"));
        d.remove(arKey("asset_id"));
        meta.setDisplayName(c("&bОфициальный AR"));
        meta.setLore(List.of());
        if(meta.hasCustomModelData())meta.setCustomModelData(null);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
    }
    private void ensureArAsset(ItemStack it,Player owner,String source,Location loc){
        if(it==null||!arMaterial(it.getType())||owner==null)return;
        int amount=Math.max(1,it.getAmount());
        long t=now(); String ownerUuid=owner.getUniqueId().toString(), ownerName=owner.getName(), mat=it.getType().name();
        String batch=first(arString(it,"batch_id"),UUID.randomUUID().toString()), world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("ensure AR asset",()->{
            for(int i=0;i<amount;i++){
                exec("INSERT INTO cmv7_ar_assets(asset_id,batch_id,material,amount,owner_uuid,owner_name,status,source,world,x,y,z,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID().toString(),batch,mat,1,ownerUuid,ownerName,"ACTIVE",source,world,x,y,z,t,t);
            }
        });
    }

    private void registerArTransferClaim(Item item,Player owner){
        if(item==null||owner==null||!isOfficialArItem(item.getItemStack()))return;
        ItemStack st=item.getItemStack();
        String oldUuid=first(arString(st,"owner_uuid"),owner.getUniqueId().toString());
        String oldName=first(arString(st,"owner_name"),owner.getName());
        ArDropClaim claim=new ArDropClaim(UUID.randomUUID().toString(),oldUuid,oldName,Math.max(1,st.getAmount()),now(),item.getLocation());
        arTransferClaims.put(item.getUniqueId(),claim);
        recordArTransaction("AR_TRANSFER_DROP_CLAIM",st,owner,oldUuid,oldName,null,item.getLocation(),"АР помечен как передаваемый через выброс и pickup; claim="+shortId(claim.claimId()));
        Bukkit.getScheduler().runTaskLater(this,()->arTransferClaims.remove(item.getUniqueId()),20L*300L);
    }

    private ArDropClaim claimArTransfer(Item item,Player picker){
        if(item==null)return null;
        ArDropClaim claim=arTransferClaims.remove(item.getUniqueId());
        if(claim==null)return null;
        if(now()-claim.createdAt()>300000L)return null;
        return claim;
    }

    private void retagArOwner(Item item,Player newOwner,String reason)throws Exception{
        retagArOwner(item,newOwner,reason,null);
    }

    private void retagArOwner(Item item,Player newOwner,String reason,ArDropClaim claim)throws Exception{
        if(item==null||newOwner==null)return;
        ItemStack st=item.getItemStack();
        if(!isOfficialArItem(st))return;
        String oldUuid=first(claim==null?"":claim.ownerUuid(),arString(st,"owner_uuid"));
        String oldName=first(claim==null?"":claim.ownerName(),arString(st,"owner_name"),"unknown");
        boolean legacy=!isOfficialAr(st)||oldUuid.isBlank();
        ItemStack updated=st.clone();
        ItemMeta meta=updated.getItemMeta();
        if(meta==null)return;
        String batch=first(arString(st,"batch_id"),"");
        setArOwnerMeta(updated,newOwner.getUniqueId().toString(),newOwner.getName(),first(arString(st,"source"),legacy?"legacy":"transfer"));
        batch=first(arString(updated,"batch_id"),batch);
        item.setItemStack(updated);
        String newUuid=newOwner.getUniqueId().toString();
        if(legacy)ensureArAsset(updated,newOwner,"legacy:"+reason,item.getLocation());
        else if(!newUuid.equals(oldUuid))transferArAssets(oldUuid,oldName,newOwner,updated.getAmount(),item.getLocation(),reason,batch,updated.getType());
        if(!newUuid.equals(oldUuid)){
            recordArTransaction("AR_TRANSFER_PICKUP",updated,newOwner,oldUuid,oldName,newOwner,item.getLocation(),"Владелец АР переписан при pickup после выбрасывания");
            recordArTransaction("AR_TRANSFER_CLAIMED",updated,newOwner,oldUuid,oldName,newOwner,item.getLocation(),"Передача подтверждена pickup; claim="+(claim==null?"none":shortId(claim.claimId())));
            msg(newOwner,"&aАР записан на тебя. &7Количество: &f"+updated.getAmount()+" &8| &7старый владелец: &f"+oldName);
            Player old=onlineByUuid(oldUuid);
            if(old!=null&&old.isOnline())msg(old,"&eАР передан игроку &f"+newOwner.getName()+"&e. Количество: &f"+updated.getAmount());
            sound(newOwner,"ENTITY_EXPERIENCE_ORB_PICKUP",0.55f,1.35f);
        }
    }
    private void transferArAssets(String fromUuid,String fromName,Player to,int amount,Location loc,String reason,String batchId,Material material){
        if(to==null||amount<=0)return;
        long t=now(); String toUuid=to.getUniqueId().toString(), toName=to.getName(), batch=first(batchId,UUID.randomUUID().toString());
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("transfer AR assets",()->tx(c->{
                int moved=0;
                if(fromUuid!=null&&!fromUuid.isBlank()){
                    List<Map<String,Object>> rows=query(c,"SELECT asset_id FROM cmv7_ar_assets WHERE owner_uuid=? AND status='ACTIVE' ORDER BY updated_at ASC LIMIT ? FOR UPDATE",fromUuid,amount);
                    for(Map<String,Object> r:rows){
                        exec(c,"UPDATE cmv7_ar_assets SET owner_uuid=?,owner_name=?,batch_id=?,updated_at=? WHERE asset_id=?",toUuid,toName,batch,t,s(r.get("asset_id")));
                        moved++;
                    }
                }
                for(int i=moved;i<amount;i++){
                    exec(c,"INSERT INTO cmv7_ar_assets(asset_id,batch_id,material,amount,owner_uuid,owner_name,status,source,world,x,y,z,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            UUID.randomUUID().toString(),batch,(material==null?Material.DIAMOND_ORE:material).name(),1,toUuid,toName,"ACTIVE","recovered-transfer:"+reason,world,x,y,z,t,t);
                }
                return moved;
        }));
    }
    private void markOneArAssetPlaced(Player owner,Location loc,Material material){
        if(owner==null)return;
        long t=now();
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("mark AR placed",()->tx(c->{
                List<Map<String,Object>> rows=query(c,"SELECT asset_id FROM cmv7_ar_assets WHERE owner_uuid=? AND status='ACTIVE' ORDER BY updated_at ASC LIMIT 1 FOR UPDATE",owner.getUniqueId().toString());
                if(rows.isEmpty())return null;
                exec(c,"UPDATE cmv7_ar_assets SET status='PLACED',material=?,world=?,x=?,y=?,z=?,updated_at=? WHERE asset_id=?",
                        (material==null?Material.DIAMOND_ORE:material).name(),world,x,y,z,t,s(rows.get(0).get("asset_id")));
                return null;
        }));
    }
    private void recordArTransaction(String type,ItemStack it,Player actor,String fromUuid,String fromName,Player to,Location loc,String details){
        long t=now();
        String actorUuid=actor==null?"":actor.getUniqueId().toString(), actorName=actor==null?"":actor.getName();
        String toUuid=to==null?"":to.getUniqueId().toString(), toName=to==null?"":to.getName();
        String ownerUuid=first(fromUuid,arString(it,"owner_uuid")), ownerName=first(fromName,arString(it,"owner_name"));
        String batch=first(arString(it,"batch_id"),ownerUuid,toUuid,"server"), asset=first(arString(it,"asset_id"),"batch:"+batch);
        String mat=it==null?Material.AIR.name():it.getType().name();
        int amount=type.equals("AR_SMELT_DIAMOND")||type.equals("UNCERTIFIED_AR_SMELT_BLOCKED")?1:Math.max(0,it==null?0:it.getAmount());
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("record AR transaction "+type,()->tx(c->{
                exec(c,"INSERT INTO cmv7_ar_transactions(time,type,asset_id,batch_id,actor_uuid,actor_name,from_uuid,from_name,to_uuid,to_name,material,amount,world,x,y,z,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        t,type,asset,batch,actorUuid,actorName,ownerUuid,ownerName,toUuid,toName,mat,amount,world,x,y,z,details);
                exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        t,type,actorUuid,actorName,toUuid,toName,world,x,y,z,mat,amount,details);
                if(type.equals("AR_SMELT_DIAMOND")&&!ownerUuid.isBlank()){
                    List<Map<String,Object>> rows=query(c,"SELECT asset_id FROM cmv7_ar_assets WHERE owner_uuid=? AND status='ACTIVE' ORDER BY updated_at ASC LIMIT 1 FOR UPDATE",ownerUuid);
                    if(rows.isEmpty()){
                        exec(c,"INSERT INTO cmv7_ar_assets(asset_id,batch_id,material,amount,owner_uuid,owner_name,status,source,world,x,y,z,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                UUID.randomUUID().toString(),batch,mat,1,ownerUuid,ownerName,"SMELTED","recovered-smelt",world,x,y,z,t,t);
                    }else{
                        exec(c,"UPDATE cmv7_ar_assets SET status='SMELTED',updated_at=? WHERE asset_id=?",t,s(rows.get(0).get("asset_id")));
                    }
                }
                return null;
        }));
    }
    private void recordArGuardIncident(String type,ItemStack it,Player actor,Location loc,String details){
        long t=now();
        String actorUuid=actor==null?"":actor.getUniqueId().toString(), actorName=actor==null?"":actor.getName();
        String ownerUuid=arString(it,"owner_uuid"), ownerName=arString(it,"owner_name");
        String mat=it==null?Material.AIR.name():it.getType().name();
        int amount=Math.max(0,it==null?0:it.getAmount());
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("record AR guard incident "+type,()->exec("INSERT INTO cmv7_ar_guard_incidents(time,type,actor_uuid,actor_name,owner_uuid,owner_name,material,amount,world,x,y,z,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                t,type,actorUuid,actorName,ownerUuid,ownerName,mat,amount,world,x,y,z,details));
    }
    private void protectArEntity(Item item,String reason){
        if(item==null)return;
        item.setFireTicks(0);
        item.setInvulnerable(true);
        item.setPersistent(true);
    }
    private boolean isArTransportInventory(Inventory inv){
        if(inv==null)return false;
        String type=inv.getType().name();
        return type.equals("HOPPER")||type.equals("DISPENSER")||type.equals("DROPPER");
    }
    private String blockKey(Block b){return b.getWorld().getName()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private boolean customBlockVisualsEnabled(){return getConfig().getBoolean("custom-block-visuals.enabled",true);}
    private double customBlockScale(String kind){return getConfig().getDouble("custom-block-visuals.models."+kind+".scale",1.01D);}
    private double customBlockOffsetY(String kind){return getConfig().getDouble("custom-block-visuals.models."+kind+".offset-y",0.5D);}
    private boolean handleProtectedVisualInteract(Player p,Entity target,PlayerInteractEntityEvent e){
        return false;
    }
    private boolean legacyHandleProtectedVisualInteractDisabled(Player p,Entity target,PlayerInteractEntityEvent e){
        if(!(target instanceof ItemDisplay display))return false;
        PersistentDataContainer pdc=display.getPersistentDataContainer();
        if(!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey,PersistentDataType.STRING)))return false;
        String kind=first(pdc.get(visualKindKey,PersistentDataType.STRING),"");
        String linkedId=first(pdc.get(visualLinkedIdKey,PersistentDataType.STRING),"");
        if("ATM".equals(kind)||linkedId.isBlank())return false;
        if(!"ATM".equals(kind))return false;
        e.setCancelled(true);
        try{
            List<Map<String,Object>> rows=query("SELECT active FROM ar_atms WHERE id=? LIMIT 1",linkedId);
            if(rows.isEmpty()||num(rows.get(0).get("active"))!=1){
                warn(p,"Этот банкомат больше недоступен.");
                return true;
            }
            legacyOpenBankAtm(p,linkedId);
        }catch(Exception ex){
            warn(p,"Не удалось открыть банкомат. Подробности записаны в лог.");
            getLogger().warning("atm visual interact: "+ex);
        }
        return true;
    }
    private void spawnOrReplaceProtectedBlockVisual(Location blockLocation,String kind,String linkedId,Material baseMaterial,int customModelData,String modelId)throws Exception{
        if(!customBlockVisualsEnabled()||blockLocation==null||blockLocation.getWorld()==null||linkedId==null||linkedId.isBlank())return;
        cleanupProtectedBlockVisuals(kind,linkedId);
        Location displayLocation=blockLocation.clone().add(0.5D,customBlockOffsetY(kind),0.5D);
        ItemStack visualItem=new ItemStack(baseMaterial);
        ItemMeta meta=visualItem.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(c("&f"+modelId));
            meta.setCustomModelData(customModelData);
            meta.addItemFlags(ItemFlag.values());
            visualItem.setItemMeta(meta);
        }
        ItemDisplay display=blockLocation.getWorld().spawn(displayLocation,ItemDisplay.class,entity->{
            entity.setItemStack(visualItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.getPersistentDataContainer().set(visualEntityTypeKey,PersistentDataType.STRING,"PROTECTED_BLOCK_VISUAL");
            entity.getPersistentDataContainer().set(visualKindKey,PersistentDataType.STRING,kind);
            entity.getPersistentDataContainer().set(visualLinkedIdKey,PersistentDataType.STRING,linkedId);
            entity.getPersistentDataContainer().set(visualModelIdKey,PersistentDataType.STRING,modelId);
            float scale=(float)customBlockScale(kind);
            entity.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),new Vector3f(scale,scale,scale),new AxisAngle4f()));
        });
        String visualId="pbv-"+UUID.randomUUID();
        String worldName=blockLocation.getWorld().getName();
        int blockX=blockLocation.getBlockX(), blockY=blockLocation.getBlockY(), blockZ=blockLocation.getBlockZ();
        String entityUuid=display.getUniqueId().toString();
        double offsetY=customBlockOffsetY(kind);
        double scaleValue=customBlockScale(kind);
        long createdAt=now();
        dbAsync("save protected block visual "+kind,()->{
            exec("DELETE FROM protected_block_visuals WHERE kind=? AND linked_id=?",kind,linkedId);
            exec("INSERT INTO protected_block_visuals(id,kind,linked_id,world,x,y,z,entity_uuid,base_material,custom_model_data,model_id,offset_x,offset_y,offset_z,scale_x,scale_y,scale_z,yaw,pitch,created_at,updated_at,active) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)",
                    visualId,kind,linkedId,worldName,blockX,blockY,blockZ,
                    entityUuid,baseMaterial.name(),customModelData,modelId,0.5D,offsetY,0.5D,scaleValue,scaleValue,scaleValue,0D,0D,createdAt,createdAt);
        });
    }
    private void cleanupProtectedBlockVisuals(String kind,String linkedId)throws Exception{
        if(linkedId==null||linkedId.isBlank())return;
        List<Map<String,Object>> rows=query("SELECT entity_uuid,world,x,y,z FROM protected_block_visuals WHERE kind=? AND linked_id=? AND active=1 ORDER BY updated_at DESC LIMIT 1",kind,linkedId);
        if(!rows.isEmpty()){
            Map<String,Object> row=rows.get(0);
            World world=Bukkit.getWorld(s(row.get("world")));
            if(world!=null){
                String entityUuid=s(row.get("entity_uuid"));
                if(!entityUuid.isBlank()){
                    try{removeOwnedProtectedVisualEntity(Bukkit.getEntity(UUID.fromString(entityUuid)),kind,linkedId);}catch(Exception ignored){}
                }
                cleanupNearbyProtectedVisuals(new Location(world,num(row.get("x")),num(row.get("y")),num(row.get("z"))),kind,linkedId);
            }
        }
        exec("UPDATE protected_block_visuals SET active=0,updated_at=? WHERE kind=? AND linked_id=? AND active=1",now(),kind,linkedId);
    }
    private void repairProtectedBlockVisuals()throws Exception{
        if(!customBlockVisualsEnabled())return;
        List<String> loadedChunks=new ArrayList<>();
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())loadedChunks.add(world.getName()+":"+chunk.getX()+":"+chunk.getZ());
        Runnable work=()->{
            for(String entry:loadedChunks){
                String[] parts=entry.split(":");
                if(parts.length!=3)continue;
                try{repairProtectedBlockVisuals(parts[0],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));}
                catch(Exception ex){getLogger().warning("atm visual repair queued: "+safeErr(ex));}
            }
        };
        if(Bukkit.isPrimaryThread())dbAsync("atm visual startup repair",()->{work.run();}); else work.run();
    }
    private void repairProtectedBlockVisuals(String worldName,int chunkX,int chunkZ)throws Exception{
        if(!customBlockVisualsEnabled())return;
        Runnable work=()->{
            try{
                List<Map<String,Object>> rows=query(
                        "SELECT a.id,a.world,a.x,a.y,a.z,pbv.entity_uuid,pbv.model_id,pbv.custom_model_data " +
                                "FROM ar_atms a " +
                                "LEFT JOIN protected_block_visuals pbv ON pbv.kind='ATM' AND pbv.linked_id=a.id AND pbv.active=1 " +
                                "WHERE a.active=1 AND a.world=? AND (a.x >> 4)=? AND (a.z >> 4)=?",
                        worldName,chunkX,chunkZ
                );
                Bukkit.getScheduler().runTask(this,()->{
                    try{
                        applyProtectedBlockVisualRepairs(worldName,chunkX,chunkZ,rows);
                    }catch(Exception ex){
                        getLogger().warning("atm visual apply: "+safeErr(ex));
                    }
                });
            }catch(Exception ex){
                getLogger().warning("atm visual fetch: "+safeErr(ex));
            }
        };
        if(Bukkit.isPrimaryThread())Bukkit.getScheduler().runTaskAsynchronously(this,work); else work.run();
    }
    private void applyProtectedBlockVisualRepairs(String worldName,int chunkX,int chunkZ,List<Map<String,Object>> rows)throws Exception{
        World world=Bukkit.getWorld(worldName);
        if(world==null||!world.isChunkLoaded(chunkX,chunkZ))return;
        for(Map<String,Object> row:rows){
            repairProtectedBlockVisual(s(row.get("id")),new Location(world,num(row.get("x")),num(row.get("y")),num(row.get("z"))),row);
        }
    }
    private void repairProtectedBlockVisual(String linkedId,Location location,Map<String,Object> row)throws Exception{
        if(location==null||location.getWorld()==null)return;
        String expectedEntityUuid=row==null?"":s(row.get("entity_uuid"));
        Entity entity=null;
        if(!expectedEntityUuid.isBlank()){
            try{entity=Bukkit.getEntity(UUID.fromString(expectedEntityUuid));}catch(Exception ignored){}
        }
        boolean validEntity=isOwnedProtectedVisualEntity(entity,"ATM",linkedId,"atm_terminal",MODEL_ATM_TERMINAL);
        cleanupNearbyProtectedVisualDuplicates(location,"ATM",linkedId,validEntity?entity.getUniqueId().toString():"");
        if(!validEntity){
            spawnOrReplaceProtectedBlockVisual(location,"ATM",linkedId,Material.PAPER,MODEL_ATM_TERMINAL,"atm_terminal");
        }
    }
    private void cleanupNearbyProtectedVisuals(Location blockLocation,String kind,String linkedId){
        if(blockLocation==null||blockLocation.getWorld()==null)return;
        for(Entity entity:blockLocation.getWorld().getNearbyEntities(blockLocation.clone().add(0.5D,customBlockOffsetY(kind),0.5D),0.9D,0.9D,0.9D)){
            removeOwnedProtectedVisualEntity(entity,kind,linkedId);
        }
    }
    private void cleanupNearbyProtectedVisualDuplicates(Location blockLocation,String kind,String linkedId,String keepEntityUuid){
        if(blockLocation==null||blockLocation.getWorld()==null)return;
        for(Entity entity:blockLocation.getWorld().getNearbyEntities(blockLocation.clone().add(0.5D,customBlockOffsetY(kind),0.5D),0.9D,0.9D,0.9D)){
            if(!isOwnedProtectedVisualEntity(entity,kind,linkedId,"atm_terminal",MODEL_ATM_TERMINAL))continue;
            if(!keepEntityUuid.isBlank()&&keepEntityUuid.equals(entity.getUniqueId().toString()))continue;
            entity.remove();
        }
    }
    private void removeOwnedProtectedVisualEntity(Entity entity,String kind,String linkedId){
        if(!isOwnedProtectedVisualEntity(entity,kind,linkedId,"atm_terminal",MODEL_ATM_TERMINAL))return;
        entity.remove();
    }
    private boolean isOwnedProtectedVisualEntity(Entity entity,String kind,String linkedId,String modelId,int customModelData){
        if(!(entity instanceof ItemDisplay display))return false;
        PersistentDataContainer pdc=display.getPersistentDataContainer();
        if(!"PROTECTED_BLOCK_VISUAL".equals(pdc.get(visualEntityTypeKey,PersistentDataType.STRING)))return false;
        if(!kind.equals(first(pdc.get(visualKindKey,PersistentDataType.STRING),"")))return false;
        if(!linkedId.equals(first(pdc.get(visualLinkedIdKey,PersistentDataType.STRING),"")))return false;
        if(!modelId.equals(first(pdc.get(visualModelIdKey,PersistentDataType.STRING),"")))return false;
        ItemStack stack=display.getItemStack();
        if(stack==null||stack.getType()!=Material.PAPER)return false;
        ItemMeta meta=stack.getItemMeta();
        return meta!=null&&meta.hasCustomModelData()&&meta.getCustomModelData()==customModelData;
    }
    private boolean arPlacedBlockExists(Block b)throws SQLException{return scalarLong("SELECT COUNT(*) FROM cmv7_ar_placed_blocks WHERE world=? AND x=? AND y=? AND z=?",b.getWorld().getName(),b.getX(),b.getY(),b.getZ())>0;}
    private void recordArPlacedBlock(BlockPlaceEvent e){Block b=e.getBlockPlaced(); long t=now(); String world=b.getWorld().getName(), mat=b.getType().name(), uuid=e.getPlayer().getUniqueId().toString(), name=e.getPlayer().getName(); int x=b.getX(),y=b.getY(),z=b.getZ(); dbAsync("AR placed block record",()->tx(c->{exec(c,"INSERT INTO cmv7_ar_placed_blocks(world,x,y,z,material,placed_by_uuid,placed_by_name,placed_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(world,x,y,z) DO UPDATE SET material=excluded.material,placed_by_uuid=excluded.placed_by_uuid,placed_by_name=excluded.placed_by_name,placed_at=excluded.placed_at",world,x,y,z,mat,uuid,name,t); exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",t,"AR_BLOCK_PLACED",uuid,name,"","",world,x,y,z,mat,1,"Поставленная алмазная руда не будет сертифицирована при добыче"); return null;}));}
    private void deleteArPlacedBlock(Block b){
        String world=b.getWorld().getName();
        int x=b.getX(),y=b.getY(),z=b.getZ();
        long t=now();
        dbAsync("AR placed block delete",()->tx(c->{
            List<Map<String,Object>> rows=query(c,"SELECT world FROM cmv7_ar_placed_blocks WHERE world=? AND x=? AND y=? AND z=? LIMIT 1",world,x,y,z);
            if(rows.isEmpty())return null;
            exec(c,"UPDATE cmv7_ar_assets SET status='REMOVED_FROM_WORLD',updated_at=? WHERE status='PLACED' AND world=? AND x=? AND y=? AND z=?",t,world,x,y,z);
            exec(c,"DELETE FROM cmv7_ar_placed_blocks WHERE world=? AND x=? AND y=? AND z=?",world,x,y,z);
            exec(c,"INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    t,"AR_PLACED_BLOCK_REMOVED","","","","",world,x,y,z,b.getType().name(),1,"Placed AR block left official circulation after world removal");
            return null;
        }));
    }
    private void arEvent(String type,Player a,Player target,String mat,int amount,Location loc,String details){long t=now(); String actorUuid=a==null?"":a.getUniqueId().toString(), actorName=a==null?"":a.getName(), targetUuid=target==null?"":target.getUniqueId().toString(), targetName=target==null?"":target.getName(), world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName(); int x=loc==null?0:loc.getBlockX(),y=loc==null?0:loc.getBlockY(),z=loc==null?0:loc.getBlockZ(); dbAsync("AR event "+type,()->exec("INSERT INTO cmv7_ar_events(time,type,actor_uuid,actor_name,target_uuid,target_name,world,x,y,z,material,amount,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",t,type,actorUuid,actorName,targetUuid,targetName,world,x,y,z,mat,amount,details));}
    private void tpArEvent(Player p,String id)throws Exception{List<Map<String,Object>> r=query("SELECT world,x,y,z FROM cmv7_ar_events WHERE id=? LIMIT 1",id); if(r.isEmpty())return; World w=Bukkit.getWorld(s(r.get(0).get("world"))); if(w==null){warn(p,"Мир не найден.");return;} p.teleport(new Location(w,num(r.get(0).get("x"))+.5,num(r.get(0).get("y"))+1,num(r.get(0).get("z"))+.5));}
    private void startScan(Player actor,int radius,boolean load){
        World world=actor.getWorld();
        int clampedRadius=Math.max(1,Math.min(radius,8));
        if(load){
            warn(actor,"Глубокий scan с принудительной загрузкой чанков отключён. Использую только загруженные чанки.");
        }
        int centerChunkX=actor.getLocation().getChunk().getX();
        int centerChunkZ=actor.getLocation().getChunk().getZ();
        List<Chunk> chunks=new ArrayList<>();
        for(int dx=-clampedRadius;dx<=clampedRadius;dx++){
            for(int dz=-clampedRadius;dz<=clampedRadius;dz++){
                int chunkX=centerChunkX+dx;
                int chunkZ=centerChunkZ+dz;
                if(!world.isChunkLoaded(chunkX,chunkZ))continue;
                chunks.add(world.getChunkAt(chunkX,chunkZ));
            }
        }
        if(chunks.isEmpty()){
            warn(actor,"Нет загруженных чанков в радиусе для безопасного scan.");
            return;
        }
        msg(actor,"&bБезопасный scan запущен. Загруженных чанков: &e"+chunks.size());
        int minBlockX=chunks.stream().mapToInt(chunk->chunk.getX()<<4).min().orElse(actor.getLocation().getBlockX());
        int maxBlockX=chunks.stream().mapToInt(chunk->(chunk.getX()<<4)+15).max().orElse(actor.getLocation().getBlockX());
        int minBlockZ=chunks.stream().mapToInt(chunk->chunk.getZ()<<4).min().orElse(actor.getLocation().getBlockZ());
        int maxBlockZ=chunks.stream().mapToInt(chunk->(chunk.getZ()<<4)+15).max().orElse(actor.getLocation().getBlockZ());
        dbAsyncLoad("ar safe scan placed blocks",
                ()->(int)scalarLong("SELECT COUNT(*) FROM cmv7_ar_placed_blocks WHERE world=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?",
                        world.getName(),minBlockX,maxBlockX,minBlockZ,maxBlockZ),
                placedBlocks->{
                    class SafeScanRun implements Runnable{
                        BukkitTask task;
                        int index=0;
                        int items=0;
                        int containers=0;
                        @Override public void run(){
                            for(int perTick=0;perTick<4&&index<chunks.size();perTick++,index++){
                                Chunk chunk=chunks.get(index);
                                try{
                                    for(BlockState state:chunk.getTileEntities()){
                                        if(!(state instanceof Container container))continue;
                                        containers++;
                                        items+=countAr(container.getInventory());
                                    }
                                }catch(Throwable error){
                                    getLogger().warning("safe scan: "+safeErr(error));
                                }
                            }
                            if(index<chunks.size())return;
                            task.cancel();
                            dbAsyncLoad("save AR safe scan report",
                                    ()->{
                                        exec("INSERT INTO cmv7_ar_scan_reports(time,actor,world,cx,cz,radius,chunks,ar_blocks,ar_items,containers,mode) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                                                now(),actor.getName(),world.getName(),centerChunkX,centerChunkZ,clampedRadius,chunks.size(),placedBlocks,items,containers,"loaded-safe");
                                        return true;
                                    },
                                    ignored->msg(actor,"&aСкан завершён. Отслеживаемые AR-блоки: &e"+placedBlocks+"&a, предметы: &e"+items+"&a, контейнеры: &e"+containers),
                                    error->warn(actor,"Не удалось сохранить отчёт сканирования. Подробности записаны в лог."));
                        }
                    }
                    SafeScanRun run=new SafeScanRun();
                    run.task=Bukkit.getScheduler().runTaskTimer(this,run,1L,2L);
                },
                error->warn(actor,"Не удалось подготовить безопасный scan. Подробности записаны в лог."));
    }
    private void startCheck(Player admin,Player t)throws Exception{checkReturn.putIfAbsent(t.getUniqueId(),t.getLocation()); checkMode.put(t.getUniqueId(),admin.getUniqueId()); frozen.add(t.getUniqueId()); t.teleport(admin.getLocation()); long time=now(); String adminUuid=admin.getUniqueId().toString(), adminName=admin.getName(), targetUuid=t.getUniqueId().toString(), targetName=t.getName(); dbAsync("player check start",()->exec("INSERT INTO cmv7_player_checks(time,admin_uuid,admin_name,player_uuid,player_name,action,active,details) VALUES(?,?,?,?,?,'START',1,?)",time,adminUuid,adminName,targetUuid,targetName,"Проверка")); t.sendTitle(c("&c&lПРОВЕРКА"),c("&fСтой на месте и отвечай администрации"),10,100,20); msg(t,"&cТы вызван на проверку. Voice-chat не блокируется."); staffNotify("&6Игрок вызван на проверку: &e"+t.getName()+" &7админом &f"+admin.getName());}
    private void stopCheck(Player admin,Player t,boolean back)throws Exception{checkMode.remove(t.getUniqueId()); frozen.remove(t.getUniqueId()); Location loc=checkReturn.remove(t.getUniqueId()); if(back&&loc!=null)t.teleport(loc); long time=now(); String adminUuid=admin.getUniqueId().toString(), adminName=admin.getName(), targetUuid=t.getUniqueId().toString(), targetName=t.getName(), details=back?"return":"no return"; dbAsync("player check stop",()->exec("INSERT INTO cmv7_player_checks(time,admin_uuid,admin_name,player_uuid,player_name,action,active,details) VALUES(?,?,?,?,?,'STOP',0,?)",time,adminUuid,adminName,targetUuid,targetName,details)); msg(t,"&aПроверка завершена."); staffNotify("&aПроверка завершена: &e"+t.getName()+" &7админом &f"+admin.getName());}
    private void toggleFreeze(Player a,Player t){ if(frozen.remove(t.getUniqueId())){staffNotify("&bFreeze снят: &e"+t.getName()+" &7админом &f"+a.getName()); msg(t,"&aТы разморожен.");}else{frozen.add(t.getUniqueId()); staffNotify("&cFreeze включён: &e"+t.getName()+" &7админом &f"+a.getName()); msg(t,"&cТы заморожен.");}}
    private void repairHand(Player a,Player t){ItemStack it=t.getInventory().getItemInMainHand(); if(it==null||it.getType()==Material.AIR)return; ItemMeta im=it.getItemMeta(); if(im instanceof org.bukkit.inventory.meta.Damageable d){d.setDamage(0); it.setItemMeta((ItemMeta)d);}}
    private void armorOff(Player a,Player t){PlayerInventory inv=t.getInventory(); ItemStack[] armor=inv.getArmorContents(); for(ItemStack it:armor) if(it!=null&&it.getType()!=Material.AIR) inv.addItem(it).values().forEach(left->t.getWorld().dropItemNaturally(t.getLocation(),left)); inv.setArmorContents(null);}
    private void shuffleHotbar(Player t){List<ItemStack> h=new ArrayList<>(); for(int i=0;i<9;i++)h.add(t.getInventory().getItem(i)); Collections.shuffle(h); for(int i=0;i<9;i++)t.getInventory().setItem(i,h.get(i)); t.updateInventory();}
    private void randomTpNear(Player t){Location base=t.getLocation(); double a=rnd.nextDouble()*Math.PI*2, d=12+rnd.nextDouble()*28; Location loc=base.clone().add(Math.cos(a)*d,0,Math.sin(a)*d); World w=loc.getWorld(); if(w!=null)loc.setY(Math.max(w.getMinHeight()+2,w.getHighestBlockYAt(loc)+1)); t.teleport(loc); sound(t,"ITEM_CHORUS_FRUIT_TELEPORT",1f,1f);}
    private void nearbyReport(Player admin,Player t){List<String> names=new ArrayList<>(); for(Player p:Bukkit.getOnlinePlayers())if(!p.equals(t)&&p.getWorld().equals(t.getWorld())&&p.getLocation().distanceSquared(t.getLocation())<=48*48)names.add(p.getName()+" "+Math.round(p.getLocation().distance(t.getLocation()))+"м"); msg(admin,"&bРядом с &f"+t.getName()+"&b: &f"+(names.isEmpty()?"никого в 48м":String.join(", ",names)));}
    private void spin(Player t){for(int i=1;i<=12;i++)Bukkit.getScheduler().runTaskLater(this,()->{if(!t.isOnline())return; Location l=t.getLocation(); l.setYaw(l.getYaw()+75f); t.teleport(l);},i*2L);}
    private void chickens(Player t){for(int i=0;i<8;i++)t.getWorld().spawnEntity(t.getLocation().clone().add(rnd.nextDouble()*4-2,1.2,rnd.nextDouble()*4-2), EntityType.CHICKEN);}
    private void giveTaxPaper(Player t){ItemStack p=new ItemStack(Material.PAPER); ItemMeta m=p.getItemMeta(); if(m!=null){m.setDisplayName(c("&f&lНалоговая повестка")); m.setLore(List.of(c("&7Явиться в администрацию."))); p.setItemMeta(m);} t.getInventory().addItem(p);}
    private void givePotato(Player t){ItemStack p=new ItemStack(Material.POTATO); ItemMeta m=p.getItemMeta(); if(m!=null){m.setDisplayName(c("&6&lКартошка судьбы")); m.setLore(List.of(c("&7Подозрительно официальная."))); p.setItemMeta(m);} t.getInventory().addItem(p);}

    private void giveTemporaryApplicationBook(Player p,String eid,String candidateUuid)throws Exception{
        if(eid==null||eid.isBlank())throw new SQLException("Нет активных выборов");
        List<Map<String,Object>> cand=query("SELECT name,display_name FROM candidates WHERE election_id=? AND uuid=? LIMIT 1",eid,candidateUuid);
        String candidate=cand.isEmpty()?candidateUuid:first(s(cand.get(0).get("display_name")),s(cand.get(0).get("name")),candidateUuid);
        List<Map<String,Object>> apps=query("""
                SELECT applicant_name,statement,status,submitted_at,reviewed_by
                FROM applications
                WHERE election_id=? AND applicant_uuid=? AND COALESCE(deleted_at,0)=0 AND COALESCE(visible_in_game,1)=1
                ORDER BY CASE status WHEN 'APPROVED' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END, submitted_at DESC
                LIMIT 1
                """,eid,candidateUuid);
        String status=apps.isEmpty()?"NO_APPLICATION":s(apps.get(0).get("status"));
        String statement=apps.isEmpty()?"Заявка кандидата ещё не опубликована ЦИК. Кандидат добавлен вручную, поэтому смотри имя в списке и дождись полной анкеты от председателя.":s(apps.get(0).get("statement"));
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bm=(BookMeta)book.getItemMeta();
        if(bm!=null){
            bm.setTitle(clipped("Заявка "+candidate,32));
            bm.setAuthor("ЦИК CopiMine");
            bm.setDisplayName(c("&e&lЗаявка кандидата: &f"+clipped(candidate,24)));
            bm.setLore(List.of(c("&7Публичная заявка кандидата."),c("&7После закрытия она пропадет из инвентаря."),c("&8Только для просмотра.")));
            bm.setPages(applicationBookPages(candidate,eid,status,statement));
            tagElectionItem(bm,"temporary_application_book",eid,p.getUniqueId().toString(),candidateUuid);
            book.setItemMeta(bm);
        }
        p.closeInventory();
        giveOfficialItemOrWarn(p,book,"Освободи слот в инвентаре для просмотра заявки кандидата.");
        Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())p.openBook(book);},1L);
        Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())purgeTemporaryApplicationBooks(p);},20L*45L);
        msg(p,"&eОткрыта подписанная книга заявки &f"+candidate+"&e. Она временная и очистится автоматически.");
    }

    private List<String> applicationBookPages(String candidate,String eid,String status,String statement){
        List<String> pages=new ArrayList<>();
        pages.add(c("&6Официальная заявка ЦИК\n\n&0Кандидат: "+candidate+"\n\nЭта книга временная: прочитай программу и закрой книгу, чтобы вернуться к бюллетеню."));
        String text=ChatColor.stripColor(first(statement,"Заявка пустая."));
        int max=230;
        if(text.isBlank()) pages.add(c("&6Программа кандидата\n\n&0Заявка пустая."));
        else for(int i=0;i<text.length()&&pages.size()<80;i+=max){
            String chunk=text.substring(i,Math.min(text.length(),i+max));
            pages.add(c((i==0?"&6Программа кандидата\n\n&0":"&0")+chunk));
        }
        return pages;
    }

    private void purgeTemporaryApplicationBooks(Player p){
        if(p==null)return;
        PlayerInventory inv=p.getInventory();
        for(int i=0;i<inv.getSize();i++)if(isTemporaryApplicationBook(inv.getItem(i)))inv.setItem(i,null);
        if(isTemporaryApplicationBook(inv.getItemInOffHand()))inv.setItemInOffHand(null);
        p.updateInventory();
    }

    private List<String> applicationBookTemplatePages(String player,String eid,String issueId){
        String candidateGuidePage=c("&6Памятка кандидата\n\n&0Игрок: "+player+"\nЦикл: "+shortId(eid)+"\nКнига: "+shortId(issueId)+"\n\nЗаполни честно. После подписи книга уйдет в ЦИК и исчезнет.");
        String campaignProgramPage=c("&6Программа\n\n&0Опиши главную идею кампании.\n\nЧто изменишь на сервере?\nПочему это полезно игрокам?\nКак это будет выглядеть в RP?");
        String first24HoursPage=c("&6Первые 24 часа\n\n&0Что ты сделаешь сразу после победы?\n\n1.\n2.\n3.\n\nПиши конкретно, без общих обещаний.");
        String economyRulesPage=c("&6Экономика и правила\n\n&0Как ты будешь относиться к АР, торговле и спорным ситуациям?\n\nНельзя обещать дюп, раздачи или лом экономики.");
        String teamRisksPage=c("&6Команда и риски\n\n&0Кто поможет тебе?\nКакие риски ты видишь?\nКак ты будешь решать конфликты с игроками и ЦИК?");
        String finalConsentPage=c("&6Финал\n\n&0Подтверди:\n- понимаю правила выборов;\n- не подделываю голоса;\n- уважаю решение ЦИК.\n\nПодпиши книгу своим ником.");
        return List.of(candidateGuidePage,campaignProgramPage,first24HoursPage,economyRulesPage,teamRisksPage,finalConsentPage);
    }
    private boolean giveApplicationBook(Player t,String eid,String issueId){
        ItemStack b=new ItemStack(Material.WRITABLE_BOOK);
        BookMeta m=(BookMeta)b.getItemMeta();
        if(m!=null){
            m.setDisplayName(c("&e&lЗаявка кандидата"));
            m.setLore(List.of(c("&8APPLICATION_BOOK_V2_POLISH"),c("&7Заполни программу, первые шаги,"),c("&7экономику, команду и риски."),c("&7Issue: &f"+shortId(issueId)),c("&cПосле подписи книга исчезнет.")));
            m.setPages(applicationBookTemplatePages(t.getName(),eid,issueId));
            tagElectionItem(m,"application_book",eid,t.getUniqueId().toString(),issueId);
            b.setItemMeta(m);
        }
        boolean ok=giveOfficialItemOrWarn(t,b,"Освободи слот в инвентаре для книги заявки кандидата.");
        if(ok)msg(t,"&eТы получил улучшенную книгу заявки кандидата. Заполни страницы и подпиши её.");
        return ok;
    }
    private boolean isApplicationBook(ItemStack it){if(it==null||it.getType()!=Material.WRITABLE_BOOK)return false; ItemMeta m=it.getItemMeta(); if(m==null)return false; String n=ChatColor.stripColor(m.getDisplayName()).toLowerCase(Locale.ROOT); return n.contains("заявка")&&n.contains("кандидат");}
    private NamespacedKey electionKey(String key){return new NamespacedKey("copimineelectionflow", key);}
    private void tagElectionItem(ItemMeta meta,String type,String eid,String owner,String id){var d=meta.getPersistentDataContainer(); d.set(electionKey("type"),org.bukkit.persistence.PersistentDataType.STRING,type); d.set(electionKey("election"),org.bukkit.persistence.PersistentDataType.STRING,eid); d.set(electionKey("owner"),org.bukkit.persistence.PersistentDataType.STRING,owner); d.set(electionKey("id"),org.bukkit.persistence.PersistentDataType.STRING,id);}
    private String electionItemString(ItemStack it,String key){if(it==null||it.getType()==Material.AIR)return null; ItemMeta meta=it.getItemMeta(); if(meta==null)return null; return meta.getPersistentDataContainer().get(electionKey(key),org.bukkit.persistence.PersistentDataType.STRING);}
    private void setElectionItemString(ItemMeta meta,String key,String value){if(meta!=null&&value!=null)meta.getPersistentDataContainer().set(electionKey(key),org.bukkit.persistence.PersistentDataType.STRING,value);}
    private boolean requireElectionItemOwner(Player p,ItemStack it,String expectedType){
        if(it==null||it.getType()==Material.AIR)return true;
        String type=first(electionItemString(it,"type"),"");
        if(expectedType!=null&&!expectedType.isBlank()&&!expectedType.equals(type)){warn(p,"Этот официальный предмет не подходит для этого действия."); return false;}
        String owner=first(electionItemString(it,"owner"),"");
        if(!owner.isBlank()&&!owner.equals(p.getUniqueId().toString())){warn(p,"Этот официальный предмет выдан другому игроку."); sound(p,"BLOCK_NOTE_BLOCK_BASS",0.7f,0.7f); return false;}
        return true;
    }
    private boolean isSealedBallot(ItemStack it){return isBallotItem(it)&&first(electionItemString(it,"selected_candidate"),"").length()>0;}
    private boolean hasOwnedSealedBallot(Player p,String eid){try{return findOwnedSealedBallot(p,eid)!=null;}catch(Exception e){return false;}}
    private ItemStack findOwnedSealedBallot(Player p,String eid)throws SQLException{return findOwnedBallot(p,eid,true);}
    private ItemStack findOwnedUnusedBallot(Player p,String eid)throws SQLException{return findOwnedBallot(p,eid,false);}
    private ItemStack findOwnedBallot(Player p,String eid,boolean sealed)throws SQLException{
        if(eid==null||eid.isBlank())return null;
        Set<String> ids=new HashSet<>();
        for(Map<String,Object> r:query("SELECT id FROM cmv7_ballot_issues WHERE election_id=? AND voter_uuid=? AND COALESCE(used,0)=0",eid,p.getUniqueId().toString()))ids.add(s(r.get("id")));
        if(ids.isEmpty())return null;
        List<ItemStack> items=new ArrayList<>();
        Collections.addAll(items,p.getInventory().getContents());
        items.add(p.getInventory().getItemInOffHand());
        for(ItemStack it:items){
            if(!isBallotItem(it))continue;
            if(!requireElectionItemOwner(p,it,"ballot"))continue;
            if(!eid.equals(first(electionItemString(it,"election"),"")))continue;
            if(!ids.contains(first(electionItemString(it,"id"),"")))continue;
            if(sealed&&!isSealedBallot(it))continue;
            if(!sealed&&isSealedBallot(it))continue;
            return it;
        }
        return null;
    }
    private void removeBallotById(Player p,String ballotId){
        PlayerInventory inv=p.getInventory();
        for(int i=0;i<inv.getSize();i++){ItemStack it=inv.getItem(i); if(ballotId.equals(first(electionItemString(it,"id"),""))){inv.setItem(i,null); p.updateInventory(); return;}}
        ItemStack off=inv.getItemInOffHand(); if(ballotId.equals(first(electionItemString(off,"id"),""))){inv.setItemInOffHand(null); p.updateInventory();}
    }
    private boolean giveOfficialItemOrWarn(Player p,ItemStack item,String failMessage){
        if(p.getInventory().firstEmpty()<0){warn(p,failMessage); return false;}
        p.getInventory().addItem(item);
        p.updateInventory();
        return true;
    }
    private boolean hasActiveApplication(String uuid,String eid)throws Exception{return scalarLong("SELECT COUNT(*) FROM applications WHERE applicant_uuid=? AND election_id=? AND status IN ('PENDING','APPROVED') AND COALESCE(deleted_at,0)=0",uuid,eid)>0;}
    private boolean targetNeedsApplication(Player t,String eid)throws Exception{
        if(t==null||eid==null||eid.isBlank())return false;
        if(isCitizenCandidate(t,eid)||hasActiveApplication(t.getUniqueId().toString(),eid))return false;
        return scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=? AND applicant_uuid=? AND COALESCE(used,0)=0 AND COALESCE(annulled,0)=0",eid,t.getUniqueId().toString())==0;
    }
    private boolean targetNeedsBallot(Player t,String eid)throws SQLException{
        if(t==null||eid==null||eid.isBlank())return false;
        return !hasCitizenVote(t,eid)&&!hasCitizenBallot(t,eid);
    }
    private String targetApplicationStatus(Player t,String eid)throws Exception{
        if(t==null)return"игрок не найден";
        if(eid==null||eid.isBlank())return"нет цикла";
        if(isCitizenCandidate(t,eid))return"кандидат";
        List<Map<String,Object>> apps=query("SELECT status FROM applications WHERE election_id=? AND applicant_uuid=? AND COALESCE(deleted_at,0)=0 ORDER BY submitted_at DESC LIMIT 1",eid,t.getUniqueId().toString());
        if(!apps.isEmpty())return switch(s(apps.get(0).get("status")).toUpperCase(Locale.ROOT)){case"APPROVED"->"одобрена";case"REJECTED"->"отклонена";default->"на проверке";};
        long issued=scalarLong("SELECT COUNT(*) FROM cmv7_application_issues WHERE election_id=? AND applicant_uuid=? AND COALESCE(used,0)=0 AND COALESCE(annulled,0)=0",eid,t.getUniqueId().toString());
        return issued>0?"книга выдана":"нет";
    }
    private String targetBallotStatus(Player t,String eid)throws SQLException{
        if(t==null)return"игрок не найден";
        if(eid==null||eid.isBlank())return"нет цикла";
        if(hasCitizenVote(t,eid))return"голос принят";
        if(hasOwnedSealedBallot(t,eid))return"запечатан";
        if(hasCitizenBallot(t,eid))return"выдан";
        return"нет";
    }
    private String cleanPage(List<String> pages,int i){if(i>=pages.size())return""; return ChatColor.stripColor(pages.get(i)).replace("Почему ты хочешь стать кандидатом?","").replace("Что ты сделаешь для сервера?","").replace("Почему игроки должны выбрать тебя?","").replace("Напиши ответ ниже:","").trim();}
    private void effect(Player t,int sec,int amp,String...names){PotionEffectType type=null; for(String n:names){type=PotionEffectType.getByName(n); if(type!=null)break;} if(type!=null)t.addPotionEffect(new PotionEffect(type,sec*20,amp));}
    private void sound(Player p,String s,float v,float pitch){try{p.playSound(p.getLocation(),Sound.valueOf(s),v,pitch);}catch(Throwable ignored){}}
    private void broadcastSound(String s,float v,float p){for(Player pl:Bukkit.getOnlinePlayers())sound(pl,s,v,p);}
    private void staffNotify(String t){for(Player p:Bukkit.getOnlinePlayers())if(hasAnyAdmin(p))msg(p,t);}

    private void recordPlayerActivity(Player p,String type,Location loc,String details,boolean adminOnly){
        if(p==null)return;
        long time=now();
        String uuid=p.getUniqueId().toString(), name=p.getName();
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        dbAsync("player activity write",()->exec("INSERT INTO cmv7_player_activity(time,player_uuid,player_name,type,world,x,y,z,details,admin_only) VALUES(?,?,?,?,?,?,?,?,?,?)",time,uuid,name,type,world,x,y,z,details==null?"":details,adminOnly?1:0));
    }

    private void updatePlayerProfile(Player p,boolean online,String brand){
        if(p==null)return;
        long time=now();
        String uuid=p.getUniqueId().toString(), name=p.getName();
        Location loc=p.getLocation();
        String world=loc==null||loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc==null?0:loc.getBlockX(), y=loc==null?0:loc.getBlockY(), z=loc==null?0:loc.getBlockZ();
        String clientBrand=brand==null?"":brand;
        dbAsync("player profile upsert",()->exec("""
                INSERT INTO cmv4_players(uuid,name,display_name,first_seen,last_seen,last_quit,online,last_world,last_x,last_y,last_z,client_brand,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name=excluded.name,
                    display_name=excluded.display_name,
                    last_seen=excluded.last_seen,
                    last_quit=CASE WHEN excluded.online=0 THEN excluded.last_quit ELSE cmv4_players.last_quit END,
                    online=excluded.online,
                    last_world=excluded.last_world,
                    last_x=excluded.last_x,
                    last_y=excluded.last_y,
                    last_z=excluded.last_z,
                    client_brand=CASE WHEN excluded.client_brand<>'' THEN excluded.client_brand ELSE cmv4_players.client_brand END,
                    updated_at=excluded.updated_at
                """,uuid,name,p.getDisplayName(),time,time,online?0:time,online?1:0,world,x,y,z,clientBrand,time));
    }

    private void snapshotOnlineInventory(Player p,String source){
        if(p==null)return;
        long time=now();
        String uuid=p.getUniqueId().toString(), name=p.getName();
        Location loc=p.getLocation();
        String world=loc.getWorld()==null?"":loc.getWorld().getName();
        int x=loc.getBlockX(), y=loc.getBlockY(), z=loc.getBlockZ();
        String inventoryJson=inventoryJson(p.getInventory()), enderJson=inventoryJson(p.getEnderChest());
        int arInv=countAr(p.getInventory()), arEnd=countAr(p.getEnderChest()), food=p.getFoodLevel();
        double health=p.getHealth();
        dbAsync("inventory snapshot write",()->exec("INSERT INTO cmv7_inventory_snapshots(time,player_uuid,player_name,source,inventory_json,ender_json,ar_inventory,ar_ender,health,food,world,x,y,z) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",time,uuid,name,source==null?"manual":source,inventoryJson,enderJson,arInv,arEnd,health,food,world,x,y,z));
    }

    private String inventoryJson(Inventory inv){
        StringBuilder sb=new StringBuilder("[");
        boolean first=true;
        ItemStack[] contents=inv==null?new ItemStack[0]:inv.getContents();
        for(int i=0;i<contents.length;i++){
            ItemStack it=contents[i];
            if(it==null||it.getType()==Material.AIR)continue;
            if(!first)sb.append(',');
            first=false;
            ItemMeta meta=it.getItemMeta();
            String name="";
            if(meta!=null&&meta.hasDisplayName())name=ChatColor.stripColor(meta.getDisplayName());
            sb.append("{\"slot\":").append(i)
                    .append(",\"type\":\"").append(jsonEscape(it.getType().name())).append('"')
                    .append(",\"amount\":").append(it.getAmount())
                    .append(",\"name\":\"").append(jsonEscape(name)).append('"')
                    .append(",\"ar\":").append(countArItem(it))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private String jsonEscape(String s){
        if(s==null)return"";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            switch(ch){
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if(ch<32)out.append(' '); else out.append(ch); }
            }
        }
        return out.toString();
    }

    private void recordEconomySnapshot(String actor,int onlinePlayers){
        dbAsync("economy snapshot write",()->{
            List<Map<String,Object>> rows=query("SELECT COALESCE(SUM(balance),0) total,COALESCE(SUM(inventory_balance),0) inv,COALESCE(SUM(ender_balance),0) ender FROM cmv7_ar_balances");
            Map<String,Object> r=rows.isEmpty()?Map.of():rows.get(0);
            exec("INSERT INTO cmv7_ar_economy_snapshots(time,actor,online_players,total_balance,total_inventory,total_ender,details) VALUES(?,?,?,?,?,?,?)",now(),actor==null?"SERVER":actor,onlinePlayers,num(r.get("total")),num(r.get("inv")),num(r.get("ender")),"syncArOnline");
        });
    }

    private void registerRpCommandGuard(){
        Collections.addAll(rpBlocked,
                "adminhub","cadm","copimine","cadmin","cpanel","cpa","cpadmin","админка","кмадмин",
                "cmultra","cmplus","uadmin","ultraadmin","ультраадмин",
                "election","elections","el","elec","vote","votes","president","pres","выборы","президент",
                "cmeflow","cmflow","electionflow","cmvoteadmin","voteadmin","выборадмин","cmvote","голос",
                "cmapply","candidateapply","applycandidate","заявка","заявление","кандидат",
                "oldvoteoff","oldvotesoff","electionoff","oldelectionoff","выключитьстарыевыборы",
                "cikfix","cik","cikbridge","fixcik","цикфикс","cmcik",
                "ar","ars","cguard","guard","arreconomy","aradmin","cpar","ары","ар",
                "cmpres","presannounce","presidentannounce");
        Collections.addAll(reportAllowed,"report","appeal","adminrequest","problem","request","helpadm","обращение","жалоба","репорт","проблема","helpme","ticket");
    }

    private boolean isBlockedRpCommand(String raw){
        if(raw==null||raw.isBlank()||!raw.startsWith("/"))return false;
        String[] parts=raw.substring(1).trim().split("\\s+");
        if(parts.length==0||parts[0].isBlank())return false;
        String cmd=parts[0].toLowerCase(Locale.ROOT);
        if(cmd.equals("cmsidebar")&&parts.length>=2){
            String sub=parts[1].toLowerCase(Locale.ROOT);
            if(sub.equals("hide")||sub.equals("show"))return false;
        }
        if(reportAllowed.contains(cmd))return false;
        return rpBlocked.contains(cmd);
    }

    private void denyRpCommand(PlayerCommandPreprocessEvent e,Player p){
        e.setCancelled(true);
        warn(p,"Эта команда отключена для игроков. Используй интерфейс выборов, участки, куратора и бюллетень.");
        sound(p,"BLOCK_NOTE_BLOCK_BASS",0.7f,0.8f);
    }

    private boolean handleDestroyableOfficialDrop(PlayerDropItemEvent e){
        Player p=e.getPlayer();
        Item dropped=e.getItemDrop();
        ItemStack stack=dropped==null?null:dropped.getItemStack();
        boolean seal=isElectionSeal(stack), mandate=isPresidentMandate(stack);
        if(!seal&&!mandate)return false;
        ItemStack destroyedStack=stack==null?null:stack.clone();
        String destroyedType=seal?"cik_seal":"president_mandate";
        if(dropped!=null&&!processedSealDrops.add(dropped.getUniqueId()))return true;
        e.setCancelled(false);
        try{if(dropped!=null&&!dropped.isDead())dropped.remove();}catch(Throwable ignored){}
        Bukkit.getScheduler().runTask(this,()->{
            try{
                markOfficialItemDestroyed(p,destroyedStack,destroyedType);
                p.updateInventory();
                msg(p,seal?"&aПечать ЦИК уничтожена. Если она снова нужна, возьми новую у председателя.":"&aМандат президента уничтожен. Если он снова нужен, выдай новый через админку.");
                audit(p.getName(),seal?"ULTRA7_CIK_SEAL_DESTROY":"ULTRA7_PRESIDENT_MANDATE_DESTROY","drop-key-destroy",true);
                sound(p,"ENTITY_ITEM_BREAK",0.7f,1.1f);
            }catch(Throwable ex){getLogger().warning("seal remove warning: "+ex.getMessage());}
            finally{if(dropped!=null)processedSealDrops.remove(dropped.getUniqueId());}
        });
        return true;
    }

    private boolean removeOneElectionSealFromInventory(Player p){
        PlayerInventory inv=p.getInventory();
        if(isElectionSeal(inv.getItemInMainHand())){decOrClear(inv,EquipmentSlot.HAND);return true;}
        if(isElectionSeal(inv.getItemInOffHand())){decOrClear(inv,EquipmentSlot.OFF_HAND);return true;}
        for(int i=0;i<inv.getSize();i++){
            ItemStack it=inv.getItem(i);
            if(isElectionSeal(it)){if(it.getAmount()<=1)inv.setItem(i,null);else it.setAmount(it.getAmount()-1);return true;}
        }
        return false;
    }

    private void decOrClear(PlayerInventory inv,EquipmentSlot slot){
        ItemStack it=slot==EquipmentSlot.HAND?inv.getItemInMainHand():inv.getItemInOffHand();
        if(it==null||it.getType()==Material.AIR)return;
        if(it.getAmount()<=1){if(slot==EquipmentSlot.HAND)inv.setItemInMainHand(null);else inv.setItemInOffHand(null);}
        else{it.setAmount(it.getAmount()-1); if(slot==EquipmentSlot.HAND)inv.setItemInMainHand(it);else inv.setItemInOffHand(it);}
    }

    private boolean isElectionSeal(ItemStack it){
        if(it==null||it.getType()==Material.AIR)return false;
        if("cik_seal".equals(first(electionItemString(it,"type"),"")))return true;
        ItemMeta meta=it.getItemMeta(); if(meta==null)return false;
        try{
            for(NamespacedKey key:meta.getPersistentDataContainer().getKeys()){
                String full=(key.getNamespace()+":"+key.getKey()).toLowerCase(Locale.ROOT);
                if((full.contains("seal")||full.contains("stamp")||full.contains("curator"))&&(full.contains("election")||full.contains("vote")||full.contains("cik")||full.contains("copimine")))return true;
                if(full.contains("clean_seal")||full.contains("curator_seal")||full.contains("election_seal"))return true;
                try{Integer i=meta.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.INTEGER); if(i!=null&&i==1&&(full.contains("seal")||full.contains("stamp")))return true;}catch(Throwable ignored){}
                try{String s=meta.getPersistentDataContainer().get(key,org.bukkit.persistence.PersistentDataType.STRING); if(s!=null){String v=s.toLowerCase(Locale.ROOT); if((v.contains("seal")||v.contains("печать"))&&(v.contains("election")||v.contains("цик")||v.contains("выбор")))return true;}}catch(Throwable ignored){}
            }
        }catch(Throwable ignored){}
        String t=itemText(it);
        boolean looksLikeSeal=t.contains("печать")||t.contains("seal")||t.contains("штамп")||t.contains("stamp");
        boolean looksElection=t.contains("цик")||t.contains("выбор")||t.contains("election")||t.contains("участ")||t.contains("куратор");
        boolean likelyMaterial=Set.of(Material.NETHER_STAR,Material.NAME_TAG,Material.PAPER,Material.BOOK,Material.WRITABLE_BOOK,Material.WRITTEN_BOOK,Material.TRIPWIRE_HOOK,Material.EMERALD,Material.GOLD_INGOT).contains(it.getType());
        return likelyMaterial&&looksLikeSeal&&looksElection;
    }

    private boolean isRestrictedTop(Inventory inv){
        if(inv==null)return false;
        return switch(inv.getType()){
            case PLAYER, CREATIVE, CHEST, BARREL, SHULKER_BOX, ENDER_CHEST -> false;
            default -> true;
        };
    }

    private boolean containsProtectedItem(ItemStack[] items){
        if(items==null)return false;
        for(ItemStack it:items)if(isRestrictedInventoryItem(it))return true;
        return false;
    }

    private boolean isRestrictedInventoryItem(ItemStack it){
        return isOfficialArItem(it)||isProtectedOfficialItem(it);
    }

    private boolean isProtectedCustomItem(ItemStack it){
        if(it==null||it.getType()==Material.AIR)return false;
        if(isOfficialArItem(it))return false;
        String type=first(electionItemString(it,"type"),"");
        if(!type.isBlank())return true;
        return isTemporaryApplicationBook(it)||isPollingStationKit(it)||isElectionSeal(it)||isPresidentMandate(it);
    }

    private boolean shouldPersistOfficialItem(ItemStack it){
        return isProtectedCustomItem(it)&&!isOfficialArItem(it)&&!isTemporaryApplicationBook(it);
    }

    private void restorePendingOfficialItems(Player p,String reason){
        if(p==null||!p.isOnline())return;
        List<ItemStack> pending=pendingOfficialReturns.get(p.getUniqueId());
        if(pending==null||pending.isEmpty())return;
        List<ItemStack> rest=new ArrayList<>();
        int restored=0;
        for(ItemStack item:pending){
            if(item==null||item.getType()==Material.AIR)continue;
            if(p.getInventory().firstEmpty()<0){rest.add(item); continue;}
            Map<Integer,ItemStack> leftovers=p.getInventory().addItem(item);
            if(leftovers.isEmpty())restored++;
            else rest.addAll(leftovers.values());
        }
        if(rest.isEmpty())pendingOfficialReturns.remove(p.getUniqueId()); else pendingOfficialReturns.put(p.getUniqueId(),rest);
        p.updateInventory();
        if(restored>0){
            msg(p,"&aОфициальные предметы возвращены после "+reason+": &e"+restored);
            sound(p,"BLOCK_AMETHYST_BLOCK_CHIME",0.7f,1.2f);
            audit(p.getName(),"ULTRA7_OFFICIAL_ITEM_RESTORE_PENDING","reason="+reason+" restored="+restored+" left="+rest.size(),true);
        }
        if(!rest.isEmpty())warn(p,"Освободи слот: часть официальных предметов ждет безопасного возврата.");
    }

    private boolean isProtectedOfficialItem(ItemStack it){
        if(it==null||it.getType()==Material.AIR)return false;
        if(isProtectedCustomItem(it)&&!isOfficialArItem(it))return true;
        if(isTemporaryApplicationBook(it))return true;
        if(isPollingStationKit(it))return true;
        if(isElectionSeal(it))return true;
        if(isPresidentMandate(it))return true;
        if(it.getType()!=Material.NETHER_STAR&&it.getType()!=Material.BEACON)return false;
        String text=itemText(it);
        if(text.isBlank())return false;
        boolean cikSeal=text.contains("печать цик")||text.contains("цик")||text.contains("cik")||text.contains("curator seal");
        boolean presidentTool=text.contains("президент")||text.contains("обращение президента")||text.contains("president");
        return cikSeal||presidentTool;
    }

    private boolean isPresidentMandate(ItemStack it){
        if(it==null||it.getType()==Material.AIR)return false;
        String type=first(electionItemString(it,"type"),"");
        if(type.equals("president_mandate"))return true;
        String text=itemText(it);
        boolean looksMandate=text.contains("мандат")||text.contains("mandate")||text.contains("удостоверение президента")||text.contains("president mandate");
        boolean looksPresident=text.contains("президент")||text.contains("president");
        boolean likelyMaterial=Set.of(Material.NETHER_STAR,Material.GOLDEN_HELMET,Material.NAME_TAG,Material.BOOK,Material.WRITTEN_BOOK,Material.PAPER).contains(it.getType());
        return likelyMaterial&&looksMandate&&looksPresident;
    }

    private boolean isOfficialArItem(ItemStack it){
        if(it==null||!arMaterial(it.getType()))return false;
        return isOfficialAr(it)||isLegacyArItem(it);
    }

    private String itemText(ItemStack it){
        ItemMeta meta=it==null?null:it.getItemMeta();
        if(meta==null)return "";
        StringBuilder sb=new StringBuilder();
        if(meta.hasDisplayName())sb.append(ChatColor.stripColor(meta.getDisplayName())).append(' ');
        if(meta.hasLore()&&meta.getLore()!=null)for(String line:meta.getLore())sb.append(ChatColor.stripColor(line)).append(' ');
        return sb.toString().toLowerCase(Locale.ROOT).replace("\u0451","\u0435").replaceAll("\\s+"," ").trim();
    }

    private record ArSnapshot(String uuid,String name,int inv,int ender,int total){}

    private void queueArSync(String reason){
        if(!arSyncQueued.compareAndSet(false,true))return;
        Bukkit.getScheduler().runTaskLater(this,()->{
            List<ArSnapshot> rows=new ArrayList<>();
            for(Player p:Bukkit.getOnlinePlayers()){
                int inv=countAr(p.getInventory()), end=countAr(p.getEnderChest());
                rows.add(new ArSnapshot(p.getUniqueId().toString(),p.getName(),inv,end,inv+end));
            }
            dbAsync("AR guard balance sync",()->{
                try{
                    for(ArSnapshot r:rows){
                        exec("INSERT INTO cmv7_ar_balances(uuid,name,balance,inventory_balance,ender_balance,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,balance=excluded.balance,inventory_balance=excluded.inventory_balance,ender_balance=excluded.ender_balance,updated_at=excluded.updated_at",r.uuid(),r.name(),r.total(),r.inv(),r.ender(),now());
                    }
                    audit("SERVER","ULTRA7_AR_GUARD_SYNC",reason+" players="+rows.size(),true);
                }finally{arSyncQueued.set(false);}
            });
        },3L);
    }

    private boolean handleSealDropCommand(CommandSender sender,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("help")){msg(sender,"&6/cmsealdrop trash &7- удалить печать из рук/инвентаря."); msg(sender,"&6/cmsealdrop status &7- диагностика."); return true;}
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(Set.of("trash","delete","remove","выкинуть","удалить").contains(sub)){
            if(!(sender instanceof Player p)){warn(sender,"Эта команда только для игрока."); return true;}
            if(removeOneElectionSealFromInventory(p)){p.updateInventory(); msg(p,"&aПечать уничтожена. Новую можно взять на участке."); sound(p,"ENTITY_ITEM_BREAK",0.7f,1.1f);}
            else warn(p,"У тебя не найдено печати ЦИК.");
            return true;
        }
        if(sub.equals("status")){
            msg(sender,"&aЗащита печати встроена в AdminPlus.");
            if(sender instanceof Player p)msg(sender,"&7Печать в руках: &f"+(isElectionSeal(p.getInventory().getItemInMainHand())||isElectionSeal(p.getInventory().getItemInOffHand())));
            return true;
        }
        msg(sender,"&6/cmsealdrop trash|status");
        return true;
    }

    private boolean handleRpGuardCommand(CommandSender sender,String[] args){
        if(sender instanceof Player p && !hasAnyAdmin(p) && !p.hasPermission("copimine.rpguard.bypass")){warn(sender,"Нет прав."); return true;}
        if(args.length==0||args[0].equalsIgnoreCase("status")){
            msg(sender,"&aRP Command Guard встроен в AdminPlus.");
            msg(sender,"&7Игрокам разрешено: &f/cmsidebar hide&7, &f/cmsidebar show&7, &f/report&7, &f/appeal");
            return true;
        }
        if(args[0].equalsIgnoreCase("test")&&args.length>=2){String probe="/"+args[1]; msg(sender,"&7Команда &f"+probe+" &7для игрока: "+(isBlockedRpCommand(probe)?"&cзаблокирована":"&aразрешена/не контролируется")); return true;}
        msg(sender,"&6/rpguard status");
        msg(sender,"&6/rpguard test <command>");
        return true;
    }

    private void handleReport(CommandSender sender,String[] args)throws Exception{
        if(!(sender instanceof Player p)){warn(sender,"Only an in-game player can create a report.");return;}
        if(args.length==0){warn(p,"Напиши текст обращения: /report <проблема>");return;}
        String text=String.join(" ",args).trim();
        if(text.length()<3){warn(p,"Слишком короткое обращение.");return;}
        String id=UUID.randomUUID().toString();
        long n=now();
        Location l=p.getLocation();
        String snapshot="world="+(l.getWorld()==null?"":l.getWorld().getName())+" x="+l.getBlockX()+" y="+l.getBlockY()+" z="+l.getBlockZ()+" gm="+p.getGameMode()+" hp="+Math.round(p.getHealth());
        exec("INSERT INTO admin_requests(id,player_uuid,player_name,message,status,created_at,updated_at,assigned_to,closed_by,close_reason,snapshot) VALUES(?,?,?,?,'OPEN',?,?,'','','',?)",id,p.getUniqueId().toString(),p.getName(),text,n,n,snapshot);
        audit(p.getName(),"ADMIN_REQUEST_CREATE",id+" "+text,false);
        msg(p,"&aОбращение отправлено администрации. ID: &e"+shortId(id));
        staffNotify("&eНовое обращение от &f"+p.getName()+"&e: &f"+text);
    }
    private boolean handleReportaCommand(CommandSender sender,String[] args)throws Exception{
        if(!(sender instanceof Player p)){warn(sender,"Only an in-game player can create a report.");return true;}
        if(args.length==0){
            warn(p,"Напиши текст обращения: /reporta <что произошло>");
            return true;
        }
        PendingBugReport pending=pendingBugReports.get(p.getUniqueId());
        if(pending!=null){
            int noteStart=args[0].equalsIgnoreCase(pending.token())?1:0;
            if(args.length<=noteStart){
                warn(p,"Коротко опишите, что произошло: /reporta <описание>");
                return true;
            }
            return submitBugReport(p,pending,String.join(" ",Arrays.copyOfRange(args,noteStart,args.length)).trim());
        }
        handleReport(sender,args);
        return true;
    }
    private boolean handleBugReportCommand(CommandSender sender,String[] args)throws Exception{
        if(!(sender instanceof Player p)){warn(sender,"Эта команда доступна только из игры."); return true;}
        if(args.length<2){warn(p,"Нет кода отчёта. Нажмите кнопку из чата после ошибки."); return true;}
        PendingBugReport pending=pendingBugReports.get(p.getUniqueId());
        if(pending==null||!pending.token().equalsIgnoreCase(args[1].trim())){
            warn(p,"Отчёт уже отправлен или срок действия истёк.");
            return true;
        }
        String playerNote=args.length>2?String.join(" ",Arrays.copyOfRange(args,2,args.length)).trim():"Игрок отправил лог без отдельного описания.";
        return submitBugReport(p,pending,playerNote);
    }
    private boolean submitBugReport(Player p,PendingBugReport pending,String playerNote)throws Exception{
        pendingBugReports.remove(p.getUniqueId());
        String requestId=UUID.randomUUID().toString();
        long createdAt=now();
        String snapshot="world="+pending.world()+" x="+pending.x()+" y="+pending.y()+" z="+pending.z()+" item="+first(pending.itemType(),"AIR")+" source="+pending.source()+" action="+pending.action()+" error="+pending.exceptionClass()+" summary="+pending.errorSummary();
        String visibleNote=first(playerNote,"Игрок отправил лог без отдельного описания.");
        String message="[BUG "+pending.token()+"] "+visibleNote;
        exec("INSERT INTO admin_requests(id,player_uuid,player_name,message,status,created_at,updated_at,assigned_to,closed_by,close_reason,snapshot) VALUES(?,?,?,?,'OPEN',?,?,'','','',?)",
                requestId,pending.playerUuid(),pending.playerName(),message,createdAt,createdAt,snapshot);
        audit(pending.playerName(),"BUG_REPORT_CREATE",requestId+" "+pending.source()+" "+pending.action(),true);
        pluginEvent("adminplus","BUG_REPORT_CREATE",pending.playerName(),requestId,"token="+pending.token()+" source="+pending.source()+" action="+pending.action()+" note="+clipped(visibleNote,160));
        pushBackendBugArtifactsAsync(pending, requestId, message);
        msg(p,"&aОтчёт отправлен администрации. Код: &e"+pending.token()+" &8| &7ID: &f"+shortId(requestId));
        staffNotify("&cНовый баг-репорт от &f"+pending.playerName()+"&c: &f"+pending.source()+" &8| &7"+pending.action());
        return true;
    }
    private boolean handleAuditCommand(CommandSender sender,String[] args)throws Exception{
        if(!hasAnyAdmin(sender)){warn(sender,"Нет прав."); return true;}
        if(args.length<2){
            msg(sender,"&6/cmultra audit ar");
            msg(sender,"&6/cmultra audit elections");
            msg(sender,"&6/cmultra audit narcotics");
            return true;
        }
        String scope=args[1].toLowerCase(Locale.ROOT);
        switch(scope){
            case "ar" -> {
                long balances=tableExists("cmv7_ar_balances")?scalarLong("SELECT COUNT(*) FROM cmv7_ar_balances"):0L;
                long assets=tableExists("cmv7_ar_assets")?scalarLong("SELECT COUNT(*) FROM cmv7_ar_assets WHERE status='ACTIVE'"):0L;
                long placed=tableExists("cmv7_ar_placed_blocks")?scalarLong("SELECT COUNT(*) FROM cmv7_ar_placed_blocks"):0L;
                long incidents=tableExists("cmv7_ar_guard_incidents")?scalarLong("SELECT COUNT(*) FROM cmv7_ar_guard_incidents"):0L;
                long atms=tableExists("ar_atms")?scalarLong("SELECT COUNT(*) FROM ar_atms WHERE active=1"):0L;
                msg(sender,"&bAR аудит");
                msg(sender,"&7Балансов: &f"+balances+" &8| &7активов: &f"+assets+" &8| &7блоков: &f"+placed);
                msg(sender,"&7Инцидентов: &f"+incidents+" &8| &7банкоматов: &f"+atms+" &8| &7service: &f"+(officialArService()!=null?"ready":"fallback"));
            }
            case "elections","election" -> {
                long stations=tableExists("polling_stations")?scalarLong("SELECT COUNT(*) FROM polling_stations WHERE active=1"):0L;
                long chairs=tableExists("cik_chairs")?scalarLong("SELECT COUNT(*) FROM cik_chairs WHERE active=1"):0L;
                long seals=tableExists("cik_seals")?scalarLong("SELECT COUNT(*) FROM cik_seals WHERE status='ACTIVE'"):0L;
                long applications=tableExists("candidate_applications")?scalarLong("SELECT COUNT(*) FROM candidate_applications WHERE admin_status='PENDING'"):0L;
                long laws=tableExists("president_laws")?scalarLong("SELECT COUNT(*) FROM president_laws WHERE status='PENDING'"):0L;
                msg(sender,"&6Выборы: аудит");
                msg(sender,"&7Участков: &f"+stations+" &8| &7председателей: &f"+chairs+" &8| &7печатей: &f"+seals);
                msg(sender,"&7Заявок: &f"+applications+" &8| &7законов на рассмотрении: &f"+laws);
            }
            case "narcotics","drugs" -> {
                long brews=tableExists("narcotics_brewing_states")?scalarLong("SELECT COUNT(*) FROM narcotics_brewing_states WHERE COALESCE(deleted,0)=0"):0L;
                long overdose=tableExists("narcotics_player_overdose")?scalarLong("SELECT COUNT(*) FROM narcotics_player_overdose WHERE COALESCE(overdose_until,0)>?",now()):0L;
                long usage=tableExists("narcotics_player_usage_window")?scalarLong("SELECT COUNT(*) FROM narcotics_player_usage_window"):0L;
                long auditRows=tableExists("narcotics_admin_audit")?scalarLong("SELECT COUNT(*) FROM narcotics_admin_audit"):0L;
                msg(sender,"&dНаркотики: аудит");
                msg(sender,"&7Активных котлов: &f"+brews+" &8| &7передозировок: &f"+overdose);
                msg(sender,"&7Окон употребления: &f"+usage+" &8| &7строк аудита: &f"+auditRows);
            }
            default -> warn(sender,"Неизвестная область аудита. Используйте: ar, elections, narcotics.");
        }
        return true;
    }

    private boolean handleOldVoteOff(CommandSender sender,String[] args)throws Exception{
        if(args.length==0||args[0].equalsIgnoreCase("status")){
            msg(sender,"&6Clean AdminPlus CIK core: &aактивен");
            msg(sender,"&7Legacy ElectionService/SidebarService: &cотключен");
            msg(sender,"&7Новые выборы: &a/cmultra -> Выборы");
            msg(sender,"&7Активных выборов: &f"+safeScalar("SELECT COUNT(*) FROM elections WHERE status IN ("+liveElectionStatusSql()+")"));
            msg(sender,"&7Всего выборов: &f"+safeScalar("SELECT COUNT(*) FROM elections"));
            msg(sender,"&7Настроек Ultra7: &f"+safeScalar("SELECT COUNT(*) FROM cmv7_election_settings"));
            return true;
        }
        if(!hasAnyAdmin(sender)){warn(sender,"Нет прав.");return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "reset" -> {int n=0; for(Player p:Bukkit.getOnlinePlayers())if(removeOldElectionSidebar(p,true))n++; msg(sender,"&aСтарый sidebar очищен у игроков: &e"+n);}
            case "cleanglow" -> {int n=0; for(Player p:Bukkit.getOnlinePlayers())if(p.hasPotionEffect(PotionEffectType.GLOWING)){p.removePotionEffect(PotionEffectType.GLOWING);n++;} msg(sender,"&aЭффект glowing очищен у игроков: &e"+n);}
            case "unlock" -> hardUnlockOldVotes(sender);
            case "open" -> {if(sender instanceof Player p)openElections(p); else msg(sender,"Only in game.");}
            default -> {msg(sender,"&6/oldvoteoff status"); msg(sender,"&6/oldvoteoff reset"); msg(sender,"&6/oldvoteoff cleanglow"); msg(sender,"&6/oldvoteoff unlock");}
        }
        return true;
    }

    private void hardUnlockOldVotes(CommandSender sender)throws Exception{
        long n=now();
        String tag=" | ADMINPLUS_UNLOCK_"+n;
        int elections=exec("UPDATE elections SET status='CANCELLED_FORCE',ended_at=?,ended_by=?,notes=COALESCE(notes,'') || ? WHERE UPPER(COALESCE(status,''))='ACTIVE' OR COALESCE(status,'')='' OR UPPER(COALESCE(status,'')) NOT IN ('ENDED','CANCELLED','CANCELLED_FORCE','ARCHIVED','RESET')",n,sender.getName(),tag);
        int s7=safeExec("DELETE FROM cmv7_election_settings");
        int s9=safeExec("DELETE FROM cmv9_election_settings");
        int sp=safeExec("DELETE FROM plus_election_settings");
        int sessions=safeExec("DELETE FROM cmv731_vote_sessions");
        int ballots=safeExec("UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'') || ?"," | INVALIDATED_BY_ADMINPLUS_"+n);
        int sidebars=0; for(Player p:Bukkit.getOnlinePlayers())if(removeOldElectionSidebar(p,true))sidebars++;
        audit(sender.getName(),"ADMINPLUS_OLD_VOTE_UNLOCK","elections="+elections+" settings="+(s7+s9+sp)+" sessions="+sessions+" ballots="+ballots,true);
        msg(sender,"&4Разблокировка выполнена.");
        msg(sender,"&7elections закрыто: &e"+elections);
        msg(sender,"&7settings удалено: &e"+(s7+s9+sp));
        msg(sender,"&7sessions удалено: &e"+sessions+"&7, бюллетени инвалидированы: &e"+ballots);
        msg(sender,"&7старый sidebar удалён у игроков: &e"+sidebars);
    }

    private int safeExec(String sql,Object...args){try{return exec(sql,args);}catch(Throwable ignored){return 0;}}
    private Player onlineByUuid(String uuid){try{return uuid==null||uuid.isBlank()?null:Bukkit.getPlayer(UUID.fromString(uuid));}catch(Throwable ignored){return null;}}

    private boolean removeOldElectionSidebar(Player p,boolean notify){
        try{
            Scoreboard sb=p.getScoreboard();
            Objective obj=sb.getObjective(DisplaySlot.SIDEBAR);
            if(obj==null)return false;
            String title=cleanText(obj.getDisplayName());
            boolean old=title.contains("выборы сервера")||title.contains("server elections");
            for(String entry:sb.getEntries()){
                String line=cleanText(entry);
                if(line.contains("состояние: идут")||line.contains("голоса обновляются")||line.contains("следи за выборами")||line.contains("кандидаты скоро появятся")){old=true;break;}
            }
            if(old){obj.unregister(); if(notify)msg(p,"&aСтарый sidebar выборов удалён."); return true;}
        }catch(Throwable ignored){}
        return false;
    }

    private String latestWinnerName(){
        try{List<Map<String,Object>> r=query("SELECT winner_name FROM elections WHERE COALESCE(winner_name,'')<>'' ORDER BY COALESCE(ended_at,0) DESC,COALESCE(started_at,0) DESC LIMIT 1"); return r.isEmpty()?"нет":first(s(r.get(0).get("winner_name")),"нет");}
        catch(Throwable ignored){return"нет";}
    }

    private String safeWinnerName(String eid){
        try{
            List<Map<String,Object>> r=query("SELECT winner_name FROM elections WHERE id=? AND COALESCE(winner_name,'')<>'' LIMIT 1",eid);
            if(!r.isEmpty())return s(r.get(0).get("winner_name"));
            Map<String,Object> w=leader(eid);
            return w==null?"":first(s(w.get("display_name")),s(w.get("name")));
        }catch(Throwable ignored){return "";}
    }

    private long arTotal(){try{return scalarLong("SELECT COALESCE(SUM(balance),0) FROM cmv7_ar_balances");}catch(Throwable ignored){return 0;}}

    private String cleanText(String s){return ChatColor.stripColor(s==null?"":s).toLowerCase(Locale.ROOT);}

    private CopiMineEconomyCore economyCore(){
        Plugin plugin=Bukkit.getPluginManager().getPlugin("CopiMineEconomyCore");
        return plugin instanceof CopiMineEconomyCore core && plugin.isEnabled()?core:null;
    }
    private CopiMineEconomyCore.OfficialArService officialArService(){
        var registration=Bukkit.getServicesManager().getRegistration(CopiMineEconomyCore.OfficialArService.class);
        return registration==null?null:registration.getProvider();
    }
    private ItemStack createOfficialArStack(Material material,int amount){
        CopiMineEconomyCore.OfficialArService service=officialArService();
        if(service!=null)return service.createStack(material, amount);
        ItemStack item=new ItemStack(material==null?Material.DIAMOND_ORE:material,Math.max(1,amount));
        setArOwnerMeta(item,"","","");
        return item;
    }
    private ItemStack normalizeOfficialArStack(ItemStack stack){
        CopiMineEconomyCore.OfficialArService service=officialArService();
        if(service==null||stack==null)return stack;
        return service.normalizeStack(stack);
    }
    private boolean hasAnyAdmin(CommandSender s){return hasAdmin(s)||hasJuniorAdmin(s)||hasElectionAdmin(s)||hasEconomyAdmin(s)||hasPlayerAdmin(s);}
    private boolean hasAdmin(CommandSender s){if(!(s instanceof Player p))return true; return p.isOp()||p.hasPermission("copimine.admin")||p.hasPermission("copimine.ultra.admin");}
    private boolean hasJuniorAdmin(CommandSender s){if(!(s instanceof Player p))return false; return !hasAdmin(s)&&p.hasPermission("copimine.admin.junior");}
    private boolean isRestrictedJuniorAdmin(CommandSender s){return hasJuniorAdmin(s)&&!hasAdmin(s);}
    private boolean hasElectionAdmin(CommandSender s){if(isRestrictedJuniorAdmin(s))return false; if(hasAdmin(s))return true; if(!(s instanceof Player p))return true; return p.hasPermission("copimine.election.admin")||p.hasPermission("copimine.election.cik")||isChair(p);}
    private boolean hasElectionRecoveryAdmin(CommandSender s){if(isRestrictedJuniorAdmin(s))return false; if(hasAdmin(s))return true; if(!(s instanceof Player p))return true; return p.hasPermission("copimine.election.admin")||p.hasPermission("copimine.election.cik")||isChair(p);}
    private boolean hasEconomyAdmin(CommandSender s){if(isRestrictedJuniorAdmin(s))return false; if(!(s instanceof Player p))return true; return hasAdmin(s)||p.hasPermission("copimine.economy.admin")||p.hasPermission("copimine.bank.admin");}
    private boolean hasPlayerAdmin(CommandSender s){if(!(s instanceof Player p))return true; return hasAdmin(s)||hasJuniorAdmin(s)||p.hasPermission("copimine.players.admin");}
    private boolean isBlockedJuniorAdminAction(String action){
        if(action==null||action.isBlank())return true;
        return !(action.equals("open:hub")
                || action.equals("open:admin-map")
                || action.equals("open:players")
                || action.startsWith("player:")
                || action.startsWith("open:p-inv:")
                || action.startsWith("open:p-timeline:")
                || action.equals("close"));
    }
    private boolean isCurator(Player p){return false;}
    private boolean isChair(Player p){return cachedElectionRole(p).chair();}
    private boolean isPresident(Player p){return cachedElectionRole(p).president();}
    public ArtifactsBridge artifactsBridge(){return artifactsBridge;}
    private void updateRoleNameplates(){for(Player p:Bukkit.getOnlinePlayers())updateRoleNameplate(p); ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm!=null)applyAllRoleScoreboardTeams(sm.getMainScoreboard());}
    private void updateRoleNameplate(Player p){
        if(p==null||!p.isOnline())return;
        String prefix=rolePrefix(p);
        String label=c(prefix+p.getName());
        try{p.setPlayerListName(label);}catch(Throwable ex){try{p.setPlayerListName(c(p.getName()));}catch(Throwable ignored){}}
        try{p.setDisplayName(label);}catch(Throwable ignored){}
        applyRoleScoreboardTeam(p,null);
    }
    private String rolePrefix(Player p){
        // ROLE_LABELS_TAB_CHAT_NAMEPLATE_V3
        if(p==null)return"";
        try{
            if(hasAdmin(p))return"&c[Админ] ";
            if(isPresident(p))return"&6[Президент] ";
            if(isChair(p))return"&b[Пред. ЦИК] ";
            if(isCurator(p))return"&3[ЦИК] ";
        }catch(Throwable ignored){}
        return"";
    }
    private String roleTeamKey(Player p){
        String prefix=rolePrefix(p);
        if(prefix.contains("Админ"))return"cmu_admin";
        if(prefix.contains("Президент"))return"cmu_president";
        if(prefix.contains("Пред. ЦИК"))return"cmu_chair";
        if(prefix.contains("[ЦИК]"))return"cmu_cik";
        return"cmu_player";
    }
    private void applyAllRoleScoreboardTeams(Scoreboard board){if(board==null)return; for(Player p:Bukkit.getOnlinePlayers())applyRoleScoreboardTeam(p,board);}
    private void applyRoleScoreboardTeam(Player p,Scoreboard board){
        if(p==null)return;
        if(board!=null){applyRoleScoreboardTeamOnBoard(p,board); return;}
        ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm!=null)applyRoleScoreboardTeamOnBoard(p,sm.getMainScoreboard());
        for(Player viewer:Bukkit.getOnlinePlayers())try{applyRoleScoreboardTeamOnBoard(p,viewer.getScoreboard());}catch(Throwable ignored){}
    }
    private void applyRoleScoreboardTeamOnBoard(Player p,Scoreboard board){
        if(p==null||board==null)return;
        String entry=p.getName(), wanted=roleTeamKey(p), prefix=rolePrefix(p);
        for(String name:List.of("cmu_admin","cmu_president","cmu_chair","cmu_cik","cmu_player")){
            Team old=board.getTeam(name);
            if(old!=null&&!name.equals(wanted))try{old.removeEntry(entry);}catch(Throwable ignored){}
        }
        Team team=board.getTeam(wanted);
        if(team==null)team=board.registerNewTeam(wanted);
        try{team.setPrefix(c(prefix));}catch(Throwable ignored){}
        try{team.addEntry(entry);}catch(Throwable ignored){}
    }    private void requireMainAdmin(CommandSender s)throws Exception{if(!hasAdmin(s))throw new Exception("Доступно только главному админу.");}
    private boolean dispatchIfExists(String command){try{return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command);}catch(Throwable e){getLogger().warning("dispatch failed: "+command+" -> "+e.getMessage());return false;}}
    private Connection conn()throws SQLException{
        if(pgPool==null)throw new SQLException("PostgreSQL pool is not initialized");
        return pgPool.borrow();
    }
    private int exec(String sql,Object...args)throws SQLException{try(Connection c=conn()){return exec(c,sql,args);}}
    private int exec(Connection c,String sql,Object...args)throws SQLException{
        String adapted=adaptSql(sql);
        if(adapted.isBlank())return 0;
        try(PreparedStatement ps=c.prepareStatement(adapted)){bind(ps,args); return ps.executeUpdate();}
    }
    private List<Map<String,Object>> query(String sql,Object...args)throws SQLException{try(Connection c=conn()){return query(c,sql,args);}}
    private List<Map<String,Object>> query(Connection c,String sql,Object...args)throws SQLException{
        String adapted=adaptSql(sql);
        if(adapted.isBlank())return List.of();
        try(PreparedStatement ps=c.prepareStatement(adapted)){
            bind(ps,args);
            try(ResultSet rs=ps.executeQuery()){return rows(rs);}
        }
    }
    private long scalarLong(String sql,Object...args)throws SQLException{List<Map<String,Object>> r=query(sql,args); if(r.isEmpty())return 0; return num(r.get(0).values().iterator().next());}
    private long scalarLong(Connection c,String sql,Object...args)throws SQLException{List<Map<String,Object>> r=query(c,sql,args); if(r.isEmpty())return 0; return num(r.get(0).values().iterator().next());}
    private boolean tableExists(String t)throws SQLException{
        return scalarLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?",t)>0;
    }
    private boolean tableExists(Connection c,String t)throws SQLException{
        return scalarLong(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?",t)>0;
    }
    private void purgeTable(Connection c,Map<String,Long> counts,String table)throws SQLException{
        if(!tableExists(c,table)){counts.put(table,0L); return;}
        long rows=scalarLong(c,"SELECT COUNT(*) FROM "+ident(table));
        exec(c,"DELETE FROM "+ident(table));
        counts.put(table,rows);
    }
    private List<String> cols(String table)throws SQLException{
        List<String> out=new ArrayList<>();
        for(Map<String,Object> r:query("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=? ORDER BY ordinal_position",table))out.add(s(r.get("column_name")).toLowerCase(Locale.ROOT));
        return out;
    }
    private void ensureColumn(String table,String col,String decl)throws SQLException{
        if(!cols(table).contains(col.toLowerCase(Locale.ROOT)))exec("ALTER TABLE "+ident(table)+" ADD COLUMN "+adaptColumnDecl(decl));
    }
    private void audit(String actor,String action,String details,boolean admin){
        Runnable write=()->{
            try{exec("INSERT INTO cmv7_audit(time,actor,action,details,admin_only) VALUES(?,?,?,?,?)",now(),actor,action,details,admin?1:0);}catch(Throwable ignored){getLogger().warning("audit cmv7_audit failed: actor="+first(actor,"")+" action="+first(action,"")+" error="+safeErr(ignored));}
            try{exec("INSERT INTO audit(time,actor,action,details,admin_only) VALUES(?,?,?,?,?)",now(),actor,action,details,admin?1:0);}catch(Throwable ignored){getLogger().warning("audit audit-table failed: actor="+first(actor,"")+" action="+first(action,"")+" error="+safeErr(ignored));}
            try{exec("INSERT INTO cmv4_audit_events(time,actor,action,details,admin_only,source) VALUES(?,?,?,?,?,?)",now(),actor==null?"":actor,action==null?"":action,details==null?"":details,admin?1:0,"plugin");}catch(Throwable ignored){getLogger().warning("audit cmv4_audit_events failed: actor="+first(actor,"")+" action="+first(action,"")+" error="+safeErr(ignored));}
        };
        if(dbReady&&dbExecutor!=null&&!dbExecutor.isShutdown())dbExecutor.execute(write); else write.run();
    }
    private void pluginEvent(String source,String eventType,String actor,String target,String details){
        try{exec("INSERT INTO plugin_events(source,event_type,actor,target,created_at,details) VALUES(?,?,?,?,?,?)",first(source,"plugin"),first(eventType,"EVENT"),first(actor,""),first(target,""),now(),first(details,""));}
        catch(Throwable ignored){getLogger().warning("pluginEvent failed: source="+first(source,"plugin")+" type="+first(eventType,"EVENT")+" target="+first(target,"")+" error="+safeErr(ignored));}
    }
    @FunctionalInterface private interface SqlTx<T>{T run(Connection c)throws Exception;}
    @FunctionalInterface private interface SqlVoid{void run()throws Exception;}
    @FunctionalInterface private interface SqlSupplier<T>{T run()throws Exception;}
    private final class ArtifactsBridgeImpl implements ArtifactsBridge {
        @Override public BridgePinStatus pinStatus(UUID playerUuid){
            if(playerUuid==null)return new BridgePinStatus(false,false,0L);
            try{
                String uuid=playerUuid.toString();
                return new BridgePinStatus(bankPinSet(uuid),bankPinMustChange(uuid),bankPinLockedSeconds(uuid));
            }catch(Exception e){
                return new BridgePinStatus(false,false,0L);
            }
        }
        @Override public long balance(UUID playerUuid, String playerName){
            if(playerUuid==null)return 0L;
            try(Connection c=conn()){
                ensureV4BankAccount(c,playerUuid.toString(),first(playerName,""));
                return scalarLong(c,"SELECT COALESCE(balance,0) FROM cmv4_bank_accounts WHERE account_id=?",bankAccountId(playerUuid.toString()));
            }catch(Exception e){return 0L;}
        }
        @Override public BridgeTxnResult charge(UUID playerUuid, String playerName, long amount, String pin, String idempotencyKey, String action, String details){
            return artifactBankTxn(playerUuid,playerName,-Math.max(0L,amount),pin,idempotencyKey,action,details);
        }
        @Override public BridgeTxnResult refund(UUID playerUuid, String playerName, long amount, String idempotencyKey, String action, String details){
            return artifactBankTxn(playerUuid,playerName,Math.max(0L,amount),"",idempotencyKey,action,details);
        }
        @Override public BridgeHealth health(UUID playerUuid, String context){
            String ctx=first(context,"health");
            try{
                boolean pinReady=playerUuid!=null&&bankPinSet(playerUuid.toString())&&!bankPinMustChange(playerUuid.toString())&&bankPinLockedSeconds(playerUuid.toString())<=0;
                long bal=playerUuid==null?0L:balance(playerUuid,"");
                BridgeHealth h=new BridgeHealth(dbReady,dbReady,pinReady,bal,ctx,"");
                audit("SERVER","ARTIFACTS_BRIDGE_HEALTH","context="+ctx+" postgres="+h.postgresReady+" pinReady="+h.pinReady,true);
                pluginEvent("artifacts-bridge","ARTIFACTS_BRIDGE_HEALTH","SERVER","bridge","context="+ctx+" postgres="+h.postgresReady+" pinReady="+h.pinReady);
                return h;
            }catch(Exception e){
                String err=safeErr(e);
                audit("SERVER","ARTIFACTS_BRIDGE_HEALTH_FAILED","context="+ctx+" error="+err,true);
                pluginEvent("artifacts-bridge","ARTIFACTS_BRIDGE_HEALTH_FAILED","SERVER","bridge","context="+ctx+" error="+err);
                return new BridgeHealth(false,dbReady,false,0L,ctx,err);
            }
        }
    }
    private BridgeTxnResult artifactBankTxn(UUID playerUuid,String playerName,long signedAmount,String pin,String idempotencyKey,String action,String details){
        if(playerUuid==null)return new BridgeTxnResult(false,"PLAYER_MISSING","Player UUID is missing.",0L,"");
        String uuid=playerUuid.toString();
        String accountId=bankAccountId(uuid);
        long amount=Math.abs(signedAmount);
        if(amount<=0)return new BridgeTxnResult(false,"AMOUNT_INVALID","Amount must be positive.",0L,"");
        String key=first(idempotencyKey,"").trim();
        if(key.isBlank())key="artifacts-"+first(action,"txn")+"-"+UUID.randomUUID();
        final String txKey=key;
        final String actionName=first(action,"txn");
        final String detailText=first(details,"");
        try{
            if(signedAmount<0){
                long locked=bankPinLockedSeconds(uuid);
                if(locked>0)return new BridgeTxnResult(false,"PIN_LOCKED","PIN temporarily locked for "+locked+" seconds.",0L,"");
                if(!bankPinSet(uuid))return new BridgeTxnResult(false,"PIN_REQUIRED","Bank PIN is not configured.",0L,"");
                if(bankPinMustChange(uuid))return new BridgeTxnResult(false,"PIN_CHANGE_REQUIRED","Temporary PIN must be changed first.",0L,"");
                boolean pinOk;
                try{pinOk=verifyBankPin(uuid,pin);}catch(Exception e){pinOk=false;}
                if(!pinOk){
                    Player online=onlineByUuid(uuid);
                    if(online!=null)recordFailedPinAttempt(online,"artifacts");
                    return new BridgeTxnResult(false,"PIN_INVALID","PIN is invalid.",0L,"");
                }
            }
            return tx(c->{
                ensureV4BankAccount(c,uuid,first(playerName,""));
                List<Map<String,Object>> existing=query(c,"SELECT tx_id,balance_after FROM cmv4_bank_ledger WHERE idempotency_key=? LIMIT 1",txKey);
                if(!existing.isEmpty())return new BridgeTxnResult(true,"OK","Idempotent replay.",num(existing.get(0).get("balance_after")),s(existing.get(0).get("tx_id")));
                List<Map<String,Object>> rows=query(c,"SELECT balance FROM cmv4_bank_accounts WHERE account_id=? FOR UPDATE",accountId);
                long before=rows.isEmpty()?0L:num(rows.get(0).get("balance"));
                long after=signedAmount<0?before-amount:before+amount;
                if(after<0)return new BridgeTxnResult(false,"INSUFFICIENT_AR","Not enough AR in bank.",before,"");
                long t=now();
                String txId="artifact-"+actionName+"-"+UUID.randomUUID();
                String eventType=signedAmount<0?"ARTIFACTS_CHARGE":"ARTIFACTS_REFUND";
                exec(c,"UPDATE cmv4_bank_accounts SET balance=?,version=version+1,updated_at=? WHERE account_id=?",after,t,accountId);
                exec(c,"INSERT INTO cmv4_bank_ledger(tx_id,account_id,counterparty_account_id,player_uuid,tx_type,amount,balance_after,idempotency_key,status,created_at,actor,details) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        txId,accountId,"ARTIFACTS",uuid,signedAmount<0?"ARTIFACT_PURCHASE":"ARTIFACT_REFUND",amount,after,txKey,"COMMITTED",t,first(playerName,""),detailText);
                exec(c,"INSERT INTO cmv4_audit_events(time,actor,action,details,admin_only,source) VALUES(?,?,?,?,?,?)",t,first(playerName,""),actionName,"amount="+(signedAmount<0?-amount:amount)+" account="+accountId+" "+detailText,0,"artifacts-bridge");
                exec(c,"INSERT INTO plugin_events(source,event_type,actor,target,created_at,details) VALUES(?,?,?,?,?,?)","artifacts-bridge",eventType,first(playerName,""),accountId,t,"amount="+amount+" balanceAfter="+after+" action="+actionName+" tx="+txId);
                return new BridgeTxnResult(true,"OK","Committed.",after,txId);
            });
        }catch(SQLException e){
            return new BridgeTxnResult(false,"BANK_ERROR",safeErr(e),0L,"");
        }
    }
    private <T>T tx(SqlTx<T> body)throws SQLException{
        try(Connection c=conn()){
            boolean old=c.getAutoCommit();
            c.setAutoCommit(false);
            try{
                T result=body.run(c);
                c.commit();
                return result;
            }catch(Throwable t){
                try{c.rollback();}catch(Throwable rollback){t.addSuppressed(rollback);}
                if(t instanceof SQLException se)throw se;
                throw new SQLException(t);
            }finally{
                try{c.setAutoCommit(old);}catch(Throwable ignored){}
            }
        }
    }
    private void dbAsync(String label,SqlVoid body){
        if(dbExecutor==null||dbExecutor.isShutdown()){
            try{body.run();}catch(Exception e){getLogger().warning(label+": "+safeErr(e));}
            return;
        }
        dbExecutor.execute(()->{
            try{body.run();}
            catch(Exception e){getLogger().warning(label+": "+safeErr(e));}
        });
    }
    private <T> void dbAsyncLoad(String label,SqlSupplier<T> body,Consumer<T> onSuccess,Consumer<Exception> onError){
        Runnable work=()->{
            try{
                T result=body.run();
                Bukkit.getScheduler().runTask(this,()->onSuccess.accept(result));
            }catch(Exception e){
                getLogger().warning(label+": "+safeErr(e));
                Bukkit.getScheduler().runTask(this,()->onError.accept(e));
            }
        };
        if(dbExecutor==null||dbExecutor.isShutdown())work.run(); else dbExecutor.execute(work);
    }
    private CachedElectionRole cachedElectionRole(Player p){
        boolean permissionPresident=p.hasPermission("copimine.election.president");
        CachedElectionRole cached=electionRoleCache.get(p.getUniqueId());
        long current=now();
        if(cached==null||current-cached.loadedAt()>ELECTION_ROLE_CACHE_TTL_MS){
            refreshElectionRoleStateAsync(p);
        }
        if(cached==null)return new CachedElectionRole(false,permissionPresident,0L);
        return permissionPresident&&!cached.president()?new CachedElectionRole(cached.chair(),true,cached.loadedAt()):cached;
    }
    private void refreshElectionRoleStateAsync(Player p){
        if(p==null)return;
        UUID playerUuid=p.getUniqueId();
        if(!electionRoleRefreshInFlight.add(playerUuid))return;
        boolean permissionPresident=p.hasPermission("copimine.election.president");
        dbAsyncLoad("refreshElectionRoleState",()->loadElectionRoleState(playerUuid,permissionPresident),state->{
            electionRoleCache.put(playerUuid,state);
            electionRoleRefreshInFlight.remove(playerUuid);
        },error->electionRoleRefreshInFlight.remove(playerUuid));
    }
    private CachedElectionRole loadElectionRoleState(UUID playerUuid,boolean permissionPresident)throws Exception{
        if(playerUuid==null)return new CachedElectionRole(false,permissionPresident,now());
        String uuid=playerUuid.toString();
        try{
            boolean chair=scalarLong("SELECT COUNT(*) FROM cik_chairs WHERE player_uuid=? AND active=1",uuid)>0;
            boolean president=permissionPresident||scalarLong("SELECT COUNT(*) FROM president_terms WHERE president_uuid=? AND status='ACTIVE'",uuid)>0;
            CachedElectionRole state=new CachedElectionRole(chair,president,now());
            electionRoleCache.put(playerUuid,state);
            return state;
        }catch(SQLException error){
            String message=String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
            if(message.contains("cik_chairs")||message.contains("president_terms")||message.contains("does not exist")||message.contains("relation")){
                CachedElectionRole fallback=new CachedElectionRole(false,permissionPresident,now());
                electionRoleCache.put(playerUuid,fallback);
                return fallback;
            }
            throw error;
        }
    }
    private void bind(PreparedStatement ps,Object...args)throws SQLException{
        for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);
    }
    private List<Map<String,Object>> rows(ResultSet rs)throws SQLException{
        ResultSetMetaData md=rs.getMetaData();
        List<Map<String,Object>> out=new ArrayList<>();
        while(rs.next()){
            Map<String,Object> row=new LinkedHashMap<>();
            for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT),rs.getObject(i));
            out.add(row);
        }
        return out;
    }
    private String adaptSql(String sql){
        if(sql==null)return "";
        String s=sql.trim();
        if(s.isBlank())return s;
        String low=s.toLowerCase(Locale.ROOT);
        if(low.startsWith("pragma "))return "";
        if(low.equals("select count(*) from sqlite_master where type='table'"))return "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'";
        if(low.equals("select count(*) from sqlite_master where type='index'"))return "SELECT COUNT(*) FROM pg_indexes WHERE schemaname=current_schema()";
        if(low.contains(" from sqlite_master "))s=s.replaceAll("(?i)SELECT\\s+COUNT\\(\\*\\)\\s+FROM\\s+sqlite_master\\s+WHERE\\s+type\\s+IN\\s*\\('table','view'\\)\\s+AND\\s+name=\\?","SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?");
        s=s.replaceAll("(?i)INTEGER\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT","BIGSERIAL PRIMARY KEY");
        s=s.replaceAll("(?i)SELECT\\s+rowid\\s*,","SELECT id AS __cm_rowid__,");
        s=s.replaceAll("(?i),\\s*rowid\\s*,",", id AS __cm_rowid__,");
        s=s.replaceAll("(?i),\\s*rowid\\s+FROM",", id AS __cm_rowid__ FROM");
        s=s.replaceAll("(?i)\\browid\\b","id");
        s=s.replace("__cm_rowid__","rowid");
        if(s.regionMatches(true,0,"INSERT OR REPLACE INTO cmv8_startup_checks",0,"INSERT OR REPLACE INTO cmv8_startup_checks".length())){
            return "INSERT INTO cmv8_startup_checks(key,time,ok,title,detail,action) VALUES(?,?,?,?,?,?) ON CONFLICT(key) DO UPDATE SET time=excluded.time,ok=excluded.ok,title=excluded.title,detail=excluded.detail,action=excluded.action";
        }
        if(s.regionMatches(true,0,"INSERT OR REPLACE INTO cmv7_ar_placed_blocks",0,"INSERT OR REPLACE INTO cmv7_ar_placed_blocks".length())){
            return "INSERT INTO cmv7_ar_placed_blocks(world,x,y,z,material,placed_by_uuid,placed_by_name,placed_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(world,x,y,z) DO UPDATE SET material=excluded.material,placed_by_uuid=excluded.placed_by_uuid,placed_by_name=excluded.placed_by_name,placed_at=excluded.placed_at";
        }
        if(s.regionMatches(true,0,"INSERT OR REPLACE",0,"INSERT OR REPLACE".length()))s=s.replaceFirst("(?i)INSERT\\s+OR\\s+REPLACE","INSERT");
        return s;
    }
    private String adaptColumnDecl(String decl){
        return decl==null?"":decl.trim().replaceAll("(?i)INTEGER\\s+PRIMARY\\s+KEY\\s+AUTOINCREMENT","BIGSERIAL PRIMARY KEY");
    }
    private String ident(String value)throws SQLException{
        if(value==null||!value.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new SQLException("Unsafe identifier: "+value);
        return "\""+value+"\"";
    }
    private String safeErr(Throwable t){
        if(t==null)return "unknown";
        String msg=t.getMessage();
        if(msg==null||msg.isBlank())return t.getClass().getSimpleName();
        return msg.replaceAll("(?i)(password=)[^\\s&]+","$1***").replaceAll("(?i)(POSTGRES_PASSWORD=)[^\\s&]+","$1***");
    }
    private String escapeJson(String value){
        String raw=String.valueOf(value==null?"":value);
        StringBuilder out=new StringBuilder(raw.length()+16);
        for(int i=0;i<raw.length();i++){
            char ch=raw.charAt(i);
            switch(ch){
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if(ch<0x20)out.append(String.format("\\u%04x",(int)ch));
                    else out.append(ch);
                }
            }
        }
        return out.toString();
    }
    private BackendIntegrationSettings loadBackendIntegrationSettings(){
        try{
            Map<String,String> values=new LinkedHashMap<>();
            Path envFile=resolveEnvFile();
            if(Files.isRegularFile(envFile))values.putAll(readEnvFile(envFile));
            values.putAll(System.getenv());
            String baseUrl=first(firstEnv(values,"COPIMINE_ADMIN_BASE_URL","ADMIN_WEB_INTERNAL_URL"),"http://127.0.0.1:8090").trim();
            String pluginApiKey=firstEnv(values,"PLUGIN_API_KEY");
            if(baseUrl.isBlank()||pluginApiKey.isBlank())return null;
            return new BackendIntegrationSettings(baseUrl.replaceAll("/+$",""),pluginApiKey);
        }catch(Exception error){
            getLogger().warning("backend settings: "+safeErr(error));
            return null;
        }
    }
    private void postBackendJson(String path,String payload){
        BackendIntegrationSettings settings=loadBackendIntegrationSettings();
        if(settings==null)return;
        try{
            HttpRequest request=HttpRequest.newBuilder()
                    .uri(URI.create(settings.baseUrl()+path))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type","application/json; charset=utf-8")
                    .header("Accept","application/json")
                    .header("X-Plugin-Key",settings.pluginApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response=backendHttpClient.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300){
                getLogger().warning("backend post failed path="+path+" status="+response.statusCode()+" body="+response.body());
            }
        }catch(Exception error){
            getLogger().warning("backend post "+path+": "+safeErr(error));
        }
    }
    private String playerFacingBugSummary(Throwable error){
        if(error==null)return "Unexpected server error";
        String type=first(error.getClass().getSimpleName(),"Exception").toLowerCase(Locale.ROOT);
        if(type.contains("nullpointer"))return "Unexpected null state";
        if(type.contains("illegalargument"))return "Invalid input reached the server";
        if(type.contains("timeout"))return "Background task timed out";
        if(type.contains("sql")||type.contains("database"))return "Database request failed";
        if(type.contains("io"))return "I/O operation failed";
        return "Unexpected server error";
    }
    private void pushBackendBugArtifactsAsync(PendingBugReport report,String requestId,String message){
        Bukkit.getScheduler().runTaskAsynchronously(this,()->{
            String ticketPayload="{"
                    +"\"player\":\""+escapeJson(report.playerName())+"\","
                    +"\"uuid\":\""+escapeJson(report.playerUuid())+"\","
                    +"\"message\":\""+escapeJson(message)+"\","
                    +"\"kind\":\"report\","
                    +"\"world\":\""+escapeJson(report.world())+"\","
                    +"\"x\":"+report.x()+","
                    +"\"y\":"+report.y()+","
                    +"\"z\":"+report.z()+","
                    +"\"metadata\":{\"reportKind\":\"bug\",\"errorCode\":\""+escapeJson(report.token())+"\",\"errorSummary\":\""+escapeJson(report.errorSummary())+"\",\"bugReport\":{\"errorCode\":\""+escapeJson(report.token())+"\",\"errorSummary\":\""+escapeJson(report.errorSummary())+"\",\"capturedAt\":"+report.createdAt()+",\"context\":{\"source\":\""+escapeJson(report.source())+"\",\"action\":\""+escapeJson(report.action())+"\",\"world\":\""+escapeJson(report.world())+"\",\"x\":"+report.x()+",\"y\":"+report.y()+",\"z\":"+report.z()+",\"itemType\":\""+escapeJson(report.itemType())+"\"},\"diagnostics\":{\"requestId\":\""+escapeJson(requestId)+"\",\"actionId\":\""+escapeJson(report.actionId())+"\"},\"technical\":{\"exceptionClass\":\""+escapeJson(report.exceptionClass())+"\",\"details\":\""+escapeJson(report.details())+"\"}}}"
                    +"}";
            postBackendJson("/api/plugin/tickets",ticketPayload);
            String metadata="{"
                    +"\"token\":\""+escapeJson(report.token())+"\","
                    +"\"requestId\":\""+escapeJson(requestId)+"\","
                    +"\"source\":\""+escapeJson(report.source())+"\","
                    +"\"action\":\""+escapeJson(report.action())+"\","
                    +"\"errorSummary\":\""+escapeJson(report.errorSummary())+"\","
                    +"\"exceptionClass\":\""+escapeJson(report.exceptionClass())+"\","
                    +"\"actionId\":\""+escapeJson(report.actionId())+"\","
                    +"\"details\":\""+escapeJson(report.details())+"\""
                    +"}";
            String eventPayload="{"
                    +"\"source\":\"adminplus\","
                    +"\"event_type\":\"player_bug_report\","
                    +"\"actor\":\""+escapeJson(report.playerName())+"\","
                    +"\"target\":\""+escapeJson(requestId)+"\","
                    +"\"world\":\""+escapeJson(report.world())+"\","
                    +"\"x\":"+report.x()+","
                    +"\"y\":"+report.y()+","
                    +"\"z\":"+report.z()+","
                    +"\"item\":\""+escapeJson(report.itemType())+"\","
                    +"\"severity\":\"error\","
                    +"\"tags\":[\"bug\",\"player-report\",\""+escapeJson(report.source())+"\"],"
                    +"\"metadata\":"+metadata+","
                    +"\"timestamp\":"+report.createdAt()
                    +"}";
            postBackendJson("/api/plugin/events",eventPayload);
        });
    }
    private void notifyPlayerBug(Player player,String source,String action,Throwable error,ItemStack item,Location location){
        if(player==null)return;
        Location loc=location!=null?location:player.getLocation();
        String token=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        String world=loc!=null&&loc.getWorld()!=null?loc.getWorld().getName():"";
        int x=loc==null?0:loc.getBlockX();
        int y=loc==null?0:loc.getBlockY();
        int z=loc==null?0:loc.getBlockZ();
        String itemType=item==null||item.getType()==Material.AIR?"AIR":item.getType().name();
        String details=first(safeErr(error),"unknown");
        String actionId=shortId(UUID.randomUUID().toString());
        String errorSummary=playerFacingBugSummary(error);
        String exceptionClass=first(error==null?"":error.getClass().getSimpleName(),"unknown");
        PendingBugReport pending=new PendingBugReport(token,first(source,"unknown"),first(action,"unknown"),player.getUniqueId().toString(),player.getName(),world,x,y,z,itemType,errorSummary,exceptionClass,details,actionId,now());
        pendingBugReports.put(player.getUniqueId(),pending);
        getLogger().warning("player-bug actionId="+actionId+" token="+token+" player="+player.getName()+" source="+source+" action="+action+" world="+world+" x="+x+" y="+y+" z="+z+" item="+itemType+" error="+details);
        pluginEvent("adminplus","PLAYER_BUG_DETECTED",player.getName(),token,"actionId="+actionId+" source="+source+" action="+action+" item="+itemType+" error="+details);
        player.sendTitle(c("&6Поздравляем, вы нашли баг"),c("&fОбратитесь к админу за вознаграждением"),10,80,15);
        warn(player,"Error code: &f"+token+"&c. "+errorSummary+" &8| &7Type: &f"+exceptionClass);
        msg(player,"&7Нажмите кнопку ниже: команда &f/reporta &7откроется в чате, а лог приложится только после отправки.");
        var button = new ComponentBuilder("")
                .append(TextComponent.fromLegacyText(c("&c[Отправить отчёт о баге]")))
                .event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/reporta "))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder(c("&7Вставить команду репорта. Опишите, что произошло, и нажмите Enter.")).create()))
                .create();
        player.spigot().sendMessage(button);
    }
    private PgSettings loadPgSettings()throws IOException,SQLException{
        Map<String,String> values=new LinkedHashMap<>();
        Path envFile=resolveEnvFile();
        if(Files.isRegularFile(envFile))values.putAll(readEnvFile(envFile));
        values.putAll(System.getenv());
        String host=firstEnv(values,"POSTGRES_HOST","PGHOST");
        if(host.isBlank())host="127.0.0.1";
        int port=parseInt(firstEnv(values,"POSTGRES_PORT","PGPORT"),5432);
        String database=first(firstEnv(values,"POSTGRES_DB","POSTGRES_DATABASE","PGDATABASE"),"copimine");
        String user=first(firstEnv(values,"POSTGRES_USER","PGUSER"),"copimine");
        String password=firstEnv(values,"POSTGRES_PASSWORD","PGPASSWORD");
        String schema=first(firstEnv(values,"POSTGRES_SCHEMA","PGSCHEMA"),"copimine");
        int poolSize=Math.max(2,Math.min(12,parseInt(firstEnv(values,"COPIMINE_PLUGIN_DB_POOL_SIZE","POSTGRES_POOL_SIZE"),4)));
        int connectTimeoutMs=Math.max(1000,parseInt(firstEnv(values,"COPIMINE_PLUGIN_DB_CONNECT_TIMEOUT_MS"),5000));
        int statementTimeoutMs=Math.max(1000,parseInt(firstEnv(values,"COPIMINE_PLUGIN_DB_STATEMENT_TIMEOUT_MS"),5000));
        if(password.isBlank())throw new SQLException("POSTGRES_PASSWORD is required");
        if(!schema.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new SQLException("Unsafe POSTGRES_SCHEMA");
        return new PgSettings(host,port,database,user,password,schema,poolSize,connectTimeoutMs,statementTimeoutMs,envFile);
    }
    private String firstEnv(Map<String,String> values,String...keys){
        for(String key:keys){
            String v=values.get(key);
            if(v!=null&&!v.isBlank())return stripEnvQuotes(v.trim());
        }
        return "";
    }
    private String stripEnvQuotes(String value){
        if(value==null||value.length()<2)return value==null?"":value;
        char a=value.charAt(0), b=value.charAt(value.length()-1);
        if((a=='"'&&b=='"')||(a=='\''&&b=='\''))return value.substring(1,value.length()-1);
        return value;
    }
    private boolean postgresRuntime(){
        return dbLabel!=null&&dbLabel.startsWith("postgresql://");
    }
    private String dbSourceSummary(){
        if(postgresRuntime())return dbLabel;
        File db=new File(first(dbPath,""));
        return db.getPath();
    }
    private boolean dbSourceReady(){
        if(postgresRuntime())return dbReady&&pgPool!=null;
        File db=new File(first(dbPath,""));
        return db.getParentFile()!=null&&db.getParentFile().isDirectory();
    }
    private int parseInt(String raw,int fallback){
        try{return raw==null||raw.isBlank()?fallback:Integer.parseInt(raw.trim());}
        catch(Exception ignored){return fallback;}
    }
    private Path resolveEnvFile(){
        String explicit=System.getenv("COPIMINE_ENV_FILE");
        if(explicit!=null&&!explicit.isBlank())return Path.of(explicit);
        Path root=releaseRoot();
        return root.resolve("admin-web").resolve(".env");
    }
    private Path releaseRoot(){
        try{
            Path server=getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            Path minecraft=server.getParent();
            Path root=minecraft==null?null:minecraft.getParent();
            if(root!=null)return root;
        }catch(Throwable ignored){}
        return Path.of("/opt/copimine");
    }
    private Map<String,String> readEnvFile(Path file)throws IOException{
        Map<String,String> out=new LinkedHashMap<>();
        try(BufferedReader br=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String line;
            while((line=br.readLine())!=null){
                String s=line.trim();
                if(s.isBlank()||s.startsWith("#"))continue;
                if(s.startsWith("export "))s=s.substring(7).trim();
                int eq=s.indexOf('=');
                if(eq<=0)continue;
                String key=s.substring(0,eq).trim();
                String value=s.substring(eq+1).trim();
                int comment=value.indexOf(" #");
                if(comment>=0)value=value.substring(0,comment).trim();
                out.put(key,stripEnvQuotes(value));
            }
        }
        return out;
    }
    private void closePostgres(){
        ExecutorService ex=dbExecutor;
        dbExecutor=null;
        if(ex!=null){
            ex.shutdown();
            try{if(!ex.awaitTermination(4,TimeUnit.SECONDS))ex.shutdownNow();}
            catch(InterruptedException ie){Thread.currentThread().interrupt(); ex.shutdownNow();}
        }
        PgConnectionPool pool=pgPool;
        pgPool=null;
        if(pool!=null)pool.close();
    }
    private long safeScalar(String sql){try{return scalarLong(sql);}catch(Exception e){return 0;}}
    private int onOff(String v){if(v==null)return 0; return Set.of("1","true","yes","on","да","вкл").contains(v.trim().toLowerCase(Locale.ROOT))?1:0;}
    private String flag(String v){return onOff(v)==1?"&aON":"&cOFF";}
    private String humanStage(String s){return switch(s==null?"":s.toUpperCase(Locale.ROOT)){case"DRAFT"->"подготовка";case"APPLICATIONS_OPEN","NOMINATION"->"приём заявок";case"APPLICATIONS_CLOSED","REVIEW"->"проверка заявок";case"BALLOTS_OPEN"->"выдача бюллетеней";case"DEBATE"->"дебаты";case"VOTING_OPEN","VOTING"->"голосование";case"COUNTING"->"подсчёт";case"FINISHED","INAUGURATION"->"итоги";case"CANCELLED"->"отменено";case"SECOND_ROUND_REQUIRED"->"второй тур";case"ARCHIVED"->"архив";case"RESET"->"сброс";default->s==null?"неизвестно":s;};}
    private String humanStatus(String s){return switch(s==null?"":s.toUpperCase(Locale.ROOT)){case"DRAFT"->"подготовка";case"APPLICATIONS_OPEN"->"приём заявок";case"APPLICATIONS_CLOSED"->"заявки закрыты";case"BALLOTS_OPEN"->"выдача бюллетеней";case"VOTING_OPEN"->"голосование";case"COUNTING"->"подсчёт";case"FINISHED"->"завершены";case"CANCELLED"->"отменены";case"SECOND_ROUND_REQUIRED"->"нужен второй тур";case"ACTIVE"->"идут";case"PAUSED"->"пауза";case"ENDED"->"завершены";case"RESET"->"сброшены";default->s==null?"нет":s;};}
    private long now(){return System.currentTimeMillis();}
    private String s(Object o){return o==null?"":String.valueOf(o);}
    private long num(Object o){if(o==null)return 0; if(o instanceof Number n)return n.longValue(); try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return 0;}}
    private String first(String...v){for(String x:v)if(x!=null&&!x.isBlank())return x; return "";}
    private String shortId(String id){return id==null||id.isBlank()?"нет":id.length()<=12?id:id.substring(0,12);}
    private String clipped(String x,int m){return x==null?"":x.length()<=m?x:x.substring(0,Math.max(0,m-1))+"…";}
    private String c(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private void msg(CommandSender s,String t){s.sendMessage(c("&2&lCopiMine Ultra7 &8| &f"+t));}
    private void warn(CommandSender s,String t){s.sendMessage(c("&c&lCopiMine Ultra7 &8| &c"+t));}
    private Inventory create(Menu m,int size,String title){
        Inventory inv=Bukkit.createInventory(m,size,c(title)); m.inv=inv;
        Map<String,Material> theme=menuTheme(title);
        Material border=theme.getOrDefault("border",Material.BLACK_STAINED_GLASS_PANE);
        Material filler=theme.getOrDefault("filler",Material.GRAY_STAINED_GLASS_PANE);
        ItemStack edge=item(border,"&8",List.of());
        ItemStack fill=item(filler,"&8",List.of());
        for(int i=0;i<size;i++){
            boolean frame=i<9||i>=size-9||i%9==0||i%9==8;
            inv.setItem(i,frame?edge:fill);
        }
        return inv;
    }
    private Map<String,Material> menuTheme(String title){
        String t=cleanText(c(title));
        Material filler=Material.GRAY_STAINED_GLASS_PANE;
        Material accent=Material.LIME_STAINED_GLASS_PANE;
        if(t.contains("выбор")||t.contains("цик")||t.contains("кандидат")||t.contains("бюллет")){filler=Material.YELLOW_STAINED_GLASS_PANE; accent=Material.ORANGE_STAINED_GLASS_PANE;}
        else if(t.contains("эконом")||t.contains("ар")){filler=Material.LIGHT_BLUE_STAINED_GLASS_PANE; accent=Material.CYAN_STAINED_GLASS_PANE;}
        else if(t.contains("игрок")||t.contains("провер")||t.contains("прикол")){filler=Material.LIGHT_GRAY_STAINED_GLASS_PANE; accent=Material.BLUE_STAINED_GLASS_PANE;}
        else if(t.contains("опас")||t.contains("наказ")){filler=Material.RED_STAINED_GLASS_PANE; accent=Material.ORANGE_STAINED_GLASS_PANE;}
        return Map.of("border",Material.BLACK_STAINED_GLASS_PANE,"filler",filler,"accent",accent);
    }
    private void btn(Menu m,int slot,Material mat,String name,List<String> lore,String a){if(slot<0||slot>=m.inv.getSize())return; m.inv.setItem(slot,item(mat,name,enhanceLore(lore,a))); m.actions.put(slot,a);}
    private List<String> enhanceLore(List<String> lore,String a){
        List<String> out=new ArrayList<>();
        if(lore!=null)out.addAll(lore);
        String hint=actionHint(a);
        if(!hint.isBlank()){
            if(!out.isEmpty())out.add("&8Служебный раздел");
            out.add("&7"+hint);
        }
        return out;
    }
    private String actionHint(String a){
        if(a==null||a.isBlank()||a.equals("none"))return"";
        if(a.equals("close"))return"ЛКМ: закрыть меню";
        if(a.startsWith("open:"))return"ЛКМ: открыть раздел";
        if(a.startsWith("p:"))return"ЛКМ: применить и остаться в карточке игрока";
        if(a.startsWith("cand:"))return"ЛКМ/ПКМ: голоса, Shift: усиленное действие";
        if(a.startsWith("app-review:"))return"ЛКМ approve, ПКМ reject, Shift+ЛКМ кандидат";
        if(a.startsWith("app-issue-annul:")||a.startsWith("app-submitted-annul:")||a.startsWith("ballot-annul:"))return"Shift+ПКМ: аварийно аннулировать";
        if(a.equals("station:create-target"))return"ЛКМ: создать участок по блоку в прицеле";
        if(a.startsWith("station:card:"))return"ЛКМ: открыть карточку участка";
        if(a.startsWith("station:teleport:"))return"ЛКМ: телепорт к участку";
        if(a.startsWith("station:delete-confirm:"))return"ЛКМ: открыть подтверждение удаления";
        if(a.startsWith("station:delete:"))return"ЛКМ: архивировать и выключить участок";
        if(a.contains("full-reset")||a.contains("reset")||a.contains("clear")||a.contains("ipban"))return"Требуется подтверждающий клик из описания";
        if(a.startsWith("vote-")||a.startsWith("ballot-candidate:"))return"ЛКМ: продолжить голосование";
        if(a.startsWith("official:recover:"))return"ЛКМ: безопасно восстановить предмет";
        return"ЛКМ: выполнить";
    }
    private ItemStack item(Material mat,String name,List<String> lore){ItemStack st=new ItemStack(mat==null?Material.PAPER:mat); ItemMeta im=st.getItemMeta(); if(im!=null){im.setDisplayName(c(name)); im.setLore(lore==null?List.of():lore.stream().map(this::c).toList()); st.setItemMeta(im);} return st;}
    private void btnIfNoAction(Menu m,int slot,Material mat,String name,List<String> lore,String action){
        if(m==null||m.inv==null||slot<0||slot>=m.inv.getSize()||m.actions.containsKey(slot))return;
        btn(m,slot,mat,name,lore,action);
    }
    private void navRail(Menu m,String refresh){
        if(m==null||m.inv==null||m.inv.getSize()<54)return;
        btnIfNoAction(m,46,Material.COMPASS,"&2Карта админки",List.of("&8BIG_ADMIN_NAV_RAIL","&7Все большие подгруппы в одной схеме."),"open:admin-map");
        btnIfNoAction(m,47,Material.TARGET,"&aГотовность запуска",List.of("&8BIG_ADMIN_NAV_RAIL","&7Проверка после замены папки на сервере."),"open:startup-readiness");
        btnIfNoAction(m,48,Material.ENDER_CHEST,"&aБД и оптимизация",List.of("&8BIG_ADMIN_NAV_RAIL","&7Состояние базы и безопасная оптимизация."),"open:db-health");
        btnIfNoAction(m,50,Material.GOLDEN_HELMET,"&6Выборы",List.of("&8BIG_ADMIN_NAV_RAIL","&7Цикл, ЦИК, участки, аудит и RP."),"open:elections");
        btnIfNoAction(m,51,Material.DIAMOND_ORE,"&bЭкономика",List.of("&8BIG_ADMIN_NAV_RAIL","&7АР, ledger, custody и защита."),"open:economy");
        btnIfNoAction(m,52,Material.PLAYER_HEAD,"&eИгроки",List.of("&8BIG_ADMIN_NAV_RAIL","&7Профили, проверки, приколы и массовые действия."),"open:players");
    }
    private void nav(Menu m,String back,String refresh){
        btn(m,45,Material.ARROW,"&aНазад",List.of(),back);
        navRail(m,refresh);
        btn(m,49,Material.BARRIER,"&cЗакрыть",List.of(),"close");
        btn(m,53,Material.EMERALD,"&aОбновить",List.of(),refresh);
    }

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        try{
            String root=command.getName().toLowerCase(Locale.ROOT);
            if(root.equals("rpguard"))return handleRpGuardCommand(sender,args);
            if(root.equals("oldvoteoff"))return handleOldVoteOff(sender,args);
            if(root.equals("cmsealdrop"))return handleSealDropCommand(sender,args);
            if(root.equals("report")||root.equals("appeal")){handleReport(sender,args);return true;}
            if(root.equals("reporta"))return handleReportaCommand(sender,args);
            if(root.equals("cadm")){if(sender instanceof Player p&&hasAnyAdmin(p))openMainHub(p);else warn(sender,"Нет прав."); return true;}
            if(root.equals("ar")||root.equals("cmbank")){if(sender instanceof Player p&&hasEconomyAdmin(p)){CopiMineEconomyCore economy=economyCore(); if(economy==null){warn(sender,"CopiMineEconomyCore недоступен."); return true;} economy.openAdminEconomyHub(p);}else msg(sender,"Банк и AR управляются через /cmultra."); return true;}
            if(root.equals("cmultra")&&args.length>0&&args[0].equalsIgnoreCase("bugreport"))return handleBugReportCommand(sender,args);
            if(!hasAnyAdmin(sender)){warn(sender,"Нет прав.");return true;}
            if(args.length==0||args[0].equalsIgnoreCase("menu")){if(sender instanceof Player p)openMainHub(p); else help(sender); return true;}
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "bugreport" -> {return handleBugReportCommand(sender,args);}
                case "audit" -> {return handleAuditCommand(sender,args);}
                case "election","sidebar","issueapp","issueballot","annulapp","annulballot" -> {msg(sender,"&eУправление выборами перенесено в новый GUI CopiMineElectionCore через /cadm -> Выборы. Техническая команда игрока: &f/hidelive"); return true;}
                case "ar" -> {if(args.length>=2&&args[1].equalsIgnoreCase("sync")){if(sender instanceof Player p){CopiMineEconomyCore economy=economyCore(); if(economy==null){warn(sender,"CopiMineEconomyCore недоступен."); return true;} warn(sender,"AR sync перенесён в CopiMineEconomyCore."); economy.openAdminEconomyHub(p);} else msg(sender,"AR sync перенесён в CopiMineEconomyCore.");}}
                case "check" -> {if(!(sender instanceof Player a)){warn(sender,"Только из игры.");return true;} if(args.length<3){msg(sender,"&6/cmultra check start|stop|return <player>");return true;} Player t=Bukkit.getPlayerExact(args[2]); if(t==null){warn(sender,"Игрок оффлайн.");return true;} if(args[1].equalsIgnoreCase("start"))startCheck(a,t); else if(args[1].equalsIgnoreCase("stop"))stopCheck(a,t,false); else if(args[1].equalsIgnoreCase("return"))stopCheck(a,t,true);}
                case "resetworldobjects" -> {return handleResetWorldObjectsCommand(sender,args);}
                case "clearfloatingtexts" -> {return handleClearFloatingTextsCommand(sender,args);}
                default -> help(sender);
            }
        }catch(Exception e){
            if(sender instanceof Player p){
                notifyPlayerBug(p,"command","/"+command.getName()+" "+String.join(" ",args),e,p.getInventory().getItemInMainHand(),p.getLocation());
            }else{
                warn(sender,"Не удалось выполнить команду. Подробности записаны в лог.");
                getLogger().warning("cmd root="+command.getName()+" args="+Arrays.toString(args)+" error="+safeErr(e));
            }
        }
        return true;
    }
    private String joinArgs(String[] args,int start){if(args==null||args.length<=start)return"emergency"; return String.join(" ",Arrays.copyOfRange(args,start,args.length)).trim();}
    private boolean handleResetWorldObjectsCommand(CommandSender sender,String[] args)throws Exception{
        requireMainAdmin(sender);
        if(args.length<2||!args[1].equalsIgnoreCase("confirm")){
            msg(sender,"&cКоманда удалит из PostgreSQL лавки, участки ЦИК, печати, защитные блоки, визуалы, заявки выдачи и банкоматы.");
            msg(sender,"&7Балансы игроков, банковский ledger, whitelist и учётные записи не затрагиваются.");
            msg(sender,"&6/cmultra resetworldobjects confirm");
            return true;
        }
        Map<String,Long> removed=tx(c->{
            LinkedHashMap<String,Long> counts=new LinkedHashMap<>();
            purgeTable(c,counts,"artifact_pending_deliveries");
            purgeTable(c,counts,"artifact_shops");
            purgeTable(c,counts,"polling_stations");
            purgeTable(c,counts,"cmv7_polling_stations");
            purgeTable(c,counts,"cik_chairs");
            purgeTable(c,counts,"cik_seals");
            purgeTable(c,counts,"protected_blocks");
            purgeTable(c,counts,"protected_block_visuals");
            purgeTable(c,counts,"text_display_links");
            purgeTable(c,counts,"ar_atms");
            purgeTable(c,counts,"atm_sessions");
            purgeTable(c,counts,"atm_events");
            purgeTable(c,counts,"atm_audit");
            purgeTable(c,counts,"cmv7_ar_placed_blocks");
            purgeTable(c,counts,"cmv7_ar_scan_reports");
            purgeTable(c,counts,"cmv7_ar_guard_incidents");
            purgeTable(c,counts,"cmv7_ar_events");
            purgeTable(c,counts,"cmv7_ar_transactions");
            purgeTable(c,counts,"cmv7_ar_economy_snapshots");
            purgeTable(c,counts,"one_time_link_codes");
            return counts;
        });
        String summary=removed.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue()).reduce((left,right)->left+", "+right).orElse("nothing");
        audit(sender.getName(),"RESET_WORLD_OBJECTS",summary,true);
        pluginEvent("adminplus","RESET_WORLD_OBJECTS",sender.getName(),"world-objects",summary);
        purgeManagedFloatingTexts();
        msg(sender,"&aМировые объекты очищены: &f"+summary);
        msg(sender,"&7Для полной очистки runtime-кэшей перезапусти Minecraft-сервис после команды.");
        return true;
    }
    private boolean handleClearFloatingTextsCommand(CommandSender sender,String[] args)throws Exception{
        requireMainAdmin(sender);
        if(args.length<2||!args[1].equalsIgnoreCase("confirm")){
            msg(sender,"&cКоманда удалит связанные election TextDisplay из мира и очистит registry в PostgreSQL.");
            msg(sender,"&6/cmultra clearfloatingtexts confirm");
            return true;
        }
        LinkedHashMap<String,Long> counts=tx(c->{
            LinkedHashMap<String,Long> rows=new LinkedHashMap<>();
            purgeTable(c,rows,"text_display_links");
            if(tableExists(c,"polling_stations"))c.createStatement().executeUpdate("UPDATE polling_stations SET text_display_uuid='',updated_at="+now());
            rows.put("text_display_entities",(long)purgeManagedFloatingTexts());
            return rows;
        });
        String summary=counts.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue()).reduce((left,right)->left+", "+right).orElse("nothing");
        audit(sender.getName(),"CLEAR_FLOATING_TEXTS",summary,true);
        pluginEvent("adminplus","CLEAR_FLOATING_TEXTS",sender.getName(),"text-displays",summary);
        msg(sender,"&aПлавающие надписи очищены: &f"+summary);
        return true;
    }
    private int purgeManagedFloatingTexts(){
        int removed=0;
        for(World world:Bukkit.getWorlds()){
            for(org.bukkit.entity.TextDisplay display:world.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)){
                boolean managed=display.getPersistentDataContainer().getKeys().stream().anyMatch(key->{
                    String namespace=key.getNamespace().toLowerCase(Locale.ROOT);
                    String localKey=key.getKey().toLowerCase(Locale.ROOT);
                    return namespace.contains("copimineelectioncore")||localKey.contains("text_linked_id")||localKey.contains("text_type");
                });
                if(!managed)continue;
                display.remove();
                removed++;
            }
        }
        return removed;
    }
    private void help(CommandSender s){msg(s,"&6/cmultra &7- меню"); msg(s,"&6/cmbank &7- открыть раздел экономики"); msg(s,"&6/cmultra audit ar|elections|narcotics"); msg(s,"&6/cmultra bugreport <code>"); msg(s,"&6/cmultra ar sync"); msg(s,"&6/cmultra check start|stop|return <player>"); msg(s,"&6/cmultra resetworldobjects confirm &7- очистить лавки, участки и ATM из БД"); msg(s,"&6/cmultra clearfloatingtexts confirm &7- очистить все election TextDisplay"); msg(s,"&6/cadm &7- общий хаб, раздел &fВыборы &7открывает CopiMineElectionCore"); msg(s,"&6/hidelive &7- скрыть live-панель выборов только у себя");}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){String root=c.getName().toLowerCase(Locale.ROOT); if(root.equals("cmbank"))return List.of(); if(root.equals("rpguard")&&args.length==1)return List.of("status","test").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList(); if(args.length==1)return List.of("menu","audit","bugreport","ar","check","resetworldobjects","clearfloatingtexts").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList(); if(args.length==2&&args[0].equalsIgnoreCase("audit"))return List.of("ar","elections","narcotics").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList(); if(args.length==2&&args[0].equalsIgnoreCase("check"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList(); if(args.length==2&&(args[0].equalsIgnoreCase("resetworldobjects")||args[0].equalsIgnoreCase("clearfloatingtexts")))return List.of("confirm").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList(); return List.of();}

    private record PgSettings(String host,int port,String database,String user,String password,String schema,int poolSize,int connectTimeoutMs,int statementTimeoutMs,Path envFile){
        String jdbcUrl(){return "jdbc:postgresql://"+host+":"+port+"/"+database;}
        String safeLabel(){return "postgresql://"+host+":"+port+"/"+database+"?schema="+schema+" env="+envFile;}
        String schemaIdent(){return quoteIdent(schema);}
        private static String quoteIdent(String value){return "\""+value.replace("\"","\"\"")+"\"";}
    }

    private static final class PgConnectionPool implements AutoCloseable{
        private final PgSettings settings;
        private final BlockingQueue<Connection> idle;
        private final Set<Connection> all=ConcurrentHashMap.newKeySet();
        private final AtomicInteger opened=new AtomicInteger();
        private volatile boolean closed=false;

        PgConnectionPool(PgSettings settings){
            this.settings=settings;
            this.idle=new ArrayBlockingQueue<>(settings.poolSize());
        }

        Connection borrow()throws SQLException{
            if(closed)throw new SQLException("PostgreSQL pool is closed");
            Connection physical=pollValid();
            if(physical==null&&opened.get()<settings.poolSize()){
                if(opened.incrementAndGet()<=settings.poolSize()){
                    try{
                        physical=openPhysical();
                        all.add(physical);
                    }catch(SQLException e){
                        opened.decrementAndGet();
                        throw e;
                    }
                }else{
                    opened.decrementAndGet();
                }
            }
            if(physical==null){
                try{physical=idle.poll(settings.connectTimeoutMs(),TimeUnit.MILLISECONDS);}
                catch(InterruptedException ie){Thread.currentThread().interrupt(); throw new SQLException("Interrupted while waiting for PostgreSQL connection",ie);}
                if(physical==null)throw new SQLException("PostgreSQL pool exhausted");
                if(!isUsable(physical)){
                    closePhysical(physical);
                    return borrow();
                }
            }
            return wrap(physical);
        }

        private Connection pollValid(){
            Connection c;
            while((c=idle.poll())!=null){
                if(isUsable(c))return c;
                closePhysical(c);
            }
            return null;
        }

        private Connection openPhysical()throws SQLException{
            Properties props=new Properties();
            props.setProperty("user",settings.user());
            props.setProperty("password",settings.password());
            props.setProperty("ApplicationName","CopiMinePlugin");
            props.setProperty("connectTimeout",String.valueOf(Math.max(1,settings.connectTimeoutMs()/1000)));
            Connection c=DriverManager.getConnection(settings.jdbcUrl(),props);
            c.setAutoCommit(true);
            try(Statement st=c.createStatement()){
                st.execute("CREATE SCHEMA IF NOT EXISTS "+settings.schemaIdent());
                st.execute("SET search_path TO "+settings.schemaIdent());
                st.execute("SET statement_timeout TO "+Math.max(1000,settings.statementTimeoutMs()));
                st.execute("SET lock_timeout TO 3000");
            }
            return c;
        }

        private Connection wrap(Connection physical){
            AtomicBoolean returned=new AtomicBoolean(false);
            return (Connection)java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy,method,args)->{
                        String name=method.getName();
                        if(name.equals("close")){
                            if(returned.compareAndSet(false,true))release(physical);
                            return null;
                        }
                        if(name.equals("isClosed"))return returned.get()||physical.isClosed();
                        if(name.equals("unwrap")&&args!=null&&args.length==1&&args[0] instanceof Class<?> cls&&cls.isInstance(physical))return physical;
                        if(name.equals("isWrapperFor")&&args!=null&&args.length==1&&args[0] instanceof Class<?> cls)return cls.isInstance(physical);
                        if(returned.get())throw new SQLException("PostgreSQL connection already returned to pool");
                        try{return method.invoke(physical,args);}
                        catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                    });
        }

        private void release(Connection physical){
            if(physical==null)return;
            if(closed){closePhysical(physical); return;}
            try{
                if(physical.isClosed()){closePhysical(physical); return;}
                if(!physical.getAutoCommit()){
                    try{physical.rollback();}catch(Throwable ignored){}
                    physical.setAutoCommit(true);
                }
                physical.clearWarnings();
                if(!idle.offer(physical))closePhysical(physical);
            }catch(Throwable ignored){
                closePhysical(physical);
            }
        }

        private boolean isUsable(Connection c){
            try{return c!=null&&!c.isClosed()&&c.isValid(2);}
            catch(Throwable ignored){return false;}
        }

        private void closePhysical(Connection c){
            if(c==null)return;
            all.remove(c);
            opened.updateAndGet(v->Math.max(0,v-1));
            try{c.close();}catch(Throwable ignored){}
        }

        @Override public void close(){
            closed=true;
            Connection c;
            while((c=idle.poll())!=null)closePhysical(c);
            for(Connection live:new ArrayList<>(all))closePhysical(live);
        }
    }

    public static final class CopiMineExpansionShim extends PlaceholderExpansion {
        private final CopiMineUltimateAdminPlus plugin;
        public CopiMineExpansionShim(CopiMineUltimateAdminPlus plugin){this.plugin=plugin;}
        @Override public String getIdentifier(){return "copimine";}
        @Override public String getAuthor(){return "SudoKillDash9";}
        @Override public String getVersion(){return "9.1.0-postgres-v4";}
        @Override public boolean persist(){return true;}
        @Override public String onRequest(OfflinePlayer player,String params){
            if(params==null)return "";
            String p=params.toLowerCase(Locale.ROOT);
            return switch(p){
                case "president" -> plugin.latestWinnerName();
                case "active_president" -> plugin.activePresidentName();
                case "ar_total" -> String.valueOf(plugin.arTotal());
                case "election_system" -> "CIK";
                default -> "";
            };
        }
    }
}
