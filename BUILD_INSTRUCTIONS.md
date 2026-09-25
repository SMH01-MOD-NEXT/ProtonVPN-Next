# Build Instructions / Инструкции по сборке 🛠️

[English](#english) | [Русский](#русский)

---

<a name="english"></a>
## English

This document explains how to build **Proton VPN-Next** from source.

### Prerequisites
- **JDK:** JDK 17 is required.
- **Android SDK:** Latest platforms and build tools. The **`android-37.1` minor platform is mandatory** — the project builds with `compileSdk = 37` / `compileSdkMinor = 1`, because `tor-android 0.4.9.12` is published against 37.1. Install it with `sdkmanager "platforms;android-37.1" "build-tools;37.0.0"` (or `android sdk install "platforms/android-37.1"` with the new Android CLI). `targetSdk` stays on 37.0.
- **NDK:** Version `29.0.14206865`.

### Recommended IDE 🚀
It is **highly recommended** to use the latest **Android Studio Canary** version.
> **Reason:** This project uses alpha/beta versions of AGP and Kotlin Compose features.

> [!IMPORTANT]
> Depending on your specific Canary version, the project's Gradle version might be flagged as unsupported. Android Studio usually provides a **one-click update** option to align Gradle with your IDE. It is safe and recommended to accept this.

### Building with Android Studio
1. **Open the project** and wait for Gradle Sync.
2. **Select Build Variant:** (e.g., `stableNightlyStandardDebug`).
3. **Run:** Click the green Play button.

### Building via Terminal
- **Debug Standard:** `./gradlew assembleStableStandardDebug`
- **Privacy Release:** `./gradlew assembleStablePrivacyRelease`

---

<a name="русский"></a>
## Русский

Этот документ объясняет, как собрать **Proton VPN-Next** из исходного кода.

### Предварительные требования
- **JDK:** Требуется JDK 17.
- **Android SDK:** Последние версии платформ и инструментов сборки. **Обязательна минорная платформа `android-37.1`** — проект собирается с `compileSdk = 37` / `compileSdkMinor = 1`, так как `tor-android 0.4.9.12` опубликован под 37.1. Установка: `sdkmanager "platforms;android-37.1" "build-tools;37.0.0"` (или `android sdk install "platforms/android-37.1"` в новом Android CLI). `targetSdk` остаётся на 37.0.
- **NDK:** Версия `29.0.14206865`.

### Рекомендуемая IDE 🚀
Настоятельно рекомендуется использовать последнюю версию **Android Studio Canary**.
> **Причина:** В проекте используются альфа/бета версии AGP и функции Kotlin Compose.

> [!IMPORTANT]
> В зависимости от вашей версии Canary, версия Gradle в проекте может быть помечена как неподдерживаемая. Android Studio обычно предлагает **обновление в один клик**, чтобы привести Gradle в соответствие с вашей IDE. Рекомендуется принять это обновление.

### Сборка через Android Studio
1. **Откройте проект** и дождитесь синхронизации Gradle.
2. **Выберите Build Variant:** (например, `stableNightlyStandardDebug`).
3. **Запуск:** Нажмите зеленую кнопку Run.

### Сборка через Терминал
- **Debug Standard:** `./gradlew assembleStableStandardDebug`
- **Privacy Release:** `./gradlew assembleStablePrivacyRelease`
