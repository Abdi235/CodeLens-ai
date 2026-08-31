"""Inverted index: term -> set of document IDs. O(1) lookup per term."""

from __future__ import annotations

import re
from collections import defaultdict


def tokenize(text: str) -> list[str]:
    return re.findall(r"[a-zA-Z_][a-zA-Z0-9_]{1,}", text.lower())


class InvertedIndex:
    """Hash map + sets: fast candidate retrieval for search queries."""

    def __init__(self) -> None:
        self._postings: dict[str, set[int]] = defaultdict(set)
        self._doc_count = 0

    def add_document(self, doc_id: int, text: str) -> None:
        self._doc_count = max(self._doc_count, doc_id + 1)
        for token in set(tokenize(text)):
            self._postings[token].add(doc_id)

    @property
    def vocabulary_size(self) -> int:
        return len(self._postings)

    def candidate_doc_ids(self, query: str) -> set[int]:
        tokens = tokenize(query)
        if not tokens:
            return set()
        candidates: set[int] | None = None
        for token in tokens:
            hits = self._postings.get(token, set())
            candidates = hits if candidates is None else candidates | hits
        return candidates or set()
