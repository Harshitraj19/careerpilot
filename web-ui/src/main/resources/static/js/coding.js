/* Coding practice, problem editor (Monaco), submissions, contests and hall of fame. */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, esc } = P;

    const MONACO = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.52.0/min/vs';
    const TEMPLATES = {
        java: 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        // write your code here\n    }\n}\n',
        cpp: '#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n    // write your code here\n    return 0;\n}\n',
        c: '#include <stdio.h>\n\nint main(void) {\n    // write your code here\n    return 0;\n}\n',
        python: 'import sys\n\ndef main():\n    data = sys.stdin.read().split()\n    # write your code here\n\nmain()\n',
        javascript: "const lines = require('fs').readFileSync(0, 'utf8').trim().split('\\n');\n// write your code here\n",
        go: 'package main\n\nimport "fmt"\n\nfunc main() {\n\tvar n int\n\tfmt.Scan(&n)\n}\n',
        rust: 'use std::io::{self, Read};\n\nfn main() {\n    let mut s = String::new();\n    io::stdin().read_to_string(&mut s).unwrap();\n}\n'
    };
    const MONACO_LANG = { java: 'java', cpp: 'cpp', c: 'c', python: 'python', javascript: 'javascript', go: 'go', rust: 'rust' };

    /**
     * Creates a code editor (Monaco from the CDN, or a plain textarea if it cannot load).
     * Returns {getValue, setValue, setLanguage, onChange}.
     */
    P.createEditor = function (container, value, lang, opts) {
        opts = opts || {};
        return new Promise((resolve) => {
            const fallback = () => {
                container.innerHTML = '<textarea class="form-control mono h-100 border-0" spellcheck="false"></textarea>';
                const ta = container.querySelector('textarea');
                ta.value = value || '';
                if (opts.noPaste) blockPaste(ta);
                resolve({ getValue: () => ta.value, setValue: (v) => { ta.value = v; }, setLanguage: () => {}, onChange: (fn) => ta.addEventListener('input', fn) });
            };
            if (window.monaco) return create();
            const s = document.createElement('script');
            s.src = MONACO + '/loader.js';
            s.onerror = fallback;
            s.onload = () => {
                window.MonacoEnvironment = {
                    getWorkerUrl: () => URL.createObjectURL(new Blob(["self.MonacoEnvironment={baseUrl:'" + MONACO + "/../'};importScripts('" + MONACO + "/base/worker/workerMain.js');"], { type: 'text/javascript' }))
                };
                window.require.config({ paths: { vs: MONACO } });
                window.require(['vs/editor/editor.main'], create, fallback);
            };
            document.head.appendChild(s);

            function create() {
                const ed = monaco.editor.create(container, {
                    value: value || '', language: MONACO_LANG[String(lang || '').toLowerCase()] || 'plaintext', automaticLayout: true, minimap: { enabled: false },
                    fontSize: 14, scrollBeyondLastLine: false, tabSize: 4
                });
                if (opts.noPaste) {
                    ed.onDidPaste(() => { if (opts.onPaste) opts.onPaste(); });
                    container.addEventListener('paste', (e) => { e.preventDefault(); e.stopPropagation(); if (opts.onPaste) opts.onPaste(); }, true);
                }
                resolve({
                    getValue: () => ed.getValue(),
                    setValue: (v) => ed.setValue(v),
                    setLanguage: (l) => monaco.editor.setModelLanguage(ed.getModel(), MONACO_LANG[String(l || '').toLowerCase()] || 'plaintext'),
                    onChange: (fn) => ed.onDidChangeModelContent(fn)
                });
            }
        });
    };

    function blockPaste(el) {
        el.addEventListener('paste', (e) => e.preventDefault());
    }

    P.template = (code) => TEMPLATES[String(code || '').toLowerCase()] || '';

    /** Renders a problem statement. */
    P.statement = function (q) {
        const sec = (title, body, pre) => (body ? '<h6 class="mt-3">' + title + '</h6>' + (pre ? '<pre class="bg-light p-2 rounded mono mb-0">' + esc(body) + '</pre>' : '<div class="pre-wrap">' + esc(body) + '</div>') : '');
        return '<h4 class="mb-1">' + esc(q.title) + '</h4><div class="mb-2">' + P.badge(q.difficulty) + ' ' + (q.rating ? '<span class="badge text-bg-light">rating ' + q.rating + '</span> ' : '') +
            P.chips(q.tags) + '</div><div class="small text-muted mb-2">Time limit ' + esc(q.timeLimit || 2) + 's · Memory ' + esc(q.memoryLimit || 256) + ' MB' +
            (q.solvedCount != null ? ' · solved by ' + q.solvedCount : '') + '</div>' +
            '<div class="pre-wrap">' + esc(q.description) + '</div>' + sec('Input format', q.inputFormat) + sec('Output format', q.outputFormat) + sec('Constraints', q.constraints) +
            sec('Sample input', q.sampleInput, true) + sec('Sample output', q.sampleOutput, true) + sec('Explanation', q.explanation);
    };

    /** Polls a submission until it is judged. */
    P.waitForVerdict = async function (id, onUpdate) {
        for (let i = 0; i < 90; i++) {
            const s = await P.get('/api/submissions/' + id);
            if (onUpdate) onUpdate(s);
            if (s.status !== 'pending' && s.status !== 'running') return s;
            await new Promise((r) => setTimeout(r, i < 10 ? 1000 : 2000));
        }
        throw new Error('Judging is taking longer than usual - check My submissions later.');
    };

    P.verdictHtml = function (s, results) {
        const ok = s.status === 'accepted';
        return '<div class="d-flex justify-content-between align-items-center"><h5 class="mb-0 ' + (ok ? 'verdict-accepted' : 'verdict-bad') + '">' +
            (ok ? '<i class="bi bi-check-circle-fill"></i> Accepted' : '<i class="bi bi-x-circle-fill"></i> ' + P.title(s.status)) + '</h5>' +
            '<span class="small text-muted">' + s.testcasesPassed + ' / ' + s.totalTestcases + ' tests · ' + (s.executionTime != null ? s.executionTime + ' s' : '') +
            (s.memoryUsed != null ? ' · ' + s.memoryUsed + ' KB' : '') + '</span></div>' +
            (s.compileOutput ? '<pre class="bg-light p-2 mt-2 small mono">' + esc(s.compileOutput) + '</pre>' : '') +
            (results && results.length ? '<div class="d-flex flex-wrap gap-1 mt-2">' + results.map((r, i) => '<span class="badge ' + (r.status === 'accepted' ? 'text-bg-success' : 'text-bg-danger') +
                '" title="' + esc(P.title(r.status)) + (r.errorMessage ? ': ' + esc(r.errorMessage) : '') + '">#' + (i + 1) + (r.hidden ? ' (hidden)' : '') + '</span>').join('') + '</div>' : '');
    };

    // ================================================================= practice list

    P.page('student-practice', async () => {
        let page = 0;
        const tags = await P.get('/api/coding/tags');
        $('#tag').innerHTML += tags.map((t) => '<option>' + esc(t) + '</option>').join('');
        const load = async () => {
            const res = await P.get('/api/coding/questions' + P.qs({ q: $('#q').value, difficulty: $('#difficulty').value, tag: $('#tag').value, page }));
            P.table('#problems', [
                { label: '', render: (q) => (q.solvedByMe ? '<i class="bi bi-check-circle-fill text-success"></i>' : '') },
                { label: 'Problem', render: (q) => '<a class="fw-semibold" href="/student/practice/' + encodeURIComponent(q.id) + '">' + esc(q.title) + '</a><div>' + P.chips((q.tags || []).slice(0, 4)) + '</div>' },
                { label: 'Difficulty', render: (q) => P.badge(q.difficulty) },
                { label: 'Rating', render: (q) => q.rating },
                { label: 'Solved by', render: (q) => q.solvedCount + (q.attemptCount ? ' <span class="small text-muted">(' + Math.round(q.solvedCount * 100 / q.attemptCount) + '%)</span>' : '') }
            ], P.content(res), 'No problems found');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        let t;
        $('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 300); });
        ['#difficulty', '#tag'].forEach((s) => $(s).addEventListener('change', () => { page = 0; load(); }));
        load();

        const st = await P.get('/api/coding/practice/stats');
        $('#practice-stats').innerHTML = [['Rating', st.rating, 'bi-graph-up', 'bg-soft-blue'], ['Solved', st.solved, 'bi-check2-circle', 'bg-soft-green'],
            ['Easy / Med / Hard', st.easySolved + ' / ' + st.mediumSolved + ' / ' + st.hardSolved, 'bi-bar-chart', 'bg-soft-purple'],
            ['Rank', st.rank ? '#' + st.rank : '-', 'bi-trophy', 'bg-soft-amber']].map(([l, v, i, c]) =>
            '<div class="col-6 col-xl-3"><div class="stat d-flex justify-content-between"><div><div class="label">' + l + '</div><div class="value">' + esc(v) + '</div></div><span class="icon ' + c + '"><i class="bi ' + i + '"></i></span></div></div>').join('');
        $('#weak').innerHTML = (st.weakTags || []).length ? P.chips(st.weakTags, 'bad') + '<div class="small text-muted mt-2">Pick these tags in the filter to practise.</div>' : '<span class="text-muted small">Solve a few problems to find your weak topics.</span>';
        P.get('/api/coding/practice/next').then((n) => {
            $('#next').innerHTML = n && n.question ? '<a class="fw-semibold" href="/student/practice/' + encodeURIComponent(n.question.id) + '">' + esc(n.question.title) + '</a> ' + P.badge(n.question.difficulty) +
                '<div class="small text-muted mt-1">' + esc(n.reason) + '</div>' : '<span class="text-muted small">Nothing to recommend right now.</span>';
        });
        P.get('/api/coding/badges/me').then((b) => {
            $('#badges').innerHTML = b.length ? b.map((x) => '<div class="d-flex gap-2 mb-2"><i class="bi bi-award-fill text-warning fs-4"></i><div><div class="fw-semibold">' + esc(x.title) +
                '</div><div class="small text-muted">' + esc(x.description) + '</div></div></div>').join('') : '<span class="text-muted small">Solve problems to earn badges.</span>';
        });
    });

    // ================================================================= problem + editor

    P.page('student-problem', async () => {
        const qid = $('#question-id').value;
        const contestId = $('#contest-id').value || null;
        const q = await P.get(contestId ? '/api/coding/contests/' + contestId + '/questions/' + encodeURIComponent(qid) : '/api/coding/questions/' + encodeURIComponent(qid));
        $('#statement').innerHTML = (contestId ? '<a class="small" href="/student/contests/' + contestId + '">&larr; Back to contest</a>' : '<a class="small" href="/student/practice">&larr; All problems</a>') + '<div class="mt-2">' + P.statement(q) + '</div>';
        document.title = q.title + ' · CareerPilot';
        if (contestId) $('#tutor-card').remove();

        const langs = await P.get('/api/submissions/languages');
        const store = 'placify.code.' + qid;
        let saved = {};
        try { saved = JSON.parse(localStorage.getItem(store) || '{}'); } catch (e) { saved = {}; }
        $('#language').innerHTML = langs.map((l) => '<option value="' + l.id + '" data-code="' + l.code + '">' + esc(l.name) + '</option>').join('');
        if (saved.lang) $('#language').value = saved.lang;
        const code = () => $('#language').selectedOptions[0].dataset.code;
        const editor = await P.createEditor($('#editor'), (saved.src && saved.src[code()]) || P.template(code()), code());
        const persist = () => {
            saved.src = saved.src || {};
            saved.src[code()] = editor.getValue();
            saved.lang = $('#language').value;
            try { localStorage.setItem(store, JSON.stringify(saved)); } catch (e) { /* storage full */ }
        };
        let tm;
        editor.onChange(() => { clearTimeout(tm); tm = setTimeout(persist, 800); });
        $('#language').addEventListener('change', () => {
            editor.setLanguage(code());
            editor.setValue((saved.src && saved.src[code()]) || P.template(code()));
            persist();
        });
        $('#reset-code').addEventListener('click', async () => {
            if (await P.confirm('Reset code', 'Replace your code with the starter template?', 'Reset')) editor.setValue(P.template(code()));
        });
        $('#stdin').value = q.sampleInput || '';

        $('#run').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try {
                const r = await P.post('/api/submissions/run', { languageId: Number($('#language').value), sourceCode: editor.getValue(), stdin: $('#stdin').value });
                $('#stdout').textContent = (r.compileOutput || '') + (r.stdout || '') + (r.stderr ? '\n' + r.stderr : '') + '\n[' + (r.status || '') + (r.time ? ', ' + r.time + ' s' : '') + ']';
            } catch (e) { P.fail(e); }
        }));

        $('#submit').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            persist();
            try {
                const sub = await P.post('/api/submissions', { questionId: qid, languageId: Number($('#language').value), sourceCode: editor.getValue(),
                    mode: contestId ? 'CONTEST' : 'PRACTICE', contestId: contestId ? Number(contestId) : null });
                $('#verdict').innerHTML = '<div class="d-flex align-items-center gap-2"><div class="spinner-border spinner-border-sm"></div>Judging on all test cases...</div>';
                const done = await P.waitForVerdict(sub.id);
                const results = await P.get('/api/submissions/' + done.id + '/results');
                $('#verdict').innerHTML = P.verdictHtml(done, results) + (done.status === 'accepted' && !contestId
                    ? '<button class="btn btn-sm btn-outline-primary mt-3" id="review"><i class="bi bi-stars me-1"></i>AI code review</button><div id="review-box"></div>' : '');
                const rv = $('#review');
                if (rv) rv.addEventListener('click', () => P.busy(rv, () => showReview(done.id, $('#review-box'))));
                if (done.status === 'accepted') P.toast('Accepted. All test cases passed.');
            } catch (e) { P.fail(e); }
        }));

        if (!contestId) setupTutor(qid, editor);
    });

    async function showReview(id, box) {
        try {
            const r = await P.post('/api/submissions/' + id + '/review');
            box.innerHTML = '<div class="mt-3 small"><p>' + esc(r.summary) + '</p><div>Time: <strong>' + esc(r.timeComplexity) + '</strong> · Space: <strong>' + esc(r.spaceComplexity) +
                '</strong> · Quality ' + esc(r.qualityScore) + '/10</div><div class="row mt-2"><div class="col-md-4"><strong>Strengths</strong><ul>' + (r.strengths || []).map((x) => '<li>' + esc(x) + '</li>').join('') +
                '</ul></div><div class="col-md-4"><strong>Improve</strong><ul>' + (r.improvements || []).map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul></div><div class="col-md-4"><strong>Edge cases</strong><ul>' +
                (r.edgeCases || []).map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul></div></div></div>';
        } catch (e) { P.fail(e); }
    }

    async function setupTutor(qid, editor) {
        const chat = $('#tutor-chat');
        const add = (role, text) => {
            const d = document.createElement('div');
            d.className = 'chat-msg ' + role + (role === 'ASSISTANT' ? ' markdown' : '');
            d.innerHTML = role === 'ASSISTANT' ? P.md(text) : esc(text);
            chat.appendChild(d);
            chat.scrollTop = chat.scrollHeight;
        };
        try {
            const h = await P.get('/api/ai/tutor/history?questionId=' + encodeURIComponent(qid));
            h.messages.forEach((m) => add(m.role, m.content));
            $('#hint-level').textContent = h.hintLevel ? 'hint level ' + h.hintLevel + '/4' : '';
            if (!h.messages.length) add('ASSISTANT', 'Hi! I am your tutor for this problem. Ask me for a hint, or paste an error you do not understand.');
        } catch (e) {
            add('ASSISTANT', 'The tutor is unavailable right now.');
        }
        $('#tutor-form').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const input = $('#tutor-q');
            const text = input.value.trim();
            if (!text) return;
            input.value = '';
            add('USER', text);
            const lang = $('#language').selectedOptions[0];
            await P.busy(ev.target.querySelector('button'), async () => {
                try {
                    const a = await P.post('/api/ai/tutor/query', { questionId: qid, query: text, code: editor.getValue(), language: lang ? lang.textContent : null });
                    add('ASSISTANT', a.answer + (a.nextStep ? '\n\n**Next step:** ' + a.nextStep : ''));
                    if (a.hintLevel) $('#hint-level').textContent = 'hint level ' + a.hintLevel + '/4';
                } catch (e) { add('ASSISTANT', e.message); }
            });
        });
    }

    // ================================================================= submissions

    P.page('student-submissions', async () => {
        const subs = await P.get('/api/submissions/me');
        P.table('#subs', [
            { label: 'When', render: (s) => P.fmtDateTime(s.submissionTime) },
            { label: 'Problem', render: (s) => '<a href="/student/practice/' + encodeURIComponent(s.questionId) + '">' + esc(s.questionId.substring(0, 8)) + '…</a> <span class="small text-muted">' + P.title(s.mode) + '</span>' },
            { label: 'Language', render: (s) => esc(s.language) },
            { label: 'Verdict', render: (s) => P.badge(s.status) },
            { label: 'Tests', render: (s) => s.testcasesPassed + '/' + s.totalTestcases },
            { label: '', cls: 'text-end', render: (s) => '<button class="btn btn-sm btn-light" data-view="' + s.id + '">View</button>' }
        ], subs, 'No submissions yet');
        P.on('#subs', '[data-view]', async (b) => {
            const s = await P.get('/api/submissions/' + b.dataset.view);
            const r = await P.get('/api/submissions/' + s.id + '/results');
            const m = P.modal('Submission', P.verdictHtml(s, r) + '<pre class="bg-dark text-light p-3 rounded mono mt-3" style="max-height:400px">' + esc(s.sourceCode) + '</pre>' +
                (s.status === 'accepted' && s.mode === 'PRACTICE' ? '<button class="btn btn-sm btn-outline-primary" id="m-review"><i class="bi bi-stars me-1"></i>AI code review</button><div id="m-review-box"></div>' : ''),
            null, { size: 'xl' });
            const rv = m.el.querySelector('#m-review');
            if (rv) rv.addEventListener('click', () => P.busy(rv, () => showReview(s.id, m.el.querySelector('#m-review-box'))));
        });
    });

    // ================================================================= contests

    P.page('contests', async () => {
        const list = await P.get('/api/coding/contests');
        const staff = P.isStaff();
        $('#contest-list').innerHTML = list.length ? list.map((c) => '<div class="col-md-6 col-xl-4"><div class="card h-100"><div class="card-body">' +
            '<div class="d-flex justify-content-between">' + P.badge(c.phase) + '<span class="small text-muted">' + c.participants + ' participants</span></div>' +
            '<h5 class="mt-2">' + esc(c.title) + '</h5><div class="small text-muted mb-2">' + P.fmtDateTime(c.startTime) + ' → ' + P.fmtDateTime(c.endTime) + '</div>' +
            '<p class="small">' + esc(c.description || '') + '</p>' +
            (staff ? '<a class="btn btn-sm btn-outline-primary" href="/tpo/contests">Manage</a>'
                : c.joined || c.phase === 'ENDED' ? '<a class="btn btn-sm btn-primary" href="/student/contests/' + c.id + '">' + (c.phase === 'ENDED' ? 'Results' : 'Enter') + '</a>'
                    : '<button class="btn btn-sm btn-primary" data-join="' + c.id + '">Register</button>') + '</div></div></div>').join('')
            : '<div class="col-12">' + P.empty('No contests scheduled', 'trophy') + '</div>';
        P.on('#contest-list', '[data-join]', async (b) => {
            try { await P.post('/api/coding/contests/' + b.dataset.join + '/join'); location.href = '/student/contests/' + b.dataset.join; } catch (e) { P.fail(e); }
        });
    });

    P.renderBoard = function (el, entries, me) {
        P.table(el, [
            { label: '#', render: (e) => e.rank },
            { label: 'Name', render: (e) => esc(e.name) + (e.userId === me ? ' <span class="badge text-bg-primary">you</span>' : '') },
            { label: 'Score', render: (e) => '<strong>' + e.score + '</strong>' },
            { label: 'Penalty', render: (e) => e.penaltyMinutes + ' min' }
        ], entries, 'No submissions yet');
    };

    P.page('student-contest', async () => {
        const id = $('#contest-id').value;
        const c = await P.get('/api/coding/contests/' + id);
        const timer = () => {
            const target = new Date(c.phase === 'UPCOMING' ? c.startTime : c.endTime).getTime();
            const s = Math.max(0, Math.round((target - Date.now()) / 1000));
            const t = Math.floor(s / 3600) + 'h ' + Math.floor((s % 3600) / 60) + 'm ' + (s % 60) + 's';
            $('#countdown').textContent = c.phase === 'ENDED' ? 'Ended' : (c.phase === 'UPCOMING' ? 'Starts in ' : 'Ends in ') + t;
            if (s === 0 && c.phase !== 'ENDED') setTimeout(() => location.reload(), 1500);
        };
        $('#contest-head').innerHTML = '<div><h1>' + esc(c.title) + '</h1><div class="sub">' + P.badge(c.phase) + ' ' + c.participants + ' participants</div></div>' +
            '<div class="exam-timer" id="countdown"></div>';
        timer();
        setInterval(timer, 1000);
        P.table('#contest-problems', [
            { label: '', render: (p) => (p.solvedByMe ? '<i class="bi bi-check-circle-fill text-success"></i>' : p.wrongAttempts ? '<i class="bi bi-x-circle text-danger"></i>' : '') },
            { label: 'Problem', render: (p) => (c.phase === 'UPCOMING' ? esc(p.title) : '<a class="fw-semibold" href="/student/contests/' + id + '/problems/' + encodeURIComponent(p.questionId) + '">' + esc(p.title) + '</a>') },
            { label: 'Difficulty', render: (p) => P.badge(p.difficulty) },
            { label: 'Points', render: (p) => p.points },
            { label: 'Wrong tries', render: (p) => p.wrongAttempts || 0 }
        ], c.problems || [], c.phase === 'UPCOMING' ? 'Problems are revealed when the contest starts' : 'No problems');
        const me = document.body.dataset.userId;
        P.renderBoard('#board', await P.get('/api/coding/contests/' + id + '/leaderboard'), me);
        if (c.phase === 'RUNNING') {
            $('#live').classList.remove('d-none');
            P.subscribe('/topic/contest/' + id, (e) => P.renderBoard('#board', e.entries, me));
        }
    });

    P.page('hall-of-fame', async () => {
        const list = await P.get('/api/coding/hall-of-fame');
        P.table('#hof', [
            { label: 'Rank', render: (e) => e.rank },
            { label: 'Name', render: (e) => esc(e.name) },
            { label: 'Rating', render: (e) => '<strong>' + e.rating + '</strong>' },
            { label: 'Solved', render: (e) => e.solved },
            { label: 'Badges', render: (e) => P.chips(e.badges) }
        ], list, 'No rankings yet');
    });
})();
