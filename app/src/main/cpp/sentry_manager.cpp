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
    return XOR_STR("https://c9a1c0cf35e7706fca405af8ee26e147@o4511097624199168.ingest.de.sentry.io/4510986956374096");
}

void SentryManager::init(const char*, bool, const char*, int, const char*) {
    // Sentry Native removed to save APK size
}

void SentryManager::shutdown() {
    // Sentry Native removed
}

void SentryManager::reportSecurityEvent(JNIEnv* env, const std::string& event) {
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

    __android_log_print(ANDROID_LOG_INFO, TAG, "Security event forwarded to Sentry Android: %s", event.c_str());
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

    // This calls Sentry.flush(3000) then killProcess() on the Kotlin side.
    // The process will not return from this call.
    env->CallStaticVoidMethod(bridgeClass, flushMethod);

    // Fallback: if the JVM call somehow returns, abort hard.
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
