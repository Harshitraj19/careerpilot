# CareerPilot REST API reference

All endpoints are called through the API gateway (`http://localhost:8080`) with `Authorization: Bearer <token>`, except the public paths listed in `services/gateway/src/main/resources/application.yml`.
The web portal calls them through its `/bff/api/...` proxy. Interactive OpenAPI docs: `http://<service>:<port>/swagger-ui.html`.

## ai-helper-service · AiController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/ai/tutor/query` | STUDENT | ask |
| `GET` | `/api/ai/tutor/history` | STUDENT | history |
| `POST` | `/api/ai/mock-interviews` | STUDENT | start |
| `POST` | `/api/ai/mock-interviews/{id}/answer` | STUDENT | answer |
| `GET` | `/api/ai/mock-interviews` | STUDENT | list |
| `GET` | `/api/ai/mock-interviews/{id}` | STUDENT | one |
| `POST` | `/api/ai/interview-questions` | STUDENT | interview questions |

## analytics-service · AnalyticsController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/analytics/student/me` | STUDENT | my dashboard |
| `GET` | `/api/analytics/student/me/readiness` | STUDENT | my readiness |
| `GET` | `/api/analytics/student/me/prediction` | STUDENT | my prediction |
| `GET` | `/api/analytics/tpo/overview` | TPO, ADMIN | tpo |
| `GET` | `/api/analytics/tpo/at-risk` | TPO, ADMIN | at risk |
| `GET` | `/api/analytics/tpo/students/{userId}` | TPO, ADMIN | student |
| `GET` | `/api/analytics/tpo/model` | TPO, ADMIN | model |
| `POST` | `/api/analytics/tpo/model/retrain` | TPO, ADMIN | retrain |
| `POST` | `/api/analytics/tpo/model/record-season` | TPO, ADMIN | record season |
| `GET` | `/api/analytics/company/overview` | COMPANY | company |
| `GET` | `/api/analytics/admin/overview` | ADMIN | admin |
| `GET` | `/api/analytics/audit-logs` | ADMIN | audit logs |
| `GET` | `/api/analytics/audit-logs/export` | ADMIN | audit csv |
| `GET` | `/api/analytics/reports/placement.pdf` | TPO, ADMIN | pdf |
| `GET` | `/api/analytics/reports/placement.xlsx` | TPO, ADMIN | excel |

## assessment-service · McqController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/assessment/practice/topics` | any signed-in user | topics |
| `GET` | `/api/assessment/practice/questions` | any signed-in user | practice |
| `POST` | `/api/assessment/practice/answer` | STUDENT | answer |
| `GET` | `/api/assessment/practice/stats` | STUDENT | stats |
| `GET` | `/api/assessment/mcqs` | TPO, ADMIN, COMPANY | search |
| `POST` | `/api/assessment/mcqs` | TPO, ADMIN, COMPANY | create |
| `PUT` | `/api/assessment/mcqs/{id}` | TPO, ADMIN, COMPANY | update |
| `DELETE` | `/api/assessment/mcqs/{id}` | TPO, ADMIN, COMPANY | delete |
| `POST` | `/api/assessment/mcqs/{id}/approve` | TPO, ADMIN | approve |
| `POST` | `/api/assessment/mcqs/generate` | TPO, ADMIN | generate |

## assessment-service · TestController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/assessment/tests` | any signed-in user | list |
| `GET` | `/api/assessment/tests/{id}` | any signed-in user | one |
| `POST` | `/api/assessment/tests` | COMPANY, TPO, ADMIN | create |
| `PUT` | `/api/assessment/tests/{id}` | COMPANY, TPO, ADMIN | update |
| `DELETE` | `/api/assessment/tests/{id}` | COMPANY, TPO, ADMIN | delete |
| `POST` | `/api/assessment/tests/{id}/publish` | COMPANY, TPO, ADMIN | publish |
| `POST` | `/api/assessment/tests/{id}/close` | COMPANY, TPO, ADMIN | close |
| `GET` | `/api/assessment/tests/{id}/pool` | COMPANY, TPO, ADMIN | pool |
| `GET` | `/api/assessment/tests/{id}/results` | COMPANY, TPO, ADMIN | results |
| `POST` | `/api/assessment/tests/{id}/start` | STUDENT | start |
| `GET` | `/api/assessment/attempts/{id}` | STUDENT | attempt |
| `POST` | `/api/assessment/attempts/{id}/answers` | STUDENT | answer |
| `GET` | `/api/assessment/attempts/{id}/coding/{questionId}` | STUDENT | coding |
| `POST` | `/api/assessment/attempts/{id}/submit` | STUDENT | submit |
| `GET` | `/api/assessment/attempts/{id}/result` | any signed-in user | result |
| `GET` | `/api/assessment/attempts/me` | STUDENT | mine |

