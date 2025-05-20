package org.example.yasspfe.appscenrios;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulates memory pressure by allocating large objects into heap.
 */
public class MemoryLoadScenario {

    private static final int ALLOCATION_MB = 50; // Amount of memory in MB to allocate per object
    private static final int MAX_OBJECTS = 100;  // Total allocations before pausing
    private static final int MONITOR_INTERVAL = 5000; // in milliseconds

    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private static final ExecutorService allocatorExecutor = Executors.newSingleThreadExecutor();
    private static final List<byte[]> memoryAllocations = new ArrayList<>();

    public static void startMemoryLoad() {
        if (active.compareAndSet(false, true)) {
            System.out.println("[MemoryLoadScenario] Starting memory load simulation");

            allocatorExecutor.submit(() -> {
                try {
                    while (active.get()) {
                        if (memoryAllocations.size() >= MAX_OBJECTS) {
                            System.out.println("[MemoryLoadScenario] Max allocations reached. Clearing memory...");
                            memoryAllocations.clear();
                            System.gc();
                            Thread.sleep(1000);
                        } else {
                            byte[] block = new byte[ALLOCATION_MB * 1024 * 1024];
                            memoryAllocations.add(block);
                            System.out.println("[MemoryLoadScenario] Allocated " + memoryAllocations.size() + " blocks of " + ALLOCATION_MB + "MB");
                            Thread.sleep(200); // Small delay to avoid too rapid allocation
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            monitor.scheduleAtFixedRate(() -> {
                Runtime rt = Runtime.getRuntime();
                long usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long maxMem = rt.maxMemory() / (1024 * 1024);
                System.out.println("[MemoryLoadScenario] Memory Usage: " + usedMem + "MB / " + maxMem + "MB");
            }, 0, MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
        }
    }

    public static void stopMemoryLoad() {
        if (active.compareAndSet(true, false)) {
            System.out.println("[MemoryLoadScenario] Stopping memory load scenario");
            memoryAllocations.clear();
            System.gc();
        }
    }

    public static boolean isActive() {
        return active.get();
    }
}
