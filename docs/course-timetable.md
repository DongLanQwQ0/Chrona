# 本地课表

入口为首页「今天的安排」。TimetableActivity 复用 UiStyle、主题色、安全区与玻璃浮窗；TimetableGrid 使用原生可访问文本控件，TimetableLayout 负责实际时间映射与冲突分列。空闲时段压缩，课程最小高度保留触摸空间；大字体和课程冲突时允许横向滚动。

TimetableParser 使用 [biweekly 0.6.8](https://github.com/mangstadt/biweekly) 读取 iCalendar，依赖 vinnie 2.0.2，均为 MIT 许可；上游 JAR 包含对应许可证和嵌入代码声明。未使用 jCal，排除 Jackson 依赖。

支持有限的日/周/月/年重复、BYDAY、COUNT/UNTIL、RDATE/EXDATE、单次修改和取消、UTC/本地/命名时区、全天与跨日事件。支持简单无限日/周规则，并在详情标明重复规则；复杂无限规则、多个 RRULE、EXRULE、范围修改会明确拒绝，避免静默遗漏。

按标题、地点、星期和开始/结束分钟合并日期，保留不同日期的备注。周次不切换；跨日课程按日期拆分，原始起止时间保留在详情。相同星期时间但不同日期的课程仍可能分列，这是全部课程汇总视图的预期行为。

上限：UTF-8 文件 1 MiB、5000 个原始事件、30000 次展开、512 个展示色块，解析预算 10 秒。导入先预览再原子替换私有 course-timetable.json；原始 ICS、导入时间和展示时区一起保存。完整备份预检课表，并在恢复失败时尝试还原原课表。

回归入口：`python checks/timetable_check.py`，编译实际解析与布局代码，使用 Gradle 缓存中的上游依赖。手机上的文件选择、旋转、主题、大字体、网格触摸及浮窗显示仍需要设备验收。
