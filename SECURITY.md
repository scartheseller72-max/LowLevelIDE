# Security Policy

## Supported versions

The latest released version on the default branch receives security fixes.

| Version | Supported |
|---------|-----------|
| 2.1.x   | yes       |
| < 2.1   | no        |

## Reporting a vulnerability

Please do **not** open a public issue for security vulnerabilities.

Instead, use GitHub's private vulnerability reporting (the **Security → Report a vulnerability**
tab on the repository) or contact the maintainer directly. Include:

- A description of the issue and its impact.
- Steps to reproduce or a proof-of-concept.
- Affected version(s) and device/Android details.

You can expect an initial acknowledgement within a few days. Once a fix is available we will
coordinate a disclosure timeline with you.

## Scope notes

LowLevelIDE executes user-supplied code in a local sandbox and, optionally, with root on the
user's own device. Reports about a user intentionally running their own privileged commands are
out of scope. Reports about the app exposing data outside its sandbox, mishandling permissions,
or insecurely fetching bootstrap assets are in scope.
