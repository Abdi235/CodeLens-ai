"""Built-in static analysis rules (Semgrep-compatible findings).

Used when Semgrep CLI is unavailable (e.g. native Windows) and as a
supplemental pass. Rules target common OWASP issues.
"""

from __future__ import annotations

from dataclasses import dataclass
import re
from pathlib import Path


@dataclass(frozen=True)
class Rule:
    rule_id: str
    issue_type: str
    severity: str
    pattern: re.Pattern[str]
    description: str
    recommendation: str
    extensions: tuple[str, ...]


RULES: list[Rule] = [
    Rule(
        rule_id="secureai.sql-concat",
        issue_type="SQL Injection",
        severity="CRITICAL",
        pattern=re.compile(
            r"(?i)(executeQuery|executeUpdate|createQuery|createNativeQuery|"
            r"Statement\.execute|\.query\()\s*\([^)]*(\+|`|\.format\(|f[\"'])"
        ),
        description="SQL query appears to be built with string concatenation or formatting.",
        recommendation="Use parameterized queries / PreparedStatement / bind variables.",
        extensions=(".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".cs", ".php", ".go"),
    ),
    Rule(
        rule_id="secureai.sql-select-plus",
        issue_type="SQL Injection",
        severity="HIGH",
        pattern=re.compile(
            r"(?i)[\"']\s*SELECT\b[^\"']*[\"']\s*\+|=\s*[\"']\s*SELECT\b[^\"']*[\"']\s*\+"
        ),
        description="SELECT statement concatenated with user-controlled or dynamic values.",
        recommendation="Replace concatenation with PreparedStatement placeholders.",
        extensions=(".java", ".py", ".js", ".ts", ".cs", ".php"),
    ),
    Rule(
        rule_id="secureai.xss-innerhtml",
        issue_type="Cross-Site Scripting (XSS)",
        severity="HIGH",
        pattern=re.compile(r"(?i)\.innerHTML\s*=|dangerouslySetInnerHTML|document\.write\s*\("),
        description="Unsanitized HTML insertion can enable XSS.",
        recommendation="Use textContent / safe templating, or sanitize with a vetted library.",
        extensions=(".js", ".ts", ".jsx", ".tsx", ".html", ".vue"),
    ),
    Rule(
        rule_id="secureai.hardcoded-password",
        issue_type="Hardcoded Credentials",
        severity="CRITICAL",
        pattern=re.compile(
            r"(?i)(password|passwd|pwd|api[_-]?key|secret[_-]?key|access[_-]?token)\s*=\s*[\"'][^\"']{4,}[\"']"
        ),
        description="Possible hardcoded credential or secret in source.",
        recommendation="Move secrets to environment variables or a secrets manager.",
        extensions=(".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".cs", ".go", ".yml", ".yaml", ".properties", ".env"),
    ),
    Rule(
        rule_id="secureai.weak-crypto-md5",
        issue_type="Weak Cryptography",
        severity="MEDIUM",
        pattern=re.compile(r"(?i)MessageDigest\.getInstance\(\s*[\"']MD5[\"']|hashlib\.md5|MD5\.Create\("),
        description="MD5 is cryptographically broken for integrity/password hashing.",
        recommendation="Use SHA-256+ for integrity and bcrypt/argon2/scrypt for passwords.",
        extensions=(".java", ".py", ".cs", ".js", ".ts"),
    ),
    Rule(
        rule_id="secureai.weak-crypto-des",
        issue_type="Weak Cryptography",
        severity="HIGH",
        pattern=re.compile(r"(?i)Cipher\.getInstance\(\s*[\"']DES|DES/|/DES[\"']|CryptoJS\.DES"),
        description="DES encryption is obsolete and insecure.",
        recommendation="Use AES-GCM (or equivalent modern AEAD cipher).",
        extensions=(".java", ".js", ".ts", ".cs"),
    ),
    Rule(
        rule_id="secureai.eval",
        issue_type="Dangerous Function",
        severity="HIGH",
        pattern=re.compile(r"(?i)(?<![.\w])eval\s*\(|exec\s*\(|Runtime\.getRuntime\(\)\.exec\s*\(|ProcessBuilder\s*\("),
        description="Dynamic code/command execution increases RCE risk.",
        recommendation="Avoid eval/exec; use safe parsers and allow-listed commands.",
        extensions=(".java", ".py", ".js", ".ts", ".jsx", ".tsx"),
    ),
    Rule(
        rule_id="secureai.path-traversal",
        issue_type="Path Traversal",
        severity="HIGH",
        pattern=re.compile(r"(?i)new\s+File\s*\([^)]*\+|Paths\.get\s*\([^)]*\+|open\s*\([^)]*\+"),
        description="File path built from concatenation may allow directory traversal.",
        recommendation="Resolve against a base directory and reject path escapes.",
        extensions=(".java", ".py", ".js", ".ts", ".cs"),
    ),
]


SKIP_DIRS = {
    ".git",
    "node_modules",
    "target",
    "dist",
    "build",
    ".venv",
    "venv",
    "__pycache__",
    ".idea",
    ".mvn",
}


def iter_source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.stat().st_size > 1_000_000:
            continue
        files.append(path)
    return files


def scan_path(root: Path) -> list[dict]:
    findings: list[dict] = []
    for file_path in iter_source_files(root):
        suffix = file_path.suffix.lower()
        try:
            text = file_path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        rel = str(file_path.relative_to(root)).replace("\\", "/")
        for rule in RULES:
            if rule.extensions and suffix not in rule.extensions:
                continue
            for match in rule.pattern.finditer(text):
                line = text.count("\n", 0, match.start()) + 1
                findings.append(
                    {
                        "rule_id": rule.rule_id,
                        "type": rule.issue_type,
                        "severity": rule.severity,
                        "file_location": rel,
                        "line_number": line,
                        "description": rule.description,
                        "recommendation": rule.recommendation,
                        "code_snippet": match.group(0)[:240],
                        "engine": "secureai-rules",
                    }
                )
    return findings