## auth-service · AuthController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/auth/captcha` | public | captcha |
| `POST` | `/api/auth/login` | public | login |
| `POST` | `/api/auth/login/2fa` | public | two factor |
| `POST` | `/api/auth/logout` | any signed-in user | logout |
| `GET` | `/api/auth/me` | any signed-in user | me |
| `POST` | `/api/auth/password/change` | any signed-in user | change password |
| `POST` | `/api/auth/password/forgot` | public | forgot |
| `POST` | `/api/auth/password/reset` | public | reset |
| `POST` | `/api/auth/company/register/otp` | public | company otp |
| `POST` | `/api/auth/company/register` | public | company register |
| `PUT` | `/api/auth/me/two-factor` | any signed-in user | two factor |
| `GET` | `/api/auth/me/login-history` | any signed-in user | my history |

## auth-service · UserAdminController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/auth/students/import` | TPO, ADMIN | import students |
| `POST` | `/api/auth/students` | TPO, ADMIN | create student |
| `POST` | `/api/auth/admin/tpo` | ADMIN | create tpo |
| `POST` | `/api/auth/admin/admins` | ADMIN | create admin |
| `GET` | `/api/auth/companies/pending` | TPO, ADMIN | pending |
| `POST` | `/api/auth/companies/{id}/approve` | TPO, ADMIN | approve |
| `POST` | `/api/auth/companies/{id}/reject` | TPO, ADMIN | reject |
| `GET` | `/api/auth/users` | TPO, ADMIN | search |
| `POST` | `/api/auth/users/{id}/block` | TPO, ADMIN | block |
| `POST` | `/api/auth/users/{id}/unblock` | TPO, ADMIN | unblock |
| `GET` | `/api/auth/admin/login-history` | ADMIN | all history |

## coding-service · ContestController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/coding/contests` | any signed-in user | list |
| `GET` | `/api/coding/contests/{id}` | any signed-in user | one |
| `POST` | `/api/coding/contests` | TPO, ADMIN | create |
| `PUT` | `/api/coding/contests/{id}` | TPO, ADMIN | update |
| `DELETE` | `/api/coding/contests/{id}` | TPO, ADMIN | delete |
| `POST` | `/api/coding/contests/{id}/join` | STUDENT | join |
| `GET` | `/api/coding/contests/{id}/leaderboard` | any signed-in user | leaderboard |
| `GET` | `/api/coding/contests/{id}/questions/{questionId}` | any signed-in user | question |

## coding-service · PracticeController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/coding/practice/next` | STUDENT | next |
| `GET` | `/api/coding/practice/stats` | STUDENT | stats |
| `GET` | `/api/coding/badges/me` | any signed-in user | my badges |
| `GET` | `/api/coding/hall-of-fame` | any signed-in user | hall of fame |

## coding-service · QuestionController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/coding/questions` | any signed-in user | list |
| `GET` | `/api/coding/tags` | any signed-in user | tags |
| `GET` | `/api/coding/questions/{id}` | any signed-in user | one |
| `POST` | `/api/coding/questions` | TPO, ADMIN, COMPANY | create |
| `PUT` | `/api/coding/questions/{id}` | TPO, ADMIN, COMPANY | update |
| `DELETE` | `/api/coding/questions/{id}` | TPO, ADMIN, COMPANY | delete |
| `GET` | `/api/coding/questions/{id}/testcases` | any signed-in user | testcases |
| `POST` | `/api/coding/questions/{id}/testcases` | TPO, ADMIN, COMPANY | add testcase |
| `PUT` | `/api/coding/testcases/{id}` | TPO, ADMIN, COMPANY | update testcase |
| `DELETE` | `/api/coding/testcases/{id}` | TPO, ADMIN, COMPANY | delete testcase |

## gateway · ServiceStatusController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/gateway/services` | ADMIN | services |

## interview-service · InterviewController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/interviews/rounds/{roundId}` | COMPANY, TPO, ADMIN | board |
| `POST` | `/api/interviews/evaluations` | COMPANY, TPO, ADMIN | evaluate |
| `GET` | `/api/interviews/me` | STUDENT | my results |
| `POST` | `/api/interviews/experiences` | STUDENT | submit |
| `GET` | `/api/interviews/experiences` | any signed-in user | search |
| `GET` | `/api/interviews/experiences/companies` | any signed-in user | companies |
| `GET` | `/api/interviews/experiences/mine` | STUDENT | mine |
| `GET` | `/api/interviews/experiences/pending` | TPO, ADMIN | pending |
| `GET` | `/api/interviews/experiences/{id}` | any signed-in user | one |
| `POST` | `/api/interviews/experiences/{id}/review` | TPO, ADMIN | review |
| `POST` | `/api/interviews/experiences/{id}/vote` | STUDENT | vote |
| `DELETE` | `/api/interviews/experiences/{id}` | any signed-in user | delete |
| `GET` | `/api/interviews/prep` | any signed-in user | prep |

