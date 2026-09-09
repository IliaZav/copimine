from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def _status_route_source() -> str:
    source = (ROOT / "admin-web" / "backend" / "main.py").read_text(encoding="utf-8")
    start = source.index('@app.get("/api/status")')
    end = source.index('@app.get("/api/dashboard/insights")', start)
    return source[start:end]


def test_status_probe_fanout_does_not_add_unavailable_port_timeouts() -> None:
    route = _status_route_source()

    assert "await asyncio.gather(" in route
    assert route.count("bg(tcp_online") >= 3
    assert "online, latency = await bg(tcp_online" not in route
    assert "web_online, web_latency = await bg(tcp_online" not in route
    assert "backend_online, backend_latency = await bg(tcp_online" not in route
