/* Student portal pages (coding pages live in coding.js, the exam in exam.js). */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, $$, esc } = P;

    // ================================================================= dashboard

    P.page('student-dashboard', async () => {
        loadDashboard(false);
        $('#refresh-readiness').addEventListener('click', () => loadDashboard(true));

        P.get('/api/placement/events' + P.qs({ from: new Date().toISOString().substring(0, 19) })).then((events) => {
            const list = (events || []).slice(0, 5);
            $('#events').innerHTML = list.length ? '<ul class="list-group list-group-flush">' + list.map((e) =>
                '<li class="list-group-item d-flex justify-content-between align-items-center"><div><div class="fw-semibold">' + esc(e.title) +
                '</div><div class="small text-muted">' + P.title(e.type) + (e.companyName ? ' · ' + esc(e.companyName) : '') + (e.location ? ' · ' + esc(e.location) : '') +
                '</div></div><span class="small text-nowrap">' + P.fmtDateTime(e.startTime) + '</span></li>').join('') + '</ul>' : P.empty('No upcoming events', 'calendar');
        }).catch(() => { $('#events').innerHTML = P.empty('Could not load events'); });

        P.get('/api/placement/jobs?status=OPEN').then((jobs) => {
            const list = (jobs || []).filter((j) => j.eligible && !j.myApplicationStatus)
                .sort((a, b) => (b.matchPercent || 0) - (a.matchPercent || 0)).slice(0, 5);
            $('#jobs').innerHTML = list.length ? '<ul class="list-group list-group-flush">' + list.map((j) =>
                '<a class="list-group-item list-group-item-action d-flex justify-content-between align-items-center" href="/student/jobs/' + j.id + '"><div><div class="fw-semibold">' +
                esc(j.title) + '</div><div class="small text-muted">' + esc(j.companyName) + ' · ' + P.pay(j) + '</div></div>' +
                (j.matchPercent != null ? '<span class="badge text-bg-primary">' + j.matchPercent + '% match</span>' : '') + '</a>').join('') + '</ul>'
                : P.empty('No new eligible openings right now', 'briefcase');
        }).catch(() => { $('#jobs').innerHTML = P.empty('Could not load jobs'); });

        P.get('/api/coding/practice/next').then((n) => {
            if (!n || !n.question) {
                $('#next-problem').innerHTML = P.empty('You have solved everything - amazing!', 'trophy');
                return;
            }
            $('#next-problem').innerHTML = '<h5 class="mb-1">' + esc(n.question.title) + ' ' + P.badge(n.question.difficulty) + '</h5>' +
                '<div class="mb-2">' + P.chips(n.question.tags) + '</div><p class="text-muted small mb-3">' + esc(n.reason) +
                ' (your rating: ' + n.yourRating + ')</p><a class="btn btn-primary" href="/student/practice/' + encodeURIComponent(n.question.id) + '">Solve now</a>';
        }).catch(() => { $('#next-problem').innerHTML = P.empty('Practice is unavailable right now'); });
    });

    async function loadDashboard(refresh) {
        $('#readiness').innerHTML = P.spinner();
        let d;
        try {
            if (refresh) await P.get('/api/analytics/student/me/readiness?refresh=true');
            d = await P.get('/api/analytics/student/me');
        } catch (e) {
            $('#readiness').innerHTML = P.empty('Analytics are unavailable right now');
            return;
        }
        const r = d.readiness;
        const c = r.components;
        $('#readiness').innerHTML = P.ring(r.score, r.level) +
            (d.batchRank ? '<div class="text-center small text-muted mt-2">Rank ' + d.batchRank + ' of ' + d.batchSize + ' in your batch</div>' : '') +
            '<div class="mt-3 small">' + [['Coding', c.coding, 30], ['Aptitude', c.aptitude, 20], ['Resume (ATS)', c.ats, 15], ['Profile', c.profile, 10],
                ['Mock interviews', c.mock, 15], ['Contests', c.contests, 10]].map(([n, v, m]) =>
                '<div class="d-flex justify-content-between"><span>' + n + '</span><span>' + v + ' / ' + m + '</span></div>' + P.progress(v, m) + '<div class="mb-2"></div>').join('') + '</div>';

        const p = d.prediction;
        $('#prediction').innerHTML = P.ring(p.percent, p.band + ' chance', P.ringColor(p.percent)) +
            '<ul class="list-unstyled small mt-3 mb-2">' + (p.factors || []).map((f) => '<li class="mb-1"><i class="bi ' +
                (f.impact === 'POSITIVE' ? 'bi-arrow-up-circle text-success' : 'bi-arrow-down-circle text-danger') + ' me-1"></i><strong>' +
                esc(f.name) + ':</strong> ' + esc(f.detail) + '</li>').join('') + '</ul><div class="small text-muted">' + esc(p.note) + '</div>';

        $('#suggestions').innerHTML = (r.suggestions || []).length ? r.suggestions.map((s) =>
            '<div class="d-flex gap-2 mb-3"><span class="badge text-bg-warning align-self-start">+' + s.potentialGain + '</span><div><div class="fw-semibold">' +
            esc(s.area) + '</div><div class="small text-muted">' + esc(s.text) + '</div></div></div>').join('')
            : '<div class="text-success"><i class="bi bi-check-circle me-1"></i>You are well prepared in every area. Keep it up!</div>';

        const pl = d.placement || {};
        const cd = d.coding || {};
        const stats = [
            ['Applications', pl.applications || 0, (pl.active || 0) + ' active', 'briefcase', 'bg-soft-blue'],
            ['Offers', pl.offers || 0, pl.placed ? 'Placed (' + P.title(pl.placedTier) + ')' : 'Not placed yet', 'award', 'bg-soft-green'],
            ['Coding rating', cd.rating || '-', (cd.solved || 0) + ' problems solved', 'code-slash', 'bg-soft-purple'],
            ['Best ATS score', d.bestAtsScore != null ? d.bestAtsScore : '-', 'Profile ' + d.profileCompleteness + '% complete', 'file-earmark-check', 'bg-soft-amber']
        ];
        $('#stats').innerHTML = stats.map(([l, v, h, i, cls]) => '<div class="col-6 col-xl-3"><div class="stat d-flex justify-content-between"><div><div class="label">' + l +
            '</div><div class="value">' + esc(v) + '</div><div class="hint">' + esc(h) + '</div></div><span class="icon ' + cls + '"><i class="bi bi-' + i + '"></i></span></div></div>').join('');

        P.chart('radar', {
            type: 'radar',
            data: {
                labels: ['Coding', 'Aptitude', 'Resume', 'Profile', 'Mock', 'Contests'],
                datasets: [{ label: 'You (% of max)', data: [c.coding / 30, c.aptitude / 20, c.ats / 15, c.profile / 10, c.mock / 15, c.contests / 10].map((x) => Math.round(x * 100)),
                    backgroundColor: 'rgba(42,157,143,.2)', borderColor: '#2a9d8f' }]
            },
            options: { scales: { r: { min: 0, max: 100, ticks: { stepSize: 25 } } } }
        });
    }

    // ================================================================= profile

    P.page('student-profile', async () => {
        let profile = await P.get('/api/profile/students/me');
        render(profile);
        // initials until a photo is uploaded (an <img> without a source would show a broken image)
        const initials = (profile.name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
        $('#photo').src = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="110" height="110"><rect width="110" height="110" fill="#e7f4f2"/>' +
            '<text x="55" y="68" font-family="Arial" font-size="40" font-weight="bold" fill="#21867a" text-anchor="middle">' + initials + '</text></svg>');
        if (profile.hasPhoto) P.imageUrl('/api/profile/students/' + profile.userId + '/photo').then((u) => { if (u) $('#photo').src = u; });

        function render(p) {
            profile = p;
            $('#p-name').textContent = p.name;
            $('#p-reg').textContent = p.regNo + ' · ' + (p.email || '');
            $('#academic').innerHTML = [['Branch', p.branch], ['Batch', p.batch], ['CGPA', p.cgpa], ['Active backlogs', p.backlogs]]
                .map(([k, v]) => '<div class="d-flex justify-content-between border-bottom py-1"><span class="text-muted">' + k + '</span><span>' + esc(v == null ? '-' : v) + '</span></div>').join('');
            const c = p.completeness || { percent: 0, missing: [] };
            $('#completeness').innerHTML = '<div class="d-flex justify-content-between mb-1"><span>' + c.percent + '%</span></div>' + P.progress(c.percent, 100, c.percent >= 80 ? 'bg-success' : '') +
                (c.missing.length ? '<div class="small text-muted mt-2">Missing: ' + c.missing.map(esc).join(', ') + '</div>' : '');
            $('#resume-info').innerHTML = p.hasResume ? '<i class="bi bi-file-earmark-pdf text-danger"></i> ' + esc(p.resumeOriginalName || 'resume') +
                '<div class="text-muted">Uploaded ' + P.fmtDateTime(p.resumeUploadedAt) + '</div>' : '<span class="text-muted">No resume uploaded yet.</span>';
            ['resume-view', 'resume-parse', 'resume-delete'].forEach((id) => { $('#' + id).disabled = !p.hasResume; });
            P.fillForm($('#profile-form'), p);
            $('#projects').innerHTML = '';
            (p.projects || []).forEach(addProject);
        }

        function addProject(pr) {
            pr = pr || {};
            const div = document.createElement('div');
            div.className = 'border rounded p-2 mb-2 project-row';
            div.innerHTML = '<div class="row g-2"><div class="col-md-5"><input class="form-control form-control-sm" data-f="title" placeholder="Title" maxlength="255"></div>' +
                '<div class="col-md-4"><input class="form-control form-control-sm" data-f="techStack" placeholder="Tech stack" maxlength="255"></div>' +
                '<div class="col-md-3 d-flex gap-1"><input class="form-control form-control-sm" data-f="link" placeholder="Link"><button type="button" class="btn btn-sm btn-outline-danger" data-remove><i class="bi bi-x"></i></button></div>' +
                '<div class="col-12"><textarea class="form-control form-control-sm" data-f="description" rows="2" placeholder="What you built and the impact" maxlength="2000"></textarea></div></div>';
            ['title', 'techStack', 'link', 'description'].forEach((f) => { div.querySelector('[data-f="' + f + '"]').value = pr[f] || ''; });
            div.querySelector('[data-remove]').addEventListener('click', () => div.remove());
            $('#projects').appendChild(div);
        }
        $('#add-project').addEventListener('click', () => addProject());

        $('#save-profile').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            const body = P.formData($('#profile-form'));
            body.backlogs = profile.backlogs;
            body.projects = $$('.project-row').map((row) => {
                const o = {};
                ['title', 'techStack', 'link', 'description'].forEach((f) => { o[f] = row.querySelector('[data-f="' + f + '"]').value.trim() || null; });
                return o;
            }).filter((o) => o.title);
            try {
                render(await P.put('/api/profile/students/me', body));
                P.toast('Profile saved');
            } catch (e) { P.fail(e); }
        }));

        $('#photo-file').addEventListener('change', async (ev) => {
            const f = ev.target.files[0];
            if (!f) return;
            const fd = new FormData();
            fd.append('file', f);
            try {
                await P.post('/api/profile/students/me/photo', fd);
                $('#photo').src = URL.createObjectURL(f);
                P.toast('Photo updated');
            } catch (e) { P.fail(e); }
        });

        $('#resume-file').addEventListener('change', async (ev) => {
            const f = ev.target.files[0];
            if (!f) return;
            const fd = new FormData();
            fd.append('file', f);
            try {
                render(await P.post('/api/profile/students/me/resume', fd));
                P.toast('Resume uploaded');
            } catch (e) { P.fail(e); }
            ev.target.value = '';
        });
        $('#resume-view').addEventListener('click', () => P.openFile('/api/profile/students/' + profile.userId + '/resume'));
        $('#resume-delete').addEventListener('click', async () => {
            if (!(await P.confirm('Delete resume', 'Remove your resume from your profile?', 'Delete', 'btn-danger'))) return;
            try {
                await P.del('/api/profile/students/me/resume');
                render(await P.get('/api/profile/students/me'));
            } catch (e) { P.fail(e); }
        });
        $('#resume-parse').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try {
                const parsed = await P.post('/api/profile/students/me/resume/parse?apply=false');
                const html = '<p class="small text-muted">The AI read your resume and found the following. Applying merges it into your profile (nothing is deleted).</p>' +
                    '<div class="mb-2"><strong>Skills</strong><div>' + P.chips(parsed.skills) + '</div></div>' +
                    '<div class="mb-2"><strong>Certifications</strong><div>' + P.chips(parsed.certifications) + '</div></div>' +
                    '<div class="mb-2"><strong>Projects</strong><ul class="small">' + (parsed.projects || []).map((p) => '<li><strong>' + esc(p.title) + '</strong> - ' + esc(p.description) + '</li>').join('') + '</ul></div>' +
                    '<div class="mb-2"><strong>Summary</strong><p class="small">' + esc(parsed.summary) + '</p></div>';
                P.modal('Resume auto-fill', html, [{ label: 'Cancel', cls: 'btn-light' }, {
                    label: 'Apply to my profile', onClick: async () => {
                        await P.post('/api/profile/students/me/resume/parse?apply=true');
                        render(await P.get('/api/profile/students/me'));
                        P.toast('Profile updated from your resume');
                    }
                }], { size: 'lg' });
            } catch (e) { P.fail(e); }
        }));
    });

    // ================================================================= jobs

    function jobCard(j) {
        const status = j.myApplicationStatus ? P.badge(j.myApplicationStatus) : j.eligible ? '<span class="badge text-bg-success">Eligible</span>' : '<span class="badge text-bg-secondary">Not eligible</span>';
        return '<div class="col-md-6 col-xl-4"><a class="card h-100 text-decoration-none text-reset" href="/student/jobs/' + j.id + '"><div class="card-body">' +
            '<div class="d-flex justify-content-between mb-2"><div class="small text-muted">' + esc(j.companyName) + '</div>' + P.badge(j.tier) + '</div>' +
            '<h5 class="mb-1">' + esc(j.title) + '</h5><div class="mb-2 text-muted small">' + P.jobType(j.jobType) + (j.location ? ' · ' + esc(j.location) : '') + '</div>' +
            '<div class="fw-semibold mb-2">' + [j.ctcLpa ? P.money(j.ctcLpa) + (j.jobType === 'INTERNSHIP_WITH_PPO' ? ' PPO' : '') : null,
                j.stipend ? '₹' + Number(j.stipend).toLocaleString('en-IN') + '/month' : null].filter(Boolean).join(' · ') + '</div>' +
            '<div class="mb-2">' + P.chips((j.requiredSkills || []).slice(0, 6)) + '</div>' +
            '<div class="d-flex justify-content-between align-items-center small"><span>' + status + (j.matchPercent != null ? ' <span class="badge text-bg-light">' + j.matchPercent + '% match</span>' : '') +
            '</span><span class="text-muted">Apply by ' + P.fmtDate(j.applicationDeadline) + '</span></div></div></a></div>';
    }

    P.page('student-jobs', async () => {
        const all = await P.get('/api/placement/jobs?status=OPEN');
        const render = () => {
            const q = $('#job-search').value.trim().toLowerCase();
            const f = $('#job-filter').value;
            const type = $('#job-type').value;
            const list = all.filter((j) => (!q || (j.title + ' ' + j.companyName + ' ' + (j.requiredSkills || []).join(' ')).toLowerCase().includes(q)) &&
                (f !== 'eligible' || j.eligible) && (f !== 'applied' || j.myApplicationStatus) &&
                (!type || (type === 'INTERNSHIP' ? j.jobType !== 'FULL_TIME' : type === 'FULL_TIME' ? j.jobType !== 'INTERNSHIP' : j.jobType === type)));
            $('#job-count').textContent = list.length + ' of ' + all.length + ' openings';
            $('#job-list').innerHTML = list.length ? list.map(jobCard).join('') : '<div class="col-12">' + P.empty('No openings match your filters', 'briefcase') + '</div>';
        };
        $('#job-search').addEventListener('input', render);
        $('#job-filter').addEventListener('change', render);
        $('#job-type').addEventListener('change', render);
        render();
    });

    P.page('student-job', async () => {
        const id = $('#job-id').value;
        const load = async () => {
            const j = await P.get('/api/placement/jobs/' + id);
            const canApply = j.status === 'OPEN' && !j.myApplicationStatus && j.eligible;
            const canWithdraw = j.myApplicationStatus && ['APPLIED', 'SHORTLISTED'].includes(j.myApplicationStatus);
            $('#job-head').innerHTML = '<div><div class="text-muted">' + esc(j.companyName) + '</div><h1>' + esc(j.title) + ' ' + P.badge(j.tier) + '</h1><div class="sub">' +
                (j.ctcLpa ? P.money(j.ctcLpa) : '') + (j.stipend ? (j.ctcLpa ? ' · ' : '') + '₹' + Number(j.stipend).toLocaleString('en-IN') + '/month stipend' : '') + ' · ' + P.jobType(j.jobType) + (j.location ? ' · ' + esc(j.location) : '') +
                ' · Apply by ' + P.fmtDateTime(j.applicationDeadline) + '</div></div>' +
                '<div class="d-flex gap-2">' + (j.myApplicationStatus ? P.badge(j.myApplicationStatus) : '') +
                (canApply ? '<button class="btn btn-primary" id="apply"><i class="bi bi-send me-1"></i>Apply now</button>' : '') +
                (canWithdraw ? '<button class="btn btn-outline-danger" id="withdraw">Withdraw</button>' : '') + '</div>';
            const pay = j.ctcBreakdown || [];
            $('#job-pay').innerHTML = '<div class="d-flex flex-wrap gap-4 mb-2">' +
                (j.ctcLpa ? '<div><div class="small text-muted">' + (j.jobType === 'INTERNSHIP' ? 'Stipend (annualised)' : 'Total CTC') + '</div><div class="fs-4 fw-bold">' + P.money(j.ctcLpa) + '</div></div>' : '') +
                (j.stipend ? '<div><div class="small text-muted">Internship stipend</div><div class="fs-4 fw-bold">₹' + Number(j.stipend).toLocaleString('en-IN') + '<span class="fs-6 fw-normal">/month</span></div></div>' : '') +
                '<div><div class="small text-muted">Offer type</div><div class="fs-6 fw-semibold mt-1">' + P.jobType(j.jobType) + ' ' + P.badge(j.tier) + '</div></div></div>' +
                (pay.length ? '<table class="table table-sm mb-0"><thead><tr><th>Component</th><th class="text-end">Amount</th><th>Details</th></tr></thead><tbody>' +
                    pay.map((c) => '<tr><td>' + esc(c.component) + '</td><td class="text-end fw-semibold text-nowrap">' + esc(c.amount || '-') + '</td><td class="small text-muted">' + esc(c.note || '') + '</td></tr>').join('') +
                    '</tbody></table>' : '<div class="small text-muted">The company has not shared a detailed breakdown.</div>');
            $('#job-body').innerHTML = '<div class="pre-wrap mb-3">' + esc(j.description) + '</div><h6>Required skills</h6><div class="mb-3">' + P.chips(j.requiredSkills) + '</div>' +
                '<div class="row small text-muted">' + [['Min CGPA', j.minCgpa], ['Max backlogs', j.maxBacklogs], ['Batch', j.eligibleBatch], ['Branches', (j.allowedBranches || []).join(', ') || 'All'],
                    ['Min 10th %', j.minTenth], ['Min 12th %', j.minTwelfth]].map(([k, v]) => '<div class="col-6 col-md-4 mb-1">' + k + ': <span class="text-body">' + esc(v == null ? '-' : v) + '</span></div>').join('') + '</div>';
            $('#job-rounds').innerHTML = (j.rounds || []).length ? '<ol class="mb-0">' + j.rounds.map((r) => '<li class="mb-1"><strong>' + esc(r.name) + '</strong> <span class="text-muted small">(' +
                P.title(r.type) + (r.scheduledAt ? ', ' + P.fmtDateTime(r.scheduledAt) : '') + ')</span>' + (r.description ? '<div class="small text-muted">' + esc(r.description) + '</div>' : '') + '</li>').join('') + '</ol>'
                : '<span class="text-muted">The company has not published the rounds yet.</span>';
            const apply = $('#apply');
            if (apply) apply.addEventListener('click', () => P.busy(apply, async () => {
                try { await P.post('/api/placement/jobs/' + id + '/apply'); P.toast('Application submitted'); load(); } catch (e) { P.fail(e); }
            }));
            const wd = $('#withdraw');
            if (wd) wd.addEventListener('click', async () => {
                if (!(await P.confirm('Withdraw application', 'You may not be able to apply again to this drive.', 'Withdraw', 'btn-danger'))) return;
                const apps = await P.get('/api/placement/applications/me');
                const a = apps.find((x) => String(x.jobId) === String(id));
                try { await P.post('/api/placement/applications/' + a.id + '/withdraw'); P.toast('Application withdrawn'); load(); } catch (e) { P.fail(e); }
            });
        };
        await load();
        P.get('/api/placement/jobs/' + id + '/eligibility').then((e) => {
            $('#eligibility').innerHTML = '<div class="mb-2">' + (e.eligible ? '<span class="badge text-bg-success">You are eligible</span>' : '<span class="badge text-bg-danger">Not eligible</span>') + '</div>' +
                '<ul class="list-unstyled small mb-0">' + e.checks.map((c) => '<li class="mb-1"><i class="bi ' + (c.passed ? 'bi-check-circle-fill text-success' : (c.blocking ? 'bi-x-circle-fill text-danger' : 'bi-exclamation-circle-fill text-warning')) +
                    ' me-1"></i><strong>' + esc(c.label) + '</strong> - ' + esc(c.message) + '</li>').join('') + '</ul>';
        }).catch(P.fail);
        $('#load-match').addEventListener('click', (ev) => P.busy(ev.currentTarget, async () => {
            try {
                const m = await P.get('/api/placement/jobs/' + id + '/match');
                $('#match').innerHTML = P.ring(m.matchPercent, 'overall match') +
                    '<div class="small mt-3">Skills ' + m.skillPercent + '%' + (m.semanticPercent != null ? ' · Resume relevance ' + m.semanticPercent + '%' : '') + ' · CGPA fit ' + m.cgpaFitPercent + '%</div>' +
                    '<div class="mt-2 small"><strong>You have</strong><div>' + P.chips(m.matchedSkills, 'ok') + '</div></div>' +
                    '<div class="mt-2 small"><strong>Missing</strong><div>' + P.chips(m.missingSkills, 'bad') + '</div></div>' +
                    (m.advice ? '<p class="small mt-2 mb-2">' + esc(m.advice) + '</p>' : '') +
                    ((m.practiceProblems || []).length ? '<div class="small"><strong>Practise these</strong><ul class="mb-0">' + m.practiceProblems.map((q) =>
                        '<li><a href="/student/practice/' + encodeURIComponent(q.id) + '">' + esc(q.title) + '</a> ' + P.badge(q.difficulty) + '</li>').join('') + '</ul></div>' : '');
            } catch (e) { P.fail(e); }
        }));
    });

    // ================================================================= applications

    P.page('student-applications', async () => {
        const apps = await P.get('/api/placement/applications/me');
        P.table('#app-table', [
            { label: 'Role', render: (a) => '<a href="/student/applications/' + a.id + '" class="fw-semibold">' + esc(a.jobTitle) + '</a><div class="small text-muted">' + esc(a.companyName) + '</div>' },
            { label: 'Status', render: (a) => P.badge(a.status) + (a.statusNote ? '<div class="small text-muted">' + esc(a.statusNote) + '</div>' : '') },
            { label: 'Current round', render: (a) => esc(a.currentRoundName || '-') },
            { label: 'Match', render: (a) => (a.matchScore != null ? a.matchScore + '%' : '-') },
            { label: 'Applied', render: (a) => P.fmtDate(a.appliedAt) },
            { label: '', cls: 'text-end', render: (a) => '<a class="btn btn-sm btn-outline-primary" href="/student/applications/' + a.id + '">Track</a>' }
        ], apps, 'You have not applied to any drive yet');
        const slots = await P.get('/api/placement/slots/me');
        P.table('#slot-table', [
            { label: 'Company', render: (s) => esc(s.companyName) + '<div class="small text-muted">' + esc(s.jobTitle) + ' · ' + esc(s.roundName) + '</div>' },
            { label: 'When', render: (s) => P.fmtDateTime(s.startTime) },
            { label: 'Where', render: (s) => esc(s.location || '-') },
            { label: '', cls: 'text-end', render: (s) => '<button class="btn btn-sm btn-outline-danger" data-cancel="' + s.id + '">Cancel</button>' }
        ], slots, 'No interview slots booked');
        P.on('#slot-table', '[data-cancel]', async (b) => {
            if (!(await P.confirm('Cancel slot', 'Release this interview slot?', 'Cancel slot', 'btn-danger'))) return;
            try { await P.post('/api/placement/slots/' + b.dataset.cancel + '/cancel'); P.toast('Slot released'); location.reload(); } catch (e) { P.fail(e); }
        });
    });

    P.page('student-application', async () => {
        const id = $('#app-id').value;
        const load = async () => {
            const t = await P.get('/api/placement/applications/' + id + '/timeline');
            const a = t.application;
            $('#app-head').innerHTML = '<div><div class="text-muted">' + esc(a.companyName) + '</div><h1>' + esc(a.jobTitle) + '</h1><div class="sub">Applied ' + P.fmtDateTime(a.appliedAt) +
                '</div></div><div>' + P.badge(a.status) + '</div>';
            $('#timeline').innerHTML = '<li><span class="dot CLEARED"></span><div class="fw-semibold">Applied</div><div class="small text-muted">' + P.fmtDateTime(a.appliedAt) + '</div></li>' +
                t.steps.map((s) => {
                    const current = s.roundId === a.currentRoundId;
                    let action = '';
                    if (current && s.status === 'PENDING' && s.assessmentTestId) {
                        action = '<a class="btn btn-sm btn-primary mt-2" href="/student/tests/' + s.assessmentTestId + '/take"><i class="bi bi-pencil-square me-1"></i>Take the test</a>';
                    }
                    if (s.bookedSlot) {
                        action += '<div class="small mt-1"><i class="bi bi-clock me-1"></i>Your slot: ' + P.fmtDateTime(s.bookedSlot.startTime) + (s.bookedSlot.location ? ' · ' + esc(s.bookedSlot.location) : '') + '</div>';
                    }
                    return '<li><span class="dot ' + (current && s.status === 'PENDING' ? 'CURRENT' : s.status) + '"></span><div class="d-flex justify-content-between"><div class="fw-semibold">' +
                        esc(s.name) + ' <span class="small text-muted">(' + P.title(s.type) + ')</span></div><div>' + (s.status === 'NOT_REACHED' ? '<span class="badge text-bg-light">Not reached</span>' : P.badge(s.status)) +
                        '</div></div><div class="small text-muted">' + (s.scheduledAt ? 'Scheduled ' + P.fmtDateTime(s.scheduledAt) : '') + (s.score != null ? ' · Score ' + s.score : '') + '</div>' +
                        (s.remarks ? '<div class="small">' + esc(s.remarks) + '</div>' : '') + action + '</li>';
                }).join('') +
                (a.status === 'SELECTED' ? '<li><span class="dot SELECTED"></span><div class="fw-semibold text-success">Selected</div><a href="/student/offers" class="small">See your offer</a></li>' : '');

            const step = t.steps.find((s) => s.roundId === a.currentRoundId);
            const interview = step && ['TECHNICAL_INTERVIEW', 'HR_INTERVIEW', 'GROUP_DISCUSSION'].includes(step.type) && step.status === 'PENDING';
            if (!interview) {
                $('#slots').innerHTML = '<span class="text-muted small">Slot booking opens when you reach an interview round.</span>';
                return;
            }
            const slots = (await P.get('/api/placement/jobs/' + a.jobId + '/slots')).filter((s) => s.roundId === step.roundId);
            const mine = slots.find((s) => s.bookedByMe);
            $('#slots').innerHTML = (mine ? '<div class="alert alert-success small">Booked: ' + P.fmtDateTime(mine.startTime) + '</div>' : '') +
                (slots.length ? '<div class="list-group">' + slots.map((s) => '<div class="list-group-item d-flex justify-content-between align-items-center"><div>' + P.fmtDateTime(s.startTime) +
                    '<div class="small text-muted">' + esc(s.location || '') + '</div></div>' + (s.bookedByMe ? '<span class="badge text-bg-success">Yours</span>'
                        : s.booked ? '<span class="badge text-bg-secondary">Taken</span>' : (mine ? '' : '<button class="btn btn-sm btn-primary" data-book="' + s.id + '">Book</button>')) + '</div>').join('') + '</div>'
                    : '<span class="text-muted small">The company has not published slots yet.</span>');
        };
        await load();
        P.on('#slots', '[data-book]', async (b) => {
            try { await P.post('/api/placement/slots/' + b.dataset.book + '/book'); P.toast('Slot booked'); load(); } catch (e) { P.fail(e); load(); }
        });
    });

    // ================================================================= offers

    P.page('student-offers', async () => {
        const load = async () => {
            const offers = await P.get('/api/placement/offers');
            P.table('#offer-table', [
                { label: 'Company', render: (o) => '<span class="fw-semibold">' + esc(o.companyName) + '</span><div class="small text-muted">' + esc(o.role || '') + (o.offCampus ? ' · off-campus' : '') + '</div>' },
                { label: 'CTC', render: (o) => P.money(o.ctcLpa) + ' ' + P.badge(o.tier) },
                { label: 'Status', render: (o) => P.badge(o.status) + (o.remarks ? '<div class="small text-muted">' + esc(o.remarks) + '</div>' : '') },
                { label: 'Respond by', render: (o) => (o.status === 'ISSUED' ? P.fmtDateTime(o.responseDeadline) : '-') },
                { label: '', cls: 'text-end text-nowrap', render: (o) => (o.hasLetter ? '<button class="btn btn-sm btn-light" data-letter="' + o.id + '"><i class="bi bi-file-earmark-pdf"></i> Letter</button> ' : '') +
                    (o.status === 'ISSUED' ? '<button class="btn btn-sm btn-success" data-accept="' + o.id + '">Accept</button> <button class="btn btn-sm btn-outline-danger" data-decline="' + o.id + '">Decline</button>' : '') }
            ], offers, 'No offers yet - keep going!');
            const certs = await P.get('/api/placement/certificates/me');
            P.table('#cert-table', [
                { label: 'Company', render: (c) => esc(c.companyName) + '<div class="small text-muted">' + esc(c.role) + '</div>' },
                { label: 'Issued', render: (c) => P.fmtDate(c.issuedAt) },
                { label: 'Code', render: (c) => '<span class="mono">' + esc(c.verificationCode) + '</span>' + (c.revoked ? ' <span class="badge text-bg-danger">Revoked</span>' : '') },
                { label: '', cls: 'text-end', render: (c) => '<button class="btn btn-sm btn-primary" data-cert="' + c.id + '"><i class="bi bi-download"></i> PDF</button> <a class="btn btn-sm btn-light" target="_blank" href="/verify/' + encodeURIComponent(c.verificationCode) + '">Verify</a>' }
            ], certs, 'Your certificate appears here once the placement office verifies an offer');
        };
        await load();
        P.on('#offer-table', '[data-letter]', (b) => P.openFile('/api/placement/offers/' + b.dataset.letter + '/letter'));
        P.on('#offer-table', '[data-accept]', async (b) => {
            if (!(await P.confirm('Accept offer', 'Accepting may make you ineligible for further drives under the placement policy. Continue?', 'Accept', 'btn-success'))) return;
            try { await P.post('/api/placement/offers/' + b.dataset.accept + '/accept'); P.toast('Offer accepted - congratulations!'); load(); } catch (e) { P.fail(e); }
        });
        P.on('#offer-table', '[data-decline]', async (b) => {
            if (!(await P.confirm('Decline offer', 'This cannot be undone.', 'Decline', 'btn-danger'))) return;
            try { await P.post('/api/placement/offers/' + b.dataset.decline + '/decline'); load(); } catch (e) { P.fail(e); }
        });
        P.on('#cert-table', '[data-cert]', (b) => P.download('/api/placement/certificates/' + b.dataset.cert + '/pdf', 'placement-certificate.pdf'));
        $('#off-campus').addEventListener('click', () => {
            P.modal('Report an off-campus offer', '<form id="oc-form"><div class="mb-2"><label class="form-label">Company</label><input class="form-control" name="companyName" required></div>' +
                '<div class="mb-2"><label class="form-label">Role</label><input class="form-control" name="role" required></div>' +
                '<div class="mb-2"><label class="form-label">CTC (LPA)</label><input class="form-control" type="number" step="0.01" name="ctcLpa"></div>' +
                '<div class="mb-2"><label class="form-label">Offer letter (PDF)</label><input class="form-control" type="file" name="file" accept=".pdf" required></div>' +
                '<div class="form-text">The placement office verifies the letter before it counts.</div></form>', [
                { label: 'Cancel', cls: 'btn-light' },
                { label: 'Submit', onClick: async (el) => {
                    const f = el.querySelector('#oc-form');
                    if (!f.reportValidity()) return false;
                    const fd = new FormData(f);
                    if (!fd.get('ctcLpa')) fd.delete('ctcLpa');
                    await P.post('/api/placement/offers/off-campus', fd);
                    P.toast('Submitted for verification');
                    load();
                } }]);
        });
    });

    // ================================================================= aptitude

    P.page('student-aptitude', async () => {
        const topics = await P.get('/api/assessment/practice/topics');
        $('#topic').innerHTML = topics.map((t) => '<option value="' + t + '">' + P.title(t) + '</option>').join('');
        const stats = async () => {
            const s = await P.get('/api/assessment/practice/stats');
            P.chart('topic-chart', {
                type: 'bar',
                data: { labels: s.map((x) => P.title(x.topic)), datasets: [{ data: s.map((x) => Math.round(x.accuracy)), backgroundColor: P.colors }] },
                options: { scales: { y: { min: 0, max: 100 } } }
            });
            $('#topic-stats').innerHTML = s.map((x) => '<div class="d-flex justify-content-between small"><span>' + P.title(x.topic) + '</span><span>' + x.correct + '/' + x.attempted + '</span></div>').join('');
        };
        stats();
        $('#start').addEventListener('click', async () => {
            const requested = Math.max(1, Math.min(30, Number($('#count').value) || 10));
            const topic = $('#topic').value;
            const difficulty = $('#difficulty').value;
            let qs;
            try {
                qs = await P.get('/api/assessment/practice/questions' + P.qs({ topic, difficulty, count: requested }));
            } catch (e) { P.fail(e); return; }
            // P.on adds a listener each time; reset the container to drop old listeners
            const fresh = $('#quiz').cloneNode(false);
            $('#quiz').replaceWith(fresh);
            if (!qs.length) { $('#quiz').innerHTML = P.empty('No questions available for this topic yet', 'inbox'); return; }

            const answers = [];
            const startedAt = Date.now();
            let i = 0;
            let shownAt = Date.now();
            let answered = false;

            const show = () => {
                if (i >= qs.length) { scorecard(); return; }
                const q = qs[i];
                answered = false;
                shownAt = Date.now();
                const done = answers.filter((a) => a.correct).length;
                $('#quiz').innerHTML = '<div class="d-flex justify-content-between align-items-center small text-muted mb-1"><span>Question ' + (i + 1) + ' of ' + qs.length +
                    ' · ' + done + ' correct so far</span><span>' + P.badge(q.difficulty) + ' ' + esc(q.subtopic || '') + '</span></div>' +
                    P.progress(i, qs.length) + '<div class="fs-6 my-3 pre-wrap">' + esc(q.text) + '</div>' +
                    q.options.map((o, k) => '<div class="option-card" data-k="' + k + '"><strong class="me-2">' + String.fromCharCode(65 + k) + '.</strong>' + esc(o) + '</div>').join('') +
                    '<div id="feedback" class="mt-3"></div>';
            };

            P.on('#quiz', '.option-card', async (el) => {
                if (answered || i >= qs.length) return;
                answered = true;
                const q = qs[i];
                const k = Number(el.dataset.k);
                try {
                    const fb = await P.post('/api/assessment/practice/answer', { questionId: q.id, selectedIndex: k });
                    answers.push({ q, selected: k, correct: fb.correct, correctIndex: fb.correctIndex, explanation: fb.explanation, secs: (Date.now() - shownAt) / 1000 });
                    $$('.option-card').forEach((o) => {
                        const n = Number(o.dataset.k);
                        if (n === fb.correctIndex) o.classList.add('correct');
                        else if (n === k) o.classList.add('wrong');
                    });
                    const last = i === qs.length - 1;
                    $('#feedback').innerHTML = '<div class="alert ' + (fb.correct ? 'alert-success' : 'alert-danger') + '"><strong>' + (fb.correct ? 'Correct!' : 'Not quite.') + '</strong>' +
                        (fb.correct ? '' : ' The answer is <strong>' + String.fromCharCode(65 + fb.correctIndex) + '</strong>.') +
                        (fb.explanation ? '<div class="small mt-1 pre-wrap">' + esc(fb.explanation) + '</div>' : '') + '</div>' +
                        '<button class="btn btn-primary" id="next">' + (last ? '<i class="bi bi-clipboard-data me-1"></i>See my scorecard' : 'Next question <i class="bi bi-arrow-right"></i>') + '</button>';
                    $('#next').addEventListener('click', () => { i++; show(); });
                } catch (e) { P.fail(e); answered = false; }
            });

            const scorecard = () => {
                const total = answers.length;
                const correct = answers.filter((a) => a.correct).length;
                const pct = Math.round(correct * 100 / total);
                const secs = Math.round((Date.now() - startedAt) / 1000);
                const avg = answers.reduce((s, a) => s + a.secs, 0) / total;
                const bySub = {};
                answers.forEach((a) => {
                    const key = a.q.subtopic || P.title(a.q.topic);
                    const s = bySub[key] || (bySub[key] = { total: 0, correct: 0 });
                    s.total++;
                    if (a.correct) s.correct++;
                });
                const subs = Object.entries(bySub).map(([name, s]) => ({ name, ...s, pct: Math.round(s.correct * 100 / s.total) }))
                    .sort((a, b) => a.pct - b.pct);
                const weak = subs.filter((s) => s.pct < 60).map((s) => s.name);
                const strong = subs.filter((s) => s.pct >= 80).map((s) => s.name);
                const band = pct >= 85 ? ['Excellent', 'text-success', 'You are well prepared for this section. Try a harder difficulty next.']
                    : pct >= 65 ? ['Good', 'text-primary', 'Solid work. Revise the explanations of the questions you missed and practise once more.']
                        : pct >= 40 ? ['Fair', 'text-warning', 'You know the basics. Focus on your weak areas below before attempting harder sets.']
                            : ['Needs practice', 'text-danger', 'Start with EASY questions, read every explanation carefully and retry this topic.'];
                const pace = avg < 10 && pct < 50 ? 'You answered in about ' + avg.toFixed(0) + ' s per question - slow down and work each question out before choosing.'
                    : avg <= 45 ? 'Your pace (' + avg.toFixed(0) + ' s per question) fits a typical placement test.'
                    : 'You averaged ' + avg.toFixed(0) + ' s per question; placement tests usually allow about 45-60 s, so work on speed.';
                const note = total < requested ? '<div class="alert alert-light small">Only ' + total + ' question(s) matched these filters, so the set was shorter than the ' + requested + ' you asked for.</div>' : '';

                $('#quiz').innerHTML = note + '<div class="row g-3 align-items-center"><div class="col-md-4 text-center">' + P.ring(pct, 'score') + '</div><div class="col-md-8">' +
                    '<h4 class="mb-1 ' + band[1] + '">' + band[0] + '</h4><div class="mb-2">' + esc(band[2]) + '</div>' +
                    '<div class="row text-center g-2 mb-2">' + [['Correct', correct + ' / ' + total], ['Accuracy', pct + '%'], ['Time', Math.floor(secs / 60) + 'm ' + (secs % 60) + 's'], ['Per question', avg.toFixed(0) + ' s']]
                        .map(([k, v]) => '<div class="col-3"><div class="small text-muted">' + k + '</div><div class="fw-bold">' + v + '</div></div>').join('') + '</div>' +
                    '<div class="small text-muted">' + esc(pace) + '</div></div></div><hr>' +
                    '<h6>By area</h6>' + subs.map((s) => '<div class="small d-flex justify-content-between"><span>' + esc(s.name) + '</span><span>' + s.correct + '/' + s.total + '</span></div>' +
                        P.progress(s.pct, 100, s.pct >= 80 ? 'bg-success' : s.pct >= 60 ? '' : 'bg-danger') + '<div class="mb-1"></div>').join('') +
                    '<div class="row small mt-2"><div class="col-md-6"><strong>Strong areas</strong><div>' + P.chips(strong, 'ok') + '</div></div>' +
                    '<div class="col-md-6"><strong>Revise these</strong><div>' + P.chips(weak, 'bad') + '</div></div></div><hr>' +
                    '<h6>Review your answers</h6><div class="accordion" id="review">' + answers.map((a, n) => '<div class="accordion-item"><h2 class="accordion-header"><button class="accordion-button collapsed py-2" type="button" data-bs-toggle="collapse" data-bs-target="#rv' + n + '">' +
                        '<i class="bi ' + (a.correct ? 'bi-check-circle-fill text-success' : 'bi-x-circle-fill text-danger') + ' me-2"></i><span class="small">Q' + (n + 1) + '. ' + esc(a.q.text.length > 90 ? a.q.text.slice(0, 90) + '...' : a.q.text) + '</span></button></h2>' +
                        '<div id="rv' + n + '" class="accordion-collapse collapse" data-bs-parent="#review"><div class="accordion-body small"><div class="pre-wrap mb-2">' + esc(a.q.text) + '</div>' +
                        '<div>Your answer: <strong class="' + (a.correct ? 'text-success' : 'text-danger') + '">' + String.fromCharCode(65 + a.selected) + '. ' + esc(a.q.options[a.selected]) + '</strong></div>' +
                        (a.correct ? '' : '<div>Correct answer: <strong class="text-success">' + String.fromCharCode(65 + a.correctIndex) + '. ' + esc(a.q.options[a.correctIndex]) + '</strong></div>') +
                        (a.explanation ? '<div class="text-muted mt-1">' + esc(a.explanation) + '</div>' : '') + '</div></div></div>').join('') + '</div>' +
                    '<div class="d-flex gap-2 mt-3"><button class="btn btn-primary" id="again"><i class="bi bi-arrow-repeat me-1"></i>Practise again</button></div>';
                $('#again').addEventListener('click', () => $('#start').click());
                stats();
            };
            show();
        });
    });

    // ================================================================= tests

    P.page('student-tests', async () => {
        const tests = await P.get('/api/assessment/tests');
        P.table('#test-table', [
            { label: 'Test', render: (t) => '<span class="fw-semibold">' + esc(t.title) + '</span><div class="small text-muted">' + P.title(t.type) + ' · ' + t.durationMinutes + ' min' +
                (t.proctored ? ' · <i class="bi bi-camera-video"></i> proctored' : '') + (t.ownerName ? ' · by ' + esc(t.ownerName) : '') + '</div>' },
            { label: 'Window', render: (t) => P.fmtDateTime(t.windowStart) + '<div class="small text-muted">to ' + P.fmtDateTime(t.windowEnd) + '</div>' },
            { label: 'Status', render: (t) => (t.myAttemptStatus ? P.badge(t.myAttemptStatus) : P.badge(t.status)) },
            { label: '', cls: 'text-end', render: (t) => {
                const now = Date.now();
                const open = now >= new Date(t.windowStart).getTime() && now <= new Date(t.windowEnd).getTime();
                if (t.myAttemptStatus === 'IN_PROGRESS') return '<a class="btn btn-sm btn-warning" href="/student/tests/' + t.id + '/take">Resume</a>';
                if (t.myAttemptStatus) return '<a class="btn btn-sm btn-outline-primary" href="/student/results/' + t.myAttemptId + '">Result</a>';
                if (open) return '<a class="btn btn-sm btn-primary" href="/student/tests/' + t.id + '/take">Start</a>';
                return '<span class="small text-muted">' + (now < new Date(t.windowStart).getTime() ? 'Opens ' + P.fromNow(t.windowStart) : 'Closed') + '</span>';
            } }
        ], tests, 'No tests assigned to you right now');
        const attempts = await P.get('/api/assessment/attempts/me');
        P.table('#attempt-table', [
            { label: 'Test', render: (a) => esc(a.testTitle) },
            { label: 'Submitted', render: (a) => P.fmtDateTime(a.submittedAt) },
            { label: 'Score', render: (a) => (a.percent != null ? P.pct(a.percent) : '-') },
            { label: 'Result', render: (a) => (a.passed == null ? P.badge(a.status) : a.passed ? '<span class="badge text-bg-success">Passed</span>' : '<span class="badge text-bg-danger">Not cleared</span>') },
            { label: '', cls: 'text-end', render: (a) => '<a class="btn btn-sm btn-light" href="/student/results/' + a.attemptId + '">Details</a>' }
        ], attempts, 'No attempts yet');
    });

    P.page('student-result', async () => {
        const id = $('#attempt-id').value;
        const r = await P.get('/api/assessment/attempts/' + id + '/result');
        const b = r.breakdown || {};
        $('#result').innerHTML = '<div class="page-head"><div><h1>' + esc(r.testTitle) + '</h1><div class="sub">Submitted ' + P.fmtDateTime(r.submittedAt) + ' · ' + P.badge(r.status) + '</div></div>' +
            '<a class="btn btn-light" href="/student/tests">Back to tests</a></div>' +
            '<div class="row g-3"><div class="col-md-4"><div class="card"><div class="card-body text-center">' + P.ring(r.percent || 0, 'percent') +
            '<div class="mt-3">' + (r.passed == null ? '' : r.passed ? '<span class="badge text-bg-success fs-6">Cleared</span>' : '<span class="badge text-bg-danger fs-6">Not cleared</span>') + '</div></div></div></div>' +
            '<div class="col-md-8"><div class="card"><div class="card-body"><div class="row text-center g-3">' +
            [['MCQ score', r.mcqScore], ['Coding score', r.codingScore], ['Total', (r.totalScore != null ? r.totalScore : '-') + ' / ' + (r.maxScore != null ? r.maxScore : '-')],
                ['Trust score', r.trustScore]].filter(([k, v]) => k !== 'Trust score' || v != null).map(([k, v]) => '<div class="col-6 col-md-3"><div class="text-muted small">' + k + '</div><div class="fs-4 fw-bold">' + esc(v == null ? '-' : v) + '</div></div>').join('') +
            '</div>' + (r.terminationReason ? '<div class="alert alert-danger mt-3 mb-0"><strong>Test ended early:</strong> ' + esc(r.terminationReason) + '</div>' : '') +
            (r.proctoringFlagged ? '<div class="alert alert-warning mt-3 mb-0">This attempt was flagged for review by proctoring.</div>' : '') +
            (b.mcqTotal ? '<hr><h6>Multiple-choice questions</h6><div class="row text-center small">' + [['Answered', b.mcqAnswered + ' / ' + b.mcqTotal], ['Correct', b.mcqCorrect],
                ['Accuracy', Math.round(100 * b.mcqCorrect / Math.max(1, b.mcqAnswered)) + '%']].map(([k, v]) => '<div class="col-4"><div class="text-muted">' + k + '</div><div class="fw-bold fs-5">' + esc(v) + '</div></div>').join('') + '</div>' : '') +
            ((b.coding || []).length ? '<hr><h6>Coding questions</h6><table class="table table-sm mb-0"><thead><tr><th>#</th><th>Test cases passed</th><th class="text-end">Marks</th></tr></thead><tbody>' +
                b.coding.map((c, i) => '<tr><td>' + (i + 1) + '</td><td>' + (c.total ? c.passed + ' / ' + c.total : '<span class="text-muted">not submitted</span>') + '</td><td class="text-end">' +
                    esc(c.marks) + ' / ' + esc(c.maxMarks) + '</td></tr>').join('') + '</tbody></table>' : '') + '</div></div></div></div>';
    });

    // ================================================================= ATS

    function atsView(a) {
        const bd = a.breakdown || {};
        const ai = a.aiFeedback;
        return '<div class="card"><div class="card-body"><div class="row g-3 align-items-center"><div class="col-md-4">' + P.ring(a.score, 'ATS score') + '</div><div class="col-md-8">' +
            '<h5>' + esc(a.jobTitle || 'Job') + (a.companyName ? ' · ' + esc(a.companyName) : '') + '</h5>' +
            Object.entries(bd).map(([k, v]) => '<div class="small d-flex justify-content-between"><span>' + esc(P.label(k)) + '</span><span>' + v + '</span></div>' + P.progress(v, 100) + '<div class="mb-1"></div>').join('') +
            '</div></div><hr><div class="row g-3"><div class="col-md-6"><h6>Matched keywords <span class="badge text-bg-success">' + (a.matchedKeywords || []).length + '</span></h6>' + P.chips(a.matchedKeywords, 'ok') + '</div>' +
            '<div class="col-md-6"><h6>Missing keywords <span class="badge text-bg-danger">' + (a.missingKeywords || []).length + '</span></h6>' + P.chips(a.missingKeywords, 'bad') +
            ((a.missingKeywords || []).length ? '<div class="small text-muted mt-1">Add the ones you genuinely have to your skills or project descriptions.</div>' : '') + '</div></div>' +
            ((a.tips || []).length ? '<hr><h6>Tips</h6><ul class="small">' + a.tips.map((t) => '<li>' + esc(t) + '</li>').join('') + '</ul>' : '') +
            (ai ? '<hr><h6>Written feedback <span class="badge text-bg-light">fit ' + esc(ai.fitScore) + '/100</span></h6><div class="row small"><div class="col-md-4"><strong>Strengths</strong><ul>' +
                (ai.strengths || []).map((s) => '<li>' + esc(s) + '</li>').join('') + '</ul></div><div class="col-md-4"><strong>Weaknesses</strong><ul>' +
                (ai.weaknesses || []).map((s) => '<li>' + esc(s) + '</li>').join('') + '</ul></div><div class="col-md-4"><strong>Suggestions</strong><ul>' +
                (ai.suggestions || []).map((s) => '<li>' + esc(s) + '</li>').join('') + '</ul></div></div>' : '') + '</div></div>';
    }

    P.page('student-ats', async () => {
        const history = async () => {
            const page = await P.get('/api/profile/ats/history?size=10');
            P.table('#ats-history', [
                { label: 'Job', render: (a) => esc(a.jobTitle || '-') + '<div class="small text-muted">' + P.fmtDate(a.createdAt) + '</div>' },
                { label: 'Score', render: (a) => '<span class="fw-bold">' + a.score + '</span>' },
                { label: '', cls: 'text-end', render: (a) => '<button class="btn btn-sm btn-light" data-ats="' + a.id + '">View</button>' }
            ], P.content(page), 'No checks yet');
        };
        history();
        P.on('#ats-history', '[data-ats]', async (b) => { $('#ats-result').innerHTML = atsView(await P.get('/api/profile/ats/' + b.dataset.ats)); });

        let mode = 'file';
        const form = $('#ats-form');
        P.$$('[data-jd]').forEach((b) => b.addEventListener('click', () => {
            mode = b.dataset.jd;
            P.$$('[data-jd]').forEach((x) => x.classList.toggle('active', x === b));
            ['file', 'text', 'job'].forEach((m) => $('#jd-' + m).classList.toggle('d-none', m !== mode));
        }));
        const jdText = form.querySelector('[name=jobDescription]');
        jdText.addEventListener('input', () => { $('#jd-count').textContent = jdText.value.trim().length; });
        P.$$('[name=resumeSource]').forEach((r) => r.addEventListener('change', () => {
            $('#resume-file').classList.toggle('d-none', !$('#rs-file').checked);
        }));
        P.get('/api/profile/students/me').then((p) => {
            $('#profile-resume').textContent = p.hasResume ? '(' + (p.resumeOriginalName || 'resume') + ')' : '(none uploaded yet)';
        }).catch(() => {});
        let jobs = [];
        P.get('/api/placement/jobs?status=OPEN').then((list) => {
            jobs = list;
            $('#jd-job-select').innerHTML += list.map((j) => '<option value="' + j.id + '">' + esc(j.companyName + ' - ' + j.title) + '</option>').join('');
        }).catch(() => {});
        $('#jd-job-select').addEventListener('change', (ev) => {
            const j = jobs.find((x) => String(x.id) === ev.target.value);
            if (!j) return;
            form.querySelector('[name=jobTitle]').value = j.title;
            form.querySelector('[name=companyName]').value = j.companyName;
        });

        form.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const fd = new FormData();
            ['jobTitle', 'companyName'].forEach((n) => { const v = form.querySelector('[name=' + n + ']').value.trim(); if (v) fd.append(n, v); });
            if (mode === 'file') {
                const f = form.querySelector('[name=jobDescriptionFile]').files[0];
                if (!f) { P.toast('Choose the job description file', 'warning'); return; }
                fd.append('jobDescriptionFile', f);
            } else if (mode === 'text') {
                if (jdText.value.trim().length < 50) { P.toast('Paste the full job description (at least 50 characters)', 'warning'); return; }
                fd.append('jobDescription', jdText.value.trim());
            } else {
                const j = jobs.find((x) => String(x.id) === $('#jd-job-select').value);
                if (!j) { P.toast('Choose a job opening', 'warning'); return; }
                fd.append('jobDescription', j.description + '\nRequired skills: ' + (j.requiredSkills || []).join(', '));
            }
            if ($('#rs-file').checked) {
                const r = form.querySelector('[name=resumeFile]').files[0];
                if (!r) { P.toast('Choose the resume file', 'warning'); return; }
                fd.append('resumeFile', r);
            }
            fd.append('useAi', $('#useAi').checked ? 'true' : 'false');
            await P.busy(form.querySelector('button[type=submit]'), async () => {
                try {
                    $('#ats-result').innerHTML = atsView(await P.post('/api/profile/ats/check-files', fd));
                    $('#ats-result').scrollIntoView({ behavior: 'smooth', block: 'start' });
                    history();
                } catch (e) { P.fail(e); }
            });
        });
    });

    // ================================================================= mock interviews

    P.page('student-mock', async () => {
        const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        const canSpeak = 'speechSynthesis' in window;
        const voice = { on: true, recognizer: null, listening: false, spokenFor: null };
        P.get('/api/ai/status').then((s) => { $('#ai-engine').innerHTML = '<i class="bi bi-cpu me-1"></i>' + esc(s.engine); }).catch(() => {});

        const speak = (text) => new Promise((resolve) => {
            if (!voice.on || !canSpeak || !text) { resolve(); return; }
            window.speechSynthesis.cancel();
            const u = new SpeechSynthesisUtterance(text);
            const voices = window.speechSynthesis.getVoices();
            u.voice = voices.find((v) => v.lang === 'en-IN') || voices.find((v) => v.lang && v.lang.startsWith('en')) || null;
            u.rate = 0.98;
            const box = $('#interviewer');
            if (box) box.classList.add('speaking');
            u.onend = u.onerror = () => { if ($('#interviewer')) $('#interviewer').classList.remove('speaking'); resolve(); };
            window.speechSynthesis.speak(u);
        });

        const stopListening = () => {
            if (voice.recognizer) { try { voice.recognizer.stop(); } catch (e) { /* already stopped */ } }
            voice.listening = false;
            const b = $('#mic');
            if (b) { b.classList.remove('listening', 'btn-danger'); b.classList.add('btn-outline-primary'); b.innerHTML = '<i class="bi bi-mic me-1"></i>Answer by voice'; }
            if ($('#interim')) $('#interim').textContent = '';
        };

        const startListening = () => {
            if (!Recognition) { P.toast('Voice answers need Chrome or Edge. You can type your answer instead.', 'warning'); return; }
            window.speechSynthesis && window.speechSynthesis.cancel();
            const rec = new Recognition();
            rec.lang = 'en-IN';
            rec.continuous = true;
            rec.interimResults = true;
            rec.onresult = (ev) => {
                const box = $('#answer-form textarea');
                let interim = '';
                for (let i = ev.resultIndex; i < ev.results.length; i++) {
                    const t = ev.results[i][0].transcript;
                    if (ev.results[i].isFinal) box.value = (box.value ? box.value.trimEnd() + ' ' : '') + t.trim();
                    else interim += t;
                }
                $('#interim').textContent = interim;
            };
            rec.onerror = (e) => { if (e.error === 'not-allowed') P.toast('Microphone access was blocked. Allow it in the browser to answer by voice.', 'warning'); stopListening(); };
            rec.onend = () => { if (voice.listening) { try { rec.start(); } catch (e) { stopListening(); } } };
            voice.recognizer = rec;
            voice.listening = true;
            rec.start();
            const b = $('#mic');
            b.classList.add('listening', 'btn-danger');
            b.classList.remove('btn-outline-primary');
            b.innerHTML = '<i class="bi bi-stop-circle me-1"></i>Stop recording';
        };

        const past = async () => {
            const list = await P.get('/api/ai/mock-interviews');
            P.table('#past', [
                { label: 'Interview', render: (i) => esc(i.role) + '<div class="small text-muted">' + P.title(i.type) + ' · ' + P.fmtDate(i.createdAt) + '</div>' },
                { label: 'Score', render: (i) => (i.overallScore != null ? '<strong>' + i.overallScore + '</strong>' : P.badge(i.status)) },
                { label: '', cls: 'text-end', render: (i) => '<button class="btn btn-sm btn-light" data-open="' + i.id + '">Open</button>' }
            ], list, 'No interviews yet');
        };
        past();
        P.on('#past', '[data-open]', async (b) => { voice.spokenFor = null; show(await P.get('/api/ai/mock-interviews/' + b.dataset.open), false); });
        $('#start-form').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            voice.on = $('#voice-mode').checked;
            await P.busy(ev.target.querySelector('button'), async () => {
                try { show(await P.post('/api/ai/mock-interviews', P.formData(ev.target)), true); past(); } catch (e) { P.fail(e); }
            });
        });

        function show(iv, announce) {
            stopListening();
            const area = $('#interview-area');
            const turns = iv.turns.map((t) => '<div class="chat-msg ASSISTANT"><div class="small text-muted mb-1">Question ' + t.seq + '</div>' + esc(t.question) + '</div>' +
                (t.answer ? '<div class="chat-msg USER">' + esc(t.answer) + '</div><div class="small ms-2 mb-3"><span class="badge ' + (t.score >= 7 ? 'text-bg-success' : t.score >= 4 ? 'text-bg-warning' : 'text-bg-danger') + '">' +
                    t.score + '/10</span> ' + esc(t.feedback || '') + (t.idealPoints && t.idealPoints.length ? '<details class="mt-1"><summary>What a strong answer covers</summary><ul>' +
                    t.idealPoints.map((p) => '<li>' + esc(p) + '</li>').join('') + '</ul></details>' : '') + '</div>' : '')).join('');
            const s = iv.summary;
            const inProgress = iv.status === 'IN_PROGRESS';
            area.innerHTML = '<div class="card"><div class="card-header d-flex justify-content-between align-items-center"><span>' + esc(iv.role) + ' · ' + P.title(iv.type) + ' · ' + iv.answered + '/' + iv.questionCount +
                '</span><span class="d-flex gap-2 align-items-center">' + (canSpeak ? '<div class="form-check form-switch small mb-0"><input class="form-check-input" type="checkbox" id="voice-toggle"' + (voice.on ? ' checked' : '') +
                '><label class="form-check-label" for="voice-toggle">Voice</label></div>' : '') + '<a href="/student/mock-interviews" class="small">New interview</a></span></div><div class="card-body">' +
                (inProgress ? '<div class="interviewer" id="interviewer"><div class="face"><i class="bi bi-person-video3"></i></div><div class="flex-grow-1"><div class="small text-muted">Interviewer · question ' + (iv.answered + 1) + ' of ' + iv.questionCount +
                    '</div><div class="fw-semibold">' + esc(iv.currentQuestion || '') + '</div></div>' + (canSpeak ? '<button class="btn btn-sm btn-light" id="repeat" title="Repeat the question"><i class="bi bi-volume-up"></i></button>' : '') + '</div>' : '') +
                '<div class="chat-box" id="chat">' + turns + '</div>' +
                (inProgress ? '<form id="answer-form" class="mt-3"><textarea class="form-control" name="answer" rows="5" required maxlength="6000" placeholder="Speak or type your answer as you would in the interview..."></textarea>' +
                    '<div class="interim small mt-1" id="interim"></div><div class="d-flex flex-wrap gap-2 mt-2"><button class="btn btn-outline-primary mic-btn" type="button" id="mic"><i class="bi bi-mic me-1"></i>Answer by voice</button>' +
                    '<button class="btn btn-primary" type="submit"><i class="bi bi-send me-1"></i>Submit answer</button></div>' +
                    '<div class="form-text">Tip: aim for 45-90 seconds - state the main idea, explain it, and give an example from your projects.</div></form>'
                    : '<div class="mt-3">' + P.ring(iv.overallScore || 0, 'overall') + (s ? '<p class="text-center fw-semibold mt-2">' + esc(s.verdict) + '</p><div class="row small"><div class="col-md-4"><strong>Strengths</strong><ul>' +
                        (s.strengths || []).map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul></div><div class="col-md-4"><strong>Improve</strong><ul>' + (s.improvements || []).map((x) => '<li>' + esc(x) + '</li>').join('') +
                        '</ul></div><div class="col-md-4"><strong>Revise</strong><ul>' + (s.topicsToRevise || []).map((x) => '<li>' + esc(x) + '</li>').join('') + '</ul></div></div>' : '') + '</div>') + '</div></div>';
            const chat = $('#chat');
            chat.scrollTop = chat.scrollHeight;
            const toggle = $('#voice-toggle');
            if (toggle) toggle.addEventListener('change', () => { voice.on = toggle.checked; if (!voice.on && canSpeak) window.speechSynthesis.cancel(); });
            if ($('#repeat')) $('#repeat').addEventListener('click', () => speak(iv.currentQuestion));
            if ($('#mic')) $('#mic').addEventListener('click', () => (voice.listening ? stopListening() : startListening()));
            const f = $('#answer-form');
            if (f) f.addEventListener('submit', async (ev) => {
                ev.preventDefault();
                stopListening();
                await P.busy(f.querySelector('button[type=submit]'), async () => {
                    try { show(await P.post('/api/ai/mock-interviews/' + iv.id + '/answer', P.formData(f)), true); past(); } catch (e) { P.fail(e); }
                });
            });
            if (!announce || voice.spokenFor === iv.id + ':' + iv.answered) return;
            voice.spokenFor = iv.id + ':' + iv.answered;
            const last = iv.turns.filter((t) => t.answer).slice(-1)[0];
            const feedback = last ? 'You scored ' + last.score + ' out of 10. ' + (last.feedback || '').split('. ')[0] + '. ' : '';
            if (inProgress) speak(feedback + (iv.answered === 0 ? "Hello, let's begin. " : 'Next question. ') + iv.currentQuestion);
            else speak(feedback + 'That completes the interview. Your overall score is ' + (iv.overallScore || 0) + '. ' + (s && s.verdict ? s.verdict : ''));
        }
    });

    // ================================================================= experiences

    function experienceCard(e) {
        return '<div class="card mb-3"><div class="card-body"><div class="d-flex justify-content-between flex-wrap gap-2"><div><h5 class="mb-0">' + esc(e.companyName) + ' · ' + esc(e.jobRole) + '</h5>' +
            '<div class="small text-muted">' + esc(e.author) + ' · ' + e.year + ' · ' + P.badge(e.result) + ' ' + P.badge(e.difficulty) + '</div></div>' +
            '<button class="btn btn-sm ' + (e.votedByMe ? 'btn-primary' : 'btn-outline-primary') + '" data-vote="' + e.id + '" ' + (e.mine ? 'disabled' : '') + '><i class="bi bi-hand-thumbs-up"></i> ' + e.upvotes + '</button></div>' +
            (e.roundsSummary ? '<div class="mt-2 small"><strong>Rounds:</strong> ' + esc(e.roundsSummary) + '</div>' : '') +
            '<div class="mt-2 pre-wrap">' + esc(e.content) + '</div>' +
            (e.questionsAsked ? '<div class="mt-2"><strong class="small">Questions asked</strong><div class="pre-wrap small">' + esc(e.questionsAsked) + '</div></div>' : '') +
            (e.tips ? '<div class="mt-2 alert alert-light small mb-0"><strong>Tips:</strong> ' + esc(e.tips) + '</div>' : '') + '</div></div>';
    }
    P.experienceCard = experienceCard;

    P.page('student-experiences', async () => {
        let page = 0;
        const companies = await P.get('/api/interviews/experiences/companies');
        $('#exp-company').innerHTML += companies.map((c) => '<option>' + esc(c.companyName) + '</option>').join('');
        const load = async () => {
            const res = await P.get('/api/interviews/experiences' + P.qs({ company: $('#exp-company').value, q: $('#exp-q').value, page }));
            const list = P.content(res);
            $('#exp-list').innerHTML = list.length ? list.map(experienceCard).join('') : P.empty('No experiences yet', 'chat-quote');
            P.pager('#exp-pager', res, (p) => { page = p; load(); });
        };
        let t;
        $('#exp-q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 300); });
        $('#exp-company').addEventListener('change', () => { page = 0; load(); });
        load();
        P.on('#exp-list', '[data-vote]', async (b) => { try { await P.post('/api/interviews/experiences/' + b.dataset.vote + '/vote'); load(); } catch (e) { P.fail(e); } });

        const mine = async () => {
            const list = await P.get('/api/interviews/experiences/mine');
            P.table('#my-exp', [
                { label: 'Company', render: (e) => esc(e.companyName) + '<div class="small text-muted">' + esc(e.jobRole) + '</div>' },
                { label: 'Status', render: (e) => P.badge(e.status) + (e.reviewNote ? '<div class="small text-muted">' + esc(e.reviewNote) + '</div>' : '') },
                { label: 'Upvotes', render: (e) => e.upvotes },
                { label: '', cls: 'text-end', render: (e) => (e.status !== 'APPROVED' ? '<button class="btn btn-sm btn-outline-danger" data-del="' + e.id + '">Delete</button>' : '') }
            ], list, 'You have not shared any experience yet');
        };
        P.on('#my-exp', '[data-del]', async (b) => { if (await P.confirm('Delete', 'Delete this submission?', 'Delete', 'btn-danger')) { await P.del('/api/interviews/experiences/' + b.dataset.del); mine(); } });

        $$('[data-tab]').forEach((a) => a.addEventListener('click', (ev) => {
            ev.preventDefault();
            $$('[data-tab]').forEach((x) => x.classList.toggle('active', x === a));
            $$('[data-pane]').forEach((p) => p.classList.toggle('d-none', p.dataset.pane !== a.dataset.tab));
            if (a.dataset.tab === 'mine') mine();
        }));

        $('#write').addEventListener('click', () => {
            P.modal('Share your interview experience', '<form id="exp-form"><div class="row g-2">' +
                '<div class="col-md-6"><label class="form-label">Company</label><input class="form-control" name="companyName" required maxlength="255"></div>' +
                '<div class="col-md-6"><label class="form-label">Role</label><input class="form-control" name="jobRole" required maxlength="255"></div>' +
                '<div class="col-md-4"><label class="form-label">Year</label><input class="form-control" type="number" name="year" value="' + new Date().getFullYear() + '" min="2000" max="2100" required></div>' +
                '<div class="col-md-4"><label class="form-label">Result</label><select class="form-select" name="result"><option>SELECTED</option><option>REJECTED</option><option>WAITLISTED</option></select></div>' +
                '<div class="col-md-4"><label class="form-label">Difficulty</label><select class="form-select" name="difficulty"><option>EASY</option><option selected>MEDIUM</option><option>HARD</option></select></div>' +
                '<div class="col-12"><label class="form-label">Rounds</label><input class="form-control" name="roundsSummary" placeholder="Online test → Technical 1 → HR"></div>' +
                '<div class="col-12"><label class="form-label">Your experience (min. 100 characters)</label><textarea class="form-control" name="content" rows="6" required minlength="100" maxlength="20000"></textarea></div>' +
                '<div class="col-12"><label class="form-label">Questions asked</label><textarea class="form-control" name="questionsAsked" rows="3" maxlength="5000"></textarea></div>' +
                '<div class="col-12"><label class="form-label">Tips for juniors</label><textarea class="form-control" name="tips" rows="2" maxlength="3000"></textarea></div>' +
                '<div class="col-12 form-check ms-1"><input class="form-check-input" type="checkbox" name="anonymous" id="anon"><label class="form-check-label" for="anon">Post anonymously</label></div>' +
                '</div></form>', [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Submit for review', onClick: async (el) => {
                const f = el.querySelector('#exp-form');
                if (!f.reportValidity()) return false;
                await P.post('/api/interviews/experiences', P.formData(f));
                P.toast('Thanks! The placement office will review it shortly.');
            } }], { size: 'lg' });
        });
    });

    // ================================================================= prep hub

    P.page('student-prep', async () => {
        P.get('/api/interviews/experiences/companies').then((cs) => {
            $('#company-list').innerHTML = cs.map((c) => '<option value="' + esc(c.companyName) + '">').join('');
        });
        const open = async (name) => {
            $('#hub').innerHTML = P.spinner();
            try {
                const h = await P.get('/api/interviews/prep' + P.qs({ company: name }));
                const st = h.stats || {};
                const ai = h.aiSummary;
                $('#hub').innerHTML = '<h3 class="mb-3">' + esc(h.companyName) + '</h3><div class="row g-3">' +
                    '<div class="col-lg-7"><div class="card h-100"><div class="card-header">Selection trend</div><div class="card-body"><div style="height:240px"><canvas id="trend"></canvas></div></div></div></div>' +
                    '<div class="col-lg-5"><div class="card h-100"><div class="card-header">Round pattern &amp; skills</div><div class="card-body">' +
                    ((st.roundPattern || []).length ? '<ol>' + st.roundPattern.map((r) => '<li>' + esc(r) + '</li>').join('') + '</ol>' : '<p class="text-muted small">No drives recorded yet.</p>') +
                    '<h6>Skills asked</h6>' + P.chips(st.skillsAsked) + '</div></div></div>' +
                    '<div class="col-12"><div class="card"><div class="card-header">AI summary of ' + h.approvedExperiences + ' experience(s)' + (h.summaryGeneratedAt ? ' <span class="small text-muted">· ' + P.fmtDate(h.summaryGeneratedAt) + '</span>' : '') +
                    '</div><div class="card-body">' + (ai ? '<p>' + esc(ai.summary) + '</p><div class="row small"><div class="col-md-4"><strong>Common topics</strong><div>' + P.chips(ai.commonTopics) +
                        '</div></div><div class="col-md-4"><strong>Pattern</strong><div>' + esc(ai.roundPattern) + '</div><strong>Difficulty</strong> ' + P.badge(ai.difficulty) + '</div><div class="col-md-4"><strong>Tips</strong><ul>' +
                        (ai.tips || []).map((t) => '<li>' + esc(t) + '</li>').join('') + '</ul></div></div>' : '<span class="text-muted">No summary yet - it appears once seniors share experiences.</span>') + '</div></div></div>' +
                    '<div class="col-12"><h5>Most helpful experiences</h5>' + ((h.topExperiences || []).map(experienceCard).join('') || P.empty('No experiences shared yet', 'chat-quote')) + '</div></div>';
                const years = st.years || [];
                P.chart('trend', {
                    type: 'bar',
                    data: { labels: years.map((y) => y.year), datasets: [
                        { label: 'Applied', data: years.map((y) => y.applications), backgroundColor: '#cfd8d6' },
                        { label: 'Shortlisted', data: years.map((y) => y.shortlisted), backgroundColor: '#8fb8b0' },
                        { label: 'Selected', data: years.map((y) => y.selected), backgroundColor: '#2a9d8f' }] },
                    options: { plugins: { legend: { display: true } } }
                });
                history.replaceState(null, '', '?company=' + encodeURIComponent(name));
            } catch (e) { P.fail(e); $('#hub').innerHTML = ''; }
        };
        $('#prep-form').addEventListener('submit', (ev) => { ev.preventDefault(); open($('#company').value.trim()); });
        if (P.param('company')) { $('#company').value = P.param('company'); open(P.param('company')); }
        $('#iq-form').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            await P.busy(ev.target.querySelector('button'), async () => {
                try {
                    const qs = await P.post('/api/ai/interview-questions', P.formData(ev.target));
                    $('#iq-list').innerHTML = '<ol>' + qs.map((q) => '<li class="mb-2"><span>' + esc(q.question) + '</span> ' + P.badge(q.type) + ' ' + P.badge(q.difficulty) +
                        (q.hint ? '<div class="small text-muted">' + esc(q.hint) + '</div>' : '') + '</li>').join('') + '</ol>';
                } catch (e) { P.fail(e); }
            });
        });
    });
})();
