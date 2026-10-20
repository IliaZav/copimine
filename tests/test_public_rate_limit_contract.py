import ast
import unittest
from pathlib import Path
from types import SimpleNamespace


MAIN = Path(__file__).resolve().parents[1] / "admin-web" / "backend" / "main.py"


def route_rate_limit(function_name: str, environment: dict[str, str] | None = None) -> int:
    source = MAIN.read_text(encoding="utf-8-sig")
    tree = ast.parse(source)
    environment = environment or {}
    namespace = {
        "max": max,
        "int": int,
        "os": SimpleNamespace(getenv=lambda key, default: environment.get(key, default)),
    }
    constants = {}
    for assignment in tree.body:
        if isinstance(assignment, ast.Assign):
            for target in assignment.targets:
                if isinstance(target, ast.Name) and target.id in {
                    "PUBLIC_STATUS_RATE_LIMIT", "PUBLIC_PRESIDENT_SKIN_RATE_LIMIT",
                }:
                    expression = ast.Expression(assignment.value)
                    constants[target.id] = eval(compile(expression, str(MAIN), "eval"), {"__builtins__": {}}, namespace)
    for node in ast.walk(tree):
        if not isinstance(node, (ast.AsyncFunctionDef, ast.FunctionDef)) or node.name != function_name:
            continue
        for child in ast.walk(node):
            if not isinstance(child, ast.Call) or not isinstance(child.func, ast.Name):
                continue
            if child.func.id != "check_rate_limit":
                continue
            for keyword in child.keywords:
                if keyword.arg != "limit":
                    continue
                if isinstance(keyword.value, ast.Constant):
                    return int(keyword.value.value)
                if isinstance(keyword.value, ast.Name) and keyword.value.id in constants:
                    return constants[keyword.value.id]
    raise AssertionError(f"rate limit not found for {function_name}")


class PublicRateLimitContractTest(unittest.TestCase):
    def test_public_burst_endpoints_allow_at_least_fifty_requests_per_ip(self) -> None:
        self.assertGreaterEqual(route_rate_limit("public_status"), 50)
        self.assertGreaterEqual(route_rate_limit("public_president_skin_body"), 50)

    def test_public_limits_use_configured_values_and_enforce_production_floor(self) -> None:
        for route, setting in [("public_status", "PUBLIC_STATUS_RATE_LIMIT"), ("public_president_skin_body", "PUBLIC_PRESIDENT_SKIN_RATE_LIMIT")]:
            with self.subTest(route=route):
                self.assertGreaterEqual(route_rate_limit(route, {setting: "1"}), 50)
                self.assertEqual(route_rate_limit(route, {setting: "173"}), 173)


if __name__ == "__main__":
    unittest.main()
