# Auto Launcher

一个面向 Android 的定时任务应用。

它的目标很直接：在指定时间点附近自动亮屏，拉起目标 App，停留一段时间，然后回到 `Auto Launcher` 并锁屏。应用支持多任务时间轴、中国工作日识别、随机触发窗口、执行日志和权限检查，适合放在专用设备上长期运行。

## 下载

- 最新安装包：[AutoLauncher-latest.apk](https://github.com/ShuTianLe/auto-launcher/raw/main/apk/AutoLauncher-latest.apk)
- 当前版本：`v1.0.5`
- 历史包目录：[apk/](https://github.com/ShuTianLe/auto-launcher/tree/main/apk)

如果后续启用了 GitHub Releases，也可以直接在 Releases 页面下载打包好的 APK。

## 核心能力

- 多任务时间轴：可以创建多条任务，按下一次触发时间排序。
- 24 小时制触发时间：直接设置明确时间点，例如 `09:00`。
- 随机窗口：在设定时间点后 `0..N` 分钟内随机触发。
- 重复规则：
  - 每天
  - 中国工作日
  - 指定星期几
- 中国节假日同步：支持自动同步和手动同步，使用本地缓存兜底。
- 执行闭环：
  - 亮屏
  - 拉起 `Auto Launcher`
  - 等待前台稳定
  - 启动目标 App
  - 停留指定时长
  - 回到 `Auto Launcher`
  - 锁屏
- 执行日志：记录每次任务的时间、步骤、失败原因和运行状态，便于排查 ROM 行为差异。

## 适用场景

- 自用侧载设备
- 长期放置在固定页面的专用安卓机
- 无密码锁屏或可接受设备管理器锁屏方案的设备
- 需要按工作日自动拉起企业应用、办公应用、签到类应用的场景

## 使用方式

1. 安装 APK 并打开应用。
2. 在“权限”页开启关键权限：
   - 精确闹钟
   - 忽略电池优化
   - 设备管理器
   - 通知
   - 辅助功能（可选，但建议开启）
3. 新建任务：
   - 选择触发时间
   - 设置随机窗口
   - 设置停留时长
   - 选择目标 App
   - 选择重复规则
4. 保存任务后，应用会自动安排下一次触发。
5. 到点后，应用会先回到 `Auto Launcher`，再尝试拉起目标 App；任务结束后回到 `Auto Launcher` 并锁屏。

## 中国工作日说明

应用支持“中国工作日”规则，不只是简单的“周一到周五”。

- 会识别法定节假日
- 会识别调休上班日
- 默认自动同步节假日数据
- 自动同步失败时，可手动点击“立即同步中国节假日”
- 本地已有缓存时，即使网络异常也会优先使用缓存

当前仓库对应的数据镜像由服务器下发，避免中国大陆网络访问外部源不稳定的问题。

## 日志能看到什么

为了排查 MIUI / HyperOS 等 ROM 的后台限制，日志里会记录：

- 计划触发时间和实际开始时间
- 任务是否顺延执行
- 屏幕是否点亮
- 是否处于锁屏状态
- 目标 App 在启动前是否已经存活
- `Auto Launcher` 当前进程重要性
- 亮屏、回前台、启动目标 App、回到 `Auto Launcher`、锁屏等每一步执行记录
- 同步节假日时的成功、失败、超时与失败原因

## 已知限制

- 不是所有 ROM 都允许后台自动拉起其他 App。
- MIUI / HyperOS 可能在某些状态下弹出“是否允许打开目标应用”的确认框。
- 即使同一台设备，同一条任务，不同时间触发时系统判定也可能不同。
- 真正锁屏依赖设备管理器；如果未开启，只能回到 `Auto Launcher`，不能强制锁屏。
- 如果设备设置了密码、图案、指纹等安全锁，自动解锁能力会受到系统限制。

## 技术实现

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- Foreground Service
- Exact Alarm
- Device Admin

主要模块：

- `TaskScheduler`：计算下次触发时间并注册精确闹钟
- `TaskExecutionService`：执行亮屏、前台切换、拉起目标 App、回应用、锁屏
- `HolidayRepository`：同步并缓存中国节假日数据
- `MainActivity`：任务、日志、权限、编辑页 Compose UI

## 目录说明

- `app/`：Android 应用源码
- `apk/`：对外下载的 APK 文件
- `.github/workflows/`：GitHub Actions 自动构建流程
- `logo_android_auto_task_cute_clean.svg`：项目图标源文件

## 本地构建

需要环境：

- JDK 17
- Android SDK 35
- Build Tools 35

构建命令：

```bash
./gradlew assembleRelease
```

如果需要本地签名发布包，可以在构建后使用 `zipalign` + `apksigner` 对输出 APK 进行签名。

## GitHub 自动构建

仓库已经包含 GitHub Actions 工作流：

- 推送 tag 时自动构建 APK
- 创建 GitHub Release 时自动上传 APK
- 如果配置了签名 secrets，可产出签名 release 包
- 如果没有配置签名 secrets，则回退为 debug APK，保证仍然有可下载产物

建议配置以下 GitHub Secrets：

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

## 说明

这个项目当前主要面向“专用机、自用机、侧载机”场景，不以应用商店合规为第一目标。
