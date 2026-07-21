"""Semgrep runner with graceful fallback to built-in rules."""

from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

from .rules import scan_path


SEVERITY_MAP = {
    "ERROR": "HIGH",
    "WARNING": "MEDIUM",
    "INFO": "LOW",
    "CRITICAL": "CRITICAL",
    "HIGH": "HIGH",
    "MEDIUM": "MEDIUM",
    "LOW": "LOW",
}


def _semgrep_available() -> bool:
    return shutil.which("semgrep") is not None


def _run_semgrep(target: Path) -> list[dict]:
    cmd = [
        "semgrep",
        "scan",
        "--config",
        "p/owasp-top-ten",
        "--config",
        "p/security-audit",
        "--json",
        "--quiet",
        str(target),
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, timeout=300, check=False)
    if not proc.stdout.strip():
        return []
    data = json.loads(proc.stdout)
    findings: list[dict] = []
    for result in data.get("results", []):
        extra = result.get("extra", {})
        meta = extra.get("metadata", {})
        severity = str(meta.get("severity") or extra.get("severity") or "MEDIUM").upper()
        findings.append(
            {
                "rule_id": result.get("check_id", "semgrep"),
                "type": meta.get("cwe", [None])[0] if isinstance(meta.get("cwe"), list) else (
                    meta.get("vulnerability_class", ["Security Finding"])[0]
                    if isinstance(meta.get("vulnerability_class"), list)
                    else result.get("check_id", "Security Finding")
                ),
                "severity": SEVERITY_MAP.get(severity, "MEDIUM"),
                "file_location": result.get("path", "").replace("\\", "/"),
                "line_number": (result.get("start") or {}).get("line"),
                "description": extra.get("message") or "Semgrep security finding",
                "recommendation": meta.get("fix") or "Review and remediate per OWASP guidance.",
                "code_snippet": (extra.get("lines") or "")[:240],
                "engine": "semgrep",
            }
        )
    return findings


def scan_directory(target: str | Path) -> dict:
    root = Path(target).resolve()
    if not root.exists():
        raise FileNotFoundError(f"Scan path does not exist: {root}")

    engines: list[str] = []
    findings: list[dict] = []

    if _semgrep_available():
        try:
            semgrep_findings = _run_semgrep(root)
            findings.extend(semgrep_findings)
            engines.append("semgrep")
        except Exception as exc:  # noqa: BLE001 - degrade gracefully
            engines.append(f"semgrep-error:{exc}")

    builtin = scan_path(root)
    # Prefer Semgrep duplicates; keep unique by file+line+type
    seen = {(f["file_location"], f.get("line_number"), f["type"]) for f in findings}
    for item in builtin:
        key = (item["file_location"], item.get("line_number"), item["type"])
        if key not in seen:
            findings.append(item)
            seen.add(key)
    engines.append("secureai-rules")

    return {
        "path": str(root),
        "engines": engines,
        "vulnerability_count": len(findings),
        "findings": findings,
    }
