package com.placify.submission.service;

import com.placify.submission.domain.Submission;
import com.placify.submission.domain.SubmissionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictTest {

    private static SubmissionResult r(SubmissionResult.Verdict v) {
        return SubmissionResult.builder().status(v).build();
    }

    @Test
    void judge0StatusIdsMapToVerdicts() {
        assertThat(SubmissionService.verdict(3)).isEqualTo(SubmissionResult.Verdict.accepted);
        assertThat(SubmissionService.verdict(4)).isEqualTo(SubmissionResult.Verdict.wrong_answer);
        assertThat(SubmissionService.verdict(5)).isEqualTo(SubmissionResult.Verdict.time_limit_exceeded);
        assertThat(SubmissionService.verdict(6)).isEqualTo(SubmissionResult.Verdict.compilation_error);
        assertThat(SubmissionService.verdict(11)).isEqualTo(SubmissionResult.Verdict.runtime_error);
    }

    @Test
    void allPassedIsAccepted() {
        assertThat(SubmissionService.finalStatus(List.of(r(SubmissionResult.Verdict.accepted)), 1, 1)).isEqualTo(Submission.Status.accepted);
    }

    @Test
    void compileErrorWinsOverOtherFailures() {
        var list = List.of(r(SubmissionResult.Verdict.wrong_answer), r(SubmissionResult.Verdict.compilation_error));
        assertThat(SubmissionService.finalStatus(list, 0, 2)).isEqualTo(Submission.Status.compilation_error);
    }

    @Test
    void timeoutIsReportedBeforeWrongAnswer() {
        var list = List.of(r(SubmissionResult.Verdict.accepted), r(SubmissionResult.Verdict.time_limit_exceeded), r(SubmissionResult.Verdict.wrong_answer));
        assertThat(SubmissionService.finalStatus(list, 1, 3)).isEqualTo(Submission.Status.time_limit_exceeded);
    }
}
