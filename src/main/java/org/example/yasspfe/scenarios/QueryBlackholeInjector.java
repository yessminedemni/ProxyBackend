package org.example.yasspfe.scenarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class QueryBlackholeInjector {
    private boolean enabled = false;
    private String lastQuery = null;

    private final String logInsertSQL = "INSERT INTO chaos_blackhole_log (query_text, query_type, timestamp) VALUES (?, ?, NOW())";

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void updateLastQuery(String query, Connection conn) {
        this.lastQuery = query;

        if (!enabled) return;

        try {
            String type = getQueryType(query);
            if (type.equals("SELECT") || type.equals("INSERT")) {
                logToDatabase(conn, query, type);
            }
        } catch (Exception e) {
            System.err.println("[QueryBlackhole] Failed to log query: " + e.getMessage());
        }
    }

    public boolean shouldDropResponse() {
        if (!enabled || lastQuery == null) return false;

        String upper = lastQuery.toUpperCase().trim();
        return upper.startsWith("SELECT") || upper.startsWith("INSERT");
    }

    private void logToDatabase(Connection conn, String query, String type) {
        try (PreparedStatement stmt = conn.prepareStatement(logInsertSQL)) {
            stmt.setString(1, query);
            stmt.setString(2, type);
            stmt.executeUpdate();
            System.out.println("[QueryBlackhole] Logged blackholed " + type + " query.");
        } catch (SQLException e) {
            System.err.println("[QueryBlackhole] Error logging to DB: " + e.getMessage());
        }
    }

    private String getQueryType(String query) {
        String upper = query.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        return "OTHER";
    }
}
