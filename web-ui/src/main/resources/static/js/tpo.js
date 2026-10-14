/* Placement office (TPO) pages. */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, $$, esc } = P;

    async function fillBatches(select) {
        const y = new Date().getFullYear();
        for (let b = y + 2; b >= y - 3; b--) select.insertAdjacentHTML('beforeend', '<option value="' + b + '">Batch ' + b + '</option>');
    }

    // ================================================================= dashboard

    P.page('tpo-dashboard', async () => {
        await fillBatches($('#batch'));
        const load = async () => {
            let o;
            try { o = await P.get('/api/analytics/tpo/overview' + P.qs({ batch: $('#batch').value })); } catch (e) { P.fail(e); return; }
            $('#stats').innerHTML = P.stat('Students', o.students, 'mortarboard', 'bg-soft-blue', o.eligibleUnplaced + ' unplaced without backlogs') +
                P.stat('Placed', o.placed + ' (' + o.placementPercent + '%)', 'award', 'bg-soft-green') +
                P.stat('Offers', o.offers, 'patch-check', 'bg-soft-purple', 'Median ' + P.money(o.ctc.median) + ' · highest ' + P.money(o.ctc.highest)) +
                P.stat('At risk', o.atRisk, 'exclamation-triangle', 'bg-soft-red', 'Avg readiness ' + (o.averageReadiness == null ? '-' : o.averageReadiness));
            P.chart('branch-chart', {
                type: 'bar',
                data: { labels: o.byBranch.map((b) => b.key), datasets: [
                    { label: 'Students', data: o.byBranch.map((b) => b.total), backgroundColor: '#cfd8d6' },
                    { label: 'Placed', data: o.byBranch.map((b) => b.placed), backgroundColor: '#2a9d8f' }] },
                options: { plugins: { legend: { display: true }, tooltip: { callbacks: { afterBody: (items) => o.byBranch[items[0].dataIndex].percent + '% placed' } } } }
            });
            P.chart('tier-chart', { type: 'doughnut', data: { labels: o.byTier.map((t) => P.title(t.name)), datasets: [{ data: o.byTier.map((t) => t.count), backgroundColor: P.colors }] } });
            P.chart('month-chart', { type: 'line', data: { labels: o.offersByMonth.map((m) => m.month), datasets: [{ label: 'Offers', data: o.offersByMonth.map((m) => m.offers), borderColor: '#2a9d8f', tension: 0.3, fill: false }] } });
            P.table('#recruiters', [
                { label: 'Company', render: (n) => esc(n.name) },
                { label: 'Offers', render: (n) => n.count }
            ], o.topRecruiters, 'No offers yet');
            $('#skills').innerHTML = o.inDemandSkills.length ? o.inDemandSkills.map((s) => '<span class="chip">' + esc(s.name) + ' <strong>' + s.count + '</strong></span>').join('') : P.empty('No job postings yet');
        };
        $('#batch').addEventListener('change', load);
        load();
        Promise.allSettled([P.get('/api/auth/companies/pending'), P.get('/api/placement/jobs?status=PENDING_APPROVAL'), P.get('/api/placement/offers?status=ACCEPTED'),
            P.get('/api/interviews/experiences/pending'), P.get('/api/assessment/mcqs?approved=false&size=1')]).then((r) => {
            const n = (i, f) => (r[i].status === 'fulfilled' ? f(r[i].value) : '?');
            const items = [['Company registrations', n(0, (v) => v.length), '/tpo/companies'], ['Job postings', n(1, (v) => v.length), '/tpo/jobs'],
                ['Offers to verify', n(2, (v) => v.length), '/tpo/offers'], ['Experiences to review', n(3, (v) => v.length), '/tpo/experiences'],
                ['AI MCQs to approve', n(4, (v) => v.totalElements != null ? v.totalElements : (v.page ? v.page.totalElements : 0)), '/tpo/mcqs?approved=false']];
            $('#pending').innerHTML = '<div class="list-group">' + items.map(([l, c, h]) => '<a class="list-group-item list-group-item-action d-flex justify-content-between" href="' + h + '">' + l +
                '<span class="badge ' + (c > 0 ? 'text-bg-warning' : 'text-bg-light') + '">' + c + '</span></a>').join('') + '</div>';
        });
    });

    // ================================================================= students

    P.page('tpo-students', async () => {
        P.tabs();
        let page = 0;
        P.get('/api/profile/students/branches').then((b) => { $('#branch').innerHTML += b.map((x) => '<option>' + esc(x) + '</option>').join(''); });
        const load = async () => {
            const res = await P.get('/api/profile/students' + P.qs({ q: $('#q').value, branch: $('#branch').value, batch: $('#batch').value, page }));
            P.table('#list', [
                { label: 'Student', render: (s) => '<a class="fw-semibold" href="/tpo/students/' + s.userId + '">' + esc(s.name) + '</a><div class="small text-muted">' + esc(s.regNo) + ' · ' + esc(s.email) + '</div>' },
                { label: 'Branch', render: (s) => esc(s.branch) + '<div class="small text-muted">' + esc(s.batch) + '</div>' },
                { label: 'CGPA', render: (s) => esc(s.cgpa) + (s.backlogs ? ' <span class="badge text-bg-danger">' + s.backlogs + '</span>' : '') },
                { label: 'Profile', render: (s) => s.completeness + '%' + (s.hasResume ? ' <i class="bi bi-file-earmark-check text-success" title="Resume"></i>' : '') },
                { label: 'ATS', render: (s) => (s.bestAtsScore != null ? s.bestAtsScore : '-') }
            ], P.content(res), 'No students - import your batch to begin');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        let t;
        ['#q', '#batch'].forEach((s) => $(s).addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 350); }));
        $('#branch').addEventListener('change', () => { page = 0; load(); });
        load();

        const loadRisk = async () => {
            $('#risk').innerHTML = P.spinner();
            try {
                const list = await P.get('/api/analytics/tpo/at-risk' + P.qs({ threshold: $('#threshold').value }));
                $('#risk-count').textContent = list.length || '';
                P.table('#risk', [
                    { label: 'Student', render: (s) => '<a href="/tpo/students/' + s.userId + '">' + esc(s.name) + '</a><div class="small text-muted">' + esc(s.regNo) + ' · ' + esc(s.branch) + '</div>' },
                    { label: 'CGPA', render: (s) => esc(s.cgpa) },
                    { label: 'Readiness', render: (s) => '<span class="badge text-bg-danger">' + s.readiness + '</span>' },
                    { label: 'Placement chance', render: (s) => s.placementChance + '%' },
                    { label: 'Weakest area', render: (s) => esc(s.weakestArea) },
                    { label: 'Updated', render: (s) => P.fromNow(s.computedAt) }
                ], list, 'Nobody below the threshold. (Scores are refreshed every night.)');
            } catch (e) { P.fail(e); $('#risk').innerHTML = ''; }
        };
        $('#reload-risk').addEventListener('click', loadRisk);
        document.addEventListener('tab-shown', (ev) => { if (ev.detail === 'risk') loadRisk(); });

        const showAccounts = (title, accounts, extra) => {
            P.modal(title, (extra || '') + (accounts.length ? '<p class="small text-muted">Temporary passwords are shown only once and were also e-mailed. Students must change them at first login.</p>' +
                '<div class="table-responsive"><table class="table table-sm small"><thead><tr><th>Login</th><th>Name</th><th>E-mail</th><th>Temporary password</th></tr></thead><tbody>' +
                accounts.map((a) => '<tr><td class="mono">' + esc(a.loginId) + '</td><td>' + esc(a.name) + '</td><td>' + esc(a.email) + '</td><td class="mono">' + esc(a.temporaryPassword || '(e-mailed)') + '</td></tr>').join('') +
                '</tbody></table></div><button class="btn btn-sm btn-outline-secondary" id="dl-csv">Download as CSV</button>' : ''), null, { size: 'lg' });
            const dl = $('#dl-csv');
            if (dl) dl.addEventListener('click', () => {
                const csv = 'loginId,name,email,temporaryPassword\n' + accounts.map((a) => [a.loginId, a.name, a.email, a.temporaryPassword || ''].map((v) => '"' + String(v || '').replace(/"/g, '""') + '"').join(',')).join('\n');
                const a = document.createElement('a');
                a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
                a.download = 'student-accounts.csv';
                a.click();
            });
        };
        $('#import').addEventListener('click', () => {
            P.modal('Import students', '<p class="small">Upload a <strong>.csv</strong> or <strong>.xlsx</strong> file with columns: <code>regNo, name, email, branch, batch, cgpa, phone</code> (header names are matched flexibly). ' +
                'Accounts are created with temporary passwords; existing registration numbers are skipped.</p><input class="form-control" type="file" id="imp" accept=".csv,.xlsx">',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Import', onClick: async (el) => {
                const f = el.querySelector('#imp').files[0];
                if (!f) { P.toast('Choose a file', 'warning'); return false; }
                const fd = new FormData();
                fd.append('file', f);
                const r = await P.post('/api/auth/students/import', fd);
                setTimeout(() => showAccounts('Import result', r.accounts || [], '<div class="alert ' + (r.errors.length ? 'alert-warning' : 'alert-success') + ' small">' + r.created + ' created, ' + r.skipped +
                    ' skipped of ' + r.totalRows + ' rows.' + (r.errors.length ? '<ul class="mb-0 mt-1">' + r.errors.slice(0, 20).map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul>' : '') + '</div>'), 400);
                load();
            } }]);
        });
        $('#add').addEventListener('click', () => {
            P.modal('Add a student', '<form id="sf" class="row g-2"><div class="col-md-6"><label class="form-label small">Registration no.</label><input class="form-control" name="regNo" required></div>' +
                '<div class="col-md-6"><label class="form-label small">Name</label><input class="form-control" name="name" required></div>' +
                '<div class="col-md-6"><label class="form-label small">E-mail</label><input class="form-control" type="email" name="email" required></div>' +
                '<div class="col-md-6"><label class="form-label small">Phone</label><input class="form-control" name="phone"></div>' +
                '<div class="col-md-4"><label class="form-label small">Branch</label><input class="form-control" name="branch" required></div>' +
                '<div class="col-md-4"><label class="form-label small">Batch</label><input class="form-control" type="number" name="batch" required></div>' +
                '<div class="col-md-4"><label class="form-label small">CGPA</label><input class="form-control" type="number" step="0.01" min="0" max="10" name="cgpa"></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Create account', onClick: async (el) => {
                const f = el.querySelector('#sf');
                if (!f.reportValidity()) return false;
                const a = await P.post('/api/auth/students', P.formData(f));
                setTimeout(() => showAccounts('Account created', [a]), 400);
                load();
            } }]);
        });
    });

    P.page('tpo-student', async () => {
        const id = $('#user-id').value;
        const p = await P.get('/api/profile/students/' + id);
        $('#head').innerHTML = '<div><h1>' + esc(p.name) + '</h1><div class="sub">' + esc(p.regNo) + ' · ' + esc(p.email) + ' · ' + esc(p.phone || '') + '</div></div>' +
            (p.hasResume ? '<button class="btn btn-outline-primary" id="resume"><i class="bi bi-file-earmark-pdf me-1"></i>Resume</button>' : '');
        if (p.hasResume) $('#resume').addEventListener('click', () => P.openFile('/api/profile/students/' + id + '/resume'));
        const academic = (x) => { $('#academic').innerHTML = [['Branch', x.branch], ['Batch', x.batch], ['CGPA', x.cgpa], ['Backlogs', x.backlogs], ['10th', x.tenthPercentage], ['12th', x.twelfthPercentage]]
            .map(([k, v]) => '<div class="d-flex justify-content-between border-bottom py-1"><span class="text-muted">' + k + '</span><span>' + esc(v == null ? '-' : v) + '</span></div>').join(''); };
        academic(p);
        $('#edit-academic').addEventListener('click', () => {
            P.modal('Edit academic record', '<form id="af" class="row g-2"><div class="col-6"><label class="form-label small">Branch</label><input class="form-control" name="branch"></div>' +
                '<div class="col-6"><label class="form-label small">Batch</label><input class="form-control" type="number" name="batch"></div>' +
                '<div class="col-6"><label class="form-label small">CGPA</label><input class="form-control" type="number" step="0.01" min="0" max="10" name="cgpa"></div>' +
                '<div class="col-6"><label class="form-label small">Backlogs</label><input class="form-control" type="number" min="0" name="backlogs"></div></form><div class="form-text">Changes are recorded in the audit log.</div>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Save', onClick: async (el) => { academic(await P.put('/api/profile/students/' + id + '/academic', P.formData(el.querySelector('#af')))); P.toast('Saved'); } }]);
            P.fillForm($('#af'), p);
        });
        $('#profile').innerHTML = (p.summary ? '<p>' + esc(p.summary) + '</p>' : '') + '<h6>Skills</h6>' + P.chips(p.skills) + '<h6 class="mt-3">Projects</h6>' +
            ((p.projects || []).map((x) => '<div class="mb-2"><strong>' + esc(x.title) + '</strong> <span class="small text-muted">' + esc(x.techStack || '') + '</span><div class="small">' + esc(x.description || '') + '</div></div>').join('') || '<span class="text-muted">-</span>') +
            '<h6 class="mt-3">Certifications</h6>' + P.chips(p.certifications) + '<div class="small text-muted mt-3">Profile ' + p.completeness.percent + '% complete</div>';
        try {
            const d = await P.get('/api/analytics/tpo/students/' + id);
            $('#readiness').innerHTML = '<div class="row"><div class="col-6">' + P.ring(d.readiness.score, d.readiness.level) + '</div><div class="col-6">' + P.ring(d.prediction.percent, 'placement chance', P.ringColor(d.prediction.percent)) + '</div></div>' +
                '<ul class="small mt-3 mb-0">' + d.readiness.suggestions.map((s) => '<li><strong>' + esc(s.area) + '</strong> (+' + s.potentialGain + ')</li>').join('') + '</ul>';
            const pl = d.placement || {};
            $('#activity').innerHTML = '<div class="row text-center g-3">' + [['Applications', pl.applications], ['Active', pl.active], ['Offers', pl.offers], ['Rounds cleared', (pl.roundsCleared || 0) + '/' + (pl.roundsTaken || 0)],
                ['Coding rating', d.coding ? d.coding.rating : '-'], ['Problems solved', d.coding ? d.coding.solved : '-'], ['Aptitude accuracy', d.aptitude ? P.pct(d.aptitude.practiceAccuracy) : '-'],
                ['Mock interviews', d.mockInterviews ? d.mockInterviews.completed : '-']].map(([k, v]) => '<div class="col-6 col-md-3"><div class="small text-muted">' + k + '</div><div class="fs-5 fw-bold">' + esc(v == null ? 0 : v) + '</div></div>').join('') +
                '</div>' + (pl.placed ? '<div class="alert alert-success mt-3 mb-0">Placed · ' + P.title(pl.placedTier) + ' · best offer ' + P.money(pl.bestCtc) + '</div>' : '');
        } catch (e) { $('#readiness').innerHTML = P.empty('Analytics unavailable'); }
    });

    // ================================================================= companies

    P.page('tpo-companies', async () => {
        const load = async () => {
            const pending = await P.get('/api/auth/companies/pending');
            P.table('#pending', [
                { label: 'Company', render: (c) => '<span class="fw-semibold">' + esc(c.companyName) + '</span><div class="small text-muted">' + esc(c.industry || '') + '</div>' },
                { label: 'Contact', render: (c) => esc(c.contactPerson) + '<div class="small text-muted">' + esc(c.email) + ' · ' + esc(c.phone || '') + '</div>' },
                { label: 'Website', render: (c) => (c.website ? '<a target="_blank" rel="noopener noreferrer" href="' + esc(c.website) + '">' + esc(c.website) + '</a>' : '-') },
                { label: 'Registered', render: (c) => P.fromNow(c.registeredAt) },
                { label: '', cls: 'text-end text-nowrap', render: (c) => '<button class="btn btn-sm btn-success" data-approve="' + c.userId + '">Approve</button> <button class="btn btn-sm btn-outline-danger" data-reject="' + c.userId + '">Reject</button>' }
            ], pending, 'No registrations waiting');
            const all = await P.get('/api/auth/users?role=COMPANY&size=100');
            P.table('#all', [
                { label: 'Company', render: (u) => esc(u.name) + '<div class="small text-muted">' + esc(u.email) + '</div>' },
                { label: 'Status', render: (u) => P.badge(u.status) + (u.statusReason ? '<div class="small text-muted">' + esc(u.statusReason) + '</div>' : '') },
                { label: 'Last login', render: (u) => P.fmtDateTime(u.lastLoginAt) }
            ], P.content(all), 'No companies yet');
        };
        P.on('#pending', '[data-approve]', async (b) => { try { await P.post('/api/auth/companies/' + b.dataset.approve + '/approve'); P.toast('Approved - the company has been notified'); load(); } catch (e) { P.fail(e); } });
        P.on('#pending', '[data-reject]', async (b) => {
            const reason = await P.prompt('Reject registration', 'Reason (e-mailed to the company)', '', true);
            if (reason == null) return;
            try { await P.post('/api/auth/companies/' + b.dataset.reject + '/reject', { reason }); load(); } catch (e) { P.fail(e); }
        });
        load();
    });

    // ================================================================= jobs / applications / offers

    P.page('tpo-jobs', async () => {
        const load = async () => P.jobsTable('#jobs', await P.get('/api/placement/jobs' + P.qs({ status: $('#status').value })), '/tpo', true);
        P.bindJobActions('#jobs', load);
        $('#status').addEventListener('change', load);
        load();
    });

    P.page('tpo-applications', async () => {
        let page = 0;
        const load = async () => {
            const res = await P.get('/api/placement/applications' + P.qs({ status: $('#status').value, page }));
            P.table('#list', [
                { label: 'Student', render: (a) => esc(a.studentName) + '<div class="small text-muted">' + esc(a.regNo) + ' · ' + esc(a.branch) + '</div>' },
                { label: 'Job', render: (a) => '<a href="/tpo/jobs/' + a.jobId + '">' + esc(a.jobTitle) + '</a><div class="small text-muted">' + esc(a.companyName) + '</div>' },
                { label: 'Status', render: (a) => P.badge(a.status) + (a.currentRoundName ? '<div class="small text-muted">' + esc(a.currentRoundName) + '</div>' : '') },
                { label: 'Match', render: (a) => (a.matchScore != null ? a.matchScore + '%' : '-') },
                { label: 'Applied', render: (a) => P.fmtDate(a.appliedAt) }
            ], P.content(res), 'No applications');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        $('#status').addEventListener('change', () => { page = 0; load(); });
        load();
    });

    P.page('tpo-offers', async () => {
        const load = async () => {
            P.offersTable('#offers', await P.get('/api/placement/offers' + P.qs({ status: $('#status').value })), true);
        };
        P.on('#offers', '[data-verify]', async (b) => {
            if (!(await P.confirm('Verify offer', 'The student will be marked placed and receive a placement certificate.', 'Verify', 'btn-success'))) return;
            try { await P.post('/api/placement/offers/' + b.dataset.verify + '/verify'); P.toast('Verified - certificate issued'); load(); } catch (e) { P.fail(e); }
        });
        P.on('#offers', '[data-reject-offer]', async (b) => {
            const note = await P.prompt('Reject offer', 'Reason (shown to the student)', '', true);
            if (note == null) return;
            try { await P.post('/api/placement/offers/' + b.dataset.rejectOffer + '/reject', { note }); load(); } catch (e) { P.fail(e); }
        });
        $('#status').addEventListener('change', load);
        load();
    });

    // ================================================================= contests

    P.page('tpo-contests', async () => {
        const load = async () => {
            const list = await P.get('/api/coding/contests');
            state.list = list;
            P.table('#list', [
                { label: 'Contest', render: (c) => '<span class="fw-semibold">' + esc(c.title) + '</span><div class="small text-muted">#' + c.id + ' · ' + (c.problems || []).length + ' problems</div>' },
                { label: 'Phase', render: (c) => P.badge(c.phase) },
                { label: 'When', render: (c) => P.fmtDateTime(c.startTime) + '<div class="small text-muted">to ' + P.fmtDateTime(c.endTime) + '</div>' },
                { label: 'Participants', render: (c) => c.participants },
                { label: '', cls: 'text-end text-nowrap', render: (c) => '<button class="btn btn-sm btn-light" data-board="' + c.id + '">Leaderboard</button> ' +
                    (c.phase === 'UPCOMING' ? '<button class="btn btn-sm btn-light" data-edit="' + c.id + '">Edit</button> <button class="btn btn-sm btn-outline-danger" data-del="' + c.id + '"><i class="bi bi-trash"></i></button>' : '') +
                    (c.phase === 'ENDED' ? ' <a class="btn btn-sm btn-outline-primary" href="/tpo/plagiarism?scope=CONTEST&scopeId=' + c.id + '">Plagiarism</a>' : '') }
            ], list, 'No contests yet');
        };
        const state = {};
        load();
        P.on('#list', '[data-board]', async (b) => {
            const c = state.list.find((x) => String(x.id) === b.dataset.board);
            $('#board-card').classList.remove('d-none');
            $('#board-title').textContent = 'Leaderboard: ' + c.title;
            P.renderBoard('#board', await P.get('/api/coding/contests/' + c.id + '/leaderboard?limit=100'), null);
            if (c.phase === 'RUNNING') P.subscribe('/topic/contest/' + c.id, (e) => P.renderBoard('#board', e.entries, null));
        });
        P.on('#list', '[data-del]', async (b) => { if (await P.confirm('Delete contest', 'Delete this contest?', 'Delete', 'btn-danger')) { await P.del('/api/coding/contests/' + b.dataset.del); load(); } });
        const editor = async (c) => {
            const qs = P.content(await P.get('/api/coding/questions?size=100'));
            const chosen = new Map((c && c.problems ? c.problems : []).map((p) => [p.questionId, p.points]));
            P.modal(c ? 'Edit contest' : 'New contest', '<form id="cf" class="row g-2"><div class="col-12"><label class="form-label small">Title</label><input class="form-control" name="title" required></div>' +
                '<div class="col-12"><label class="form-label small">Description</label><textarea class="form-control" name="description" rows="2"></textarea></div>' +
                '<div class="col-md-6"><label class="form-label small">Starts</label><input class="form-control" type="datetime-local" name="startTime" required></div>' +
                '<div class="col-md-6"><label class="form-label small">Ends</label><input class="form-control" type="datetime-local" name="endTime" required></div>' +
                '<div class="col-12"><label class="form-label small">Problems and points</label><div style="max-height:260px;overflow:auto" class="border rounded p-2">' +
                qs.map((q) => '<div class="d-flex gap-2 align-items-center mb-1 cqp"><input class="form-check-input" type="checkbox" value="' + esc(q.id) + '" ' + (chosen.has(q.id) ? 'checked' : '') + '>' +
                    '<span class="flex-fill small">' + esc(q.title) + ' ' + P.badge(q.difficulty) + '</span><input type="number" class="form-control form-control-sm" style="width:80px" min="1" value="' + (chosen.get(q.id) || 100) + '"></div>').join('') +
                '</div></div></form>', [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Save', onClick: async (el) => {
                const f = el.querySelector('#cf');
                if (!f.reportValidity()) return false;
                const body = P.formData(f);
                body.questions = P.$$('.cqp', f).filter((d) => d.querySelector('input[type=checkbox]').checked)
                    .map((d) => ({ questionId: d.querySelector('input[type=checkbox]').value, points: Number(d.querySelector('input[type=number]').value) || 100 }));
                if (!body.questions.length) { P.toast('Pick at least one problem', 'warning'); return false; }
                if (c) await P.put('/api/coding/contests/' + c.id, body); else await P.post('/api/coding/contests', body);
                load();
            } }], { size: 'lg' });
            if (c) P.fillForm($('#cf'), c);
        };
        $('#new').addEventListener('click', () => editor());
        P.on('#list', '[data-edit]', (b) => editor(state.list.find((x) => String(x.id) === b.dataset.edit)));
    });

    // ================================================================= moderation, policy, reports, announcements

    P.page('tpo-experiences', async () => {
        const load = async () => {
            const list = await P.get('/api/interviews/experiences/pending');
            $('#list').innerHTML = list.length ? list.map((e) => P.experienceCard(e).replace('</div></div>', '<div class="mt-3 d-flex gap-2"><button class="btn btn-sm btn-success" data-ok="' + e.id +
                '">Approve</button><button class="btn btn-sm btn-outline-danger" data-no="' + e.id + '">Reject</button></div></div></div>')).join('') : P.empty('Nothing to review', 'check2-all');
        };
        P.on('#list', '[data-ok]', async (b) => { await P.post('/api/interviews/experiences/' + b.dataset.ok + '/review', { approve: true }); P.toast('Published'); load(); });
        P.on('#list', '[data-no]', async (b) => {
            const note = await P.prompt('Reject experience', 'Reason (sent to the student)', '', true);
            if (note == null) return;
            await P.post('/api/interviews/experiences/' + b.dataset.no + '/review', { approve: false, note });
            load();
        });
        load();
    });

    P.page('tpo-policy', async () => {
        const p = await P.get('/api/placement/policy');
        P.fillForm($('#form'), p);
        $('#updated').textContent = p.updatedAt ? 'Last changed ' + P.fmtDateTime(p.updatedAt) : '';
        $('#save').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try { await P.put('/api/placement/policy', P.formData($('#form'))); P.toast('Policy saved'); } catch (e) { P.fail(e); }
        }));
    });

    P.page('tpo-reports', async () => {
        $('#pdf').addEventListener('click', (ev) => P.busy(ev.currentTarget, () => P.download('/api/analytics/reports/placement.pdf' + P.qs({ batch: $('#batch').value }), 'placement-report.pdf')));
        $('#xlsx').addEventListener('click', (ev) => P.busy(ev.currentTarget, () => P.download('/api/analytics/reports/placement.xlsx' + P.qs({ batch: $('#batch').value }), 'placement-report.xlsx')));
        const render = (m) => {
            const w = m.weights || {};
            $('#model').innerHTML = '<div class="row g-3 text-center mb-3">' + [['Status', m.trained ? 'Trained' : 'Not trained'], ['Examples', m.examples], ['Accuracy', m.accuracy != null ? Math.round(m.accuracy * 100) + '%' : '-'],
                ['ROC AUC', m.auc != null ? m.auc.toFixed(2) : '-']].map(([k, v]) => '<div class="col-6 col-md-3"><div class="small text-muted">' + k + '</div><div class="fs-5 fw-bold">' + esc(v) + '</div></div>').join('') + '</div>' +
                (Object.keys(w).length ? '<div style="height:200px"><canvas id="weights"></canvas></div>' : '') +
                '<p class="small text-muted mt-2">' + esc(m.note || '') + (m.trainedAt ? ' · trained ' + P.fmtDateTime(m.trainedAt) : '') + '</p>' +
                '<div class="d-flex flex-wrap gap-2 align-items-end"><button class="btn btn-outline-primary" id="retrain"><i class="bi bi-arrow-repeat me-1"></i>Retrain now</button>' +
                '<div class="ms-auto d-flex gap-2"><input class="form-control" type="number" id="season" placeholder="Batch" style="width:120px"><button class="btn btn-primary" id="record">Record finished season</button></div></div>' +
                '<div class="form-text">At the end of a season, record the batch: every student\'s preparation and final outcome become training data, making predictions specific to your college.</div>';
            if (Object.keys(w).length) P.chart('weights', { type: 'bar', data: { labels: Object.keys(w), datasets: [{ label: 'Weight toward "placed"', data: Object.values(w), backgroundColor: Object.values(w).map((v) => (v >= 0 ? '#2a9d8f' : '#d1495b')) }] }, options: { indexAxis: 'y' } });
            $('#retrain').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => { try { render(await P.post('/api/analytics/tpo/model/retrain')); P.toast('Model retrained'); } catch (e) { P.fail(e); } }));
            $('#record').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
                const b = $('#season').value;
                if (!b) return P.toast('Enter the batch year', 'warning');
                if (!(await P.confirm('Record season ' + b, 'This stores the final outcome of every student in batch ' + b + ' and retrains the model. It can be done once per batch.', 'Record'))) return;
                try { const r = await P.post('/api/analytics/tpo/model/record-season?batch=' + encodeURIComponent(b)); P.toast(r.recorded + ' students recorded (' + r.placed + ' placed)'); render(r.model); } catch (e) { P.fail(e); }
            }));
        };
        render(await P.get('/api/analytics/tpo/model'));
    });

    P.page('tpo-announcements', async () => {
        const render = P.renderAnnouncements('#list', '#pager', (p) => render(p));
        render(0);
        $('#form').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const body = P.formData(ev.target);
            if (!(await P.confirm('Send announcement', 'Send to ' + P.title(body.audience) + (body.sendEmail ? ' (also by e-mail)' : '') + '?', 'Send'))) return;
            try { const a = await P.post('/api/notifications/announcements', body); P.toast('Sent to ' + a.recipients + ' people'); ev.target.reset(); render(0); } catch (e) { P.fail(e); }
        });
    });
})();
