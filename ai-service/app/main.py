from __future__ import annotations

import tempfile
import zipfile
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.llm import explain, generate_fix, llm_enabled, metrics_snapshot
from app.scanner.semgrep_runner import scan_directory

app = FastAPI(
    title="SecureAI AI Service",
    description="Static analysis + LLM vulnerability explanation and fix generation",
    version="0.2.0",
)

METRICS: dict[str, Any] = {
    "explained": 0,
    "fixes_generated": 0,
    "fix_accepted": 0,
}


class ExplainRequest(BaseModel):
    issue_type: str = Field(..., examples=["SQL Injection"])
    code: str
    severity: str | None = None
    file_location: str | None = None


class ExplainResponse(BaseModel):
    why_dangerous: str
    attack_scenario: str
    secure_fix: str
    developer_summary: str
    llm_used: bool


class FixRequest(BaseModel):
    issue_type: str
    code: str
    language: str = "java"


class FixResponse(BaseModel):
    before: str
    after: str
    explanation: str
    llm_used: bool


class ScanPathRequest(BaseModel):
    path: str


class FixFeedbackRequest(BaseModel):
    accepted: bool


@app.get("/health")
def health():
    return {"status": "ok", "service": "secureai-ai", "llm_enabled": llm_enabled()}


@app.get("/metrics")
def metrics():
    return metrics_snapshot(METRICS)


@app.post("/metrics/fix-feedback")
def fix_feedback(payload: FixFeedbackRequest):
    if payload.accepted:
        METRICS["fix_accepted"] = int(METRICS.get("fix_accepted", 0)) + 1
    return metrics_snapshot(METRICS)


@app.post("/explain", response_model=ExplainResponse)
def explain_vulnerability(payload: ExplainRequest):
    result = explain(payload.issue_type, payload.code, payload.severity, payload.file_location)
    METRICS["explained"] = int(METRICS.get("explained", 0)) + 1
    return ExplainResponse(**result, llm_used=llm_enabled())


@app.post("/fix", response_model=FixResponse)
def generate_fix_endpoint(payload: FixRequest):
    result = generate_fix(payload.issue_type, payload.code, payload.language)
    METRICS["fixes_generated"] = int(METRICS.get("fixes_generated", 0)) + 1
    return FixResponse(**result, llm_used=llm_enabled())


@app.post("/scan/path")
def scan_path_endpoint(payload: ScanPathRequest):
    try:
        return scan_directory(payload.path)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"Scan failed: {exc}") from exc


@app.post("/scan/upload")
async def scan_upload(file: UploadFile = File(...)):
    suffix = Path(file.filename or "upload.zip").suffix.lower()
    with tempfile.TemporaryDirectory(prefix="secureai-scan-") as tmp:
        tmp_path = Path(tmp)
        if suffix == ".zip":
            zip_path = tmp_path / "upload.zip"
            zip_path.write_bytes(await file.read())
            extract_dir = tmp_path / "src"
            extract_dir.mkdir()
            with zipfile.ZipFile(zip_path, "r") as zf:
                zf.extractall(extract_dir)
            return scan_directory(extract_dir)

        # Single source file upload
        target = tmp_path / (file.filename or "source.txt")
        target.write_bytes(await file.read())
        return scan_directory(tmp_path)
