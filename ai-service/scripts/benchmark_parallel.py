#!/usr/bin/env python3
"""Measure sequential vs concurrent file scanning on the samples directory."""

from __future__ import annotations

import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-service"))

from app.retrieval.chunker import iter_source_files  # noqa: E402
from app.worker.pipeline import _parallel_file_scan, _scan_single_file  # noqa: E402


def sequential_scan(root: Path) -> tuple[int, float]:
    files = iter_source_files(root)
    started = time.perf_counter()
    findings = []
    for fp in files:
        findings.extend(_scan_single_file(root, fp))
    return len(findings), time.perf_counter() - started


def concurrent_scan(root: Path, workers: int) -> tuple[int, float]:
    files = iter_source_files(root)
    started = time.perf_counter()
    findings = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(_scan_single_file, root, fp) for fp in files]
        for future in as_completed(futures):
            findings.extend(future.result())
    return len(findings), time.perf_counter() - started


def main() -> None:
    samples = ROOT / "samples"
    file_workers = int(os.environ.get("FILE_WORKERS", "4"))
    file_count = len(iter_source_files(samples))

    seq_count, seq_time = sequential_scan(samples)
    conc_count, conc_time = concurrent_scan(samples, file_workers)
    pipeline_count, pipeline_time = len(_parallel_file_scan(samples)), 0.0
    pipeline_started = time.perf_counter()
    pipeline_count = len(_parallel_file_scan(samples))
    pipeline_time = time.perf_counter() - pipeline_started

    report = {
        "repository": str(samples),
        "file_count": file_count,
        "file_workers": file_workers,
        "sequential": {"findings": seq_count, "seconds": round(seq_time, 3)},
        "concurrent_thread_pool": {"findings": conc_count, "seconds": round(conc_time, 3)},
        "pipeline_parallel_scan": {"findings": pipeline_count, "seconds": round(pipeline_time, 3)},
        "speedup_vs_sequential": round(seq_time / conc_time, 3) if conc_time > 0 else None,
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
