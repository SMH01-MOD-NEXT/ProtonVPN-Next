# Security Policy

## Reporting Security Vulnerabilities

ProtonVPN-Next takes security very seriously. We appreciate your efforts to responsibly disclose security vulnerabilities and will make every effort to acknowledge your contributions.

### Reporting Process

**Please do NOT create public GitHub issues for security vulnerabilities.** Instead, follow the responsible disclosure guidelines below:

#### For Low & Medium Severity Issues
**Email**: `vpn-next@outlook.com`

Please include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if available)
- Your contact information for follow-up

**Response Time**: We aim to respond within 7 days with initial assessment.

#### For High & Critical Severity Issues
**Email**: `smali.pg@outlook.com`

**⚠️ IMPORTANT**: This email is **ONLY** for critical security vulnerabilities that pose immediate risk to user privacy, data, or system integrity.

Examples of critical issues:
- Remote code execution vulnerabilities
- Complete authentication bypass
- Data exfiltration or encryption bypass
- Privilege escalation vulnerabilities
- Zero-day exploits

**DO NOT email this address for**:
- Minor bugs or non-security issues
- Feature requests
- Low-impact vulnerabilities
- General questions

Abuse of this channel will result in your messages being ignored.

**Response Time**: Critical issues receive immediate priority review (within 24 hours).

### Security Vulnerability Criteria

**High/Critical** (email smali.pg@outlook.com):
- CVE-eligible vulnerabilities
- Active exploitation possible
- Affects multiple users or versions
- Bypasses core security features
- Severity score: 7.0+

**Medium** (email vpn-next@outlook.com):
- Limited exploitation potential
- Requires specific conditions
- Affects specific configurations
- Severity score: 4.0-6.9

**Low** (email vpn-next@outlook.com):
- Theoretical vulnerabilities
- Difficult to exploit
- Limited real-world impact
- Severity score: 0.1-3.9

## Scope

This security policy applies to:
- **ProtonVPN-Next Android** (ru.protonmod.next)
- **ProtonVPN-Next Web** (ProtonVPN-Next-WEB)
- Associated native libraries and dependencies

## What to Expect

### Acknowledgment
We will acknowledge receipt of your vulnerability report within 48 hours.

### Assessment
Our security team will:
- Reproduce the vulnerability
- Determine severity and impact
- Identify affected versions
- Develop and test patches
- Plan deployment timeline

### Notification
- You will be informed of the vulnerability status
- We will provide updates on remediation progress
- You will be notified before public disclosure
- We may credit you in security announcements (with your permission)

### Timeline for Disclosure
- **Critical**: 24-48 hours for patch release
- **High**: 7-14 days for patch release
- **Medium/Low**: 30-60 days for patch release

We follow responsible disclosure practices and will coordinate public disclosure timing with you.

## Supported Versions

Security updates are provided for:
- Current stable release (full support)
- Previous stable release (6 months support)
- Nightly channel (best effort)
- Older versions (no guaranteed support)

Check the [releases page](https://github.com/SMH01-MOD-NEXT/ProtonVPN-Next/releases) for version status.

## Security Best Practices

### For Users
- Keep ProtonVPN-Next updated to the latest version
- Use strong, unique passwords if password protection is enabled
- Report any unusual behavior or suspected compromises
- Do not share logs containing sensitive information

### For Developers
- Review sensitive code changes thoroughly
- Use secure coding practices in Kotlin, C++, and TypeScript
- Never commit credentials or API keys
- Test changes against known vulnerability patterns
- Keep dependencies updated
- Use static analysis tools (linting, type checking)

## Known Issues & Workarounds

See [SECURITY_ADVISORIES.md](SECURITY_ADVISORIES.md) for published security advisories and known vulnerabilities with available patches.

## Security Infrastructure

- **Code Analysis**: Static analysis on all pull requests
- **Dependency Scanning**: Regular updates and vulnerability checks
- **Build Security**: Signed releases and integrity verification
- **Native Security**: Tamper detection and code obfuscation in privacy builds

## Third-Party Dependencies

ProtonVPN-Next uses the following security-critical dependencies:
- **amneziawg-android**: AmneziaWG VPN protocol
- **go-vpn-lib**: Go-based VPN core
- **Retrofit & OkHttp**: Network requests with DNS-over-HTTPS
- **Room Database**: Encrypted data storage via KSP
- **Jetpack Compose**: Modern UI framework with security updates

All dependencies are monitored for vulnerabilities and updated regularly.

## Incident Response

In case of a confirmed security vulnerability in a released version:
1. **Severity Assessment**: Determine impact and affected users
2. **Patch Development**: Create and test security fix
3. **Release Coordination**: Publish patched version
4. **Public Disclosure**: Announce vulnerability and remediation
5. **Post-Incident Review**: Analyze root cause and prevent recurrence

## Contact & Attribution

**Primary Security Contact**: vpn-next@outlook.com  
**Critical Issues**: smali.pg@outlook.com (high/critical only)

We appreciate security researchers who:
- Report vulnerabilities responsibly
- Provide clear reproduction steps
- Avoid accessing others' data
- Do not publicly disclose before we patch (unless you provide permission)
- Respect our team's time

## Legal

By submitting a security vulnerability report, you agree that:
- You will not pursue legal action against the project or maintainers
- You will allow us reasonable time to address the issue
- You will not publicly disclose before we patch (unless you provide permission)
- Your report may be shared with security researchers or law enforcement if necessary

## Additional Resources

- [Vulnerability Disclosure Program](https://github.com/SMH01-MOD-NEXT/ProtonVPN-Next/security/advisories)
- [Privacy Policy](https://protonvpn.example.com/privacy) *(update link)*
- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

---

**Last Updated**: 2026-08-15

Thank you for helping keep ProtonVPN-Next secure!
