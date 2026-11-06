# Security policy

## Supported versions

| Version | Supported |
|---|---|
| `main` and the latest `1.x` release | ✅ |
| Anything older | ❌ |

## Reporting a vulnerability

Please **do not open a public issue**. Report it privately through GitHub: open the
repository's **Security** tab and choose **Report a vulnerability**. Include:

- what an attacker can do, and against which component (API, web app, solver, deployment
  configuration);
- steps or a request sequence to reproduce, with the `requestId` from any error response;
- the commit or image tag you tested.

The maintainer aims to acknowledge reports within 3 working days and to share an
assessment within 10. Fixes are released as soon as they are verified, and reporters are
credited in the changelog unless they prefer otherwise.

## Scope

In scope: authentication and authorisation (including access to another student's data),
injection, upload handling, the public seat finder and its rate limiting, and the shipped
Docker, Compose, Caddy and Nginx configurations.

Out of scope: denial of service by sheer volume, findings that need an already compromised
administrator account, and the demo credentials in `docker-compose.full.yml`, which are
deliberately public for local evaluation and must never be used in production.

How ExamGrid is secured, including its known limitations, is documented in
[docs/security.md](../docs/security.md).
