from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event" / "src" / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"


def method(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    return source[start : source.index(next_signature, start)]


def test_named_trial_visuals_are_signed_and_bound_only_to_matching_wave7_actors():
    source = EVENT.read_text(encoding="utf-8")
    visuals = {
        "WARDEN": ("EntityType.WARDEN", "CLIENT_VISUAL_WAVE7_WARDEN", "END_RIFT_WAVE7_WARDEN_V1"),
        "MOBILE_MARKSMAN": ("EntityType.SKELETON", "CLIENT_VISUAL_WAVE7_MARKSMAN", "END_RIFT_WAVE7_MARKSMAN_V1"),
        "RIFT_ARCHMAGE": ("EntityType.EVOKER", "CLIENT_VISUAL_WAVE7_ARCHMAGE", "END_RIFT_WAVE7_ARCHMAGE_V1"),
    }

    visual_mapping = method(
        source,
        "private String namedWave7ClientVisualId(Entity entity)",
        "private String clientVisualId(Entity entity)",
    )
    for role, (entity_type, constant, visual_id) in visuals.items():
        assert f'"{role}"' in visual_mapping
        assert entity_type in visual_mapping
        assert constant in visual_mapping
        assert f'private static final String {constant} = "{visual_id}";' in source
    assert "isNamedRealitySplitActor(entity)" in visual_mapping
    assert "PERSONAL_ECHO" not in visual_mapping

    client_mapping = method(
        source,
        "private String clientVisualId(Entity entity)",
        "private boolean isVisualEventMob(Entity entity)",
    )
    assert "namedWave7ClientVisualId(entity)" in client_mapping

    visual_allowlist = method(
        source,
        "private boolean isVisualEventMob(Entity entity)",
        "private void bindBossClient(Player player)",
    )
    assert "namedWave7ClientVisualId(entity).isBlank()" in visual_allowlist

    binding = method(
        source,
        "private void bindEventEntityClient(Player player, Entity entity)",
        "private void sendRitualCasterPhase(Player viewer, Entity caster)",
    )
    assert '"END_ENTITY_BIND"' in binding
    assert "sendClientPacket(player" in binding


def test_new_named_trial_actors_bind_after_their_identity_checkpoint_is_valid():
    source = EVENT.read_text(encoding="utf-8")
    ensure_actor = method(
        source,
        "private boolean ensureNamedRealitySplitTrialRoom",
        "private NamespacedKey namedTrialActorCheckpointKey",
    )
    bind = "bindEventEntityClientForOnlinePlayers(actor)"
    warden_start = ensure_actor.index("if (profile == null) {")
    first_actor_declaration = ensure_actor.index("Entity actor = findRealitySplitTrialEntity", warden_start)
    profile_start = ensure_actor.index("Entity actor = findRealitySplitTrialEntity", first_actor_declaration + 1)
    warden = ensure_actor[warden_start:profile_start]
    profile = ensure_actor[profile_start:]

    assert "boolean createdActor = false" in warden
    assert "if (createdActor) " + bind in warden
    assert warden.index('"end_event_named_trial_schema"') < warden.index(bind)
    assert "boolean createdActor = false" in profile
    assert "if (createdActor) " + bind in profile
    assert profile.index("writeNamedTrialActorCheckpoint(actor, initial)") < profile.index(bind)

