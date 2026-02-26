# Proton VPN API Documentation (Unofficial & Deep Dive) 🛠️

[English](#english) | [Русский](#русский)

---

## English

This document provides a comprehensive technical guide to the Proton VPN API, reverse-engineered and implemented in this project. Since official documentation is unavailable, this serves as a primary reference for the networking layer.

### 1. Infrastructure & Base URLs

Proton uses several domains for its API. In case of censorship, the app can switch between them.

- **Primary:** `https://vpn-api.proton.me/`
- **Secondary:** `https://api.protonmail.ch/`
- **Alternative:** `https://api.protonvpn.ch/`

### 2. Networking Layer (`NetworkModule`)

Proton's backend is highly sensitive to headers. If they are missing or incorrect, the server returns `403 Forbidden` or `422 Unprocessable Entity`.

#### Headers Implementation
All requests must be intercepted to include these mandatory headers:

```kotlin
val headerInterceptor = Interceptor { chain ->
    val userAgent = "ProtonVPN/5.15.95.5 (Android XX; MODEL XXX-XXX)"
    val request = chain.request().newBuilder()
        .addHeader("User-Agent", userAgent)
        .addHeader("x-pm-appversion", "android-vpn@5.15.95.5-dev+play")
        .addHeader("x-pm-apiversion", "4")
        .addHeader("Accept", "application/vnd.protonmail.v1+json")
        .build()
    chain.proceed(request)
}
```

### 3. Authentication Flow (SRP Protocol)

Proton uses **Secure Remote Password (SRP)**. This allows authentication without ever sending the password to the server.

#### Step 1: Anonymous Session (`POST /auth/v4/sessions`)
Before logging in, a session must be created to receive a `UID` (Session ID).

#### Step 2: Get Auth Info (`POST /auth/v4/info`)
Retrieves the server's SRP parameters.
- **Request:** `{"Username": "user123"}`
- **Response Key Fields:**
    - `Modulus`: SRP big prime number (N).
    - `Salt`: User-specific salt (s).
    - `ServerEphemeral`: Server's public ephemeral value (B).
    - `SRPSession`: A temporary token for the SRP handshake.

#### Step 3: Perform Login (`POST /auth/v4`)
The client computes the `ClientProof` (M2) locally using the password, salt, and ephemeral values.
- **Request Body:**
```json
{
  "Username": "user123",
  "ClientEphemeral": "...", // Client public value (A)
  "ClientProof": "...",     // Computed proof (M1)
  "SRPSession": "..."       // From Step 2
}
```
- **Success Response:** Returns `AccessToken`, `RefreshToken`, and `UID`.

#### Step 4: 2FA (If applicable) (`POST /auth/v4/2fa`)
If the account has 2FA, the `AccessToken` from Step 3 will have limited scopes.
- **Request:** `{"TwoFactorCode": "123456"}`

### 4. VPN & Tunnel Management

#### A. Fetching Logical Servers (`GET /vpn/v2/logicals`)
Returns the hierarchy of locations.
- **LogicalServer:** Represents a "Location" (e.g., US-FREE#1).
- **PhysicalServer:** Represents a specific node with an `ExitIP` and `Domain`.
- **Key Field:** `X25519PublicKey` – The server's public key for WireGuard.

#### B. Registering WireGuard Keys (`POST /vpn/v1/certificate`)
This endpoint is used to register your local public key on the Proton backend.
- **Request:** `{"ClientPublicKey": "YOUR_BASE64_PUBLIC_KEY"}`
- **Response:** Returns the internal IP assigned to your tunnel and DNS settings.

---

## Русский

Это самое полное техническое руководство по API Proton VPN, воссозданное в процессе разработки этого клиента. Так как официальной документации не существует, этот файл является основным справочником по работе с сетью.

### 1. Инфраструктура и Базовые URL

Proton использует несколько доменов. В случае блокировок приложение может переключаться между ними.

- **Основной:** `https://vpn-api.proton.me/`
- **Дополнительный:** `https://api.protonmail.ch/`
- **Альтернативный:** `https://api.protonvpn.ch/`

### 2. Сетевой уровень (`NetworkModule`)

Бэкенд Proton крайне чувствителен к заголовкам. При их отсутствии или неверном формате сервер возвращает ошибки `403` или `422`.

#### Реализация заголовков
Все запросы должны проходить через интерцептор:

```kotlin
// Пример из NetworkModule.kt
val headerInterceptor = Interceptor { chain ->
    val userAgent = "ProtonVPN/5.15.95.5 (Android XX; MODEL XXX-XXX)"
    val request = chain.request().newBuilder()
        .addHeader("User-Agent", userAgent)
        .addHeader("x-pm-appversion", "android-vpn@5.15.95.5-dev+play")
        .addHeader("x-pm-apiversion", "4")
        .addHeader("Accept", "application/vnd.protonmail.v1+json")
        .build()
    chain.proceed(request)
}
```

### 3. Процесс аутентификации (Протокол SRP)

Proton использует **Secure Remote Password (SRP)**. Это позволяет войти в аккаунт, не передавая пароль на сервер в открытом или даже хешированном виде.

#### Шаг 1: Анонимная сессия (`POST /auth/v4/sessions`)
Перед входом необходимо создать сессию для получения `UID` (ID сессии).

#### Шаг 2: Получение параметров (`POST /auth/v4/info`)
Запрос параметров SRP сервера.
- **Запрос:** `{"Username": "user123"}`
- **Ключевые поля ответа:**
    - `Modulus`: Большое простое число SRP (N).
    - `Salt`: Соль пользователя (s).
    - `ServerEphemeral`: Публичное эфемерное значение сервера (B).
    - `SRPSession`: Временный токен для хендшейка.

#### Шаг 3: Авторизация (`POST /auth/v4`)
Клиент вычисляет `ClientProof` локально, используя пароль и полученные значения.
- **Тело запроса:**
```json
{
  "Username": "user123",
  "ClientEphemeral": "...", // Публичное значение клиента (A)
  "ClientProof": "...",     // Вычисленное доказательство (M1)
  "SRPSession": "..."       // Из Шага 2
}
```
- **Успешный ответ:** Содержит `AccessToken`, `RefreshToken` и `UID`.

#### Шаг 4: 2FA (Если включено) (`POST /auth/v4/2fa`)
Если на аккаунте активна двухфакторная аутентификация.
- **Запрос:** `{"TwoFactorCode": "123456"}`

### 4. Управление VPN и Туннелем

#### A. Список серверов (`GET /vpn/v2/logicals`)
Получение иерархии локаций.
- **LogicalServer:** Группа серверов (например, US-FREE#1).
- **PhysicalServer:** Конкретный узел с `ExitIP` и `Domain`.
- **Важное поле:** `X25519PublicKey` – Публичный ключ сервера для WireGuard.

#### B. Регистрация ключей WireGuard (`POST /vpn/v1/certificate`)
Эндпоинт для "привязки" вашего локального публичного ключа к бэкенду Proton.
- **Запрос:** `{"ClientPublicKey": "ВАШ_BASE64_ПУБЛИЧНЫЙ_КЛЮЧ"}`
- **Ответ:** Содержит назначенный внутренний IP для туннеля и настройки DNS.

---

### Disclaimer / Отказ от ответственности
This documentation is for educational purposes only. It is the result of reverse-engineering and may change without notice.
Данная документация создана исключительно в образовательных целях на основе реверс-инжиниринга и может измениться без уведомления.
