# Privacy Policy and Disclaimer | Политика конфиденциальности и отказ от ответственности

*Last updated: October 1, 2026*

## English

### 1. Introduction
ProtonVPN-Next is an open-source, unofficial client for Proton VPN services. This document outlines how data is handled by the application. By using this application, you agree to the terms described herein.

### 2. No Affiliation
This project is **NOT** affiliated with, authorized, maintained, sponsored, or endorsed by Proton AG or any of its affiliates. This is an independent, community-driven project.

### 3. Data Collection and Handling

#### 3.1 Proton Services
The application interacts directly with Proton's official APIs to provide VPN services.
- **Authentication**: Your credentials (username/password) are processed using the Secure Remote Password (SRP) protocol. The application does not store your password in plain text.
- **VPN Traffic**: All your internet traffic is routed through Proton's servers. This application does not intercept, log, or monitor your VPN traffic. Please refer to [Proton's Privacy Policy](https://protonvpn.com/privacy-policy) for details on how they handle your data.

#### 3.2 Diagnostics and Analytics (Standard build)
The standard build retains **Sentry**. Crash reports and handled-error reports are enabled by default and can be disabled in Settings → Error Reporting. Usage analytics, logs, metrics, performance tracing and session replay are disabled by default and require separate opt-in. The privacy build does not initialize Sentry.
- Diagnostics may include device model, OS/app version and stack traces. HTTP request objects and user identity are removed from error events; sensitive strings and nested extras are scrubbed. Automatic screenshots and view hierarchies are not attached. Scrubbing is a safeguard, not a promise that every conceivable identifier is detectable.
- Any receiving service and its hosting/network infrastructure can see the source IP of the HTTPS connection, even when the IP is omitted from the report payload. With a working VPN this is normally the exit IP; exclusions and system routing can affect this.
- Optional session replay is a separate setting; enabling it sends a masked UI recording. Changing SDK collection options may require restarting the app.

#### 3.3 Local Storage
The application stores certain data locally on your device:
- Encrypted session tokens and cryptographic keys.
- Server list cache.
- User settings and preferences.
  Session secrets remain in app-private storage. Local settings or app context may be included in explicitly enabled AI features described below.

#### 3.4 API Block Bypass (Optional)
- **Project reverse proxies (Netlify, Cloudflare, Deno, temporary mirrors)** terminate TLS and can inspect Proton API request/response bodies: SRP exchanges, 2FA codes and session tokens. SRP does not send the plaintext password, but does not conceal the rest of the API exchange from a TLS-terminating proxy. These proxies carry API calls, not the VPN tunnel traffic.
- **Proton alternative routing and local ByeDPI** avoid project-operated TLS-terminating reverse proxies. New selections default to Proton mirrors; an explicitly saved strategy is preserved.
- A custom SOCKS5/HTTP CONNECT proxy normally sees source/destination metadata, not HTTPS bodies unless it also terminates TLS. Only use a proxy you trust.

#### 3.5 IP and Country Lookups
Settings → Privacy → IP address services lets you select Proton (direct HTTPS), independent MyIP/FreeIPAPI services, embedded project bypasses and each available entry from the cached event configuration (currently Vercel). Defaults are Proton, MyIP and FreeIPAPI; project-operated hosts require opt-in, including on existing installs without a saved selection. Event permissions apply to the individual endpoint, not every future host published under the same event name. Only selected services are used for both IP and country lookups; no unselected fallback is permitted. The selected service sees the request IP, but these lookups send no Proton credentials or VPN configuration and do not deliberately bind to a physical network to bypass a VPN. Disabling all lookups also clears the remembered real IP/country. Requests already sent before a setting change cannot be recalled. This switch does not disable API calls, updates, connection verification, event configuration fetching or Sentry.

#### 3.6 AI Assistant and Other Network Features
The AI assistant is off by default. When enabled and invoked, it sends your prompt, settings context and installed-app list to your configured AI provider; this is separate from Sentry. Updates and temporary-mirror configuration also contact project infrastructure, which can see the connection source IP. The app does not establish whether hosting providers retain access logs. Android cloud backup and device transfer of app-private data are excluded; user-triggered exports are separate and must be stored securely.

### 4. Mandatory Acceptance
By using this application, you acknowledge that you have read and accepted this Privacy Policy. Access to the application is conditional upon this acceptance.

### 5. Disclaimer of Warranty
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

---

## Русский

### 1. Введение
ProtonVPN-Next — это неофициальный клиент с открытым исходным кодом для сервисов Proton VPN. В этом документе описывается, как приложение обрабатывает данные. Используя это приложение, вы соглашаетесь с условиями, описанными здесь.

### 2. Отсутствие аффилированности
Этот проект **НЕ** связан, не авторизован, не поддерживается и не одобряется компанией Proton AG или её филиалами. Это независимый проект сообщества.

### 3. Сбор и обработка данных

#### 3.1 Сервисы Proton
Приложение напрямую взаимодействует с официальными API Proton для предоставления услуг VPN.
- **Аутентификация**: Ваши учетные данные (имя пользователя/пароль) обрабатываются по протоколу Secure Remote Password (SRP). Приложение не хранит ваш пароль в открытом виде.
- **VPN-трафик**: Весь ваш интернет-трафик направляется через серверы Proton. Данное приложение не перехватывает, не регистрирует и не отслеживает ваш VPN-трафик. Пожалуйста, ознакомьтесь с [Политикой конфиденциальности Proton](https://protonvpn.com/privacy-policy) для получения подробной информации о том, как они обрабатывают ваши данные.

#### 3.2 Диагностика и аналитика (Standard)
**Sentry сохранён** в стандартной сборке. Отчёты о сбоях и обработанных ошибках включены по умолчанию; их можно отключить в «Настройки → Отчёты об ошибках». Аналитика использования, логи, метрики, трассировка производительности и запись сессии выключены по умолчанию и включаются отдельно. Сборка privacy не инициализирует Sentry.
- Диагностика может содержать модель устройства, версии ОС/приложения и стек вызовов. Объект HTTP-запроса и сведения о пользователе удаляются из отчётов; чувствительные строки и вложенные данные очищаются. Автоматические скриншоты и иерархия интерфейса не прикладываются. Очистка — защитная мера, а не гарантия распознавания любых идентификаторов.
- Получатель и хостинг видят исходный IP HTTPS-соединения, даже если IP удалён из тела отчёта. При работающем VPN это обычно выходной IP; исключения и системная маршрутизация могут влиять на результат.
- Запись сессии — отдельная опция, передающая маскированную запись интерфейса. Для изменения некоторых параметров SDK может потребоваться перезапуск приложения.

#### 3.3 Локальное хранилище
Приложение сохраняет определенные данные локально на вашем устройстве:
- Зашифрованные токены сессий и криптографические ключи.
- Кэш списка серверов.
- Пользовательские настройки и предпочтения.
  Секреты сессии хранятся в закрытом хранилище приложения. Контекст настроек может отправляться явно включённым AI-функциям, описанным ниже.

#### 3.4 Обход блокировок API (Опционально)
- **Обратные прокси проекта (Netlify, Cloudflare, Deno, временные зеркала)** завершают TLS и могут читать тело запросов/ответов API Proton: SRP-обмен, коды 2FA и токены сессии. SRP не передаёт открытый пароль, но не скрывает остальные данные API от прокси, завершающего TLS. Через эти прокси проходят API-запросы, а не VPN-трафик.
- **Альтернативная маршрутизация Proton и локальный ByeDPI** не используют обратные прокси проекта, завершающие TLS. По умолчанию выбираются зеркала Proton; явно сохранённая стратегия сохраняется.
- Пользовательский SOCKS5/HTTP CONNECT обычно видит метаданные соединения, но не тело HTTPS, если сам не завершает TLS. Используйте только доверенный прокси.

#### 3.5 Определение IP и страны
«Настройки → Приватность → Сервисы определения IP» позволяет выбрать Proton (прямой HTTPS), независимые MyIP/FreeIPAPI, встроенные обходы проекта и доступные записи кэша event (сейчас Vercel). По умолчанию разрешены Proton, MyIP и FreeIPAPI. Сервисы проекта включаются отдельно, в том числе на существующих установках без сохранённого выбора. Разрешение event относится к конкретному адресу, а не ко всем будущим хостам с тем же именем. Только выбранные сервисы участвуют в определении IP и страны: скрытого резервного обращения к отключённым сервисам нет. Сервис видит IP запроса; запросы не содержат учётные данные Proton или конфигурацию VPN и не привязываются принудительно к физической сети в обход VPN. Полное отключение удаляет сохранённый реальный IP и страну. Уже отправленный запрос нельзя отозвать. Настройка не отключает API, обновления, проверку соединения, загрузку временной конфигурации или Sentry.

#### 3.6 AI и другие сетевые функции
AI-ассистент выключен по умолчанию. При включении и вызове он передаёт запрос, контекст настроек и список установленных приложений выбранному AI-провайдеру; это не Sentry. Обновления и конфигурация временных зеркал также обращаются к инфраструктуре проекта, видящей исходный IP соединения. Приложение не устанавливает, сохраняются ли журналы доступа у хостинга. Данные приложения исключены из облачного бэкапа Android и переноса между устройствами; ручной экспорт — отдельная операция, результат которой нужно хранить безопасно.

### 4. Обязательное принятие
Используя это приложение, вы подтверждаете, что прочитали и приняли настоящую Политику конфиденциальности. Доступ к приложению возможен только при условии этого принятия.

### 5. Отказ от ответственности
ПРОГРАММНОЕ ОБЕСПЕЧЕНИЕ ПРЕДОСТАВЛЯЕТСЯ «КАК ЕСТЬ», БЕЗ КАКИХ-ЛИБО ГАРАНТИЙ, ЯВНЫХ ИЛИ ПОДРАЗУМЕВАЕМЫХ. НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ АВТОРЫ ИЛИ ПРАВООБЛАДАТЕЛИ НЕ НЕСУТ ОТВЕТСТВЕННОСТИ ПО КАКИМ-ЛИБО ИСКАМ, ЗА УЩЕРБ ИЛИ ПО ИНЫМ ОБЯЗАТЕЛЬСТВАМ, ВОЗНИКШИМ В РЕЗУЛЬТАТЕ ИСПОЛЬЗОВАНИЯ ПРОГРАММНОГО ОБЕСПЕЧЕНИЯ.
