package com.secureai;

import com.secureai.search.Bm25SearchEngine;
import com.secureai.model.CodeIndexEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Bm25SearchEngineTest {

  private final Bm25SearchEngine engine = new Bm25SearchEngine();

  @Test
  void ranksAuthenticationChunkHigher() {
    List<CodeIndexEntry> entries = List.of(
        entry("auth.py", "def authenticate_user(request): pass"),
        entry("db.py", "SELECT * FROM users")
    );
    var hits = engine.search(entries, "authenticate_user jwt", 2);
    assertFalse(hits.isEmpty());
    assertTrue(hits.get(0).entry().getFilePath().contains("auth"));
  }

  private static CodeIndexEntry entry(String path, String text) {
    CodeIndexEntry e = new CodeIndexEntry();
    e.setFilePath(path);
    e.setStartLine(1);
    e.setEndLine(5);
    e.setLanguage("python");
    e.setChunkText(text);
    e.setTokenCount(text.split("\\s+").length);
    return e;
  }
}
