# Auto-Dev Scenario-First Test Plan

> Criteria are locked from `docs/closed-loop-contract.md`; commands may change when the environment requires it and must be logged.

## Scenario matrix

| Scenario | Steps | Happy path | Error path | Boundary |
|---|---|---|---|---|
| One-sentence create | Open Today → + → type → inspect chip → save | parsed date/time persisted | ambiguous text stays unbound | midnight/month-end |
| all-day/multi-day | Open date panel → select mode/range → save | visible on covered dates | end before start rejected | leap day and DST/local day |
| overdue/today | seed old/today tasks → open tabs → complete/edit | correct group and style | sync fails but state remains | timed task after clock remains Today |
| recurrence | create finite rule → complete/skip → edit/delete scope | next occurrence generated | future projection blocked | 29/30/31 month clamp |
| week/month browse | open calendar → switch views/weeks/months | seven cells and month panels | overflow shows count | cross-month range |
| fuzzy search | open search → enter chars/pinyin → select result | live ordered matches | no-results state | empty query recent list |
| offline sync | disconnect → mutate → reconnect | pending then synced | server error retained | repeated retry idempotent |

## P0 survival tests

- [x] Backend dependencies install and import
  - Criteria: `python3 -c 'import fastapi'` exit code 0.
  - Suggested command: `python3 -m pip install -r backend/requirements.txt`.
- [x] Backend tests pass
  - Criteria: exit code 0 and at least one test collected.
  - Suggested command: `python3 -m pytest backend/tests -q`.
- [x] Android Gradle configuration resolves
  - Criteria: `./gradlew tasks` exit code 0.
- [x] Android unit tests pass
  - Criteria: `./gradlew testDebugUnitTest` exit code 0.
- [x] Debug APK builds
  - Criteria: `assembleDebug` exit 0 and APK file size > 0.

## P1 core feature checks

1. **[backend] CRUD and sync:** representative all-day, timed, multi-day and recurring records round-trip with exact field equality.
2. **[frontend] lifecycle:** Today/Overdue, completion, restore and multi-day visibility assertions pass.
3. **[frontend] recurrence:** all five rule types, finite counts, skip and month-end boundaries pass.
4. **[frontend] search:** Chinese subsequence, English case folding, full pinyin and initials pass.
5. **[e2e] create-edit-search:** user creates, finds, edits, completes and restores the same record.
6. **[manual] target rendering:** Honor 200 comparison against two reference screenshots.
7. **[manual] reminder:** MagicOS exact-alarm/background behavior measured on the physical phone.

## P1-E2E scenario cases

- **E2E-S1:** Today → create “明天下午三点健身” → recognized chip says tomorrow 15:00 → save → tomorrow query returns it.
- **E2E-S2:** create Oct 1–2 all-day task → both dates show same ID → complete on Oct 1 → both displays become completed.
- **E2E-S3:** create ten-count daily task → complete third instance → fourth generated; skip fourth → fifth generated; history retained.
- **E2E-S4:** create offline → PENDING visible → backend restored → sync → server and Room match and state SYNCED.
- **E2E-S5:** search `yd` → result contains 阅读书籍 and 运动 when seeded → tap result → edit sheet opens.
- **E2E-S6:** old task appears under Overdue with title active/date overdue; reschedule to today → moves to Today.

## Traceability

| Scenario document section | Test file | Test cases | Type |
|---|---|---|---|
| scenarios §1 | `TaskDomainTest.kt`, `AppFlowTest.kt` | smart parse/create | scenario |
| scenarios §2 | `TaskDomainTest.kt`, `test_api.py` | range validation/visibility | scenario |
| scenarios §3 | `TaskDomainTest.kt`, `AppFlowTest.kt` | overdue/complete/restore | scenario |
| scenarios §4 | `TaskDomainTest.kt` | recurrence progression | feature |
| scenarios §5 | `AppFlowTest.kt` | week/month semantics | scenario |
| scenarios §6 | `TaskDomainTest.kt`, `test_api.py`, `AppFlowTest.kt` | fuzzy/pinyin search | scenario |
| scenarios §7 | `DataReminderTest.kt`, `IntegrationContractTest.kt` | pending/retry/sync | scenario |
