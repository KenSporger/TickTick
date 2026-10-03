# Closed-loop development rules

- Run applicable P0 and P1 checks after each logical unit.
- On failure: analyze, fix, re-run, and record evidence in `docs/dev-log.md`.
- Acceptance criteria are locked by `docs/closed-loop-contract.md`; commands may be adjusted only when logged.
- No completion claim without fresh command output and exit status.
- Stop an item after the same failure occurs three consecutive times; stop broad fixes after ten total fix attempts.
- Never commit or expose `qingyin.pem`, `.env`, keystores, or production data.
- The inspected Qingyin host and its qy-console directories are read-only context for this project.

