from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
BOT = ROOT / "tests" / "LocalEndRiftMobCombatBot.js"
DRIVER = ROOT / "tests" / "RunEndRiftOfficialTwoPlayerLive.ps1"
PLUGIN = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def test_wave_one_bot_modes_send_right_clicks_for_charge_and_core():
    bot = BOT.read_text(encoding="utf-8")

    assert "CARRIER_PICKUP" in bot
    assert "CORE_DELIVER" in bot
    assert "bot.activateItem()" in bot
    assert "bot.activateBlock(coreBlock)" in bot


def test_wave_one_driver_activates_charge_and_delivers_as_authoritative_holder():
    driver = DRIVER.read_text(encoding="utf-8")
    wait_delivery = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]

    assert "END_RIFT_CARRIER_CHARGE_CREATED" in wait_delivery
    assert "END_RIFT_CARRIER_PICKED_UP" in wait_delivery
    assert "-Mode 'CARRIER_PICKUP'" in wait_delivery
    assert "-Mode 'CORE_DELIVER'" in wait_delivery
    assert "Teleport-Player $PickupPlayer $activeChargeLocation[0]" in wait_delivery
    assert "END_RIFT_CARRIER_DELIVERED" in wait_delivery


def test_wave_one_driver_resumes_combat_after_a_charge_timeout_transfer():
    driver = DRIVER.read_text(encoding="utf-8")
    wait_delivery = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]

    assert "END_RIFT_CARRIER_TIMEOUT_(?:TRANSFER|REPLACEMENT)" in wait_delivery
    assert "Set-PlayerBotMode -Mode 'ACTIVE'" in wait_delivery


def test_wave_one_charge_pickup_accepts_paper_pre_cancelled_air_interactions():
    source = PLUGIN.read_text(encoding="utf-8")
    bot = BOT.read_text(encoding="utf-8")
    handler = re.search(
        r"@EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = false\)\s*"
        r"public void onCarrierChargePlayerInteract\(PlayerInteractEvent event\)",
        source,
    )

    assert handler, "Wave 1 must receive Paper's pre-cancelled right-click-air event"
    assert "bot.activateItem()" in bot, "The pickup probe must send a right-click-air use packet"
