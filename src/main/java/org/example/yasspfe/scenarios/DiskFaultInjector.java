package org.example.yasspfe.scenarios;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests database resilience to disk-related failures while ensuring
 * all queries reach the target database. Simulates disk errors AFTER
 * database execution to meet "affect target DB" requirement.
 */
public class DiskFaultInjector {
    private boolean enabled = false;
    private final AtomicLong writesAffected = new AtomicLong(0);
    private final AtomicLong totalWrites = new AtomicLong(0);

    // Error codes for different disk scenarios
    private static final int DISK_FULL_ERROR = 1021;
    private static final int IO_ERROR = 1030;
    private static final int TEMP_FILE_ERROR = 1114;

    private int currentErrorCode = DISK_FULL_ERROR;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            writesAffected.set(0);
            totalWrites.set(0);
        }
    }

    public void setErrorScenario(String scenario) {
        switch (scenario.toLowerCase()) {
            case "disk_full" -> currentErrorCode = DISK_FULL_ERROR;
            case "io_error" -> currentErrorCode = IO_ERROR;
            case "temp_file" -> currentErrorCode = TEMP_FILE_ERROR;
            default -> currentErrorCode = DISK_FULL_ERROR;
        }
    }

    /**
     * Determines if we should override a SUCCESSFUL database response
     * with a fake disk error. Database always processes the query first.
     */
    public boolean shouldInjectError(String query, boolean dbExecutionSuccess) {
        if (!enabled || !dbExecutionSuccess || query == null) return false;

        String upper = query.trim().toUpperCase();
        boolean isWriteOperation = upper.startsWith("INSERT") || upper.startsWith("UPDATE")
                || upper.startsWith("DELETE") || upper.startsWith("REPLACE")
                || upper.startsWith("CREATE") || upper.startsWith("ALTER");

        if (isWriteOperation) {
            totalWrites.incrementAndGet();

            // Only inject errors for specific write types that reached the DB
            boolean shouldInject = (upper.startsWith("INSERT") && upper.length() > 200)
                    || (upper.startsWith("CREATE") && (upper.contains("TABLE") || upper.contains("INDEX")))
                    || (upper.contains("BLOB") || upper.contains("LONGTEXT"));

            if (shouldInject) {
                writesAffected.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    /**
     * Creates fake error packet AFTER database has processed the query
     */
    public byte[] fakeDiskErrorPacket() {
        String errorMessage = switch(currentErrorCode) {
            case DISK_FULL_ERROR -> "Disk full - no space left on device";
            case IO_ERROR -> "Got error 28 from storage engine - I/O error";
            case TEMP_FILE_ERROR -> "The table '%s' is full";
            default -> "Disk full - no space left on device";
        };

        // Convert error message to bytes
        byte[] messageBytes = errorMessage.getBytes();

        // Create MySQL protocol error packet
        byte[] packet = new byte[13 + messageBytes.length];

        // Packet header (length depends on message)
        packet[0] = (byte)((messageBytes.length + 9) & 0xff);
        packet[1] = (byte)(((messageBytes.length + 9) >> 8) & 0xff);
        packet[2] = (byte)(((messageBytes.length + 9) >> 16) & 0xff);

        packet[3] = 0x01;        // Packet number
        packet[4] = (byte) 0xff; // Error packet marker

        // Error code (2 bytes)
        packet[5] = (byte)(currentErrorCode & 0xff);
        packet[6] = (byte)((currentErrorCode >> 8) & 0xff);

        // SQL State marker and state code
        packet[7] = '#';
        packet[8] = 'H';
        packet[9] = 'Y';
        packet[10] = '0';
        packet[11] = '0';
        packet[12] = '0';

        System.arraycopy(messageBytes, 0, packet, 13, messageBytes.length);

        System.out.println("🔍 [DB Resilience Test] Simulating disk error after DB execution: " + errorMessage);
        return packet;
    }

    /**
     * Gets metrics showing database impact
     */
    public String getMetrics() {
        return String.format("Disk Fault Test Metrics - Total Writes: %d, Affected: %d, Impact Rate: %.2f%%",
                totalWrites.get(),
                writesAffected.get(),
                totalWrites.get() > 0 ?
                        ((double) writesAffected.get() / totalWrites.get() * 100) : 0);
    }
}