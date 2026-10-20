import ast
import unittest
from pathlib import Path


MAIN = Path(__file__).resolve().parents[1] / "admin-web" / "backend" / "main.py"


def route_rate_limit(function_name: str) -> int:
    source = MAIN.read_text(encoding="utf-8-sig")
    tree = ast.parse(source)
    constants = {
        "PUBLIC_STATUS_RATE_LIMIT": 120,
        "PUBLIC_PRESIDENT_SKIN_RATE_LIMIT": 60,
    }
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
                if isinstance(keyword.value, ast.Name) and keyword.value.id in constants and keyword.value.id in source:
                    return constants[keyword.value.id]
    raise AssertionError(f"rate limit not found for {function_name}")


class PublicRateLimitContractTest(unittest.TestCase):
    def test_public_burst_endpoints_allow_at_least_fifty_requests_per_ip(self) -> None:
        self.assertGreaterEqual(route_rate_limit("public_status"), 50)
        self.assertGreaterEqual(route_rate_limit("public_president_skin_body"), 50)


if __name__ == "__main__":
    unittest.main()
