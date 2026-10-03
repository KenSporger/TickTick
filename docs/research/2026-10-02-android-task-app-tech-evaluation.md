# Android 本地任务应用技术评估（仿滴答清单范围）

- 日期：2026-10-02
- 工作流：W3 技术评估
- 验证深度：🔴
- 时效性：📅；权限、上架要求与依赖能力按 2026-10-02 可访问的一手资料复核
- 决策状态：草案，供需求澄清与技术方案阶段选择，不代表用户已经拍板

## 1. 结论摘要

该范围在 Android 上完全可行，且纯本地 MVP 不需要后端。候选基线可采用 **Kotlin + 单 Activity Jetpack Compose + ViewModel/单向数据流 + Repository + Room + AlarmManager + NotificationCompat**。这是当前证据下的推荐候选，而不是既定结论；若团队只熟悉 XML Views，Views 方案仍然可交付，只是对本项目这种状态驱动、日历/列表快速迭代界面，Compose 的开发反馈更直接。

关键判断：

1. 【已验证】Compose 是 Android 官方推荐的现代原生 UI 工具包；官方也支持 Compose 与 Views 互操作。[Android Developers — Compose](https://developer.android.com/compose)
2. 【已验证】Android 官方离线优先架构建议以本地数据源为规范数据源；Room 是结构化本地存储的明确候选。[Android Developers — Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)
3. 【已验证】`WorkManager` 面向“可延迟、可靠的异步工作”，不能作为“用户指定 09:00 就提醒”的精确调度器；Doze 下 WorkManager/JobScheduler 不运行。[Android Developers — WorkManager](https://developer.android.com/jetpack/androidx/releases/work)、[Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)
4. 【已验证】Android 12+ 的精确闹钟需要特殊访问；新装且 target 33+ 的应用在 Android 14+ 上 `SCHEDULE_EXACT_ALARM` 默认不授予。应先解释用途、检查 `canScheduleExactAlarms()`，拒绝时降级为非精确提醒。[Android Developers — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
5. 【已验证】Android 13+ 的普通通知需要运行时 `POST_NOTIFICATIONS`；新安装应用默认关闭通知，用户拒绝后提醒即使调度成功也无法正常展示。[Android Developers — Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
6. 【已验证】系统关机时已注册闹钟全部取消；必须从 Room 重建未来闹钟，并处理 `BOOT_COMPLETED`。精确闹钟授权重新授予后也应重建。[Android Developers — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
7. 【已验证】Unicode CLDR 提供“今天/明天/后天”等中文相对日期词和本地化格式数据，但它是 locale 数据/格式规范，不是从任意句子抽取时间的 NLP 解析器。[Unicode CLDR Chinese summary](https://unicode.org/cldr/charts/44/summary/zh.html)、[LDML Dates](https://cldr-smoke.unicode.org/spec/main/ldml/tr35-dates.html)
8. 【推测】本 MVP 的中文输入边界若限定为“今天/明天/后天、周几、M月D日、HH:mm、上午/下午/晚上”等高频表达，项目内确定性规则解析器的总体风险最低；需由产品规格明确歧义策略并用语料测试验证。

## 2. SCOPE：边界与待确认约束

### 2.1 本次范围

- 本地任务增删改查、完成/未完成状态。
- 今日页：逾期未完成 + 今日任务；未完成高亮，历史已完成置灰。
- 日历/日期浏览：过去与未来一周至一月的任务列表。
- 一句话创建：从中文标题提取日期/时间，同时保留手动日期时间选择。
- 单次定时提醒与系统通知。
- 不含账号、跨设备同步、共享清单、附件、位置提醒、复杂重复规则与服务端推送。

### 2.2 尚未拍板、会改变设计的约束

- 最低 Android 版本（`minSdk`）、分发渠道（仅 APK/企业分发/Google Play）、团队对 Compose 的熟悉度。
- 提醒语义是“尽量准时”还是“必须精确到分钟”。
- 用户跨时区时，任务应保持原绝对时刻，还是保持当地墙上时间。
- 中文解析支持到什么程度：仅规格列出的高频短语，还是接近通用 NLP。
- 是否需要农历、法定节假日、“下下周”等长尾表达。

### 2.3 2026 上架约束

【已验证】如果走 Google Play，2026-08-31 起，新应用和更新须 target Android 16 / API 36 或更高；APK 私下分发不受 Play 提交门槛约束，但仍应面向目标系统测试行为变化。[Google Play — Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

## 3. ORIENT：Android 实现路线

### 3.1 UI 路线

| 候选 | 可行性 | 优点 | 代价/风险 | 适配判断 |
|---|---|---|---|---|
| Kotlin + Compose | 高 | 官方推荐；状态驱动；列表、底部编辑器、日期选中态和主题迭代快；测试可按 semantics 定位 | 团队若无 Compose 经验，有状态管理/重组学习成本；1:1 视觉仍需逐像素校准 | 推荐候选 |
| Kotlin + XML Views/Fragment | 高 | 成熟、团队经验可能更广；RecyclerView/Calendar 生态长期稳定 | 样式和动态状态样板代码更多；新项目迭代速度一般较慢 | 团队强 Views 经验时可选 |
| Flutter/React Native | 可行但非优先 | 若后续明确同时做 iOS，有跨端复用价值 | Android 通知、精确闹钟、启动广播仍需原生桥接；当前只要求 APK，额外抽象收益不足 | 只有跨端路线已确定时再评估 |

【已验证】官方把 Compose 定位为 Android 推荐的现代原生 UI 工具包，并支持与既有 Views 混用。[Android Developers — Compose](https://developer.android.com/compose)

### 3.2 本地数据路线

| 候选 | 可行性 | 判断 |
|---|---|---|
| Room/SQLite | 高 | 任务、提醒、完成态、日期范围查询天然是关系型数据；支持事务、迁移、索引与 Flow 观察。推荐候选。 |
| DataStore | 低（作为任务主库） | 适合简单设置，不适合作为需要范围查询、排序、索引和迁移的任务主库；可用于主题、默认提醒等偏好。 |
| JSON/文件 | 中 | 原型可用，但并发更新、迁移、查询和损坏恢复成本会转嫁给项目，不建议。 |

【已验证】Android 官方建议 repository 暴露单一数据源；离线优先应用以本地数据源为规范数据源，并明确列出 Room 作为结构化存储选项。[Android Developers — Data layer](https://developer.android.com/topic/architecture/data-layer)、[Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)

建议的 MVP 模块边界（概念级，非最终包结构）：

- `ui/today`、`ui/calendar`、`ui/task-editor`
- `domain/model` 与用例（可在 MVP 保持轻量，避免无价值分层）
- `data/local`：Room entity/DAO/database
- `data/repository`：UI 唯一读写入口
- `reminder`：调度策略、广播接收、通知展示、重建协调器
- `datetime`：中文时间抽取、标准化、歧义结果

建议的核心持久化字段：

- `Task(id, title, dueDate, dueTime?, completionStatus, completedAt?, createdAt, updatedAt, reminderPolicy?, revision, deletedAt?)`
- 日期单独保存有利于“今日/逾期/月范围”查询；提醒触发点应由日期、时间、时区策略计算，不应只把展示字符串当真值。
- 【推测】即使纯本地 MVP，也建议使用 UUID、`updatedAt`、`revision`、软删除字段或至少预留迁移，以降低未来同步改表成本；是否现在加入软删除取决于同步是否真的进入路线图。

### 3.3 列表查询语义

建议用数据库查询表达产品规则，而不是 UI 临时拼接：

- 今日页：`未完成 && dueDate < today`（逾期） + `dueDate == today`（当天全部或按产品规则筛选）。
- 日期页：以本地日历日 `[startOfDay, nextStartOfDay)` 或直接 `LocalDate` 进行范围查询。
- 排序应由产品确认：逾期优先、全天任务/有时间任务顺序、已完成沉底，以及同时间的稳定次序。
- 【推测】“过去已完成置灰”不能自动推出“今天已完成也置灰”或“是否折叠”，须在 PRD 中单独写明。

## 4. 中文自然语言日期解析方案对比

### 4.1 必须先定义输出契约

无论采用哪条路线，解析器不应直接静默创建任务，而应返回：

- 原始文本与剥离时间词后的标题建议；
- `date`、可选 `time`、粒度（天/分钟）；
- 命中的原文区间，用于 UI 高亮；
- 置信/歧义状态与候选解释；
- 基准时刻、locale、时区；
- 若日期已过，采用“今年过去则推到明年”还是保留过去日期的明确策略。

### 4.2 候选方案

| 方案 | 覆盖 | 离线/隐私 | Android 集成 | 主要风险 | 结论 |
|---|---|---|---|---|---|
| A. 项目内受限语法：词典 + 正则/扫描器 + `java.time` | 由规格控制；可覆盖今天/明天/后天、周几、M月D日、HH:mm、上午下午晚上 | 完全离线 | 原生、轻量、可单测 | 长尾召回有限；歧义规则必须产品化 | MVP 推荐候选 |
| B. `xk-time` / Time-NLP 系分支 | 中文表达更广 | 离线 | Java 可接入 Kotlin | 源自较老规则工程；README 明示有识别失败率；需要 Android 兼容、体积、边界语料和维护活跃度验证 | 可做对照基线/借鉴，不建议无验证直接绑定 |
| C. Microsoft Recognizers-Text | 多语言体系成熟，.NET 的中文 DateTime 已实现 | 取决于部署 | Java 版直接接入 Android 看似方便 | 【已验证】当前 Java `DateTimeRecognizer` 代码只注册英/西/法/德，未注册中文；不能据总 README 的中文支持表推断 Android Java 可用 | 排除直接 Android 依赖；可参考规则或用服务端其他运行时 |
| D. Duckling 服务 | 规则体系广，支持 Time 维度和中文规则 | 需要网络/自托管服务 | App 调 HTTP | Haskell 服务、运维、延迟、隐私与离线失败；对纯本地 MVP 明显过重 | 未来服务端 NLP 候选，不用于本地 MVP |

#### 方案 A：自研受限规则

建议第一版只承诺白名单语法，例如：

- 相对日：今天、明天、后天、今晚、明早；
- 绝对日：10月3日、2026年10月3日；
- 星期：本周五、周五、下周一（“周五”遇到当天的解释需确认）；
- 时间：9点、9:30、上午九点、下午3点、晚上8点；
- 组合：明天下午3点、10月3日 9:30。

【已验证】CLDR 中文 locale 数据包括前天、昨天、今天、明天、后天等相对日词，可用作词汇覆盖参考；CLDR 48 新增 relative date-time 组合格式，但用途是本地化数据/格式化，而非从任意任务标题抽取语义。[CLDR Chinese summary](https://unicode.org/cldr/charts/44/summary/zh.html)、[CLDR 48 notes](https://cldr.unicode.org/downloads/cldr-48)

反例与必须测试的歧义：

- “明天下午讨论3点方案”：`3点` 是时间还是标题内容？
- “提醒我买8点档电影票”：8点可能是电影场次，也可能是提醒时刻。
- “3月1日”在 3 月 2 日输入时，是今年过去日期还是明年？
- “周一”在周一晚输入，是今天还是下周一？
- “12点”是中午还是午夜；“晚上12点”如何归一。
- 没有年份的 2 月 29 日；不存在的 4 月 31 日；系统日期/时区变化。

因此必须让 UI 展示解析后的日期时间标签，允许一键删除或手动覆盖；低置信/非法输入不能静默猜测。

#### 方案 B：复用中文 Java 库

【已验证】`xk-time` 声明使用 Java 8 `java.time` 重写 Time-NLP、无第三方依赖，并列出多项中文 NLP 修复；它也明确提醒 NLP 存在识别失败率。[xk-time README](https://github.com/xkzhangsan/xk-time)

【推测】它可能减少初始规则量，但不能仅凭 README 判定生产可用。进入实现前需单独完成：许可证确认、最近发布/提交活跃度、Android desugaring/资源加载、APK 增量体积、线程安全实测，以及用本项目语料跑准确率。原始 `shinyke/Time-NLP` 可作为历史实现参考，但其 GitHub 页面未显示稳定 release，不宜直接视为维护良好的移动依赖。[Time-NLP](https://github.com/shinyke/Time-NLP)

#### 方案 C/D：通用解析器

【已验证】Microsoft Recognizers-Text 顶层项目支持中文 DateTime，但 Java 实现的当前注册代码只出现 English、Spanish、French、German；历史讨论也显示中文 Java DateTime 长期未落地。因此“项目支持中文”不能等价为“Android/JVM 包支持中文”。[Java source](https://github.com/microsoft/Recognizers-Text/blob/master/Java/libraries/recognizers-text-date-time/src/main/java/com/microsoft/recognizers/text/datetime/DateTimeRecognizer.java)、[discussion #1329](https://github.com/microsoft/Recognizers-Text/discussions/1329)

【已验证】Duckling 是 Haskell 工程，官方 quickstart 运行 HTTP server；支持 Time 维度和中文规则，但不是 Android 嵌入式 Kotlin 库。[Duckling](https://github.com/facebook/duckling)

### 4.3 解析路线建议

推荐候选是 A：**受限、可解释、离线的规则解析器 + 手动选择兜底**。将解析接口隔离，先用同一套金标语料评测 A 和 `xk-time`；若后者显著提高覆盖且依赖审计通过，可在接口后替换或组合。不要把通用大模型/API 放进 MVP 核心路径，否则离线、成本、隐私、延迟和确定性都会变成新需求。

## 5. 提醒调度方案对比

### 5.1 方案表

| 机制 | 时间保证 | Doze | 权限/限制 | 适用 |
|---|---|---|---|---|
| `AlarmManager.setExactAndAllowWhileIdle()` | 近似精确、可唤醒 | 可在 idle 触发，但系统限频 | Android 12+ 精确闹钟特殊访问；耗电更高 | 用户明确要求分钟级准时的单次提醒 |
| `AlarmManager.setExact()` | 精确，但普通 exact 在 Doze 可被推迟 | 会受 Doze | 同上 | 设备活跃或不要求穿透 Doze，不是稳妥默认 |
| `setAndAllowWhileIdle()` / `set()` / `setWindow()` | 非精确；不早于触发点，可能明显延后 | 前者可在 idle 大致触发 | 无精确闹钟特殊权限 | 用户接受“约在该时刻”的省电降级 |
| `WorkManager` | 保证最终执行，不保证指定分钟 | Doze 中不运行 | 周期任务最小粒度/系统调度约束 | 闹钟触发后的长工作、重建/维护，不用于准点通知 |

【已验证】Android 官方称多数应用应优先非精确闹钟；Android 12+ 非精确闹钟一般会在触发点后约一小时内调用，但省电限制可能进一步影响。`setWindow()` 对 target 31+ 的最小窗口为 10 分钟。[Android Developers — Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)

【已验证】Doze 会把标准 `setExact()`/`setWindow()` 延到维护窗口；需在 idle 触发时使用 `setAndAllowWhileIdle()` 或 `setExactAndAllowWhileIdle()`，且每个应用不能高于约每 9 分钟一次。[Android Developers — Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)

### 5.2 推荐的能力分级

建议产品明确显示两级能力，而不是假装所有设备都能一样准：

1. “准时提醒”：已授权通知，且设备/API 需要时已授予精确闹钟访问，使用 `RTC_WAKEUP + setExactAndAllowWhileIdle()`。
2. “大致提醒”：精确权限被拒绝时，使用 `setAndAllowWhileIdle()` 或合适窗口；UI 告知可能延迟。
3. “提醒关闭”：通知权限被拒绝、通知渠道被关闭，任务仍保存但明确显示提醒不可用。

【推测】该产品属于任务/日历提醒类，可能满足 Play 对日历应用 `USE_EXACT_ALARM` 的可接受场景；但应用商店审核归类不能由技术调研保证。保守路线是先用可撤销的 `SCHEDULE_EXACT_ALARM` 并提供降级，待上架定位确认后再决定是否声明 `USE_EXACT_ALARM`。

【已验证】Google Play 将 `USE_EXACT_ALARM` 视为受限权限，允许的典型场景是闹钟/计时器，或展示事件通知的日历应用；不满足时应考虑 `SCHEDULE_EXACT_ALARM`。[Google Play exact alarm policy](https://support.google.com/googleplay/android-developer/answer/9888170)

### 5.3 权限与生命周期

- Android 13+：在用户首次打开提醒开关或创建首个提醒时解释并请求 `POST_NOTIFICATIONS`，不要无上下文首启即请求。
- Android 12+ exact：每次调度前检查 `canScheduleExactAlarms()`；授权撤销会停止应用并删除未来 exact alarms；重新授予后广播触发重建。
- 开机：监听 `BOOT_COMPLETED`，从 Room 查询未来且未完成、提醒开启的任务并重建；官方说明设备关机时所有闹钟会取消。
- 应用更新：接收 `MY_PACKAGE_REPLACED` 后重建是稳妥做法（此条为工程建议，最终 manifest 需按目标 API 验证）。
- 时区/手动改时：基于用户选定的语义重新计算未来触发时刻。需在 PRD 先定“浮动当地时间”还是“固定瞬间”。
- 任务编辑/完成/删除：先以数据库事务提交真值，再幂等取消旧 `PendingIntent` 并调度新实例；启动/恢复时可做一致性修复。

### 5.4 OEM 与电池限制风险

【已验证】AOSP Doze 本身会限制网络、JobScheduler/WorkManager 与普通 alarms；`allowWhileIdle`/exact 也有频率和电量约束。[Android Developers — Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)

【推测】部分厂商系统可能施加比 AOSP 更激进的后台限制，导致实际提醒可靠性低于模拟器/Pixel。官方 API 文档无法保证每个 OEM 的行为；必须把小米、华为、OPPO/vivo、三星等真机矩阵纳入测试，且不要默认诱导用户关闭全部电池优化。

## 6. 纯本地 MVP 与未来同步的边界

### 6.1 MVP 现在做

- Room 是唯一规范数据源，UI 只观察 repository 暴露的 Flow。
- 本地事务完成任务写入；调度器是数据库状态的派生物，不是另一份真值。
- 生成稳定全局 ID；保存 `createdAt/updatedAt`，数据库 migration 不使用破坏式迁移。
- 解析器和提醒器均通过接口隔离，便于替换、测试与未来服务化。
- 所有核心创建、浏览、完成、提醒设置在断网时工作。

### 6.2 MVP 不做

- 不加入 Retrofit/登录/token/云表，不为了“未来可能同步”提前承担安全与运维成本。
- 不实现冲突合并、增量游标、远端删除 tombstone、端到端加密、推送唤醒。
- 不把 WorkManager 当成同步存在的理由；没有远端就不需要周期网络任务。

### 6.3 真正加入同步时再做

- 服务端 ID/版本向量或 server revision、删除墓碑、幂等 mutation ID。
- outbox/change-log 与上传状态，而不是扫描全表猜变更。
- 冲突策略：字段级合并、最后写入胜出，或显式冲突；完成态和标题/日期不能默认共用一条规则。
- Room 仍作为 UI 读取的规范本地源，repository 负责网络同步后回写本地库。该边界符合 Android 官方 offline-first 指南。[Android Developers — Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)

## 7. REUSE ANALYSIS

| 组件/项目 | 🟢直接用 | 🟡借鉴 | 🔵参考 | ⚪自己写 | 原因 |
|---|---:|---:|---:|---:|---|
| Jetpack Compose | ✓ |  |  |  | 新项目原生 UI 推荐候选；是否采用仍取决于团队能力 |
| Room | ✓ |  |  |  | 范围查询、事务、迁移、Flow 与未来同步边界适配 |
| AlarmManager | ✓ |  |  |  | 用户时点提醒的系统级机制，必须包一层策略以处理权限/降级/重建 |
| WorkManager | ✓ |  |  |  | 只用于可延迟维护或未来同步；不用于准点提醒 |
| Unicode CLDR/Android locale 数据 | ✓ |  |  |  | 用于格式化与相对日期词参考，不把它误当 NLP parser |
| `xk-time` |  | ✓ |  |  | 可对照语料/借鉴规则；直接依赖前需许可证、维护、体积与 Android spike |
| `shinyke/Time-NLP` |  | ✓ |  |  | 历史中文规则参考，维护与 release 信号不足 |
| Microsoft Recognizers-Text |  |  | ✓ |  | 中文体系可参考；当前 Java DateTime 未注册中文，不能直接用于 Android 中文 |
| Duckling |  |  | ✓ |  | 服务端广覆盖备选；纯本地 MVP 不引入 Haskell 服务 |
| MVP 中文白名单解析层 |  |  |  | ✓ | 产品语法有限，可解释、可测试、隐私和体积最优 |

## 8. 对抗性检验：反例与风险

| 风险 | 证据状态 | 后果 | 缓解 |
|---|---|---|---|
| “用了 exact 就一定准时” | 已证伪 | 权限被拒、通知被禁、OEM 限制时仍失败 | 能力分级、状态可见、真机测试、降级 |
| “WorkManager 能定点提醒” | 已证伪 | Doze/系统调度导致延迟 | AlarmManager 负责时点，WorkManager 只做可延迟工作 |
| “库 README 写支持中文即可 Android 直用” | 已证伪 | Recognizers-Text Java 中文 DateTime 缺失 | 检查具体运行时源代码和金标语料 |
| “CLDR 就是 NLP” | 已证伪 | 任意句子抽取失败 | CLDR 仅供 locale/词汇；另建解析层 |
| “保存一个 epoch millis 足够” | 推测为高风险 | 跨时区/改时后用户预期不一致 | 产品先定 wall-clock vs instant；保存足够重算的信息 |
| “提醒调度成功即可” | 已证伪 | Android 13+ 通知默认关闭 | 通知权限/渠道状态单独建模并反馈 |
| “纯本地以后很容易同步” | 推测为中高风险 | 无稳定 ID/版本/迁移时成本陡增 | 现在保留稳定 ID、时间戳、repository 边界；不提前做云逻辑 |
| “规则解析越多越好” | 推测为中风险 | 假阳性会篡改任务标题/时间 | 白名单、显式标签、可撤销、低置信不猜 |

## 9. ADR 草案

### ADR-001：本地应用架构（待用户确认）

- **Context**：需快速构建 Android-only、离线可用、列表与日历状态丰富的任务应用，并为未来同步保留边界。
- **Options**：Kotlin/Compose/Room；Kotlin/XML Views/Room；Flutter/本地数据库。
- **Proposed decision**：Kotlin + Compose + ViewModel/UDF + Repository + Room；Compose 和 Room 是候选推荐，不视为已批准。
- **Consequences**：原生平台集成和测试路径直接；团队需具备 Compose 能力；跨端代码不复用，但当前没有 iOS 需求。

### ADR-002：中文时间解析（待语法范围确认）

- **Context**：需要离线从一句中文任务中提取有限日期/时间，错误解析比漏解析更危险。
- **Options**：受限自研规则；`xk-time`/Time-NLP；Recognizers-Text；Duckling 服务。
- **Proposed decision**：定义 Parser 接口，MVP 采用受限规则 + 手动选择兜底；以金标语料与 `xk-time` 做对照后再决定是否复用其实现。
- **Consequences**：确定性、隐私和体积好；长尾覆盖有限，必须在 UI 显示解析结果并允许覆盖。

### ADR-003：提醒调度（待“精确”产品承诺确认）

- **Context**：用户期望到点通知，但 Android 对精确闹钟、通知和后台执行有多层限制。
- **Options**：仅 inexact AlarmManager；exact + 授权 + inexact 降级；WorkManager。
- **Proposed decision**：用户明确设置时分时尝试 exact + allow-while-idle；无权限则透明降级；WorkManager 不承担时点提醒。
- **Consequences**：最佳提醒体验，但需权限教育、Play 合规、开机/授权恢复重建和 OEM 测试。

### ADR-004：同步边界（待路线图确认）

- **Context**：当前要求纯本地，但可能未来同步。
- **Options**：现在加入后端；完全单体直连 DAO；local-first repository 边界。
- **Proposed decision**：Room 为规范数据源，repository 隔离；现在只保留稳定 ID/时间戳/迁移能力，不实现远端与冲突算法。
- **Consequences**：MVP 简单、离线完整；未来同步仍是独立项目，不能宣称零成本。

## 10. 实施前验证门槛与测试重点

本次文档证据足以判断架构方向，未做代码 spike。实现前建议用项目测试而非临时 demo 验证：

1. 建立至少 100 条中文金标语料，按相对日、绝对日、星期、时段、非法日期、标题数字、跨年分桶，记录 precision/recall；核心目标应优先保证 precision。
2. 用可注入 `Clock`/`ZoneId` 做边界测试：23:59、月末、闰年、跨年、周起始日、改时区。
3. AlarmManager 仪器测试：exact 授权允许/拒绝/撤销、通知允许/拒绝、Doze、关机重启、应用更新、任务编辑/完成/删除。
4. 真机矩阵至少覆盖一台 AOSP/Pixel 参考机与目标中国主流 OEM；记录提醒偏差而非只测“收到/没收到”。
5. Room migration 测试与范围查询测试；测试大量历史任务下今日/月视图索引是否命中。
6. UI 测试覆盖未完成高亮、过去完成置灰、无日期/全天/有时间、空态、长标题与大字体。

若考虑直接引入 `xk-time`，这是唯一建议追加的短 spike：仅测 Android 构建兼容、APK 增量、初始化耗时和金标语料结果；在这些数据出现前不做采用决策。

## 11. 不确定项与下一轮澄清建议

1. APK 是否会上 Google Play；若会，产品定位是否能稳定满足 calendar/reminder 类 exact alarm 政策。
2. 最低 Android 版本、目标机型/OEM 与是否必须支持无 Google 服务设备。
3. 提醒可接受误差：分钟级、10 分钟窗口，还是一小时内均可。
4. 自然语言语法白名单与歧义优先级，尤其“周五”“12点”“无年份月日”。
5. 跨时区和用户手动修改系统时间后的任务/提醒语义。
6. “今日”中已完成任务的排序/折叠规则，以及无截止日期任务是否出现。
7. 未来同步是否已进入确定路线图；若没有，不应为它引入网络层。

## 参考来源

### Android / Google 官方

- [Jetpack Compose UI toolkit](https://developer.android.com/compose)
- [Guide to app architecture](https://developer.android.com/topic/architecture)
- [Data layer](https://developer.android.com/topic/architecture/data-layer)
- [Build an offline-first app](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- [WorkManager release notes / positioning](https://developer.android.com/jetpack/androidx/releases/work)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Android 14 exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Google Play exact alarm permission policy](https://support.google.com/googleplay/android-developer/answer/9888170)
- [Google Play target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)

### Unicode / 候选项目

- [Unicode CLDR Chinese locale summary](https://unicode.org/cldr/charts/44/summary/zh.html)
- [Unicode LDML Part 4: Dates](https://cldr-smoke.unicode.org/spec/main/ldml/tr35-dates.html)
- [CLDR 48 release notes](https://cldr.unicode.org/downloads/cldr-48)
- [xk-time](https://github.com/xkzhangsan/xk-time)
- [shinyke/Time-NLP](https://github.com/shinyke/Time-NLP)
- [Microsoft Recognizers-Text](https://github.com/microsoft/Recognizers-Text)
- [Recognizers-Text Java DateTimeRecognizer source](https://github.com/microsoft/Recognizers-Text/blob/master/Java/libraries/recognizers-text-date-time/src/main/java/com/microsoft/recognizers/text/datetime/DateTimeRecognizer.java)
- [Recognizers-Text discussion: Chinese DateTime in Java](https://github.com/microsoft/Recognizers-Text/discussions/1329)
- [Duckling](https://github.com/facebook/duckling)
