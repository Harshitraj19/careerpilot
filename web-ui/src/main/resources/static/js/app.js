/*
 * CareerPilot core front-end library.
 * - P.api(...)   : calls the API through the /bff proxy (session cookie + CSRF header)
 * - UI helpers   : toasts, modal dialogs, tables, badges, formatting, charts, markdown
 * - live updates : notification badge + STOMP WebSocket (one-time ticket)
 * - page registry: each page registers an init function by its data-page id
 */
(function () {
    'use strict';

    const P = (window.Placify = window.Placify || {});
    P.pages = {};

    /** Register the initialiser of a page (body[data-page]). */
    P.page = function (id, fn) {
        P.pages[id] = fn;
    };

    // =================================================================== API

    function csrfToken() {
        const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return m ? decodeURIComponent(m[1]) : null;
    }

    class ApiError extends Error {
        constructor(status, code, message, fields) {
            super(message || 'Request failed');
            this.status = status;
            this.code = code;
            this.fields = fields || {};
        }
    }
    P.ApiError = ApiError;

    /**
     * Calls the API. path starts with /api/... ; body may be an object (sent as JSON) or FormData.
     * Resolves with parsed JSON (or text / null), rejects with ApiError.
     */
    P.api = async function (method, path, body, opts) {
        opts = opts || {};
        const headers = { Accept: 'application/json' };
        const token = csrfToken();
        if (token) headers['X-XSRF-TOKEN'] = token;
        let payload;
        if (body instanceof FormData) {
            payload = body;
        } else if (body !== undefined && body !== null) {
            headers['Content-Type'] = 'application/json';
            payload = JSON.stringify(body);
        }
        let res;
        try {
            res = await fetch('/bff' + path, { method, headers, body: payload, credentials: 'same-origin' });
        } catch (e) {
            throw new ApiError(0, 'NETWORK', 'Network error - check your connection');
        }
        if (res.status === 401) {
            if (!opts.noRedirect) {
                window.location.href = '/login?expired';
            }
            throw new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired');
        }
        if (opts.raw) {
            if (!res.ok) throw await toError(res);
            return res;
        }
        if (!res.ok) throw await toError(res);
        if (res.status === 204) return null;
        const type = res.headers.get('Content-Type') || '';
        if (type.includes('json')) {
            const text = await res.text();
            return text ? JSON.parse(text) : null;
        }
        return res.text();
    };

    async function toError(res) {
        let data = {};
        try {
            data = await res.json();
        } catch (e) { /* not JSON */ }
        let msg = data.message || (res.status === 403 ? 'You are not allowed to do this' : 'Request failed (' + res.status + ')');
        if (res.status === 429) msg = 'Too many requests - please slow down and try again.';
        return new ApiError(res.status, data.code, msg, data.fieldErrors);
    }

    P.get = (path, opts) => P.api('GET', path, null, opts);
    P.post = (path, body, opts) => P.api('POST', path, body === undefined ? {} : body, opts);
    P.put = (path, body, opts) => P.api('PUT', path, body, opts);
    P.del = (path, opts) => P.api('DELETE', path, null, opts);

    /** Builds a query string from an object, skipping empty values. */
    P.qs = function (params) {
        const q = new URLSearchParams();
        Object.entries(params || {}).forEach(([k, v]) => {
            if (v !== undefined && v !== null && v !== '') q.append(k, v);
        });
        const s = q.toString();
        return s ? '?' + s : '';
    };

    /** Downloads a file from the API (PDF, Excel, CSV, resume...). */
    P.download = async function (path, fallbackName) {
        try {
            const res = await P.api('GET', path, null, { raw: true });
            const blob = await res.blob();
            let name = fallbackName || 'download';
            const cd = res.headers.get('Content-Disposition');
            const m = cd && cd.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
            if (m) name = decodeURIComponent(m[1]);
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = name;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 2000);
        } catch (e) {
            P.toast(e.message, 'danger');
        }
    };

    /** Opens a file (PDF / image) from the API in a new tab. */
    P.openFile = async function (path) {
        const win = window.open('', '_blank');
        try {
            const res = await P.api('GET', path, null, { raw: true });
            const blob = await res.blob();
            win.location.href = URL.createObjectURL(blob);
        } catch (e) {
            win.close();
            P.toast(e.message, 'danger');
        }
    };

    /** Image from the API as an object URL (for <img src>). */
    P.imageUrl = async function (path) {
        try {
            const res = await P.api('GET', path, null, { raw: true, noRedirect: true });
            return URL.createObjectURL(await res.blob());
        } catch (e) {
            return null;
        }
    };

    // =================================================================== formatting

    P.esc = function (v) {
        if (v === null || v === undefined) return '';
        return String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    };

    const parseDate = (v) => (v ? new Date(v) : null);

    P.fmtDate = function (v) {
        const d = parseDate(v);
        return d ? d.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
    };

    P.fmtDateTime = function (v) {
        const d = parseDate(v);
        return d ? d.toLocaleString(undefined, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';
    };

    P.fromNow = function (v) {
        const d = parseDate(v);
        if (!d) return '-';
        const s = Math.round((Date.now() - d.getTime()) / 1000);
        const abs = Math.abs(s);
        const unit = abs < 60 ? [abs, 'sec'] : abs < 3600 ? [Math.round(abs / 60), 'min'] : abs < 86400 ? [Math.round(abs / 3600), 'hr'] : [Math.round(abs / 86400), 'day'];
        const label = unit[0] + ' ' + unit[1] + (unit[0] === 1 ? '' : 's');
        return s >= 0 ? label + ' ago' : 'in ' + label;
    };

    /** Value for <input type="datetime-local"> from an ISO date-time. */
    P.toLocalInput = function (v) {
        if (!v) return '';
        return String(v).substring(0, 16);
    };

    P.money = function (lpa) {
        return lpa === null || lpa === undefined ? '-' : Number(lpa).toLocaleString(undefined, { maximumFractionDigits: 2 }) + ' LPA';
    };

    /** Pay line for a job: the CTC, or the monthly stipend for internships that have no CTC. */
    P.pay = function (j) {
        if (j.ctcLpa !== null && j.ctcLpa !== undefined) return P.money(j.ctcLpa);
        return j.stipend ? '₹' + Number(j.stipend).toLocaleString('en-IN') + '/month' : 'Stipend not disclosed';
    };

    P.pct = (v) => (v === null || v === undefined ? '-' : Math.round(Number(v) * 10) / 10 + '%');

    P.title = (s) => (s ? String(s).toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) : '');
    /** "keywordMatch" -> "Keyword match" (labels for camelCase keys). */
    P.label = (s) => (s ? String(s).replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : '');
    const JOB_TYPES = { FULL_TIME: 'Full-time (FTE)', INTERNSHIP: 'Internship', INTERNSHIP_WITH_PPO: 'Internship + PPO' };
    P.jobType = (t) => JOB_TYPES[t] || P.title(t);

    const BADGE = {
        success: ['OPEN', 'SELECTED', 'CLEARED', 'ACCEPTED', 'VERIFIED', 'APPROVED', 'ACTIVE', 'accepted', 'PUBLISHED', 'COMPLETED', 'SUBMITTED', 'RUNNING', 'PLACED', 'EASY', 'SENT'],
        primary: ['APPLIED', 'IN_PROCESS', 'ISSUED', 'IN_PROGRESS', 'UPCOMING', 'SHORTLISTED', 'PENDING', 'MEDIUM', 'running', 'pending', 'DREAM'],
        warning: ['PENDING_APPROVAL', 'UNDER_REVIEW', 'DRAFT', 'AUTO_SUBMITTED', 'WAITLISTED', 'SUPER_DREAM', 'LOGGED', 'HARD'],
        danger: ['REJECTED', 'DECLINED', 'EXPIRED', 'BLOCKED', 'TERMINATED', 'ABSENT', 'WITHDRAWN', 'wrong_answer', 'runtime_error', 'compilation_error', 'time_limit_exceeded', 'memory_limit_exceeded', 'FAILED', 'HIGH'],
        secondary: ['CLOSED', 'ENDED', 'NORMAL', 'LOW']
    };
    P.badge = function (status, label) {
        if (status === null || status === undefined || status === '') return '';
        let cls = 'secondary';
        for (const [k, list] of Object.entries(BADGE)) {
            if (list.includes(status)) {
                cls = k;
                break;
            }
        }
        const text = label || P.title(status);
        return '<span class="badge text-bg-' + cls + (cls === 'warning' ? '' : '') + '">' + P.esc(text) + '</span>';
    };

    P.chips = function (list, cls) {
        return (list || []).map((s) => '<span class="chip ' + (cls || '') + '">' + P.esc(s) + '</span>').join('') || '<span class="text-muted">-</span>';
    };

    /** Markdown (from the AI) rendered safely. */
    P.md = function (text) {
        if (!text) return '';
        if (window.marked && window.DOMPurify) {
            return DOMPurify.sanitize(marked.parse(String(text)));
        }
        return '<div class="pre-wrap">' + P.esc(text) + '</div>';
    };

    // =================================================================== UI helpers

    P.$ = (sel, root) => (root || document).querySelector(sel);
    P.$$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

    P.toast = function (message, type) {
        const box = document.getElementById('toasts');
        if (!box) {
            alert(message);
            return;
        }
        const el = document.createElement('div');
        el.className = 'toast align-items-center text-bg-' + (type || 'success') + ' border-0';
        el.setAttribute('role', 'alert');
        el.innerHTML = '<div class="d-flex"><div class="toast-body">' + P.esc(message) + '</div>' +
            '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
        box.appendChild(el);
        const t = new bootstrap.Toast(el, { delay: type === 'danger' ? 7000 : 4000 });
        el.addEventListener('hidden.bs.toast', () => el.remove());
        t.show();
    };

    /** Shows an error from a failed call. */
    P.fail = function (e) {
        console.error(e);
        let msg = e && e.message ? e.message : 'Something went wrong';
        if (e && e.fields && Object.keys(e.fields).length) {
            msg += ' - ' + Object.entries(e.fields).map(([k, v]) => k + ': ' + v).join(', ');
        }
        P.toast(msg, 'danger');
    };

    /**
     * Generic modal. buttons: [{label, cls, onClick(modalEl) -> (false to keep open | Promise)}]
     * Returns {el, hide}.
     */
    P.modal = function (title, bodyHtml, buttons, opts) {
        opts = opts || {};
        const el = document.getElementById('app-modal');
        el.querySelector('.modal-dialog').className = 'modal-dialog modal-dialog-centered modal-dialog-scrollable ' + (opts.size ? 'modal-' + opts.size : '');
        el.querySelector('.modal-title').textContent = title;
        el.querySelector('.modal-body').innerHTML = bodyHtml;
        const footer = el.querySelector('.modal-footer');
        footer.innerHTML = '';
        const modal = bootstrap.Modal.getOrCreateInstance(el);
        (buttons || [{ label: 'Close', cls: 'btn-secondary' }]).forEach((b) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn ' + (b.cls || 'btn-primary');
            btn.textContent = b.label;
            btn.addEventListener('click', async () => {
                if (!b.onClick) {
                    modal.hide();
                    return;
                }
                btn.disabled = true;
                try {
                    const r = await b.onClick(el);
                    if (r !== false) modal.hide();
                } catch (e) {
                    P.fail(e);
                } finally {
                    btn.disabled = false;
                }
            });
            footer.appendChild(btn);
        });
        modal.show();
        return { el, hide: () => modal.hide() };
    };

    P.confirm = function (title, text, okLabel, okCls) {
        return new Promise((resolve) => {
            let done = false;
            const m = P.modal(title, '<p class="mb-0">' + P.esc(text) + '</p>', [
                { label: 'Cancel', cls: 'btn-light' },
                { label: okLabel || 'Confirm', cls: okCls || 'btn-primary', onClick: () => { done = true; resolve(true); } }
            ]);
            m.el.addEventListener('hidden.bs.modal', () => { if (!done) resolve(false); }, { once: true });
        });
    };

    /** Asks for a line of text. Resolves with the text or null. */
    P.prompt = function (title, label, placeholder, required) {
        return new Promise((resolve) => {
            let done = false;
            const m = P.modal(title,
                '<label class="form-label">' + P.esc(label) + '</label><textarea class="form-control" rows="3" id="prompt-input" placeholder="' + P.esc(placeholder || '') + '"></textarea>',
                [{ label: 'Cancel', cls: 'btn-light' },
                    { label: 'OK', onClick: (el) => {
                        const v = el.querySelector('#prompt-input').value.trim();
                        if (required && !v) { P.toast('Please enter a value', 'warning'); return false; }
                        done = true;
                        resolve(v);
                    } }]);
            m.el.addEventListener('hidden.bs.modal', () => { if (!done) resolve(null); }, { once: true });
        });
    };

    /** Reads a form into an object; numbers for type=number, booleans for checkboxes, arrays for data-list (comma separated). */
    P.formData = function (form) {
        const out = {};
        P.$$('input, select, textarea', form).forEach((el) => {
            if (!el.name || el.disabled) return;
            if (el.type === 'checkbox') {
                out[el.name] = el.checked;
            } else if (el.type === 'radio') {
                if (el.checked) out[el.name] = el.value;
            } else if (el.type === 'number') {
                out[el.name] = el.value === '' ? null : Number(el.value);
            } else if (el.dataset.list !== undefined) {
                out[el.name] = el.value.split(',').map((s) => s.trim()).filter(Boolean);
            } else if (el.type === 'file') {
                // handled separately
            } else {
                out[el.name] = el.value === '' ? null : el.value;
            }
        });
        return out;
    };

    /** Fills a form from an object. */
    P.fillForm = function (form, data) {
        P.$$('input, select, textarea', form).forEach((el) => {
            if (!el.name || !(el.name in data)) return;
            const v = data[el.name];
            if (el.type === 'checkbox') el.checked = !!v;
            else if (el.type === 'datetime-local') el.value = P.toLocalInput(v);
            else if (Array.isArray(v)) el.value = v.join(', ');
            else el.value = v === null || v === undefined ? '' : v;
        });
    };

    P.empty = (msg, icon) => '<div class="empty"><i class="bi bi-' + (icon || 'inbox') + '"></i>' + P.esc(msg) + '</div>';
    P.spinner = () => '<div class="text-center py-4"><div class="spinner-border text-primary" role="status"></div></div>';

    /**
     * Renders a table. columns: [{label, render(row) -> html, cls}]
     */
    P.table = function (el, columns, rows, emptyMsg) {
        if (typeof el === 'string') el = P.$(el);
        if (!rows || !rows.length) {
            el.innerHTML = P.empty(emptyMsg || 'Nothing here yet');
            return;
        }
        el.innerHTML = '<div class="table-responsive"><table class="table table-hover align-middle mb-0"><thead><tr>' +
            columns.map((c) => '<th class="' + (c.cls || '') + '">' + P.esc(c.label) + '</th>').join('') +
            '</tr></thead><tbody>' +
            rows.map((r, i) => '<tr>' + columns.map((c) => '<td class="' + (c.cls || '') + '">' + c.render(r, i) + '</td>').join('') + '</tr>').join('') +
            '</tbody></table></div>';
    };

    /** Simple pager for Spring pages ({number, totalPages}). */
    P.pager = function (el, page, onGo) {
        if (typeof el === 'string') el = P.$(el);
        const p = page.page || page;
        const number = p.number !== undefined ? p.number : 0;
        const total = p.totalPages !== undefined ? p.totalPages : 1;
        if (total <= 1) {
            el.innerHTML = '';
            return;
        }
        el.innerHTML = '<nav><ul class="pagination pagination-sm justify-content-end mb-0">' +
            '<li class="page-item ' + (number === 0 ? 'disabled' : '') + '"><a class="page-link" href="#" data-p="' + (number - 1) + '">&laquo;</a></li>' +
            '<li class="page-item disabled"><span class="page-link">Page ' + (number + 1) + ' of ' + total + '</span></li>' +
            '<li class="page-item ' + (number >= total - 1 ? 'disabled' : '') + '"><a class="page-link" href="#" data-p="' + (number + 1) + '">&raquo;</a></li>' +
            '</ul></nav>';
        P.$$('a[data-p]', el).forEach((a) => a.addEventListener('click', (ev) => {
            ev.preventDefault();
            onGo(Number(a.dataset.p));
        }));
    };

    /** Content of a Spring Data page response. */
    P.content = (page) => (page && page.content) || [];

    const charts = {};
    /** Creates (or replaces) a Chart.js chart on a canvas id. */
    P.chart = function (canvasId, config) {
        if (!window.Chart) return null;
        if (charts[canvasId]) charts[canvasId].destroy();
        const ctx = document.getElementById(canvasId);
        if (!ctx) return null;
        config.options = Object.assign({ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: config.type === 'doughnut' || config.type === 'pie' } } }, config.options || {});
        charts[canvasId] = new Chart(ctx, config);
        return charts[canvasId];
    };
    P.colors = ['#2a9d8f', '#264653', '#e9c46a', '#f4a261', '#e76f51', '#6a994e', '#8d99ae', '#b07d2b', '#457b9d', '#9c6644'];

    /** Score ring (0-100). */
    P.ring = function (value, label, color) {
        const v = Math.max(0, Math.min(100, Number(value) || 0));
        return '<div class="score-ring" style="--v:' + v + ';--ring-color:' + (color || ringColor(v)) + '"><div><span class="num">' + Math.round(v) +
            '</span><span class="small text-muted">' + P.esc(label || '') + '</span></div></div>';
    };
    function ringColor(v) {
        return v >= 75 ? '#2a9d8f' : v >= 50 ? '#3d7f9c' : v >= 35 ? '#e9a03b' : '#d1495b';
    }
    P.ringColor = ringColor;

    P.progress = function (value, max, cls) {
        const pct = max ? Math.max(0, Math.min(100, (Number(value) / max) * 100)) : 0;
        return '<div class="progress"><div class="progress-bar ' + (cls || '') + '" style="width:' + pct + '%"></div></div>';
    };

    /** Disables a button while a promise runs. */
    P.busy = async function (btn, fn) {
        const old = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>' + old;
        try {
            return await fn();
        } finally {
            btn.disabled = false;
            btn.innerHTML = old;
        }
    };

    /** Delegated click handler: P.on(container, 'button[data-act]', (el, ev) => ...) */
    P.on = function (root, selector, handler, type) {
        if (typeof root === 'string') root = P.$(root);
        if (!root) return;
        root.addEventListener(type || 'click', (ev) => {
            const el = ev.target.closest(selector);
            if (el && root.contains(el)) handler(el, ev);
        });
    };

    P.param = (name) => new URLSearchParams(window.location.search).get(name);
    P.role = () => document.body.dataset.role;
    P.isStaff = () => ['TPO', 'ADMIN'].includes(P.role());
    /** '/company' or '/tpo' depending on who is logged in (for shared pages). */
    P.base = () => (P.role() === 'COMPANY' ? '/company' : P.role() === 'STUDENT' ? '/student' : '/tpo');

    // =================================================================== live updates

    const ws = { client: null, subs: [], connected: false };

    /** Subscribes to a STOMP destination (connects on first use). */
    P.subscribe = function (destination, callback) {
        ws.subs.push({ destination, callback, handle: null });
        connect();
        if (ws.connected) attach(ws.subs[ws.subs.length - 1]);
    };

    function attach(s) {
        s.handle = ws.client.subscribe(s.destination, (msg) => {
            try {
                s.callback(JSON.parse(msg.body));
            } catch (e) {
                console.error(e);
            }
        });
    }

    function connect() {
        if (ws.client || !window.StompJs) return;
        const url = document.querySelector('meta[name="ws-url"]');
        if (!url || !url.content) return;
        ws.client = new StompJs.Client({
            reconnectDelay: 5000,
            heartbeatIncoming: 20000,
            heartbeatOutgoing: 20000,
            beforeConnect: async () => {
                // a fresh one-time ticket for every (re)connection
                try {
                    const t = await P.post('/api/notifications/ws-ticket', {}, { noRedirect: true });
                    ws.client.brokerURL = url.content + '?ticket=' + encodeURIComponent(t.ticket);
                } catch (e) {
                    ws.client.brokerURL = url.content;
                }
            },
            onConnect: () => {
                ws.connected = true;
                ws.subs.forEach(attach);
            },
            onWebSocketClose: () => {
                ws.connected = false;
            }
        });
        ws.client.activate();
    }

    async function refreshBadge() {
        const badge = document.getElementById('notif-badge');
        if (!badge) return;
        try {
            const r = await P.get('/api/notifications/unread-count', { noRedirect: true });
            setBadge(r.unread);
        } catch (e) { /* ignore */ }
    }

    function setBadge(n) {
        const badge = document.getElementById('notif-badge');
        if (!badge) return;
        badge.textContent = n > 99 ? '99+' : n;
        badge.classList.toggle('d-none', !n);
        badge.dataset.count = n;
    }

    /** Pending-query count next to the help desk link in the sidebar. */
    async function refreshQueryBadge() {
        const link = P.$('.sidebar a[href="/student/queries"], .sidebar a[href="/tpo/queries"]');
        if (!link) return;
        try {
            const s = await P.get('/api/notifications/queries/stats', { noRedirect: true });
            let b = link.querySelector('.query-badge');
            if (!b) {
                b = document.createElement('span');
                b.className = 'badge rounded-pill text-bg-danger ms-auto query-badge';
                link.appendChild(b);
            }
            b.textContent = s.pending;
            b.classList.toggle('d-none', !s.pending);
        } catch (e) { /* ignore */ }
    }

    function startNotifications() {
        if (!document.body.dataset.role) return;
        refreshBadge();
        refreshQueryBadge();
        setInterval(refreshBadge, 90000);
        P.subscribe('/user/queue/notifications', (n) => {
            setBadge((Number(document.getElementById('notif-badge').dataset.count) || 0) + 1);
            P.toast(n.title + (n.message ? ' - ' + n.message : ''), 'primary');
            if (n.type === 'QUERY') {
                refreshQueryBadge();
                if (P.onQueryNotification) P.onQueryNotification(n);
            }
        });
    }
    P.refreshBadge = refreshBadge;
    P.refreshQueryBadge = refreshQueryBadge;

    // =================================================================== boot

    document.addEventListener('DOMContentLoaded', () => {
        P.$$('[data-toggle-sidebar]').forEach((b) => b.addEventListener('click', () => P.$('.sidebar').classList.toggle('open')));
        startNotifications();
        const id = document.body.dataset.page;
        const fn = P.pages[id];
        if (fn) {
            Promise.resolve()
                .then(() => fn(document.body.dataset))
                .catch(P.fail);
        }
    });
})();
