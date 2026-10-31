# 当前交付：全仓 UI 样式统一（core）+ 网页导航修复收尾（chrona-web）

分支 `ui/global-style-refresh`，工作树同一份、未提交。本文件是唯一工作单，写入方 `core`。

## 已构建并部署

- `build/ui-refresh/Chrona-ui-refresh-0.15.79+241.apk`：release，0.15.79 / 241，已覆盖安装到真机 V2527A。
- 真机截图：`build/ui-refresh/{home-zh,schedule-headings,phone-inbox,detail-final,calendar-month2,calendar-week}.png`。

## 本轮修复：首页切换「系统日程」导致整页抽搐

- 根因：`DashboardActivity.applyCompletionRows()` 给 `TransitionManager` 挂了 `Fade OUT + ChangeBounds + Fade IN`，作用对象是整块 `content`。任何一次数据变化（系统日程开关、轮询）都会把整页（含标题行）淡出再淡入一次，读作「抽搐」。切换开关并不触发 `render()`，所以不是入场动画。
- 修复：只保留 `ChangeBounds`。保留的卡片继续按位移连续变化，新增行直接出现，不再整页洗白。
- 证据：开关确实触发了行刷新（`拾时` 来源 0→1、系统节点 1→2）；构建通过，`completion_card_motion_check`／`dashboard_switch_check`／`section_history_check`／`review_badge_check` 全过。
- 现场阻塞：真机当前停在 `LanSettingsActivity` 的「浏览器授权」页（10.171.176.71 的 Edge 配对请求），需要用户决定，未代点；因此本次未做逐帧视觉复核。

## 前序成果

- UI：标题区 24sp／64dp／8dp；`cardInset` 16/12；记录间距 8dp；`emptyState`；类型筛选可见化；日标题 15/13sp；日历圆角 `RADIUS_CHIP`、标题行 48dp、内容轴统一；详情页动作连续过渡、紧凑动作、中文日期。
- 检查：full 套件 120/121，仅 `data_integrity_check` 因临时库文件占用＋快照缺表失败（数据层夹具问题，未改）。

## 唯一下一步

1. 用户处理浏览器授权后，复核首页开关的过渡手感（逐帧或主观）。
2. `data_integrity_check` 快照夹具。
3. 课表／课堂标题行收敛。

## 边界

未改版本号、未建 Release；真机仅安装与浏览，未代用户授权、未写入用户数据。
