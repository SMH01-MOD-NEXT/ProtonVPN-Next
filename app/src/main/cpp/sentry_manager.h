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

#ifndef NEXT_SENTRY_MANAGER_H
#define NEXT_SENTRY_MANAGER_H

#include <string>
#include <jni.h>

namespace next {

class SentryManager {
public:
    /**
     * No-op since Sentry Native is removed.
     */
    static void init(const char* cache_dir, bool debug, const char* version_name, int version_code, const char* package_name);

    /**
     * No-op since Sentry Native is removed.
     */
    static void shutdown();

    /**
     * Returns the XOR-protected Sentry DSN.
     */
    static std::string getSentryDsn();

    /**
     * Reports a security-related event to Sentry via JNI.
     * @param env JNI environment.
     * @param event Description of the security event.
     */
    static void reportSecurityEvent(JNIEnv* env, const std::string& event);

    /**
     * Reports a log message to Sentry via JNI.
     * @param env JNI environment.
     * @param level Log level (2=DEBUG, 3=INFO, 4=WARN, 5=ERROR, 6=FATAL).
     * @param tag Log tag.
     * @param message Log message.
     */
    static void reportLog(JNIEnv* env, int level, const char* tag, const char* message);

    /**
     * Flushes pending Sentry events (gives SDK time to deliver them) then
     * terminates the process. Call this after reportSecurityEvent() whenever
     * a critical violation (e.g. Frida/Xposed injection) is detected.
     * @param env JNI environment.
     */
    static void flushAndTerminate(JNIEnv* env);
};

} // namespace next

#endif // NEXT_SENTRY_MANAGER_H
