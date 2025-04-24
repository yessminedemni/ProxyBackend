package org.example.yasspfe.scenarios;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.example.yasspfe.entities.DatabaseConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DatabaseStressTester {
    private String jdbcUrl;
    private String username;
    private String password;

    private static final int THREAD_MULTIPLIER = 4;
    private static final int REPORT_INTERVAL = 1000;
    private static final Random RANDOM = new Random();

    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile HikariDataSource dataSource;
    private volatile ExecutorService executor;
    private final Map<String, List<String>> tableColumns = new HashMap<>();

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Added overloaded method for main() usage
    public boolean startStressTest() {
        // Use configured values if they're set
        if (jdbcUrl != null && username != null && password != null) {
            DatabaseConfig config = new DatabaseConfig(jdbcUrl, username, password);
            return startStressTest(config);
        } else {
            System.err.println("❌ Cannot start stress test: database connection details not configured");
            return false;
        }
    }

    public boolean startStressTest(DatabaseConfig config) {
        System.out.println("🔍 Proxy triggered startStressTest with config: " + config);

        if (running.compareAndSet(false, true)) {
            System.out.println("🔴 Starting Database Stress Test with dynamic config...");

            try {
                initializeDataSource(config);
                List<String> tables = getTables();

                if (tables.isEmpty()) {
                    System.err.println("❌ No tables found in the database!");
                    shutdownResources();
                    return false;
                }

                for (String table : tables) {
                    tableColumns.put(table, getTableColumns(table));
                }

                System.out.println("✅ Tables detected: " + tables);
                int threadCount = THREAD_MULTIPLIER * Runtime.getRuntime().availableProcessors();
                executor = Executors.newFixedThreadPool(threadCount);

                // Start monitoring thread
                executor.execute(this::monitorPerformance);

                // Start query threads - multiple threads per table
                for (String table : tables) {
                    // More threads per table
                    for (int i = 0; i < 6; i++) {
                        executor.execute(() -> runQueriesIndefinitely(table));
                    }

                    if (tableColumns.get(table).size() > 1) {
                        executor.execute(() -> runWriteOperations(table));
                    }

                    // Add index operations for stress
                    executor.execute(() -> runComplexQueries(table));
                }

                System.out.println("⏳ Stress test running with " + threadCount + " threads");
                return true;
            } catch (Exception e) {
                System.err.println("❌ Error starting stress test: " + e.getMessage());
                e.printStackTrace();
                shutdownResources();
                return false;
            }
        } else {
            System.out.println("⚠ Stress test is already running!");
            return true;
        }
    }

    public synchronized boolean stopStressTest() {
        System.out.println("🛑 stopStressTest() CALLED!");

        if (!running.get()) {
            System.out.println("ℹ️ Stress test is not running, nothing to stop");
            return true;
        }

        // Set running to false first to signal all threads to stop
        running.set(false);
        System.out.println("🛑 Stopping stress test... running flag set to: " + running.get());

        return shutdownResources();
    }

    private boolean shutdownResources() {
        boolean success = true;

        // Shutdown the executor service
        if (executor != null && !executor.isShutdown()) {
            try {
                // Try graceful shutdown first
                System.out.println("🛑 Initiating executor shutdown");
                executor.shutdown();

                // Wait for termination
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("⚠️ Threads not stopping gracefully, forcing shutdown...");
                    executor.shutdownNow();

                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("❌ CRITICAL: Failed to terminate all threads!");
                        success = false;
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("⚠ Shutdown interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                success = false;
            }
        }

        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                System.out.println("✅ Connection pool closed");
            } catch (Exception e) {
                System.err.println("⚠ Error closing connection pool: " + e.getMessage());
                success = false;
            }
        }

        executor = null;
        dataSource = null;
        tableColumns.clear();

        // Make sure running is set to false
        running.set(false);

        System.out.println("✅ Stress test shutdown completed. Total queries executed: " + totalQueries.get());
        return success;
    }

    private void initializeDataSource(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Save for reference
        this.jdbcUrl = config.getJdbcUrl();
        this.username = config.getUsername();
        this.password = config.getPassword();


        hikariConfig.setMinimumIdle(20);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setMaximumPoolSize(250);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(hikariConfig);
    }

    private List<String> getTables() {
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW TABLES")) {

            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error retrieving tables: " + e.getMessage());
        }
        return tables;
    }

    private List<String> getTableColumns(String table) {
        List<String> columns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("DESCRIBE " + table)) {

            while (rs.next()) {
                String columnName = rs.getString("Field");
                columns.add(columnName);
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error retrieving columns for table " + table + ": " + e.getMessage());
        }
        return columns;
    }

    private void monitorPerformance() {
        String threadName = Thread.currentThread().getName();
        System.out.println("🔍 Performance monitor started on thread: " + threadName);

        int lastCount = 0;
        long lastTime = System.currentTimeMillis();

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(5000); // Check every 5 seconds

                int currentCount = totalQueries.get();
                long currentTime = System.currentTimeMillis();

                double timeElapsed = (currentTime - lastTime) / 1000.0;
                if (timeElapsed <= 0) timeElapsed = 0.001; // Ensure we don't divide by zero

                double qps = (currentCount - lastCount) / timeElapsed;

                System.out.println("📊 PERFORMANCE: " + String.format("%.2f", qps) +
                        " queries/sec | Total: " + currentCount);

                lastCount = currentCount;
                lastTime = currentTime;

                if (dataSource != null && !dataSource.isClosed()) {
                    System.out.println("🔌 CONNECTIONS: Active=" + dataSource.getHikariPoolMXBean().getActiveConnections() +
                            " | Idle=" + dataSource.getHikariPoolMXBean().getIdleConnections() +
                            " | Waiting=" + dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
                }
            }
        } catch (InterruptedException e) {
            System.out.println("🔵 Monitor thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("⚠ Error in monitor thread: " + e.getMessage());
        }

        System.out.println("🔍 Performance monitor terminated");
    }

    private void runQueriesIndefinitely(String table) {
        String threadName = Thread.currentThread().getName();
        System.out.println("🟢 Running queries on table: " + table + " in thread: " + threadName);

        List<String> columns = tableColumns.get(table);
        if (columns == null || columns.isEmpty()) {
            System.err.println("⚠ No columns found for table " + table + ", skipping query thread");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            // Main query loop
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Choose a random query type from multiple options
                    int queryType = RANDOM.nextInt(4);
                    switch (queryType) {
                        case 0: // Simple count
                            try (Statement stmt = connection.createStatement();
                                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                                rs.next(); // Actually read the result
                            }
                            break;

                        case 1: // Select with limit - forces more work and less caching
                            try (Statement stmt = connection.createStatement();
                                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + table +
                                         " LIMIT " + RANDOM.nextInt(100) + ", 20")) {
                                while (rs.next()) { /* Read all results */ }
                            }
                            break;

                        case 2: // Select with random sorting if we have columns
                            if (!columns.isEmpty()) {
                                String column = columns.get(RANDOM.nextInt(columns.size()));
                                String order = RANDOM.nextBoolean() ? "ASC" : "DESC";
                                try (Statement stmt = connection.createStatement();
                                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + table +
                                             " ORDER BY " + column + " " + order +
                                             " LIMIT 50")) {
                                    while (rs.next()) { /* Read all results */ }
                                }
                            }
                            break;

                        case 3: // Conditional select if we have columns
                            if (columns.size() > 1) {
                                String column = columns.get(RANDOM.nextInt(columns.size()));
                                try (Statement stmt = connection.createStatement();
                                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + table +
                                             " WHERE " + column + " IS NOT NULL " +
                                             " LIMIT 30")) {
                                    while (rs.next()) { /* Read all results */ }
                                }
                            }
                            break;
                    }

                    int count = totalQueries.incrementAndGet();
                    if (count % REPORT_INTERVAL == 0) {
                        System.out.println("✅ Total queries executed: " + count);
                    }

                    // No sleep here - we want maximum pressure
                    // Only add minimal sleep when CPU is at 100% for too long
                    if (RANDOM.nextInt(100) > 95) {  // 5% chance of a tiny sleep
                        Thread.sleep(1);
                    }

                } catch (SQLException e) {
                    if (running.get()) {
                        System.err.println("⚠ SQL Error on table " + table + ": " + e.getMessage());
                        Thread.sleep(100); // Pause before retry on error
                    }
                } catch (InterruptedException e) {
                    System.out.println("🔵 Thread " + threadName + " interrupted for table: " + table);
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    System.out.println("🔵 Exit condition detected for " + threadName + " on table: " + table);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Error in query thread for table " + table + ": " + e.getMessage());
        }

        System.out.println("🔴 Query thread TERMINATED for table: " + table);
    }

    private void runComplexQueries(String table) {
        String threadName = Thread.currentThread().getName();
        System.out.println("🟣 Running complex queries on table: " + table + " in thread: " + threadName);

        List<String> columns = tableColumns.get(table);
        if (columns == null || columns.isEmpty()) {
            System.err.println("⚠ No columns found for table " + table + ", skipping complex query thread");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            // Main query loop
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    int queryType = RANDOM.nextInt(3);

                    try (Statement stmt = connection.createStatement()) {
                        switch (queryType) {
                            case 0:
                                // Group by query
                                String groupColumn = columns.get(RANDOM.nextInt(columns.size()));
                                try (ResultSet rs = stmt.executeQuery(
                                        "SELECT " + groupColumn + ", COUNT(*) " +
                                                "FROM " + table + " " +
                                                "GROUP BY " + groupColumn + " " +
                                                "LIMIT 50")) {
                                    while (rs.next()) { /* Read all results */ }
                                }
                                break;

                            case 1:
                                // Full table scan with complex WHERE clause
                                if (columns.size() > 1) {
                                    String col1 = columns.get(RANDOM.nextInt(columns.size()));
                                    String col2 = columns.get(RANDOM.nextInt(columns.size()));
                                    try (ResultSet rs = stmt.executeQuery(
                                            "SELECT * FROM " + table + " " +
                                                    "WHERE " + col1 + " IS NOT NULL OR " + col2 + " IS NOT NULL " +
                                                    "LIMIT 100")) {
                                        while (rs.next()) { /* Read all results */ }
                                    }
                                }
                                break;

                            case 2:
                                // LIKE query (expensive)
                                String col = columns.get(RANDOM.nextInt(columns.size()));
                                try (ResultSet rs = stmt.executeQuery(
                                        "SELECT * FROM " + table + " " +
                                                "WHERE " + col + " LIKE '%a%' " +
                                                "LIMIT 100")) {
                                    while (rs.next()) { /* Read all results */ }
                                }
                                break;
                        }

                        totalQueries.incrementAndGet();
                    }

                    // Sleep a bit longer between complex queries
                    Thread.sleep(100);

                } catch (SQLException e) {
                    if (running.get()) {
                        System.err.println("⚠ SQL Error on complex query for table " + table + ": " + e.getMessage());
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    System.out.println("🔵 Complex query thread " + threadName + " interrupted for table: " + table);
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Error in complex query thread for table " + table + ": " + e.getMessage());
        }

        System.out.println("🔴 Complex query thread TERMINATED for table: " + table);
    }

    private void runWriteOperations(String table) {
        String threadName = Thread.currentThread().getName();
        System.out.println("📝 Running write operations on table: " + table + " in thread: " + threadName);

        List<String> columns = tableColumns.get(table);
        if (columns == null || columns.size() <= 1) {
            System.err.println("⚠ Not enough columns for write operations on table " + table + ", skipping write thread");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (RANDOM.nextInt(20) == 0) { // 5% chance of write operation
                        connection.setAutoCommit(false);
                        try (Statement stmt = connection.createStatement()) {
                            // First get count to see if we have rows
                            try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                                countRs.next();
                                int count = countRs.getInt(1);

                                if (count > 0) {
                                    stmt.execute("BEGIN");

                                    String updateColumn = null;
                                    for (String col : columns) {
                                        if (!col.equalsIgnoreCase("id") &&
                                                !col.toLowerCase().contains("key") &&
                                                !col.toLowerCase().contains("uuid")) {
                                            updateColumn = col;
                                            break;
                                        }
                                    }

                                    if (updateColumn != null) {
                                        int updated = stmt.executeUpdate(
                                                "UPDATE " + table + " SET " + updateColumn + " = " + updateColumn +
                                                        " LIMIT 1"
                                        );

                                        // Update successful - now perform some selects in the same transaction
                                        try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 10")) {
                                            while (rs.next()) { /* read results */ }
                                        }

                                        connection.commit();

                                        // Only count as one operation since it's a transaction
                                        totalQueries.incrementAndGet();
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            try {
                                connection.rollback();
                            } catch (SQLException e2) {
                                System.err.println("⚠ Rollback error: " + e2.getMessage());
                            }
                            System.err.println("⚠ Write operation error on table " + table + ": " + e.getMessage());
                        } finally {
                            // Reset auto commit
                            try {
                                connection.setAutoCommit(true);
                            } catch (SQLException e) {
                                System.err.println("⚠ Error resetting autocommit: " + e.getMessage());
                            }
                        }
                    } else {
                        // Just do a simple select query
                        try (Statement stmt = connection.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 10")) {
                            while (rs.next()) { /* read results */ }
                            totalQueries.incrementAndGet();
                        }
                    }

                    // Sleep a bit between write operations
                    Thread.sleep(500);

                } catch (SQLException e) {
                    if (running.get()) {
                        System.err.println("⚠ SQL Error in write thread for table " + table + ": " + e.getMessage());
                        Thread.sleep(1000); // Longer pause before retry after write error
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Error in write thread for table " + table + ": " + e.getMessage());
        }

        System.out.println("🔴 Write thread TERMINATED for table: " + table);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void forceStop() {
        System.out.println("💥 FORCE STOP REQUESTED!");

        running.set(false);

        if (executor != null) {
            List<Runnable> tasks = executor.shutdownNow();
            System.out.println("💥 Forcibly shutdown executor with " + tasks.size() + " pending tasks");
            executor = null;
        }

        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            System.out.println("💥 Force closed data source");
        }

        System.out.println("💥 Force stop completed");
    }

    public int getTotalQueries() {
        return totalQueries.get();
    }

    // For standalone testing
    public static void main(String[] args) {
        // Create a tester with configured connection details
        DatabaseStressTester tester = new DatabaseStressTester();
        tester.setJdbcUrl("jdbc:mysql://localhost:3306/testdb");
        tester.setUsername("root");
        tester.setPassword("password");

        // Start the test using the no-arg method that will use the configured values
        boolean success = tester.startStressTest();

        if (!success) {
            System.err.println("Failed to start database stress test");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(tester::stopStressTest));
    }
}