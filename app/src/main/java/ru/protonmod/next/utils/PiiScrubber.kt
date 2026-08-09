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

import java.util.regex.Pattern

/**
 * Utility to scrub Personally Identifiable Information (PII) from logs and events
 * before they are sent to external services like Sentry.
 */
object PiiScrubber {
    
    // IPv4 Address regex
    private val IPV4_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    
    // IPv6 Address regex (simplified but covers most cases)
    private val IPV6_PATTERN = Pattern.compile(
        "\\b(?:[A-Fa-f0-9]{1,4}:){2,7}[A-Fa-f0-9]{1,4}\\b|" +
        "\\b(?:[A-Fa-f0-9]{1,4}:){1,7}:\\b|" +
        "\\b:(?::[A-Fa-f0-9]{1,4}){1,7}\\b"
    )
    
    // Sensitive key-value pairs in logs (e.g., "accessToken=...", "sessionId: ...", "session: ...", "user: ...")
    private val SENSITIVE_KV_REGEX = Regex(
        "(?i)[\"']?\\b(accessToken|refreshToken|sessionId|session|captchaToken|token|privateKey|presharedKey|pass|password|secret|auth|nonce|wgPrivateKey|wgCertificate|username|user|email|UserID|UID|AccessToken|RefreshToken|SRPSession|ServerEphemeral|ClientEphemeral|ClientProof|TwoFactorCode)\\b[\"']?\\s*[:=->]+\\s*[\"']?([a-zA-Z0-9._\\-+=/]{4,})[\"']?"
    )
    
    // URL query parameters that might contain tokens
    private val URL_QUERY_TOKEN_REGEX = Regex(
        "(?i)\\b(token|sessionId|access_token|refresh_token|captchaToken)=([^&\\s]+)"
    )

    // URL regex to redact domains (browsing history is PII)
    // Matches http://, https://, or just domains that look like they are part of a URL
    private val URL_DOMAIN_REGEX = Regex(
        "(?i)\\b(https?://|www\\.)[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9](?:\\.[a-zA-Z]{2,})+(?::\\d+)?(?:/\\S*)?"
    )

    // Standalone long tokens (likely session tokens, keys or identifiers)
    // Catches strings that look like Base64/Hex/Tokens with 32+ characters
    private val STANDALONE_TOKEN_REGEX = Regex(
        "\\b[a-zA-Z0-9._\\-+=/]{32,}(?:={1,2})?(?=\\s|$)"
    )
    
    // Lowercase config markers avoid repeated case-insensitive scans for every log message.
    private val CONFIG_MARKERS = listOf(
        "[interface]", "[peer]", "privatekey", "presharedkey", "publickey",
        "address", "dns", "allowedips", "endpoint", "jc =", "jmin ="
    )

    /**
     * Scrubs PII from the given input string.
     */
    fun scrub(input: String?): String {
        if (input == null) return ""
        var result = input

        // 1. Detect and redact whole configuration blocks
        if (isConfigBlock(result)) {
            return "[VPN_CONFIG_REDACTED]"
        }

        // 2. Redact IP Addresses
        result = IPV4_PATTERN.matcher(result).replaceAll("[IPv4]")
        result = IPV6_PATTERN.matcher(result).replaceAll("[IPv6]")

        // 3. Redact Sensitive Key-Value pairs
        result = SENSITIVE_KV_REGEX.replace(result) { match ->
            val group2 = match.groups[2] ?: return@replace match.value
            val startInMatch = group2.range.first - match.range.first
            val endInMatch = group2.range.last + 1 - match.range.first
            
            val prefix = match.value.substring(0, startInMatch)
            val suffix = match.value.substring(endInMatch)
            prefix + "[REDACTED]" + suffix
        }

        // 4. Redact Tokens in URL query parameters
        result = URL_QUERY_TOKEN_REGEX.replace(result) { match ->
            val group2 = match.groups[2] ?: return@replace match.value
            val startInMatch = group2.range.first - match.range.first
            val endInMatch = group2.range.last + 1 - match.range.first
            
            val prefix = match.value.substring(0, startInMatch)
            val suffix = match.value.substring(endInMatch)
            prefix + "[REDACTED]" + suffix
        }

        // 5. Redact URLs (domains) as browsing history is PII
        result = URL_DOMAIN_REGEX.replace(result, "[URL_REDACTED]")

        // 6. Redact Standalone long tokens
        result = STANDALONE_TOKEN_REGEX.replace(result) { match ->
            // Skip if it's already redacted or an IP tag
            if (match.value.startsWith("[") && match.value.endsWith("]")) return@replace match.value
            
            // Check if it's not a common non-sensitive long string (like a URL)
            // But usually 32+ chars of random-looking text IS sensitive in this context
            "[REDACTED]"
        }

        return result
    }

    /**
     * Checks if the string looks like a VPN configuration block.
     */
    private fun isConfigBlock(input: String): Boolean {
        // Almost every routine log is short and single-line. Reject it before scanning markers;
        // doing a case-insensitive search for every marker on the main thread caused widget ANRs.
        if (input.length <= 200 && '\n' !in input) return false

        val normalized = input.lowercase()
        var markersFound = 0
        for (marker in CONFIG_MARKERS) {
            if (marker in normalized && ++markersFound >= 3) return true
        }
        return false
    }
}
