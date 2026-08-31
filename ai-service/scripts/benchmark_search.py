#!/usr/bin/env python3
"""Compare naive linear scan vs inverted-index candidate retrieval."""

from __future__ import annotations

import json
import time
from pathlib import Path

import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-service"))

from app.index.bm25 import Bm25Index
from app.index.inverted_index import InvertedIndex, tokenize
from app.retrieval.chunker import build_chunks


def naive_search(chunks: list, query: str) -> int:
    q = set(tokenize(query))
    hits = 0
    for chunk in chunks:
        doc = set(tokenize(chunk.text))
        if q & doc:
            hits += 1
    return hits


def indexed_search(chunks: list, query: str) -> int:
    inverted = InvertedIndex()
    for i, chunk in enumerate(chunks):
        inverted.add_document(i, chunk.text)
    candidates = inverted.candidate_doc_ids(query)
    return len(candidates)


def bm25_search(chunks: list, query: str) -> int:
    index = Bm25Index()
    for chunk in chunks:
        index.add(chunk.file_path, chunk.start_line, chunk.end_line, chunk.text)
    return len(index.search(query, top_k=10))


def main() -> None:
    samples = ROOT / "samples"
    chunks = build_chunks(samples)
    query = "sql injection database query"

    t0 = time.perf_counter()
    naive_hits = naive_search(chunks, query)
    naive_ms = (time.perf_counter() - t0) * 1000

    t1 = time.perf_counter()
    index_hits = indexed_search(chunks, query)
    index_ms = (time.perf_counter() - t1) * 1000

    t2 = time.perf_counter()
    bm25_hits = bm25_search(chunks, query)
    bm25_ms = (time.perf_counter() - t2) * 1000

    report = {
        "chunks": len(chunks),
        "query": query,
        "naive": {"hits": naive_hits, "ms": round(naive_ms, 3)},
        "inverted_index": {"hits": index_hits, "ms": round(index_ms, 3)},
        "bm25": {"hits": bm25_hits, "ms": round(bm25_ms, 3)},
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
