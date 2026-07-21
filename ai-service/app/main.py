from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(
    title="SecureAI AI Service",
    description="LLM-backed vulnerability explanation and fix generation",
    version="0.1.0",
)


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


class FixRequest(BaseModel):
    issue_type: str
    code: str
    language: str = "java"


class FixResponse(BaseModel):
    before: str
    after: str
    explanation: str


@app.get("/health")
def health():
    return {"status": "ok", "service": "secureai-ai"}


@app.post("/explain", response_model=ExplainResponse)
def explain_vulnerability(payload: ExplainRequest):
    """
    Week 3 will call a real LLM. Week 1 returns a deterministic template
    so the Spring Boot client contract is ready.
    """
    return ExplainResponse(
        why_dangerous=(
            f"{payload.issue_type} lets an attacker influence program behavior "
            "in ways the developer did not intend, often leading to data theft or takeover."
        ),
        attack_scenario=(
            "An attacker crafts malicious input that changes query or render logic, "
            "then extracts sensitive data or executes unintended actions."
        ),
        secure_fix=(
            "Validate input, use parameterized APIs / safe templating, "
            "and apply least-privilege controls around the affected resource."
        ),
        developer_summary=(
            f"Treat this {payload.issue_type} finding as actionable: "
            "isolate the unsafe sink, replace it with a safe API, and add a regression test."
        ),
    )


@app.post("/fix", response_model=FixResponse)
def generate_fix(payload: FixRequest):
    after = payload.code
    if "SQL" in payload.issue_type.upper() or "+" in payload.code:
        after = (
            'PreparedStatement stmt = connection.prepareStatement(\n'
            '    "SELECT * FROM users WHERE id = ?"\n'
            ');\n'
            'stmt.setLong(1, userId);\n'
            'ResultSet rs = stmt.executeQuery();'
        )

    return FixResponse(
        before=payload.code,
        after=after,
        explanation=(
            f"Suggested remediation for {payload.issue_type}. "
            "Wire this endpoint to an LLM in Week 3 for context-aware patches."
        ),
    )
