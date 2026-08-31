"""Modular source parser — extracts symbols and chunks per language."""

from __future__ import annotations

import ast
import re
from dataclasses import dataclass, field
from pathlib import Path

from app.retrieval.chunker import SOURCE_EXTENSIONS, SKIP_DIRS, chunk_file, iter_source_files

JAVA_SYMBOL = re.compile(
    r"^\s*(?:public|private|protected)?\s*(?:static\s+)?(?:class|interface|enum)\s+(\w+)",
    re.MULTILINE,
)
JAVA_METHOD = re.compile(
    r"^\s*(?:public|private|protected)?\s*(?:static\s+)?[\w<>\[\],\s]+\s+(\w+)\s*\([^;]*\)\s*\{",
    re.MULTILINE,
)
JS_SYMBOL = re.compile(
    r"^\s*(?:export\s+)?(?:async\s+)?function\s+(\w+)|^\s*(?:export\s+)?class\s+(\w+)",
    re.MULTILINE,
)


@dataclass
class CodeSymbol:
    name: str
    kind: str
    file_path: str
    line_number: int
    language: str


@dataclass
class ParsedFile:
    file_path: str
    language: str
    symbols: list[CodeSymbol] = field(default_factory=list)
    imports: list[str] = field(default_factory=list)


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
    }.get(ext, "unknown")


def _parse_python(path: Path, rel: str) -> ParsedFile:
    symbols: list[CodeSymbol] = []
    imports: list[str] = []
    try:
        tree = ast.parse(path.read_text(encoding="utf-8", errors="ignore"))
    except SyntaxError:
        return ParsedFile(file_path=rel, language="python", symbols=[], imports=[])

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imports.append(alias.name)
        elif isinstance(node, ast.ImportFrom) and node.module:
            imports.append(node.module)
        elif isinstance(node, ast.FunctionDef):
            symbols.append(CodeSymbol(node.name, "function", rel, node.lineno, "python"))
        elif isinstance(node, ast.ClassDef):
            symbols.append(CodeSymbol(node.name, "class", rel, node.lineno, "python"))

    return ParsedFile(file_path=rel, language="python", symbols=symbols, imports=imports)


def _parse_java(path: Path, rel: str) -> ParsedFile:
    text = path.read_text(encoding="utf-8", errors="ignore")
    symbols: list[CodeSymbol] = []
    for match in JAVA_SYMBOL.finditer(text):
        line = text[: match.start()].count("\n") + 1
        symbols.append(CodeSymbol(match.group(1), "class", rel, line, "java"))
    for match in JAVA_METHOD.finditer(text):
        line = text[: match.start()].count("\n") + 1
        symbols.append(CodeSymbol(match.group(1), "method", rel, line, "java"))
    imports = re.findall(r"^\s*import\s+([\w.]+);", text, re.MULTILINE)
    return ParsedFile(file_path=rel, language="java", symbols=symbols, imports=imports)


def _parse_javascript(path: Path, rel: str, language: str) -> ParsedFile:
    text = path.read_text(encoding="utf-8", errors="ignore")
    symbols: list[CodeSymbol] = []
    for match in JS_SYMBOL.finditer(text):
        name = match.group(1) or match.group(2)
        if name:
            line = text[: match.start()].count("\n") + 1
            kind = "class" if match.group(2) else "function"
            symbols.append(CodeSymbol(name, kind, rel, line, language))
    imports = re.findall(r"""from\s+['"]([^'"]+)['"]""", text)
    return ParsedFile(file_path=rel, language=language, symbols=symbols, imports=imports)


def parse_file(root: Path, file_path: Path) -> ParsedFile:
    rel = str(file_path.relative_to(root)).replace("\\", "/")
    language = _language_for(file_path)
    if language == "python":
        return _parse_python(file_path, rel)
    if language == "java":
        return _parse_java(file_path, rel)
    if language in {"javascript", "typescript"}:
        return _parse_javascript(file_path, rel, language)
    return ParsedFile(file_path=rel, language=language, symbols=[], imports=[])


def parse_repository(root: Path) -> list[ParsedFile]:
    return [parse_file(root, fp) for fp in iter_source_files(root)]
