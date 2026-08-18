# Security Policy

The ZIO HTTP maintainers take the security of the library and its ecosystem
seriously. We appreciate your efforts to responsibly disclose your findings and
will make every effort to acknowledge your contributions.

## Supported Versions

Security updates are applied to the latest release of the actively maintained
series. Older series may not receive security fixes.

| Version | Supported          |
| ------- | ------------------ |
| 3.x     | :white_check_mark: |
| 2.x     | :x:                |
| 0.0.x   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
pull requests, or the Discord server.**

Instead, report them privately using GitHub's
[Private Vulnerability Reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability):

1. Open the [**Report a vulnerability**](https://github.com/zio/zio-http/security/advisories/new)
   form.
2. Fill out the advisory with as much detail as possible.

This opens a private channel between you and the maintainers where the issue can
be discussed and fixed before public disclosure.

### What to Include

To help us triage and resolve the issue quickly, please include as much of the
following as you can:

- A description of the vulnerability and its potential impact.
- The affected version(s) and, if known, the affected module(s) (for example
  server, client, `Endpoint` API, WebSockets, TLS/SSL support, or one of the
  authentication middlewares).
- Whether the issue affects the server, the client, or both.
- Steps to reproduce, ideally with a minimal code sample or test case.
- Any proof-of-concept or exploit code.
- Suggested remediation, if you have one.

## Disclosure Process

We follow a coordinated (responsible) disclosure process:

1. **Acknowledgement** — We aim to acknowledge your report within a few business
   days.
2. **Assessment** — We investigate and determine the severity and affected
   versions.
3. **Fix** — We prepare a fix and, where warranted, a
   [GitHub Security Advisory](https://github.com/zio/zio-http/security/advisories)
   with a CVE identifier.
4. **Release** — We publish the fix and advisory, crediting the reporter unless
   anonymity is requested.

Please give us a reasonable amount of time to address the issue before any
public disclosure. We will keep you informed of our progress throughout.

## Scope

This policy applies to the `zio/zio-http` repository and all artifacts
published from it, including `zio-http`, `zio-http-shaded`, `zio-http-cli`,
`zio-http-gen`, `zio-http-testkit`, and the integration modules
(`zio-http-htmx`, `zio-http-datastar-sdk`, `zio-http-stomp`).

Vulnerabilities in dependencies (such as ZIO core, zio-schema, or Netty) should
be reported to their respective projects; if such an issue is exploitable
through ZIO HTTP's own API surface or defaults, we still want to hear about it.
