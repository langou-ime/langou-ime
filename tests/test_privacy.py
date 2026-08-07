from langou_backend.privacy import sanitize_suggestion_request
from langou_backend.schemas import SuggestionRequest


def test_sanitizer_removes_common_identifiers_before_model_or_storage() -> None:
    request = SuggestionRequest.model_validate(
        {
            "request_id": "req_01JZQ6K3EP7EZAW4ZK2B7J1X8M",
            "device_id": "dev_01JZQ6M0HZK9TBEXB1N6H7Y2RP",
            "application": "wechat",
            "locale": "zh-CN",
            "turns": [
                {
                    "role": "other",
                    "text": (
                        "电话13800138000，邮箱girl@example.com，"
                        "身份证11010519491231002X，卡号6222021234567890"
                    ),
                }
            ],
            "draft": "也可以打 +86 139 0013 9000",
            "save_history": True,
        }
    )

    sanitized = sanitize_suggestion_request(request)
    combined = " ".join(
        [turn.text for turn in sanitized.turns] + [sanitized.draft or ""]
    )

    assert "13800138000" not in combined
    assert "girl@example.com" not in combined
    assert "11010519491231002X" not in combined
    assert "6222021234567890" not in combined
    assert "[手机号]" in combined
    assert "[邮箱]" in combined
    assert "[身份证]" in combined
    assert "[银行卡]" in combined
