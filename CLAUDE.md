# Closed-loop development rules

- Run applicable P0 and P1 checks after each logical unit.
- On failure: analyze, fix, re-run, and record evidence in `docs/dev-log.md`.
- Acceptance criteria are locked by `docs/closed-loop-contract.md`; commands may be adjusted only when logged.
- No completion claim without fresh command output and exit status.
- Stop an item after the same failure occurs three consecutive times; stop broad fixes after ten total fix attempts.
- Never commit or expose `.env`, keystores, `local.properties`, or production data.
- There is no official hosted API. Operators deploy `backend/` themselves and point the Android app at that URL.

