package com.secureai;

import com.secureai.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceServiceTest {

    @TempDir
    Path temp;

    @Test
    void copiesLocalDirectory() throws Exception {
        Path source = temp.resolve("src");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Bad.java"), "String password = \"secret1234\";\n");

        WorkspaceService workspaceService = new WorkspaceService(temp.resolve("ws").toString());
        Path dest = workspaceService.prepareProjectWorkspace(1L, 2L).resolve("repo");
        workspaceService.resolveLocalOrSample(source.toString(), dest);

        assertTrue(Files.exists(dest.resolve("Bad.java")));
    }
}
