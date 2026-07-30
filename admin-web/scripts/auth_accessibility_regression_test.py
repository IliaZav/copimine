#!/usr/bin/env python3
"""Keep authentication validation feedback available to assistive technology."""
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    for name in ("signin.html", "register.html"):
        markup = (ROOT / "frontend" / name).read_text(encoding="utf-8")
        assert 'id="loginError"' in markup, f"{name}: missing validation feedback element"
        assert 'id="loginError" class="form-error" role="alert" aria-live="assertive"' in markup, (
            f"{name}: validation feedback must be announced"
        )
    print("Auth accessibility regression test OK")


if __name__ == "__main__":
    main()
