# Proton VPN-Next 🛡️

[![Crowdin](https://badges.crowdin.net/protonvpn-next/localized.svg)](https://crowdin.com/project/protonvpn-next)

[English](#english) | [Русский](#русский)

---

## English

**Proton VPN-Next** is a modern, high-performance **unofficial** Android client for Proton VPN. Built with a focus on privacy, speed, and a sleek Material 3 interface, it leverages AmneziaWG to ensure stable connectivity even in restrictive environments.

### ⚠️ IMPORTANT DISCLAIMER
- **UNOFFICIAL CLIENT:** This application is **NOT** an official product of Proton AG and is in no way affiliated with or endorsed by Proton AG.
- **USE AT YOUR OWN RISK:** This software is provided "as is" without any warranties. The developer assumes **no responsibility** for your accounts, data, or any potential consequences (such as account restrictions) resulting from the use of this unofficial client.
- **SUPPORT PROTON:** If you value privacy and enjoy Proton's services, we highly recommend subscribing to an official **Proton VPN paid plan**. Supporting the original creators ensures the continued development of the secure infrastructure we all rely on.

### 🚨 About Fakes & Counterfeit APKs
> **Warning:** Counterfeit versions of this app have been found in the wild. These fake APKs are **dangerous** — they may contain malware, steal your credentials, or silently redirect update checks to attacker-controlled servers.

**The ONLY official sources for Proton VPN-Next are:**
- ✅ This GitHub repository: [SMH01-MIRRORS/ProtonVPN-Next-MIRROR](https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR)
- ✅ Official releases published on this repo's [Releases page](https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR/releases)

**Known fake/counterfeit distribution channels (DO NOT use):**
- ❌ Telegram channel `t.me/Getmodpcs` — distributes a tampered APK that modifies the update server to point to their Telegram channel. This fake has been caught sending non-JSON responses to the app and may contain malware.

**How to verify you have the real app:**
1. Check the package name must be `ru.protonmod.next` (NOT `ch.protonvpn.android` or any other name).
2. Verify the APK signature matches the one published in this repository.
3. Only install from the official GitHub Releases page linked above.
4. **Never install APKs shared via Telegram, random websites, or third-party app stores.**

If you encounter a fake version, please open an issue on this repository so we can track and warn other users.

### 📱 System Requirements
- **Operating System:** Android 10+ (API level 29 or higher)
- **Device Architecture:** **64-bit only** (arm64-v8a or x86_64)
  - ⚠️ **32-bit devices are NOT supported.** The VPN engine (AmneziaWG) is compiled exclusively for 64-bit architectures. Attempting to install on 32-bit devices will result in a native library error.
  - Most modern Android devices are 64-bit. Check your device's CPU architecture to verify compatibility.

### 🛠 Build Instructions

#### Using Android Studio (Recommended)
1. **Open Android Studio** (Android Studio Panda 4 | 2025.3.4 Patch 1 or newer recommended).
2. Select **Open** and navigate to the project root directory.
3. Wait for the **Gradle Sync** to complete.
4. Ensure you have **JDK 17** configured in `Settings > Build, Execution, Deployment > Build Tools > Gradle`.
5. Connect your device or start an emulator.
6. Click the **Run** button (green play icon).

#### Using Terminal
Ensure you have the Android SDK and JDK 17 installed.
1. Navigate to the project root.
2. Build the Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. The generated APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

### ✨ Key Features
- **🚀 AmneziaWG Core:** Advanced protocol integration to bypass censorship and maintain high speeds.
- **🎨 Material 3 & Compose:** Fully modern UI built with Jetpack Compose and dynamic color support.
- **🔐 Privacy Suite:**
    - **Kill Switch:** System-level and internal protection.
    - **Split Tunneling:** Exclude specific apps or IP addresses from the VPN tunnel.
- **🌍 Global Network:** Easy server selection with load indicators.
- **📱 Multi-language:** Support for English, Russian, Ukrainian, Belarusian, Farsi, and Chinese.

### 🛠 Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt (Dagger 2)
- **Persistence:** Room Database
- **VPN Engine:** AmneziaWG (via Maven dependency)
### 💎 Infrastructure & Sponsors
- **[1Password](https://1password.com/):** Password management and security (Teams subscription provided for open source development).
- **[Sentry](https://sentry.io/):** Error monitoring and performance tracking (Business Plan provided for the project).
- **[Cloudflare](https://www.cloudflare.com/):** High-performance infrastructure using **Cloudflare Pages** (hosting) and **R2 Storage** (asset storage).
- **[DigitalPlat FreeDomain](https://freedomain.digiplat.org/):**
  This project uses DigitalPlat FreeDomain, an open domain infrastructure maintained by Edward Hsing. Built on DigitalPlat FreeDomain for domain provisioning, with credit to the platform and its maintainer.

---

## Русский

**Proton VPN-Next** — это современный высокопроизводительный **неофициальный** Android-клиент для Proton VPN. Разработан с упором на приватность, скорость и современный интерфейс Material 3. Использует технологию AmneziaWG для обеспечения стабильного соединения даже в условиях жесткой цензуры.

### ⚠️ ВАЖНЫЙ ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ
- **НЕОФИЦИАЛЬНЫЙ КЛИЕНТ:** Данное приложение **НЕ ЯВЛЯЕТСЯ** официальным продуктом Proton AG и никак не связано с официальной командой разработчиков Proton.
- **ИСПОЛЬЗУЙТЕ НА СВОЙ СТРАХ И РИСК:** Программное обеспечение предоставляется по принципу «как есть». Разработчик **не несет ответственности** за ваши аккаунты, сохранность данных или любые возможные последствия (включая блокировки аккаунтов), возникшие в результате использования этого клиента.
- **ПОДДЕРЖИТЕ PROTON:** Если вы цените приватность и вам нравятся продукты Proton, мы настоятельно рекомендуем **оформить платную подписку** на официальном сайте. Поддержка оригинальных создателей гарантирует развитие защищенной инфраструктуры, которой мы все пользуемся.

### 🚨 О фейках и поддельных APK
> **Внимание:** В сети обнаружены поддельные версии этого приложения. Такие APK **опасны** — они могут содержать вредоносное ПО, красть ваши данные или перенаправлять проверки обновлений на серверы злоумышленников.

**ЕДИНСТВЕННЫЕ официальные источники Proton VPN-Next:**
- ✅ Этот репозиторий GitHub: [SMH01-MIRRORS/ProtonVPN-Next-MIRROR](https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR)
- ✅ Официальные релизы на [странице Releases](https://github.com/SMH01-MIRRORS/ProtonVPN-Next-MIRROR/releases) этого репозитория

**Известные поддельные каналы распространения (НЕ использовать):**
- ❌ Telegram-канал `t.me/Getmodpcs` — распространяет модифицированный APK, который изменяет сервер обновлений на свой Telegram-канал. Этот фейк был замечен в отправке некорректных ответов и может содержать вредоносный код.

**Как убедиться, что у вас настоящее приложение:**
1. Имя пакета должно быть `ru.protonmod.next` (НЕ `ch.protonvpn.android` и никакое другое).
2. Проверьте подпись APK — она должна совпадать с опубликованной в этом репозитории.
3. Устанавливайте приложение только со страницы официальных релизов GitHub.
4. **Никогда не устанавливайте APK из Telegram, сторонних сайтов или магазинов приложений.**

Если вы столкнулись с поддельной версией, пожалуйста, создайте issue в этом репозитории.

### 📱 Системные требования
- **Операционная система:** Android 10+ (API 29 и выше)
- **Архитектура устройства:** **Только 64-бит** (arm64-v8a или x86_64)
  - ⚠️ **32-бит устройства НЕ поддерживаются.** VPN-движок (AmneziaWG) скомпилирован исключительно под 64-битные архитектуры. Попытка установки на 32-бит устройства приведет к ошибке загрузки нативной библиотеки.
  - Большинство современных Android-устройств - это 64-бит. Проверьте архитектуру процессора вашего устройства для подтверждения совместимости.

### 🛠 Инструкции по сборке

#### Через Android Studio (Рекомендуется)
1. **Откройте Android Studio** (рекомендуется версия Android Studio Panda 4 | 2025.3.4 Patch 1 или новее).
2. Выберите **Open** и укажите путь к корневой папке проекта.
3. Дождитесь завершения синхронизации **Gradle**.
4. Убедитесь, что в настройках (`Settings > Build, Execution, Deployment > Build Tools > Gradle`) выбран **JDK 17**.
5. Подключите устройство или запустите эмулятор.
6. Нажмите кнопку **Run** (зеленый треугольник).

#### Через терминал
Убедитесь, что установлены Android SDK и JDK 17.
1. Перейдите в корневую папку проекта.
2. Соберите Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Готовый APK файл будет находиться по пути:
   `app/build/outputs/apk/debug/app-debug.apk`

### ✨ Ключевые особенности
- **🚀 Ядро AmneziaWG:** Продвинутый протокол для обхода блокировок и высокой скорости работы.
- **🎨 Material 3 & Compose:** Полностью современный интерфейс на Jetpack Compose с поддержкой динамических цветов.
- **🔐 Инструменты приватности:**
    - **Kill Switch:** Системная и внутренняя защита при обрыве соединения.
    - **Раздельное туннелирование:** Возможность исключать приложения или конкретные IP-адреса из VPN.
- **🌍 Глобальная сеть:** Удобный выбор стран и серверов с индикацией нагрузки.
- **📱 Мультиязычность:** Поддержка русского, английского, украинского, белорусского, фарси и китайского языков.

### 🛠 Технологический стек
- **Язык:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Архитектура:** MVVM + Clean Architecture
- **DI:** Hilt (Dagger 2)
- **База данных:** Room
- **VPN Engine:** AmneziaWG (через Maven-зависимость)

### 💎 Инфраструктура и Спонсоры
- **[1Password](https://1password.com/):** Менеджер паролей и безопасность (предоставлена подписка Teams для разработки Open Source проектов).
- **[Sentry](https://sentry.io/):** Мониторинг ошибок и отслеживание производительности (предоставлен Business Plan для проекта).
- **[Cloudflare](https://www.cloudflare.com/):** Высокопроизводительная инфраструктура с использованием **Cloudflare Pages** (хостинг) и **R2 Storage** (хранилище объектов).
- **[DigitalPlat FreeDomain](https://freedomain.digiplat.org/):**
  This project uses DigitalPlat FreeDomain, an open domain infrastructure maintained by Edward Hsing. Built on DigitalPlat FreeDomain for domain provisioning, with credit to the platform and its maintainer.

---

## License / Лицензия
This project is licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for details.
