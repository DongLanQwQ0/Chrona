# 动效实现与性能测量

视觉原则见[UI 规范](ui-style-contract.md)，具体动效按[交互基线](ui-interaction-baseline.md)维护。历史调参不再列为当前指导。

## 帧驱动和渲染

选择栏由 `View.postOnAnimation` 跟随系统 vsync，使用实际时间积分，没有固定 60fps 上限。`DockSpring` 的 120Hz 子步用于积分精度，不是显示帧率限制。设备刷新率与实际呈现帧率分别测量。

`UiStyle.AcrylicSurfaceDrawable` 复用像素输入不变的嵌套亚克力或非亚克力底板；尺寸、样式与状态变化时失效。独立壁纸采样仍实时更新，高光和文字保持独立硬件节点。不要将整栏截图缓存以降低重绘成本。

列表局部位移、展开与背景采样保持同一视图身份和真实位置；Activity 转场交给 Android，页内和浮层动效由公共组件管理。性能改动先测量具体热点，不能改变已确认效果来掩盖卡顿。

## 选择栏的统一用法

产品中正式称为“选择栏”，原生实现复用 DockNavigationLayout。顶部筛选、日历模式、任务类型及弹窗选项使用同一控件、高光物理和按压反馈。滚动弹窗内采用横向选项，选择时不重建整组视图；禁用状态不接收拖动或点击。标签按真实内容宽度与字体环境布局，保持单行可读；不靠过度缩小文字适配容器。

### 容器必须容纳完整动画

独立选择栏使用 `dock.motionHost()` 容纳动画。紧凑详情和筛选使用 `fullWidthMotionHost()` 保持轨道满宽，中间容器允许高光自然溢出；绘制空间使用父容器已有留白，滚动视口保留真实外边界。不得通过高光框内限幅、向内挪动、缩窄轨道或增加字段永久行高掩盖裁切。验收覆盖首尾长按、拖出松手、快速甩动、窄屏及大字体。

选项行关闭 `LinearLayout` 的基线对齐。不同长度标签自动调整字号后仍共享单元格中心，不能让文字基线将末项及其高光推离选择栏。

筛选选项按文字长度分配宽度，日期自定义入口简称“自选”；不同宽度之间的高光按当前视觉位置插值，松手速度按所在区间换算，保持连续。

日程清除筛选时，筛选摘要连同底部间距以高度和透明度收起，下方选择栏随布局返回。快速反向操作从当前画面接续；初次创建、恢复页面及关闭动画时直接显示终态。日期范围的左右箭头分别切换前后区间，中间点击修改日期，横滑继续有效。


## 测量与验收

区分 [Android 官方定义的 TTID 与 TTFD](https://developer.android.com/topic/performance/issues/launch-time)：前者是初始显示，后者通过 `reportFullyDrawn()` 报告主要内容已就绪。首页加载提示不报告完成；启用系统日程时等待当前窗口的查询结束与内容绘制。壁纸作为装饰独立加载。

`ChronaStartup` 的 `activity_ms` 只从 Activity 实例计时，不能代替包括进程启动成本的系统 TTFD。系统日志与应用就绪标记都存在时，脚本才判定该样本可用于验收；失败或缺失的样本不会算作达标。脚本不安装、不清数据、不更改引导和权限配置。

先在测试设备安装相同签名的当前版本，结束所有解析工作并完成引导/权限配置。以下命令会强制停止应用进行冷启动，不能用于正在解析真实任务的设备：

```powershell
python checks/measure_startup.py --adb 'F:\Android\Sdk\platform-tools\adb.exe' --serial '设备序列号' --samples 10 --target-ms 1000 --allow-force-stop --output build/startup-device.json
```

分别检查无壁纸/大壁纸、少量/较多历史、系统日程开关、首次安装/普通冷启动、热恢复。报告每次数据、中位数、P90、最大值；全部样本系统 TTFD ≤1000 ms 才表示该设备和条件下本次目标达成。设备性能、系统调度、首次数据库迁移与日历 Provider 耗时仍可能使结果超过目标；不要将一台设备的成绩推广为所有设备保证。

动画验收需覆盖：引导快速连点与旋转、打开/关闭及嵌套弹窗、详情未修改/有修改/保存中返回、Dock 短按/长按/拖动/取消、主体横滑、图片首次显示与返回缓存、暗色/自定义色/壁纸，以及系统动画缩放为 0。Activity 使用 [系统预测返回](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)，需分别验收拖动、取消、提交及按钮返回；拦截未保存编辑时以数据保护为先。

普通返回/筛选的数据刷新和部分非首页页面仍沿用同步、有边界的查询；本轮没有改成全应用异步数据架构。实际耗时需要先取得设备剖析结果，再针对仍然超标的路径优化。


## 检查与证据入口

- `checks/dock_navigation_check.py`：生产 Dock 的队列、手势和几何逻辑。
- `checks/dashboard_dock_chrome_check.py`：导航与快捷操作协调。
- `checks/acrylic_translation_check.py`：壁纸采样与位移。
- 原生 `DeviceUiChecks` 的 `acrylic_raster`：位图改写次数、录制 CPU 耗时及失效；不代表整体 GPU 帧率。
- `checks/lifecycle_motion_check.py`、`checks/startup_metrics_check.py`：生命周期与启动数据解析。

各版测量条件、结果和未覆盖项统一见[验证记录](validation-history.md)，避免在规范中累积过时成绩。
