"""Tests for BM25 index and inverted index."""

from pathlib import Path

from app.index.bm25 import Bm25Index
from app.index.inverted_index import InvertedIndex, tokenize
from app.parser.source_parser import parse_repository


def test_tokenize_splits_identifiers():
    tokens = tokenize("authenticate_user LoginHandler")
    assert "authenticate_user" in tokens
    assert "loginhandler" in tokens


def test_inverted_index_candidates():
    idx = InvertedIndex()
    idx.add_document(0, "user authentication login")
    idx.add_document(1, "database query sql")
    hits = idx.candidate_doc_ids("authentication login")
    assert 0 in hits


def test_bm25_ranks_auth_chunk_higher():
    index = Bm25Index()
    index.add("auth.py", 1, 10, "def authenticate_user(request): validate jwt token")
    index.add("db.py", 1, 5, "SELECT * FROM products WHERE id = ?")
    results = index.search("authenticate_user jwt token", top_k=2)
    assert results[0].file_path == "auth.py"
    if len(results) > 1:
        assert results[0].score >= results[1].score


def test_parser_extracts_python_symbols():
    root = Path(__file__).resolve().parents[2] / "samples"
    parsed = parse_repository(root)
    assert any(p.symbols for p in parsed)
