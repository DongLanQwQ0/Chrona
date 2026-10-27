# 公共 UI 库

修改页面时，先确定页面类型，再选已有组件；通用外观见[UI 规范](ui-style-contract.md)，必须保留的动作与数值见[交互基线](ui-interaction-baseline.md)。本页只说明实现职责和接入方式，不复制尺寸或动效参数。

## 从职责找到唯一入口

源码位于 `app/src/main/java/com/donglan/chrona/`。

| 需要完成的事 | 入口与职责 |
| --- | --- |
| 读取主题、圆角，设置文字／输入／按钮／卡片样式 | `UiStyle`；主题值来自 `ThemeStore` 与 `CornerScale`，外观滑块在原控件上更新 |
| 采样与绘制亚克力、匹配涟漪遮罩、刷新移动中的表面 | `UiSurfaces`；负责矩阵、观察器、位图缓存与绘制状态，不负责页面导航或业务状态 |
| 选择、确认、文字编辑、日期／时间和自定义浮层 | `UiDialogs`；统一菜单、操作行、窗口开合、多层模糊与背景恢复 |
| 内容呈现、列表切换、局部交叉淡化与取消恢复 | `UiMotion`；系统减少动画时落到正确终态，Activity 转场继续交给 Android |
| 普通按压与组合操作反馈 | `PressFeedback`；Dock 继续独立管理按压、拖动与惯性 |
| 按压／焦点绘制优先级与固定导航 | `InteractionLayers`；提升祖先绘制分支，保留布局顺序并恢复原 Z 值；由公共反馈与 Dock 接入 |
| 设置页结构／安全区 | `SettingsPageLayout`；底层安全区接口保留在 `UiStyle` |
| 横屏／短窗口工具与正文联动滚动 | `CollapsingPageLayout`；使用 `SettingsPageLayout.workspaceContent` 后明确登记 `setScrollingBody`，保持现有滚动监听和页面实例 |
| 工作区标题／共享操作胶囊 | `WorkspacePageHeader`、`HeaderActionGroup` |
| 日期区间／滚轮选择器 | `DateIntervalBar`、`GlassDateTimePickerView` |
| 选择栏／底部导航 | `DockNavigationLayout`、`DockSpring`、`DashboardDockChrome` |
| 页面背景／短时结果消息 | `GlassBackdropView`、`Feedback` |
| 学校课表／课堂周表 | `TimetableGrid`、`TimetableLayout`；同一绘制及冲突分列，课堂仅有开始时间时使用开始标记，不推算结束 |

二级页面使用`SettingsPageLayout.content/header/show`。自带独立滚动视口、需要固定标题的阅读页改用`showFixed`承载内容，背景与安全区仍由同一公共实现负责；浮动操作通过`InteractionLayers.pin`保持高于可选择正文的焦点分支。

共享操作组中的条件入口使用`HeaderActionGroup.setActionVisible`，按钮和相邻分隔共同连续收宽；标题栏用`WRAP_CONTENT`包裹动作组，避免隐藏后留下固定宽度空槽。页面只决定入口所属视图，动画、触摸停用和脱离清理由公共组维护。

操作组几何变化由 `HeaderActionGroup` 通知 `UiSurfaces` 使高光遮罩缓存失效；固定宽度的剩余单元也按当前父表面完整圆角裁切，不在页面替换圆形遮罩。

短窗口的固定工具组使用`SettingsPageLayout.toolsViewport`，限制工具区占用并保留可滚动操作；`SchedulePage`只给正文增加底部浮动导航的末尾留白，不给工具视口重复加空白。表单的键盘和系统安全区由滚动视口外层承担，焦点按真实可见高度滚动。

工作区的短窗口由`CollapsingPageLayout`接收纵向嵌套滚动，先移出正文上方工具，再把同等高度还给正文；回到正文开头后下拖恢复。`FreeCalendarScrollView`保留自身双向滚动、捏合及周切换，同时把纵向预消费和窗口偏移交给该父级。竖屏普通高度不折叠，键盘编辑时保留输入工具，不替换既有滚动监听。日期／时间轮使用`UiDialogs.fitPicker`按可见高度扣除标题与操作行，窄高时减少可见轮行而非缩小所选值字体。

