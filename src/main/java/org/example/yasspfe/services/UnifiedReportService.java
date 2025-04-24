package org.example.yasspfe.services;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

@Service
public class UnifiedReportService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedReportService.class);
    private final Appscenrioservice appScenarioService;
    private final ScenarioService scenarioService;
    private final MetricsService jvmMetricsService;

    @Autowired
    public UnifiedReportService(
            Appscenrioservice appScenarioService,
            ScenarioService scenarioService,
            MetricsService jvmMetricsService) {
        this.appScenarioService = appScenarioService;
        this.scenarioService = scenarioService;
        this.jvmMetricsService = jvmMetricsService;
    }

    public ByteArrayInputStream generateReport(
            String dashboardType,
            String reportType,
            String timeRange,
            List<String> metrics) {

        logger.info("Generating {} report for dashboard: {}, time range: {}, metrics: {}",
                reportType, dashboardType, timeRange, metrics);

        if ("pdf".equalsIgnoreCase(reportType)) {
            return generatePdfReport(dashboardType, timeRange, metrics);
        } else if ("csv".equalsIgnoreCase(reportType)) {
            return generateCsvReport(dashboardType, timeRange, metrics);
        } else {
            throw new IllegalArgumentException("Unsupported report type: " + reportType);
        }
    }

    private ByteArrayInputStream generatePdfReport(
            String dashboardType,
            String timeRange,
            List<String> metrics) {

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Add header
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
            String title = getDashboardTitle(dashboardType);
            Paragraph header = new Paragraph(title + " Report", headerFont);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);
            document.add(Chunk.NEWLINE);

            // Add timestamp
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL, BaseColor.BLACK);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.BLACK);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Paragraph timestamp = new Paragraph("Generated on: " + sdf.format(new Date()), normalFont);
            timestamp.setAlignment(Element.ALIGN_RIGHT);
            document.add(timestamp);
            document.add(Chunk.NEWLINE);

            // Add time range info
            Paragraph timeRangeInfo = new Paragraph("Time Range: " + formatTimeRange(timeRange), normalFont);
            document.add(timeRangeInfo);
            document.add(Chunk.NEWLINE);

            // Add dashboard-specific content
            switch (dashboardType) {
                case "app-scenarios":
                    addAppScenariosContent(document, metrics, boldFont, normalFont);
                    break;
                case "scenarios":
                    addScenariosContent(document, metrics, boldFont, normalFont);
                    break;
                case "jvm-metrics":
                    addJvmMetricsContent(document, metrics, boldFont, normalFont);
                    break;
                default:
                    document.add(new Paragraph("Unknown dashboard type: " + dashboardType, normalFont));
            }



            document.close();

        } catch (Exception e) {
            logger.error("Error generating PDF report", e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage());
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    // Add the rest of your methods here...

    private void addAppScenariosContent(Document document, List<String> metrics, Font boldFont, Font normalFont) throws DocumentException {
        // Implementation details...
    }

    private void addScenariosContent(Document document, List<String> metrics, Font boldFont, Font normalFont) throws DocumentException {
        // Implementation details...
    }

    private void addJvmMetricsContent(Document document, List<String> metrics, Font boldFont, Font normalFont) throws DocumentException {
        // Implementation details...
    }

    private ByteArrayInputStream generateCsvReport(String dashboardType, String timeRange, List<String> metrics) {
        // Implementation details...
        return new ByteArrayInputStream("CSV Report".getBytes());
    }

    private PdfPCell createHeaderCell(String text) {
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, BaseColor.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont));
        cell.setBackgroundColor(BaseColor.DARK_GRAY);
        cell.setPadding(5);
        return cell;
    }

    private void addMetricSection(Document document, String title, String description, Font titleFont, Font textFont) throws DocumentException {
        document.add(new Paragraph(title, titleFont));
        document.add(new Paragraph(description, textFont));
        document.add(Chunk.NEWLINE);
    }

    private String formatTimeRange(String timeRange) {
        switch (timeRange) {
            case "1h":
                return "Last 1 Hour";
            case "6h":
                return "Last 6 Hours";
            case "12h":
                return "Last 12 Hours";
            case "24h":
                return "Last 24 Hours";
            case "7d":
                return "Last 7 Days";
            case "30d":
                return "Last 30 Days";
            default:
                return timeRange;
        }
    }

    private String getDashboardTitle(String dashboardType) {
        switch (dashboardType) {
            case "app-scenarios":
                return "App Scenarios Dashboard";
            case "scenarios":
                return "Scenarios Dashboard";
            case "jvm-metrics":
                return "JVM Metrics Dashboard";
            default:
                return "Dashboard";
        }
    }

    public List<Map<String, String>> getAvailableReportTypes() {
        List<Map<String, String>> types = new ArrayList<>();

        Map<String, String> pdf = new HashMap<>();
        pdf.put("id", "pdf");
        pdf.put("name", "PDF Document");
        pdf.put("icon", "fa-file-pdf");
        types.add(pdf);

        Map<String, String> csv = new HashMap<>();
        csv.put("id", "csv");
        csv.put("name", "CSV Spreadsheet");
        csv.put("icon", "fa-file-csv");
        types.add(csv);

        return types;
    }

    public List<Map<String, String>> getAvailableTimeRanges() {
        List<Map<String, String>> ranges = new ArrayList<>();

        addTimeRange(ranges, "1h", "Last 1 Hour");
        addTimeRange(ranges, "6h", "Last 6 Hours");
        addTimeRange(ranges, "12h", "Last 12 Hours");
        addTimeRange(ranges, "24h", "Last 24 Hours");
        addTimeRange(ranges, "7d", "Last 7 Days");
        addTimeRange(ranges, "30d", "Last 30 Days");

        return ranges;
    }

    private void addTimeRange(List<Map<String, String>> ranges, String id, String name) {
        Map<String, String> range = new HashMap<>();
        range.put("id", id);
        range.put("name", name);
        ranges.add(range);
    }

    public List<Map<String, String>> getMetricsForDashboard(String dashboardType) {
        List<Map<String, String>> metrics = new ArrayList<>();

        switch (dashboardType) {
            case "app-scenarios":
                addMetric(metrics, "scenarios-status", "Scenarios Status", "List of active and inactive app scenarios", dashboardType);
                addMetric(metrics, "cpu-load", "CPU Load", "Processor utilization percentage", dashboardType);
                addMetric(metrics, "traffic-load", "Traffic Load", "Number of requests per second", dashboardType);
                addMetric(metrics, "response-time", "Response Time", "Average response time in milliseconds", dashboardType);
                break;

            case "scenarios":
                addMetric(metrics, "scenarios-status", "Scenarios Status", "List of active and inactive network scenarios", dashboardType);
                addMetric(metrics, "packet-loss", "Packet Loss", "Percentage of network packets lost", dashboardType);
                addMetric(metrics, "latency", "Latency", "Network response time in milliseconds", dashboardType);
                addMetric(metrics, "query-load", "Query Load", "Database queries per second", dashboardType);
                break;

            case "jvm-metrics":
                addMetric(metrics, "cpu-usage", "CPU Usage", "JVM CPU utilization percentage", dashboardType);
                addMetric(metrics, "heap-memory", "Heap Memory", "JVM heap memory usage in MB", dashboardType);
                addMetric(metrics, "non-heap-memory", "Non-Heap Memory", "JVM non-heap memory usage in MB", dashboardType);
                addMetric(metrics, "threads", "Thread Count", "Number of active JVM threads", dashboardType);
                addMetric(metrics, "gc-collection", "GC Collection", "Garbage collection time in seconds", dashboardType);
                addMetric(metrics, "loaded-classes", "Loaded Classes", "Number of classes loaded in the JVM", dashboardType);
                break;
        }

        return metrics;
    }

    private void addMetric(List<Map<String, String>> metrics, String id, String name, String description, String dashboardType) {
        Map<String, String> metric = new HashMap<>();
        metric.put("id", id);
        metric.put("name", name);
        metric.put("description", description);
        metric.put("dashboardType", dashboardType);
        metrics.add(metric);
    }
}