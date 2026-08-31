"""BM25 ranking over code chunks — lexical retrieval with relevance scores."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass

from app.index.inverted_index import InvertedIndex, tokenize


@dataclass
class SearchResult:
    doc_id: int
    score: float
    file_path: str
    start_line: int
    end_line: int
    text: str


@dataclass
class _Document:
    doc_id: int
    file_path: str
    start_line: int
    end_line: int
    text: str
    length: int
    term_freq: dict[str, int]


class Bm25Index:
    """BM25 over an inverted index. Build: O(n * avg_tokens). Search: O(|candidates| * |query|)."""

    def __init__(self, k1: float = 1.5, b: float = 0.75) -> None:
        self._k1 = k1
        self._b = b
        self._documents: list[_Document] = []
        self._inverted = InvertedIndex()
        self._avg_dl = 0.0
        self._doc_freq: dict[str, int] = {}

    def add(self, file_path: str, start_line: int, end_line: int, text: str) -> int:
        doc_id = len(self._documents)
        tokens = tokenize(text)
        tf: dict[str, int] = {}
        for t in tokens:
            tf[t] = tf.get(t, 0) + 1
        doc = _Document(doc_id, file_path, start_line, end_line, text, len(tokens), tf)
        self._documents.append(doc)
        self._inverted.add_document(doc_id, text)
        for term in set(tokens):
            self._doc_freq[term] = self._doc_freq.get(term, 0) + 1
        total_len = sum(d.length for d in self._documents)
        self._avg_dl = total_len / len(self._documents) if self._documents else 0.0
        return doc_id

    def search(self, query: str, top_k: int = 10) -> list[SearchResult]:
        if not self._documents:
            return []
        candidates = self._inverted.candidate_doc_ids(query)
        if not candidates:
            candidates = set(range(len(self._documents)))
        query_terms = tokenize(query)
        n = len(self._documents)
        scored: list[SearchResult] = []
        for doc_id in candidates:
            doc = self._documents[doc_id]
            score = 0.0
            for term in query_terms:
                if term not in doc.term_freq:
                    continue
                df = self._doc_freq.get(term, 0)
                idf = math.log(1 + (n - df + 0.5) / (df + 0.5))
                tf = doc.term_freq[term]
                denom = tf + self._k1 * (1 - self._b + self._b * doc.length / max(self._avg_dl, 1))
                score += idf * (tf * (self._k1 + 1)) / denom
            if score > 0:
                scored.append(
                    SearchResult(doc.doc_id, score, doc.file_path, doc.start_line, doc.end_line, doc.text)
                )
        scored.sort(key=lambda r: r.score, reverse=True)
        return scored[:top_k]