`UiMotion.enterPage`用于首次子页面呈现，方向`swap`用于工作区子视图和分页；轮询不重播入场。`DateIntervalBar`保持实例并负责箭头、滑动与点击选择入口。`ImageSlidePager`负责有界手势、当前／相邻帧缓存、原图比例及连续切页；`PanHandler`先消费放大图片内部平移，仅把横边余量交给翻页，方向仲裁仍按真实手指方向。权限和来源由调用页提供；全部图片入口共用`AttachmentViewerActivity`的焦点缩放、保存及横屏沉浸布局，媒体与操作安全区分离，不复制入口专属查看器。

页面调用样式和组合组件，不自行创建另一份亚克力 Drawable、弹窗层级管理或快照切换。渲染状态由 `UiSurfaces` 管理；浮层登记窗口模糊根，渲染层无需依赖浮层生命周期实现。
`CalendarVisibilityToggle` 只管理开关图标及连续颜色变化；按压通过 `UiStyle.pressable` 使用 `UiSurfaces` 的公共填充遮罩，随 `HeaderActionGroup` 胶囊边界裁切。调用方不叠加专用圆形涟漪或选中底色。

浮层背景缩放状态覆盖进入、持有和退出全过程；退出中重开复用最初基准，最终恢复后释放状态，视图脱离时清理。该边界由公共实现处理，调用方不补延时或手动重置缩放。

日历初始视口、候选状态文案、课堂选择等业务专用实现继续留在各自功能中，不因名称含 View／Motion 就合并进通用库。短小且职责独立的纯计算类也无需为了减少文件数合并。

## 常用接入

### 选择值后更新业务状态

```java
UiDialogs.choiceDialog(activity, "视图", labels, selected, index -> {
    selected = index;
    refreshContent();
});
```

菜单负责关闭与选中外观，调用方负责保存值和刷新受影响内容。选项行使用 `UiDialogs.addMenuItem`，不叠加按钮背景或缩放。

### 组装包含长内容的浮层

```java
Dialog dialog = new Dialog(activity);
LinearLayout panel = UiDialogs.dialogPanel(activity, "详情");
UiDialogs.dialogBody(panel, body);
UiDialogs.showFloatingDialog(dialog, panel);
```

长正文放进公共可滚动区域；需要始终可达的操作放在其外。统一入口负责窗口表面、开合和多层恢复，不再单独设置另一套背景缩放或延迟关闭。

### 内容替换与表面更新分开处理

需要交叉淡化的局部重建使用 `UiMotion.swap(container, rebuild)`；背景和内容分层时使用三参数形式。生命周期／焦点变化通过 `UiMotion.finishSwap` 收敛；数据刷新保留内容身份时不启动重建或入场。

仅表面随位置变化时，使用 `UiSurfaces.collectAcrylicSurfaces` 收集受影响节点，或 `invalidateAcrylicSurfaces` 刷新可见表面，不截图重建整页。组件自身负责静止时停止无效刷新。

## 整理和扩展的门槛

1. 先读目标实现、全部直接调用者及对应检查，明确哪种重复或职责混杂值得消除。
2. 合并同一机制，保留不同业务含义；内部入口迁移同步完成生产、原生夹具、离线替身与当前文档，不留下旧转发别名。
3. 保留主题、五档圆角、壁纸／亚克力开关与强度、缓存、完整动作、点击和无障碍语义、减少动画及生命周期恢复。不借整理修改已确认参数。
4. 按影响验证实际生产方法、构建、渲染和关键交互；菜单、嵌套浮层、日期选择、页面切换、动态表面分别使用适合的证据，不能互相替代。
5. 固定候选后由专门 UI 审核代理只读检查。源码职责检查不代替实际呈现；缺口记录在唯一[当前工作单](../HANDOFF.md)。

新增回归检查加入现有 `checks/run_checks.py`，沿用已有测试入口；不为本次整理另建 UI 框架、通用配置或独立验证平台。

职责与调用迁移、共享操作行见 `checks/ui_library_check.py`；浮层快速反向、嵌套、减少动画及脱离清理见 `checks/dialog_scale_check.py`。原有菜单、表面采样、按压、选择栏与内容切换检查继续保留。
