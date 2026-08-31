"""End-to-end repository analysis pipeline for the worker."""

from __future__ import annotations

import logging
import shutil
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from app.llm import explain
from app.nlp.classifier import VulnerabilityClassifier
from app.nlp.normalizer import normalize_description, normalize_remediation
from app.retrieval.chunker import build_chunks, iter_source_files
from app.retrieval.indexer import CodeRetriever
from app.scanner.rules import scan_path
from app.scanner.semgrep_runner import scan_directory
from app.worker import db
from app.worker.config import FILE_WORKERS, WORKSPACE_DIR

log = logging.getLogger(__name__)
_classifier = VulnerabilityClassifier()


def _prepare_repository(repository: str, work_dir: Path) -> Path:
    work_dir.mkdir(parents=True, exist_ok=True)
    target = work_dir / "repo"

    if target.exists():
        shutil.rmtree(target, ignore_errors=True)

    repo = repository.strip()
    if not repo or repo.lower() in {"samples", "samples/"}:
        candidates = [Path("samples"), Path("/app/samples"), Path("../samples")]
        for candidate in candidates:
            if candidate.exists():
                shutil.copytree(candidate, target)
                return target
        raise FileNotFoundError("samples/ directory not found")

    if repo.startswith("http://") or repo.startswith("https://") or repo.startswith("git@"):
        subprocess.run(
            ["git", "clone", "--depth", "1", repo, str(target)],
            check=True,
            capture_output=True,
            text=True,
            timeout=180,
        )
        return target

    local = Path(repo)
    if local.exists():
        shutil.copytree(local, target)
        return target

    raise FileNotFoundError(f"Cannot resolve repository: {repository}")


def _scan_single_file(root: Path, file_path: Path) -> list[dict]:
    """Scan one file in isolation (I/O-bound regex work — thread pool)."""
    rel_parent = file_path.parent.relative_to(root)
    temp_root = root / "_partial" / str(rel_parent).replace("\\", "_").replace("/", "_")
    temp_root.mkdir(parents=True, exist_ok=True)
    temp_file = temp_root / file_path.name
    shutil.copy2(file_path, temp_file)
    try:
        return scan_path(temp_root)
    finally:
        shutil.rmtree(temp_root, ignore_errors=True)


def _parallel_file_scan(root: Path) -> list[dict]:
    files = iter_source_files(root)
    if not files:
        return scan_directory(root).get("findings", [])

    findings: list[dict] = []
    semgrep_findings = scan_directory(root).get("findings", [])
    findings.extend(semgrep_findings)

    seen = {(f.get("file_location"), f.get("line_number"), f.get("type")) for f in findings}

    with ThreadPoolExecutor(max_workers=FILE_WORKERS) as pool:
        futures = {pool.submit(_scan_single_file, root, fp): fp for fp in files}
        for future in as_completed(futures):
            try:
                for item in future.result():
                    key = (item.get("file_location"), item.get("line_number"), item.get("type"))
                    if key not in seen:
                        findings.append(item)
                        seen.add(key)
            except Exception as exc:  # noqa: BLE001
                log.warning("File scan failed %s: %s", futures[future], exc)

    return findings


def _enrich_finding(finding: dict, retriever: CodeRetriever) -> dict:
    issue_type = finding.get("type", "Security Finding")
    description = normalize_description(finding.get("description"))
    remediation = normalize_remediation(finding.get("recommendation"))
    snippet = finding.get("code_snippet", "")

    classification = _classifier.classify(issue_type, description, snippet)
    query = f"{issue_type} {description} {finding.get('file_location', '')}"
    retrieved = retriever.retrieve(query, top_k=2)
    context_parts = [
        f"// {r.chunk.file_path}:{r.chunk.start_line}-{r.chunk.end_line} (score={r.score:.3f})\n{r.chunk.text}"
        for r in retrieved
    ]
    retrieved_context = "\n\n---\n\n".join(context_parts) if context_parts else snippet

    llm_input = retrieved_context[:3000] if retrieved_context else snippet
    explanation = explain(
        classification.normalized_type,
        llm_input,
        finding.get("severity"),
        finding.get("file_location"),
    )

    return {
        "vulnerability_type": issue_type,
        "normalized_type": classification.normalized_type,
        "severity": finding.get("severity", "MEDIUM"),
        "file_path": finding.get("file_location"),
        "line_number": finding.get("line_number"),
        "description": description,
        "remediation": remediation,
        "retrieved_context": retrieved_context[:4000],
        "ai_explanation": explanation.get("developer_summary") or explanation.get("why_dangerous"),
        "classification_confidence": classification.confidence,
        "rule_id": finding.get("rule_id"),
    }


def run_analysis(job_id: str, repository: str) -> int:
    started = time.perf_counter()
    work_dir = Path(WORKSPACE_DIR) / job_id
    log.info("job_id=%s starting analysis repository=%s", job_id, repository)

    root = _prepare_repository(repository, work_dir)
    chunks = build_chunks(root)
    retriever = CodeRetriever(chunks)
    raw_findings = _parallel_file_scan(root)

    db.delete_findings(job_id)

    enriched: list[dict] = []
    with ThreadPoolExecutor(max_workers=FILE_WORKERS) as pool:
        futures = [pool.submit(_enrich_finding, f, retriever) for f in raw_findings]
        for future in as_completed(futures):
            try:
                enriched.append(future.result())
            except Exception as exc:  # noqa: BLE001
                log.warning("job_id=%s enrichment failed: %s", job_id, exc)

    for finding in enriched:
        db.insert_finding(job_id, finding)

    elapsed = time.perf_counter() - started
    log.info(
        "job_id=%s analysis complete findings=%d duration_sec=%.2f files=%d chunks=%d",
        job_id,
        len(enriched),
        elapsed,
        len(iter_source_files(root)),
        len(chunks),
    )

    shutil.rmtree(work_dir, ignore_errors=True)
    return len(enriched)
