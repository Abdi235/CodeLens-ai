package com.example.vulnerable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Intentionally vulnerable sample used by SecureAI demos and tests.
 * DO NOT use these patterns in production code.
 */
public class UserController {

    private final Connection connection;

    public UserController(Connection connection) {
        this.connection = connection;
    }

    // SQL Injection via string concatenation
    public ResultSet findUser(String userId) throws Exception {
        String query = "SELECT * FROM users WHERE id=" + userId;
        Statement statement = connection.createStatement();
        return statement.executeQuery(query);
    }

    // Hardcoded credentials
    public String getDbPassword() {
        String password = "SuperSecretPassword123!";
        return password;
    }

    // Weak cryptography
    public byte[] weakHash(String input) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        return md.digest(input.getBytes());
    }

    // Dangerous command execution
    public void runCommand(String cmd) throws Exception {
        Runtime.getRuntime().exec(cmd);
    }

    // Path traversal risk
    public java.io.File openUserFile(String name) {
        return new java.io.File("/var/data/" + name);
    }
}
