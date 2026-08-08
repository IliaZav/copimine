#!/usr/bin/env python3
"""Pure unit tests for the president-law review transition.

These tests do not connect to production PostgreSQL.  The database adapter calls
the same transition function, so approval/rejection and the safety limits are
tested independently from HTTP and JDBC/psycopg plumbing.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path


BACKEND = Path(__file__).resolve().parents[1] / "backend"
sys.path.insert(0, str(BACKEND))

from president_law_workflow import (  # noqa: E402
    PRESIDENT_LAW_REPLACE_COOLDOWN_MS,
    PresidentLawReviewError,
    review_president_law_transition,
)


class PresidentLawReviewTransitionTests(unittest.TestCase):
    def test_approval_publishes_law_and_preserves_slot(self) -> None:
        result = review_president_law_transition(
            law_status="PENDING",
            decision="approved",
            published_count=2,
            replaced_law_id="",
            replacement_elapsed_ms=0,
            replaced_law_status="",
            replacement_term_matches=True,
            replacement_slot=0,
            next_slot=3,
        )
        self.assertEqual(result["status"], "PUBLISHED")
        self.assertEqual(result["slot_no"], 3)
        self.assertEqual(result["replaced_law_id"], "")

    def test_rejection_is_terminal_and_does_not_publish(self) -> None:
        result = review_president_law_transition(
            law_status="PENDING",
            decision="rejected",
            published_count=5,
            replaced_law_id="",
            replacement_elapsed_ms=0,
            replaced_law_status="",
            replacement_term_matches=True,
            replacement_slot=0,
            next_slot=6,
        )
        self.assertEqual(result, {"status": "REJECTED", "slot_no": 0, "replaced_law_id": ""})

    def test_approval_rejects_the_sixth_law_without_replacement(self) -> None:
        with self.assertRaises(PresidentLawReviewError):
            review_president_law_transition(
                law_status="PENDING",
                decision="APPROVED",
                published_count=5,
                replaced_law_id="",
                replacement_elapsed_ms=0,
                replaced_law_status="",
                replacement_term_matches=True,
                replacement_slot=0,
                next_slot=6,
            )

    def test_replacement_respects_three_day_cooldown(self) -> None:
        with self.assertRaises(PresidentLawReviewError):
            review_president_law_transition(
                law_status="PENDING",
                decision="approved",
                published_count=5,
                replaced_law_id="old-law",
                replacement_elapsed_ms=PRESIDENT_LAW_REPLACE_COOLDOWN_MS - 1,
                replaced_law_status="PUBLISHED",
                replacement_term_matches=True,
                replacement_slot=2,
                next_slot=6,
            )


if __name__ == "__main__":
    unittest.main()
