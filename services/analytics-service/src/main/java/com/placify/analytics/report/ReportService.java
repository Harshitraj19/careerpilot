package com.placify.analytics.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.placify.analytics.client.AnalyticsClients;
import com.placify.analytics.client.AnalyticsClients.OfferRow;
import com.placify.analytics.client.AnalyticsClients.PlacementData;
import com.placify.analytics.client.AnalyticsClients.StudentSummary;
import com.placify.analytics.domain.ReadinessSnapshot;
import com.placify.analytics.repository.ReadinessSnapshotRepository;
import com.placify.analytics.service.DashboardService;
import com.placify.analytics.web.AnalyticsDtos.CountPercent;
import com.placify.analytics.web.AnalyticsDtos.NamedCount;
import com.placify.analytics.web.AnalyticsDtos.TpoOverview;
import com.placify.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Placement reports for the TPO: a printable PDF and a detailed Excel workbook. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final Color BRAND = new Color(20, 20, 20);

    private final DashboardService dashboards;
    private final AnalyticsClients clients;
    private final ReadinessSnapshotRepository snapshots;

    record StudentLine(StudentSummary student, OfferRow offer, ReadinessSnapshot readiness) {
    }

    private List<StudentLine> lines(Integer batch) {
        List<StudentSummary> students = clients.students(null, batch);
        PlacementData data = clients.placementData(null);
        Map<String, OfferRow> best = dashboards.bestOffers(data);
        Map<String, ReadinessSnapshot> snaps = snapshots.findByUserIdIn(students.stream().map(StudentSummary::userId).toList())
                .stream().collect(Collectors.toMap(ReadinessSnapshot::getUserId, Function.identity()));
        return students.stream()
                .sorted(Comparator.comparing((StudentSummary s) -> s.branch() == null ? "" : s.branch())
                        .thenComparing(s -> s.regNo() == null ? "" : s.regNo()))
                .map(s -> new StudentLine(s, best.get(s.userId()), snaps.get(s.userId())))
                .toList();
    }

    // ------------------------------------------------------------------ PDF

    public byte[] pdf(Integer batch) {
        TpoOverview o = dashboards.tpo(batch);
        List<StudentLine> lines = lines(batch);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            doc.add(new Paragraph("CareerPilot - Placement Report" + (batch == null ? "" : " (Batch " + batch + ")"), title));
            doc.add(new Paragraph("Generated on " + LocalDateTime.now().format(STAMP), small));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Summary", h2));
            PdfPTable summary = table(new float[] {3, 2});
            kv(summary, "Students", String.valueOf(o.students()));
            kv(summary, "Placed", o.placed() + " (" + o.placementPercent() + "%)");
            kv(summary, "Jobs (open / total)", o.openJobs() + " / " + o.totalJobs());
            kv(summary, "Applications", String.valueOf(o.applications()));
            kv(summary, "Offers (accepted / verified)", String.valueOf(o.offers()));
            kv(summary, "Average CTC (LPA)", str(o.ctc().average()));
            kv(summary, "Median CTC (LPA)", str(o.ctc().median()));
            kv(summary, "Highest CTC (LPA)", str(o.ctc().highest()));
            kv(summary, "Average readiness", o.averageReadiness() == null ? "-" : String.valueOf(o.averageReadiness()));
            kv(summary, "Students at risk", String.valueOf(o.atRisk()));
            doc.add(summary);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Branch-wise placement", h2));
            PdfPTable branches = table(new float[] {4, 2, 2, 2});
            header(branches, "Branch", "Students", "Placed", "%");
            for (CountPercent b : o.byBranch()) {
                cells(branches, b.key(), String.valueOf(b.total()), String.valueOf(b.placed()), String.valueOf(b.percent()));
            }
            doc.add(branches);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Top recruiters", h2));
            PdfPTable rec = table(new float[] {5, 2});
            header(rec, "Company", "Offers");
            for (NamedCount n : o.topRecruiters()) {
                cells(rec, n.name(), String.valueOf(n.count()));
            }
            doc.add(rec);

            doc.newPage();
            doc.add(new Paragraph("Student-wise status", h2));
            PdfPTable st = table(new float[] {2.2f, 3.2f, 2.2f, 1.2f, 1.4f, 3.2f, 1.4f});
            header(st, "Reg. No", "Name", "Branch", "CGPA", "Ready", "Company", "CTC");
            for (StudentLine l : lines) {
                cells(st, nz(l.student().regNo()), nz(l.student().name()), nz(l.student().branch()),
                        str(l.student().cgpa()), l.readiness() == null ? "-" : String.valueOf(l.readiness().getScore()),
                        l.offer() == null ? "Not placed" : l.offer().companyName(),
                        l.offer() == null ? "-" : str(l.offer().ctcLpa()));
            }
            doc.add(st);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.badRequest("Could not build the PDF report: " + ex.getMessage());
        }
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        return t;
    }

    private static void header(PdfPTable t, String... names) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String n : names) {
            PdfPCell c = new PdfPCell(new Phrase(n, f));
            c.setBackgroundColor(BRAND);
            c.setPadding(4);
            t.addCell(c);
        }
        t.setHeaderRows(1);
    }

    private static void cells(PdfPTable t, String... values) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (String v : values) {
            PdfPCell c = new PdfPCell(new Phrase(v, f));
            c.setPadding(3);
            t.addCell(c);
        }
    }

    private static void kv(PdfPTable t, String k, String v) {
        Font kf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        Font vf = FontFactory.getFont(FontFactory.HELVETICA, 9);
        PdfPCell a = new PdfPCell(new Phrase(k, kf));
        a.setPadding(4);
        PdfPCell b = new PdfPCell(new Phrase(v, vf));
        b.setPadding(4);
        b.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
        t.addCell(b);
    }

    // ------------------------------------------------------------------ Excel

    public byte[] excel(Integer batch) {
        TpoOverview o = dashboards.tpo(batch);
        List<StudentLine> lines = lines(batch);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet s1 = wb.createSheet("Summary");
            int r = 0;
            r = kv(s1, r, "Batch", batch == null ? "All" : String.valueOf(batch));
            r = kv(s1, r, "Students", o.students());
            r = kv(s1, r, "Placed", o.placed());
            r = kv(s1, r, "Placement %", o.placementPercent());
            r = kv(s1, r, "Open jobs", o.openJobs());
            r = kv(s1, r, "Total jobs", o.totalJobs());
            r = kv(s1, r, "Applications", o.applications());
            r = kv(s1, r, "Offers", o.offers());
            r = kv(s1, r, "Average CTC (LPA)", str(o.ctc().average()));
            r = kv(s1, r, "Median CTC (LPA)", str(o.ctc().median()));
            r = kv(s1, r, "Highest CTC (LPA)", str(o.ctc().highest()));
            kv(s1, r, "Students at risk", o.atRisk());
            s1.autoSizeColumn(0);
            s1.autoSizeColumn(1);

            Sheet s2 = wb.createSheet("Branch-wise");
            headerRow(s2, head, "Branch", "Students", "Placed", "Placement %");
            int i = 1;
            for (CountPercent b : o.byBranch()) {
                Row row = s2.createRow(i++);
                row.createCell(0).setCellValue(b.key());
                row.createCell(1).setCellValue(b.total());
                row.createCell(2).setCellValue(b.placed());
                row.createCell(3).setCellValue(b.percent());
            }
            autosize(s2, 4);

            Sheet s3 = wb.createSheet("Students");
            headerRow(s3, head, "Reg. No", "Name", "Email", "Branch", "Batch", "CGPA", "Backlogs", "Readiness",
                    "Placed", "Company", "CTC (LPA)", "Tier");
            i = 1;
            for (StudentLine l : lines) {
                Row row = s3.createRow(i++);
                StudentSummary s = l.student();
                text(row, 0, s.regNo());
                text(row, 1, s.name());
                text(row, 2, s.email());
                text(row, 3, s.branch());
                if (s.batch() != null) {
                    row.createCell(4).setCellValue(s.batch());
                }
                if (s.cgpa() != null) {
                    row.createCell(5).setCellValue(s.cgpa().doubleValue());
                }
                row.createCell(6).setCellValue(s.backlogs());
                if (l.readiness() != null) {
                    row.createCell(7).setCellValue(l.readiness().getScore());
                }
                row.createCell(8).setCellValue(l.offer() == null ? "No" : "Yes");
                if (l.offer() != null) {
                    text(row, 9, l.offer().companyName());
                    if (l.offer().ctcLpa() != null) {
                        row.createCell(10).setCellValue(l.offer().ctcLpa().doubleValue());
                    }
                    text(row, 11, l.offer().tier());
                }
            }
            autosize(s3, 12);
            s3.createFreezePane(0, 1);

            Sheet s4 = wb.createSheet("Recruiters");
            headerRow(s4, head, "Company", "Offers");
            i = 1;
            for (NamedCount n : o.topRecruiters()) {
                Row row = s4.createRow(i++);
                text(row, 0, n.name());
                row.createCell(1).setCellValue(n.count());
            }
            autosize(s4, 2);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not build the Excel report: " + ex.getMessage());
        }
    }

    private static void headerRow(Sheet s, CellStyle style, String... names) {
        Row row = s.createRow(0);
        for (int i = 0; i < names.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(names[i]);
            c.setCellStyle(style);
        }
    }

    /** Text cell, protected against spreadsheet formula injection. */
    private static void text(Row row, int col, String value) {
        if (value == null) {
            return;
        }
        String v = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        row.createCell(col).setCellValue(v);
    }

    private static int kv(Sheet s, int r, String k, Object v) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(k);
        if (v instanceof Number n) {
            row.createCell(1).setCellValue(n.doubleValue());
        } else {
            row.createCell(1).setCellValue(String.valueOf(v));
        }
        return r + 1;
    }

    private static void autosize(Sheet s, int cols) {
        for (int c = 0; c < cols; c++) {
            s.autoSizeColumn(c);
        }
    }

    private static String str(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }
}
