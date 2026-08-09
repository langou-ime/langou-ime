import re

from langou_backend.schemas import ConversationTurn, SuggestionRequest

_PATTERNS = (
    (re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)"), "[身份证]"),
    (re.compile(r"(?<!\d)(?:\d[\s-]?){15,18}\d(?!\d)"), "[银行卡]"),
    (
        re.compile(
            r"(?<!\d)(?:\+?86[\s-]?)?1[3-9](?:[\s-]?\d){9}(?!\d)",
            re.IGNORECASE,
        ),
        "[手机号]",
    ),
    (
        re.compile(
            r"(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}"
            r"(?![A-Z0-9.-])",
            re.IGNORECASE,
        ),
        "[邮箱]",
    ),
)


def sanitize_suggestion_request(request: SuggestionRequest) -> SuggestionRequest:
    return request.model_copy(
        update={
            "turns": [
                ConversationTurn(role=turn.role, text=_redact(turn.text))
                for turn in request.turns
            ],
            "draft": _redact(request.draft) if request.draft is not None else None,
            "memory_summary": (
                _redact(request.memory_summary)
                if request.memory_summary is not None
                else None
            ),
        }
    )


def sanitize_generated_summary(value: str) -> str:
    return _redact(" ".join(value.split()))[:4000]


def _redact(value: str) -> str:
    sanitized = value
    for pattern, replacement in _PATTERNS:
        sanitized = pattern.sub(replacement, sanitized)
    return sanitized
