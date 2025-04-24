package org.example.yasspfe.appscenrios;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class should be implemented in the target application to handle CPU load requests.
 * It performs intensive calculations when triggered by the proxy.
 */
public class CPULoadHandler {
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static ExecutorService cpuLoadExecutor;
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    /**
     * Call this method when receiving a request with the CPU load parameter
     * or the X-CPU-Load header.
     */
    public static void handleCpuLoadRequest(int durationSeconds) {
        if (!isRunning.get()) {
            startCpuLoad(durationSeconds);
        }
    }

    public static void startCpuLoad(int durationSeconds) {
        if (isRunning.compareAndSet(false, true)) {
            System.out.println("[CPULoadHandler] Starting CPU load with " + NUM_THREADS + " threads for " + durationSeconds + " seconds");

            // Create a thread pool
            cpuLoadExecutor = Executors.newFixedThreadPool(NUM_THREADS);

            // Start CPU-intensive tasks
            for (int i = 0; i < NUM_THREADS; i++) {
                final int threadId = i;
                cpuLoadExecutor.submit(() -> runIntensiveOperations(threadId));
            }

            // Schedule shutdown after the specified duration
            new Thread(() -> {
                try {
                    Thread.sleep(durationSeconds * 1000);
                    stopCpuLoad();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    public static void stopCpuLoad() {
        if (isRunning.compareAndSet(true, false)) {
            System.out.println("[CPULoadHandler] Stopping CPU load");

            if (cpuLoadExecutor != null) {
                cpuLoadExecutor.shutdownNow();
                try {
                    if (!cpuLoadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("[CPULoadHandler] Executor did not terminate within 5 seconds");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cpuLoadExecutor = null;
            }
        }
    }

    private static void runIntensiveOperations(int threadId) {
        System.out.println("[CPULoadHandler] Thread " + threadId + " starting intensive operations");

        while (isRunning.get()) {
            // 1. Complex mathematical calculations
            double result = 0;
            for (int i = 0; i < 1000000; i++) {
                result += Math.sin(i) * Math.cos(i) * Math.tan(i) / Math.PI;
                result = Math.pow(result, 0.5);
            }

            // 2. Random number generation and sorting
            double[] array = new double[10000];
            for (int i = 0; i < array.length; i++) {
                array[i] = ThreadLocalRandom.current().nextDouble() * 1000;
            }
            bubbleSort(array);

            // 3. String operations
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append((char) ThreadLocalRandom.current().nextInt(65, 91));
                if (i % 100 == 0) {
                    sb.setLength(Math.min(sb.length(), 10));
                }
            }

            // 4. Recursive Fibonacci calculation
            int fibResult = fibonacci(25);

            // 5. Matrix operations
            double[][] matrix = new double[50][50];
            for (int i = 0; i < 50; i++) {
                for (int j = 0; j < 50; j++) {
                    matrix[i][j] = Math.random();
                }
            }
            multiplyMatrices(matrix, matrix);

            // Brief pause to prevent complete system lockup
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[CPULoadHandler] Thread " + threadId + " finished");
    }

    // Intentionally inefficient bubble sort
    private static void bubbleSort(double[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    double temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
    }

    // Inefficient recursive Fibonacci
    private static int fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    // Matrix multiplication
    private static double[][] multiplyMatrices(double[][] a, double[][] b) {
        int rows = a.length;
        int cols = b[0].length;
        double[][] result = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                for (int k = 0; k < a[0].length; k++) {
                    result[i][j] += a[i][k] * b[k][j];
                }
            }
        }

        return result;
    }
}