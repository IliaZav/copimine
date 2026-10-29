from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_event_reward_replay_matches_the_full_persisted_payload():
    source = (ROOT / "copimine-artifacts/src/me/copimine/artifacts/CopiMineArtifacts.java").read_text(
        encoding="utf-8"
    )
    reserve_start = source.index("private RewardIssueResult reserveEventReward(")
    lookup_start = source.index("private RewardIssueResult readEventReward(", reserve_start)
    reserve = source[reserve_start:lookup_start]
    lookup_end = source.index("private UUID eventWorldOwnerUuid(", lookup_start)
    lookup = source[lookup_start:lookup_end]

    assert "readEventReward(connection, key, expectedPayload)" in reserve
    assert "SELECT status,unique_item_id,event_id,item_id,recipient_uuid,recipient_name" in lookup
    assert "delivery_mode,world_name,drop_x,drop_y,drop_z" in lookup
    assert "EventRewardIdempotencyPolicy.sameRequest(expectedPayload, persistedPayload)" in lookup
    assert "idempotency key conflicts with a different request payload" in lookup
