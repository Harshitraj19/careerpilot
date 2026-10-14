package com.placify.analytics.web;

import com.placify.analytics.ml.PlacementModel.ModelInfo;
import com.placify.analytics.report.ReportService;
import com.placify.analytics.service.AuditService;
import com.placify.analytics.service.DashboardService;
import com.placify.analytics.service.PredictionService;
import com.placify.analytics.service.ReadinessService;
import com.placify.analytics.web.AnalyticsDtos.AdminOverview;
import com.placify.analytics.web.AnalyticsDtos.AtRiskStudent;
import com.placify.analytics.web.AnalyticsDtos.AuditView;
import com.placify.analytics.web.AnalyticsDtos.CompanyOverview;
import com.placify.analytics.web.AnalyticsDtos.Prediction;
import com.placify.analytics.web.AnalyticsDtos.Readiness;
import com.placify.analytics.web.AnalyticsDtos.SeasonRecordResult;
import com.placify.analytics.web.AnalyticsDtos.StudentDashboard;
import com.placify.analytics.web.AnalyticsDtos.TpoOverview;
import com.placify.common.audit.Audited;
import com.placify.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Analytics and reports")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final DashboardService dashboards;
    private final ReadinessService readiness;
    private final PredictionService prediction;
    private final ReportService reports;
    private final AuditService audit;

    // ------------------------------------------------------------------ student

    @GetMapping("/student/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentDashboard myDashboard() {
        return dashboards.student(CurrentUser.id());
    }

    @GetMapping("/student/me/readiness")
    @PreAuthorize("hasRole('STUDENT')")
    public Readiness myReadiness(@RequestParam(defaultValue = "false") boolean refresh) {
        String me = CurrentUser.id();
        return readiness.explain(refresh ? readiness.compute(me) : readiness.get(me));
    }

    @GetMapping("/student/me/prediction")
    @PreAuthorize("hasRole('STUDENT')")
    public Prediction myPrediction() {
        return prediction.predict(CurrentUser.id());
    }

    // ------------------------------------------------------------------ TPO

    @GetMapping("/tpo/overview")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public TpoOverview tpo(@RequestParam(required = false) Integer batch) {
        return dashboards.tpo(batch);
    }

    @GetMapping("/tpo/at-risk")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public List<AtRiskStudent> atRisk(@RequestParam(required = false) Integer batch,
                                      @RequestParam(required = false) Integer threshold) {
        return dashboards.atRisk(batch, threshold);
    }

    @GetMapping("/tpo/students/{userId}")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public StudentDashboard student(@PathVariable String userId) {
        return dashboards.student(userId);
    }

    @GetMapping("/tpo/model")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    public ModelInfo model() {
        return prediction.info();
    }

    @PostMapping("/tpo/model/retrain")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    @Audited(action = "MODEL_RETRAINED")
    public ModelInfo retrain() {
        return prediction.retrain();
    }

    /** End of a season: store every student's outcome as training data and retrain. */
    @PostMapping("/tpo/model/record-season")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    @Audited(action = "SEASON_RECORDED", target = "#batch")
    public SeasonRecordResult recordSeason(@RequestParam int batch) {
        return prediction.recordSeason(batch);
    }

    // ------------------------------------------------------------------ company

    @GetMapping("/company/overview")
    @PreAuthorize("hasRole('COMPANY')")
    public CompanyOverview company() {
        return dashboards.company(CurrentUser.id());
    }

    // ------------------------------------------------------------------ admin

    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminOverview admin() {
        return dashboards.admin();
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditView> auditLogs(@RequestParam(required = false) String service,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) String actor,
                                     @RequestParam(required = false) Boolean success,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return audit.search(service, action, actor, success, start(from), end(to), page, size);
    }

    @GetMapping("/audit-logs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> auditCsv(@RequestParam(required = false) String service,
                                           @RequestParam(required = false) String action,
                                           @RequestParam(required = false) String actor,
                                           @RequestParam(required = false) Boolean success,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = audit.csv(service, action, actor, success, start(from), end(to)).getBytes(StandardCharsets.UTF_8);
        return file(body, "audit-log.csv", new MediaType("text", "csv", StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ reports

    @GetMapping("/reports/placement.pdf")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    @Audited(action = "REPORT_DOWNLOADED", target = "'pdf'")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) Integer batch) {
        return file(reports.pdf(batch), "placement-report" + (batch == null ? "" : "-" + batch) + ".pdf",
                MediaType.APPLICATION_PDF);
    }

    @GetMapping("/reports/placement.xlsx")
    @PreAuthorize("hasAnyRole('TPO','ADMIN')")
    @Audited(action = "REPORT_DOWNLOADED", target = "'xlsx'")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) Integer batch) {
        return file(reports.excel(batch), "placement-report" + (batch == null ? "" : "-" + batch) + ".xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(type)
                .body(body);
    }

    private static LocalDateTime start(LocalDate d) {
        return d == null ? null : d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return d == null ? null : d.plusDays(1).atStartOfDay();
    }
}
