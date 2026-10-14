/* Login / registration helpers: captcha refresh, show password, strength meter, company e-mail OTP. */
(function () {
    'use strict';
    const $ = (s) => document.querySelector(s);

    document.querySelectorAll('[data-toggle-password]').forEach((btn) => {
        btn.addEventListener('click', () => {
            const input = $(btn.dataset.togglePassword);
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.querySelector('i').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
        });
    });

    document.querySelectorAll('[data-strength]').forEach((input) => {
        const bar = $(input.dataset.strength);
        input.addEventListener('input', () => {
            const v = input.value;
            let score = 0;
            if (v.length >= 8) score++;
            if (v.length >= 12) score++;
            if (/[a-z]/.test(v) && /[A-Z]/.test(v)) score++;
            if (/\d/.test(v)) score++;
            if (/[^A-Za-z0-9]/.test(v)) score++;
            bar.style.width = (score * 20) + '%';
            bar.className = 'progress-bar ' + (score >= 5 ? 'bg-success' : score >= 3 ? 'bg-warning' : 'bg-danger');
        });
    });

    const refresh = $('#captcha-refresh');
    if (refresh) {
        refresh.addEventListener('click', async () => {
            const r = await fetch('/login/captcha', { credentials: 'same-origin' });
            const c = await r.json();
            if (c.enabled) {
                $('#captcha-img').src = c.image;
                $('#captchaId').value = c.id;
                $('#captchaAnswer').value = '';
                $('#captchaAnswer').focus();
            }
        });
    }

    const sendOtp = $('#send-otp');
    if (sendOtp) {
        sendOtp.addEventListener('click', async () => {
            const email = $('#email').value.trim();
            const status = $('#otp-status');
            if (!email) {
                status.textContent = 'Enter your e-mail first.';
                return;
            }
            sendOtp.disabled = true;
            try {
                const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
                // the student form also sends the registration number (data-otp-fields="regNo")
                const params = { email };
                (sendOtp.dataset.otpFields || '').split(',').filter(Boolean).forEach((id) => { params[id] = $('#' + id).value.trim(); });
                const r = await fetch(sendOtp.dataset.otpUrl || '/register/company/otp', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': m ? decodeURIComponent(m[1]) : '' },
                    body: new URLSearchParams(params)
                });
                const data = await r.json().catch(() => ({}));
                status.textContent = data.message || (r.ok ? 'Code sent.' : 'Could not send the code.');
                status.className = 'form-text ' + (r.ok ? 'text-success' : 'text-danger');
                if (r.ok) {
                    let left = 60;
                    const t = setInterval(() => {
                        sendOtp.textContent = 'Resend (' + left-- + 's)';
                        if (left < 0) {
                            clearInterval(t);
                            sendOtp.textContent = 'Resend code';
                            sendOtp.disabled = false;
                        }
                    }, 1000);
                    return;
                }
            } catch (e) {
                status.textContent = 'Network error.';
            }
            sendOtp.disabled = false;
        });
    }
})();
