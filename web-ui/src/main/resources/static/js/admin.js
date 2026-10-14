/* Administrator pages. */
(function () {
    'use strict';
    const P = window.Placify;
    const { $, esc } = P;

    P.page('admin-dashboard', async () => {
        const o = await P.get('/api/analytics/admin/overview');
        const u = o.users || {};
        $('#stats').innerHTML = P.stat('Students', u.students || 0, 'mortarboard', 'bg-soft-blue') +
            P.stat('Companies', u.companies || 0, 'building', 'bg-soft-purple', (u.pendingCompanies || 0) + ' pending approval') +
            P.stat('Logins (7 days)', u.successfulLogins7d || 0, 'box-arrow-in-right', 'bg-soft-green', (u.failedLogins7d || 0) + ' failed') +
            P.stat('Audit events (24 h)', o.auditEvents24h, 'journal-text', 'bg-soft-amber', o.failedActions24h + ' failed · ' + (u.blockedUsers || 0) + ' blocked users');
        P.chart('actions', { type: 'bar', data: { labels: o.topActions7d.map((a) => P.title(a.name)), datasets: [{ data: o.topActions7d.map((a) => a.count), backgroundColor: '#2a9d8f' }] }, options: { indexAxis: 'y' } });
        try {
            const s = await P.get('/api/gateway/services');
            P.table('#services', [
                { label: 'Service', render: (x) => esc(x.name) },
                { label: 'Instances', render: (x) => x.instances },
                { label: 'Status', render: (x) => (x.up ? '<span class="badge text-bg-success">UP</span>' : '<span class="badge text-bg-danger">DOWN</span>') }
            ], s, 'No service information');
        } catch (e) { $('#services').innerHTML = P.empty('Service registry unavailable'); }
    });

    P.page('admin-users', async () => {
        let page = 0;
        const load = async () => {
            const res = await P.get('/api/auth/users' + P.qs({ q: $('#q').value, role: $('#role').value, status: $('#status').value, page }));
            P.table('#list', [
                { label: 'User', render: (x) => '<span class="fw-semibold">' + esc(x.name) + '</span><div class="small text-muted">' + esc(x.loginId) + ' · ' + esc(x.email) + '</div>' },
                { label: 'Role', render: (x) => P.title(x.role) },
                { label: 'Status', render: (x) => P.badge(x.status) + (x.locked ? ' <span class="badge text-bg-warning">locked</span>' : '') + (x.twoFactorEnabled ? ' <i class="bi bi-shield-check text-success" title="2-step login"></i>' : '') +
                    (x.statusReason ? '<div class="small text-muted">' + esc(x.statusReason) + '</div>' : '') },
                { label: 'Last login', render: (x) => P.fmtDateTime(x.lastLoginAt) },
                { label: '', cls: 'text-end', render: (x) => (x.id === document.body.dataset.userId ? '' : x.status === 'BLOCKED'
                    ? '<button class="btn btn-sm btn-outline-success" data-unblock="' + x.id + '">Unblock</button>' : x.status === 'ACTIVE' ? '<button class="btn btn-sm btn-outline-danger" data-block="' + x.id + '">Block</button>' : '') }
            ], P.content(res), 'No users found');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        let t;
        $('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 300); });
        ['#role', '#status'].forEach((s) => $(s).addEventListener('change', () => { page = 0; load(); }));
        load();
        P.on('#list', '[data-block]', async (b) => {
            const reason = await P.prompt('Block account', 'Reason', '', true);
            if (reason == null) return;
            try { await P.post('/api/auth/users/' + b.dataset.block + '/block', { reason }); P.toast('Blocked - active sessions were ended'); load(); } catch (e) { P.fail(e); }
        });
        P.on('#list', '[data-unblock]', async (b) => { try { await P.post('/api/auth/users/' + b.dataset.unblock + '/unblock'); load(); } catch (e) { P.fail(e); } });
        const created = (a) => P.modal('Account created', '<p>Share these details securely. The user must change the password at first login.</p><table class="table table-sm"><tr><th>Login</th><td class="mono">' +
            esc(a.loginId) + '</td></tr><tr><th>E-mail</th><td>' + esc(a.email) + '</td></tr><tr><th>Temporary password</th><td class="mono">' + esc(a.temporaryPassword || '(e-mailed)') + '</td></tr></table>');
        $('#new-tpo').addEventListener('click', () => P.modal('New placement officer', '<form id="tf" class="row g-2"><div class="col-md-6"><label class="form-label small">Employee ID</label><input class="form-control" name="employeeId" required></div>' +
            '<div class="col-md-6"><label class="form-label small">Name</label><input class="form-control" name="name" required></div><div class="col-md-6"><label class="form-label small">E-mail</label><input class="form-control" type="email" name="email" required></div>' +
            '<div class="col-md-6"><label class="form-label small">Phone</label><input class="form-control" name="phone"></div></form>',
        [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Create', onClick: async (el) => {
            const f = el.querySelector('#tf');
            if (!f.reportValidity()) return false;
            const a = await P.post('/api/auth/admin/tpo', P.formData(f));
            setTimeout(() => created(a), 400);
            load();
        } }]));
        $('#new-admin').addEventListener('click', () => P.modal('New administrator', '<form id="af" class="row g-2"><div class="col-md-6"><label class="form-label small">Username</label><input class="form-control" name="username" required></div>' +
            '<div class="col-md-6"><label class="form-label small">Name</label><input class="form-control" name="name" required></div><div class="col-12"><label class="form-label small">E-mail</label><input class="form-control" type="email" name="email" required></div></form>',
        [{ label: 'Cancel', cls: 'btn-light' }, { label: 'Create', onClick: async (el) => {
            const f = el.querySelector('#af');
            if (!f.reportValidity()) return false;
            const a = await P.post('/api/auth/admin/admins', P.formData(f));
            setTimeout(() => created(a), 400);
            load();
        } }]));
    });

    P.page('admin-audit', async () => {
        P.tabs();
        let page = 0;
        const filters = () => P.formData($('#filters'));
        const load = async () => {
            const res = await P.get('/api/analytics/audit-logs' + P.qs(Object.assign(filters(), { page })));
            P.table('#list', [
                { label: 'Time', render: (a) => '<span class="small">' + P.fmtDateTime(a.occurredAt) + '</span>' },
                { label: 'Service', render: (a) => '<span class="small">' + esc(a.service) + '</span>' },
                { label: 'Actor', render: (a) => '<span class="mono small">' + esc((a.actorUserId || 'system').substring(0, 8)) + '</span> <span class="small text-muted">' + esc(a.actorRole || '') + '</span>' },
                { label: 'Action', render: (a) => P.title(a.action) },
                { label: 'Target', render: (a) => '<span class="small">' + esc(a.target || '') + '</span>' },
                { label: 'Result', render: (a) => (a.success ? '<span class="badge text-bg-success">OK</span>' : '<span class="badge text-bg-danger">Failed</span>') },
                { label: 'Details', render: (a) => '<span class="small text-muted">' + esc((a.details || '').substring(0, 120)) + '</span>' }
            ], P.content(res), 'No audit events for these filters');
            P.pager('#pager', res, (p) => { page = p; load(); });
        };
        let t;
        $('#filters').addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { page = 0; load(); }, 400); });
        $('#filters').addEventListener('submit', (ev) => ev.preventDefault());
        load();
        $('#export').addEventListener('click', () => P.download('/api/analytics/audit-logs/export' + P.qs(filters()), 'audit-log.csv'));

        let lp = 0;
        const logins = async () => {
            const res = await P.get('/api/auth/admin/login-history?page=' + lp);
            P.table('#logins', [
                { label: 'Time', render: (x) => P.fmtDateTime(x.time) },
                { label: 'Login', render: (x) => '<span class="mono">' + esc(x.loginId) + '</span> <span class="small text-muted">' + esc(x.portal) + '</span>' },
                { label: 'Result', render: (x) => (x.success ? '<span class="badge text-bg-success">Success</span>' : '<span class="badge text-bg-danger">' + esc(P.title(x.reason)) + '</span>') },
                { label: 'IP', render: (x) => '<span class="mono small">' + esc(x.ip) + '</span>' },
                { label: 'Device', render: (x) => '<span class="small text-muted">' + esc((x.userAgent || '').substring(0, 60)) + '</span>' }
            ], P.content(res), 'No logins recorded');
            P.pager('#login-pager', res, (p) => { lp = p; logins(); });
        };
        document.addEventListener('tab-shown', (ev) => { if (ev.detail === 'logins') logins(); });
    });
})();