## notification-service · NotificationController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/notifications` | any signed-in user | list |
| `GET` | `/api/notifications/unread-count` | any signed-in user | unread |
| `POST` | `/api/notifications/{id}/read` | any signed-in user | read |
| `POST` | `/api/notifications/read-all` | any signed-in user | read all |
| `POST` | `/api/notifications/ws-ticket` | any signed-in user | ticket |
| `POST` | `/api/notifications/announcements` | TPO, ADMIN | announce |
| `GET` | `/api/notifications/announcements` | any signed-in user | announcements |

## placement-service · ApplicationController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/placement/jobs/{jobId}/apply` | STUDENT | apply |
| `POST` | `/api/placement/applications/{id}/withdraw` | STUDENT | withdraw |
| `GET` | `/api/placement/applications/me` | STUDENT | mine |
| `GET` | `/api/placement/applications/{id}/timeline` | any signed-in user | timeline |
| `GET` | `/api/placement/applications` | TPO, ADMIN | monitor |
| `GET` | `/api/placement/jobs/{jobId}/applicants` | COMPANY, TPO, ADMIN | applicants |
| `POST` | `/api/placement/jobs/{jobId}/shortlist` | COMPANY, TPO, ADMIN | shortlist |
| `POST` | `/api/placement/jobs/{jobId}/reject-applicants` | COMPANY, TPO, ADMIN | reject |
| `GET` | `/api/placement/rounds/{roundId}/results` | COMPANY, TPO, ADMIN | results |
| `POST` | `/api/placement/rounds/{roundId}/results` | COMPANY, TPO, ADMIN | decide |
| `PUT` | `/api/placement/rounds/{roundId}/test` | COMPANY, TPO, ADMIN | link test |

## placement-service · CalendarController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/placement/events` | any signed-in user | list |
| `POST` | `/api/placement/events` | COMPANY, TPO, ADMIN | create |
| `PUT` | `/api/placement/events/{id}` | COMPANY, TPO, ADMIN | update |
| `DELETE` | `/api/placement/events/{id}` | COMPANY, TPO, ADMIN | delete |
| `POST` | `/api/placement/events/{id}/attendance` | COMPANY, TPO, ADMIN | mark |
| `GET` | `/api/placement/events/{id}/attendance` | COMPANY, TPO, ADMIN | attendance |
| `POST` | `/api/placement/events/{id}/check-in` | STUDENT | check in |

## placement-service · JobController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/placement/jobs` | any signed-in user | list |
| `GET` | `/api/placement/jobs/{id}` | any signed-in user | one |
| `POST` | `/api/placement/jobs` | COMPANY | create |
| `PUT` | `/api/placement/jobs/{id}` | COMPANY, TPO, ADMIN | update |
| `POST` | `/api/placement/jobs/{id}/publish` | TPO, ADMIN | publish |
| `POST` | `/api/placement/jobs/{id}/reject` | TPO, ADMIN | reject |
| `POST` | `/api/placement/jobs/{id}/close` | COMPANY, TPO, ADMIN | close |
| `GET` | `/api/placement/jobs/{id}/eligibility` | STUDENT | eligibility |
| `GET` | `/api/placement/jobs/{id}/match` | STUDENT | match |
| `GET` | `/api/placement/jobs/{id}/rounds` | any signed-in user | rounds |
| `POST` | `/api/placement/jobs/{id}/rounds` | COMPANY, TPO, ADMIN | add round |
| `PUT` | `/api/placement/rounds/{roundId}` | COMPANY, TPO, ADMIN | update round |
| `DELETE` | `/api/placement/rounds/{roundId}` | COMPANY, TPO, ADMIN | delete round |

## placement-service · OfferController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/placement/applications/{applicationId}/offer` | COMPANY, TPO, ADMIN | issue |
| `GET` | `/api/placement/offers` | any signed-in user | list |
| `POST` | `/api/placement/offers/{id}/accept` | STUDENT | accept |
| `POST` | `/api/placement/offers/{id}/decline` | STUDENT | decline |
| `POST` | `/api/placement/offers/off-campus` | STUDENT | off campus |
| `POST` | `/api/placement/offers/{id}/verify` | TPO, ADMIN | verify |
| `POST` | `/api/placement/offers/{id}/reject` | TPO, ADMIN | reject |
| `GET` | `/api/placement/offers/{id}/letter` | any signed-in user | letter |
| `GET` | `/api/placement/certificates/me` | STUDENT | my certificates |
| `GET` | `/api/placement/certificates/{id}/pdf` | any signed-in user | certificate pdf |
| `GET` | `/api/placement/certificates/verify/{code}` | public | verify |

