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

package ru.protonmod.next.utils

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel

class SentryCrashReporter : CrashReporter {

    override fun addBreadcrumb(tag: String, message: String, level: String, category: String) {
        val breadcrumb = Breadcrumb().apply {
            this.category = if (category == "log.message") tag else category
            this.message = PiiScrubber.scrub(message)
            this.level = parseLevel(level)
            if (category != "log.message") {
                this.setData("tag", tag)
            }
        }
        Sentry.addBreadcrumb(breadcrumb)
    }

    override fun addLog(tag: String, message: String, level: String, throwable: Throwable?) {
        val fullMessage = PiiScrubber.scrub("[$tag] $message")
        val sentryLogLevel = when (parseLevel(level)) {
            SentryLevel.DEBUG -> SentryLogLevel.DEBUG
            SentryLevel.INFO -> SentryLogLevel.INFO
            SentryLevel.WARNING -> SentryLogLevel.WARN
            SentryLevel.ERROR -> SentryLogLevel.ERROR
            SentryLevel.FATAL -> SentryLogLevel.FATAL
        }

        if (throwable != null) {
            val scrubbedThrowableMsg = PiiScrubber.scrub(throwable.message)
            // Do not let the logger serialize the unsanitized throwable/cause.
            Sentry.logger().log(sentryLogLevel, "$fullMessage: $scrubbedThrowableMsg")
        } else {
            Sentry.logger().log(sentryLogLevel, fullMessage)
        }
    }

    override fun captureException(throwable: Throwable, extras: Map<String, String>?) {
        if (extras != null) {
            Sentry.withScope { scope ->
                extras.forEach { (k, v) -> scope.setExtra(k, v) }
                Sentry.captureException(throwable)
            }
        } else {
            Sentry.captureException(throwable)
        }
    }

    override fun captureMessage(message: String, level: String, extras: Map<String, String>?) {
        val sentryLevel = parseLevel(level)
        if (extras != null) {
            Sentry.withScope { scope ->
                extras.forEach { (k, v) -> scope.setExtra(k, v) }
                scope.level = sentryLevel
                Sentry.captureMessage(PiiScrubber.scrub(message))
            }
        } else {
            Sentry.captureMessage(PiiScrubber.scrub(message), sentryLevel)
        }
    }

    override fun flush(timeout: Long) {
        Sentry.flush(timeout)
    }

    override fun recordDistribution(name: String, value: Double) {
        Sentry.metrics().distribution(name, value)
    }

    override fun recordCount(name: String, value: Double) {
        Sentry.metrics().count(name, value)
    }

    private fun parseLevel(level: String): SentryLevel = when (level) {
        "DEBUG" -> SentryLevel.DEBUG
        "INFO" -> SentryLevel.INFO
        "WARNING" -> SentryLevel.WARNING
        "ERROR" -> SentryLevel.ERROR
        "FATAL" -> SentryLevel.FATAL
        else -> SentryLevel.INFO
    }
}
