(function () {
    function resolveFeedbackEndpoint() {
        const endpointFromPage = document.body?.dataset?.feedbackEndpoint;
        if (endpointFromPage && endpointFromPage.trim().length > 0) {
            return endpointFromPage;
        }
        return '/feedback';
    }

    function createWidget() {
        const wrapper = document.createElement('div');
        wrapper.className = 'feedback-widget feedback-widget-inline';
        wrapper.innerHTML = `
            <button class="feedback-toggle feedback-toggle-text" type="button" aria-label="Открыть форму обратной связи">Обратная связь</button>
            <div class="feedback-panel" hidden>
                <h3>Обратная связь</h3>
                <p class="feedback-hint">Поделитесь идеей или проблемой — сообщение придёт в Telegram-бот.</p>
                <form id="feedback-form" class="feedback-form">
                    <label>Имя
                        <input type="text" name="name" required maxlength="80" placeholder="Ваше имя">
                    </label>
                    <label>Email
                        <input type="email" name="email" required maxlength="120" placeholder="you@example.com">
                    </label>
                    <label>Сообщение
                        <textarea name="message" rows="4" required maxlength="1200" placeholder="Что улучшить?"></textarea>
                    </label>
                    <label>Скриншоты (можно несколько)
                        <input type="file" name="screenshots" accept="image/*" multiple>
                    </label>
                    <button type="submit" class="btn btn-primary">Отправить</button>
                    <div class="feedback-status" role="status" aria-live="polite"></div>
                </form>
            </div>
        `;

        const footerLinks = document.querySelector('.home-footer-links, .footer .container, .footer');
        if (footerLinks) {
            footerLinks.appendChild(wrapper);
        } else {
            document.body.appendChild(wrapper);
        }

        const toggle = wrapper.querySelector('.feedback-toggle');
        const panel = wrapper.querySelector('.feedback-panel');
        const form = wrapper.querySelector('#feedback-form');
        const status = wrapper.querySelector('.feedback-status');
        const feedbackEndpoint = resolveFeedbackEndpoint();

        toggle.addEventListener('click', () => {
            const isHidden = panel.hasAttribute('hidden');
            if (isHidden) {
                panel.removeAttribute('hidden');
                toggle.classList.add('active');
            } else {
                panel.setAttribute('hidden', '');
                toggle.classList.remove('active');
            }
        });

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            status.textContent = 'Отправляем...';

            const formData = new FormData(form);
            try {
                const response = await fetch(feedbackEndpoint, {
                    method: 'POST',
                    body: formData
                });
                const contentType = response.headers.get('content-type') || '';
                const payload = contentType.includes('application/json')
                    ? await response.json()
                    : {status: 'error', message: 'Сервер вернул неожиданный ответ.'};

                if (response.ok && payload.status === 'ok') {
                    status.textContent = payload.message;
                    form.reset();
                    return;
                }
                status.textContent = payload.message || 'Не удалось отправить фидбек.';
            } catch (e) {
                status.textContent = 'Ошибка сети. Попробуйте снова.';
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createWidget);
    } else {
        createWidget();
    }
})();
