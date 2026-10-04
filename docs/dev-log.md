# Development Verification Log

## 2026-10-04 Inbox device UI

| Time | Action | P0 Result | P1 Result | Details |
|---|---|---|---|---|
| 2026-10-04 19:18 CST | First `connectedDebugAndroidTest` on ELI-AN00 | ❌ 6/8 | inbox ✅ | `inboxKeepsUnscheduledTasksOffTodayAndCalendar` passed. `complexTimeCronAndRangeChip` failed: `到` also matched 移到收集箱. `deleteThisOccurrenceKeepsLaterRepeats` failed: Sunday is last cell of current week so next daily round is off-screen. |
| 2026-10-04 19:19 CST | Assertion fixes + rerun | ❌ 7/8 | — | Range chip now asserted via `打开日期` tag. Sunday swipe exposed 14 `阅读书籍` nodes; `onNodeWithText` requires a unique match. |
| 2026-10-04 19:20 CST | Use first visible future occurrence | ✅ 8/8 | inbox on device | `./gradlew connectedDebugAndroidTest` BUILD SUCCESSFUL. Finished 8 tests on ELI-AN00 - 15, 0 failed. |

## 2026-10-04 Inbox page

| Time | Action | P0 Result | P1 Result | Details |
|---|---|---|---|---|
| 2026-10-04 19:11 CST | Replace 我的 with 收集箱; unscheduled store + move-off-schedule | ✅ unit + API + APK | ❌ device UI | RED: `isInbox`/`moveToInbox`/`inboxItems` unresolved. GREEN: `./gradlew testDebugUnitTest` 28 passed (TaskDomainTest 21). `python3 -m pytest backend/tests -q`: 5 passed. `./gradlew assembleDebug` exit 0; `app-debug.apk` 11,540,030 bytes. `compileDebugAndroidTestKotlin` exit 0. `adb devices` empty, so `inboxKeepsUnscheduledTasksOffTodayAndCalendar` did not run on phone. |

## Phase 2: Development Verification

| Time | Task | Gate1 (Red) | Gate2 (Green) | Review A (Spec) | Review B (Quality) | File Scope | Independent Test |
|---|---|---|---|---|---|---|---|
| 2026-10-03 08:45 CST | feat-backend-api | ✅ missing module | ✅ 4 passed | ✅ | ✅ warnings logged | ✅ | ✅ `pytest` 4 passed |
| 2026-10-03 08:51 CST | feat-domain-lifecycle | ✅ missing wrapper | ✅ after toolchain install | ✅ | ✅ dependency adjusted | ✅ | ✅ Gradle BUILD SUCCESSFUL |
| 2026-10-03 11:15 CST | fix-compose-overlay-scope | ✅ `compileDebugKotlin` unresolved receiver | ✅ `ColumnScope` receiver compiles | ✅ | ✅ minimal type correction | ✅ | ✅ full unit suite reached execution |
| 2026-10-03 11:18 CST | feat-android-backend-contract | ✅ aware timestamp contract test failed | ✅ DTO uses Asia/Shanghai offset | ✅ | ✅ exact field round-trip | ✅ | ✅ focused contract test passed |

## Phase 3: Integration Verification

| Time | Action | P0 Result | P1 Result | Details |
|---|---|---|---|---|
| 2026-10-03 11:20 CST | Backend regression | ✅ | backend API | `python3 -m pytest backend/tests -q`: 4 passed, 1 dependency deprecation warning |
| 2026-10-03 11:20 CST | Android JVM regression | ✅ | domain/data/integration | `./gradlew testDebugUnitTest`: 14 passed |
| 2026-10-03 11:20 CST | Debug APK | ✅ | packaging | `./gradlew assembleDebug`: success; `app-debug.apk` 10,933,911 bytes |
| 2026-10-03 11:21 CST | Instrumentation test APK | ✅ compile/package | UI scenarios not executed | `./gradlew assembleDebugAndroidTest`: success; emulator/device still required to run 4 Compose tests |
| 2026-10-03 13:12 CST | fix abandon, smart fill, date/repeat sheets, month swipe | ✅ unit assertions | ✅ device Compose | `./gradlew testDebugUnitTest` 19 passed; `./gradlew connectedDebugAndroidTest` 5 passed on ELI-AN00. Dependency repos include Aliyun because dl.google.com TLS handshake fails on this machine. |
| 2026-10-03 13:46 CST | abandon mark, complex time/cron, no snackbar, repeat projection, range chip | ✅ unit assertions | ✅ device Compose + manual UI | `./gradlew testDebugUnitTest` BUILD SUCCESSFUL (22 tests). `./gradlew connectedDebugAndroidTest` 6 passed on ELI-AN00 API 35. Reinstalled `app-debug.apk`. Device: abandon shows × and strikethrough with no “任务已完成”; `0 30 9 * * 0` fills 明天 09:30; range chip shows 10月3日到8日; weekly Sunday task is on 周日 4 and 周日 11. |
| 2026-10-03 15:50 CST | repeat delete scope; cloud sync client | ✅ unit + API | ❌ phone offline | `python3 -m pytest backend/tests -q`: 5 passed. `./gradlew testDebugUnitTest` BUILD SUCCESSFUL. `./gradlew connectedDebugAndroidTest` did not run: `adb devices` empty. Qingyin probe read-only: qy-console online on 127.0.0.1:8100, nginx :80. No rsync and no nginx change pending confirmation. |
| 2026-10-03 17:20 CST | deploy ticktick-api beside qy-console | ✅ local API + public proxy | ✅ phone sync and delete-one-round | pm2 `ticktick-api` on 127.0.0.1:8200, data `/root/project-data/ticktick-api`. Nginx only added `location /ticktick-api/`. `qy-console` pid 249661 stayed online; `http://8.133.175.86/login` 200. Phone pushed tasks; deleting only 2026-10-03 left `阅读书籍` on 周日 4 and stored `excluded_dates: ["2026-10-03"]`. |
| 2026-10-03 17:35 CST | do not reseed demo tasks over a cloud snapshot | ✅ unit assertions | ❌ phone offline at install | Cloud row `read` is `DELETED`. `./gradlew testDebugUnitTest` BUILD SUCCESSFUL. `adb devices` empty, so the fixed APK was not installed. |
| 2026-10-03 18:20 CST | regression after cloud reseed fix | ✅ unit + API | ✅ ELI-AN00 wipe and Compose | `./gradlew testDebugUnitTest` UP-TO-DATE success. `python3 -m pytest backend/tests -q`: 5 passed. `./gradlew connectedDebugAndroidTest`: 7 passed on ELI-AN00. After `pm clear` and a fresh install, today shows 运动 only; calendar Sunday shows 出差准备 and not 阅读书籍. Cloud stays `read DELETED`, `trip ACTIVE`. App reinstalled and launched. |
