package org.example.yasspfe.controllers;

import org.example.yasspfe.services.UnifiedReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private final UnifiedReportService reportService;

    @Autowired
    public ReportController(UnifiedReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    public ResponseEntity<InputStreamResource> generateReport(@RequestBody Map<String, Object> reportConfig) {
        try {
            logger.info("Generating report with config: {}", reportConfig);

            // Extract report configuration
            String dashboardType = (String) reportConfig.getOrDefault("dashboardType", "app-scenarios");
            String reportType = (String) reportConfig.getOrDefault("type", "pdf");
            String timeRange = (String) reportConfig.getOrDefault("timeRange", "1h");

            @SuppressWarnings("unchecked")
            List<String> metrics = (List<String>) reportConfig.getOrDefault("metrics", List.of());

            // Generate the report
            ByteArrayInputStream reportStream = reportService.generateReport(
                    dashboardType, reportType, timeRange, metrics);

            // Set up response headers
            HttpHeaders headers = new HttpHeaders();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = dashboardType + "_report_" + timestamp + "." + reportType.toLowerCase();

            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

            // Determine content type based on report type
            MediaType mediaType = MediaType.APPLICATION_PDF;
            if (reportType.equalsIgnoreCase("csv")) {
                mediaType = MediaType.parseMediaType("text/csv");
            }

            logger.info("Report generated successfully: {}", filename);

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(new InputStreamResource(reportStream));

        } catch (Exception e) {
            logger.error("Error generating report", e);
            throw new RuntimeException("Failed to generate report: " + e.getMessage());
        }
    }

    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> getReportTypes() {
        return ResponseEntity.ok(Map.of(
                "types", reportService.getAvailableReportTypes(),
                "timeRanges", reportService.getAvailableTimeRanges()
        ));
    }

    @GetMapping("/metrics/{dashboardType}")
    public ResponseEntity<List<Map<String, String>>> getMetricsForDashboard(@PathVariable String dashboardType) {
        return ResponseEntity.ok(reportService.getMetricsForDashboard(dashboardType));
    }
}