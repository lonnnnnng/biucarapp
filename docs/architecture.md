# Biu Car 架构与兼容性

## 设计边界

车机端以驾驶场景的低干扰和旧硬件稳定性为第一优先级。界面固定横屏，一级导航只保留首页、媒体库和播放页；不移植手机端的视频渲染、歌词、下载管理、动态和复杂弹层。

## 登录

首选 TV 扫码接口：

- `POST /x/passport-tv-login/qrcode/auth_code` 申请二维码。
- `POST /x/passport-tv-login/qrcode/poll` 每两秒轮询。
- `86039` 表示未确认，`86090` 表示已扫码待确认，`86038` 表示过期。
- 成功响应中的 `cookie_info.cookies` 组装为 Web Cookie，并使用 Android Keystore AES/GCM 加密保存。
- 登录完成后必须调用 `/x/web-interface/nav`，只有 `isLogin=true` 才进入账号态。

2026-08-04 已实时验证二维码申请返回 `code=0`，未扫码轮询返回 `86039`。该链路不依赖系统 WebView，因此避开目标 Android 8.1 设备仅有 Chromium 61 的兼容风险。

## 首页

配置候选来自当前账号完整关注列表。选中项只保存 `mid`、名称、头像和顺序；每位 UP 对应一个 Tab，通过 WBI 空间投稿接口按发布时间倒序加载。配置为空时不请求任何默认推荐接口。

## 收藏与历史

收藏夹区分“我创建的”和“我收藏的”。普通收藏夹使用 `/x/v3/fav/resource/list`，视频合集使用 `/x/space/fav/season/list`，两类 ID 不混用。

历史使用 Room 保存 `bvid/cid`、标题、作者、播放位置、缓存文件和最后播放时间。网络播放启动后后台写入临时文件，完整下载并校验后原子改名；只有状态为 `READY` 的条目允许离线播放。

## 性能约束

- 单 Activity、单 MediaSessionService，不引入导航框架和视频解码器。
- 列表使用固定高度与稳定 key，图片首版不加载，减少旧 GPU 的纹理和内存压力。
- 音频缓存默认上限 512MB，优先删除最久未播放的完整缓存。
- 网络并发限制为 API 请求与单个音频缓存任务，避免播放时并行下载多条音频。
- Release 启用 R8 和资源收缩；Debug 在 `CASKA_1024x600` 上完成最终验收。

## 证据来源

- [bilibili-API-collect APPKey](https://github.com/pskdje/bilibili-API-collect/blob/main/docs/misc/sign/APPKey.md)
- [TV 扫码调用实现](https://github.com/WhiteSevs/TamperMonkeyScript/blob/master/scripts-vite/%E3%80%90%E7%A7%BB%E5%8A%A8%E7%AB%AF%E3%80%91bilibili%E4%BC%98%E5%8C%96/src/api/BilibiliLoginApi.ts)
- [扫码接口字段演进记录](https://gitea.s1f.ren/shiran/bilibili-API-collect/commit/1deb78b295866755cbbcb46eaaf18d8004db46d9.patch)
