import pytest
from pathlib import Path

from app.scanner.semgrep_runner import scan_directory


def test_samples_have_findings():
    root = Path(__file__).resolve().parents[2] / "samples"
    result = scan_directory(root)
    assert result["vulnerability_count"] >= 3
    types = {f["type"] for f in result["findings"]}
    assert any("SQL" in t or "XSS" in t or "Credential" in t for t in types)
