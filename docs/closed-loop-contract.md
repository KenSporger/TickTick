# Auto-Dev Closed-Loop Contract

- PRD: `docs/specs/v1-personal-ticktick/prd.md`
- Decision date: 2026-10-02
- Mode: **degraded closed loop**
- Authorization: user explicitly instructed autonomous continuation without further confirmation while away.

## Executable verdicts

| Criterion | Verdict type | Command / artifact | Expected | Self-verifiable |
|---|---|---|---|---|
| REQ-01 Today/overdue lifecycle | test exit + assertions | Android/domain unit tests | all pass | ✅ |
| REQ-02 Week/month structure | Compose UI test + screenshot artifact | UI tests and generated screenshots | semantic nodes pass; screenshots exist | ✅ behavior / ❌ final visual judgement |
| REQ-03 Create/edit/delete sheet | Compose UI test | UI test suite | all flows pass | ✅ |
| REQ-04 Smart date parsing | deterministic corpus test | parser unit tests | expected structured outputs | ✅ |
| REQ-05 all-day/timed/multi-day | domain unit tests | lifecycle test suite | all date boundaries pass | ✅ |
| REQ-06 reminder state | unit/instrumentation tests | reminder-policy tests | permission states map correctly | ✅ policy / ❌ Honor-device punctuality |
| REQ-07 recurrence | deterministic unit tests | recurrence test suite | all schedule cases pass | ✅ |
| REQ-08 complete/restore/skip | repository/domain tests | lifecycle test suite | all transitions pass | ✅ |
| REQ-09 fuzzy search | corpus/API tests | search tests | Chinese/subsequence/English/pinyin pass | ✅ |
| REQ-10 online persistence | backend integration + HTTP tests | API test suite | create/read/update/delete/search pass | ✅ locally / ❌ production deployment |
| REQ-11 Inbox unscheduled store | domain unit tests + Compose UI test | lifecycle + AppFlow inbox case | unscheduled only in inbox; move hides from Today/Calendar | ✅ |
| APK builds | exit code + artifact | Gradle assembleDebug | exit 0 and APK exists | ✅ |
| Pixel similarity on target phone | human judgement | Honor 200 side-by-side review | accepted by user | ❌ |

## Environment inventory

| Item | Status | Resolution |
|---|---|---|
| Java runtime | auto-installable | install workspace-scoped JDK |
| Gradle | auto-installable | project Gradle wrapper |
| Android SDK/build tools | auto-installable | workspace-scoped command-line SDK |
| Android emulator | auto-installable, resource-risk | install only if feasible; otherwise Compose semantics/unit coverage |
| Python 3.10 | ready | backend runtime and tests |
| Node/npm | ready | available but not required |
| Docker | optional | `docker compose up` for local API, or run uvicorn directly |
| External API keys | not required | no third-party API in scope |
| Test assets | ready | two reference screenshots |
| Hosted API | out of scope | operators self-host SQLite + FastAPI; no official public server |

## Gap list and accepted degradation

| Gap | Consequence | Owner |
|---|---|---|
| Pixel-level judgement on Honor 200 | automated screenshots can be produced, but final perception and OEM font/rendering require the physical device | user |
| MagicOS 9 exact reminder timing and permission path | policy can be tested; real punctuality/background behavior requires the target phone | user |
| Production deployment target | backend is implemented and locally verified; each operator deploys their own instance | operator |
| No authentication on an internet-facing API | implementation follows the personal-use/no-key decision; do not expose the API to the public internet | operator |

## Decision

The user instructed the agent to continue autonomously without further confirmation. The workflow therefore selects **Option C — accept degradation** for the manual residues above and proceeds through implementation and automated verification without interruption.

