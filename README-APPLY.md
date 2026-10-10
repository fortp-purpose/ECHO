# ECHO Phase 4 — API error contract patch

This patch adds the first part of Phase 4's error-envelope foundation:
- a stable `ApiError` response shape (`code`, `status`, safe `message`, `details`, `correlationId`, `timestamp`);
- a request filter that creates or accepts a UUID-shaped `X-Correlation-Id` and returns it in the response header;
- a global fallback handler that logs unexpected exceptions server-side but never exposes exception details to clients;
- a focused unit test for the response contract.

## Apply

Extract this ZIP into `C:\XboxGames\echo`, preserving the directory structure and allowing the new files to be added. The patch only adds files; it does not overwrite existing project files.

Then run in PowerShell:

```powershell
cd C:\XboxGames\echo\backend
.\mvnw.cmd clean test
```

If successful, return to the repository root and review:

```powershell
cd C:\XboxGames\echo
git status --short
```

Do not commit yet. We will first verify the test output and review any differences.

## Important scope note

This is a first implementation slice, not a claim that Phase 4 is complete. Request-validation errors and explicit domain/API errors still need mapping into the same envelope, and the existing roadmap/status documentation needs to be updated in the same change before the phase can be marked complete.
