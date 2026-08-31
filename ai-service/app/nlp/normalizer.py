"""Normalize remediation text for consistent reporting."""

from __future__ import annotations

import re

_REPLACEMENTS = (
    (r"\s+", " "),
    (r"(?i)use prepared statements?", "Use parameterized queries / prepared statements"),
    (r"(?i)move secrets to env", "Store secrets in environment variables or a secrets manager"),
)


def normalize_remediation(text: str | None) -> str:
    if not text:
        return "Review and remediate according to OWASP secure coding guidelines."
    normalized = text.strip()
    for pattern, replacement in _REPLACEMENTS:
        normalized = re.sub(pattern, replacement, normalized)
    return normalized[:2000]


def normalize_description(text: str | None) -> str:
    if not text:
        return "Security finding detected by static analysis."
    return re.sub(r"\s+", " ", text.strip())[:2000]
