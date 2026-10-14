# CareerPilot — Navigate Your Future. Own Your Career.

CareerPilot is a complete campus placement and training platform written in **Java 21 / Spring Boot 3.5**,
including all of its AI: language-model features with **Spring AI**, embeddings and object detection with
**DJL**, webcam proctoring with **OpenCV**, plagiarism detection with **JPlag**, and placement prediction with
**Oracle Tribuo**.

It merges and extends three earlier projects: the preparation-focused *Placify* (MERN), the placement-drive
workflow of *GradPlacifyr*, and the Spring Boot microservices of *Placify (coding interviews)*.

| Portal | Who | Highlights |
|---|---|---|
| **Student** | registration number login | readiness dashboard and ML placement chance, job matching and eligibility, round-by-round tracking, slot booking, offers and QR certificate, coding practice with AI tutor, contests, aptitude practice, proctored tests, resume ATS checker, AI mock interviews, company prep hub, interview experiences, help desk to raise queries with the placement office |
| **Recruiter** | official e-mail, TPO-approved | job postings with rounds, AI-ranked applicants, shortlisting, tests with proctoring reports, interview scorecards, slots, offers, plagiarism checks, hiring funnel |
| **Placement office (TPO)** | employee ID | student import (CSV/Excel), company approvals, job approval and tiers, placement policy, offer verification, calendar and attendance, MCQ bank with AI generation, coding questions, contests, announcements, experience moderation, student query help desk (reply and resolve), at-risk students, PDF/Excel reports, model training |
| **Admin** | username | user management, TPO/admin creation, system overview, audit log and login history |

---

## Architecture

```
Browser ── web-ui (:3000, Thymeleaf + BFF) ── API gateway (:8080) ──┬─ auth          (:8081)
   └──── WebSocket (one-time ticket) ──────────────┘                ├─ profile       (:8082)
                                                                     ├─ placement     (:8083)
  Eureka (:8761) · MySQL (DB per service) · Redis · RabbitMQ         ├─ coding        (:8084)
  Judge0 (code execution) · Gemini/Ollama (LLM via Spring AI)        ├─ submission    (:8085)
                                                                     ├─ assessment    (:8086)
                                                                     ├─ proctoring    (:8087)
                                                                     ├─ ai-helper     (:8088)
                                                                     ├─ interview     (:8089)
                                                                     ├─ notification  (:8090)
                                                                     └─ analytics     (:8091)
```

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · API: [docs/API.md](docs/API.md) · Features: [docs/FEATURES.md](docs/FEATURES.md)

## Quick start (Docker)

Requirements: Docker Desktop (or Docker Engine + Compose v2), about 8 GB RAM for the full stack.

```bash
cp .env.example .env          # set AI_API_KEY and JUDGE0_* for AI and code execution
docker compose up -d --build  # first build takes a while (Gradle downloads dependencies)
```

Open **http://localhost:3000**. With `DEMO_DATA=true` (the default in `.env.example`) these accounts exist:

| Portal | Login | Password |
|---|---|---|
| Student | `22BCE1001` … `22BIT1006` | `Student@123` |
| Recruiter | `hr@technova.demo`, `careers@finedge.demo` (and 10 more companies, e.g. `campus@cloudpeak.demo`) | `Company@123` |
| TPO | `TPO1001` | `Tpo@12345` |
| Admin | `admin` | `Admin@12345` (must be changed at first login) |

Demo data also includes a placement season for the 2027 batch: 17 drives from 12 companies (full-time, internship and internship +
PPO roles, each with a job description, salary breakdown and selection rounds), a calendar of pre-placement talks,
tests, interviews and workshops, a proctored online test linked to every drive's first round plus a campus mock test,
132 aptitude questions (11 per topic and difficulty), 10 practice problems and 4 test-only problems with hidden test
cases, a generated PDF resume for every demo student, and sample interview experiences.

**New accounts** – students create their own account on the login page (*Create a student account*: registration
number, details, e-mail code and their own password) or are added by the TPO (*Students → Add student* or an
Excel/CSV import, with a temporary password changed at first login). Recruiters register with *Register your company*
and can log in once the TPO approves them. Admins create TPO and admin accounts. While e-mail is switched off,
`EXPOSE_OTP=true` shows the verification code on screen; set it to `false` once real e-mail is configured.

**Try the main flow:** log in as a student, apply to a drive under *Job openings*, then open *Tests*: the drive's
online test is unlocked. Starting it requires the camera, sharing the entire screen and full-screen mode; the result
moves the application to the next round or rejects it below the cut-off.

**AI** – every AI feature works without any key through CareerPilot's built-in engine (`ai-helper-service`, package
`offline`): resume parsing and auto-fill, resume feedback, the mock interviewer and answer scoring, the coding tutor's
progressive hints, code review, MCQ generation, interview questions and experience summaries. For language-model
answers, create a free Gemini key at <https://aistudio.google.com/app/apikey> and set `AI_API_KEY` (any
OpenAI-compatible endpoint works, e.g. a local Ollama: see `.env.example`); the built-in engine then remains the
fallback. The mock interview can speak its questions and take spoken answers (browser speech APIs, Chrome or Edge).

**Code execution** – with no Judge0 configured (`CODE_RUNNER=auto`), `submission-service` compiles and runs code
itself (Java, Python, C, C++ and JavaScript - whichever toolchains are installed; the Docker image includes them) with a
time limit, and judges every public and hidden test case. This is meant for local use and demos: it does not sandbox
the code. For untrusted users use Judge0 CE on RapidAPI (`JUDGE0_RAPIDAPI_KEY`) or run it yourself with
`docker compose --profile judge0 up -d` and set `JUDGE0_URL=http://judge0-server:2358`.

