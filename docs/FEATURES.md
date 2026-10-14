# CareerPilot feature list

Everything below is implemented in Java (backend and AI). ✅ = implemented in this repository.

## 1. Accounts and security (auth-service, gateway, web-ui)
- ✅ Four VTOP-style portals: Student (registration number), Recruiter (e-mail), TPO (employee ID), Admin (username)
- ✅ Portal check: an account can only log in on its own portal
- ✅ Bulk student import from CSV / Excel with flexible headers and temporary passwords
- ✅ Forced password change on first login (enforced by gateway and portal)
- ✅ Strong password policy, BCrypt (cost 12)
- ✅ Image captcha (Java2D) on login and registration
- ✅ Account lockout after 5 failed attempts for 15 minutes
- ✅ E-mail OTP for company registration and password reset (cool-down, attempt limits)
- ✅ Optional two-step login with e-mail OTP
- ✅ Single active session (new login ends the old one) and inactivity timeout (Redis)
- ✅ Login history for every user and for admins
- ✅ Company self-registration with TPO approval / rejection
- ✅ Block / unblock users; admin creates TPO and admin accounts
- ✅ JWT kept server-side (BFF), CSRF protection, CSP and security headers, rate limiting per IP
- ✅ Central audit log with search and safe CSV export

## 2. Profiles and resumes (profile-service)
- ✅ Student profile with academics (TPO-managed), skills, projects, certifications, links, photo
- ✅ Resume upload (PDF/DOCX) with text extraction (Tika, PDFBox)
- ✅ AI resume auto-fill (skills, projects, summary) with preview before applying
- ✅ Profile completeness score with missing items
- ✅ ATS checker: keyword match, semantic similarity (DJL embeddings), content, structure, impact and parseability scores, tips, AI feedback, history
- ✅ Company profile with logo

## 3. Placement drives (placement-service)
- ✅ Job postings with eligibility (CGPA, backlogs, branches, batch, 10th/12th), CTC/stipend, deadline
- ✅ TPO approval and tiers (normal / dream / super-dream) from the placement policy
- ✅ 12 eligibility rules with clear pass/fail reasons (including one-offer policy, PPT attendance, resume required, active-application limit)
- ✅ AI job match: skills, resume relevance (embeddings), CGPA fit, practice suggestions for missing skills
- ✅ Multi-round pipelines (tests, interviews, GD) with shortlisting, bulk decisions and automatic progression from test results and interview scorecards
- ✅ Application timeline for students
- ✅ Interview slot generation and booking (no double booking)
- ✅ Offers with letters, accept/decline with deadlines, expiry reminders
- ✅ Off-campus offer reporting and TPO verification
- ✅ Placement certificate PDF with QR code and a public verification page
- ✅ Placement calendar (PPTs, drives, tests, workshops) with attendance codes and check-in

## 4. Coding practice and contests (coding-service, submission-service)
- ✅ Problem bank (practice and private test-only problems, hidden test cases)
- ✅ Judge0 execution in 7 languages, custom input runs, per-test verdicts
- ✅ Elo-style rating per student and per problem, adaptive "next problem" recommendation, weak-topic detection
- ✅ Badges and hall of fame
- ✅ Timed contests with ICPC-style scoring, penalties and a live WebSocket leaderboard (Redis)
- ✅ AI tutor with progressive hints (never in tests or contests)
- ✅ AI code review for accepted solutions (complexity, improvements, edge cases)
- ✅ Plagiarism detection for tests and contests (JPlag + winnowing fingerprints) with side-by-side comparison
- ✅ Monaco code editor

## 5. Aptitude and online tests (assessment-service)
- ✅ MCQ bank (quantitative, logical, verbal, technical) with explanations
- ✅ AI MCQ generation with TPO approval
- ✅ Practice mode with instant feedback and accuracy by topic
- ✅ Tests with randomised pools and option order, time windows, cut-offs, marks
- ✅ Mixed MCQ + coding tests; auto-submit on timeout; results and breakdown
- ✅ Tests linked to job rounds so results move candidates automatically

## 6. AI proctoring (proctoring-service + exam page)
- ✅ Consent, reference photo and identity verification (DJL face embeddings, OpenCV fallback)
- ✅ Webcam analysis in Java: no face, multiple faces, looking away (eyes/profile), excessive movement, camera blocked
- ✅ Mobile phone detection (YOLOv5 via DJL)
- ✅ Browser events: tab switch, window blur, full-screen exit, copy/paste, background noise (Web Audio)
- ✅ Live warnings over WebSocket, warning limit with auto-submit / termination
- ✅ Trust score, flagging and a report with snapshots for recruiters and the TPO
- ✅ Snapshot retention clean-up

## 7. Interviews and preparation (interview-service, ai-helper-service)
- ✅ Structured interview scorecards (weighted criteria) feeding the round results
- ✅ AI mock interviews (technical / HR / mixed) with per-answer scores, ideal points and a final report
- ✅ AI interview question generator by role and skills
- ✅ Interview experiences with anonymity, TPO moderation and upvotes
- ✅ Company prep hub: year-wise selections, round pattern, skills asked, AI summary of experiences

## 8. Communication (notification-service)
- ✅ In-app notifications with live badge and toasts (STOMP WebSocket)
- ✅ HTML e-mails (Thymeleaf) with delivery log
- ✅ Announcements to students / recruiters / TPOs / everyone
- ✅ Event-driven notifications for every important step (applications, results, offers, approvals...)

## 9. Analytics and reports (analytics-service)
- ✅ Placement readiness score (0-100) with breakdown and personalised next steps
- ✅ Placement-chance prediction (Tribuo logistic regression) with explanations; season recording and retraining
- ✅ Student, recruiter, TPO and admin dashboards (Chart.js)
- ✅ Branch-wise placement, CTC statistics, tiers, top recruiters, monthly offers, skills in demand
- ✅ At-risk student detection (nightly refresh)
- ✅ PDF and Excel placement reports
- ✅ Admin system overview with service status from Eureka

## 10. Engineering
- ✅ 13 Spring Boot services + web portal, database per service, Flyway migrations
- ✅ Eureka discovery, gateway routing, Resilience4j circuit breakers and fallbacks
- ✅ RabbitMQ events published after commit
- ✅ OpenAPI (springdoc) for every service
- ✅ Docker Compose for the whole stack (optional self-hosted Judge0)
- ✅ Demo data seeders
- ✅ Unit tests for the core algorithms
