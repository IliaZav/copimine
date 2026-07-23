#!/usr/bin/env python3
"""Static contract checks for the two-stage RP election workflow.

This test intentionally does not connect to production. It catches drift
between the FastAPI routes, request limits, database marker and the frontend
controls before a release is packaged.
"""
from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "backend" / "main.py").read_text(encoding="utf-8")
FRONTEND = (ROOT / "frontend" / "assets" / "js" / "cabinet-runtime.js").read_text(encoding="utf-8")
MIGRATION = (ROOT.parent / "db" / "migrations" / "20260723_013_rp_election_workflow.sql").read_text(encoding="utf-8")
PLUGIN = (ROOT.parent / "copimine-election-core" / "src" / "me" / "copimine" / "electioncore" / "CopiMineElectionCore.java").read_text(encoding="utf-8")


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        raise AssertionError(message)


def main() -> None:
    for route in (
        '@app.get("/api/player/elections")',
        '@app.post("/api/player/elections/application")',
        '@app.get("/api/elections/detail")',
        '@app.post("/api/elections/rp/control")',
        '@app.post("/api/elections/rp/voting-blocks")',
    ):
        require(BACKEND, route, f"Missing RP election route: {route}")
    for field in ("why_president", "server_plan", "short_program", "faction"):
        require(BACKEND, field, f"Application field missing: {field}")
    require(BACKEND, "ge=24, le=72", "Voting duration must be bounded to 24..72 hours")
    require(BACKEND, 'action not in {"start", "select", "stage", "finish", "remove", "resign"}', "Unexpected RP action accepted")
    require(BACKEND, 'stage not in {"DEBATES", "VOTING"}', "Only Debates and Voting may be selected")
    require(BACKEND, "_rp_active_election", "Single active campaign guard missing")
    require(BACKEND, "president_terms WHERE status='ACTIVE'", "Active president guard missing")
    require(BACKEND, "voting_started_at", "Voting start timestamp missing")
    require(BACKEND, "voting_deadline_at", "Voting deadline timestamp missing")
    require(BACKEND, "requested_deadline < current_deadline", "Voting extension must never shorten the deadline")
    require(BACKEND, "requested_deadline > max_deadline", "Voting extension must be capped at 72 hours")
    require(BACKEND, "protected_blocks", "Voting block must be persisted in world protection")
    require(BACKEND, "resignation_reason", "President removal must record a reason")
    require(BACKEND, "president_taxes SET status='ARCHIVED' WHERE term_id=%s", "President removal must close the active tax")
    require(BACKEND, "alreadyAtStage", "Repeated stage actions must be idempotent")
    require(MIGRATION, "election_voting_blocks", "RP voting-block migration missing")
    require(MIGRATION, "ALTER TABLE elections ADD COLUMN IF NOT EXISTS voting_started_at", "Migration is not additive for voting timestamps")
    require(PLUGIN, "openDirectVoteMenu", "Plugin direct-vote menu missing")
    require(PLUGIN, "confirmDirectVote", "Plugin vote confirmation missing")
    require(PLUGIN, "uq_votes_voter_round", "One-vote database uniqueness guard missing")
    require(PLUGIN, "election_voting_blocks", "Plugin block table missing")
    require(PLUGIN, "votingDeadline", "Plugin deadline guard missing")
    require(FRONTEND, "renderRpElectionAdminPanel", "Admin RP controls missing")
    require(FRONTEND, "submitPlayerElectionApplication", "Player application form missing")
    require(FRONTEND, "data-click=\"rpElectionControl('stage','VOTING')\"", "Voting control missing")
    if re.search(r"/api/player/elections.*vote|player.*elections.*POST.*vote", BACKEND, re.I):
        raise AssertionError("Website voting endpoint must not be added")
    print("Election RP contract test OK")


if __name__ == "__main__":
    main()
