from pathlib import Path

import orjson


def test_committed_openapi_contract_contains_v1_surface() -> None:
    contract_path = Path(__file__).parents[1] / "contracts" / "openapi-v1.json"
    contract = orjson.loads(contract_path.read_bytes())

    assert contract["info"]["version"] == "1.0.0"
    assert {
        "/v1/devices/guest-session",
        "/v1/auth/sms/send",
        "/v1/auth/sms/verify",
        "/v1/auth/sms/merge",
        "/v1/auth/token/refresh",
        "/v1/ai/suggestions:stream",
        "/v1/history",
        "/v1/settings",
        "/v1/releases/{platform}/latest",
    }.issubset(contract["paths"])
    request_schema = contract["components"]["schemas"]["SuggestionRequest"]
    assert "screenshot" not in request_schema.get("properties", {})
    assert {
        "conversation_id",
        "memory_summary",
        "trigger",
    }.issubset(request_schema["properties"])
