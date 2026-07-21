"""LLM helpers with OpenAI when configured, deterministic fallback otherwise."""

from __future__ import annotations

import json
import os
from typing import Any

import httpx

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")


def llm_enabled() -> bool:
    return bool(OPENAI_API_KEY)


def _chat(system: str, user: str) -> str | None:
    if not llm_enabled():
        return None
    payload = {
        "model": OPENAI_MODEL,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    headers = {
        "Authorization": f"Bearer {OPENAI_API_KEY}",
        "Content-Type": "application/json",
    }
    try:
        with httpx.Client(timeout=60.0) as client:
            response = client.post(f"{OPENAI_BASE_URL}/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]
    except Exception:  # noqa: BLE001
        return None


def explain(issue_type: str, code: str, severity: str | None, file_location: str | None) -> dict[str, str]:
    system = (
        "You are a senior cybersecurity engineer. Respond ONLY with valid JSON keys: "
        "why_dangerous, attack_scenario, secure_fix, developer_summary."
    )
    user = (
        f"Issue: {issue_type}\nSeverity: {severity or 'UNKNOWN'}\n"
        f"File: {file_location or 'n/a'}\nCode:\n{code}"
    )
    raw = _chat(system, user)
    if raw:
        try:
            parsed = json.loads(raw)
            if all(k in parsed for k in ("why_dangerous", "attack_scenario", "secure_fix", "developer_summary")):
                return {k: str(parsed[k]) for k in ("why_dangerous", "attack_scenario", "secure_fix", "developer_summary")}
        except json.JSONDecodeError:
            pass

    return {
        "why_dangerous": (
            f"{issue_type} lets an attacker influence program behavior "
            "in ways the developer did not intend, often leading to data theft or takeover."
        ),
        "attack_scenario": (
            "An attacker crafts malicious input that changes query or render logic, "
            "then extracts sensitive data or executes unintended actions."
        ),
        "secure_fix": (
            "Validate input, use parameterized APIs / safe templating, "
            "and apply least-privilege controls around the affected resource."
        ),
        "developer_summary": (
            f"Treat this {issue_type} finding as actionable: "
            "isolate the unsafe sink, replace it with a safe API, and add a regression test."
        ),
    }


def generate_fix(issue_type: str, code: str, language: str) -> dict[str, str]:
    system = (
        "You are a senior cybersecurity engineer. Respond ONLY with valid JSON keys: "
        "after, explanation. 'after' must be secure replacement code."
    )
    user = f"Language: {language}\nIssue: {issue_type}\nInsecure code:\n{code}"
    raw = _chat(system, user)
    if raw:
        try:
            parsed = json.loads(raw)
            if "after" in parsed and "explanation" in parsed:
                return {"before": code, "after": str(parsed["after"]), "explanation": str(parsed["explanation"])}
        except json.JSONDecodeError:
            pass

    after = code
    upper = issue_type.upper()
    if "SQL" in upper:
        after = (
            'PreparedStatement stmt = connection.prepareStatement(\n'
            '    "SELECT * FROM users WHERE id = ?"\n'
            ');\n'
            'stmt.setLong(1, userId);\n'
            'ResultSet rs = stmt.executeQuery();'
        )
    elif "XSS" in upper:
        after = "element.textContent = userInput; // never assign unsanitized HTML"
    elif "HARDCODED" in upper or "CREDENTIAL" in upper:
        after = 'String password = System.getenv("APP_DB_PASSWORD");'
    elif "CRYPTO" in upper or "MD5" in upper or "DES" in upper:
        after = 'MessageDigest digest = MessageDigest.getInstance("SHA-256");'

    return {
        "before": code,
        "after": after,
        "explanation": (
            f"Suggested remediation for {issue_type}. "
            + ("Generated via LLM." if llm_enabled() else "Deterministic template (set OPENAI_API_KEY for LLM).")
        ),
    }


def metrics_snapshot(store: dict[str, Any]) -> dict[str, Any]:
    explained = int(store.get("explained", 0))
    accepted = int(store.get("fix_accepted", 0))
    fixes = int(store.get("fixes_generated", 0))
    return {
        "explanations_generated": explained,
        "fixes_generated": fixes,
        "fix_acceptance_rate": round((accepted / fixes) * 100, 1) if fixes else 0.0,
        "llm_enabled": llm_enabled(),
        "model": OPENAI_MODEL if llm_enabled() else None,
    }
