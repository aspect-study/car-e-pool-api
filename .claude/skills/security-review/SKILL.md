---
name: security-review
description: Use before merging any branch to main. Runs the full security checklist across all layers — backend, web, and mobile. Flags issues that must be resolved before the PR lands.
---

# Security Review (Pre-Merge)

Run this skill before merging any branch. Do not merge until all HIGH items pass.

## Step 1 — Get the diff

```bash
git diff main...HEAD --name-only
```

Identify which layers are affected: backend only / web only / mobile only / multiple layers.

## Step 2 — Backend Checks

Run these on any changed Java file:

### Identity and Access Control
- [ ] `carpoolUserId` is extracted from `SecurityContext`, never from the request body
- [ ] Every new endpoint has an explicit permission rule in the security config — not relying solely on the catch-all deny
- [ ] Driver-only actions check `ride.driver.id == carpoolUserId` inside the service, not just at the controller
- [ ] Admin endpoints (port 8082) are behind a separate security configurer — not reachable on port 8080

### Secrets and Config
- [ ] No secrets, tokens, or passwords in any `.properties` file that will be committed
- [ ] No `JWT_SECRET`, `TELEGRAM_BOT_TOKEN`, or DB credentials in `application-local.properties`
- [ ] All new env vars are documented in `docker-compose.yml` and `.env.example`

### Data Privacy
- [ ] Plate number is masked at the service layer before the DTO is constructed — no raw plate in any response
- [ ] No PII (user names, chat IDs, phone numbers) in log lines at INFO or above
- [ ] `log.error()` calls pass `e` as the final argument — never `e.getMessage()` alone

### API Safety
- [ ] New endpoints with unbounded list returns use `Pageable` — no `findAll()` without pagination
- [ ] Telegram message text that includes user input uses HTML escaping if `ParseMode.HTML` is active
- [ ] `auth_date` staleness check is present on any Telegram Login Widget validation

---

## Step 3 — Web (Next.js) Checks

Run these on any changed TypeScript/TSX file:

### JWT Storage
- [ ] JWT is set via `next/headers` in an API route — never in a client component
- [ ] Cookie options: `httpOnly: true`, `secure: true` (in production), `sameSite: 'lax'`
- [ ] No `localStorage.setItem` or `sessionStorage.setItem` with any token or user ID

### Environment Variables
- [ ] No new `NEXT_PUBLIC_` variable that contains a sensitive value (API keys, internal URLs)
- [ ] `API_BASE_URL` does not have a `NEXT_PUBLIC_` prefix

### XSS
- [ ] No new `dangerouslySetInnerHTML` on user-supplied content (`ride.notes`, `passengerMessage`, user names)
- [ ] Any new `dangerouslySetInnerHTML` has DOMPurify sanitization

### Route Protection
- [ ] New authenticated routes are added to `middleware.ts` matcher — not protected only by a per-page check

---

## Step 4 — Mobile (Flutter) Checks

Run these on any changed Dart file:

### JWT Storage
- [ ] JWT is stored via `flutter_secure_storage` only — no `SharedPreferences` for tokens
- [ ] `AndroidOptions(encryptedSharedPreferences: true)` is present
- [ ] `IOSOptions(accessibility: KeychainAccessibility.first_unlock)` is present

### Token Lifecycle
- [ ] `TokenStorage.delete()` is called on logout — iOS Keychain persists after uninstall
- [ ] First-launch token validation is present — stale tokens from a previous install are cleared

### Secrets and Logging
- [ ] No hardcoded `API_BASE_URL` in Dart source — uses `--dart-define-from-file`
- [ ] No `debugPrint` or `print` calls that output tokens, user IDs, or auth data
- [ ] No sensitive values in `flutter_secure_storage` key names that would appear in device logs

---

## Step 5 — Output Format

For each issue found:

```
[LAYER][SEVERITY] file:line — risk description — fix
```

**Severity levels:**
- `HIGH` — must fix before merge: auth bypass, secret exposure, XSS, data leak
- `MED` — fix before production: privacy leak, missing pagination, missing validation
- `LOW` — fix before next sprint: cosmetic security hygiene, log verbosity

If a layer has no issues: `[LAYER] PASS — no issues found`

---

## Step 6 — Block criteria

Do not approve the PR if any `HIGH` item is open.

`MED` items: create a TODOS.md entry and link from the PR description before merging.

`LOW` items: note in PR description, fix in next sprint.

## Checklist

- [ ] Diff identified — all affected layers noted
- [ ] Backend: identity/access, secrets/config, data privacy, API safety all checked
- [ ] Web: JWT storage, env vars, XSS, route protection all checked
- [ ] Mobile: JWT storage, token lifecycle, secrets/logging all checked
- [ ] Output written in [LAYER][SEVERITY] format
- [ ] No HIGH items open before merge
- [ ] MED items tracked in TODOS.md
