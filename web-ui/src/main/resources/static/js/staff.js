/* Pages shared by recruiters and the placement office, plus pages every role uses. */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, $$, esc } = P;

    function tabs() {
        $$('[data-tab]').forEach((a) => a.addEventListener('click', (ev) => {
            ev.preventDefault();
            $$('[data-tab]').forEach((x) => x.classList.toggle('active', x === a));
            $$('[data-pane]').forEach((p) => p.classList.toggle('d-none', p.dataset.pane !== a.dataset.tab));
            a.dispatchEvent(new CustomEvent('tab-shown', { bubbles: true, detail: a.dataset.tab }));
        }));
    }
    P.tabs = tabs;

    function hideStaffOnly() {
        if (!P.isStaff()) $$('.staff-only').forEach((e) => e.classList.add('d-none'));
    }

    function stat(label, value, icon, cls, hint) {
        return '<div class="col-6 col-xl-3"><div class="stat d-flex justify-content-between"><div><div class="label">' + esc(label) + '</div><div class="value">' + esc(value) +
            '</div>' + (hint ? '<div class="hint">' + esc(hint) + '</div>' : '') + '</div><span class="icon ' + cls + '"><i class="bi bi-' + icon + '"></i></span></div></div>';
    }
    P.stat = stat;

    // ================================================================= company dashboard + profile

    P.page('company-dashboard', async () => {
        const o = await P.get('/api/analytics/company/overview');
        $('#stats').innerHTML = stat('Jobs', o.jobs, 'briefcase', 'bg-soft-blue', o.openJobs + ' open') + stat('Applicants', o.applicants, 'people', 'bg-soft-purple') +
            stat('Selected', o.selected, 'person-check', 'bg-soft-green') + stat('Offers accepted', o.offersAccepted + ' / ' + o.offersMade, 'award', 'bg-soft-amber', o.acceptanceRate + '% acceptance');
        const f = o.jobFunnels || [];
        P.chart('funnel', {
            type: 'bar',
            data: { labels: f.map((j) => j.title), datasets: [
                { label: 'Applicants', data: f.map((j) => j.applicants), backgroundColor: '#cfd8d6' },
                { label: 'Selected', data: f.map((j) => j.selected), backgroundColor: '#2a9d8f' },
                { label: 'Offers', data: f.map((j) => j.offers), backgroundColor: '#e9a03b' }] },
            options: { plugins: { legend: { display: true } } }
        });
        const br = {};
        f.forEach((j) => (j.applicantsByBranch || []).forEach((b) => { br[b.name] = (br[b.name] || 0) + b.count; }));
        P.chart('branches', { type: 'doughnut', data: { labels: Object.keys(br), datasets: [{ data: Object.values(br), backgroundColor: P.colors }] } });
        P.table('#job-table', [
            { label: 'Job', render: (j) => '<a href="/company/jobs/' + j.jobId + '">' + esc(j.title) + '</a>' },
            { label: 'Status', render: (j) => P.badge(j.status) },
            { label: 'Applicants', render: (j) => j.applicants },
            { label: 'Selected', render: (j) => j.selected },
            { label: 'Offers', render: (j) => j.offers }
        ], f, 'No jobs posted yet');
    });

    P.page('company-profile', async () => {
        const c = await P.get('/api/profile/companies/me');
        P.fillForm($('#form'), c);
        if (c.hasLogo) P.imageUrl('/api/profile/companies/' + c.userId + '/logo').then((u) => { if (u) $('#logo').src = u; });
        $('#save').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try { await P.put('/api/profile/companies/me', P.formData($('#form'))); P.toast('Saved'); } catch (e) { P.fail(e); }
        }));
        $('#logo-file').addEventListener('change', async (ev) => {
            const fd = new FormData();
            fd.append('file', ev.target.files[0]);
            try { await P.post('/api/profile/companies/me/logo', fd); $('#logo').src = URL.createObjectURL(ev.target.files[0]); P.toast('Logo updated'); } catch (e) { P.fail(e); }
        });
    });

    function jobsTable(el, jobs, base, staff) {
        P.table(el, [
            { label: 'Job', render: (j) => '<a class="fw-semibold" href="' + base + '/jobs/' + j.id + '">' + esc(j.title) + '</a><div class="small text-muted">' + esc(j.companyName) + ' · ' + P.pay(j) + '</div>' },
            { label: 'Status', render: (j) => P.badge(j.status) + ' ' + P.badge(j.tier) + (j.reviewNote ? '<div class="small text-muted">' + esc(j.reviewNote) + '</div>' : '') },
            { label: 'Deadline', render: (j) => P.fmtDateTime(j.applicationDeadline) },
            { label: 'Applicants', render: (j) => (j.applicantCount != null ? j.applicantCount : '-') },
            { label: '', cls: 'text-end text-nowrap', render: (j) => '<a class="btn btn-sm btn-light" href="' + base + '/jobs/' + j.id + '/edit">Edit</a> ' +
                (staff && j.status === 'PENDING_APPROVAL' ? '<button class="btn btn-sm btn-success" data-publish="' + j.id + '">Approve</button> <button class="btn btn-sm btn-outline-danger" data-reject-job="' + j.id + '">Reject</button> ' : '') +
                (j.status === 'OPEN' ? '<button class="btn btn-sm btn-outline-secondary" data-close="' + j.id + '">Close</button>' : '') }
        ], jobs, 'No jobs yet');
    }
    P.jobsTable = jobsTable;

    P.bindJobActions = function (el, reload) {
        P.on(el, '[data-close]', async (b) => {
            if (!(await P.confirm('Close applications', 'Students will no longer be able to apply.', 'Close'))) return;
            try { await P.post('/api/placement/jobs/' + b.dataset.close + '/close'); reload(); } catch (e) { P.fail(e); }
        });
        P.on(el, '[data-publish]', (b) => {
            P.modal('Approve and publish', '<label class="form-label">Placement tier</label><select class="form-select" id="tier"><option value="">Automatic (from CTC and policy)</option>' +
                '<option>NORMAL</option><option>DREAM</option><option>SUPER_DREAM</option></select><div class="form-text">Eligible students are notified when the job is published.</div>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Publish', cls: 'btn-success', onClick: async (m) => {
                const tier = m.querySelector('#tier').value;
                await P.post('/api/placement/jobs/' + b.dataset.publish + '/publish', tier ? { tier } : {});
                P.toast('Published');
                reload();
            } }]);
        });
        P.on(el, '[data-reject-job]', async (b) => {
            const note = await P.prompt('Reject job posting', 'Reason (sent to the company)', '', true);
            if (note == null) return;
            try { await P.post('/api/placement/jobs/' + b.dataset.rejectJob + '/reject', { note }); reload(); } catch (e) { P.fail(e); }
        });
    };

    P.page('company-jobs', async () => {
        const load = async () => jobsTable('#jobs', await P.get('/api/placement/jobs'), '/company', false);
        P.bindJobActions('#jobs', load);
        load();
    });

    function offersTable(el, offers, staff) {
        P.table(el, [
            { label: 'Student', render: (o) => esc(o.studentName) + '<div class="small text-muted">' + esc(o.regNo || '') + '</div>' },
            { label: 'Company / role', render: (o) => esc(o.companyName) + '<div class="small text-muted">' + esc(o.role || '') + (o.offCampus ? ' · off-campus' : '') + '</div>' },
            { label: 'CTC', render: (o) => P.money(o.ctcLpa) + ' ' + P.badge(o.tier) },
            { label: 'Status', render: (o) => P.badge(o.status) + (o.remarks ? '<div class="small text-muted">' + esc(o.remarks) + '</div>' : '') },
            { label: 'Issued', render: (o) => P.fmtDate(o.createdAt) },
            { label: '', cls: 'text-end text-nowrap', render: (o) => (o.hasLetter ? '<button class="btn btn-sm btn-light" data-letter="' + o.id + '">Letter</button> ' : '') +
                (staff && ['ACCEPTED', 'ISSUED'].includes(o.status) || (staff && o.offCampus && o.status !== 'VERIFIED' && o.status !== 'REJECTED')
                    ? '<button class="btn btn-sm btn-success" data-verify="' + o.id + '">Verify</button> <button class="btn btn-sm btn-outline-danger" data-reject-offer="' + o.id + '">Reject</button>' : '') }
        ], offers, 'No offers yet');
        P.on(el, '[data-letter]', (b) => P.openFile('/api/placement/offers/' + b.dataset.letter + '/letter'));
    }
    P.offersTable = offersTable;

    P.page('company-offers', async () => offersTable('#offers', await P.get('/api/placement/offers'), false));

    // ================================================================= job form

    const ROUND_TYPES = ['APTITUDE_TEST', 'CODING_TEST', 'TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'GROUP_DISCUSSION', 'OTHER'];

    P.page('job-form', async () => {
        const id = $('#job-id').value;
        P.get('/api/profile/students/branches').then((b) => { $('#branch-list').innerHTML = b.map((x) => '<option value="' + esc(x) + '">').join(''); }).catch(() => {});
        const addRound = (r) => {
            r = r || {};
            const div = document.createElement('div');
            div.className = 'row g-2 align-items-end border rounded p-2 mb-2 round-row';
            if (r.id) div.dataset.id = r.id;
            div.innerHTML = '<div class="col-md-3"><label class="form-label small">Name</label><input class="form-control form-control-sm" data-f="name" required></div>' +
                '<div class="col-md-3"><label class="form-label small">Type</label><select class="form-select form-select-sm" data-f="type">' + ROUND_TYPES.map((t) => '<option value="' + t + '">' + P.title(t) + '</option>').join('') + '</select></div>' +
                '<div class="col-md-3"><label class="form-label small">Scheduled</label><input class="form-control form-control-sm" type="datetime-local" data-f="scheduledAt"></div>' +
                '<div class="col-md-2"><label class="form-label small">Cut-off %</label><input class="form-control form-control-sm" type="number" data-f="cutoffPercent" min="0" max="100"></div>' +
                '<div class="col-md-1"><button type="button" class="btn btn-sm btn-outline-danger w-100" data-remove><i class="bi bi-x"></i></button></div>' +
                '<div class="col-12"><input class="form-control form-control-sm" data-f="description" placeholder="Description (optional)"></div>';
            div.querySelector('[data-f=name]').value = r.name || '';
            div.querySelector('[data-f=type]').value = r.type || 'TECHNICAL_INTERVIEW';
            div.querySelector('[data-f=scheduledAt]').value = P.toLocalInput(r.scheduledAt);
            div.querySelector('[data-f=cutoffPercent]').value = r.cutoffPercent == null ? '' : r.cutoffPercent;
            div.querySelector('[data-f=description]').value = r.description || '';
            div.querySelector('[data-remove]').addEventListener('click', async () => {
                if (div.dataset.id) {
                    if (!(await P.confirm('Delete round', 'Delete this round?', 'Delete', 'btn-danger'))) return;
                    try { await P.del('/api/placement/rounds/' + div.dataset.id); } catch (e) { P.fail(e); return; }
                }
                div.remove();
            });
            $('#rounds').appendChild(div);
        };
        const readRound = (div) => ({
            name: div.querySelector('[data-f=name]').value.trim(), type: div.querySelector('[data-f=type]').value,
            scheduledAt: div.querySelector('[data-f=scheduledAt]').value || null,
            cutoffPercent: div.querySelector('[data-f=cutoffPercent]').value === '' ? null : Number(div.querySelector('[data-f=cutoffPercent]').value),
            description: div.querySelector('[data-f=description]').value.trim() || null
        });
        $('#add-round').addEventListener('click', () => addRound());
        const addSalary = (c) => {
            c = c || {};
            const div = document.createElement('div');
            div.className = 'row g-2 mb-2 salary-row';
            div.innerHTML = '<div class="col-md-4"><input class="form-control form-control-sm" data-f="component" placeholder="Component, e.g. Base pay" maxlength="120"></div>' +
                '<div class="col-md-3"><input class="form-control form-control-sm" data-f="amount" placeholder="Amount, e.g. ₹9,00,000" maxlength="60"></div>' +
                '<div class="col-md-4"><input class="form-control form-control-sm" data-f="note" placeholder="Note, e.g. per annum, paid monthly" maxlength="255"></div>' +
                '<div class="col-md-1"><button type="button" class="btn btn-sm btn-outline-danger w-100" data-remove><i class="bi bi-x"></i></button></div>';
            ['component', 'amount', 'note'].forEach((f) => { div.querySelector('[data-f=' + f + ']').value = c[f] || ''; });
            div.querySelector('[data-remove]').addEventListener('click', () => div.remove());
            $('#salary').appendChild(div);
        };
        const readSalary = () => $$('.salary-row').map((div) => ({
            component: div.querySelector('[data-f=component]').value.trim(),
            amount: div.querySelector('[data-f=amount]').value.trim() || null,
            note: div.querySelector('[data-f=note]').value.trim() || null
        })).filter((c) => c.component);
        $('#add-salary').addEventListener('click', () => addSalary());
        if (id) {
            const j = await P.get('/api/placement/jobs/' + id);
            $('#form-title').textContent = 'Edit: ' + j.title;
            P.fillForm($('#job-form'), j);
            (j.rounds || []).forEach(addRound);
            (j.ctcBreakdown || []).forEach(addSalary);
        } else {
            [{ name: 'Online assessment', type: 'APTITUDE_TEST' }, { name: 'Technical interview', type: 'TECHNICAL_INTERVIEW' }, { name: 'HR interview', type: 'HR_INTERVIEW' }].forEach(addRound);
            [{ component: 'Base pay', note: 'per annum' }, { component: 'Joining bonus', note: 'one time' }].forEach(addSalary);
        }
        $('#save').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            const form = $('#job-form');
            if (!form.reportValidity()) return;
            const body = P.formData(form);
            body.ctcBreakdown = readSalary();
            const rows = $$('.round-row');
            try {
                if (id) {
                    await P.put('/api/placement/jobs/' + id, Object.assign(body, { rounds: null }));
                    for (const div of rows) {
                        const r = readRound(div);
                        if (!r.name) continue;
                        if (div.dataset.id) await P.put('/api/placement/rounds/' + div.dataset.id, r);
                        else await P.post('/api/placement/jobs/' + id + '/rounds', r);
                    }
                    P.toast('Job updated');
                    location.href = P.base() + '/jobs/' + id;
                } else {
                    body.rounds = rows.map(readRound).filter((r) => r.name);
                    const j = await P.post('/api/placement/jobs', body);
                    P.toast(P.isStaff() ? 'Job created' : 'Submitted to the placement office for approval');
                    location.href = P.base() + '/jobs/' + j.id;
                }
            } catch (e) { P.fail(e); }
        }));
    });

    // ================================================================= job management

    P.page('job-manage', async () => {
        const id = $('#job-id').value;
        const base = P.base();
        tabs();
        let job = await P.get('/api/placement/jobs/' + id);
        $('#head').innerHTML = '<div><div class="text-muted">' + esc(job.companyName) + '</div><h1>' + esc(job.title) + ' ' + P.badge(job.status) + '</h1><div class="sub">' +
            P.money(job.ctcLpa) + ' · ' + P.badge(job.tier) + ' · apply by ' + P.fmtDateTime(job.applicationDeadline) + '</div></div>' +
            '<a class="btn btn-light" href="' + base + '/jobs/' + id + '/edit"><i class="bi bi-pencil me-1"></i>Edit</a>';

        // ---- applicants
        const loadApplicants = async () => {
            const list = await P.get('/api/placement/jobs/' + id + '/applicants' + P.qs({ status: $('#app-status').value, sort: $('#app-sort').value }));
            P.table('#applicants', [
                { label: '<input type="checkbox" id="all">', render: (a) => '<input type="checkbox" class="pick" value="' + a.application.id + '">' },
                { label: 'Student', render: (a) => '<span class="fw-semibold">' + esc(a.application.studentName) + '</span><div class="small text-muted">' + esc(a.application.regNo) + ' · ' + esc(a.application.branch) + '</div>' },
                { label: 'CGPA', render: (a) => esc(a.cgpa) + (a.backlogs ? ' <span class="badge text-bg-danger">' + a.backlogs + ' backlog</span>' : '') },
                { label: 'Match / rank', render: (a) => (a.application.matchScore != null ? a.application.matchScore + '%' : '-') + ' / ' + (a.application.rankScore != null ? a.application.rankScore : '-') },
                { label: 'ATS', render: (a) => (a.atsScore != null ? a.atsScore : '-') },
                { label: 'Readiness', render: (a) => (a.readinessScore != null ? a.readinessScore : '-') },
                { label: 'Skills', render: (a) => P.chips((a.skills || []).slice(0, 5)) },
                { label: 'Status', render: (a) => P.badge(a.application.status) + (a.application.currentRoundName ? '<div class="small text-muted">' + esc(a.application.currentRoundName) + '</div>' : '') },
                { label: '', cls: 'text-end text-nowrap', render: (a) => (a.hasResume ? '<button class="btn btn-sm btn-light" data-resume="' + a.application.studentUserId + '">Resume</button> ' : '') +
                    '<button class="btn btn-sm btn-light" data-profile="' + a.application.studentUserId + '">Profile</button>' }
            ], list, 'No applicants yet');
            const all = $('#all');
            if (all) all.addEventListener('change', () => $$('.pick').forEach((c) => { c.checked = all.checked; }));
        };
        ['#app-status', '#app-sort'].forEach((s) => $(s).addEventListener('change', loadApplicants));
        const picked = () => $$('.pick:checked').map((c) => Number(c.value));
        $('#shortlist').addEventListener('click', async () => {
            if (!picked().length) return P.toast('Select applicants first', 'warning');
            try { const r = await P.post('/api/placement/jobs/' + id + '/shortlist', { applicationIds: picked() }); P.toast(r.shortlisted + ' shortlisted - they moved to round 1'); loadApplicants(); } catch (e) { P.fail(e); }
        });
        $('#reject').addEventListener('click', async () => {
            if (!picked().length) return P.toast('Select applicants first', 'warning');
            const reason = await P.prompt('Reject applicants', 'Reason (optional, shown to students)', '');
            if (reason === null) return;
            try { const r = await P.post('/api/placement/jobs/' + id + '/reject-applicants', { applicationIds: picked(), reason }); P.toast(r.rejected + ' rejected'); loadApplicants(); } catch (e) { P.fail(e); }
        });
        P.on('#applicants', '[data-resume]', (b) => P.openFile('/api/profile/students/' + b.dataset.resume + '/resume'));
        P.on('#applicants', '[data-profile]', async (b) => {
            const s = await P.get('/api/profile/students/' + b.dataset.profile);
            P.modal(s.name, '<div class="small text-muted mb-2">' + esc(s.regNo) + ' · ' + esc(s.branch) + ' · ' + esc(s.batch) + ' · CGPA ' + esc(s.cgpa) + '</div>' +
                (s.summary ? '<p>' + esc(s.summary) + '</p>' : '') + '<h6>Skills</h6>' + P.chips(s.skills) + '<h6 class="mt-3">Projects</h6>' +
                ((s.projects || []).map((p) => '<div class="mb-2"><strong>' + esc(p.title) + '</strong> <span class="small text-muted">' + esc(p.techStack || '') + '</span><div class="small">' + esc(p.description || '') + '</div></div>').join('') || '-') +
                '<h6 class="mt-3">Certifications</h6>' + P.chips(s.certifications) + (s.internships ? '<h6 class="mt-3">Internships</h6><div class="small pre-wrap">' + esc(s.internships) + '</div>' : '') +
                '<div class="mt-3 small">' + [s.githubUrl, s.linkedinUrl, s.portfolioUrl].filter(Boolean).map((u) => '<a target="_blank" rel="noopener" href="' + esc(u) + '">' + esc(u) + '</a>').join(' · ') + '</div>',
            null, { size: 'lg' });
        });
        loadApplicants();

        // ---- rounds
        const loadRounds = async () => {
            job = await P.get('/api/placement/jobs/' + id);
            const blocks = await Promise.all((job.rounds || []).map(async (r) => {
                const results = await P.get('/api/placement/rounds/' + r.id + '/results');
                const isTest = ['APTITUDE_TEST', 'CODING_TEST'].includes(r.type);
                const isInterview = ['TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'GROUP_DISCUSSION', 'OTHER'].includes(r.type);
                return '<div class="card mb-3"><div class="card-header d-flex flex-wrap gap-2 justify-content-between align-items-center"><span>Round ' + r.sequence + ': ' + esc(r.name) +
                    ' <span class="small text-muted">' + P.title(r.type) + (r.scheduledAt ? ' · ' + P.fmtDateTime(r.scheduledAt) : '') + '</span></span><span class="d-flex gap-2">' +
                    (isTest ? (r.assessmentTestId ? '<a class="btn btn-sm btn-light" href="' + base + '/tests/' + r.assessmentTestId + '">Test #' + r.assessmentTestId + '</a>' : '') +
                        '<button class="btn btn-sm btn-outline-primary" data-link="' + r.id + '">' + (r.assessmentTestId ? 'Change test' : 'Link test') + '</button>' : '') +
                    (isInterview ? '<a class="btn btn-sm btn-outline-primary" href="' + base + '/rounds/' + r.id + '/interviews"><i class="bi bi-clipboard-check me-1"></i>Scorecards</a>' : '') +
                    '<button class="btn btn-sm btn-success" data-decide="' + r.id + '" data-status="CLEARED">Clear selected</button>' +
                    '<button class="btn btn-sm btn-outline-danger" data-decide="' + r.id + '" data-status="REJECTED">Reject selected</button></span></div><div class="card-body p-0">' +
                    (results.length ? '<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead><tr><th></th><th>Student</th><th>Status</th><th>Score</th><th>Remarks</th></tr></thead><tbody>' +
                        results.map((x) => '<tr><td>' + (['PENDING', 'UNDER_REVIEW'].includes(x.status) ? '<input type="checkbox" class="rpick-' + r.id + '" value="' + x.applicationId + '">' : '') + '</td><td>' +
                            esc(x.studentName) + ' <span class="small text-muted">' + esc(x.regNo) + '</span></td><td>' + P.badge(x.status) + '</td><td>' + esc(x.score == null ? '-' : x.score) +
                            '</td><td class="small">' + esc(x.remarks || '') + '</td></tr>').join('') + '</tbody></table></div>' : P.empty('No candidates have reached this round yet')) + '</div></div>';
            }));
            $('#round-list').innerHTML = blocks.join('') || P.empty('No rounds defined - edit the job to add rounds');
            $('#slot-round').innerHTML = (job.rounds || []).map((r) => '<option value="' + r.id + '">' + esc(r.name) + '</option>').join('');
        };
        P.on('#round-list', '[data-decide]', async (b) => {
            const ids = $$('.rpick-' + b.dataset.decide + ':checked').map((c) => Number(c.value));
            if (!ids.length) return P.toast('Select candidates first', 'warning');
            const remarks = await P.prompt(b.dataset.status === 'CLEARED' ? 'Clear candidates' : 'Reject candidates', 'Remarks (optional)', '');
            if (remarks === null) return;
            try { await P.post('/api/placement/rounds/' + b.dataset.decide + '/results', { applicationIds: ids, status: b.dataset.status, remarks }); P.toast('Results saved'); loadRounds(); } catch (e) { P.fail(e); }
        });
        P.on('#round-list', '[data-link]', async (b) => {
            const tests = await P.get('/api/assessment/tests');
            P.modal('Link an online test', '<select class="form-select" id="lt">' + tests.map((t) => '<option value="' + t.id + '">#' + t.id + ' ' + esc(t.title) + ' (' + P.title(t.status) + ')</option>').join('') +
                '</select><div class="form-text">Candidates in this round can take the linked test; results move them on automatically using the cut-off.</div>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Link', onClick: async (m) => {
                await P.put('/api/placement/rounds/' + b.dataset.link + '/test', { assessmentTestId: Number(m.querySelector('#lt').value) });
                loadRounds();
            } }]);
        });

        // ---- slots
        const loadSlots = async () => {
            const slots = await P.get('/api/placement/jobs/' + id + '/slots');
            P.table('#slots', [
                { label: 'Round', render: (s) => esc(s.roundName) },
                { label: 'Time', render: (s) => P.fmtDateTime(s.startTime) + ' - ' + new Date(s.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) },
                { label: 'Location', render: (s) => esc(s.location || '-') },
                { label: 'Booked by', render: (s) => (s.booked ? esc(s.bookedByName) : '<span class="text-muted">free</span>') },
                { label: '', cls: 'text-end', render: (s) => '<button class="btn btn-sm btn-outline-danger" data-del-slot="' + s.id + '"><i class="bi bi-trash"></i></button>' }
            ], slots, 'No slots yet');
        };
        $('#slot-form').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const body = P.formData(ev.target);
            body.roundId = Number(body.roundId);
            try { const r = await P.post('/api/placement/jobs/' + id + '/slots', body); P.toast(r.length + ' slots created'); loadSlots(); } catch (e) { P.fail(e); }
        });
        P.on('#slots', '[data-del-slot]', async (b) => { try { await P.del('/api/placement/slots/' + b.dataset.delSlot); loadSlots(); } catch (e) { P.fail(e); } });

        // ---- selected + offers
        const loadSelected = async () => {
            const sel = await P.get('/api/placement/jobs/' + id + '/applicants?status=SELECTED');
            const offers = (await P.get('/api/placement/offers')).filter((o) => String(o.jobId) === String(id));
            P.table('#selected', [
                { label: 'Student', render: (a) => esc(a.application.studentName) + '<div class="small text-muted">' + esc(a.application.regNo) + '</div>' },
                { label: 'Offer', render: (a) => { const o = offers.find((x) => x.applicationId === a.application.id); return o ? P.badge(o.status) + ' ' + P.money(o.ctcLpa) : '<span class="text-muted">none</span>'; } },
                { label: '', cls: 'text-end', render: (a) => (offers.some((x) => x.applicationId === a.application.id && x.status !== 'DECLINED' && x.status !== 'EXPIRED') ? ''
                    : '<button class="btn btn-sm btn-primary" data-offer="' + a.application.id + '">Issue offer</button>') }
            ], sel, 'No selected candidates yet');
        };
        P.on('#selected', '[data-offer]', (b) => {
            const dl = new Date(Date.now() + 7 * 86400000).toISOString().substring(0, 16);
            P.modal('Issue offer', '<form id="of"><div class="mb-2"><label class="form-label">Role</label><input class="form-control" name="role" value="' + esc(job.title) + '"></div>' +
                '<div class="mb-2"><label class="form-label">CTC (LPA)</label><input class="form-control" type="number" step="0.01" name="ctcLpa" value="' + esc(job.ctcLpa == null ? '' : job.ctcLpa) + '"></div>' +
                '<div class="mb-2"><label class="form-label">Respond by</label><input class="form-control" type="datetime-local" name="responseDeadline" value="' + dl + '" required></div>' +
                '<div class="mb-2"><label class="form-label">Offer letter (PDF, optional)</label><input class="form-control" type="file" name="file" accept=".pdf"></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Issue', onClick: async (m) => {
                const f = m.querySelector('#of');
                if (!f.reportValidity()) return false;
                const fd = new FormData(f);
                if (!f.file.files.length) fd.delete('file');
                if (!fd.get('ctcLpa')) fd.delete('ctcLpa');
                await P.post('/api/placement/applications/' + b.dataset.offer + '/offer', fd);
                P.toast('Offer issued - the student has been notified');
                loadSelected();
            } }]);
        });

        document.addEventListener('tab-shown', (ev) => {
            if (ev.detail === 'rounds') loadRounds();
            if (ev.detail === 'slots') { loadRounds(); loadSlots(); }
            if (ev.detail === 'selected') loadSelected();
        });
    });

    // ================================================================= interview scorecards

    P.page('interview-board', async () => {
        const roundId = $('#round-id').value;
        const load = async () => {
            const b = await P.get('/api/interviews/rounds/' + roundId);
            $('#head').innerHTML = '<div><div class="text-muted">' + esc(b.jobTitle) + '</div><h1>' + esc(b.roundName) + '</h1><div class="sub">' + P.title(b.roundType) +
                ' · structured scorecards (technical 35%, problem solving 30%, communication 20%, culture fit 15%)</div></div>';
            P.table('#pending', [
                { label: 'Candidate', render: (c) => '<span class="fw-semibold">' + esc(c.name) + '</span><div class="small text-muted">' + esc(c.regNo) + ' · ' + esc(c.branch) + '</div>' },
                { label: 'CGPA', render: (c) => esc(c.cgpa) },
                { label: 'Skills', render: (c) => P.chips((c.skills || []).slice(0, 5)) },
                { label: 'Draft', render: (c) => (c.evaluation ? P.badge('UNDER_REVIEW') + ' ' + c.evaluation.overallScore : '-') },
                { label: '', cls: 'text-end', render: (c) => '<button class="btn btn-sm btn-primary" data-eval="' + c.studentUserId + '">Scorecard</button>' }
            ], b.pending, 'Nobody is waiting in this round');
            P.table('#evaluated', [
                { label: 'Candidate', render: (e) => esc(e.studentName) + '<div class="small text-muted">' + esc(e.regNo) + '</div>' },
                { label: 'Scores (T/P/C/F)', render: (e) => e.technical + ' / ' + e.problemSolving + ' / ' + e.communication + ' / ' + e.cultureFit },
                { label: 'Overall', render: (e) => '<strong>' + e.overallScore + '</strong>' },
                { label: 'Result', render: (e) => P.badge(e.result) },
                { label: 'Interviewer', render: (e) => esc(e.interviewerName || '') }
            ], b.evaluated, 'No scorecards yet');
            state.pending = b.pending;
        };
        const state = {};
        P.on('#pending', '[data-eval]', (btn) => {
            const c = state.pending.find((x) => x.studentUserId === btn.dataset.eval);
            const e = c.evaluation || {};
            const slider = (name, label) => '<div class="col-md-6"><label class="form-label small">' + label + ': <strong id="v-' + name + '">' + (e[name] || 5) + '</strong>/10</label>' +
                '<input type="range" class="form-range" min="1" max="10" name="' + name + '" value="' + (e[name] || 5) + '"></div>';
            const m = P.modal('Scorecard: ' + c.name, '<form id="sc" class="row g-2">' + slider('technical', 'Technical knowledge') + slider('problemSolving', 'Problem solving') +
                slider('communication', 'Communication') + slider('cultureFit', 'Culture fit') +
                '<div class="col-12"><label class="form-label small">Strengths</label><textarea class="form-control" name="strengths" rows="2">' + esc(e.strengths || '') + '</textarea></div>' +
                '<div class="col-12"><label class="form-label small">Concerns</label><textarea class="form-control" name="concerns" rows="2">' + esc(e.concerns || '') + '</textarea></div>' +
                '<div class="col-12"><label class="form-label small">Feedback for the student</label><textarea class="form-control" name="feedback" rows="2">' + esc(e.feedback || '') + '</textarea></div>' +
                '<div class="col-md-6"><label class="form-label small">Interviewer</label><input class="form-control" name="interviewerName" value="' + esc(e.interviewerName || '') + '"></div>' +
                '<div class="col-md-6"><label class="form-label small">Decision</label><select class="form-select" name="result"><option value="UNDER_REVIEW">Save as draft</option>' +
                '<option value="CLEARED">Cleared</option><option value="REJECTED">Rejected</option><option value="ABSENT">Absent</option></select></div>' +
                '<div class="col-12 form-check ms-2"><input class="form-check-input" type="checkbox" name="shareFeedback" id="share"><label class="form-check-label small" for="share">Share written feedback with the student</label></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Save', onClick: async (el) => {
                const body = P.formData(el.querySelector('#sc'));
                ['technical', 'problemSolving', 'communication', 'cultureFit'].forEach((k) => { body[k] = Number(body[k]); });
                body.roundId = Number(roundId);
                body.studentUserId = c.studentUserId;
                if (body.result !== 'UNDER_REVIEW' && !(await P.confirm('Final decision', 'A final result moves the candidate on (or out) and cannot be changed. Continue?', 'Save'))) return false;
                await P.post('/api/interviews/evaluations', body);
                P.toast('Scorecard saved');
                load();
            } }], { size: 'lg' });
            P.$$('input[type=range]', m.el).forEach((r) => r.addEventListener('input', () => { m.el.querySelector('#v-' + r.name).textContent = r.value; }));
        });
        load();
    });

    // ================================================================= tests

    P.page('staff-tests', async () => {
        const base = P.base();
        $('#new-test').href = base + '/tests/new';
        const load = async () => {
            const tests = await P.get('/api/assessment/tests');
            P.table('#tests', [
                { label: 'Test', render: (t) => '<a class="fw-semibold" href="' + base + '/tests/' + t.id + '">' + esc(t.title) + '</a><div class="small text-muted">#' + t.id + ' · ' + P.title(t.type) +
                    ' · ' + t.durationMinutes + ' min' + (t.proctored ? ' · proctored' : '') + (t.roundId ? ' · round ' + t.roundId : '') + '</div>' },
                { label: 'Status', render: (t) => P.badge(t.status) },
                { label: 'Window', render: (t) => P.fmtDateTime(t.windowStart) + '<div class="small text-muted">to ' + P.fmtDateTime(t.windowEnd) + '</div>' },
                { label: 'Attempts', render: (t) => (t.attempts != null ? t.attempts : '-') },
                { label: '', cls: 'text-end text-nowrap', render: (t) => (t.status === 'DRAFT' ? '<a class="btn btn-sm btn-light" href="' + base + '/tests/' + t.id + '/edit">Edit</a> <button class="btn btn-sm btn-success" data-pub="' + t.id + '">Publish</button> <button class="btn btn-sm btn-outline-danger" data-del="' + t.id + '"><i class="bi bi-trash"></i></button>'
                    : t.status === 'PUBLISHED' ? '<button class="btn btn-sm btn-outline-secondary" data-closet="' + t.id + '">Close</button>' : '') }
            ], tests, 'No tests yet');
        };
        P.on('#tests', '[data-pub]', async (b) => { if (await P.confirm('Publish test', 'Students will see the test in its window. The pool cannot be changed afterwards.', 'Publish', 'btn-success')) { try { await P.post('/api/assessment/tests/' + b.dataset.pub + '/publish'); load(); } catch (e) { P.fail(e); } } });
        P.on('#tests', '[data-closet]', async (b) => { try { await P.post('/api/assessment/tests/' + b.dataset.closet + '/close'); load(); } catch (e) { P.fail(e); } });
        P.on('#tests', '[data-del]', async (b) => { if (await P.confirm('Delete test', 'Delete this draft?', 'Delete', 'btn-danger')) { await P.del('/api/assessment/tests/' + b.dataset.del); load(); } });
        load();
    });

    const CHECKS = ['NO_FACE', 'MULTIPLE_FACES', 'DIFFERENT_PERSON', 'LOOKING_AWAY', 'EXCESSIVE_MOVEMENT', 'PHONE_DETECTED', 'CAMERA_BLOCKED', 'TAB_SWITCH', 'FULLSCREEN_EXIT', 'COPY_PASTE', 'NOISE',
        'SCREEN_SHARE_STOPPED', 'MULTIPLE_MONITORS'];

    P.page('test-form', async () => {
        const id = $('#test-id').value;
        const picked = { mcq: new Map(), coding: new Map() };
        $('#checks').innerHTML = CHECKS.map((c) => '<div class="col-6"><div class="form-check"><input class="form-check-input chk" type="checkbox" value="' + c + '" id="c-' + c + '" checked>' +
            '<label class="form-check-label small" for="c-' + c + '">' + P.title(c) + '</label></div></div>').join('');
        const showPicked = () => {
            $('#mcq-picked').innerHTML = picked.mcq.size ? picked.mcq.size + ' MCQs in the pool: ' + [...picked.mcq.values()].slice(0, 6).map((t) => '<span class="chip">' + esc(t.substring(0, 40)) + '</span>').join('') + (picked.mcq.size > 6 ? '…' : '')
                : '<span class="text-muted">No MCQs picked - leave empty to draw randomly from all approved MCQs.</span>';
            $('#coding-picked').innerHTML = picked.coding.size ? [...picked.coding.entries()].map(([k, v]) => '<span class="chip">' + esc(v.title) + ' · ' + v.points + ' pts</span>').join('') : '<span class="text-muted">No coding questions.</span>';
        };
        if (id) {
            const t = await P.get('/api/assessment/tests/' + id);
            $('#form-title').textContent = 'Edit: ' + t.title;
            P.fillForm($('#test-form'), t);
            $$('.chk').forEach((c) => { c.checked = (t.proctoringChecks || []).includes(c.value); });
            const pool = await P.get('/api/assessment/tests/' + id + '/pool');
            pool.forEach((p) => { if (p.kind === 'MCQ') picked.mcq.set(Number(p.refId), p.title || ''); else picked.coding.set(p.refId, { title: p.title, points: p.points }); });
        } else {
            const now = new Date(Date.now() + 3600000);
            $('[name=windowStart]').value = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().substring(0, 16);
            const end = new Date(now.getTime() + 7 * 86400000);
            $('[name=windowEnd]').value = new Date(end.getTime() - end.getTimezoneOffset() * 60000).toISOString().substring(0, 16);
            if (P.param('roundId')) $('[name=roundId]').value = P.param('roundId');
        }
        showPicked();

        $('#pick-mcqs').addEventListener('click', async () => {
            const page = await P.get('/api/assessment/mcqs?approved=true&size=200');
            const list = P.content(page);
            const m = P.modal('Pick MCQs (' + list.length + ' approved)', '<input class="form-control mb-2" id="mf" placeholder="Filter">' + list.map((q) =>
                '<div class="form-check mcq-opt"><input class="form-check-input" type="checkbox" value="' + q.id + '" id="m' + q.id + '" ' + (picked.mcq.has(q.id) ? 'checked' : '') + '>' +
                '<label class="form-check-label small" for="m' + q.id + '">' + P.badge(q.topic) + ' ' + P.badge(q.difficulty) + ' ' + esc(q.text.substring(0, 140)) + '</label></div>').join(''),
            [{ label: 'Done', onClick: (el) => {
                picked.mcq.clear();
                P.$$('.mcq-opt input:checked', el).forEach((c) => picked.mcq.set(Number(c.value), list.find((q) => q.id === Number(c.value)).text));
                showPicked();
            } }], { size: 'lg' });
            m.el.querySelector('#mf').addEventListener('input', (ev) => {
                const f = ev.target.value.toLowerCase();
                P.$$('.mcq-opt', m.el).forEach((d) => { d.classList.toggle('d-none', !d.textContent.toLowerCase().includes(f)); });
            });
        });
        $('#pick-coding').addEventListener('click', async () => {
            const list = P.content(await P.get('/api/coding/questions?size=100'));
            P.modal('Pick coding questions', list.map((q) => '<div class="d-flex align-items-center gap-2 mb-1 cq"><input class="form-check-input" type="checkbox" value="' + esc(q.id) + '" ' + (picked.coding.has(q.id) ? 'checked' : '') + '>' +
                '<span class="flex-fill small">' + esc(q.title) + ' ' + P.badge(q.difficulty) + ' ' + P.badge(q.visibility) + '</span><input class="form-control form-control-sm" style="width:90px" type="number" min="1" value="' +
                (picked.coding.has(q.id) ? picked.coding.get(q.id).points : 100) + '" title="points"></div>').join(''),
            [{ label: 'Done', onClick: (el) => {
                picked.coding.clear();
                P.$$('.cq', el).forEach((d) => {
                    const c = d.querySelector('input[type=checkbox]');
                    if (c.checked) picked.coding.set(c.value, { title: d.querySelector('span').textContent, points: Number(d.querySelector('input[type=number]').value) || 100 });
                });
                showPicked();
            } }], { size: 'lg' });
        });

        $('#save').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            const f = $('#test-form');
            if (!f.reportValidity()) return;
            const body = P.formData(f);
            body.mcqIds = [...picked.mcq.keys()];
            body.codingQuestions = [...picked.coding.entries()].map(([questionId, v]) => ({ questionId, points: v.points }));
            body.proctoringChecks = $$('.chk:checked').map((c) => c.value);
            try {
                const t = id ? await P.put('/api/assessment/tests/' + id, body) : await P.post('/api/assessment/tests', body);
                P.toast('Saved as draft - publish it from the tests list');
                location.href = P.base() + '/tests/' + t.id;
            } catch (e) { P.fail(e); }
        }));
    });

    P.page('test-detail', async () => {
        const id = $('#test-id').value;
        const base = P.base();
        const t = await P.get('/api/assessment/tests/' + id);
        $('#head').innerHTML = '<div><h1>' + esc(t.title) + ' ' + P.badge(t.status) + '</h1><div class="sub">#' + t.id + ' · ' + P.title(t.type) + ' · ' + t.durationMinutes + ' min · ' +
            P.fmtDateTime(t.windowStart) + ' → ' + P.fmtDateTime(t.windowEnd) + (t.proctored ? ' · proctored (max ' + t.maxViolations + ' warnings)' : '') + '</div></div>' +
            (t.status === 'DRAFT' ? '<a class="btn btn-light" href="' + base + '/tests/' + id + '/edit">Edit</a>' : '');
        const results = await P.get('/api/assessment/tests/' + id + '/results');
        const done = results.filter((r) => r.percent != null);
        const avg = done.length ? done.reduce((s, r) => s + Number(r.percent), 0) / done.length : null;
        $('#stats').innerHTML = stat('Attempts', results.length, 'people', 'bg-soft-blue') + stat('Average', avg == null ? '-' : P.pct(avg), 'percent', 'bg-soft-purple') +
            stat('Passed', results.filter((r) => r.passed).length, 'check2-circle', 'bg-soft-green') + stat('Flagged / ended', results.filter((r) => r.proctoringFlagged || r.status === 'TERMINATED').length, 'flag', 'bg-soft-red');
        P.table('#results', [
            { label: 'Student', render: (r) => esc(r.studentName) + '<div class="small text-muted">' + esc(r.studentLogin || '') + '</div>' },
            { label: 'Status', render: (r) => P.badge(r.status) },
            { label: 'MCQ', render: (r) => esc(r.mcqScore == null ? '-' : r.mcqScore) },
            { label: 'Coding', render: (r) => esc(r.codingScore == null ? '-' : r.codingScore) },
            { label: '%', render: (r) => (r.percent != null ? '<strong>' + P.pct(r.percent) + '</strong>' : '-') },
            { label: 'Result', render: (r) => (r.passed == null ? '-' : r.passed ? '<span class="badge text-bg-success">Pass</span>' : '<span class="badge text-bg-danger">Fail</span>') },
            { label: 'Trust', render: (r) => (r.trustScore != null ? '<span class="badge ' + (r.trustScore >= 80 ? 'text-bg-success' : r.trustScore >= 50 ? 'text-bg-warning' : 'text-bg-danger') + '">' + r.trustScore + '</span>' : '-') +
                (r.proctoringFlagged ? ' <i class="bi bi-flag-fill text-danger" title="Flagged"></i>' : '') },
            { label: '', cls: 'text-end', render: (r) => (t.proctored ? '<a class="btn btn-sm btn-light" href="' + base + '/attempts/' + r.attemptId + '/proctoring">Proctoring</a>' : '') }
        ], results, 'No attempts yet');
        const pool = await P.get('/api/assessment/tests/' + id + '/pool');
        P.table('#pool', [
            { label: 'Kind', render: (p) => P.badge(p.kind) },
            { label: 'Question', render: (p) => esc((p.title || p.refId).substring(0, 160)) },
            { label: 'Points', render: (p) => p.points }
        ], pool, 'The pool is drawn randomly from the approved MCQ bank');
        $('#plag').addEventListener('click', () => { location.href = base + '/plagiarism?scope=ASSESSMENT&scopeId=' + id; });
    });

    // ================================================================= proctoring report

    P.page('proctoring-report', async () => {
        const id = $('#attempt-id').value;
        const r = await P.get('/api/proctoring/reports/attempts/' + id);
        $('#report').innerHTML = '<div class="page-head"><div><h1>Proctoring report</h1><div class="sub">Attempt #' + r.attemptId + ' · test #' + r.testId + ' · ' + P.badge(r.status) + ' · ' +
            P.fmtDateTime(r.startedAt) + ' → ' + P.fmtDateTime(r.endedAt) + '</div></div><a class="btn btn-light" href="' + P.base() + '/tests/' + r.testId + '">Back to test</a></div>' +
            '<div class="row g-3 mb-3"><div class="col-md-3"><div class="card h-100"><div class="card-body text-center">' + P.ring(r.trustScore, 'trust score') +
            (r.flagged ? '<div class="alert alert-danger small mt-3 mb-0"><strong>Flagged:</strong> ' + esc(r.flagReason || '') + '</div>' : '<div class="text-success small mt-3">Not flagged</div>') + '</div></div></div>' +
            '<div class="col-md-9"><div class="row g-3">' + stat('High severity', r.highCount, 'exclamation-octagon', 'bg-soft-red') + stat('Medium', r.mediumCount, 'exclamation-triangle', 'bg-soft-amber') +
            stat('Low', r.lowCount, 'info-circle', 'bg-soft-blue') + stat('Frames analysed', r.framesAnalysed, 'camera', 'bg-soft-purple') +
            '<div class="col-12"><div class="stat small">Identity: ' + (r.identityVerified == null ? 'not checked' : r.identityVerified ? '<span class="text-success">verified</span>' : '<span class="text-danger">not matched</span>') +
            (r.identitySimilarity != null ? ' (similarity ' + Math.round(r.identitySimilarity * 100) + '%, ' + esc(r.identityMethod) + ')' : '') + '</div></div></div></div></div>' +
            '<div class="card"><div class="card-header">Timeline of violations</div><div class="card-body p-0" id="viol"></div></div>';
        P.table('#viol', [
            { label: 'Time', render: (v) => P.fmtDateTime(v.occurredAt) },
            { label: 'Type', render: (v) => P.title(v.type) },
            { label: 'Severity', render: (v) => P.badge(v.severity) },
            { label: 'Source', render: (v) => P.title(v.source) },
            { label: 'Detail', render: (v) => '<span class="small">' + esc(v.detail || '') + '</span>' },
            { label: '', cls: 'text-end', render: (v) => (v.hasSnapshot ? '<button class="btn btn-sm btn-light" data-snap="' + v.id + '"><i class="bi bi-image"></i></button>' : '') }
        ], r.violations, 'No violations - a clean attempt');
        P.on('#viol', '[data-snap]', async (b) => {
            const url = await P.imageUrl('/api/proctoring/reports/violations/' + b.dataset.snap + '/snapshot');
            P.modal('Snapshot', url ? '<img class="img-fluid rounded" src="' + url + '" alt="snapshot">' : 'Snapshot not available (it may have been deleted by the retention policy).');
        });
    });

    // ================================================================= MCQ bank

    P.page('mcqs', async () => {
        hideStaffOnly();
        if (P.param('approved')) $('#approved').value = P.param('approved');
        let page = 0;
        const load = async () => {
            const res = await P.get('/api/assessment/mcqs' + P.qs({ q: $('#q').value, topic: $('#topic').value, difficulty: $('#difficulty').value, approved: $('#approved').value, page }));
            P.table('#list', [
                { label: 'Question', render: (m) => '<div class="small pre-wrap">' + esc(m.text.length > 220 ? m.text.substring(0, 220) + '…' : m.text) + '</div><div class="small text-muted mt-1">Answer: ' +
                    esc(m.options[m.correctIndex] || '') + '</div>' },
                { label: 'Topic', render: (m) => P.title(m.topic) + '<div class="small text-muted">' + esc(m.subtopic || '') + '</div>' },
                { label: 'Level', render: (m) => P.badge(m.difficulty) },
                { label: 'Source', render: (m) => (m.source === 'AI' ? '<span class="badge text-bg-info">AI</span>' : 'Manual') + ' ' + (m.approved ? '' : '<span class="badge text-bg-warning">Pending</span>') },
                { label: '', cls: 'text-end text-nowrap', render: (m) => (!m.approved && P.isStaff() ? '<button class="btn btn-sm btn-success" data-approve="' + m.id + '">Approve</button> ' : '') +
                    '<button class="btn btn-sm btn-light" data-edit="' + m.id + '">Edit</button> <button class="btn btn-sm btn-outline-danger" data-del="' + m.id + '"><i class="bi bi-trash"></i></button>' }
            ], P.content(res), 'No questions found');
            P.pager('#pager', res, (p) => { page = p; load(); });
            state.items = P.content(res);
        };
        const state = {};
        let t;
        $('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 300); });
        ['#topic', '#difficulty', '#approved'].forEach((s) => $(s).addEventListener('change', () => { page = 0; load(); }));
        load();

        const editor = (m) => {
            m = m || { topic: 'QUANTITATIVE', difficulty: 'MEDIUM', options: ['', '', '', ''], correctIndex: 0 };
            P.modal(m.id ? 'Edit MCQ' : 'New MCQ', '<form id="mf" class="row g-2"><div class="col-md-4"><label class="form-label small">Topic</label><select class="form-select" name="topic">' +
                ['QUANTITATIVE', 'LOGICAL', 'VERBAL', 'TECHNICAL'].map((x) => '<option ' + (m.topic === x ? 'selected' : '') + '>' + x + '</option>').join('') + '</select></div>' +
                '<div class="col-md-4"><label class="form-label small">Subtopic</label><input class="form-control" name="subtopic" value="' + esc(m.subtopic || '') + '"></div>' +
                '<div class="col-md-4"><label class="form-label small">Difficulty</label><select class="form-select" name="difficulty">' + ['EASY', 'MEDIUM', 'HARD'].map((x) => '<option ' + (m.difficulty === x ? 'selected' : '') + '>' + x + '</option>').join('') + '</select></div>' +
                '<div class="col-12"><label class="form-label small">Question</label><textarea class="form-control" name="text" rows="3" required>' + esc(m.text || '') + '</textarea></div>' +
                m.options.map((o, i) => '<div class="col-md-6"><div class="input-group input-group-sm"><span class="input-group-text"><input type="radio" name="correctIndex" value="' + i + '" ' + (m.correctIndex === i ? 'checked' : '') +
                    ' title="correct"></span><input class="form-control opt" value="' + esc(o) + '" placeholder="Option ' + String.fromCharCode(65 + i) + '"></div></div>').join('') +
                '<div class="col-12"><label class="form-label small">Explanation</label><textarea class="form-control" name="explanation" rows="2">' + esc(m.explanation || '') + '</textarea></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Save', onClick: async (el) => {
                const f = el.querySelector('#mf');
                if (!f.reportValidity()) return false;
                const body = P.formData(f);
                body.correctIndex = Number(body.correctIndex);
                body.options = P.$$('.opt', f).map((o) => o.value.trim()).filter(Boolean);
                if (body.options.length < 2) { P.toast('Add at least two options', 'warning'); return false; }
                if (m.id) await P.put('/api/assessment/mcqs/' + m.id, body); else await P.post('/api/assessment/mcqs', body);
                load();
            } }], { size: 'lg' });
        };
        $('#add').addEventListener('click', () => editor());
        P.on('#list', '[data-edit]', (b) => editor(state.items.find((x) => String(x.id) === b.dataset.edit)));
        P.on('#list', '[data-approve]', async (b) => { await P.post('/api/assessment/mcqs/' + b.dataset.approve + '/approve'); load(); });
        P.on('#list', '[data-del]', async (b) => { if (await P.confirm('Delete', 'Delete this question?', 'Delete', 'btn-danger')) { try { await P.del('/api/assessment/mcqs/' + b.dataset.del); load(); } catch (e) { P.fail(e); } } });
        $('#generate').addEventListener('click', () => {
            P.modal('Generate MCQs with AI', '<form id="gf" class="row g-2"><div class="col-md-6"><label class="form-label small">Topic</label><select class="form-select" name="topic"><option>QUANTITATIVE</option><option>LOGICAL</option><option>VERBAL</option><option>TECHNICAL</option></select></div>' +
                '<div class="col-md-6"><label class="form-label small">Subtopic</label><input class="form-control" name="subtopic" placeholder="e.g. time and work, OS scheduling"></div>' +
                '<div class="col-md-6"><label class="form-label small">Difficulty</label><select class="form-select" name="difficulty"><option>EASY</option><option selected>MEDIUM</option><option>HARD</option></select></div>' +
                '<div class="col-md-6"><label class="form-label small">How many</label><input class="form-control" type="number" name="count" value="5" min="1" max="10"></div></form>' +
                '<div class="form-text">Generated questions are saved as pending - review and approve them before use.</div>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Generate', onClick: async (el) => {
                const r = await P.post('/api/assessment/mcqs/generate', P.formData(el.querySelector('#gf')));
                P.toast(r.length + ' questions generated - review them below');
                $('#approved').value = 'false';
                page = 0;
                load();
            } }]);
        });
    });

    // ================================================================= coding questions

    P.page('questions', async () => {
        const base = P.base();
        $('#new-q').href = base + '/questions/new';
        let page = 0;
        const load = async () => {
            const res = await P.get('/api/coding/questions' + P.qs({ q: $('#q').value, difficulty: $('#difficulty').value, page }));
            P.table('#list', [
                { label: 'Title', render: (q) => '<a href="' + base + '/questions/' + encodeURIComponent(q.id) + '">' + esc(q.title) + '</a><div>' + P.chips((q.tags || []).slice(0, 4)) + '</div>' },
                { label: 'Difficulty', render: (q) => P.badge(q.difficulty) },
                { label: 'Visibility', render: (q) => P.badge(q.visibility === 'PRACTICE' ? 'OPEN' : 'CLOSED', P.title(q.visibility)) },
                { label: 'Solved / tried', render: (q) => q.solvedCount + ' / ' + q.attemptCount }
            ], P.content(res), 'No questions');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        let t;
        $('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 300); });
        $('#difficulty').addEventListener('change', () => { page = 0; load(); });
        load();
    });

    P.page('question-form', async () => {
        const id = $('#question-id').value;
        const addTc = (tc) => {
            tc = tc || { hidden: true };
            const div = document.createElement('div');
            div.className = 'row g-2 border rounded p-2 mb-2 tc-row';
            if (tc.id) div.dataset.id = tc.id;
            div.innerHTML = '<div class="col-md-5"><label class="form-label small">Input</label><textarea class="form-control mono" rows="3" data-f="input"></textarea></div>' +
                '<div class="col-md-5"><label class="form-label small">Expected output</label><textarea class="form-control mono" rows="3" data-f="expectedOutput"></textarea></div>' +
                '<div class="col-md-2 d-flex flex-column justify-content-between"><div class="form-check"><input class="form-check-input" type="checkbox" data-f="hidden"><label class="form-check-label small">Hidden</label></div>' +
                '<button type="button" class="btn btn-sm btn-outline-danger" data-remove><i class="bi bi-trash"></i></button></div>';
            div.querySelector('[data-f=input]').value = tc.input || '';
            div.querySelector('[data-f=expectedOutput]').value = tc.expectedOutput || '';
            div.querySelector('[data-f=hidden]').checked = !!tc.hidden;
            div.querySelector('[data-remove]').addEventListener('click', async () => {
                if (div.dataset.id) { try { await P.del('/api/coding/testcases/' + div.dataset.id); } catch (e) { P.fail(e); return; } }
                div.remove();
            });
            $('#testcases').appendChild(div);
        };
        const readTc = (d) => ({ input: d.querySelector('[data-f=input]').value, expectedOutput: d.querySelector('[data-f=expectedOutput]').value, hidden: d.querySelector('[data-f=hidden]').checked });
        $('#add-tc').addEventListener('click', () => addTc());
        if (id) {
            const q = await P.get('/api/coding/questions/' + encodeURIComponent(id));
            $('#form-title').textContent = 'Edit: ' + q.title;
            P.fillForm($('#q-form'), q);
            (await P.get('/api/coding/questions/' + encodeURIComponent(id) + '/testcases')).forEach(addTc);
        } else {
            addTc({ hidden: false });
            addTc({ hidden: true });
        }
        $('#save').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            const f = $('#q-form');
            if (!f.reportValidity()) return;
            const body = P.formData(f);
            const rows = $$('.tc-row');
            try {
                if (id) {
                    await P.put('/api/coding/questions/' + encodeURIComponent(id), Object.assign(body, { testcases: null }));
                    for (const d of rows) {
                        if (d.dataset.id) await P.put('/api/coding/testcases/' + d.dataset.id, readTc(d));
                        else await P.post('/api/coding/questions/' + encodeURIComponent(id) + '/testcases', readTc(d));
                    }
                    P.toast('Saved');
                } else {
                    body.testcases = rows.map(readTc).filter((t) => t.expectedOutput !== '');
                    const q = await P.post('/api/coding/questions', body);
                    location.href = P.base() + '/questions/' + encodeURIComponent(q.id);
                }
            } catch (e) { P.fail(e); }
        }));
    });

    // ================================================================= plagiarism

    P.page('plagiarism', async () => {
        const f = $('#plag-form');
        if (P.param('scope')) f.scope.value = P.param('scope');
        if (P.param('scopeId')) f.scopeId.value = P.param('scopeId');
        const show = (c) => {
            $('#plag-result').innerHTML = '<div class="card"><div class="card-header">Check #' + c.id + ' · ' + c.submissionsCompared + ' submissions · ' + c.pairsFlagged + ' pair(s) ≥ ' + Math.round(c.threshold * 100) +
                '% · engine ' + esc(c.engine) + '</div><div class="card-body p-0" id="pairs"></div></div>';
            P.table('#pairs', [
                { label: 'Similarity', render: (p) => '<span class="badge ' + (p.similarity >= 0.9 ? 'text-bg-danger' : 'text-bg-warning') + '">' + Math.round(p.similarity * 100) + '%</span>' },
                { label: 'Question', render: (p) => '<span class="mono small">' + esc(p.questionId.substring(0, 8)) + '</span>' },
                { label: 'Student A', render: (p) => esc(p.userA) },
                { label: 'Student B', render: (p) => esc(p.userB) },
                { label: '', cls: 'text-end', render: (p) => '<button class="btn btn-sm btn-light" data-pair="' + p.id + '">Compare</button>' }
            ], c.pairs || [], 'No suspicious pairs found.');
        };
        f.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const body = P.formData(f);
            body.threshold = body.threshold == null ? null : body.threshold / 100; // the API uses a 0..1 fraction
            await P.busy(f.querySelector('button[type=submit]'), async () => {
                try { show(await P.post('/api/submissions/plagiarism/checks', body)); } catch (e) { P.fail(e); }
            });
        });
        $('#history').addEventListener('click', async () => {
            if (!f.scopeId.value) return P.toast('Enter the test or contest ID', 'warning');
            const list = await P.get('/api/submissions/plagiarism/checks' + P.qs({ scope: f.scope.value, scopeId: f.scopeId.value }));
            $('#plag-result').innerHTML = '<div class="card"><div class="card-body p-0" id="hist"></div></div>';
            P.table('#hist', [
                { label: 'When', render: (c) => P.fmtDateTime(c.createdAt) },
                { label: 'Compared', render: (c) => c.submissionsCompared },
                { label: 'Flagged', render: (c) => c.pairsFlagged },
                { label: '', cls: 'text-end', render: (c) => '<button class="btn btn-sm btn-light" data-check="' + c.id + '">Open</button>' }
            ], list, 'No checks run yet');
        });
        P.on('#plag-result', '[data-check]', async (b) => show(await P.get('/api/submissions/plagiarism/checks/' + b.dataset.check)));
        P.on('#plag-result', '[data-pair]', async (b) => {
            const d = await P.get('/api/submissions/plagiarism/pairs/' + b.dataset.pair);
            P.modal('Similarity ' + Math.round(d.pair.similarity * 100) + '%', '<div class="row g-2"><div class="col-md-6"><div class="small fw-semibold">' + esc(d.pair.userA) + ' · ' + esc(d.a.language) +
                '</div><pre class="bg-dark text-light p-2 rounded mono small" style="max-height:60vh">' + esc(d.a.sourceCode) + '</pre></div><div class="col-md-6"><div class="small fw-semibold">' +
                esc(d.pair.userB) + ' · ' + esc(d.b.language) + '</div><pre class="bg-dark text-light p-2 rounded mono small" style="max-height:60vh">' + esc(d.b.sourceCode) + '</pre></div></div>', null, { size: 'xl' });
        });
        if (P.param('scopeId')) f.requestSubmit();
    });

    // ================================================================= calendar (all roles)

    P.page('calendar', async () => {
        hideStaffOnly();
        const student = P.role() === 'STUDENT';
        let month = new Date();
        month.setDate(1);
        const TYPES = { PRE_PLACEMENT_TALK: 'Pre-placement talk', TEST: 'Online test', INTERVIEW: 'Interviews', DRIVE: 'Campus drive', WORKSHOP: 'Workshop', OTHER: 'Other' };
        $('#legend').innerHTML = Object.entries(TYPES).map(([k, v]) => '<span><span class="legend-dot ev-' + k + '"></span>' + v + '</span>').join('');
        const dayKey = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
        const time = (s) => new Date(s).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
        let selected = dayKey(new Date());

        const showDay = (key) => {
            selected = key;
            $$('.cal-day').forEach((c) => c.classList.toggle('selected', c.dataset.day === key));
            const [y, m, d] = key.split('-').map(Number);
            $('#day-label').textContent = new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
            const list = (state.events || []).filter((e) => dayKey(new Date(e.startTime)) === key);
            $('#day-events').innerHTML = list.length ? list.map((e) => '<div class="day-event"><div class="d-flex justify-content-between gap-2">' +
                '<span class="badge ev-' + e.type + '">' + esc(TYPES[e.type] || P.title(e.type)) + '</span><span class="small text-muted">' + time(e.startTime) +
                (e.endTime ? ' - ' + time(e.endTime) : '') + '</span></div><div class="fw-semibold mt-1">' + esc(e.title) + '</div>' +
                (e.companyName ? '<div class="small"><i class="bi bi-building me-1"></i>' + esc(e.companyName) + '</div>' : '') +
                (e.location ? '<div class="small"><i class="bi bi-geo-alt me-1"></i>' + esc(e.location) + '</div>' : '') +
                (e.description ? '<div class="small text-muted mt-1">' + esc(e.description) + '</div>' : '') +
                (e.jobId && student ? '<a class="small" href="/student/jobs/' + e.jobId + '">View the job</a>' : '') + '</div>').join('')
                : P.empty('Nothing scheduled on this day', 'calendar');
        };

        const grid = (events) => {
            const first = new Date(month.getFullYear(), month.getMonth(), 1);
            const start = new Date(first);
            start.setDate(1 - ((first.getDay() + 6) % 7)); // weeks start on Monday
            const byDay = {};
            events.forEach((e) => { (byDay[dayKey(new Date(e.startTime))] = byDay[dayKey(new Date(e.startTime))] || []).push(e); });
            const today = dayKey(new Date());
            let html = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => '<div class="dow">' + d + '</div>').join('');
            for (let i = 0; i < 42; i++) {
                const d = new Date(start);
                d.setDate(start.getDate() + i);
                if (i === 35 && d.getMonth() !== month.getMonth()) break;
                const key = dayKey(d);
                const list = byDay[key] || [];
                html += '<div class="cal-day' + (d.getMonth() !== month.getMonth() ? ' other' : '') + (key === today ? ' today' : '') + '" data-day="' + key + '"><div class="num">' + d.getDate() + '</div>' +
                    list.slice(0, 3).map((e) => '<span class="cal-ev ev-' + e.type + '" title="' + esc(e.title) + '">' + time(e.startTime) + ' ' + esc(e.companyName || e.title) + '</span>').join('') +
                    (list.length > 3 ? '<div class="cal-more">+' + (list.length - 3) + ' more</div>' : '') + '</div>';
            }
            $('#cal-grid').innerHTML = html;
        };

        const load = async () => {
            const from = new Date(month.getFullYear(), month.getMonth(), 1);
            const to = new Date(month.getFullYear(), month.getMonth() + 1, 1);
            const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 19);
            $('#month-label').textContent = month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
            const events = await P.get('/api/placement/events' + P.qs({ from: iso(from), to: iso(to) }));
            state.events = events;
            grid(events);
            const inMonth = selected.startsWith(dayKey(from).substring(0, 7));
            showDay(inMonth ? selected : (events.length ? dayKey(new Date(events[0].startTime)) : dayKey(from)));
            P.table('#events', [
                { label: 'When', render: (e) => '<span class="fw-semibold">' + P.fmtDateTime(e.startTime) + '</span>' + (e.endTime ? '<div class="small text-muted">until ' + P.fmtDateTime(e.endTime) + '</div>' : '') },
                { label: 'Event', render: (e) => '<span class="fw-semibold">' + esc(e.title) + '</span> ' + P.badge(e.type === 'DRIVE' ? 'OPEN' : 'NORMAL', P.title(e.type)) +
                    (e.description ? '<div class="small text-muted">' + esc(e.description) + '</div>' : '') },
                { label: 'Company', render: (e) => esc(e.companyName || '-') },
                { label: 'Where', render: (e) => esc(e.location || '-') },
                { label: '', cls: 'text-end text-nowrap', render: (e) => student
                    ? (e.attended ? '<span class="badge text-bg-success">Attended</span>' : '<button class="btn btn-sm btn-outline-primary" data-checkin="' + e.id + '">Check in</button>')
                    : (e.attendanceCode ? '<span class="mono small me-2" title="Attendance code">' + esc(e.attendanceCode) + '</span>' : '') + '<button class="btn btn-sm btn-light" data-att="' + e.id + '">Attendance</button> ' +
                        (e.canEdit ? '<button class="btn btn-sm btn-light" data-edit-ev="' + e.id + '">Edit</button> <button class="btn btn-sm btn-outline-danger" data-del-ev="' + e.id + '"><i class="bi bi-trash"></i></button>' : '') }
            ], events, 'No events this month');
        };
        const state = {};
        $('#prev-month').addEventListener('click', () => { month.setMonth(month.getMonth() - 1); load(); });
        $('#next-month').addEventListener('click', () => { month.setMonth(month.getMonth() + 1); load(); });
        $('#this-month').addEventListener('click', () => { month = new Date(); month.setDate(1); selected = dayKey(new Date()); load(); });
        P.on('#cal-grid', '.cal-day', (el) => showDay(el.dataset.day));
        load();

        P.on('#events', '[data-checkin]', async (b) => {
            const code = await P.prompt('Check in', 'Attendance code announced at the event', '', true);
            if (!code) return;
            try { await P.post('/api/placement/events/' + b.dataset.checkin + '/check-in', { code }); P.toast('Attendance recorded'); load(); } catch (e) { P.fail(e); }
        });
        P.on('#events', '[data-att]', async (b) => {
            const list = await P.get('/api/placement/events/' + b.dataset.att + '/attendance');
            P.modal('Attendance (' + list.length + ')', list.length ? '<ul class="small">' + list.map((a) => '<li class="mono">' + esc(a.studentUserId) + ' · ' + P.fmtDateTime(a.markedAt) + ' · ' + esc(a.markedBy || '') + '</li>').join('') + '</ul>' : 'Nobody has checked in yet.');
        });
        P.on('#events', '[data-del-ev]', async (b) => { if (await P.confirm('Delete event', 'Delete this event?', 'Delete', 'btn-danger')) { await P.del('/api/placement/events/' + b.dataset.delEv); load(); } });
        const editor = (ev) => {
            ev = ev || { type: 'PRE_PLACEMENT_TALK' };
            P.modal(ev.id ? 'Edit event' : 'New event', '<form id="ef" class="row g-2"><div class="col-md-8"><label class="form-label small">Title</label><input class="form-control" name="title" required></div>' +
                '<div class="col-md-4"><label class="form-label small">Type</label><select class="form-select" name="type">' + ['PRE_PLACEMENT_TALK', 'DRIVE', 'TEST', 'INTERVIEW', 'WORKSHOP', 'OTHER'].map((t) => '<option value="' + t + '">' + P.title(t) + '</option>').join('') + '</select></div>' +
                '<div class="col-md-6"><label class="form-label small">Starts</label><input class="form-control" type="datetime-local" name="startTime" required></div>' +
                '<div class="col-md-6"><label class="form-label small">Ends</label><input class="form-control" type="datetime-local" name="endTime"></div>' +
                '<div class="col-md-6"><label class="form-label small">Location</label><input class="form-control" name="location"></div>' +
                '<div class="col-md-6"><label class="form-label small">Job ID (optional)</label><input class="form-control" type="number" name="jobId"></div>' +
                '<div class="col-12"><label class="form-label small">Description</label><textarea class="form-control" name="description" rows="2"></textarea></div>' +
                '<div class="col-12 form-check ms-2"><input class="form-check-input" type="checkbox" name="generateAttendanceCode" id="gac" checked><label class="form-check-label small" for="gac">Generate an attendance code for student check-in</label></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Save', onClick: async (el) => {
                const f = el.querySelector('#ef');
                if (!f.reportValidity()) return false;
                const body = P.formData(f);
                if (ev.id) await P.put('/api/placement/events/' + ev.id, body); else await P.post('/api/placement/events', body);
                load();
            } }]);
            P.fillForm(P.$('#ef'), ev);
        };
        if ($('#new-event')) $('#new-event').addEventListener('click', () => editor());
        P.on('#events', '[data-edit-ev]', (b) => editor(state.events.find((e) => String(e.id) === b.dataset.editEv)));
    });

    // ================================================================= help desk (students raise, placement office resolves)

    P.page('queries', async () => {
        const staff = P.isStaff();
        hideStaffOnly();
        if (staff) $$('.student-only').forEach((e) => e.classList.add('d-none'));
        $('#q-title').textContent = staff ? 'Student queries' : 'Help desk';
        $('#q-sub').textContent = staff
            ? 'Questions and issues raised by students. Reply, resolve, and the student is notified immediately.'
            : 'Ask the placement office about drives, eligibility, tests, offers or any issue. You are notified when they reply.';
        const categories = await P.get('/api/notifications/queries/categories');
        $('#q-category').innerHTML += categories.map((c) => '<option value="' + c.value + '">' + esc(c.label) + '</option>').join('');
        const state = { status: 'PENDING', page: 0, selected: Number(P.param('id')) || null };

        const statusBadge = (q) => q.status === 'RESOLVED' ? '<span class="badge text-bg-success">Resolved</span>'
            : staff ? (q.awaitingStudent ? '<span class="badge text-bg-secondary">Waiting for student</span>' : '<span class="badge text-bg-warning">Needs reply</span>')
                : (q.awaitingStudent ? '<span class="badge text-bg-primary">New reply</span>' : '<span class="badge text-bg-warning">Pending</span>');

        const loadCounts = async () => {
            const s = await P.get('/api/notifications/queries/stats');
            $('#count-PENDING').textContent = s.pending;
            $('#count-RESOLVED').textContent = s.resolved;
            P.refreshQueryBadge();
        };

        const loadList = async () => {
            const page = await P.get('/api/notifications/queries' + P.qs({ status: state.status, category: $('#q-category').value,
                q: $('#q-search').value.trim(), page: state.page, size: 15 }));
            const list = P.content(page);
            $('#q-list').innerHTML = list.length ? list.map((q) => '<a class="list-group-item list-group-item-action' + (q.id === state.selected ? ' active-query' : '') + '" data-q="' + q.id + '">' +
                '<div class="d-flex justify-content-between gap-2"><span class="fw-semibold text-truncate">' + esc(q.subject) + '</span>' + statusBadge(q) + '</div>' +
                '<div class="small text-muted">' + esc(q.reference) + ' · ' + esc(q.categoryLabel) + (staff ? ' · ' + esc(q.studentName || '') + ' (' + esc(q.studentLogin || '') + ')' : '') + '</div>' +
                (q.lastMessage ? '<div class="small text-truncate">' + esc(q.lastMessage) + '</div>' : '') +
                '<div class="small text-muted">' + P.fromNow(q.updatedAt) + ' · ' + q.messageCount + ' message(s)</div></a>').join('')
                : '<div class="p-3">' + P.empty(state.status === 'PENDING' ? 'No pending queries' : 'No resolved queries yet', 'inbox') + '</div>';
            P.pager('#q-pager', page, (n) => { state.page = n; loadList(); });
        };

        const showThread = async (id) => {
            state.selected = id;
            $$('#q-list [data-q]').forEach((a) => a.classList.toggle('active-query', Number(a.dataset.q) === id));
            const d = await P.get('/api/notifications/queries/' + id);
            render(d);
        };

        const render = (d) => {
            const q = d.query;
            const resolved = q.status === 'RESOLVED';
            $('#q-thread').innerHTML = '<div class="d-flex justify-content-between align-items-start gap-2 mb-2"><div><h5 class="mb-0">' + esc(q.subject) + '</h5>' +
                '<div class="small text-muted">' + esc(q.reference) + ' · ' + esc(q.categoryLabel) + ' · raised ' + P.fmtDateTime(q.createdAt) +
                (staff ? ' by <strong>' + esc(q.studentName || '') + '</strong> (' + esc(q.studentLogin || '') + ')' : '') + '</div></div>' + statusBadge(q) + '</div>' +
                (resolved ? '<div class="alert alert-success py-2 small">Resolved ' + P.fmtDateTime(q.resolvedAt) + (q.resolvedByName ? ' by ' + esc(q.resolvedByName) : '') + '.' +
                    (staff ? '' : ' Not solved? Reply below and the query opens again.') + '</div>' : '') +
                '<div class="query-thread" id="q-msgs">' + d.messages.map((m) => '<div class="qmsg' + (m.mine ? ' mine' : '') + '"><div class="meta">' +
                    esc(m.authorName || '') + ' · ' + (m.authorRole === 'STUDENT' ? 'Student' : 'Placement office') + ' · ' + P.fmtDateTime(m.createdAt) + '</div>' +
                    '<div class="pre-wrap">' + esc(m.message) + '</div></div>').join('') + '</div>' +
                '<form id="q-reply" class="mt-2"><textarea class="form-control" name="message" rows="3" maxlength="4000" required placeholder="' +
                (staff ? 'Write your answer to the student...' : 'Add more details or reply...') + '"></textarea>' +
                '<div class="d-flex flex-wrap gap-2 mt-2"><button class="btn btn-primary" type="submit"><i class="bi bi-send me-1"></i>Send</button>' +
                (staff && !resolved ? '<button class="btn btn-success" type="button" id="q-reply-resolve"><i class="bi bi-check2-circle me-1"></i>Send and resolve</button>' : '') +
                (!resolved ? '<button class="btn btn-outline-success ms-auto" type="button" id="q-resolve">Mark resolved</button>'
                    : '<button class="btn btn-outline-secondary ms-auto" type="button" id="q-reopen">Re-open</button>') + '</div></form>';
            const box = $('#q-msgs');
            box.scrollTop = box.scrollHeight;
            const text = () => $('#q-reply [name=message]').value.trim();
            const after = async (fn, msg) => {
                try { render(await fn()); P.toast(msg); loadList(); loadCounts(); } catch (e) { P.fail(e); }
            };
            $('#q-reply').addEventListener('submit', (ev) => {
                ev.preventDefault();
                after(() => P.post('/api/notifications/queries/' + q.id + '/messages', { message: text() }), 'Message sent');
            });
            if ($('#q-reply-resolve')) $('#q-reply-resolve').addEventListener('click', () => {
                if (!text()) { P.toast('Write the answer first', 'warning'); return; }
                after(() => P.post('/api/notifications/queries/' + q.id + '/resolve', { message: text() }), 'Answered and resolved');
            });
            if ($('#q-resolve')) $('#q-resolve').addEventListener('click', () => after(() => P.post('/api/notifications/queries/' + q.id + '/resolve', {}), 'Marked as resolved'));
            if ($('#q-reopen')) $('#q-reopen').addEventListener('click', () => after(() => P.post('/api/notifications/queries/' + q.id + '/reopen'), 'Query re-opened'));
        };

        $$('[data-status]').forEach((b) => b.addEventListener('click', () => {
            state.status = b.dataset.status;
            state.page = 0;
            $$('[data-status]').forEach((x) => { x.classList.toggle('btn-dark', x === b); x.classList.toggle('btn-outline-dark', x !== b); });
            loadList();
        }));
        let timer;
        $('#q-search').addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => { state.page = 0; loadList(); }, 300); });
        $('#q-category').addEventListener('change', () => { state.page = 0; loadList(); });
        P.on('#q-list', '[data-q]', (a) => showThread(Number(a.dataset.q)));
        P.onQueryNotification = () => { loadList(); loadCounts(); if (state.selected) showThread(state.selected); };

        $('#new-query').addEventListener('click', () => {
            P.modal('Raise a query', '<form id="nq"><div class="mb-2"><label class="form-label small">Category</label><select class="form-select" name="category" required>' +
                categories.map((c) => '<option value="' + c.value + '">' + esc(c.label) + '</option>').join('') + '</select></div>' +
                '<div class="mb-2"><label class="form-label small">Subject</label><input class="form-control" name="subject" required maxlength="200" placeholder="e.g. Not eligible for the TechNova drive although my CGPA is 8.2"></div>' +
                '<div class="mb-2"><label class="form-label small">Describe your query or issue</label><textarea class="form-control" name="message" rows="5" required maxlength="4000" ' +
                'placeholder="Give the details the placement office needs: the drive or test, what happened, and what you expected."></textarea></div></form>',
            [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Submit query', onClick: async (el) => {
                const f = el.querySelector('#nq');
                if (!f.reportValidity()) return false;
                const d = await P.post('/api/notifications/queries', P.formData(f));
                P.toast('Query ' + d.query.reference + ' sent to the placement office');
                state.status = 'PENDING';
                $$('[data-status]')[0].click();
                loadCounts();
                render(d);
                state.selected = d.query.id;
            } }]);
        });

        await loadCounts();
        if (state.selected) {
            try {
                const d = await P.get('/api/notifications/queries/' + state.selected);
                if (d.query.status === 'RESOLVED') $$('[data-status]')[1].click(); else await loadList();
                render(d);
            } catch (e) { await loadList(); }
        } else {
            await loadList();
        }
    });

    // ================================================================= notifications / announcements / account

    P.page('notifications', async () => {
        let page = 0;
        const load = async () => {
            const res = await P.get('/api/notifications' + P.qs({ unreadOnly: $('#unread-only').checked, page }));
            const list = P.content(res);
            $('#list').innerHTML = list.length ? list.map((n) => '<div class="list-group-item d-flex gap-3 ' + (n.read ? '' : 'bg-light') + '"><i class="bi bi-' + (n.read ? 'bell' : 'bell-fill text-primary') + ' fs-5"></i>' +
                '<div class="flex-fill"><div class="fw-semibold">' + esc(n.title) + '</div><div class="small">' + esc(n.message) + '</div><div class="small text-muted">' + P.fromNow(n.createdAt) + '</div></div>' +
                '<div class="d-flex flex-column gap-1">' + (n.link && n.link.startsWith('/') ? '<a class="btn btn-sm btn-light" href="' + esc(n.link) + '" data-read="' + n.id + '">Open</a>' : '') +
                (n.read ? '' : '<button class="btn btn-sm btn-outline-secondary" data-read="' + n.id + '">Mark read</button>') + '</div></div>').join('') : P.empty("You're all caught up", 'bell');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        P.on('#list', '[data-read]', async (b, ev) => {
            const isLink = b.tagName === 'A';
            if (isLink) ev.preventDefault();
            await P.post('/api/notifications/' + b.dataset.read + '/read');
            P.refreshBadge();
            if (isLink) location.href = b.getAttribute('href'); else load();
        });
        $('#unread-only').addEventListener('change', () => { page = 0; load(); });
        $('#read-all').addEventListener('click', async () => { await P.post('/api/notifications/read-all'); P.refreshBadge(); load(); });
        load();
    });

    P.renderAnnouncements = function (el, pagerEl, onPage) {
        return async (page) => {
            const res = await P.get('/api/notifications/announcements?page=' + (page || 0));
            const list = P.content(res);
            $(el).innerHTML = list.length ? list.map((a) => '<div class="card mb-3"><div class="card-body"><div class="d-flex justify-content-between"><h5 class="mb-1">' + esc(a.title) + '</h5>' +
                '<span class="small text-muted">' + P.fmtDateTime(a.createdAt) + '</span></div><div class="small text-muted mb-2">' + esc(a.author || 'Placement office') + ' · to ' + P.title(a.audience) +
                (P.isStaff() ? ' · ' + a.recipients + ' recipients' : '') + '</div><div class="pre-wrap">' + esc(a.message) + '</div>' +
                (a.link && a.link.startsWith('/') ? '<a class="btn btn-sm btn-light mt-2" href="' + esc(a.link) + '">Open</a>' : '') + '</div></div>').join('') : P.empty('No announcements', 'megaphone');
            P.pager(pagerEl, res, onPage);
        };
    };

    P.page('announcements', async () => {
        const render = P.renderAnnouncements('#list', '#pager', (p) => render(p));
        render(0);
    });

    P.page('account', async () => {
        const me = await P.get('/api/auth/me');
        $('#account').innerHTML = [['Name', me.name], ['Login ID', me.loginId], ['E-mail', me.email], ['Role', P.title(me.role)], ['Status', me.status], ['Last login', P.fmtDateTime(me.lastLoginAt)], ['Member since', P.fmtDate(me.createdAt)]]
            .map(([k, v]) => '<div class="d-flex justify-content-between border-bottom py-1"><span class="text-muted">' + k + '</span><span>' + esc(v || '-') + '</span></div>').join('');
        $('#tfa').checked = !!me.twoFactorEnabled;
        $('#tfa').addEventListener('change', async (ev) => {
            try { await P.put('/api/auth/me/two-factor', { enabled: ev.target.checked }); P.toast('Two-step login ' + (ev.target.checked ? 'enabled' : 'disabled')); } catch (e) { ev.target.checked = !ev.target.checked; P.fail(e); }
        });
        const h = await P.get('/api/auth/me/login-history?size=20');
        P.table('#history', [
            { label: 'Time', render: (x) => P.fmtDateTime(x.time) },
            { label: 'Result', render: (x) => (x.success ? '<span class="badge text-bg-success">Success</span>' : '<span class="badge text-bg-danger">' + esc(P.title(x.reason)) + '</span>') },
            { label: 'IP', render: (x) => '<span class="mono small">' + esc(x.ip) + '</span>' },
            { label: 'Device', render: (x) => '<span class="small text-muted">' + esc((x.userAgent || '').substring(0, 60)) + '</span>' }
        ], P.content(h), 'No login history');
    });
})();
