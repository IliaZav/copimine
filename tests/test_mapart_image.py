from pathlib import Path

import pytest

from backend import mapart


def _png():
    import io

    from PIL import Image

    out = io.BytesIO()
    Image.new("RGB", (4, 4), (10, 200, 90)).save(out, format="PNG")
    return out.getvalue()


def test_broken_png_is_a_client_error_not_a_server_crash():
    """Pillow raises SyntaxError on a bad PNG checksum.

    It used to escape the handler and become HTTP 500 internal_error, so a
    corrupt upload looked like a site failure instead of a rejected file.
    """
    import base64

    good = _png()
    broken = good[:40] + b"\x00" * 8 + good[48:]

    with pytest.raises(Exception) as caught:
        mapart._png(base64.b64encode(broken).decode())

    error = caught.value
    assert getattr(error, "status_code", None) == 400, error
    assert error.detail["code"] == "invalid_mapart_image"


def test_valid_png_still_passes_the_same_helper():
    import base64

    png = mapart._png(base64.b64encode(_png()).decode())
    assert png.startswith(b"\x89PNG")
    assert len(png) > 0