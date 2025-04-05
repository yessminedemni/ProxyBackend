package org.example.yasspfe.scenarios;


public class LatencyInjector {

    public  static void injectLatencyIfNeeded(MySQLProxy.ConnectionState state) throws InterruptedException {
        if (state.isAddDelay()) {
            String queryType = state.getCurrentQueryType();
            long latency = getLatencyForType(queryType);
            System.out.println("Injecting latency of " + latency + " ms for query type: " + queryType);
            Thread.sleep(latency);
            state.setAddDelay(false);
        }
    }

    public static long getLatencyForType(String type) {
        if (type == null) return 0;
        return switch (type) {
            case "DQL" -> 600;   // e.g., SELECT, SHOW
            case "DML" -> 60000; // e.g., INSERT, UPDATE, DELETE
            case "DDL" -> 500;   // e.g., CREATE, DROP
            case "TCL" -> 150;   // e.g., COMMIT, ROLLBACK
            case "DCL" -> 250;   // e.g., GRANT, REVOKE
            default -> 0;        // OTHER
        };
    }

    public static String getQueryType(String query) {
        String upperQuery = query.toUpperCase().trim();
        if (upperQuery.startsWith("SELECT") || upperQuery.startsWith("SHOW") || upperQuery.startsWith("DESCRIBE")) {
            return "DQL"; // Data Query Language
        } else if (upperQuery.startsWith("INSERT") || upperQuery.startsWith("UPDATE") || upperQuery.startsWith("DELETE")) {
            return "DML"; // Data Manipulation Language
        } else if (upperQuery.startsWith("CREATE") || upperQuery.startsWith("ALTER") || upperQuery.startsWith("DROP") || upperQuery.startsWith("TRUNCATE")) {
            return "DDL"; // Data Definition Language
        } else if (upperQuery.startsWith("COMMIT") || upperQuery.startsWith("ROLLBACK") || upperQuery.startsWith("SAVEPOINT")) {
            return "TCL"; // Transaction Control Language
        } else if (upperQuery.startsWith("GRANT") || upperQuery.startsWith("REVOKE") || upperQuery.startsWith("SET PASSWORD")) {
            return "DCL"; // Data Control Language
        }
        return "OTHER";
    }
}