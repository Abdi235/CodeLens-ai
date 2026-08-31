"""Split source files into meaningful chunks for retrieval."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

SOURCE_EXTENSIONS = {
    ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".cs", ".php", ".rb", ".cpp", ".c", ".h"
}
SKIP_DIRS = {".git", "node_modules", "target", "dist", "build", ".venv", "venv", "__pycache__", ".mvn"}
MAX_CHUNK_LINES = 80


@dataclass(frozen=True)
class CodeChunk:
    file_path: str
    start_line: int
    end_line: int
    text: str
    language: str


def _language_for(path: Path) -> str:
    ext = path.suffix.lower()
    return {
        ".py": "python",
        ".java": "java",
        ".js": "javascript",
        ".jsx": "javascript",
        ".ts": "typescript",
        ".tsx": "typescript",
        ".go": "go",
        ".cs": "csharp",
        ".php": "php",
    }.get(ext, "unknown")


def iter_source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() not in SOURCE_EXTENSIONS:
            continue
        if path.stat().st_size > 1_000_000:
            continue
        files.append(path)
    return files


def chunk_file(root: Path, file_path: Path) -> list[CodeChunk]:
    rel = str(file_path.relative_to(root)).replace("\\", "/")
    try:
        lines = file_path.read_text(encoding="utf-8", errors="ignore").splitlines()
    except OSError:
        return []

    chunks: list[CodeChunk] = []
    language = _language_for(file_path)
    for i in range(0, len(lines), MAX_CHUNK_LINES):
        block = lines[i : i + MAX_CHUNK_LINES]
        if not block:
            continue
        chunks.append(
            CodeChunk(
                file_path=rel,
                start_line=i + 1,
                end_line=i + len(block),
                text="\n".join(block),
                language=language,
            )
        )
    return chunks


def build_chunks(root: Path) -> list[CodeChunk]:
    all_chunks: list[CodeChunk] = []
    for file_path in iter_source_files(root):
        all_chunks.extend(chunk_file(root, file_path))
    return all_chunks
