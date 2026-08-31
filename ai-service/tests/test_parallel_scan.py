"""Benchmark-style test for sequential vs concurrent file scanning."""

import time
from pathlib import Path

from app.worker.pipeline import _parallel_file_scan


def test_parallel_scan_finds_issues_in_samples():
    root = Path(__file__).resolve().parents[2] / "samples"
    findings = _parallel_file_scan(root)
    assert len(findings) >= 3


def test_parallel_scan_performance_smoke():
    """Smoke test: concurrent scan completes in reasonable time on samples."""
    root = Path(__file__).resolve().parents[2] / "samples"
    started = time.perf_counter()
    findings = _parallel_file_scan(root)
    elapsed = time.perf_counter() - started
    assert findings
    assert elapsed < 30.0
