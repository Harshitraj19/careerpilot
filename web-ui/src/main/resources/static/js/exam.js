/*
 * Online test with AI proctoring.
 * The browser only captures: webcam frames are analysed on the server in Java
 * (OpenCV face/eye detection, DJL face matching and phone detection); this script
 * also reports browser events (tab switch, full-screen exit, copy/paste, focus loss,
 * background voices) and shows live warnings.
 */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, $$, esc } = P;

    P.page('student-exam', async () => {
        const testId = $('#test-id').value;
        const frameSeconds = Number($('#frame-seconds').value) || 5;
        const test = await P.get('/api/assessment/tests/' + testId);

        if (test.myAttemptStatus && test.myAttemptStatus !== 'IN_PROGRESS') {
            location.href = '/student/results/' + test.myAttemptId;
            return;
        }

        const state = {
            attempt: null, session: null, stream: null, screen: null, index: 0, ended: false, started: false, timer: null, frameTimer: null,
            checks: new Set(), lastEvent: {}, editors: {}
        };
        const testChecks = test.proctoringChecks || [];
        const screenRequired = test.proctored && testChecks.includes('SCREEN_SHARE_STOPPED');
        const extendedScreen = () => window.screen && window.screen.isExtended === true;

        // ------------------------------------------------------------ intro + consent
        $('#exam-intro').innerHTML = '<div class="row justify-content-center"><div class="col-lg-8"><div class="card"><div class="card-body p-4">' +
            '<h2 class="h4">' + esc(test.title) + '</h2><div class="text-muted mb-3">' + P.title(test.type) + ' · ' + test.durationMinutes + ' minutes · ' +
            (test.mcqCount ? test.mcqCount + ' MCQs ' : '') + (test.codingCount ? test.codingCount + ' coding' : '') + '</div>' +
            (test.instructions ? '<div class="alert alert-light pre-wrap">' + esc(test.instructions) + '</div>' : '') +
            (test.proctored ? '<div class="alert alert-warning"><h6><i class="bi bi-camera-video me-1"></i>This test is proctored</h6><ul class="small mb-0">' +
                '<li>Your webcam' + (test.cameraRequired ? ' (required)' : '') + ' is monitored. Snapshots are kept only when a rule is broken.</li>' +
                '<li>The test runs in full screen. Leaving full screen, switching tabs, copy/paste and other people or phones in view are recorded.</li>' +
                (screenRequired ? '<li>Your <strong>entire screen</strong> is shared for the whole test. Stopping the share or using a second monitor is a violation.</li>' : '') +
                '<li>After ' + test.maxViolations + ' serious warnings ' + (test.autoSubmitOnViolations ? 'the test is submitted automatically.' : 'your attempt is flagged for review.') + '</li></ul></div>' +
                '<div class="row g-3 align-items-center mb-3"><div class="col-md-6"><video id="preview" autoplay muted playsinline class="w-100 rounded bg-dark" style="transform:scaleX(-1)"></video></div>' +
                '<div class="col-md-6"><div class="d-flex align-items-center gap-2 mb-1"><span class="badge rounded-pill text-bg-secondary">1</span><button class="btn btn-outline-primary flex-grow-1" id="cam-on"><i class="bi bi-camera me-1"></i>Turn on camera</button></div>' +
                '<div class="small text-muted mb-3" id="cam-msg">We take a reference photo when you start, to check it is you throughout the test.</div>' +
                (screenRequired ? '<div class="d-flex align-items-center gap-2 mb-1"><span class="badge rounded-pill text-bg-secondary">2</span><button class="btn btn-outline-primary flex-grow-1" id="screen-on"><i class="bi bi-display me-1"></i>Share entire screen</button></div>' +
                    '<div class="small text-muted" id="screen-msg">Choose <strong>Entire screen</strong> in the browser dialog (not a window or tab).</div>' : '') +
                (extendedScreen() ? '<div class="alert alert-warning small mt-2 mb-0"><i class="bi bi-exclamation-triangle me-1"></i>A second monitor is connected. Disconnect it before you start; it is reported as a violation.</div>' : '') +
                '</div></div>' +
                '<div class="form-check mb-3"><input class="form-check-input" type="checkbox" id="consent"><label class="form-check-label" for="consent">I agree to camera and screen monitoring for this test.</label></div>' : '') +
            '<button class="btn btn-primary btn-lg" id="begin"' + (test.proctored ? ' disabled' : '') + '><i class="bi bi-play-fill me-1"></i>' + (test.myAttemptStatus === 'IN_PROGRESS' ? 'Resume test' : 'Start test') + '</button>' +
            '</div></div></div></div>';

        /** Asks for the entire screen; resolves true when a monitor (not a window or tab) is shared. */
        async function shareScreen() {
            try {
                const s = await navigator.mediaDevices.getDisplayMedia({
                    video: { displaySurface: 'monitor' }, audio: false,
                    monitorTypeSurfaces: 'include', selfBrowserSurface: 'exclude', surfaceSwitching: 'exclude'
                });
                const track = s.getVideoTracks()[0];
                const surface = track && track.getSettings ? track.getSettings().displaySurface : 'monitor';
                if (surface && surface !== 'monitor') {
                    s.getTracks().forEach((t) => t.stop());
                    P.toast('Please share your entire screen, not a window or a tab', 'warning');
                    return false;
                }
                state.screen = s;
                track.addEventListener('ended', () => {
                    state.screen = null;
                    $('#screen-status').classList.add('d-none');
                    if (state.started && !state.ended) {
                        browserEvent('SCREEN_SHARE_STOPPED', 'Screen sharing was stopped');
                        P.modal('Screen sharing stopped', '<p>Screen sharing is required for this test. Share your <strong>entire screen</strong> again to continue.</p>',
                            [{ label: 'Share screen again', onClick: async () => (await shareScreen()) ? undefined : false }]);
                    } else if (!state.started) {
                        $('#screen-msg').innerHTML = '<span class="text-danger">Screen sharing stopped. Share your entire screen again.</span>';
                        $('#screen-on').disabled = false;
                        if (state.ready) state.ready();
                    }
                });
                if (state.started) $('#screen-status').classList.remove('d-none');
                return true;
            } catch (e) {
                P.toast('Screen sharing was not allowed. It is required for this test.', 'warning');
                return false;
            }
        }

        if (test.proctored) {
            const ready = () => {
                $('#begin').disabled = !($('#consent').checked && (state.stream || !test.cameraRequired) && (state.screen || !screenRequired));
            };
            state.ready = ready;
            $('#consent').addEventListener('change', ready);
            if (screenRequired) {
                $('#screen-on').addEventListener('click', async () => {
                    if (await shareScreen()) {
                        $('#screen-msg').innerHTML = '<span class="text-success"><i class="bi bi-check-circle"></i> Your entire screen is being shared.</span>';
                        $('#screen-on').disabled = true;
                    }
                    ready();
                });
            }
            $('#cam-on').addEventListener('click', async () => {
                try {
                    const wantAudio = (test.proctoringChecks || []).includes('NOISE');
                    state.stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: wantAudio });
                    $('#preview').srcObject = state.stream;
                    $('#cam-msg').innerHTML = '<span class="text-success"><i class="bi bi-check-circle"></i> Camera is on. Sit facing the screen in good light.</span>';
                    $('#cam-on').disabled = true;
                } catch (e) {
                    $('#cam-msg').innerHTML = '<span class="text-danger">Camera access was blocked. Allow the camera in your browser settings and try again.</span>';
                }
                ready();
            });
            ready();
        }

        $('#begin').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try {
                state.attempt = await P.post('/api/assessment/tests/' + testId + '/start');
                if (test.proctored) await startProctoring();
                await enterFullscreen();
                $('#exam-intro').classList.add('d-none');
                $('#exam').classList.remove('d-none');
                $('#exam-title').textContent = state.attempt.testTitle;
                state.started = true;
                document.body.classList.add('exam-mode');
                if (state.screen) $('#screen-status').classList.remove('d-none');
                if (extendedScreen()) browserEvent('MULTIPLE_MONITORS', 'A second monitor is connected');
                bindExamEvents();
                renderNav();
                show(0);
                startTimer();
            } catch (e) { P.fail(e); }
        }));

        // ------------------------------------------------------------ proctoring

        function capture(w, h) {
            const video = $('#cam').srcObject ? $('#cam') : $('#preview');
            const c = $('#snap');
            c.width = w || 320;
            c.height = h || 240;
            c.getContext('2d').drawImage(video, 0, 0, c.width, c.height);
            return c.toDataURL('image/jpeg', 0.7);
        }

        async function startProctoring() {
            const s = state.attempt.proctoring || {};
            (s.checks || []).forEach((c) => state.checks.add(c));
            let reference = null;
            if (state.stream) {
                $('#cam').srcObject = state.stream;
                $('#cam-box').classList.remove('d-none');
                await new Promise((r) => setTimeout(r, 400));
                reference = capture(480, 360);
            }
            state.session = await P.post('/api/proctoring/sessions', { attemptId: state.attempt.attemptId, consent: true, referencePhoto: reference });
            updateProctor(state.session);
            if (state.stream && state.session.cameraAnalysisAvailable !== false) {
                state.frameTimer = setInterval(sendFrame, frameSeconds * 1000);
            }
            if (state.checks.has('NOISE') && state.stream && state.stream.getAudioTracks().length) listenForNoise();
            P.subscribe('/user/queue/proctoring', (w) => {
                if (state.session && w.sessionId === state.session.sessionId) warn(w.message, w.violationCount, w.maxViolations);
            });
        }

        async function sendFrame() {
            if (state.ended || !state.session) return;
            const video = $('#cam');
            if (!video.videoWidth) return;
            try {
                const st = await P.post('/api/proctoring/sessions/' + state.session.sessionId + '/frames', { image: capture() });
                if (st) updateProctor(st);
            } catch (e) {
                if (e.status === 409 || e.status === 410) terminated();
            }
        }

        async function browserEvent(type, detail) {
            if (state.ended || !state.session || !state.checks.has(type)) return;
            const now = Date.now();
            if (state.lastEvent[type] && now - state.lastEvent[type] < 3000) return; // debounce
            state.lastEvent[type] = now;
            try {
                const st = await P.post('/api/proctoring/sessions/' + state.session.sessionId + '/events', { type, detail: detail || null });
                updateProctor(st);
            } catch (e) { /* offline: ignore */ }
        }

        function updateProctor(st) {
            state.session = Object.assign(state.session || {}, st);
            $('#proctor-state').textContent = st.status === 'ACTIVE' ? 'Proctored' : P.title(st.status);
            $('#violations').textContent = 'Warnings: ' + st.violationCount + ' / ' + st.maxViolations;
            const latest = (st.recentWarnings || [])[0];
            if (latest && latest.id !== state.lastWarningId) {
                state.lastWarningId = latest.id;
                if (Date.now() - new Date(latest.time).getTime() < 30000) warn(latest.message, st.violationCount, st.maxViolations);
            }
            if (st.status === 'TERMINATED') terminated();
        }

        function warn(message, count, max) {
            const b = $('#warning-banner');
            b.innerHTML = '<i class="bi bi-exclamation-triangle-fill me-1"></i><strong>Warning ' + count + ' of ' + max + ':</strong> ' + esc(message);
            b.classList.remove('d-none', 'warning-flash');
            void b.offsetWidth;
            b.classList.add('warning-flash');
            clearTimeout(state.warnTimer);
            state.warnTimer = setTimeout(() => b.classList.add('d-none'), 8000);
        }

        function listenForNoise() {
            try {
                const ctx = new (window.AudioContext || window.webkitAudioContext)();
                const src = ctx.createMediaStreamSource(state.stream);
                const an = ctx.createAnalyser();
                an.fftSize = 1024;
                src.connect(an);
                const buf = new Float32Array(an.fftSize);
                let loud = 0;
                setInterval(() => {
                    an.getFloatTimeDomainData(buf);
                    let sum = 0;
                    for (let i = 0; i < buf.length; i++) sum += buf[i] * buf[i];
                    const rms = Math.sqrt(sum / buf.length);
                    loud = rms > 0.06 ? loud + 1 : Math.max(0, loud - 1);
                    if (loud >= 6) { // about 3 s of sustained voice-level sound
                        loud = 0;
                        if (!state.lastEvent.NOISE || Date.now() - state.lastEvent.NOISE > 30000) browserEvent('NOISE', 'Sustained sound level ' + rms.toFixed(2));
                    }
                }, 500);
            } catch (e) { /* audio not available */ }
        }

        async function enterFullscreen() {
            if (!test.proctored) return;
            try { await document.documentElement.requestFullscreen(); } catch (e) { /* user may deny */ }
        }

        function bindExamEvents() {
            document.addEventListener('visibilitychange', () => { if (document.hidden) browserEvent('TAB_SWITCH', 'Tab hidden'); });
            window.addEventListener('blur', () => setTimeout(() => { if (!document.hidden) browserEvent('WINDOW_BLUR', 'Window lost focus'); }, 300));
            document.addEventListener('fullscreenchange', () => {
                if (!document.fullscreenElement && !state.ended && test.proctored) {
                    browserEvent('FULLSCREEN_EXIT', 'Left full screen');
                    P.modal('Full screen required', '<p>Please return to full-screen mode to continue your test.</p>',
                        [{ label: 'Return to full screen', onClick: () => { enterFullscreen(); } }]);
                }
            });
            ['copy', 'cut', 'paste'].forEach((t) => document.addEventListener(t, (e) => {
                if (state.ended) return;
                e.preventDefault();
                browserEvent('COPY_PASTE', t);
                P.toast('Copy and paste are disabled during the test', 'warning');
            }));
            document.addEventListener('contextmenu', (e) => { if (!state.ended) e.preventDefault(); });
            window.addEventListener('beforeunload', (e) => { if (!state.ended) { e.preventDefault(); e.returnValue = ''; } });
            $('#prev').addEventListener('click', () => show(state.index - 1));
            $('#next').addEventListener('click', () => show(state.index + 1));
            $('#finish').addEventListener('click', async () => {
                const unanswered = state.attempt.questions.filter((q) => q.kind === 'MCQ' && q.selectedIndex == null).length;
                if (await P.confirm('Submit test', (unanswered ? unanswered + ' MCQ(s) are unanswered. ' : '') + 'You cannot change answers after submitting.', 'Submit', 'btn-danger')) finish();
            });
        }

        // ------------------------------------------------------------ questions

        function renderNav() {
            $('#q-nav').innerHTML = state.attempt.questions.map((q, i) => {
                const done = q.kind === 'MCQ' ? q.selectedIndex != null : q.codingPassed != null && q.codingPassed > 0;
                return '<button class="btn btn-outline-secondary ' + (done ? 'answered' : '') + (i === state.index ? ' border-primary border-2' : '') + '" data-i="' + i + '" title="' +
                    (q.kind === 'CODING' ? 'Coding' : 'MCQ') + '">' + (i + 1) + '</button>';
            }).join('');
            P.$$('[data-i]', $('#q-nav')).forEach((b) => b.addEventListener('click', () => show(Number(b.dataset.i))));
        }

        async function show(i) {
            const qs = state.attempt.questions;
            if (i < 0 || i >= qs.length) return;
            state.index = i;
            renderNav();
            $('#prev').disabled = i === 0;
            $('#next').disabled = i === qs.length - 1;
            const q = qs[i];
            if (q.kind === 'MCQ') {
                $('#q-body').innerHTML = '<div class="small text-muted mb-2">Question ' + (i + 1) + ' of ' + qs.length + ' · ' + esc(q.maxMarks) + ' mark(s)</div>' +
                    '<div class="fs-6 mb-3 pre-wrap user-select-none">' + esc(q.text) + '</div>' +
                    q.options.map((o, k) => '<div class="option-card user-select-none ' + (q.selectedIndex === k ? 'selected' : '') + '" data-k="' + k + '"><strong class="me-2">' +
                        String.fromCharCode(65 + k) + '.</strong>' + esc(o) + '</div>').join('');
                P.$$('.option-card', $('#q-body')).forEach((el) => el.addEventListener('click', async () => {
                    const k = Number(el.dataset.k);
                    try {
                        await P.post('/api/assessment/attempts/' + state.attempt.attemptId + '/answers', { attemptQuestionId: q.attemptQuestionId, selectedIndex: k });
                        q.selectedIndex = k;
                        P.$$('.option-card', $('#q-body')).forEach((o) => o.classList.toggle('selected', o === el));
                        renderNav();
                    } catch (e) { handleError(e); }
                }));
            } else {
                await showCoding(q, i);
            }
        }

        async function showCoding(q, i) {
            $('#q-body').innerHTML = P.spinner();
            const st = await P.get('/api/assessment/attempts/' + state.attempt.attemptId + '/coding/' + encodeURIComponent(q.codingQuestionId));
            const langs = state.langs || (state.langs = await P.get('/api/submissions/languages'));
            $('#q-body').innerHTML = '<div class="small text-muted mb-2">Question ' + (i + 1) + ' · coding · ' + esc(q.maxMarks) + ' marks' +
                (q.codingTotal ? ' · best so far ' + q.codingPassed + '/' + q.codingTotal + ' tests' : '') + '</div><div class="row g-3"><div class="col-xl-5 problem-statement user-select-none">' +
                P.statement(st) + '</div><div class="col-xl-7"><div class="d-flex gap-2 mb-2"><select class="form-select form-select-sm" id="x-lang" style="width:auto">' +
                langs.map((l) => '<option value="' + l.id + '" data-code="' + l.code + '">' + esc(l.name) + '</option>').join('') + '</select>' +
                '<button class="btn btn-sm btn-outline-secondary ms-auto" id="x-run">Run with sample</button><button class="btn btn-sm btn-primary" id="x-submit">Submit code</button></div>' +
                '<div class="editor-box" id="x-editor" style="height:380px"></div><pre class="form-control mono mt-2 small" id="x-out" style="height:80px;overflow:auto"></pre><div id="x-verdict"></div></div></div>';
            const code = () => $('#x-lang').selectedOptions[0].dataset.code;
            const key = q.codingQuestionId;
            const saved = state.editors[key] || {};
            if (saved.lang) $('#x-lang').value = saved.lang;
            const ed = await P.createEditor($('#x-editor'), saved.src || P.template(code()), code(),
                { noPaste: true, onPaste: () => browserEvent('COPY_PASTE', 'paste in editor') });
            const keep = () => { state.editors[key] = { lang: $('#x-lang').value, src: ed.getValue() }; };
            ed.onChange(keep);
            $('#x-lang').addEventListener('change', () => { ed.setLanguage(code()); ed.setValue(P.template(code())); keep(); });
            $('#x-run').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
                try {
                    const r = await P.post('/api/submissions/run', { languageId: Number($('#x-lang').value), sourceCode: ed.getValue(), stdin: st.sampleInput || '' });
                    $('#x-out').textContent = (r.compileOutput || '') + (r.stdout || '') + (r.stderr ? '\n' + r.stderr : '') + '\n[' + (r.status || '') + ']';
                } catch (e) { handleError(e); }
            }));
            $('#x-submit').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
                try {
                    keep();
                    const sub = await P.post('/api/submissions', { questionId: key, languageId: Number($('#x-lang').value), sourceCode: ed.getValue(), mode: 'ASSESSMENT', attemptId: state.attempt.attemptId });
                    $('#x-verdict').innerHTML = '<div class="small mt-2"><span class="spinner-border spinner-border-sm"></span> Judging...</div>';
                    const done = await P.waitForVerdict(sub.id);
                    $('#x-verdict').innerHTML = '<div class="mt-2">' + P.verdictHtml(done, null) + '</div>';
                    state.attempt = await P.get('/api/assessment/attempts/' + state.attempt.attemptId);
                    renderNav();
                } catch (e) { handleError(e); }
            }));
        }

        // ------------------------------------------------------------ timer + finish

        function startTimer() {
            const deadline = Date.now() + state.attempt.remainingSeconds * 1000;
            const tick = () => {
                const s = Math.max(0, Math.round((deadline - Date.now()) / 1000));
                const t = $('#timer');
                t.textContent = String(Math.floor(s / 3600)).padStart(2, '0') + ':' + String(Math.floor((s % 3600) / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
                t.classList.toggle('low', s < 300);
                if (s === 0) { P.toast('Time is up - submitting your test', 'warning'); finish(); }
            };
            tick();
            state.timer = setInterval(tick, 1000);
        }

        function stopAll() {
            state.ended = true;
            clearInterval(state.timer);
            clearInterval(state.frameTimer);
            if (state.stream) state.stream.getTracks().forEach((t) => t.stop());
            if (state.screen) state.screen.getTracks().forEach((t) => t.stop());
            document.body.classList.remove('exam-mode');
            $('#cam-box').classList.add('d-none');
            if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
        }

        async function finish() {
            if (state.ended) return;
            stopAll();
            try {
                await P.post('/api/assessment/attempts/' + state.attempt.attemptId + '/submit');
            } catch (e) { /* may already be auto-submitted */ }
            if (state.session) {
                try { await P.post('/api/proctoring/sessions/' + state.session.sessionId + '/end'); } catch (e) { /* ignore */ }
            }
            location.href = '/student/results/' + state.attempt.attemptId;
        }

        function terminated() {
            if (state.ended) return;
            stopAll();
            P.modal('Test ended', '<p>Your test was ended because of repeated proctoring violations. Your answers so far have been submitted and the placement office can review the report.</p>',
                [{ label: 'See result', onClick: () => { location.href = '/student/results/' + state.attempt.attemptId; } }]);
        }

        function handleError(e) {
            if (e.status === 409 || e.status === 410 || e.code === 'ATTEMPT_CLOSED') {
                terminated();
                return;
            }
            P.fail(e);
        }
    });
})();
