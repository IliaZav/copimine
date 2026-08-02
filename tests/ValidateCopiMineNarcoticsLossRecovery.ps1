. "$PSScriptRoot\ElectionPhase1Validator.Helpers.ps1"
$errors = New-ErrorList
$narcotics = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\CopiMineNarcotics.java')
$database = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\db\NarcoticsDatabase.java')
$factory = Read-Utf8 (Join-Path $root 'copimine-narcotics\src\me\copimine\narcotics\item\NarcoticItemFactory.java')

# A narcotic is quarantined only after the write-ahead journal is fsynced;
# PostgreSQL downtime must not turn a loss into a permanent disappearance.
Require-Contains $database 'appendRefundJournal(refundId, playerUuid, narcoticId, amount, source)' 'Narcotic recovery must write the durable refund journal before DB work.'
Require-Contains $database 'public CompletableFuture<Void> flushPendingRefundJournal()' 'Refund journal must be replayable without a server restart.'
Require-Contains $database 'ON CONFLICT DO NOTHING' 'Refund replay must be idempotent for both id and source-instance conflicts.'
Require-Contains $database 'source_instance_id' 'Physical narcotic refunds must carry an instance identity for duplicate suppression.'
Require-Contains $database 'uq_narcotics_pending_refund_source' 'Duplicate loss callbacks must be rejected by a database uniqueness guard.'
Require-Contains $narcotics 'queueNarcoticRecovery' 'All destructive item paths must go through one recovery gate.'
Require-Contains $narcotics 'onNarcoticInsideBlock' 'Cactus/fire/lava block destruction must be covered.'
Require-Contains $narcotics 'onNarcoticDamage' 'Explosion/void/fire entity damage must be covered.'
Require-Contains $narcotics 'onNarcoticItemRemoved' 'Silent plugin/creative removal must be covered.'
Require-Contains $narcotics 'onNarcoticDespawn' 'Item despawn must be recoverable.'
Require-Contains $narcotics 'case "recover", "restore"' 'Players must have a manual retry command for pending recoveries.'
Require-Contains $narcotics 'reservePendingRefunds(player, flushError)' 'Recovery must replay the journal before reserving rows.'
Require-Contains $narcotics 'createOfficialItem(definition, Math.max(1, row.amount()), player.getUniqueId())' 'Refunded narcotics must retain owner metadata.'
Require-Contains $factory 'narcotic_owner_uuid' 'Issued narcotics must carry durable owner identity.'
Require-Contains $factory 'bindOwnerIfMissing' 'Legacy official narcotics must be bound exactly once to their known owner.'
Require-Contains $narcotics 'UUID ownerUuid = owner;' 'Async recovery callbacks must capture an effectively final owner.'
Require-Contains $narcotics 'queuePendingRefund(owner, definition.id(), amount, instanceId)' 'Narcotic loss must persist the physical instance identity.'

Throw-IfErrors 'ValidateCopiMineNarcoticsLossRecovery'