**E-mail** – with `MAIL_ENABLED=false` every e-mail, including OTP codes, is printed in the
`notification-service` log (`docker compose logs -f notification-service`). Set SMTP values to send real mail.

The first start of `proctoring-service` and `ai-helper-service` downloads the DJL PyTorch runtime and models
(cached in the `djl-cache` volume). If they cannot be downloaded, face matching falls back to an OpenCV matcher, phone detection is skipped, and the other camera checks keep working.

## Running on Windows without Docker

Requirements: JDK 21, about 2 GB of free disk space and 8 GB of RAM. Optional for code execution: Python, GCC (MinGW)
and Node.js on the PATH (Java always works).

```powershell
copy .env.example .env
powershell -ExecutionPolicy Bypass -File scripts\setup-local.ps1   # once: portable MySQL, Redis, Erlang, RabbitMQ
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1   # builds if needed, starts everything (~2 minutes)
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1    # -All also stops MySQL, Redis and RabbitMQ
```

The runtime lives in `%USERPROFILE%\placify-runtime` (logs in its `logs` folder), so nothing is installed system-wide.

### Sharing a public link

```powershell
powershell -ExecutionPolicy Bypass -File scripts\share-online.ps1
```

Opens two Cloudflare quick tunnels (portal and gateway, no account needed), restarts the gateway, notification
service and web UI with the public addresses, and redeploys the static front page in `deploy/vercel` so that
https://careerpilot-portal.vercel.app opens the portal. The links work while the laptop is on and change on every run;
`stop-local.ps1` closes the tunnels.

Free quick tunnels are deleted when the laptop sleeps or the network drops. For a demo, run
`scripts\keep-online.ps1` instead: it shares the portal, checks the public link every minute, reopens the tunnels and
updates the Vercel page when needed, and keeps Windows awake while its window is open.

## Running without Docker (manual)

1. Install JDK 21. Start MySQL 8, Redis 7 (6.2 or newer) and RabbitMQ 3/4 locally (or
   `docker compose up -d mysql redis rabbitmq`), create the `placify` database user from `.env` and run
   `docker/mysql/init.sql` once.
2. Build: `./gradlew build` (runs the unit tests) or `./gradlew bootJar -x test`.
3. Start in this order, each in its own terminal (environment variables from `.env.example` as needed):

```bash
./gradlew :discovery-server:bootRun
./gradlew :gateway:bootRun
./gradlew :auth-service:bootRun        # then profile, placement, coding, submission, assessment,
                                        # proctoring, ai-helper, interview, notification, analytics
./gradlew :web-ui:bootRun
```

Each service applies its own Flyway migrations at start-up.

## Project layout

```
placify/
├── common/                  shared library: security filter, errors, events, internal client, audit, storage
├── services/
│   ├── discovery-server/    Eureka
│   ├── gateway/             Spring Cloud Gateway: JWT + Redis session, rate limit, circuit breakers, WebSocket route
│   ├── auth-service/        portals, captcha, OTP/2FA, lockout, single session, student import, company approval
│   ├── profile-service/     profiles, resume parsing, ATS scoring
│   ├── placement-service/   jobs, eligibility rules, rounds, slots, offers, certificates, calendar, policy
│   ├── coding-service/      problems, ratings, recommendations, contests, badges
│   ├── submission-service/  Judge0, verdicts, AI review, plagiarism (JPlag + winnowing)
│   ├── assessment-service/  MCQ bank, tests, attempts, grading
│   ├── proctoring-service/  OpenCV + DJL camera analysis, browser events, trust score
│   ├── ai-helper-service/   Spring AI features + DJL embeddings
│   ├── interview-service/   scorecards, experiences, prep hub
│   ├── notification-service/ notifications, e-mail, announcements, WebSocket
│   └── analytics-service/   dashboards, readiness, Tribuo prediction, reports, audit log
├── web-ui/                  Thymeleaf + Bootstrap 5 portal with a BFF proxy
├── docker/                  Dockerfile and MySQL init script
├── docs/                    architecture, API reference, feature list
└── docker-compose.yml
```

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Cloud 2025 (Gateway, Eureka, LoadBalancer, Resilience4j) · Spring Security ·
Spring Data JPA + Flyway · MySQL 8 · Redis · RabbitMQ · Spring AI 1.0 · DJL 0.31 (PyTorch, HuggingFace tokenizers) ·
OpenCV 4.9 · JPlag 6 · Tribuo 4.3 · Apache Tika / PDFBox · OpenPDF · Apache POI · ZXing · Thymeleaf · Bootstrap 5 ·
Monaco editor · Chart.js · STOMP over WebSocket · Docker Compose · JUnit 5 / AssertJ.

## Tests

```bash
./gradlew test
```

Unit tests cover the core algorithms: Elo ratings, verdict mapping, winnowing plagiarism similarity, proctoring
trust score, ATS scoring, readiness score, the Tribuo placement model, MCQ validation, similarity fallback,
interview scoring, CSV safety and statistics, the local code runner, and the built-in AI engine (answer scoring,
resume parsing, MCQ generation and complexity estimation).

## Security checklist for deployment

* Generate new `JWT_SECRET`, `GATEWAY_SECRET`, `INTERNAL_SECRET` and database passwords; never commit `.env`.
* Serve the portal over HTTPS and set `COOKIE_SECURE=true`; use `wss://` for `PUBLIC_WS_URL`.
* Set `EXPOSE_TEMP_PASSWORDS=false` and `DEMO_DATA=false` in production.
* Only expose ports 3000 (portal) and 8080 (gateway, needed for the WebSocket); keep the rest internal.
* Change the admin password at first login (the portal enforces it).
