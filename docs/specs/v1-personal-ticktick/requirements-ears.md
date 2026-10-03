# 产品与交互需求（EARS）：个人 TickTick APK v1

<!-- 初稿日期: 2026-10-02 -->

## REQ-01 今天与已过期 [Must Have]

1. WHEN 用户打开任务主页 THE SYSTEM SHALL 显示“已过期”和“今天”两个可切换区域。
2. WHEN 未完成任务的最后计划自然日早于今天 THE SYSTEM SHALL 将任务显示在“已过期”。
3. WHEN 逾期任务显示 THE SYSTEM SHALL 保持标题高亮并仅将原日期或时间标红。
4. WHEN 今天的任务完成 THE SYSTEM SHALL 将其置灰并沉到今天列表底部。

## REQ-02 周与月视图 [Must Have]

1. WHEN 用户进入周视图 THE SYSTEM SHALL 用七个日期格铺满一屏内容区。
2. WHEN 某日任务超过格内容量 THE SYSTEM SHALL 显示“还有 N 项”。
3. WHEN 用户进入月视图 THE SYSTEM SHALL 显示紧凑月历和两列日期任务卡。

## REQ-03 创建编辑删除 [Must Have]

1. WHEN 用户点击加号或现有任务 THE SYSTEM SHALL 打开半屏创建或编辑弹层。
2. WHEN 用户删除普通任务 THE SYSTEM SHALL 请求确认并在成功后提供短暂撤销入口。

## REQ-04 智能日期 [Must Have]

1. WHEN 标题中存在受支持的日期时间表达 THE SYSTEM SHALL 展示可撤销的识别结果。
2. WHEN 用户手动选择日期时间 THE SYSTEM SHALL 以手动值覆盖自动识别值。
3. IF 解析结果存在歧义 THEN THE SYSTEM SHALL NOT 静默采用低置信日期。

## REQ-05 日期类型 [Must Have]

1. WHEN 用户创建单日无时间任务 THE SYSTEM SHALL 在目标日期全天显示且不自动设置提醒。
2. WHEN 用户创建跨天任务 THE SYSTEM SHALL 在开始日至结束日每天显示同一任务。
3. WHEN 跨天任务在任意覆盖日期完成 THE SYSTEM SHALL 将整个任务标为完成。
4. WHEN 结束日结束且任务未完成 THE SYSTEM SHALL 在次日将其显示为已过期。

## REQ-06 提醒 [Must Have]

1. WHEN 用户设置准时提醒 THE SYSTEM SHALL 展示提醒是否可准点、可能延迟或不可用。
2. IF 通知权限不可用 THEN THE SYSTEM SHALL 仍保存任务并明确显示提醒不可用。

## REQ-07 重复任务 [Must Have]

1. WHEN 用户设置重复 THE SYSTEM SHALL 支持每天、工作日、每周指定星期、每月指定日期和指定次数。
2. WHILE 当前实例未处理 THE SYSTEM SHALL NOT 允许操作未来重复投影。
3. WHEN 当前实例完成或跳过且仍有次数 THE SYSTEM SHALL 生成下一可操作实例。
4. WHEN 用户编辑或删除重复实例 THE SYSTEM SHALL 提供“仅本次”和“本次及以后”。

## REQ-08 完成与恢复 [Must Have]

1. WHEN 用户完成任务 THE SYSTEM SHALL 在相关视图同步更新状态。
2. WHEN 用户恢复普通任务 THE SYSTEM SHALL 恢复其原日期和原排序位置。
3. IF 重复任务的下一实例已经被处理 THEN THE SYSTEM SHALL 阻止撤销前一实例完成并解释原因。

## REQ-09 模糊搜索 [Must Have]

1. WHEN 用户输入搜索文本 THE SYSTEM SHALL 实时匹配任务名称。
2. THE SYSTEM SHALL 支持中文连续或非连续字符、英文大小写忽略、拼音全拼和拼音首字母。
3. WHEN 搜索结果显示 THE SYSTEM SHALL 将未完成结果优先、已完成结果置灰、逾期日期标红。

## REQ-10 联网数据 [Must Have]

1. WHEN 用户改变任务 THE SYSTEM SHALL 将变化提交至联网数据库。
2. IF 网络不可用 THEN THE SYSTEM SHALL 保留本地变化并显示等待同步。
3. WHEN 网络恢复 THE SYSTEM SHALL 自动提交待同步变化。

