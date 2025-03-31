package org.example.yasspfe.scenarios;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
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
    private static final String URL = "jdbc:mysql://localhost:3306/bloggerplatform";
    private static final String USER = "root";
    private static final String PASSWORD = "root";
    private static final int THREAD_MULTIPLIER = 4; // Increased from 2
    private static final int REPORT_INTERVAL = 1000; // Report every 1000 queries
    private static final Random RANDOM = new Random();

    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private Map<String, List<String>> tableColumns = new HashMap<>();


    // Start the stress test if not already running
    public synchronized boolean startStressTest() {
        if (running.compareAndSet(false, true)) {
            System.out.println("🔴 Starting Database Stress Test...");
            try {
                initializeDataSource();
                List<String> tables = getTables();

                if (tables.isEmpty()) {
                    System.err.println("❌ No tables found in the database!");
                    running.set(false);
                    return false;
                }

                // Get column information for each table
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

                    // Add some write operations if table has more than one column
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
                running.set(false);
                return false;
            }
        } else {
            System.out.println("⚠ Stress test is already running!");
            return true;
        }
    }

    // Stop the stress test if it's running
    public synchronized boolean stopStressTest() {
        System.out.println("🛑 stopStressTest() CALLED!");

        if (!running.get()) {
            System.out.println("ℹ️ Stress test is not running, nothing to stop");
            return true;
        }

        // Set running to false first to signal all threads to stop
        running.set(false);
        System.out.println("🛑 Stopping stress test... running flag set to: " + running.get());

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

        // Close the connection pool
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                System.out.println("✅ Connection pool closed");
            } catch (Exception e) {
                System.err.println("⚠ Error closing connection pool: " + e.getMessage());
                success = false;
            }
        }

        // Reset the executor to null to prevent reuse
        executor = null;
        dataSource = null;
        tableColumns.clear();

        System.out.println("✅ Stress test shutdown completed. Total queries executed: " + totalQueries.get());
        return success;
    }

    private void initializeDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMinimumIdle(20);  // Increased
        config.setIdleTimeout(30000);  // Increased to 30 seconds
        config.setMaxLifetime(1800000);  // Increased to 30 minutes
        config.setConnectionTimeout(10000);  // Increased to 10 seconds
        config.setMaximumPoolSize(250);  // Increased pool size
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
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

                double qps = (currentCount - lastCount) / ((currentTime - lastTime) / 1000.0);

                System.out.println("📊 PERFORMANCE: " + String.format("%.2f", qps) +
                        " queries/sec | Total: " + currentCount);

                lastCount = currentCount;
                lastTime = currentTime;

                // Check connection pool statistics
                if (dataSource != null) {
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
        Connection connection = null;
        List<String> columns = tableColumns.get(table);

        try {
            connection = dataSource.getConnection();
            String threadName = Thread.currentThread().getName();
            System.out.println("🟢 Running queries on table: " + table + " in thread: " + threadName);

            // Main query loop
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Choose a random query type from multiple options
                    int queryType = RANDOM.nextInt(4);
                    switch (queryType) {
                        case 0: // Simple count
                            try (Statement stmt = connection.createStatement()) {
                                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                                rs.next(); // Actually read the result
                                rs.close();
                            }
                            break;

                        case 1: // Select with limit - forces more work and less caching
                            try (Statement stmt = connection.createStatement()) {
                                // Add LIMIT with random offset to prevent caching
                                int offset = RANDOM.nextInt(100);
                                ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT " + offset + ", 20");
                                while (rs.next()) { /* Read all results */ }
                                rs.close();
                            }
                            break;

                        case 2: // Select with random sorting if we have columns
                            if (!columns.isEmpty()) {
                                try (Statement stmt = connection.createStatement()) {
                                    String column = columns.get(RANDOM.nextInt(columns.size()));
                                    String order = RANDOM.nextBoolean() ? "ASC" : "DESC";
                                    ResultSet rs = stmt.executeQuery("SELECT * FROM " + table +
                                            " ORDER BY " + column + " " + order +
                                            " LIMIT 50");
                                    while (rs.next()) { /* Read all results */ }
                                    rs.close();
                                }
                            }
                            break;

                        case 3: // Conditional select if we have columns
                            if (columns.size() > 1) {
                                try (Statement stmt = connection.createStatement()) {
                                    String column = columns.get(RANDOM.nextInt(columns.size()));
                                    ResultSet rs = stmt.executeQuery("SELECT * FROM " + table +
                                            " WHERE " + column + " IS NOT NULL " +
                                            " LIMIT 30");
                                    while (rs.next()) { /* Read all results */ }
                                    rs.close();
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

                // Double check in case we missed an interrupt
                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    System.out.println("🔵 Exit condition detected for " + threadName + " on table: " + table);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("⚠ Error in query thread for table " + table + ": " + e.getMessage());
        } finally {
            // Close resources properly
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                System.err.println("⚠ Error closing resources: " + e.getMessage());
            }
            System.out.println("🔴 Query thread TERMINATED for table: " + table);
        }
    }

    private void runComplexQueries(String table) {
        Connection connection = null;
        List<String> columns = tableColumns.get(table);

        try {
            connection = dataSource.getConnection();
            String threadName = Thread.currentThread().getName();
            System.out.println("🟣 Running complex queries on table: " + table + " in thread: " + threadName);

            // Main query loop
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!columns.isEmpty()) {
                        try (Statement stmt = connection.createStatement()) {
                            // These queries are more expensive and force index usage
                            int queryType = RANDOM.nextInt(3);

                            switch (queryType) {
                                case 0:
                                    // Group by query
                                    String groupColumn = columns.get(RANDOM.nextInt(columns.size()));
                                    ResultSet rs = stmt.executeQuery(
                                            "SELECT " + groupColumn + ", COUNT(*) " +
                                                    "FROM " + table + " " +
                                                    "GROUP BY " + groupColumn + " " +
                                                    "LIMIT 50"
                                    );
                                    while (rs.next()) { /* Read all results */ }
                                    rs.close();
                                    break;

                                case 1:
                                    // Full table scan with complex WHERE clause
                                    if (columns.size() > 1) {
                                        String col1 = columns.get(RANDOM.nextInt(columns.size()));
                                        String col2 = columns.get(RANDOM.nextInt(columns.size()));
                                        ResultSet rs2 = stmt.executeQuery(
                                                "SELECT * FROM " + table + " " +
                                                        "WHERE " + col1 + " IS NOT NULL OR " + col2 + " IS NOT NULL " +
                                                        "LIMIT 100"
                                        );
                                        while (rs2.next()) { /* Read all results */ }
                                        rs2.close();
                                    }
                                    break;

                                case 2:
                                    // LIKE query (expensive)
                                    if (columns.size() > 0) {
                                        String col = columns.get(RANDOM.nextInt(columns.size()));
                                        ResultSet rs3 = stmt.executeQuery(
                                                "SELECT * FROM " + table + " " +
                                                        "WHERE " + col + " LIKE '%a%' " +
                                                        "LIMIT 100"
                                        );
                                        while (rs3.next()) { /* Read all results */ }
                                        rs3.close();
                                    }
                                    break;
                            }

                            totalQueries.incrementAndGet();
                        }
                    }

                    // Sleep a bit longer between complex queries
                    Thread.sleep(100);

                } catch (SQLException e) {
                    if (running.get()) {
                        System.err.println("⚠ SQL Error on complex query for table " + table + ": " + e.getMessage());
                        Thread.sleep(200); // Pause before retry
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
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                System.err.println("⚠ Error closing resources: " + e.getMessage());
            }
            System.out.println("🔴 Complex query thread TERMINATED for table: " + table);
        }
    }

    private void runWriteOperations(String table) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            String threadName = Thread.currentThread().getName();
            System.out.println("📝 Running write operations on table: " + table + " in thread: " + threadName);

            // We'll perform read operations frequently, but occasionally do writes
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Most of the time, do selects to avoid messing up the database
                    // but occasionally do a transaction with writes
                    if (RANDOM.nextInt(20) == 0) { // 5% chance of write operation
                        connection.setAutoCommit(false);
                        try (Statement stmt = connection.createStatement()) {
                            // First get count to see if we have rows
                            ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                            countRs.next();
                            int count = countRs.getInt(1);
                            countRs.close();

                            if (count > 0) {
                                // Try a simple update - limit to a single row to avoid too much damage
                                stmt.execute("BEGIN");

                                List<String> columns = tableColumns.get(table);
                                if (columns.size() > 1) {
                                    // Find a good column to update
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
                                        // Execute an update that's unlikely to break things
                                        int updated = stmt.executeUpdate(
                                                "UPDATE " + table + " SET " + updateColumn + " = " + updateColumn +
                                                        " LIMIT 1"
                                        );

                                        // Update successful - now perform some selects in the same transaction
                                        ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 10");
                                        while (rs.next()) { /* read results */ }
                                        rs.close();

                                        connection.commit();

                                        // Only count as one operation since it's a transaction
                                        totalQueries.incrementAndGet();
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            // Rollback on error
                            try {
                                connection.rollback();
                            } catch (SQLException e2) {
                                System.err.println("⚠ Rollback error: " + e2.getMessage());
                            }
                            System.err.println("⚠ Write operation error on table " + table + ": " + e.getMessage());
                        } finally {
                            // Reset auto commit
                            connection.setAutoCommit(true);
                        }
                    } else {
                        // Just do a simple select query
                        try (Statement stmt = connection.createStatement()) {
                            ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " LIMIT 10");
                            while (rs.next()) { /* read results */ }
                            rs.close();
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
        } finally {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                System.err.println("⚠ Error closing resources: " + e.getMessage());
            }
            System.out.println("🔴 Write thread TERMINATED for table: " + table);
        }
    }

    // Check if the stress test is running
    public boolean isRunning() {
        return running.get();
    }


    // Force stop method for emergency use
    public void forceStop() {
        System.out.println("💥 FORCE STOP REQUESTED!");

        // Set flag to false
        running.set(false);

        // Force shutdown executor
        if (executor != null) {
            List<Runnable> tasks = executor.shutdownNow();
            System.out.println("💥 Forcibly shutdown executor with " + tasks.size() + " pending tasks");
            executor = null;
        }

        // Force close datasource
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            System.out.println("💥 Force closed data source");
        }

        System.out.println("💥 Force stop completed");
    }

    // For standalone testing
    public static void main(String[] args) {
        DatabaseStressTester tester = new DatabaseStressTester();
        tester.startStressTest();

        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(tester::stopStressTest));
    }

    public Object getActiveThreadCount() {
        return totalQueries.get();
    }


}