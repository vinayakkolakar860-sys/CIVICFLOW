package com.civicflow.reporting;

import com.civicflow.exception.ExportException;
import com.civicflow.exception.ValidationException;
import com.civicflow.security.FileUploadValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportingAndUploadTest {

    @TempDir
    Path tempDir;

    @Test
    void pdfExporterGeneratesValidPdfAndHandlesEmptyReport() throws Exception {
        PdfReportExporter exporter = new PdfReportExporter();
        ReportData report = new ReportData(
                "Simulated Candidate Report",
                "Unit test export",
                "DEMO DATA — Educational Simulation Only",
                "Test Admin",
                LocalDateTime.of(2026, 6, 10, 12, 0),
                List.of("Code", "Name", "Party"),
                List.of(List.of("CND-01", "Avery Chen", "CFA"))
        );

        Path pdfFile = tempDir.resolve("report.pdf");
        exporter.export(report, pdfFile);
        assertTrue(Files.exists(pdfFile));
        byte[] bytes = Files.readAllBytes(pdfFile);
        assertTrue(bytes.length > 500);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));

        // Empty report should still produce valid PDF cleanly
        ReportData emptyReport = new ReportData(
                "Empty Report", "No rows", "DEMO DATA", "Test Admin",
                LocalDateTime.now(), List.of("Col1", "Col2"), List.of());
        Path emptyPdf = tempDir.resolve("empty.pdf");
        exporter.export(emptyReport, emptyPdf);
        assertTrue(Files.exists(emptyPdf));
        assertTrue(Files.size(emptyPdf) > 300);
    }

    @Test
    void csvExporterGeneratesSafeCsvWithFormulaInjectionProtection() throws Exception {
        CsvReportExporter exporter = new CsvReportExporter();
        ReportData report = new ReportData(
                "Voter Export",
                "Test",
                "DEMO DATA",
                "Admin",
                LocalDateTime.of(2026, 6, 10, 12, 0),
                List.of("Name", "FormulaTest"),
                List.of(List.of("Maya Linwood", "=HYPERLINK(\"http://evil\")"))
        );

        Path csvFile = tempDir.resolve("voters.csv");
        exporter.export(report, csvFile);
        String content = Files.readString(csvFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("CIVICFLOW"));
        assertTrue(content.contains("'=HYPERLINK"));
    }

    @Test
    void exportersRejectInvalidDestinationDirectory() {
        Path badPath = tempDir.resolve("nonexistent_dir").resolve("out.pdf");
        ReportData report = new ReportData("Test", "", "DEMO", "Admin", LocalDateTime.now(), List.of("A"), List.of());
        assertThrows(ExportException.class, () -> new PdfReportExporter().export(report, badPath));
        assertThrows(ExportException.class, () -> new CsvReportExporter().export(report, badPath));
    }

    @Test
    void fileUploadValidatorAcceptsRealPngHeaderAndRejectsForgedFile() throws Exception {
        FileUploadValidator validator = new FileUploadValidator();

        Path fakePng = tempDir.resolve("forged.png");
        Files.writeString(fakePng, "NOT A REAL PNG FILE");
        assertThrows(ValidationException.class, () -> validator.validateImageFile(fakePng));

        Path badExt = tempDir.resolve("script.exe");
        Files.write(badExt, new byte[]{0x4D, 0x5A, 0x00, 0x00});
        assertThrows(ValidationException.class, () -> validator.validateImageFile(badExt));

        Path validPng = tempDir.resolve("logo.png");
        Files.write(validPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00});
        assertDoesNotThrow(() -> validator.validateImageFile(validPng));

        String stored = validator.storeValidatedImage(validPng, tempDir.resolve("uploads"), "party_CFA");
        assertTrue(Files.exists(Path.of(stored)));
    }
}
