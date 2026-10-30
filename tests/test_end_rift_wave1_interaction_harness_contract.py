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


def test_wave_bots_use_the_pinned_via_backwards_26_1_protocol_route():
    bot = BOT.read_text(encoding="utf-8")

    assert "const protocolVersion = process.env.END_RIFT_BOT_PROTOCOL_VERSION || '26.1'" in bot
    assert "protocolVersion !== '26.1'" in bot
    assert "client=26.1 backend=Paper-26.3-205 gateway=ViaVersion-5.12.1" in bot
    assert "minecraftProtocol.supportedVersions.includes(protocolVersion)" in bot
    assert re.search(r"version:\s*protocolVersion", bot)


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


def test_wave_one_runner_teleports_before_waiting_for_delivery_mode_ack():
    driver = DRIVER.read_text(encoding="utf-8")
    bot = BOT.read_text(encoding="utf-8")
    mode_writer = driver.split("function Set-PlayerBotModeForName", 1)[1].split(
        "function Start-PlayerBot", 1
    )[0]
    pickup_branch = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]
    delivered_branch = pickup_branch.split(
        "if ($tail -match $deliveredPattern)", 1
    )[1].split("return $tail", 1)[0]

    assert "Wait-PlayerBotModeAcknowledgement" not in mode_writer, (
        "A blocking bot-log acknowledgement can consume the carrier's 10-second "
        "lifetime before the holder is teleported to the core."
    )
    mode_request = pickup_branch.index(
        "Set-PlayerBotModeForName -Name $holderName -Mode 'CORE_DELIVER'"
    )
    core_teleport = pickup_branch.index("Teleport-Player $holderName")
    assert mode_request < core_teleport
    assert "Wait-PlayerBotModeAcknowledgement -Name $activeHolder -Mode 'CORE_DELIVER'" in delivered_branch
    assert "BOT_OBJECTIVE_INTERACTION" in bot
    assert "CURRENT_WAVE1_CHARGE_TRACKED" in pickup_branch


def test_wave_one_pickup_ack_is_bound_to_the_client_that_received_the_request():
    driver = DRIVER.read_text(encoding="utf-8")
    wait_delivery = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]
    pickup_request = wait_delivery.split(
        "if ($configuredPickupCharge -ne $activeCharge)", 1
    )[1].split("$configuredPickupCharge = $activeCharge", 1)[0]
    picked_holder = wait_delivery.split(
        "if ($null -ne $pickedPattern -and $tail -match $pickedPattern)", 1
    )[1].split("} elseif ($null -ne $activeCharge)", 1)[0]

    assert "$activePickupPlayer = $PickupPlayer" in pickup_request
    assert pickup_request.index("$activePickupPlayer = $PickupPlayer") < pickup_request.index(
        "Set-PlayerBotModeForName -Name $PickupPlayer -Mode 'CARRIER_PICKUP'"
    )
    assert "$activePickupPlayer = $holderName" not in picked_holder


def test_official_runner_allows_live_bot_log_reads_while_capturing_output():
    driver = DRIVER.read_text(encoding="utf-8")

    assert "[IO.FileShare]::ReadWrite" in driver, (
        "The runner polls objective acknowledgements while the bot is writing its log; "
        "the output stream must permit shared reads or Wave 1 aborts on a file lock."
    )


def test_wave_one_delivery_poll_does_not_issue_slow_rcon_mob_recovery():
    driver = DRIVER.read_text(encoding="utf-8")
    wait_delivery = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]

    assert "Teleport-PlayersToNearestWaveMobIfFar" not in wait_delivery, (
        "Repeated position queries and RCON teleports can take longer than the "
        "10-second carrier charge lifetime and hide a fresh charge until it expires."
    )
    assert "Teleport-Player $PickupPlayer $activeCarrierLocation" not in wait_delivery, (
        "The live bot's combat navigation should reach the selected carrier; "
        "do not spend charge lifetime on an unrelated RCON teleport."
    )


def test_official_runner_keeps_probe_players_passive_until_wave_one_starts():
    driver = DRIVER.read_text(encoding="utf-8")
    start = driver.index("Start-PlayerBot -Name $name -Core $core")
    mode_before_start = driver.rfind("Set-PlayerBotModeForName -Name $name -Mode '", 0, start)
    assert mode_before_start >= 0
    assert "-Mode 'PASSIVE'" in driver[mode_before_start:start]
    assert "-Mode 'ACTIVE'" not in driver[mode_before_start:start]

    wave_one_started = driver.index(".*wave=1\\b")
    first_wave_one_delivery = driver.index("Wait-CarrierDelivery -AfterOffset $carrierOffset")
    active_mode = driver.index("Set-PlayerBotMode -Mode 'ACTIVE'", wave_one_started)
    assert wave_one_started < active_mode < first_wave_one_delivery


def test_passive_probe_keeps_client_tick_stream_alive_without_attacking_or_navigating():
    bot = BOT.read_text(encoding="utf-8")
    passive_mode = bot.split("function enterPassiveMode ()", 1)[1].split(
        "function enterActiveMode (", 1
    )[0]
    objective_mode = bot.split("function enterWaveOneInteractionMode (", 1)[1].split(
        "function pollControlMode", 1
    )[0]
    active_mode = bot.split("function enterActiveMode (", 1)[1].split(
        "function stopObjectiveInteraction", 1
    )[0]

    assert "bot.physicsEnabled = true" in passive_mode, (
        "Passive mode must retain Mineflayer's movement/tick_end stream for the "
        "26.1-to-26.3 ViaBackwards route."
    )
    assert "stopNavigation()" in passive_mode
    assert "clearControlStates()" in passive_mode
    assert "clearInterval(attackTimer)" in passive_mode
    assert "bot.physicsEnabled = false" not in objective_mode, (
        "Wave 1 carrier and core interactions also require the real client "
        "movement/tick_end cadence through the protocol gateway."
    )
    assert "bot.on('physicsTick'" in bot and "bot._client.write('tick_end', {})" in bot, (
        "Passive bots must keep emitting real 26.1 client tick boundaries while "
        "the server-authoritative probe holds its position."
    )
    assert "bot.physicsEnabled = true" in active_mode, (
        "Wave combat must restore client physics when the probe becomes active."
    )


def test_official_runner_does_not_teleport_bots_repeatedly_during_start_ritual_hold():
    driver = DRIVER.read_text(encoding="utf-8")
    ritual_completion_wait = next(
        line for line in driver.splitlines()
        if "RITUAL_COMPLETED.*event=" in line
    )

    assert "-Action" not in ritual_completion_wait, (
        "Repeated RCON teleports during the ten-second rune hold can desynchronize "
        "the player clients and evict them from their pads."
    )


def test_wave_one_driver_keeps_the_charge_without_a_timeout():
    driver = DRIVER.read_text(encoding="utf-8")
    wait_delivery = driver.split("function Wait-CarrierDelivery", 1)[1].split(
        "function Get-Status", 1
    )[0]

    assert "END_RIFT_CARRIER_TIMEOUT_" not in wait_delivery
    assert "Set-PlayerBotMode -Mode 'ACTIVE'" in wait_delivery
    assert "$activeCharge = $candidate" in wait_delivery


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
