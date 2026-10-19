package com.behlul.liftlog.controller;

import com.behlul.liftlog.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Downloads of the caller's own data. The methods write to the response stream themselves instead of
 * returning a body, so the file streams out while the rows are read.
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private static final String CSV = "text/csv;charset=UTF-8";
    private static final String ZIP = "application/zip";

    private final ExportService exportService;
    private final Clock clock;

    public ExportController(ExportService exportService, Clock clock) {
        this.exportService = exportService;
        this.clock = clock;
    }

    @GetMapping("/workouts.csv")
    public void workouts(HttpServletResponse response) throws IOException {
        startDownload(response, CSV, "liftlog-antrenman", "csv");
        exportService.writeWorkoutsCsv(response.getOutputStream());
    }

    @GetMapping("/nutrition.csv")
    public void nutrition(HttpServletResponse response) throws IOException {
        startDownload(response, CSV, "liftlog-beslenme", "csv");
        exportService.writeNutritionCsv(response.getOutputStream());
    }

    @GetMapping("/all.zip")
    public void all(HttpServletResponse response) throws IOException {
        startDownload(response, ZIP, "liftlog-veriler", "zip");
        exportService.writeAllZip(response.getOutputStream());
    }

    /** e.g. liftlog-antrenman-2026-10-19.csv; no-store because the file holds personal data. */
    private void startDownload(HttpServletResponse response, String contentType, String baseName, String extension) {
        String filename = baseName + "-" + LocalDate.now(clock) + "." + extension;
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
