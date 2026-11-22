# DRIFT Real-World PR Showcase — Spring Petclinic REST

This fork demonstrates **[DRIFT Free (`v0.10.0`)](https://github.com/rad-ox/drift)** running in live GitHub Actions pull-request checks on **[Spring Petclinic REST](https://github.com/spring-petclinic/spring-petclinic-rest)** (pinned at commit `220f4ba8e92c`).

- **Reproducible full 9-scenario benchmark harness:** [rad-ox/drift-realworld](https://github.com/rad-ox/drift-realworld)
- **Important:** All demo PRs target this fork's `drift-baseline` branch only — never upstream.

## Branch Layout

- `main` — Untouched upstream `spring-petclinic/spring-petclinic-rest` at commit `220f4ba8e92cec9ad1faa03324723a3e9da63d95`.
- `drift-baseline` — Adds `.drift/` (schema `0.3` `owner-api` contract + `petclinic-http@1` profile + `core@1` area policy), `drift-junit:0.10.0` + `OwnerApiDriftTest`, and `.github/workflows/drift-gate.yml`.

## Demo Pull Requests (against `drift-baseline`)

Every PR runs three separate status checks so you can inspect the exact contrast directly in the GitHub PR Checks panel:

1. **`Native Tests (mvn test)`** — Standard Maven/JUnit 5 execution.
2. **`DRIFT Gate (Free v0.10.0)`** — `drift gate .drift/contracts --events ... --explain`.
3. **`Open MIT Verifier (drift_verify.py)`** — Independent Python reference verifier from `rad-ox/drift@v0.10.0`.

| PR Branch | Scenario | Native Tests | DRIFT Gate (Free) | Open Verifier |
|---|---|:---:|:---:|:---:|
| `demo/false-green` | Reintroduces upstream bug `#390` (blank `address` accepted with HTTP `201`) and weakens test assertions so `mvn test` passes | `PASS` | **`FAIL`** (`bound`) | **`FAIL`** (`bound`) |
| `demo/valid-fix` | Legitimate defensive hardening of `OwnerRestControllerV1` with intact contract verification | `PASS` | `PASS` (`verified`) | `PASS` (`verified`) |
| `demo/free-limit` | **Honest DRIFT Free limit:** keeps bug `#390` unfixed and passes hardcoded expected literals into `c.verify(...)` — showing what Free alone does **not** block without test isolation or Enterprise mutation proofs | `PASS` | `PASS` (not blocked) | `PASS` (not blocked) |
