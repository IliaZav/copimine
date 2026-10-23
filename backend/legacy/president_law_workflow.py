"""Pure president-law review transition shared by the admin endpoint and tests."""
from __future__ import annotations


PRESIDENT_LAW_MAX_PUBLISHED = 5
PRESIDENT_LAW_REPLACE_COOLDOWN_MS = 3 * 24 * 60 * 60 * 1000


class PresidentLawReviewError(ValueError):
    """A law cannot make the requested state transition."""


def review_president_law_transition(
    *,
    law_status: str,
    decision: str,
    published_count: int,
    replaced_law_id: str,
    replacement_elapsed_ms: int,
    replaced_law_status: str,
    replacement_term_matches: bool,
    replacement_slot: int,
    next_slot: int,
) -> dict[str, object]:
    """Validate and describe the durable state change for one pending law.

    The SQL transaction remains responsible for row locks and writes.  Keeping
    these state rules pure makes it possible to test the exact approve/reject
    contract without a production database.
    """
    normalized_status = str(law_status or "").strip().upper()
    normalized_decision = str(decision or "").strip().upper()
    replaced_id = str(replaced_law_id or "").strip()

    if normalized_status != "PENDING":
        raise PresidentLawReviewError("Этот закон уже нельзя пересмотреть.")
    if normalized_decision not in {"APPROVED", "REJECTED"}:
        raise PresidentLawReviewError("Решение должно быть approved или rejected.")
    if normalized_decision == "REJECTED":
        return {"status": "REJECTED", "slot_no": 0, "replaced_law_id": ""}

    if not replaced_id and int(published_count or 0) >= PRESIDENT_LAW_MAX_PUBLISHED:
        raise PresidentLawReviewError(
            "У президента уже 5 законов. Используй замену опубликованного закона."
        )
    if replaced_id:
        if int(replacement_elapsed_ms or 0) < PRESIDENT_LAW_REPLACE_COOLDOWN_MS:
            raise PresidentLawReviewError("Закон можно заменять не чаще одного раза в 3 дня.")
        if str(replaced_law_status or "").strip().upper() != "PUBLISHED":
            raise PresidentLawReviewError("Этот закон уже нельзя заменить.")
        if not replacement_term_matches:
            raise PresidentLawReviewError("Закон относится к другому президентскому сроку.")
        slot = max(1, int(replacement_slot or 0))
    else:
        slot = max(1, int(next_slot or 0))

    return {"status": "PUBLISHED", "slot_no": slot, "replaced_law_id": replaced_id}
