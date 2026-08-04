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

配置候选默认来自当前账号完整关注列表，也可以在“首页 UP”页面输入名称或 UID 手动搜索：名称使用 WBI `/x/web-interface/wbi/search/type?search_type=bili_user`，纯数字 UID 使用 WBI `/x/space/wbi/acc/info`。关注结果与搜索结果分为两个子 Tab，二者都可以勾选；保存时持久化选中 UP 的 `mid`、名称、头像和顺序，避免搜索得到的 UP 因不在关注列表中而无法保存。每位 UP 对应一个 Tab，通过 WBI 空间投稿接口按发布时间倒序加载。配置为空时不请求任何默认推荐接口。

## 收藏与历史

收藏夹区分“我创建的”和“我收藏的”。普通收藏夹使用 `/x/v3/fav/resource/list`，视频合集使用 `/x/space/fav/season/list`，两类 ID 不混用。

历史使用 Room 保存 `bvid/cid`、标题、作者、播放位置、缓存文件和最后播放时间。网络播放启动后后台写入临时文件，完整下载并校验后原子改名；只有状态为 `READY` 的条目允许离线播放。

## 分 P 播放

单 P 资源只建立一个 Media3 播放项。多 P 资源先播放用户选中的分 P，再逐项解析后续音轨并追加到同一个 Media3 队列；当前 P 播放结束后由 ExoPlayer 自动切换下一 P，不使用业务计时器模拟结束事件。切换资源或进入本地离线历史时必须取消旧队列装载，防止旧资源的后续分 P 串入新队列。

每个分 P 使用 `bvid:cid` 作为独立 `mediaId`，当前分 P 名称写入通知主标题，总视频名称保留为专辑标题。自动切 P 时使用 `onPositionDiscontinuity` 的旧媒体项保存上一 P 最终进度，再由 `onMediaItemTransition` 为新 P 建立历史和缓存任务。

播放页左侧常驻显示当前 Media3 队列，点击分 P 直接调用 `seekToDefaultPosition(index)`；右侧提供上一曲、下一曲、顺序/单曲/列表循环和随机播放控制。所有控制都操作同一个 `MediaController`，因此车机页面、通知栏和锁屏不会维护互相矛盾的播放状态。

播放页根据当前媒体项是否存在分 P 标题区分布局：多 P 显示左侧播放列表，列表行只显示接口返回的分 P 标题，不额外拼接序号，播放列表与右侧播放器按 `1:1` 分栏；单 P 不显示播放列表，左侧封面与右侧播放器按 `1:2` 分栏。单 P 封面按素材自身宽高比完整显示，不裁剪、不拉伸；右侧两行标题使用略大于字号的行高，避免文字上下相贴。右侧控制按钮保持同一横排，播放状态说明文案不放在控制区，避免占用车机屏幕空间。

右侧控制条固定为均匀分布的五项：播放顺序、上一曲、播放/暂停、下一曲、喜欢。播放顺序按钮统一管理顺序、单曲循环、列表循环和随机模式；喜欢按钮按 `bvid:cid` 形成的 `mediaId` 持久化到 Room，跨进程重启后仍可在媒体库恢复。

“我喜欢的”使用独立 Room 表 `liked_media` 保存 `bvid/cid`、主标题、分 P 标题、作者、封面和喜欢时间，`mediaId` 使用 `bvid:cid` 区分多 P 的具体条目。数据库从 v1 通过 `MIGRATION_1_2` 增加该表；播放页喜欢按钮异步写入或删除，媒体库列表通过 Flow 实时更新。点击喜欢列表时优先恢复本地播放历史或缓存，否则按记录的 `bvid/cid` 重新解析，退出账号不会清理本地喜欢记录。

## 性能约束

- 单 Activity、单 MediaSessionService，不引入导航框架和视频解码器。
- 列表使用固定高度与稳定 key，内容列表不加载无实际意义的播放图片占位，减少旧 GPU 的纹理和内存压力。
- 音频缓存默认上限 512MB，优先删除最久未播放的完整缓存。
- 网络并发限制为 API 请求与单个音频缓存任务，避免播放时并行下载多条音频。
- Release 启用 R8 和资源收缩；Debug 在 `CASKA_1024x600` 上完成最终验收。

## 车机交互

- 首页和收藏夹提供固定位置的刷新入口；刷新收藏夹目录时保留当前选中目录，避免操作焦点跳回第一项。
- 快速切换首页 UP 时取消尚未完成的旧请求，并以当前 UP 的 MID 标记加载状态；有限网络并发始终优先服务当前可见 Tab。
- 投稿、收藏内容和在线历史接近列表末尾时自动读取下一页，不要求驾驶员精确点击“加载更多”。同一列表处于加载状态时不重复请求。
- 不同 UP 和收藏夹使用独立列表身份；在线历史以 `bvid:cid` 作为行标识，避免切换数据源沿用错误滚动位置或多 P 历史产生重复 key。
- 播放进度拖动期间只更新页面时间预览，手指松开后才向 Media3 提交一次 Seek，减少旧硬件上的重复解码和网络缓冲。
- 播放队列只在 Media3 Timeline 变化时重建；每秒进度同步不再复制最多 200 项的分 P 队列，降低持续播放时的分配和 GC 压力。
- 音频地址解析期间显示轻量加载状态；导航栏和账号页不展示分辨率、服务端确认等开发或状态说明文案。
- 退出登录必须二次确认；退出会清除 Bilibili 凭证和首页 UP 配置，但保留本地播放历史与“我喜欢的”。

## 证据来源

- [bilibili-API-collect APPKey](https://github.com/pskdje/bilibili-API-collect/blob/main/docs/misc/sign/APPKey.md)
- [bilibili-API-collect 搜索接口与 WBI](https://github.com/pskdje/bilibili-API-collect/blob/main/docs/search/search_request.md)
- [bilibili-API-collect 用户资料接口](https://github.com/pskdje/bilibili-API-collect/blob/main/docs/user/info.md)
- [TV 扫码调用实现](https://github.com/WhiteSevs/TamperMonkeyScript/blob/master/scripts-vite/%E3%80%90%E7%A7%BB%E5%8A%A8%E7%AB%AF%E3%80%91bilibili%E4%BC%98%E5%8C%96/src/api/BilibiliLoginApi.ts)
- [扫码接口字段演进记录](https://gitea.s1f.ren/shiran/bilibili-API-collect/commit/1deb78b295866755cbbcb46eaaf18d8004db46d9.patch)
