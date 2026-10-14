package com.placify.web.controller;

import com.placify.web.config.WebProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the portal pages. Pages are rendered by Thymeleaf with the shared
 * layout; their data is loaded by page scripts through the /bff proxy.
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final WebProperties props;

    // ================================================================= student

    @GetMapping("/student/dashboard")
    public String studentDashboard() {
        return "student/dashboard";
    }

    @GetMapping("/student/profile")
    public String studentProfile() {
        return "student/profile";
    }

    @GetMapping("/student/jobs")
    public String studentJobs() {
        return "student/jobs";
    }

    @GetMapping("/student/jobs/{id}")
    public String studentJob(@PathVariable Long id, Model model) {
        model.addAttribute("id", id);
        return "student/job";
    }

    @GetMapping("/student/applications")
    public String studentApplications() {
        return "student/applications";
    }

    @GetMapping("/student/applications/{id}")
    public String studentApplication(@PathVariable Long id, Model model) {
        model.addAttribute("id", id);
        return "student/application";
    }

    @GetMapping("/student/offers")
    public String studentOffers() {
        return "student/offers";
    }

    @GetMapping("/student/practice")
    public String practice() {
        return "student/practice";
    }

    @GetMapping("/student/practice/{questionId}")
    public String problem(@PathVariable String questionId, Model model) {
        model.addAttribute("questionId", questionId);
        model.addAttribute("contestId", null);
        return "student/problem";
    }

    @GetMapping("/student/submissions")
    public String submissions() {
        return "student/submissions";
    }

    @GetMapping("/student/contests/{id}")
    public String contest(@PathVariable Long id, Model model) {
        model.addAttribute("id", id);
        return "student/contest";
    }

    @GetMapping("/student/contests/{id}/problems/{questionId}")
    public String contestProblem(@PathVariable Long id, @PathVariable String questionId, Model model) {
        model.addAttribute("questionId", questionId);
        model.addAttribute("contestId", id);
        return "student/problem";
    }

    @GetMapping("/student/aptitude")
    public String aptitude() {
        return "student/aptitude";
    }

    @GetMapping("/student/tests")
    public String tests() {
        return "student/tests";
    }

    @GetMapping("/student/tests/{id}/take")
    public String exam(@PathVariable Long id, Model model) {
        model.addAttribute("testId", id);
        model.addAttribute("frameSeconds", Math.max(2, props.proctoringFrameSeconds()));
        return "student/exam";
    }

    @GetMapping("/student/results/{attemptId}")
    public String result(@PathVariable Long attemptId, Model model) {
        model.addAttribute("attemptId", attemptId);
        return "student/result";
    }

    @GetMapping("/student/ats")
    public String ats() {
        return "student/ats";
    }

    @GetMapping("/student/mock-interviews")
    public String mockInterviews() {
        return "student/mock-interviews";
    }

    @GetMapping("/student/experiences")
    public String experiences() {
        return "student/experiences";
    }

    @GetMapping("/student/prep")
    public String prep() {
        return "student/prep";
    }

    // ================================================================= company

    @GetMapping("/company/dashboard")
    public String companyDashboard() {
        return "company/dashboard";
    }

    @GetMapping("/company/profile")
    public String companyProfile() {
        return "company/profile";
    }

    @GetMapping("/company/jobs")
    public String companyJobs() {
        return "company/jobs";
    }

    @GetMapping("/company/offers")
    public String companyOffers() {
        return "company/offers";
    }

    // ================================================================= TPO

    @GetMapping("/tpo/dashboard")
    public String tpoDashboard() {
        return "tpo/dashboard";
    }

    @GetMapping("/tpo/students")
    public String tpoStudents() {
        return "tpo/students";
    }

    @GetMapping("/tpo/students/{userId}")
    public String tpoStudent(@PathVariable String userId, Model model) {
        model.addAttribute("userId", userId);
        return "tpo/student";
    }

    @GetMapping("/tpo/companies")
    public String tpoCompanies() {
        return "tpo/companies";
    }

    @GetMapping("/tpo/jobs")
    public String tpoJobs() {
        return "tpo/jobs";
    }

    @GetMapping("/tpo/applications")
    public String tpoApplications() {
        return "tpo/applications";
    }

    @GetMapping("/tpo/offers")
    public String tpoOffers() {
        return "tpo/offers";
    }

    @GetMapping("/tpo/contests")
    public String tpoContests() {
        return "tpo/contests";
    }

    @GetMapping("/tpo/experiences")
    public String moderation() {
        return "tpo/experiences";
    }

    @GetMapping("/tpo/policy")
    public String policy() {
        return "tpo/policy";
    }

    @GetMapping("/tpo/reports")
    public String reports() {
        return "tpo/reports";
    }

    // ================================================================= admin

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/users")
    public String users() {
        return "admin/users";
    }

    @GetMapping("/admin/audit")
    public String audit() {
        return "admin/audit";
    }

    // ================================================================= shared by company + TPO

    @GetMapping({"/company/jobs/new", "/tpo/jobs/new"})
    public String newJob(Model model) {
        model.addAttribute("jobId", null);
        return "common/job-form";
    }

    @GetMapping({"/company/jobs/{id}/edit", "/tpo/jobs/{id}/edit"})
    public String editJob(@PathVariable Long id, Model model) {
        model.addAttribute("jobId", id);
        return "common/job-form";
    }

    @GetMapping({"/company/jobs/{id}", "/tpo/jobs/{id}"})
    public String manageJob(@PathVariable Long id, Model model) {
        model.addAttribute("jobId", id);
        return "common/job-manage";
    }

    @GetMapping({"/company/rounds/{roundId}/interviews", "/tpo/rounds/{roundId}/interviews"})
    public String interviewBoard(@PathVariable Long roundId, Model model) {
        model.addAttribute("roundId", roundId);
        return "common/interview-board";
    }

    @GetMapping({"/company/tests", "/tpo/tests"})
    public String manageTests() {
        return "common/tests";
    }

    @GetMapping({"/company/tests/new", "/tpo/tests/new"})
    public String newTest(Model model) {
        model.addAttribute("testId", null);
        return "common/test-form";
    }

    @GetMapping({"/company/tests/{id}/edit", "/tpo/tests/{id}/edit"})
    public String editTest(@PathVariable Long id, Model model) {
        model.addAttribute("testId", id);
        return "common/test-form";
    }

    @GetMapping({"/company/tests/{id}", "/tpo/tests/{id}"})
    public String testDetail(@PathVariable Long id, Model model) {
        model.addAttribute("testId", id);
        return "common/test-detail";
    }

    @GetMapping({"/company/attempts/{attemptId}/proctoring", "/tpo/attempts/{attemptId}/proctoring"})
    public String proctoringReport(@PathVariable Long attemptId, Model model) {
        model.addAttribute("attemptId", attemptId);
        return "common/proctoring-report";
    }

    @GetMapping({"/student/calendar", "/company/calendar", "/tpo/calendar"})
    public String calendar() {
        return "common/calendar";
    }

    @GetMapping({"/student/contests", "/tpo/contests/list"})
    public String contests() {
        return "common/contests";
    }

    @GetMapping({"/student/hall-of-fame", "/tpo/hall-of-fame"})
    public String hallOfFame() {
        return "common/hall-of-fame";
    }

    @GetMapping({"/company/mcqs", "/tpo/mcqs"})
    public String mcqs() {
        return "common/mcqs";
    }

    @GetMapping({"/company/questions", "/tpo/questions"})
    public String questions() {
        return "common/questions";
    }

    @GetMapping({"/company/questions/new", "/tpo/questions/new"})
    public String newQuestion(Model model) {
        model.addAttribute("questionId", null);
        return "common/question-form";
    }

    @GetMapping({"/company/questions/{id}", "/tpo/questions/{id}"})
    public String editQuestion(@PathVariable String id, Model model) {
        model.addAttribute("questionId", id);
        return "common/question-form";
    }

    @GetMapping({"/company/plagiarism", "/tpo/plagiarism"})
    public String plagiarism() {
        return "common/plagiarism";
    }

    @GetMapping({"/student/queries", "/tpo/queries"})
    public String queries() {
        return "common/queries";
    }

    @GetMapping({"/tpo/announcements"})
    public String announcementsManage() {
        return "tpo/announcements";
    }

    // ================================================================= every role

    @GetMapping("/notifications")
    public String notifications() {
        return "common/notifications";
    }

    @GetMapping("/announcements")
    public String announcements() {
        return "common/announcements";
    }

    @GetMapping("/account")
    public String account() {
        return "common/account";
    }
}
