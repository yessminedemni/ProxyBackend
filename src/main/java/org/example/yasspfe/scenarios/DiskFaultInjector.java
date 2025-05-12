package org.example.yasspfe.scenarios;

public class DiskFaultInjector {
    private boolean enabled = false;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean shouldBlockQuery(String query) {
        if (!enabled || query == null) return false;

        String upper = query.trim().toUpperCase();
        return upper.startsWith("INSERT") || upper.startsWith("UPDATE") ||
                upper.startsWith("DELETE") || upper.startsWith("REPLACE");
    }

    public byte[] fakeDiskFullErrorPacket() {
        return new byte[]{
                0x07, 0x00, 0x00, 0x01,    // Header
                (byte) 0xff,              // Error packet
                (byte) 0xfd, 0x03,        // Error code 1021 (Disk full)
                '#', 'H', 'Y', '0', '0', '0', // SQL State
                // Message: Disk full ...
                'D', 'i', 's', 'k', ' ', 'f', 'u', 'l', 'l', ' ', '-', ' ', 'n', 'o', ' ', 's', 'p', 'a', 'c', 'e'
        };
    }
}
