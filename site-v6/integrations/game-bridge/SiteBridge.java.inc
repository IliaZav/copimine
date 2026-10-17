
    /** Website bridge. Source extension: see integrations/game-bridge/README.md. */
    private final SiteBridge siteBridge = new SiteBridge();

    private final class SiteBridge implements Listener {
        private final String version="copimine-site-bridge/1.0.0";
        private Path root;
        private byte[] secret;
        private boolean ready;
        private BukkitTask timer;
        private final Map<String,Map<String,Object>> owned=new ConcurrentHashMap<>();
        private final Map<String,Map<String,Object>> registry=new HashMap<>();
        private final Map<String,Map<String,Object>> bindings=new ConcurrentHashMap<>();
        private final Map<String,List<String>> snapshotHistory=new HashMap<>();
        private long lastProfileSync;
        private final Set<String> extra=Set.of("confetti","snowball","cobweb","slime","sparkle","note","amethyst","chorus","powdersnow","jumpscare","blind","bees","fsb","wind","squid","phantom","fortune","totem","clock","spy","rainbow","paperwork","tinyquest","sneeze","snowcloud","knockback","fakecredits");
        private final Set<String> serverActions=Set.of("db.optimize","db.checkpoint","world.reset-objects","world.clear-floating-texts","game.audit.ar","game.audit.elections","game.audit.restricted","rpguard.status","rpguard.test","legacy-votes.status","legacy-votes.reset-sidebar","legacy-votes.clear-glow","legacy-votes.unlock","election-seal.status");
        private final Set<String> localActions=Set.of("money.adjust","purchase.limit","sessions.revoke");
        private final Set<String> internalActions=Set.of("inventory.refresh","purchase.deliver","sanction.revoke","effect.revert");

        void start() {
            if(!getConfig().getBoolean("site-bridge.enabled",false))return;
            try {
                root=getDataFolder().toPath().resolve("site-bridge").toAbsolutePath().normalize();
                Files.createDirectories(root);
                for(String folder:List.of("incoming","receipts","journal","snapshots","profiles"))Files.createDirectories(root.resolve(folder));
                Path key=Path.of(getConfig().getString("site-bridge.secret-file",""));
                if(!key.isAbsolute())throw new IllegalArgumentException("bridge_secret_path_must_be_absolute");
                secret=BridgeProtocol.secret(key);
                Path state=root.resolve("effects.json");
                if(Files.exists(state)) {
                    Map<String,Object> saved=BridgeProtocol.map(BridgeProtocol.parse(BridgeProtocol.read(state,BridgeProtocol.MAX_RECORD)));
                    for(var e:saved.entrySet())owned.put(e.getKey(),BridgeProtocol.map(e.getValue()));
                }
                Path bindingFile=root.resolve("bindings.json");
                if(Files.exists(bindingFile))for(var item:BridgeProtocol.map(BridgeProtocol.parse(BridgeProtocol.read(bindingFile,BridgeProtocol.MAX_RECORD))).entrySet())bindings.put(item.getKey(),BridgeProtocol.map(item.getValue()));
                Path historyFile=root.resolve("snapshot-history.json");
                if(Files.exists(historyFile))for(var item:BridgeProtocol.map(BridgeProtocol.parse(BridgeProtocol.read(historyFile,BridgeProtocol.MAX_RECORD))).entrySet()){
                    List<String> ids=new ArrayList<>();for(Object id:(List<?>)item.getValue())ids.add(BridgeProtocol.uuid((String)id).toString());snapshotHistory.put(item.getKey(),ids);
                }
                try(var stream=getResource("site-bridge-actions.json")) {
                    if(stream==null)throw new IOException("bridge_registry_missing");
                    Map<String,Object> defs=BridgeProtocol.map(BridgeProtocol.parse(stream.readAllBytes()));
                    for(Object row:(List<?>)defs.get("actions")) {
                        Map<String,Object> def=BridgeProtocol.map(row);
                        registry.put(BridgeProtocol.string(def,"id",100),def);
                    }
                }
                Bukkit.getPluginManager().registerEvents(this,CopiMineUltimateAdminPlus.this);
                ready=true;
                timer=Bukkit.getScheduler().runTaskTimer(CopiMineUltimateAdminPlus.this,this::expire,20,20);
                for(Player player:Bukkit.getOnlinePlayers())applyPersisted(player);
                getLogger().info("Typed website bridge enabled; signed private spool, registry="+registry.size());
            } catch(Exception error) {
                ready=false;getLogger().severe("Website bridge disabled: "+error.getClass().getSimpleName());
            }
        }
        void close() {ready=false;if(timer!=null)timer.cancel();}

        boolean command(CommandSender sender,String[] args) {
            if(!(sender instanceof ConsoleCommandSender)&&!(sender instanceof RemoteConsoleCommandSender)){
                sender.sendMessage("CMWEB_DENIED_CONSOLE_ONLY");return true;
            }
            if(!ready){sender.sendMessage("CMWEB_DISABLED");return true;}
            if(args.length!=3||!Set.of("execute","status").contains(args[1])){sender.sendMessage("CMWEB_INVALID_PROTOCOL");return true;}
            String id=args[2];
            try {
                BridgeProtocol.uuid(id);
                if(args[1].equals("status")){
                    sender.sendMessage(Files.exists(root.resolve("receipts").resolve(id+".json"))?"CMWEB_RECEIPT_AVAILABLE "+id:
                        Files.exists(root.resolve("journal").resolve(id+".json"))?"CMWEB_PENDING "+id:"CMWEB_UNKNOWN "+id);
                    return true;
                }
                if(!Bukkit.isPrimaryThread()) {
                    Bukkit.getScheduler().runTask(CopiMineUltimateAdminPlus.this,()->execute(id));
                } else execute(id);
                sender.sendMessage("CMWEB_RECEIPT_REQUIRED "+id);
            }catch(Exception error){sender.sendMessage("CMWEB_INVALID_PROTOCOL");}
            return true;
        }
        private final class Op {
            final Map<String,Object> body,params,target;
            final String id,action,hash,actor,reason;
            final List<Map<String,Object>> targets=new ArrayList<>();
            final List<Map<String,Object>> snapshots=new ArrayList<>();
            final Map<String,Object> extraResult=new LinkedHashMap<>();
            boolean begun;
            Long resolvedExpiry;
            Op(BridgeProtocol.Envelope env) {
                body=env.value();hash=BridgeProtocol.sha(env.body());
                id=BridgeProtocol.uuid(BridgeProtocol.string(body,"operationId",36)).toString();
                action=BridgeProtocol.string(body,"action",100);
                params=BridgeProtocol.map(body.getOrDefault("params",Map.of()));
                target=BridgeProtocol.map(body.get("target"));
                actor="SITE:"+BridgeProtocol.optional(body,"actorId","unknown");
                reason=BridgeProtocol.optional(body,"reason","Website administration");
                if("server".equals(target.get("scope"))) {
                    Object values=target.getOrDefault("players",List.of());
                    if(!(values instanceof List<?> list)||list.size()>1000)throw new IllegalArgumentException("invalid_bound_players");
                    for(Object value:list)targets.add(BridgeProtocol.map(value));
                } else targets.add(target);
                Set<String> ids=new HashSet<>();
                for(Map<String,Object> binding:targets) {
                    String uuid=BridgeProtocol.uuid(BridgeProtocol.string(binding,"uuid",36)).toString();
                    if(!ids.add(uuid))throw new IllegalArgumentException("duplicate_bound_player");
                    BridgeProtocol.string(binding,"accountId",100);
                    String name=BridgeProtocol.optional(binding,"name","");
                    if(!name.isEmpty()&&!name.matches("[A-Za-z0-9_]{1,32}"))throw new IllegalArgumentException("invalid_player_name");
                }
                if((action.startsWith("players.")||serverActions.contains(action))&&!"server".equals(target.get("scope")))
                    throw new IllegalArgumentException("server_scope_required");
                if(!(action.startsWith("players.")||serverActions.contains(action))&&targets.size()!=1)
                    throw new IllegalArgumentException("one_verified_target_required");
            }
            UUID uuid(){return BridgeProtocol.uuid(BridgeProtocol.string(target,"uuid",36));}
            Player player(){Player player=Bukkit.getPlayer(uuid());if(player==null||!player.isOnline())throw new IllegalArgumentException("player_offline");return player;}
            String name(){OfflinePlayer p=Bukkit.getOfflinePlayer(uuid());return p.getName()!=null?p.getName():BridgeProtocol.string(target,"name",32);}
            long expiry(){
                if(resolvedExpiry==null) {
                    long value=params.get("expiresAt")==null?0:BridgeProtocol.number(params,"expiresAt",1,Long.MAX_VALUE);
                    if(value==0&&params.get("durationMs")!=null) {
                        long duration=BridgeProtocol.number(params,"durationMs",0,31622400000L);
                        if(duration>0)value=Math.addExact(now(),duration);
                    }
                    resolvedExpiry=value;
                }
                long value=resolvedExpiry;
                if(value!=0&&value<=now())throw new IllegalArgumentException("action_expired_before_execution");
                return value;
            }
        }

        private void execute(String id) {
            Op op=null;
            try {
                Path incoming=root.resolve("incoming").resolve(id+".json");
                op=new Op(BridgeProtocol.open(secret,BridgeProtocol.read(incoming,BridgeProtocol.MAX_INPUT)));
                if(!op.id.equals(id))throw new IllegalArgumentException("operation_path_mismatch");
                Path journal=root.resolve("journal").resolve(id+".json");
                if(Files.exists(journal)) {
                    Map<String,Object> saved=BridgeProtocol.map(BridgeProtocol.parse(BridgeProtocol.read(journal,BridgeProtocol.MAX_RECORD)));
                    if(!op.hash.equals(saved.get("requestSha256"))){getLogger().warning("Website operation payload conflict: "+id);return;}
                    if(Files.exists(root.resolve("receipts").resolve(id+".json")))return;
                    // This one API has its own persistent PG idempotency journal.
                    // Re-entry is a status poll, never a second inventory grant.
                    if("awaiting_factory".equals(saved.get("state"))&&isCustomGive(op)) {
                        op.begun=true;customGive(op);return;
                    }
                    if("awaiting_credit".equals(saved.get("state"))&&Set.of("game.money.credit","game.money.adjust").contains(op.action)){
                        op.begun=true;credit(op);return;
                    }
                    if(!("waiting_preflight".equals(saved.get("state"))&&op.action.equals("purchase.deliver"))&&
                       !("awaiting_snapshot".equals(saved.get("state"))&&snapshotOnly(op)))return;
                }
                validate(op);
                rememberBindings(op);
                if(op.action.equals("purchase.deliver")&&!isCustomGive(op)){
                    Player recipient=Bukkit.getPlayer(op.uuid());
                    if(recipient==null||!recipient.isOnline()){journal(op,"waiting_preflight");return;}
                    try{plannedGive(recipient,vanillaItem(op));}catch(IllegalArgumentException noSpace){
                        if("inventory_has_no_space".equals(noSpace.getMessage())){journal(op,"waiting_preflight");return;}throw noSpace;
                    }
                }
                prepare(op);
                journal(op,"applying");
                op.begun=true;
                if(isCustomGive(op)){journal(op,"awaiting_factory");customGive(op);return;}
                if(Set.of("game.money.credit","game.money.adjust").contains(op.action)){journal(op,"awaiting_credit");credit(op);return;}
                String message=apply(op);
                complete(op,message);
            }catch(Exception error){
                if(op==null){getLogger().warning("Website bridge rejected an unauthenticated or malformed spool record.");return;}
                try {
                    if(!op.begun)receipt(op,"failed","Action rejected before game changes","validation_"+safeCode(error),true);
                    else if(snapshotOnly(op)) {
                        // Re-reading private inventory snapshots has no game side effect.
                        journal(op,"awaiting_snapshot");
                    } else {
                        journal(op,"uncertain");
                        // No failure ACK: a partial side effect must never trigger a purchase refund.
                        getLogger().warning("Website operation awaits review: "+op.id+" "+error.getClass().getSimpleName());
                    }
                }catch(Exception persistenceError){getLogger().severe("Website receipt persistence failed for "+op.id);}
            }
        }
        private String safeCode(Throwable error){
            String message=error.getMessage();
            return message!=null&&message.matches("[a-z0-9_]{1,100}")?message:error.getClass().getSimpleName();
        }
        private void journal(Op op,String state)throws IOException {
            BridgeProtocol.atomic(root.resolve("journal").resolve(op.id+".json"),
                BridgeProtocol.json(BridgeProtocol.object("operationId",op.id,"requestSha256",op.hash,"state",state,"action",op.action,"at",now())));
        }
        private void receipt(Op op,String status,String message,String errorCode,boolean atomicNoDelivery)throws IOException {
            Path receiptPath=root.resolve("receipts").resolve(op.id+".json");
            if(Files.exists(receiptPath)) {
                Map<String,Object> previous=BridgeProtocol.open(secret,BridgeProtocol.read(receiptPath,BridgeProtocol.MAX_RECORD)).value();
                if(!op.id.equals(previous.get("operationId"))||!op.hash.equals(previous.get("requestSha256")))
                    throw new IOException("receipt_identity_conflict");
                // Multiple listeners may observe the same native idempotent future.
                // Preserve the first committed ackId and its exact final payload.
                return;
            }
            Map<String,Object> result=BridgeProtocol.object("message",message.length()>1000?message.substring(0,1000):message,"adapterVersion",version);
            result.putAll(op.extraResult);
            if(errorCode!=null)result.put("errorCode",errorCode);
            if(!op.snapshots.isEmpty())result.put("snapshotId",op.snapshots.get(op.snapshots.size()-1).get("snapshotId"));
            Map<String,Object> receipt=BridgeProtocol.object("operationId",op.id,"requestSha256",op.hash,"ackId",UUID.randomUUID().toString(),
                "status",status,"executedAt",now(),"result",result,"snapshots",op.snapshots,"atomicNoDelivery",atomicNoDelivery);
            BridgeProtocol.atomic(receiptPath,BridgeProtocol.signed(secret,receipt));
            journal(op,status);
        }
        private boolean snapshotOnly(Op op){return Set.of("inventory.refresh","inventory.snapshot","players.snapshot-all").contains(op.action);}
        private void complete(Op op,String message)throws Exception {
            boolean missingSnapshot=false;
            for(Map<String,Object> binding:op.targets) {
                Player player=Bukkit.getPlayer(UUID.fromString((String)binding.get("uuid")));
                if(player!=null&&player.isOnline()) {
                    try {op.snapshots.add(snapshot(player,binding));}
                    catch(Exception captureError) {
                        if(snapshotOnly(op))throw captureError;
                        missingSnapshot=true;
                        getLogger().warning("Post-action snapshot unavailable for "+op.id+" "+captureError.getClass().getSimpleName());
                    }
                }
            }
            if(Set.of("inventory.refresh","inventory.snapshot").contains(op.action)&&op.snapshots.isEmpty())
                throw new IOException("required_snapshot_unavailable");
            receipt(op,"executed",message,missingSnapshot?"post_action_snapshot_unavailable":null,false);
            try {audit(op.actor,"SITE_"+op.action,"operation="+op.id,true);}
            catch(Exception auditError){getLogger().warning("Supplementary website audit unavailable for "+op.id);}
        }
        private void validate(Op op) {
            if(localActions.contains(op.action))throw new IllegalArgumentException("site_transaction_only");
            Map<String,Object> definition=registry.get(op.action);
            if(definition==null&&!internalActions.contains(op.action))throw new IllegalArgumentException("unsupported_action");
            if(definition!=null) {
                Map<String,Object> schema=BridgeProtocol.map(definition.getOrDefault("params",Map.of()));
                Set<String> permitted=new HashSet<>(schema.keySet());
                if(op.action.equals("ban.ip"))permitted.addAll(Set.of("expiresAt","durationMs"));
                if(!permitted.containsAll(op.params.keySet()))throw new IllegalArgumentException("unknown_parameter");
                for(var field:schema.entrySet()){
                    Map<String,Object> spec=BridgeProtocol.map(field.getValue());Object value=op.params.get(field.getKey());
                    if(value==null&&!Boolean.TRUE.equals(spec.get("required")))continue;
                    boolean valid=switch(String.valueOf(spec.get("type"))){
                        case "string"->value instanceof String s&&s.length()<=1000;
                        case "integer"->value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()==n.longValue();
                        case "number"->value instanceof Number n&&Double.isFinite(n.doubleValue());
                        case "boolean"->value instanceof Boolean;default->false;};
                    if(!valid)throw new IllegalArgumentException("invalid_typed_parameter");
                    if(spec.get("enum") instanceof List<?> options&&!options.contains(value))throw new IllegalArgumentException("invalid_enum");
                }
            }
            if(op.params.containsKey("expiresAt")||op.params.containsKey("durationMs"))op.expiry();
            if(op.action.equals("give")||op.action.equals("purchase.deliver")){
                BridgeProtocol.string(op.params,"itemId",100);BridgeProtocol.number(op.params,"quantity",1,64);
            }
            switch(op.action){
                case "world.reset-objects"->{if(!"RESET_WORLD_OBJECTS".equals(op.params.get("confirm")))throw new IllegalArgumentException("explicit_confirmation_required");}
                case "world.clear-floating-texts"->{if(!"CLEAR_FLOATING_TEXTS".equals(op.params.get("confirm")))throw new IllegalArgumentException("explicit_confirmation_required");}
                case "legacy-votes.unlock"->{if(!"CANCEL_LEGACY_VOTES".equals(op.params.get("confirm")))throw new IllegalArgumentException("explicit_confirmation_required");}
                case "gamemode"->GameMode.valueOf(BridgeProtocol.string(op.params,"mode",20).toUpperCase(Locale.ROOT));
                case "fun.sound"->{soundKey(op.params);BridgeProtocol.decimal(op.params,"volume",0,2,1);BridgeProtocol.decimal(op.params,"pitch",.5,2,1);}
                case "fun.firework"->{color(op.params);if(op.params.get("count")!=null)BridgeProtocol.number(op.params,"count",1,10);}
                case "fun.glow"->{color(op.params);if(op.params.get("durationMs")!=null)BridgeProtocol.number(op.params,"durationMs",1,86400000);}
                case "fly","god"->BridgeProtocol.bool(op.params,"enabled");
                case "mute"->{if(!Set.of("text","voice","both").contains(BridgeProtocol.optional(op.params,"channel","both")))throw new IllegalArgumentException("invalid_mute_channel");}
                case "rpguard.test"->{String probe=BridgeProtocol.string(op.params,"probe",200);if(probe.contains("\n")||probe.contains("\r"))throw new IllegalArgumentException("invalid_probe");}
                case "game.money.credit"->{if(!Set.of("AR","CM").contains(op.params.get("currency")))throw new IllegalArgumentException("invalid_currency");BridgeProtocol.number(op.params,"amount",1,1000000000);}
                case "game.money.adjust"->{if(!Set.of("AR","CM").contains(op.params.get("currency"))||!Set.of("credit","debit","set").contains(op.params.get("mode")))throw new IllegalArgumentException("invalid_balance_operation");BridgeProtocol.number(op.params,"amount","set".equals(op.params.get("mode"))?0:1,1000000000);}
                case "sanction.revoke","effect.revert"->{String kind=BridgeProtocol.string(op.params,"kind",30);if(!Set.of("ban","ban.ip","mute","warn","freeze","jail","god","fly").contains(kind))throw new IllegalArgumentException("invalid_revert_kind");BridgeProtocol.uuid(BridgeProtocol.string(op.params,"originalActionId",36));}
            }
        }
        private void prepare(Op op)throws Exception {
            if(serverActions.contains(op.action)||op.action.startsWith("players."))return;
            if(Set.of("ban","mute","warn","sanction.revoke","effect.revert","game.money.credit","game.money.adjust").contains(op.action))return;
            if(isCustomGive(op)){factory();return;}
            Player player=op.player();
            if(op.action.equals("ban.ip")&&(player.getAddress()==null||player.getAddress().getAddress()==null))throw new IllegalArgumentException("player_ip_unavailable");
            if(op.action.startsWith("tp.")){destination(op,player);if(active(player.getUniqueId(),"jail")!=null)throw new IllegalArgumentException("player_jailed");}
            if(op.action.equals("jail"))jailLocation();
            if(op.action.equals("give")||op.action.equals("purchase.deliver")){
                if(isCustomGive(op))factory();else plannedGive(player,vanillaItem(op));
            }
            if(Set.of("inventory.remove","ender.remove","inventory.restore","inventory.armor-off","inventory.clear","inventory.shuffle-hotbar").contains(op.action)){
                checkSnapshot(player,BridgeProtocol.string(op.params,"expectedSnapshotId",128));
                if(op.action.equals("inventory.remove")||op.action.equals("ender.remove")){
                    Inventory inventory=op.action.equals("ender.remove")?player.getEnderChest():player.getInventory();
                    int slot=(int)BridgeProtocol.number(op.params,"slot",0,op.action.equals("ender.remove")?26:40);
                    ItemStack item=inventory.getItem(slot);
                    int quantity=(int)BridgeProtocol.number(op.params,"quantity",1,64);
                    if(item==null||item.getAmount()<quantity||!itemId(item).equals(BridgeProtocol.string(op.params,"expectedItemId",100)))
                        throw new IllegalArgumentException("inventory_slot_changed");
                }
                if(op.action.equals("inventory.restore"))loadSnapshot(player,BridgeProtocol.string(op.params,"snapshotId",128));
                if(op.action.equals("inventory.armor-off"))plannedArmorOff(player);
                // Preserve an additional complete copy immediately before destructive actions.
                snapshot(player,op.target);
            }
        }
        private String apply(Op op)throws Exception {
            String action=op.action;
            CommandSender actor=capture(op.actor,new ArrayList<>());
            if(action.startsWith("players."))return bulk(op);
            if(serverActions.contains(action))return server(op);
            if(action.equals("sanction.revoke")||action.equals("effect.revert")){
                String kind=BridgeProtocol.string(op.params,"kind",30);
                revoke(op.uuid(),kind,BridgeProtocol.string(op.params,"originalActionId",36),BridgeProtocol.optional(op.params,"channel","both"));
                return "Matching operation policy revoked; newer operations preserved";
            }
            if(action.equals("ban")||action.equals("mute")||action.equals("warn"))return sanction(op);
            Player t=op.player();
            switch(action){
                case "ban.ip","freeze","jail","god","fly"->{return sanction(op);}
                case "unfreeze"->{revoke(t.getUniqueId(),"freeze",null,"both");frozen.remove(t.getUniqueId());}
                case "unjail"->revoke(t.getUniqueId(),"jail",null,"both");
                case "kick"->t.kickPlayer(op.reason);
                case "heal"->{t.setHealth(Math.min(t.getMaxHealth(),20));t.setFoodLevel(20);t.setSaturation(20);t.setFireTicks(0);}
                case "feed"->{t.setFoodLevel(20);t.setSaturation(20);}
                case "extinguish"->t.setFireTicks(0);
                case "effect.clear"->{t.setFireTicks(0);for(PotionEffect pe:new ArrayList<>(t.getActivePotionEffects()))t.removePotionEffect(pe.getType());}
                case "gamemode"->t.setGameMode(GameMode.valueOf(BridgeProtocol.string(op.params,"mode",20).toUpperCase(Locale.ROOT)));
                case "tp.coords","tp.spawn","tp.bed","tp.random","tp.up10"->{if(!t.teleport(destination(op,t)))throw new IllegalStateException("teleport_cancelled");}
                case "give","purchase.deliver"->{ItemStack[] planned=plannedGive(t,vanillaItem(op));t.getInventory().setStorageContents(planned);t.saveData();if(!Arrays.equals(BridgeProtocol.json(encoded(planned)),BridgeProtocol.json(encoded(t.getInventory().getStorageContents()))))throw new IllegalStateException("inventory_postcondition_failed");}
                case "inventory.remove","ender.remove"->{
                    Inventory inventory=action.equals("ender.remove")?t.getEnderChest():t.getInventory();
                    int slot=(int)BridgeProtocol.number(op.params,"slot",0,action.equals("ender.remove")?26:40),qty=(int)BridgeProtocol.number(op.params,"quantity",1,64);
                    ItemStack item=inventory.getItem(slot).clone();int remaining=item.getAmount()-qty;
                    if(remaining==0)inventory.setItem(slot,null);else{item.setAmount(remaining);inventory.setItem(slot,item);}t.saveData();
                }
                case "inventory.restore"->{Map<String,Object> saved=loadSnapshot(t,BridgeProtocol.string(op.params,"snapshotId",128));t.getInventory().setContents(decoded(saved.get("items"),41));t.getEnderChest().setContents(decoded(saved.get("enderItems"),27));t.saveData();}
                case "inventory.armor-off"->{ItemStack[] planned=plannedArmorOff(t);t.getInventory().setStorageContents(planned);t.getInventory().setArmorContents(new ItemStack[4]);t.saveData();}
                case "inventory.clear"->{t.getInventory().clear();t.saveData();}
                case "inventory.shuffle-hotbar"->{shuffleHotbar(t);t.saveData();}
                case "inventory.repair-hand"->{repairHand(actor,t);t.saveData();}
                case "inventory.snapshot","inventory.refresh"->{}
                case "inventory.lock"->{inventoryLocks.put(t.getUniqueId(),now()+5000L);t.closeInventory();t.sendTitle(c("&7Инвентарь закрыт"),c("&fПауза 5 секунд"),5,45,10);}
                case "fun.title"->t.sendTitle(BridgeProtocol.string(op.params,"title",160),BridgeProtocol.optional(op.params,"subtitle",""),10,60,10);
                case "fun.sound"->t.playSound(t.getLocation(),soundKey(op.params),(float)BridgeProtocol.decimal(op.params,"volume",0,2,1),(float)BridgeProtocol.decimal(op.params,"pitch",.5,2,1));
                case "fun.firework"->fireworks(t,op);
                case "fun.glow"->glow(t,op);
                case "check-remind"->remind(t);
                case "check-stop","check-stop-return"->stopRemoteCheck(t,op,action.endsWith("return"));
                case "nearby"->{List<String> near=new ArrayList<>();for(Player p:Bukkit.getOnlinePlayers())if(!p.equals(t)&&p.getWorld().equals(t.getWorld())&&p.getLocation().distanceSquared(t.getLocation())<=2304)near.add(p.getName()+" "+Math.round(p.getLocation().distance(t.getLocation()))+"m");return near.isEmpty()?"No players within 48m":String.join(", ",near);}
                case "player.message"->msg(t,"&eАдминистрация: &f"+op.reason);
                case "effects.burn5"->t.setFireTicks(100);
                case "effects.slow10"->{effect(t,10,5,"SLOWNESS","SLOW");effect(t,10,2,"MINING_FATIGUE","SLOW_DIGGING");}
                case "effects.god10"->{effect(t,10,4,"DAMAGE_RESISTANCE","RESISTANCE");effect(t,10,2,"REGENERATION");}
                default->{
                    if(!action.startsWith("fun."))throw new IllegalArgumentException("unsupported_action");
                    String source=action.substring(4);
                    if(extra.contains(source))handleExtraPrank(actor,t,source);else sourcePrank(t,source);
                }
            }
            return action+" applied to verified UUID "+t.getUniqueId();
        }

        private String soundKey(Map<String,Object> params){
            String key=BridgeProtocol.string(params,"sound",120).toLowerCase(Locale.ROOT);
            if(!key.matches("(minecraft:)?[a-z0-9_.]+"))throw new IllegalArgumentException("invalid_sound");
            if(!key.contains(":"))key="minecraft:"+key;
            if(Registry.SOUNDS.get(NamespacedKey.fromString(key))==null)throw new IllegalArgumentException("unknown_sound");
            return key;
        }
        private Color color(Map<String,Object> params){
            String color=BridgeProtocol.optional(params,"color","#f4b942");
            if(!color.matches("#?[a-fA-F0-9]{6}"))throw new IllegalArgumentException("invalid_color");
            return Color.fromRGB(Integer.parseInt(color.replace("#",""),16));
        }
        private void fireworks(Player p,Op op){
            int count=op.params.get("count")==null?1:(int)BridgeProtocol.number(op.params,"count",1,10);
            for(int i=0;i<count;i++){
                Firework f=p.getWorld().spawn(p.getLocation().add(0,1,0),Firework.class);
                f.getPersistentDataContainer().set(new NamespacedKey(CopiMineUltimateAdminPlus.this,"site_visual_firework"),PersistentDataType.BYTE,(byte)1);
                FireworkMeta meta=f.getFireworkMeta();meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(color(op.params)).withTrail().build());meta.setPower(0);f.setFireworkMeta(meta);
                Bukkit.getScheduler().runTaskLater(CopiMineUltimateAdminPlus.this,()->{if(f.isValid())f.detonate();},2);
            }
        }
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
        public void noFireworkDamage(EntityDamageByEntityEvent event){
            if(event.getDamager() instanceof Firework f&&f.getPersistentDataContainer().has(new NamespacedKey(CopiMineUltimateAdminPlus.this,"site_visual_firework"),PersistentDataType.BYTE))event.setCancelled(true);
        }
        private void glow(Player p,Op op){
            int ticks=(int)Math.max(1,Math.min(1728000,(op.params.get("durationMs")==null?30000:BridgeProtocol.number(op.params,"durationMs",1,86400000))/50));
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,ticks,0));
            if(!op.params.containsKey("color"))return;
            Color wanted=color(op.params);ChatColor[] options={ChatColor.BLACK,ChatColor.DARK_BLUE,ChatColor.DARK_GREEN,ChatColor.DARK_AQUA,ChatColor.DARK_RED,ChatColor.DARK_PURPLE,ChatColor.GOLD,ChatColor.GRAY,ChatColor.DARK_GRAY,ChatColor.BLUE,ChatColor.GREEN,ChatColor.AQUA,ChatColor.RED,ChatColor.LIGHT_PURPLE,ChatColor.YELLOW,ChatColor.WHITE};
            int[] colors={0x000000,0x0000aa,0x00aa00,0x00aaaa,0xaa0000,0xaa00aa,0xffaa00,0xaaaaaa,0x555555,0x5555ff,0x55ff55,0x55ffff,0xff5555,0xff55ff,0xffff55,0xffffff};
            int chosen=0;double distance=Double.MAX_VALUE;
            for(int i=0;i<colors.length;i++){Color c=Color.fromRGB(colors[i]);double d=Math.pow(c.getRed()-wanted.getRed(),2)+Math.pow(c.getGreen()-wanted.getGreen(),2)+Math.pow(c.getBlue()-wanted.getBlue(),2);if(d<distance){distance=d;chosen=i;}}
            String teamName="cmw"+op.id.replace("-","").substring(0,12);ChatColor selected=options[chosen];
            Set<Scoreboard> boards=new HashSet<>();boards.add(Bukkit.getScoreboardManager().getMainScoreboard());for(Player viewer:Bukkit.getOnlinePlayers())boards.add(viewer.getScoreboard());
            for(Scoreboard board:boards){
                Team previous=board.getEntryTeam(p.getName());String previousName=previous==null?null:previous.getName();
                Team team=board.getTeam(teamName);if(team==null)team=board.registerNewTeam(teamName);team.setColor(selected);team.addEntry(p.getName());Team created=team;
                Bukkit.getScheduler().runTaskLater(CopiMineUltimateAdminPlus.this,()->{try{if(board.getEntryTeam(p.getName())==created){created.removeEntry(p.getName());Team old=previousName==null?null:board.getTeam(previousName);if(old!=null)old.addEntry(p.getName());}created.unregister();}catch(IllegalStateException ignored){}},ticks);
            }
        }
        private void sourcePrank(Player t,String action){
            switch(action){
                case "launch"->{t.setVelocity(t.getVelocity().setY(1.45));sound(t,"ENTITY_FIREWORK_ROCKET_LAUNCH",1f,1f);}
                case "levitate"->effect(t,5,1,"LEVITATION");
                case "dark"->{effect(t,6,0,"BLINDNESS");effect(t,6,1,"SLOWNESS","SLOW");}
                case "fakeop"->t.sendMessage(c("&7[Server: Made "+t.getName()+" a server operator]"));
                case "panic"->{t.sendTitle(c("&c&lВНИМАНИЕ"),c("&fСрочная проверка реакции"),0,45,10);sound(t,"ENTITY_ENDER_DRAGON_GROWL",.45f,1.7f);}
                case "confuse"->{effect(t,7,0,"NAUSEA","CONFUSION");effect(t,3,0,"BLINDNESS");sound(t,"ENTITY_ILLUSIONER_CAST_SPELL",.8f,1.2f);}
                case "pumpkin"->t.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                case "scare"->{sound(t,"ENTITY_CREEPER_PRIMED",1f,.55f);Bukkit.getScheduler().runTaskLater(CopiMineUltimateAdminPlus.this,()->sound(t,"ENTITY_WITHER_SPAWN",.7f,.7f),18);}
                case "lightning"->t.getWorld().strikeLightningEffect(t.getLocation());
                case "explosion"->t.getWorld().createExplosion(t.getLocation(),0F,false,false);
                case "spin"->spin(t);case "chickens"->chickens(t);case "anvil"->sound(t,"BLOCK_ANVIL_LAND",1f,.8f);
                case "taxpaper"->giveTaxPaper(t);case "potato"->givePotato(t);
                case "bee"->{sound(t,"ENTITY_BEE_LOOP_AGGRESSIVE",1f,1.8f);t.sendTitle(c("&eБзззз..."),c("&7Где-то рядом пчёлы"),5,45,10);}
                case "warden"->{sound(t,"ENTITY_WARDEN_SONIC_BOOM",1f,.9f);t.sendTitle(c("&9&lТссс..."),c("&7Варден услышал тебя"),5,55,10);}
                case "nausea"->effect(t,8,0,"NAUSEA","CONFUSION");
                case "fireworkfake"->{sound(t,"ENTITY_FIREWORK_ROCKET_LAUNCH",1f,1f);sound(t,"ENTITY_FIREWORK_ROCKET_TWINKLE",1f,1.2f);}
                case "fakeban"->{t.sendTitle(c("&4&lBAN"),c("&cШутка. Но правила лучше соблюдать."),5,70,15);sound(t,"ENTITY_WITHER_DEATH",.7f,.8f);}
                case "horn"->sound(t,"ITEM_GOAT_HORN_SOUND_0",1f,1f);
                default->throw new IllegalArgumentException("unsupported_prank");
            }
        }

        private String stateKey(UUID uuid,String kind){return uuid+"/"+kind;}
        private Map<String,Object> active(UUID uuid,String kind){
            Map<String,Object> state=owned.get(stateKey(uuid,kind));
            if(state==null||Boolean.TRUE.equals(state.get("revertPending")))return null;
            long expires=((Number)state.getOrDefault("expiresAt",0L)).longValue();
            return expires==0||expires>now()?state:null;
        }
        private void saveState()throws IOException{BridgeProtocol.atomic(root.resolve("effects.json"),BridgeProtocol.json(owned));}
        void externalChange(UUID uuid,String kind){
            if(!ready)return;owned.remove(stateKey(uuid,kind));
            try{saveState();}catch(IOException error){getLogger().warning("Website effect ownership persistence failed.");}
        }
        private Map<String,Object> policy(Op op,String kind){
            Map<String,Object> previous=owned.get(stateKey(op.uuid(),kind));
            Map<String,Object> state=BridgeProtocol.object("operationId",op.id,"uuid",op.uuid().toString(),"kind",kind,
                "name",op.name(),"expiresAt",op.expiry(),"createdAt",now());
            if(previous!=null)for(String field:List.of("previous","previousFlying","previousVoiceUntil","return"))if(previous.containsKey(field))state.put(field,previous.get(field));
            return state;
        }
        private String sanction(Op op)throws Exception {
            UUID uuid=op.uuid();Player p=Bukkit.getPlayer(uuid);String kind=op.action;
            if(kind.equals("mute")){
                String channel=BridgeProtocol.optional(op.params,"channel","both");
                for(String selected:List.of("text","voice"))if(channel.equals(selected)||channel.equals("both")){
                    String key="mute:"+selected;Map<String,Object> state=policy(op,key);
                    if(selected.equals("voice"))state.putIfAbsent("previousVoiceUntil",voiceMutes.getOrDefault(uuid,0L));
                    owned.put(stateKey(uuid,key),state);
                }
                saveState();
                if(!channel.equals("text")){voiceMutes.put(uuid,op.expiry()==0?Long.MAX_VALUE:op.expiry());saveVoiceMutes();}
                if(p!=null)msg(p,"&cЧат ограничен администрацией: "+op.reason);
                return "Selected chat channels muted for the verified UUID";
            }
            Map<String,Object> state=policy(op,kind);
            if(kind.equals("ban")){
                owned.put(stateKey(uuid,kind),state);saveState();
                org.bukkit.ban.ProfileBanList list=(org.bukkit.ban.ProfileBanList)Bukkit.getBanList(BanList.Type.PROFILE);
                var profile=Bukkit.createPlayerProfile(uuid,op.name());
                list.addBan(profile,op.reason,op.expiry()==0?null:new Date(op.expiry()),"CMWEB:"+op.id);
                if(!list.isBanned(profile))throw new IllegalStateException("ban_postcondition_failed");
                if(p!=null)p.kickPlayer(op.reason);
            }else if(kind.equals("ban.ip")){
                if(p==null||p.getAddress()==null)throw new IllegalArgumentException("player_ip_unavailable");
                String ip=p.getAddress().getAddress().getHostAddress();
                state.put("ip",ip);owned.put(stateKey(uuid,kind),state);saveState();
                org.bukkit.ban.IpBanList list=(org.bukkit.ban.IpBanList)Bukkit.getBanList(BanList.Type.IP);
                java.net.InetAddress address=java.net.InetAddress.getByName(ip);
                list.addBan(address,op.reason,op.expiry()==0?null:new Date(op.expiry()),"CMWEB:"+op.id);
                if(!list.isBanned(address))throw new IllegalStateException("ip_ban_postcondition_failed");
                p.kickPlayer(op.reason);
            }else if(kind.equals("warn")){
                owned.put(stateKey(uuid,kind),state);saveState();
                if(p!=null)msg(p,"&cПредупреждение администрации: &f"+op.reason);
            }else if(kind.equals("freeze")){
                state.putIfAbsent("previous",frozen.contains(uuid));owned.put(stateKey(uuid,kind),state);saveState();frozen.add(uuid);
            }else if(kind.equals("jail")){
                if(p==null)throw new IllegalArgumentException("player_offline");
                state.putIfAbsent("return",location(p.getLocation()));state.put("jail",location(jailLocation()));
                owned.put(stateKey(uuid,kind),state);saveState();
                if(!p.teleport(jailLocation()))throw new IllegalStateException("jail_teleport_cancelled");
            }else if(kind.equals("god")||kind.equals("fly")){
                if(p==null)throw new IllegalArgumentException("player_offline");
                boolean enabled=BridgeProtocol.bool(op.params,"enabled");
                if(!enabled){revoke(uuid,kind,null,"both");if(kind.equals("god"))p.setInvulnerable(false);else{p.setFlying(false);p.setAllowFlight(false);}return kind+" disabled";}
                state.putIfAbsent("previous",kind.equals("god")?p.isInvulnerable():p.getAllowFlight());
                if(kind.equals("fly"))state.putIfAbsent("previousFlying",p.isFlying());
                owned.put(stateKey(uuid,kind),state);saveState();
                if(kind.equals("god"))p.setInvulnerable(true);else p.setAllowFlight(true);
            }
            return kind+" policy persisted and applied";
        }
        private void revoke(UUID uuid,String kind,String original,String channel)throws Exception{
            if(kind.equals("mute")){
                for(String selected:List.of("text","voice"))if(channel.equals("both")||channel.equals(selected))revoke(uuid,"mute:"+selected,original,channel);
                return;
            }
            String key=stateKey(uuid,kind);Map<String,Object> state=owned.get(key);
            if(state==null||original!=null&&!original.equals(state.get("operationId")))return;
            Player p=Bukkit.getPlayer(uuid);
            switch(kind){
                case "ban"->{
                    org.bukkit.ban.ProfileBanList list=(org.bukkit.ban.ProfileBanList)Bukkit.getBanList(BanList.Type.PROFILE);
                    var profile=Bukkit.createPlayerProfile(uuid,(String)state.get("name"));var entry=list.getBanEntry(profile);
                    if(entry!=null&&("CMWEB:"+state.get("operationId")).equals(entry.getSource()))list.pardon(profile);
                }
                case "ban.ip"->{
                    org.bukkit.ban.IpBanList list=(org.bukkit.ban.IpBanList)Bukkit.getBanList(BanList.Type.IP);
                    java.net.InetAddress address=java.net.InetAddress.getByName((String)state.get("ip"));var entry=list.getBanEntry(address);
                    if(entry!=null&&("CMWEB:"+state.get("operationId")).equals(entry.getSource()))list.pardon(address);
                }
                case "mute:voice"->{
                    long expires=((Number)state.getOrDefault("expiresAt",0L)).longValue();
                    long expected=expires==0?Long.MAX_VALUE:expires;
                    if(voiceMutes.getOrDefault(uuid,0L)==expected){long prior=((Number)state.getOrDefault("previousVoiceUntil",0L)).longValue();if(prior>now())voiceMutes.put(uuid,prior);else voiceMutes.remove(uuid);saveVoiceMutes();}
                }
                case "freeze"->{if(Boolean.TRUE.equals(state.get("previous")))frozen.add(uuid);else frozen.remove(uuid);}
                case "jail","god","fly"->{
                    if(p==null||!p.isOnline()){Map<String,Object> pending=new LinkedHashMap<>(state);pending.put("revertPending",true);owned.put(key,pending);saveState();return;}
                    if(kind.equals("jail")){
                        Location old=readLocation(state.get("return"));owned.remove(key,state);
                        boolean moved=false;
                        try {moved=p.teleport(old);if(!moved)throw new IllegalStateException("jail_return_cancelled");}
                        finally {if(!moved){owned.putIfAbsent(key,state);saveState();}}
                    }
                    else if(kind.equals("god")){if(p.isInvulnerable())p.setInvulnerable(Boolean.TRUE.equals(state.get("previous")));}
                    else if(p.getAllowFlight()){p.setFlying(false);p.setAllowFlight(Boolean.TRUE.equals(state.get("previous")));if(p.getAllowFlight())p.setFlying(Boolean.TRUE.equals(state.get("previousFlying")));}
                    p.saveData();
                }
            }
            owned.remove(key,state);saveState();
        }
        private void expire(){
            if(!ready)return;
            for(Map<String,Object> state:new ArrayList<>(owned.values())){
                long expires=((Number)state.getOrDefault("expiresAt",0L)).longValue();
                if(expires!=0&&expires<=now()&&!Boolean.TRUE.equals(state.get("revertPending"))){
                    try{revoke(UUID.fromString((String)state.get("uuid")),(String)state.get("kind"),(String)state.get("operationId"),"both");}
                    catch(Exception error){getLogger().warning("Website timed policy could not yet be reverted.");}
                }
            }
            long interval=Math.max(5,Math.min(3600,getConfig().getLong("site-bridge.profile-interval-seconds",30)))*1000L;
            if(now()-lastProfileSync>=interval){lastProfileSync=now();for(Map<String,Object> binding:bindings.values()){
                Player player=Bukkit.getPlayer(UUID.fromString((String)binding.get("uuid")));if(player!=null&&player.isOnline())publishProfile(player,binding,true);
            }}
        }
        private void rememberBindings(Op op)throws IOException{
            boolean changed=false;
            for(Map<String,Object> value:op.targets){String uuid=(String)value.get("uuid");Map<String,Object> clean=BridgeProtocol.object("uuid",uuid,"accountId",value.get("accountId"));
                if(!clean.equals(bindings.get(uuid))){bindings.put(uuid,clean);changed=true;}}
            if(changed)BridgeProtocol.atomic(root.resolve("bindings.json"),BridgeProtocol.json(bindings));
        }
        private void publishProfile(Player player,Map<String,Object> binding,boolean online){
            try{Map<String,Object> value=snapshot(player,binding);Map<String,Object> profile=BridgeProtocol.map(value.get("profile"));profile.put("online",online);value.put("profile",profile);
                BridgeProtocol.atomic(root.resolve("profiles").resolve(player.getUniqueId()+".json"),BridgeProtocol.signed(secret,value));
            }catch(Exception error){getLogger().warning("Website periodic snapshot is unavailable; previous timestamp is retained.");}
        }
        private void applyPersisted(Player p){
            for(Map<String,Object> state:new ArrayList<>(owned.values())){
                if(!p.getUniqueId().toString().equals(state.get("uuid")))continue;
                String kind=(String)state.get("kind");
                try{
                    long expires=((Number)state.getOrDefault("expiresAt",0L)).longValue();
                    if(Boolean.TRUE.equals(state.get("revertPending"))||expires!=0&&expires<=now()){
                        revoke(p.getUniqueId(),kind,(String)state.get("operationId"),"both");continue;
                    }
                    switch(kind){
                        case "freeze"->frozen.add(p.getUniqueId());
                        case "jail"->p.teleport(readLocation(state.get("jail")));
                        case "god"->p.setInvulnerable(true);
                        case "fly"->p.setAllowFlight(true);
                        case "mute:voice"->voiceMutes.put(p.getUniqueId(),expires==0?Long.MAX_VALUE:expires);
                    }
                }catch(Exception error){getLogger().warning("Website policy application awaits the next join.");}
            }
        }
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
        public void textMute(AsyncPlayerChatEvent event){
            if(ready&&active(event.getPlayer().getUniqueId(),"mute:text")!=null){event.setCancelled(true);event.getPlayer().sendMessage("Чат ограничен администрацией.");}
        }
        @EventHandler(priority=EventPriority.MONITOR)
        public void joined(PlayerJoinEvent event){if(ready)Bukkit.getScheduler().runTask(CopiMineUltimateAdminPlus.this,()->{Player player=event.getPlayer();applyPersisted(player);Map<String,Object> binding=bindings.get(player.getUniqueId().toString());if(binding!=null)publishProfile(player,binding,true);});}
        @EventHandler(priority=EventPriority.MONITOR)
        public void left(PlayerQuitEvent event){if(ready){Map<String,Object> binding=bindings.get(event.getPlayer().getUniqueId().toString());if(binding!=null)publishProfile(event.getPlayer(),binding,false);}}
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
        public void jailMove(PlayerMoveEvent event){
            if(!ready||event.getTo()==null)return;Map<String,Object> state=active(event.getPlayer().getUniqueId(),"jail");if(state==null)return;
            try{Location jail=readLocation(state.get("jail"));Location to=event.getTo();
                double radius=Math.max(1,Math.min(64,getConfig().getDouble("site-bridge.jail.radius",8)));
                if(to.getWorld()!=jail.getWorld()||to.distanceSquared(jail)>radius*radius)event.setTo(event.getFrom());
            }catch(Exception error){event.setTo(event.getFrom());}
        }
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
        public void jailTeleport(PlayerTeleportEvent event){
            if(!ready||event.getTo()==null)return;Map<String,Object> state=active(event.getPlayer().getUniqueId(),"jail");if(state==null)return;
            try{Location jail=readLocation(state.get("jail"));if(event.getTo().getWorld()!=jail.getWorld()||event.getTo().distanceSquared(jail)>4096)event.setCancelled(true);}
            catch(Exception error){event.setCancelled(true);}
        }
        private Map<String,Object> location(Location loc){return BridgeProtocol.object("world",loc.getWorld().getUID().toString(),"x",loc.getX(),"y",loc.getY(),"z",loc.getZ(),"yaw",(double)loc.getYaw(),"pitch",(double)loc.getPitch());}
        private Location readLocation(Object value){
            Map<String,Object> loc=BridgeProtocol.map(value);World world=Bukkit.getWorld(UUID.fromString(BridgeProtocol.string(loc,"world",36)));
            if(world==null)throw new IllegalArgumentException("world_unavailable");
            return new Location(world,((Number)loc.get("x")).doubleValue(),((Number)loc.get("y")).doubleValue(),((Number)loc.get("z")).doubleValue(),
                ((Number)loc.getOrDefault("yaw",0)).floatValue(),((Number)loc.getOrDefault("pitch",0)).floatValue());
        }
        private World world(String canonical){
            World.Environment environment=switch(canonical){case "overworld"->World.Environment.NORMAL;case "nether"->World.Environment.NETHER;case "end"->World.Environment.THE_END;default->throw new IllegalArgumentException("invalid_world");};
            String configured=getConfig().getString("site-bridge.worlds."+canonical,"");
            if(!configured.isEmpty()){World world=Bukkit.getWorld(configured);if(world==null)throw new IllegalArgumentException("world_unavailable");return world;}
            return Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==environment).findFirst().orElseThrow(()->new IllegalArgumentException("world_unavailable"));
        }
        private String canonicalWorld(World world){return switch(world.getEnvironment()){case NETHER->"nether";case THE_END->"end";default->"overworld";};}
        private Location jailLocation(){
            if(!getConfig().isConfigurationSection("site-bridge.jail"))throw new IllegalArgumentException("jail_location_not_configured");
            World w=world(getConfig().getString("site-bridge.jail.world","overworld"));
            Location location=new Location(w,getConfig().getDouble("site-bridge.jail.x"),getConfig().getDouble("site-bridge.jail.y"),getConfig().getDouble("site-bridge.jail.z"));
            validLocation(location);return location;
        }
        private void validLocation(Location location){
            World w=location.getWorld();
            if(location.getY()<w.getMinHeight()||location.getY()>=w.getMaxHeight()||!w.getWorldBorder().isInside(location))throw new IllegalArgumentException("destination_outside_world");
        }
        private Location destination(Op op,Player p){
            String target=BridgeProtocol.optional(op.params,"world",canonicalWorld(p.getWorld()));
            World world=world(target);Location loc;
            switch(op.action){
                case "tp.coords"->loc=new Location(world,BridgeProtocol.decimal(op.params,"x",-29999984,29999984,Double.NaN),BridgeProtocol.decimal(op.params,"y",world.getMinHeight(),world.getMaxHeight()-1,Double.NaN),BridgeProtocol.decimal(op.params,"z",-29999984,29999984,Double.NaN));
                case "tp.bed"->{loc=p.getRespawnLocation();if(loc==null||op.params.containsKey("world")&&loc.getWorld()!=world)throw new IllegalArgumentException("respawn_location_unavailable");}
                case "tp.spawn"->loc=world.getSpawnLocation();
                case "tp.up10"->loc=p.getLocation().clone().add(0,10,0);
                case "tp.random"->{double angle=rnd.nextDouble()*Math.PI*2,distance=12+rnd.nextDouble()*28;loc=p.getLocation().clone().add(Math.cos(angle)*distance,0,Math.sin(angle)*distance);loc.setY(Math.max(loc.getWorld().getMinHeight()+2,loc.getWorld().getHighestBlockYAt(loc)+1));}
                default->throw new IllegalArgumentException("invalid_teleport");
            }
            validLocation(loc);return loc;
        }
        private void remind(Player p){p.sendTitle(c("&c&lПРОВЕРКА"),c("&fСтой на месте и отвечай администрации"),10,80,10);sound(p,"BLOCK_NOTE_BLOCK_PLING",1f,.8f);}
        private void stopRemoteCheck(Player p,Op op,boolean back)throws Exception{
            checkMode.remove(p.getUniqueId());frozen.remove(p.getUniqueId());Location old=checkReturn.remove(p.getUniqueId());
            if(back&&old!=null&&!p.teleport(old))throw new IllegalStateException("check_return_cancelled");
            exec("INSERT INTO cmv7_player_checks(time,admin_uuid,admin_name,player_uuid,player_name,action,active,details) VALUES(?,?,?,?,?,'STOP',0,?)",
                now(),"site",op.actor,p.getUniqueId().toString(),p.getName(),back?"site return":"site no return");
            msg(p,"&aПроверка завершена.");
        }

        private List<String> encoded(ItemStack[] items){
            List<String> data=new ArrayList<>();
            for(ItemStack item:items)data.add(item==null||item.getType().isAir()?null:Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            return data;
        }
        private ItemStack[] decoded(Object value,int maximum){
            if(!(value instanceof List<?> data)||data.size()!=maximum)throw new IllegalArgumentException("invalid_snapshot_shape");
            ItemStack[] items=new ItemStack[maximum];
            for(int i=0;i<items.length;i++)if(data.get(i)!=null)items[i]=ItemStack.deserializeBytes(Base64.getDecoder().decode((String)data.get(i)));
            return items;
        }
        private String inventoryHash(Player player){
            return BridgeProtocol.sha(BridgeProtocol.json(BridgeProtocol.object("inventory",encoded(player.getInventory().getContents()),"ender",encoded(player.getEnderChest().getContents()))));
        }
        private String itemId(ItemStack item){
            Plugin artifacts=Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
            if(artifacts!=null)try{
                Object id=artifacts.getClass().getMethod("siteBridgeItemId",ItemStack.class).invoke(artifacts,item);
                if(id instanceof String text&&!text.isEmpty())return text;
            }catch(ReflectiveOperationException ignored){}
            return item.getType().getKey().toString();
        }
        private List<Map<String,Object>> publicItems(ItemStack[] items){
            List<Map<String,Object>> values=new ArrayList<>();
            for(int slot=0;slot<items.length;slot++){
                ItemStack item=items[slot];if(item==null||item.getType().isAir())continue;
                String name=item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?ChatColor.stripColor(item.getItemMeta().getDisplayName()):item.getType().name();
                if(item.getAmount()<1)throw new IllegalArgumentException("invalid_snapshot_stack_size");
                values.add(BridgeProtocol.object("slot",slot,"id",itemId(item),"quantity",item.getAmount(),"name",name.length()>160?name.substring(0,160):name));
            }
            return values;
        }
        private Map<String,Object> snapshot(Player player,Map<String,Object> binding)throws IOException{
            String id=UUID.randomUUID().toString();Location loc=player.getLocation();
            Map<String,Object> saved=BridgeProtocol.object("snapshotId",id,"uuid",player.getUniqueId().toString(),"inventoryHash",inventoryHash(player),
                "items",encoded(player.getInventory().getContents()),"enderItems",encoded(player.getEnderChest().getContents()),"capturedAt",now());
            BridgeProtocol.atomic(root.resolve("snapshots").resolve(id+".json"),BridgeProtocol.json(saved));
            List<String> history=snapshotHistory.computeIfAbsent(player.getUniqueId().toString(),key->new ArrayList<>());history.add(id);
            int retained=Math.max(16,Math.min(4096,getConfig().getInt("site-bridge.snapshot-retention-count",256)));
            while(history.size()>retained)Files.deleteIfExists(root.resolve("snapshots").resolve(history.remove(0)+".json"));
            BridgeProtocol.atomic(root.resolve("snapshot-history.json"),BridgeProtocol.json(snapshotHistory));
            Map<String,Object> profile=BridgeProtocol.object("world",canonicalWorld(player.getWorld()),"x",loc.getX(),"y",loc.getY(),"z",loc.getZ(),
                "health",player.getHealth(),"food",player.getFoodLevel(),"experience",player.getTotalExperience(),
                "playtime",player.getStatistic(Statistic.PLAY_ONE_MINUTE)/20,"online",player.isOnline(),
                "mapVisible",!player.hasPermission("copimine.map.hidden"),"gamemode",player.getGameMode().name().toLowerCase(Locale.ROOT));
            return BridgeProtocol.object("playerId",binding.get("accountId"),"gameUUID",player.getUniqueId().toString(),"snapshotId",id,
                "capturedAt",saved.get("capturedAt"),"inventory",publicItems(player.getInventory().getContents()),"ender",publicItems(player.getEnderChest().getContents()),"profile",profile);
        }
        private Map<String,Object> loadSnapshot(Player player,String id)throws IOException{
            BridgeProtocol.uuid(id);
            Map<String,Object> saved=BridgeProtocol.map(BridgeProtocol.parse(BridgeProtocol.read(root.resolve("snapshots").resolve(id+".json"),BridgeProtocol.MAX_RECORD)));
            if(!player.getUniqueId().toString().equals(saved.get("uuid")))throw new IllegalArgumentException("snapshot_owner_mismatch");
            decoded(saved.get("items"),41);decoded(saved.get("enderItems"),27);return saved;
        }
        private void checkSnapshot(Player player,String id)throws IOException{
            Map<String,Object> saved=loadSnapshot(player,id);
            if(!inventoryHash(player).equals(saved.get("inventoryHash")))throw new IllegalArgumentException("inventory_changed_since_snapshot");
        }
        private boolean isCustomGive(Op op){
            if(!Set.of("give","purchase.deliver").contains(op.action))return false;
            String item=String.valueOf(op.params.get("itemId"));
            return !item.startsWith("minecraft:")&&Material.matchMaterial(item)==null;
        }
        private ItemStack vanillaItem(Op op){
            String id=BridgeProtocol.string(op.params,"itemId",100);Material material=Material.matchMaterial(id);
            if(material==null||material.isAir()||!material.isItem())throw new IllegalArgumentException("item_not_giveable");
            return new ItemStack(material,(int)BridgeProtocol.number(op.params,"quantity",1,64));
        }
        private ItemStack[] addPlanned(ItemStack[] source,ItemStack given){
            ItemStack[] slots=new ItemStack[source.length];for(int i=0;i<source.length;i++)slots[i]=source[i]==null?null:source[i].clone();
            int remaining=given.getAmount(),max=given.getMaxStackSize();
            for(int i=0;i<slots.length&&remaining>0;i++)if(slots[i]!=null&&slots[i].isSimilar(given)){
                int moved=Math.min(remaining,Math.max(0,max-slots[i].getAmount()));slots[i].setAmount(slots[i].getAmount()+moved);remaining-=moved;
            }
            for(int i=0;i<slots.length&&remaining>0;i++)if(slots[i]==null||slots[i].getType().isAir()){
                slots[i]=given.clone();int moved=Math.min(remaining,max);slots[i].setAmount(moved);remaining-=moved;
            }
            if(remaining>0)throw new IllegalArgumentException("inventory_has_no_space");return slots;
        }
        private ItemStack[] plannedGive(Player player,ItemStack item){return addPlanned(player.getInventory().getStorageContents(),item);}
        private ItemStack[] plannedArmorOff(Player player){
            ItemStack[] result=player.getInventory().getStorageContents();
            for(ItemStack armor:player.getInventory().getArmorContents())if(armor!=null&&!armor.getType().isAir())result=addPlanned(result,armor);
            return result;
        }
        private Plugin factory()throws ReflectiveOperationException{
            Plugin plugin=Bukkit.getPluginManager().getPlugin("CopiMineArtifacts");
            if(plugin==null||!plugin.isEnabled())throw new IllegalArgumentException("artifact_plugin_unavailable");
            plugin.getClass().getMethod("siteBridgeGive",UUID.class,String.class,int.class,String.class,String.class,String.class);return plugin;
        }
        @SuppressWarnings("unchecked")
        private void customGive(Op op)throws Exception{
            Plugin plugin=factory();
            Object invoked=plugin.getClass().getMethod("siteBridgeGive",UUID.class,String.class,int.class,String.class,String.class,String.class)
                .invoke(plugin,op.uuid(),BridgeProtocol.string(op.params,"itemId",100),(int)BridgeProtocol.number(op.params,"quantity",1,64),op.id,op.actor,op.reason);
            if(!(invoked instanceof CompletableFuture<?> future))throw new IllegalStateException("invalid_factory_protocol");
            future.whenComplete((value,error)->Bukkit.getScheduler().runTask(CopiMineUltimateAdminPlus.this,()->{
                try{
                    if(error!=null)return;Map<String,Object> result=BridgeProtocol.map(value);
                    if("executed".equals(result.get("status")))complete(op,"Verified artifact delivery and native entitlement committed");
                    else if("failed".equals(result.get("status"))&&Boolean.TRUE.equals(result.get("atomicNoDelivery")))
                        receipt(op,"failed",BridgeProtocol.optional(result,"message","Artifact rejected before delivery"),"artifact_validation_failed",true);
                }catch(Exception receiptError){getLogger().warning("Artifact receipt remains pending for "+op.id);}
            }));
        }
        private void credit(Op op)throws Exception{
            CopiMineEconomyCore economy=economyCore();if(economy==null)throw new IllegalArgumentException("economy_plugin_unavailable");
            String mode=op.action.equals("game.money.credit")?"credit":BridgeProtocol.string(op.params,"mode",20);
            long amount=BridgeProtocol.number(op.params,"amount",mode.equals("set")?0:1,1000000000);
            CompletableFuture<Map<String,Object>> future=economy.siteBridgeAdjust(op.uuid(),op.name(),(String)op.params.get("currency"),mode,amount,op.id,op.actor,op.reason);
            future.whenComplete((result,error)->Bukkit.getScheduler().runTask(CopiMineUltimateAdminPlus.this,()->{
                try{if(error!=null||result==null)return;for(String key:List.of("balanceAfter","currency","transactionId","authority"))if(result.containsKey(key))op.extraResult.put(key,result.get(key));if("executed".equals(result.get("status")))complete(op,"EconomyCore "+op.params.get("currency")+" balance="+result.get("balanceAfter"));
                    else if("failed".equals(result.get("status"))&&Boolean.TRUE.equals(result.get("atomicNoDelivery")))receipt(op,"failed",String.valueOf(result.get("message")),"ledger_rejected",true);
                }catch(Exception receiptError){getLogger().warning("Credit receipt remains pending for "+op.id);}
            }));
        }
        private String bulk(Op op)throws Exception{
            int done=0;
            for(Map<String,Object> binding:op.targets){
                Player p=Bukkit.getPlayer(UUID.fromString((String)binding.get("uuid")));if(p==null||!p.isOnline())continue;
                switch(op.action){
                    case "players.snapshot-all"->{}
                    case "players.heal-all"->{p.setHealth(Math.min(p.getMaxHealth(),20));p.setFoodLevel(20);p.setSaturation(20);p.setFireTicks(0);}
                    case "players.feed-all"->{p.setFoodLevel(20);p.setSaturation(20);}
                    case "players.unfreeze-all"->{revoke(p.getUniqueId(),"freeze",null,"both");frozen.remove(p.getUniqueId());checkMode.remove(p.getUniqueId());checkReturn.remove(p.getUniqueId());}
                    case "players.remind-checks"->{if(checkMode.containsKey(p.getUniqueId()))remind(p);}
                    case "players.save-all"->p.saveData();
                    case "players.night-vision-all"->{effect(p,480,0,"NIGHT_VISION");}
                    case "players.clear-negative-effects-all"->{for(PotionEffect pe:new ArrayList<>(p.getActivePotionEffects()))if(isNegativePotion(pe.getType()))p.removePotionEffect(pe.getType());}
                    default->throw new IllegalArgumentException("unsupported_bulk_action");
                }
                done++;
            }
            return op.action+" applied to "+done+" bound online players";
        }
        private CommandSender capture(String actor,List<String> messages){
            return (CommandSender)java.lang.reflect.Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(proxy,method,args)->{
                return switch(method.getName()){
                    case "getName"->actor;case "getServer"->Bukkit.getServer();case "hasPermission","isPermissionSet","isOp"->true;
                    case "sendMessage"->{if(args!=null)for(Object arg:args){if(arg instanceof String s)messages.add(ChatColor.stripColor(s));else if(arg instanceof String[] many)for(String s:many)messages.add(ChatColor.stripColor(s));}yield null;}
                    case "getEffectivePermissions"->Set.of();case "spigot"->new CommandSender.Spigot();case "toString"->actor;
                    case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];
                    default->null;
                };
            });
        }
        private String server(Op op)throws Exception{
            List<String> messages=new ArrayList<>();CommandSender actor=capture(op.actor,messages);
            switch(op.action){
                case "db.optimize","db.checkpoint"->{
                    try(Connection c=conn();Statement statement=c.createStatement()){statement.execute("ANALYZE");if(op.action.equals("db.checkpoint"))statement.execute("CHECKPOINT");}
                    return op.action+" completed by PostgreSQL";
                }
                case "world.reset-objects"->handleResetWorldObjectsCommand(actor,new String[]{"resetworldobjects","confirm"});
                case "world.clear-floating-texts"->handleClearFloatingTextsCommand(actor,new String[]{"clearfloatingtexts","confirm"});
                case "game.audit.ar"->handleAuditCommand(actor,new String[]{"audit","ar"});
                case "game.audit.elections"->handleAuditCommand(actor,new String[]{"audit","elections"});
                case "game.audit.restricted"->handleAuditCommand(actor,new String[]{"audit","narcotics"});
                case "rpguard.status"->handleRpGuardCommand(actor,new String[]{"status"});
                case "rpguard.test"->handleRpGuardCommand(actor,new String[]{"test",BridgeProtocol.string(op.params,"probe",200).replaceFirst("^/","")});
                case "election-seal.status"->handleSealDropCommand(actor,new String[]{"status"});
                case "legacy-votes.status"->handleOldVoteOff(actor,new String[]{"status"});
                case "legacy-votes.reset-sidebar","legacy-votes.clear-glow"->{
                    int count=0;for(Map<String,Object> binding:op.targets){Player p=Bukkit.getPlayer(UUID.fromString((String)binding.get("uuid")));if(p==null)continue;
                        if(op.action.endsWith("reset-sidebar")){if(removeOldElectionSidebar(p,true))count++;}
                        else if(p.hasPotionEffect(PotionEffectType.GLOWING)){p.removePotionEffect(PotionEffectType.GLOWING);count++;}}
                    return op.action+" applied to "+count+" bound online players";
                }
                case "legacy-votes.unlock"->{
                    long stamp=now();String tag=" | SITE_UNLOCK_"+op.id;
                    int count=tx(connection->{
                        int rows;
                        try(PreparedStatement st=connection.prepareStatement("UPDATE elections SET status='CANCELLED_FORCE',ended_at=?,ended_by=?,notes=COALESCE(notes,'') || ? WHERE UPPER(COALESCE(status,''))='ACTIVE' OR COALESCE(status,'')='' OR UPPER(COALESCE(status,'')) NOT IN ('ENDED','CANCELLED','CANCELLED_FORCE','ARCHIVED','RESET')")){
                            st.setLong(1,stamp);st.setString(2,op.actor);st.setString(3,tag);rows=st.executeUpdate();
                        }
                        for(String table:List.of("cmv7_election_settings","cmv9_election_settings","plus_election_settings","cmv731_vote_sessions"))if(tableExists(connection,table))try(Statement st=connection.createStatement()){st.executeUpdate("DELETE FROM "+table);}
                        if(tableExists(connection,"cmv7_ballot_issues"))try(PreparedStatement st=connection.prepareStatement("UPDATE cmv7_ballot_issues SET used=1,notes=COALESCE(notes,'') || ?")){st.setString(1,tag);st.executeUpdate();}
                        return rows;
                    });
                    for(Map<String,Object> binding:op.targets){Player p=Bukkit.getPlayer(UUID.fromString((String)binding.get("uuid")));if(p!=null)removeOldElectionSidebar(p,true);}
                    return "Legacy elections cancelled in one transaction: "+count;
                }
                default->throw new IllegalArgumentException("unsupported_server_action");
            }
            return messages.isEmpty()?op.action+" completed":String.join("\n",messages);
        }
    }


