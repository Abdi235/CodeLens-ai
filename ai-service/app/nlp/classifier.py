"""Security finding classification and normalization (embedding-assisted)."""

from __future__ import annotations

from dataclasses import dataclass

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

CANONICAL_TYPES = [
    "SQL Injection",
    "Cross-Site Scripting (XSS)",
    "Hardcoded Credentials",
    "Weak Cryptography",
    "Dangerous Function",
    "Path Traversal",
    "Insecure Deserialization",
    "Security Misconfiguration",
]

_TYPE_DESCRIPTIONS = {
    "SQL Injection": "database query concatenation unsanitized user input sql injection",
    "Cross-Site Scripting (XSS)": "innerHTML document.write unsanitized html xss script injection",
    "Hardcoded Credentials": "password api key secret token hardcoded credential",
    "Weak Cryptography": "md5 des weak hash cipher encryption",
    "Dangerous Function": "eval exec runtime command execution",
    "Path Traversal": "file path concatenation directory traversal",
    "Insecure Deserialization": "pickle yaml load untrusted deserialize",
    "Security Misconfiguration": "debug enabled cors wildcard insecure default",
}


@dataclass(frozen=True)
class ClassificationResult:
    normalized_type: str
    confidence: float


class VulnerabilityClassifier:
    def __init__(self) -> None:
        labels = list(_TYPE_DESCRIPTIONS.keys())
        corpus = [_TYPE_DESCRIPTIONS[label] for label in labels]
        self._labels = labels
        self._vectorizer = TfidfVectorizer(stop_words="english")
        self._matrix = self._vectorizer.fit_transform(corpus)

    def classify(self, issue_type: str, description: str, code_snippet: str) -> ClassificationResult:
        query = f"{issue_type} {description} {code_snippet}"
        query_vec = self._vectorizer.transform([query])
        scores = cosine_similarity(query_vec, self._matrix).flatten()
        best_idx = int(scores.argmax())
        confidence = float(scores[best_idx])
        return ClassificationResult(
            normalized_type=self._labels[best_idx],
            confidence=round(confidence, 4),
        )
