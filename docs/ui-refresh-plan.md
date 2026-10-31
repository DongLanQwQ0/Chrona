# UI 样式统一计划（分支 ui/global-style-refresh）

本文件是 core 的执行清单：审查结论、车道划分、验收方式。规范本体仍是 `ui-style-contract.md` 与 `ui-interaction-baseline.md`，本文件只记录本轮做什么、为什么、怎么验证。

## 原则

1. 不改已确认动效参数、公共组件语义与检查断言。
2. 优先消除同一含义的多种实现（令牌漂移、重复覆盖、逐页补丁），再做局部观感调整。
3. 每项改动必须能指出违反的规范条款或可证的实现缺陷；个人偏好不作为唯一理由。
4. 视觉最终由用户验收；本轮提供源码级与渲染级证据，不宣称视觉通过。

## 车道

| 车道 | 路径 | 负责人 |
| --- | --- | --- |
| 设计系统 | `UiStyle`、`UiSurfaces`、`GlassBackdropView`、`DockNavigationLayout`、`res/values*`、`res/layout` | core + 执行代理 |
| 一级页面 | `DashboardActivity`、`MainActivity` | 待审查结论 |
| 详情与日历 | `TaskDetailActivity`、`CalendarOverviewPage` | 待审查结论 |
| 二级页面 | `TimetablePage`、`Classroom*`、`AppearanceActivity`、`SchedulePrompt`、小组件 | 待审查结论 |
| 网页 | `assets/lan/*` | chrona-web（本轮修复期间） |

## 已知证据

- `build/css-debt.md`：网页 CSS 629 规则块、41 组重复选择器、43 条可证被覆盖声明、border-radius 18 种取值。
- 原生颜色集中在 `UiStyle`；字号 11–26sp 共 14 种取值，需按规范抽齐。
- `build/pipe-check/result.txt`：并发写入与租约可用。

## 待填

审查结论返回后补：每车道的问题清单（P0/P1/P2）、改动项、验证命令。
