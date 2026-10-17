   // CopiMine site-v6: inserted at the pinned CatalogItem declaration anchor.
   // This is a trusted in-process API; HTTP/session authorization remains in the
   // site service and the signed console bridge. It does not parse raw commands.
   private final Map<String, SiteBridgeFlight> siteBridgeFlights = new ConcurrentHashMap<>();

   private static record SiteBridgeFlight(String hash, CompletableFuture<Map<String, Object>> future) {}
   private static record SiteBridgeRequest(UUID target, String itemId, int quantity, String operationId,
         String actor, String reason, String hash, CatalogItem item, boolean donation,
         boolean catalogEnabled, String ownerName) {}
   private static record SiteBridgeUnit(int ordinal, String purchaseId, String deliveryId,
         String uniqueItemId, String status, List<String> deliveredIds) {}

   /**
    * Stable operation IDs may be polled after a pending result. A successful
    * result requires the stock claim state machine, saved physical inventory
    * (or the tax-subscription entitlement), and a committed bridge receipt.
    * SQL/transport uncertainty is always pending, never refundable failure.
    */
   public CompletableFuture<Map<String, Object>> siteBridgeGive(UUID target, String itemId, int quantity,
         String operationId, String actor, String reason) {
      CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
      if (target == null || itemId == null || !itemId.matches("[a-z0-9_]{1,100}")
            || quantity < 1 || quantity > 64 || operationId == null
            || !operationId.matches("[A-Za-z0-9_-]{8,128}") || actor == null || actor.length() > 100
            || reason == null || reason.length() < 3 || reason.length() > 1000) {
         result.completeExceptionally(new IllegalArgumentException("Invalid typed site delivery request."));
         return result;
      }
      String hash = siteBridgeHash(target, itemId, quantity, actor, reason);
      SiteBridgeFlight previous = this.siteBridgeFlights.putIfAbsent(operationId, new SiteBridgeFlight(hash, result));
      if (previous != null) {
         return previous.hash().equals(hash) ? previous.future()
               : CompletableFuture.completedFuture(siteBridgeResult("pending", "IDEMPOTENCY_CONFLICT", false));
      }
      result.completeOnTimeout(siteBridgeResult("pending", "DELIVERY_STILL_PENDING", false), 30L, TimeUnit.SECONDS)
            .whenComplete((value, error) -> this.siteBridgeFlights.computeIfPresent(operationId,
                  (key, flight) -> flight.future() == result ? null : flight));
      Runnable prepare = () -> {
         try {
            CatalogItem item = this.runtimeCatalogItem(itemId);
            boolean donation = this.isDonationCatalogItem(itemId);
            boolean enabled = item != null && !this.isRetiredArtifact(itemId)
                  && (!donation || this.isPurchasableDonationItem(itemId));
            Player player = Bukkit.getPlayer(target);
            String ownerName = player == null ? "" : player.getName();
            SiteBridgeRequest request = new SiteBridgeRequest(target, itemId, quantity, operationId,
                  actor, reason, hash, item, donation, enabled, ownerName);
            siteBridgeAdvance(request, result, -1, false, false);
         } catch (Throwable error) {
            result.complete(siteBridgeResult("pending", "BRIDGE_UNAVAILABLE", false));
         }
      };
      if (Bukkit.isPrimaryThread()) prepare.run(); else this.runSync(prepare);
      return result;
   }

   /** Inventory presentation only; never expose the UUID, signature, or raw PDC. */
   public String siteBridgeItemId(ItemStack stack) {
      if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Read inventories on the server thread.");
      return this.isOfficialArtifactItem(stack) ? this.itemIdOf(stack) : null;
   }

   private String siteBridgeHash(UUID target, String itemId, int quantity, String actor, String reason) {
      try {
         java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
         try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            out.writeUTF(target.toString()); out.writeUTF(itemId); out.writeInt(quantity);
            out.writeUTF(actor); out.writeUTF(reason);
         }
         return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
      } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
         throw new IllegalStateException(impossible);
      }
   }

   private Map<String, Object> siteBridgeResult(String status, String code, boolean atomicNoDelivery) {
      return Map.of("status", status, "errorCode", code, "atomicNoDelivery", atomicNoDelivery,
            "message", "executed".equals(status) ? "Delivery and durable receipt confirmed."
                  : "failed".equals(status) ? "Request rejected before any delivery."
                  : "Delivery is pending or requires review; do not refund or issue a second copy.");
   }

   private String siteBridgeUnitKey(SiteBridgeRequest request, int ordinal) {
      return "site-v6:" + request.operationId() + ":" + ordinal;
   }

   private void siteBridgeAdvance(SiteBridgeRequest request, CompletableFuture<Map<String, Object>> result,
         int previousOrdinal, boolean afterDelivery, boolean afterCreate) {
      if (result.isDone()) return;
      boolean accepted = this.runAsync(() -> {
         if (result.isDone()) return;
         try {
            SiteBridgeUnit unit = this.siteBridgeRead(request);
            if ("EXECUTED".equals(unit.status())) {
               result.complete(siteBridgeResult("executed", "", false));
            } else if ("FAILED".equals(unit.status())) {
               result.complete(siteBridgeResult("failed", "CATALOG_OR_QUANTITY_REJECTED", true));
            } else if ("CONFLICT".equals(unit.status())) {
               result.complete(siteBridgeResult("pending", "IDEMPOTENCY_CONFLICT", false));
            } else if ("COMPLETE".equals(unit.status())) {
               this.runSync(() -> this.siteBridgeConfirmSaved(request, unit, result));
            } else if ("UNSAVED".equals(unit.status())) {
               this.runSync(() -> this.siteBridgeConfirmUnit(request, unit, result));
            } else if (afterDelivery && unit.ordinal() <= previousOrdinal) {
               result.complete(siteBridgeResult("pending", "CLAIM_NOT_FINALIZED", false));
            } else if ("MISSING".equals(unit.status())) {
               if (afterCreate || !request.catalogEnabled()) {
                  result.complete(siteBridgeResult("pending", "GRANT_NOT_CONFIRMED", false));
               } else {
                  this.siteBridgeCreateUnit(request, unit, result);
               }
            } else if (("PENDING".equals(unit.status()) || "UNCLAIMED".equals(unit.status())) && request.item() != null) {
               this.runSync(() -> this.siteBridgeDeliverUnit(request, unit, result));
            } else {
               result.complete(siteBridgeResult("pending", "CLAIM_IN_PROGRESS_OR_REVIEW", false));
            }
         } catch (Throwable error) {
            this.getLogger().warning("Site delivery remains pending: " + request.operationId() + " (" + error.getClass().getSimpleName() + ")");
            result.complete(siteBridgeResult("pending", "DATABASE_UNCONFIRMED", false));
         }
      });
      if (!accepted) result.complete(siteBridgeResult("pending", "DATABASE_QUEUE_UNAVAILABLE", false));
   }

   /**
    * Reads the exact native purchase/claim records. No new claim is created until
    * every previous unit is durably delivered, preserving the native live-instance
    * uniqueness constraint. The bridge journal binds the full immutable request.
    */
   private SiteBridgeUnit siteBridgeRead(SiteBridgeRequest request) throws SQLException {
      Connection connection = this.pgPool.acquire();
      try {
         connection.setAutoCommit(false);
         try (Statement ddl = connection.createStatement()) {
            ddl.execute("CREATE TABLE IF NOT EXISTS artifact_site_bridge_operations("
                  + "operation_id TEXT PRIMARY KEY,request_hash TEXT NOT NULL,player_uuid TEXT NOT NULL,"
                  + "item_id TEXT NOT NULL,quantity INTEGER NOT NULL,actor TEXT NOT NULL,reason TEXT NOT NULL,"
                  + "delivery_kind TEXT NOT NULL,status TEXT NOT NULL,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL)");
            ddl.execute("CREATE TABLE IF NOT EXISTS artifact_site_bridge_units("
                  + "operation_id TEXT NOT NULL,ordinal INTEGER NOT NULL,purchase_id TEXT NOT NULL,"
                  + "unique_item_id TEXT NOT NULL,confirmed_at BIGINT NOT NULL,PRIMARY KEY(operation_id,ordinal))");
         }
         String initial = request.catalogEnabled() && (!request.donation() || request.quantity() == 1) ? "PENDING" : "FAILED";
         try (PreparedStatement insert = connection.prepareStatement(
               "INSERT INTO artifact_site_bridge_operations(operation_id,request_hash,player_uuid,item_id,quantity,actor,reason,delivery_kind,status,created_at,updated_at) "
                     + "VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(operation_id) DO NOTHING")) {
            insert.setString(1, request.operationId()); insert.setString(2, request.hash());
            insert.setString(3, request.target().toString()); insert.setString(4, request.itemId());
            insert.setInt(5, request.quantity()); insert.setString(6, request.actor()); insert.setString(7, request.reason());
            insert.setString(8, request.donation() ? "DONATION" : "AR"); insert.setString(9, initial);
            insert.setLong(10, this.now()); insert.setLong(11, this.now()); insert.executeUpdate();
         }
         String status;
         try (PreparedStatement select = connection.prepareStatement(
               "SELECT request_hash,status,delivery_kind FROM artifact_site_bridge_operations WHERE operation_id=?")) {
            select.setString(1, request.operationId());
            try (ResultSet row = select.executeQuery()) {
               if (!row.next()) throw new SQLException("Site operation journal row is missing.");
               status = request.hash().equals(row.getString(1)) ? row.getString(2) : "CONFLICT";
               if ("PENDING".equals(status) && !(request.donation() ? "DONATION" : "AR").equals(row.getString(3))) status = "CONFLICT";
            }
         }
         if (!"PENDING".equals(status)) {
            connection.commit();
            return new SiteBridgeUnit(0, "", "", "", status, List.of());
         }
         List<String> deliveredIds = new ArrayList<>();
         for (int ordinal = 0; ordinal < request.quantity(); ordinal++) {
            String query = request.donation()
                  ? "SELECT p.id,p.player_uuid,p.item_id,p.status,c.id AS delivery_id,c.status AS delivery_status,'' AS unique_item_id "
                        + "FROM donation_purchases p LEFT JOIN donation_item_claims c ON c.purchase_id=p.id AND c.player_uuid=p.player_uuid "
                        + "WHERE p.idempotency_key=?"
                  : "SELECT p.purchase_id AS id,p.player_uuid,p.item_id,p.status,d.delivery_id,d.status AS delivery_status,p.unique_item_id "
                        + "FROM artifact_purchases p LEFT JOIN artifact_pending_deliveries d ON d.purchase_id=p.purchase_id "
                        + "WHERE p.idempotency_key=?";
            try (PreparedStatement select = connection.prepareStatement(query)) {
               select.setString(1, siteBridgeUnitKey(request, ordinal));
               try (ResultSet row = select.executeQuery()) {
                  if (!row.next()) {
                     connection.commit();
                     return new SiteBridgeUnit(ordinal, "", "", "", "MISSING", List.copyOf(deliveredIds));
                  }
                  if (!request.target().toString().equals(row.getString("player_uuid")) || !request.itemId().equals(row.getString("item_id"))) {
                     throw new SQLException("Native gift key belongs to a different target or item.");
                  }
                  String purchaseId = row.getString("id"), deliveryId = row.getString("delivery_id");
                  String uniqueId = row.getString("unique_item_id"), deliveryStatus = row.getString("delivery_status");
                  String purchaseStatus = row.getString("status");
                  if ("CLAIMED".equals(deliveryStatus) && (request.donation() ? "CLAIMED".equals(purchaseStatus) : "DELIVERED".equals(purchaseStatus))) {
                     if (request.donation() && (request.item() == null || !"TAX_CLOCK".equalsIgnoreCase(request.item().effect()))) {
                        try (PreparedStatement instances = connection.prepareStatement(
                              "SELECT unique_item_id FROM artifact_item_instances WHERE purchase_id=? AND owner_uuid=? AND item_id=?")) {
                           instances.setString(1, purchaseId); instances.setString(2, request.target().toString()); instances.setString(3, request.itemId());
                           try (ResultSet instance = instances.executeQuery()) {
                              int count = 0;
                              while (instance.next()) { uniqueId = instance.getString(1); count++; }
                              if (count != 1) throw new SQLException("Donation claim has no single provable physical instance.");
                           }
                        }
                     }
                     if (uniqueId == null) uniqueId = "";
                     boolean checkpoint = false;
                     try (PreparedStatement saved = connection.prepareStatement(
                           "SELECT purchase_id,unique_item_id FROM artifact_site_bridge_units WHERE operation_id=? AND ordinal=?")) {
                        saved.setString(1, request.operationId()); saved.setInt(2, ordinal);
                        try (ResultSet receipt = saved.executeQuery()) {
                           if (receipt.next()) {
                              if (!purchaseId.equals(receipt.getString(1)) || !uniqueId.equals(receipt.getString(2)))
                                 throw new SQLException("Unit checkpoint refers to a different native delivery.");
                              checkpoint = true;
                           }
                        }
                     }
                     if (!checkpoint) {
                        connection.commit();
                        return new SiteBridgeUnit(ordinal, purchaseId, deliveryId == null ? "" : deliveryId,
                              uniqueId, "UNSAVED", uniqueId.isBlank() ? List.of() : List.of(uniqueId));
                     }
                     if (!uniqueId.isBlank()) deliveredIds.add(uniqueId);
                     continue;
                  }
                  connection.commit();
                  return new SiteBridgeUnit(ordinal, purchaseId, deliveryId == null ? "" : deliveryId,
                        uniqueId == null ? "" : uniqueId, deliveryStatus == null ? "REVIEW" : deliveryStatus, List.copyOf(deliveredIds));
               }
            }
         }
         connection.commit();
         return new SiteBridgeUnit(request.quantity(), "", "", "", "COMPLETE", List.copyOf(deliveredIds));
      } catch (SQLException error) {
         try { connection.rollback(); } catch (SQLException ignored) {}
         throw error;
      } finally {
         try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
         this.pgPool.release(connection);
      }
   }

   private void siteBridgeCreateUnit(SiteBridgeRequest request, SiteBridgeUnit unit,
         CompletableFuture<Map<String, Object>> result) {
      if (result.isDone()) return;
      if (request.donation()) {
         DonationPurchaseService service = this.donationPurchaseService();
         if (service == null) {
            result.complete(siteBridgeResult("pending", "ECONOMY_SERVICE_UNAVAILABLE", false));
            return;
         }
         service.createAdminGiftAsync(request.target(), request.ownerName(), request.itemId(),
               "site-v6:" + request.actor(), siteBridgeUnitKey(request, unit.ordinal()))
            .whenComplete((created, error) -> {
               // Even an exception can follow an uncertain commit. The exact key
               // is read again; only a durable FAILED bridge row is refundable.
               siteBridgeAdvance(request, result, unit.ordinal(), false, true);
            });
      } else {
         this.persistAdminGiftDelivery(request.target(), request.ownerName(), request.item(),
               "site-v6:" + request.actor(), siteBridgeUnitKey(request, unit.ordinal()));
         siteBridgeAdvance(request, result, unit.ordinal(), false, true);
      }
   }

   private void siteBridgeDeliverUnit(SiteBridgeRequest request, SiteBridgeUnit unit,
         CompletableFuture<Map<String, Object>> result) {
      if (result.isDone()) return;
      Player player = Bukkit.getPlayer(request.target());
      if (player == null || !player.isOnline()) {
         result.complete(siteBridgeResult("pending", "PLAYER_OFFLINE", false)); return;
      }
      if (player.getInventory().firstEmpty() < 0
            && (request.item() == null || !"TAX_CLOCK".equalsIgnoreCase(request.item().effect()))) {
         result.complete(siteBridgeResult("pending", "INVENTORY_FULL", false)); return;
      }
      Runnable completed = () -> siteBridgeAdvance(request, result, unit.ordinal(), true, false);
      if (request.donation()) {
         this.deliverDonationClaimRowV2(player,
               new DonationClaimRow(unit.deliveryId(), unit.purchaseId(), request.itemId(), 1L, unit.status()), completed);
      } else {
         this.deliverPendingRowV2(player,
               new PendingDeliveryRow(unit.deliveryId(), unit.purchaseId(), unit.uniqueItemId(), request.itemId()), completed);
      }
   }

   /** Checkpoint every saved unit before creating the next one. A player may
    * move or use an already confirmed unit without invalidating its historical
    * delivery receipt. An uncertain unit is never reissued or refunded. */
   private void siteBridgeConfirmUnit(SiteBridgeRequest request, SiteBridgeUnit unit,
         CompletableFuture<Map<String, Object>> result) {
      if (result.isDone()) return;
      Player player = Bukkit.getPlayer(request.target());
      boolean subscription = request.item() != null && "TAX_CLOCK".equalsIgnoreCase(request.item().effect());
      if (!subscription) {
         if (player == null || !player.isOnline()
               || unit.uniqueItemId().isBlank()
               || !this.playerHasOfficialInstance(player, unit.uniqueItemId(), request.itemId(), request.target())) {
            result.complete(siteBridgeResult("pending", "PHYSICAL_DELIVERY_REQUIRES_REVIEW", false)); return;
         }
      }
      try {
         if (player != null && player.isOnline()) player.saveData();
      } catch (Throwable error) {
         result.complete(siteBridgeResult("pending", "PLAYER_SAVE_UNCONFIRMED", false)); return;
      }
      if (!this.runAsync(() -> {
         Connection connection = null;
         try {
            connection = this.pgPool.acquire();
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                  "INSERT INTO artifact_site_bridge_units(operation_id,ordinal,purchase_id,unique_item_id,confirmed_at) "
                        + "VALUES(?,?,?,?,?) ON CONFLICT(operation_id,ordinal) DO NOTHING")) {
               insert.setString(1, request.operationId()); insert.setInt(2, unit.ordinal());
               insert.setString(3, unit.purchaseId()); insert.setString(4, unit.uniqueItemId());
               insert.setLong(5, this.now()); insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement(
                  "SELECT purchase_id,unique_item_id FROM artifact_site_bridge_units WHERE operation_id=? AND ordinal=?")) {
               select.setString(1, request.operationId()); select.setInt(2, unit.ordinal());
               try (ResultSet saved = select.executeQuery()) {
                  if (!saved.next() || !unit.purchaseId().equals(saved.getString(1)) || !unit.uniqueItemId().equals(saved.getString(2)))
                     throw new SQLException("Unit checkpoint binding was not confirmed.");
               }
            }
            connection.commit();
            siteBridgeAdvance(request, result, unit.ordinal(), false, false);
         } catch (SQLException error) {
            if (connection != null) try { connection.rollback(); } catch (SQLException ignored) {}
            result.complete(siteBridgeResult("pending", "UNIT_RECEIPT_UNCONFIRMED", false));
         } finally {
            if (connection != null) {
               try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
               this.pgPool.release(connection);
            }
         }
      })) result.complete(siteBridgeResult("pending", "DATABASE_QUEUE_UNAVAILABLE", false));
   }

   private void siteBridgeConfirmSaved(SiteBridgeRequest request, SiteBridgeUnit unit,
         CompletableFuture<Map<String, Object>> result) {
      if (result.isDone()) return;
      // Every unit was physically proved and saved before its checkpoint. The
      // completed native records and those immutable checkpoints were read by
      // siteBridgeRead. Do not demand possession of historical deliveries again.
      if (!this.runAsync(() -> {
         Connection connection = null;
         try {
            connection = this.pgPool.acquire();
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(
                  "UPDATE artifact_site_bridge_operations SET status='EXECUTED',updated_at=? "
                        + "WHERE operation_id=? AND request_hash=? AND status='PENDING' "
                        + "AND (SELECT COUNT(*) FROM artifact_site_bridge_units WHERE operation_id=?)=?")) {
               update.setLong(1, this.now()); update.setString(2, request.operationId()); update.setString(3, request.hash());
               update.setString(4, request.operationId()); update.setInt(5, request.quantity());
               if (update.executeUpdate() != 1) {
                  connection.rollback();
                  result.complete(siteBridgeResult("pending", "RECEIPT_COMMIT_UNCONFIRMED", false)); return;
               }
            }
            connection.commit();
            result.complete(siteBridgeResult("executed", "", false));
         } catch (SQLException error) {
            if (connection != null) try { connection.rollback(); } catch (SQLException ignored) {}
            result.complete(siteBridgeResult("pending", "RECEIPT_COMMIT_UNCONFIRMED", false));
         } finally {
            if (connection != null) {
               try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
               this.pgPool.release(connection);
            }
         }
      })) result.complete(siteBridgeResult("pending", "DATABASE_QUEUE_UNAVAILABLE", false));
   }


