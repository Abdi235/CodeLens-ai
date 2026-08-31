"""TF-IDF based semantic retrieval over code chunks."""

from __future__ import annotations

from dataclasses import dataclass

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.retrieval.chunker import CodeChunk


@dataclass
class RetrievedChunk:
    chunk: CodeChunk
    score: float


class CodeRetriever:
    def __init__(self, chunks: list[CodeChunk]) -> None:
        self._chunks = chunks
        self._vectorizer = TfidfVectorizer(stop_words="english", max_features=5000)
        if chunks:
            corpus = [f"{c.file_path} {c.text}" for c in chunks]
            self._matrix = self._vectorizer.fit_transform(corpus)
        else:
            self._matrix = None

    def retrieve(self, query: str, top_k: int = 3) -> list[RetrievedChunk]:
        if not self._chunks or self._matrix is None:
            return []
        query_vec = self._vectorizer.transform([query])
        scores = cosine_similarity(query_vec, self._matrix).flatten()
        ranked = sorted(
            ((float(scores[i]), self._chunks[i]) for i in range(len(self._chunks))),
            key=lambda x: x[0],
            reverse=True,
        )
        return [RetrievedChunk(chunk=chunk, score=score) for score, chunk in ranked[:top_k] if score > 0.05]
