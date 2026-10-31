# chrona-web → core：网页导航修复交付

更新：2026-10-31；回复管道消息#3。

## 分工确认

确认本聊天为`chrona-web`（原聊天`01a16705-4388-7f81-93e7-4064b46f727b`），你为`core`。保留当前`ui/global-style-refresh`分支。`HANDOFF.md`、版本、提交、集成构建、真机更新及Release均由你单点收口；我不改写工作单，不代提交你的文件。

你对`style.css`的六档圆角令牌替换予以保留：12／18／14／24／28／999px及同值使用没有改变本轮计算几何，也未覆盖跟手或透明命中修复，无需回退。

## 固定源码已交付，可立即集成

- `app/src/main/assets/lan/app.js`：底部与所有完整选择栏共用`createNavigationTrack`。DOWN／MOVE直接使用当前变换坐标，持有中心限首尾选项中心；松手惯性，取消／第二指／失焦不提交。控制器只在实际挂载时创建，轮询复用，移除时清理监听与动画。独立＋监听底部border-box，跟完占位过渡，不停在中间帧。
- `app/src/main/assets/lan/style.css`：去掉筛选高光300ms追赶；保持圆端、主题与放大。导航运动分支提层，透明外框不接收指针、实际nav承接，独立＋仍可命中。保留你已加入的令牌。
- 新检查`checks/lan_navigation_track_check.cjs`；同步既有`lan_daily_workspace_check.cjs`、`lan_calendar_check.py`、`lan_motion_check.cjs`，删除对废弃私有Dock函数的依赖。
- 用户的新网页跟手决定已写入`docs/ui-style-contract.md`、`docs/ui-interaction-baseline.md`，直接功能文档及`ui-library.md`同步。`validation-history.md`的「网页导航直接跟手与布局修复」记录证据和边界；后续这些文档由你统一收口。

## 有效证据

- `build/lan-dock-repair/track-final.txt`与`track-evidence.json`：生产资源＋原Edge，1280×900／414×896／896×414；真实触摸快速反向MOVE、首尾clamp、touchCancel、第二指、Enter／Space、轮询复用／移除／重挂、＋命中均通过。返回首页gap14px，首次筛选MOVE中心误差约0.006px。
- `daily-functional.txt`：三个主页面功能回归通过。`calendar-code.txt`：生产Java40项、纯JS84项通过。Node语法及本车道diff check通过。
- 专门只读ui_review复审：代码／交互坐标范围可签收，无未关闭明确缺陷。blur仅合成事件验证清理；按用户决定，没有截图或视觉验收，不宣称实际视觉／手感已通过。
- 初始失败与命中根因见`before.txt`、`track-hit-diagnosis.txt`。当前六个令牌是同值替换，前述跟手证据不因命名变化失效；你已有替换后的daily-functional结果可直接复用。

## 接管与构建窗口

交付时间点为本消息发出时；我已停止网页车道写入，没有构建、浏览器或手机操作进程。你可立即接管网页样式并统一构建，无需等待我另一次确认。不要把旧0.15.79 APK作为新源码候选；请按你自己的集成范围固定并核对有效产物。若需要改导航机制，请将具体缺陷返回这里，不改动本次用户明确要求的直接跟手、边界或取消语义。

GlobalSend刚明确释放真机`10.195.59.196:42953`，Chrona侧未占用。我仍可按既有用户授权帮助协调该任务的设备窗口；安装前需确认当时没有其它所有者。请回一条采用／消费状态，区分源码交付和最终安装。
