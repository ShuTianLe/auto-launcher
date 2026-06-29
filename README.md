<p align="center">
  <img src="logo_android_auto_task_cute_clean.svg" width="168" alt="Auto Launcher Logo" />
</p>

# Auto Launcher

`Auto Launcher` 是一个 Android 定时任务应用，用来在指定时间自动亮屏、打开目标应用、停留一段时间，再回到 `Auto Launcher` 并锁屏。

目前支持：

- 多任务管理
- 24 小时制触发时间
- 随机触发窗口
- 每天 / 中国工作日 / 指定周几
- 每个任务可设置多个一次性跳过日期
- 未来 7 天任务时间表预览
- 中国节假日自动同步
- 远程 Web 控制台查看和辅助修改任务
- 执行日志
- 权限检查与引导

## 下载

- 当前版本：[`v1.2.0`](https://github.com/ShuTianLe/auto-launcher/releases/tag/v1.2.0)
- 安装包：[`AutoLauncher-v1.2.0.apk`](https://github.com/ShuTianLe/auto-launcher/releases/download/v1.2.0/AutoLauncher-v1.2.0.apk)
- 历史版本：[`Releases`](https://github.com/ShuTianLe/auto-launcher/releases)

## 远程控制台

- 控制台地址：`https://auto-launcher.19930630.xyz`
- 设备码：在 Android App 的“权限与系统设置”页查看
- 控制台代码位于 `console/`
- 任务数据以 Android App 本地 Room 为准；Web 控制台查看 App 上报快照并下发待执行命令
- 手机断网或控制台不可用时，已保存的本地任务仍由 Android App 独立执行

## 功能

- 支持创建多条任务，按下一次触发时间排列
- 触发时间使用 24 小时制
- 随机窗口按“基准时间后 0..N 分钟”计算
- 支持每天、中国工作日、指定周几三种重复方式
- 支持给单个任务添加多个一次性跳过日期，适合临时请假或单日暂停
- 支持查看单个任务未来 7 天的执行/跳过预览
- 目标应用可从本机已安装应用中选择
- Web 控制台可远程创建任务、启停任务、添加/删除跳过日期
- 任务执行后自动回到 `Auto Launcher`
- 设备管理器开启后可在任务结束后锁屏

## 使用场景

- 公司使用飞书等支持“极速打卡”的工具时，可以把一台 Android 手机固定放在公司，保持应用和权限设置完成后，在指定时间自动拉起飞书执行极速打卡流程。

## 使用

1. 安装并打开应用
2. 在“权限”页开启关键权限：
   - 精确闹钟
   - 忽略电池优化
   - 设备管理器
   - 通知
   - 辅助功能
3. 新建任务
4. 设置触发时间、随机窗口、停留时长、目标应用和重复规则
5. 保存后等待任务执行

## 中国工作日

“中国工作日”不是简单按周一到周五判断。

应用会处理：

- 法定节假日
- 调休上班日
- 自动同步节假日数据
- 网络异常时优先使用本地缓存

如果自动同步失败，也可以手动触发同步。

## 执行流程

一次任务的默认执行顺序是：

1. 亮屏
2. 拉起 `Auto Launcher`
3. 等待前台稳定
4. 启动目标应用
5. 停留指定时长
6. 回到 `Auto Launcher`
7. 锁屏

## 限制

- 不同 ROM 对后台拉起应用的限制不同
- MIUI / HyperOS 在某些情况下可能弹出确认框
- 真正锁屏依赖设备管理器
- 如果设备本身设置了密码、图案或指纹，自动解锁会受到系统限制

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- Foreground Service
- Exact Alarm
- Next.js 远程控制台
- SQLite 命令队列和设备快照

## 本地构建

环境要求：

- JDK 17
- Android SDK 35
- Build Tools 35

构建命令：

```bash
./gradlew assembleRelease
```

控制台本地运行：

```bash
cd console
npm install
npm run dev
```
