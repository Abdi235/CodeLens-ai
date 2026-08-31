"""Tests for code retrieval and NLP classification."""

from pathlib import Path

from app.nlp.classifier import VulnerabilityClassifier
from app.nlp.normalizer import normalize_description, normalize_remediation
from app.retrieval.chunker import build_chunks
from app.retrieval.indexer import CodeRetriever


def test_chunker_builds_chunks_from_samples():
    root = Path(__file__).resolve().parents[2] / "samples"
    chunks = build_chunks(root)
    assert len(chunks) > 0
    assert all(c.file_path for c in chunks)
    assert all(c.text for c in chunks)


def test_retriever_returns_relevant_chunk():
    root = Path(__file__).resolve().parents[2] / "samples"
    chunks = build_chunks(root)
    retriever = CodeRetriever(chunks)
    results = retriever.retrieve("sql injection database query", top_k=2)
    assert len(results) >= 1
    assert results[0].score > 0.05


def test_classifier_normalizes_sql_injection():
    classifier = VulnerabilityClassifier()
    result = classifier.classify(
        "SQL Injection",
        "unsanitized user input concatenated into database query",
        'query = "SELECT * FROM users WHERE id=" + userId',
    )
    assert result.normalized_type == "SQL Injection"
    assert result.confidence > 0


def test_normalizer_trims_and_defaults():
    assert normalize_description("  hello  ") == "hello"
    assert "OWASP" in normalize_remediation("")
