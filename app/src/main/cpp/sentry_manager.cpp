/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "sentry_manager.h"
#include "obfuscation.h"
#include <android/log.h>

#define TAG "SentryManager"

namespace next {

std::string SentryManager::getSentryDsn() {
#ifdef PRIVACY_FLAVOR
    return "";
#else
    return XOR_STR("https://45480dc521a24d1e6dc87c6ebb7380bd@o4511097624199168.ingest.de.sentry.io/4510986956374096");
#endif
}

void SentryManager::init(const char*, bool, const char*, int, const char*) {
    // Sentry Native removed to save APK size
}

void SentryManager::shutdown() {
    // Sentry Native removed
}

void SentryManager::reportSecurityEvent(JNIEnv* env, const std::string& event) {
#ifdef PRIVACY_FLAVOR
    __android_log_print(ANDROID_LOG_INFO, TAG, "[PRIVACY] Security event: %s", event.c_str());
#endif
    if (!env) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot report security event: JNIEnv is null. Event: %s", event.c_str());
        return;
    }

    jclass bridgeClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/SentryBridge").c_str());
    if (!bridgeClass) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to find SentryBridge class");
        return;
    }

    jmethodID reportMethod = env->GetStaticMethodID(bridgeClass, XOR_STR("reportSecurityEvent").c_str(), XOR_STR("(Ljava/lang/String;)V").c_str());
    if (!reportMethod) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to find reportSecurityEvent method");
        return;
    }

    jstring jEvent = env->NewStringUTF(event.c_str());
    env->CallStaticVoidMethod(bridgeClass, reportMethod, jEvent);
    env->DeleteLocalRef(jEvent);

#ifndef PRIVACY_FLAVOR
    __android_log_print(ANDROID_LOG_INFO, TAG, "Security event forwarded to Sentry Android: %s", event.c_str());
#endif
}

void SentryManager::flushAndTerminate(JNIEnv* env) {
    if (!env) {
        // No JNI env — can't flush, just die.
        abort();
        return;
    }

    jclass bridgeClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/SentryBridge").c_str());
    if (!bridgeClass) {
        env->ExceptionClear();
        abort();
        return;
    }

    jmethodID flushMethod = env->GetStaticMethodID(bridgeClass, XOR_STR("flushAndTerminate").c_str(), XOR_STR("()V").c_str());
    if (!flushMethod) {
        env->ExceptionClear();
        abort();
        return;
    }

    // In privacy flavor, this just calls Process.killProcess().
    // In other flavors, it dispatches a background thread that calls
    // Sentry.flush(3000) then killProcess(), and returns immediately so
    // the main-thread Looper is not blocked (avoids ANR).
    env->CallStaticVoidMethod(bridgeClass, flushMethod);

    // The Kotlin side is now responsible for terminating the process.
    // Block this native thread briefly to give the background thread time
    // to kill the process before we fall through.
    struct timespec ts = {4, 0}; // 4 seconds > the 3-second Sentry flush timeout
    nanosleep(&ts, nullptr);

    // Hard fallback only if the process is somehow still alive after 4 s.
    abort();
}

void SentryManager::reportLog(JNIEnv* env, int level, const char* tag, const char* message) {
    if (!env) return;

    jclass bridgeClass = env->FindClass(XOR_STR("ru/protonmod/next/vpn/SentryBridge").c_str());
    if (!bridgeClass) {
        env->ExceptionClear();
        return;
    }

    jmethodID reportLogMethod = env->GetStaticMethodID(bridgeClass, XOR_STR("reportLog").c_str(), XOR_STR("(ILjava/lang/String;Ljava/lang/String;)V").c_str());
    if (!reportLogMethod) {
        env->ExceptionClear();
        return;
    }

    jstring jTag = env->NewStringUTF(tag);
    jstring jMessage = env->NewStringUTF(message);
    env->CallStaticVoidMethod(bridgeClass, reportLogMethod, level, jTag, jMessage);
    env->DeleteLocalRef(jTag);
    env->DeleteLocalRef(jMessage);
}

} // namespace next