## placement-service · PolicyController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/placement/policy` | any signed-in user | get |
| `PUT` | `/api/placement/policy` | TPO, ADMIN | update |

## placement-service · SlotController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/placement/jobs/{jobId}/slots` | COMPANY, TPO, ADMIN | generate |
| `GET` | `/api/placement/jobs/{jobId}/slots` | any signed-in user | for job |
| `GET` | `/api/placement/slots/me` | STUDENT | mine |
| `POST` | `/api/placement/slots/{id}/book` | STUDENT | book |
| `POST` | `/api/placement/slots/{id}/cancel` | STUDENT | cancel |
| `DELETE` | `/api/placement/slots/{id}` | COMPANY, TPO, ADMIN | delete |

## proctoring-service · ProctoringController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/proctoring/sessions` | STUDENT | start |
| `POST` | `/api/proctoring/sessions/{id}/frames` | STUDENT | frame |
| `POST` | `/api/proctoring/sessions/{id}/events` | STUDENT | event |
| `GET` | `/api/proctoring/sessions/{id}` | STUDENT | status |
| `POST` | `/api/proctoring/sessions/{id}/end` | STUDENT | end |
| `GET` | `/api/proctoring/reports/attempts/{attemptId}` | COMPANY, TPO, ADMIN | report |
| `GET` | `/api/proctoring/reports/violations/{id}/snapshot` | COMPANY, TPO, ADMIN | snapshot |

## profile-service · AtsController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `POST` | `/api/profile/ats/check` | STUDENT | check |
| `GET` | `/api/profile/ats/history` | STUDENT | history |
| `GET` | `/api/profile/ats/stats` | STUDENT | stats |
| `GET` | `/api/profile/ats/{id}` | STUDENT | one |

## profile-service · CompanyProfileController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/profile/companies/me` | COMPANY | me |
| `PUT` | `/api/profile/companies/me` | COMPANY | update |
| `POST` | `/api/profile/companies/me/logo` | COMPANY | logo |
| `GET` | `/api/profile/companies/{userId}` | any signed-in user | one |
| `GET` | `/api/profile/companies/{userId}/logo` | any signed-in user | logo |

## profile-service · StudentProfileController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/profile/students/me` | STUDENT | me |
| `PUT` | `/api/profile/students/me` | STUDENT | update |
| `GET` | `/api/profile/students/me/completeness` | STUDENT | completeness |
| `POST` | `/api/profile/students/me/photo` | STUDENT | upload photo |
| `GET` | `/api/profile/students/{userId}/photo` | any signed-in user | photo |
| `POST` | `/api/profile/students/me/resume` | STUDENT | upload resume |
| `DELETE` | `/api/profile/students/me/resume` | STUDENT | delete resume |
| `GET` | `/api/profile/students/{userId}/resume` | any signed-in user | resume |
| `POST` | `/api/profile/students/me/resume/parse` | STUDENT | parse |
| `GET` | `/api/profile/students/{userId}` | TPO, ADMIN, COMPANY, STUDENT | one |
| `GET` | `/api/profile/students` | TPO, ADMIN | search |
| `GET` | `/api/profile/students/branches` | any signed-in user | branches |
| `PUT` | `/api/profile/students/{userId}/academic` | TPO, ADMIN | academic |

## submission-service · SubmissionController

| Method | Path | Roles | Operation |
|---|---|---|---|
| `GET` | `/api/submissions/languages` | any signed-in user | languages |
| `POST` | `/api/submissions/run` | any signed-in user | run |
| `POST` | `/api/submissions` | any signed-in user | submit |
| `GET` | `/api/submissions/me` | any signed-in user | mine |
| `GET` | `/api/submissions/{id}` | any signed-in user | one |
| `GET` | `/api/submissions/{id}/results` | any signed-in user | results |
| `POST` | `/api/submissions/{id}/review` | any signed-in user | review |
| `PUT` | `/api/submissions/callback` | Judge0 (secret key) | callback put |
| `POST` | `/api/submissions/callback` | Judge0 (secret key) | callback post |
| `POST` | `/api/submissions/plagiarism/checks` | TPO, ADMIN, COMPANY | run check |
| `GET` | `/api/submissions/plagiarism/checks` | TPO, ADMIN, COMPANY | checks |
| `GET` | `/api/submissions/plagiarism/checks/{id}` | TPO, ADMIN, COMPANY | check |
| `GET` | `/api/submissions/plagiarism/pairs/{id}` | TPO, ADMIN, COMPANY | pair |

