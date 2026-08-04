# Biu Car

Biu Car 是面向旧款 Android 车机的 Bilibili 音频客户端。项目与 `android-app`、`desktop-app` 完全独立，手机端只作为 API 与业务规则参考。

## 目标设备

- 验收 AVD：`CASKA_1024x600`
- 设备：`emulator-5554`
- Android 8.1 / API 27 / arm64-v8a
- 1024×600，160 dpi，约 1.5GB 内存

## 首版范围

- TV 接口扫码登录，登录成功后以 `/x/web-interface/nav` 复核账号态。
- 首页仅展示用户选择的关注 UP 投稿，不配置时保持空白。
- 首页 UP 来源分为“我的关注”和“搜索添加”两个子 Tab；名称搜索使用 WBI 用户搜索，输入纯数字时按 UID 查询用户资料。
- 收藏夹包含“我创建的”和“我收藏的”，支持普通收藏夹与视频合集读取。
- 播放历史同时保存进度和完整音频缓存；缓存完成的历史可离线播放。
- 播放页可以添加/取消喜欢；“我喜欢的”媒体库 Tab 按具体 `bvid:cid` 保存并支持重启后播放。
- 只播放 DASH 音频，不实现视频、歌词、下载管理、动态、推荐榜单和复杂主题。

## 本地构建

```zsh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`

详细设计见 [docs/architecture.md](docs/architecture.md)。

## 当前实现

- 固定横屏的首页、媒体库、播放页和底部迷你播放器。
- TV 接口二维码申请与两秒轮询，不依赖旧版系统 WebView。
- 关注 UP 配置、每位 UP 独立 Tab、按发布时间读取投稿。
- 首页 UP 支持关注列表与名称/UID 搜索两种来源，保存后每位 UP 独立 Tab。
- “我创建的”“我收藏的”双栏收藏夹、在线历史和本地离线历史。
- “我喜欢的”本地 Room 列表，支持多 P 按具体分 P 收藏。
- Media3 后台音频播放、进度持久化、完整音频缓存与 512MB 淘汰策略。
- Android Keystore 加密保存 Cookie 和 token。

模拟器验收记录见 [docs/qa-2026-08-04.md](docs/qa-2026-08-04.md)。
