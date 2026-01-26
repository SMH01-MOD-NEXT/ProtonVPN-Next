# ProtonMOD‑Next for Android
> ⚠️ **EDUCATIONAL USE ONLY** — This fork modifies client-side behavior and may unlock paid features. Not for commercial use or production.

**[English](#english) | [Русский](#russian)**

<a id="english"></a>
Based on [ProtonVPN Android](https://github.com/ProtonVPN/android-app)  
© 2026 SMH01 — Community modification under GPLv3
***
## Overview
ProtonMOD‑Next is an **actively developed experimental fork** of the official ProtonVPN Android client.  
It is aimed at users in **heavily restricted networks** and focuses on:
- Transparent **VLESS proxy (Xray) integration**
- **Disabling GuestHole** (pre‑login VPN tunnel)
- Keeping **TLS certificate pinning** fully intact
***
## Legal / Ethical Notice
This project is provided **for educational and research purposes only**.
ProtonMOD‑Next is an unofficial community fork of the Proton VPN Android client.  
It **modifies client‑side checks and may unlock paid or restricted features** that are normally available only with a valid Proton VPN subscription.
### Usage Terms
- Do **not** use this project for any kind of **commercial activity or profit**.
- Do **not** use this fork in **production environments**.
- Using this fork with your own Proton account is **entirely at your own risk**.
- You are solely responsible for ensuring that your usage complies with Proton VPN's Terms of Service and your local laws.
- The author is **not affiliated** with Proton AG and provides **no warranties or support**.
### If You Like Proton VPN
**Support the original project and purchase a legitimate plan.**  
Proton AG provides excellent privacy-focused services, and they deserve your support.
***
## Screenshots
<p align="center">
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/2.jpg" width="250" alt="Connection screen"/>
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/3.jpg" width="250" alt="Country Screen"/>
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/4.jpg" width="250" alt="Connection"/>
</p>

*⚠️ **Note**: Screenshots above are from the official ProtonVPN Android app. **ProtonMOD‑Next has a completely redesigned UI** with modern card-based layout, and a custom LiquidGlass floating navigation bar. The actual interface will look significantly different.*
***
## Features / Modifications
- **VLESS proxy integration (Xray)**  
  Proton API traffic (login, IP checks, account metadata) can be routed through a local VLESS proxy.  
  Implemented via a custom `ProxySelector` applied only to Proton API hosts.
- **GuestHole disabled**  
  The pre‑login GuestHole tunnel is suppressed to avoid failed or suspicious VPN attempts  
  in environments where Proton endpoints are blocked.
- **AMOLED‑optimized dark theme** ✅  
  True black colors + enhanced contrast for AMOLED displays.
- **Security preserved**  
  The original TLS certificate pinning is kept. Connections to Proton servers are still validated  
  against their official pinned certificates.

⚠️ **Note**: The UI of ProtonMOD‑Next has been significantly redesigned and **will look different** from the official ProtonVPN app. The interface uses modern card-based design patterns with Material 3 components.
***
## Build Instructions
Clone the repository and build with Gradle:
```bash
./gradlew assembleProductionVanillaOpenSourceDebug
```
### Android Studio
1. Open **Android Studio** (latest stable recommended).
2. Select **File → Open…** and choose the root folder of this repository.
3. Wait for Gradle sync to finish (first sync may take several minutes).
4. In the toolbar, select the build variant:
    - `productionVanillaOpenSourceDebug` — development / testing
    - `productionVanillaOpenSourceRelease` — release build
5. Press **Run ▶** to install on a connected device or emulator.
You can also use **Build → Build Bundle(s) / APK(s)** to generate APKs directly from the IDE.
***
## Roadmap
- [x] Integrate VLESS proxy into Proton API requests
- [x] Suppress GuestHole (pre‑login VPN tunnel)
- [x] Disable proxy when not required
- [x] Suppress auto‑connect on process restore
- [x] Add AMOLED‑optimized dark theme (true black + contrast tweaks)

*(Roadmap is intentionally small and focused; more items will be added as the project stabilizes.)*
***
## Contributions
Pull requests and issues to this fork's repository are **allowed and very welcome**.
Bug fixes, refactoring, documentation improvements, and clean feature implementations are especially appreciated.
***
## Development Status
🚧 **Active, experimental**
APIs and behavior may change between builds.  
If you depend on a specific behavior, **pin to a tag** and follow release notes / changelog.
***
## License
This project is a community modification of ProtonVPN for Android and is distributed under the **GPLv3**.  
See `LICENSE` for details.

---

<a id="russian"></a>

# ProtonMOD‑Next для Android
> ⚠️ **ТОЛЬКО ДЛЯ ОБРАЗОВАТЕЛЬНОГО ИСПОЛЬЗОВАНИЯ** — Этот форк изменяет поведение клиента и может разблокировать платные функции. Не для коммерческого использования или production.

Основано на [ProtonVPN Android](https://github.com/ProtonVPN/android-app)  
© 2026 SMH01 — Модификация сообщества под GPLv3

***

## Обзор
ProtonMOD‑Next — это **активно развиваемый экспериментальный форк** официального клиента ProtonVPN для Android.  
Предназначен для пользователей в **сильно ограниченных сетях** и сосредоточен на:
- Прозрачной интеграции **VLESS прокси (Xray)**
- **Отключении GuestHole** (VPN-туннель до входа)
- **Отключении автоподключения при восстановлении процесса**
- Сохранении **полной целостности закрепления TLS-сертификатов**

***

## Правовое уведомление / Этические стандарты
Этот проект предоставляется **исключительно в образовательных и исследовательских целях**.

ProtonMOD‑Next — неофициальный форк клиента Proton VPN для Android.  
Он **изменяет проверки на стороне клиента и может разблокировать платные или ограниченные функции**, которые обычно доступны только с действительной подпиской Proton VPN.

### Условия использования
- **Не используйте** этот проект для **коммерческой деятельности или извлечения прибыли**.
- **Не используйте** этот форк в **production-окружении**.
- Использование этого форка со своим аккаунтом Proton — **полностью на ваш риск**.
- Вы несете полную ответственность за обеспечение соответствия вашего использования Условиям обслуживания Proton VPN и местным законам.
- Автор **не аффилирован** с Proton AG и **не предоставляет гарантии или поддержку**.

### Если вам нравится Proton VPN
**Поддержите оригинальный проект и приобретите легитимный тарифный план.**  
Proton AG предоставляет отличные сервисы, ориентированные на конфиденциальность, и они достойны вашей поддержки.

***

## Скриншоты
<p align="center">
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/2.jpg" width="250" alt="Экран подключения"/>
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/3.jpg" width="250" alt="Выбор страны"/>
  <img src="https://raw.githubusercontent.com/ProtonVPN/android-app/master/metadata/en-US/images/phoneScreenshots/4.jpg" width="250" alt="Подключение"/>
</p>

*⚠️ **Примечание**: Скриншоты выше из официального приложения ProtonVPN для Android. **ProtonMOD‑Next имеет полностью переработанный интерфейс** с современным карточным макетом,и пользовательской плавающей навигационной панелью LiquidGlass. Фактический интерфейс будет выглядеть значительно иначе.*

***

## Функции / Модификации
- **Интеграция VLESS прокси (Xray)**  
  Трафик API Proton (вход, проверки IP, метаданные аккаунта) может быть направлен через локальный VLESS прокси.  
  Реализовано через пользовательский `ProxySelector`, применяемый только к хостам API Proton.

- **GuestHole отключен**  
  Туннель GuestHole перед входом подавляется, чтобы избежать ошибок или подозрительных попыток VPN  
  в окружениях, где конечные точки Proton заблокированы.

- **AMOLED-оптимизированная темная тема** ✅  
  Полностью черный цвет + улучшенный контраст для экранов AMOLED.

- **Безопасность сохранена**  
  Оригинальное закрепление TLS-сертификата сохранено. Подключения к серверам Proton по-прежнему проверяются  
  с использованием их официально закрепленных сертификатов.

⚠️ **Примечание**: Интерфейс ProtonMOD‑Next был значительно переработан и **будет выглядеть иначе** чем официальное приложение ProtonVPN. Интерфейс использует современные паттерны карточного дизайна с компонентами Material 3.

***

## Инструкции по сборке
Клонируйте репозиторий и соберите с помощью Gradle:

```bash
./gradlew assembleProductionVanillaOpenSourceDebug
```

### Android Studio
1. Откройте **Android Studio** (рекомендуется последняя стабильная версия).
2. Выберите **File → Open…** и выберите корневую папку этого репозитория.
3. Дождитесь завершения синхронизации Gradle (первая синхронизация может занять несколько минут).
4. В панели инструментов выберите вариант сборки:
    - `productionVanillaOpenSourceDebug` — разработка / тестирование
    - `productionVanillaOpenSourceRelease` — сборка релиза
5. Нажмите **Run ▶**, чтобы установить на подключенное устройство или эмулятор.

Вы также можете использовать **Build → Build Bundle(s) / APK(s)**, чтобы создать APK непосредственно из IDE.

***

## Дорожная карта
- [x] Интегрировать VLESS прокси в запросы API Proton
- [x] Подавить GuestHole (VPN-туннель перед входом)
- [x] Отключить прокси, когда он не требуется
- [x] Подавить автоподключение при восстановлении процесса
- [x] Добавить AMOLED-оптимизированную темную тему (истинный черный + улучшенный контраст)

*(Дорожная карта намеренно маленькая и сосредоточенная; больше пунктов будут добавлены по мере стабилизации проекта.)*

***

## Вклад
Pull-запросы и проблемы в репозиторий этого форка **разрешены и очень приветствуются**.  
Исправления ошибок, рефакторинг, улучшения документации и чистые реализации функций особенно ценятся.

***

## Статус разработки
🚧 **Активно, экспериментально**

API и поведение могут измениться между сборками.  
Если вы полагаетесь на определенное поведение, **привяжитесь к тегу** и следите за примечаниями к выпуску / журналом изменений.

***

## Лицензия
Этот проект является модификацией сообщества ProtonVPN для Android и распространяется под **GPLv3**.  
Подробности см. в `LICENSE`.

