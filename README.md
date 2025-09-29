# 局域网投屏（热点直连，无第三方服务器）

一个最小可用的 Android 屏幕镜像示例：
- 发送端（A 手机）开启系统热点并运行 SenderService，作为 TCP 服务端；
- 接收端（B 手机）连接该热点并运行 ReceiverService，作为 TCP 客户端；
- 仅使用原生 Socket（TCP），不依赖 WebRTC、STUN/TURN/ICE 或任何第三方信令服务器。

本项目使用 MediaProjection + MediaCodec(H.264) 采集/编码屏幕，并通过 TCP 实时发送到另一台手机，接收端用 MediaCodec 解码后渲染到 SurfaceView。

---

## 功能特性
- 热点直连：A 手机开热点，B 手机连入热点即可，无需互联网、无需第三方服务器。
- 原生编码/解码：
  - 发送端：MediaProjection 捕获屏幕，MediaCodec 编码为 H.264。
  - 接收端：MediaCodec 解码 H.264，输出到 SurfaceView。
- 简单可靠的长度前缀协议：支持发送 SPS/PPS（csd-0/csd-1）和帧数据，稳定初始化解码器。
- 基础错误处理：连接失败/断开时弹 Toast，不崩溃。

---

Manifest 中已声明所需权限与前台服务；Gradle 已配置 Compose（Kotlin 2.0 的 Compose 编译插件）。

---

## 工作原理（热点 + 网关 IP）
1. A 手机开启系统热点后，系统会为热点分配一个局域网网关 IP（常见是 `192.168.43.1`，具体由设备决定）。
2. 本 App 的发送端在 A 手机上监听 `0.0.0.0:8080`（TCP）作为服务端。
3. B 手机连入热点后，通过 `WifiManager.dhcpInfo.gateway` 获取“热点网关 IP”。
4. 接收端直接连接该网关 IP 的 8080 端口，与发送端建立 TCP 连接，随后接收 H.264 数据流并解码显示。

无需任何第三方服务器或互联网，完全局域网内点对点传输。

---

## 传输协议（简化版）
- 为保证解码器能正确初始化，发送端在 MediaCodec 输出格式变更时发送 csd（SPS/PPS）：
  - 发送 `int(-1)` + `int(len)` + `len` 字节的 `csd-0`（SPS）
  - 发送 `int(-2)` + `int(len)` + `len` 字节的 `csd-1`（PPS）
- 每一帧视频：
  - 发送 `int(frameLen)` + `frameLen` 字节的 H.264 数据
- 接收端：
  - 先收齐 `csd-0` 和 `csd-1`，且拿到渲染 Surface 后，配置解码器并开始解码后续帧。

---

## 权限说明
- INTERNET：Socket 网络通信。
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PROJECTION：前台服务和投屏前台服务类型。
- ACCESS_WIFI_STATE / ACCESS_NETWORK_STATE：读取网关/网络状态。

> Android 13+ 建议授予通知权限（POST_NOTIFICATIONS），以确保前台服务通知稳定显示（工程未强制要求）。

---

## 运行步骤

1) 发送端（A 手机）
- 打开系统“热点”功能。
- 安装并启动 App。
- 点击“Send Screen”（发送屏幕），同意屏幕捕获权限。
- 此时 SenderService 会启动，监听 8080 端口并等待连接。

2) 接收端（B 手机）
- 连接到 A 手机的热点。
- 启动 App，界面会显示“Hotspot Gateway: <ip>”。
- 点击“Receive Screen”（接收屏幕），将出现一个 SurfaceView；连接成功后显示 A 手机屏幕内容。

> 默认编码参数：分辨率 720x1280，30fps，~1.5Mbps，比特率和分辨率可在 `SenderService.kt` 调整（VIDEO_WIDTH/HEIGHT、KEY_BIT_RATE、KEY_FRAME_RATE 等）。

---

## 常见问题与排查
- 接收端提示“Failed to connect to sender”
  - 确认 B 手机已连接 A 手机热点。
  - 确认 A 手机端已点击“Send Screen”，SenderService 正在监听。
  - 某些设备的热点网关 IP 不是 192.168.43.1，App 会自动读取网关 IP；若读取异常，可考虑添加手动输入。
  - 检查是否有厂商/系统限制（如 AP 隔离）。

- 只有黑屏/无画面
  - 首次连接时请等待 1-2 秒，接收端需要先收到 csd-0/csd-1 才能初始化解码器。
  - 确认接收端 Surface 已创建（UI 需要看到 SurfaceView 显示区域）。

- 画面卡顿/延迟较高
  - TCP 为可靠传输，丢包重传会引入延迟；可尝试降低分辨率/码率，或后续改为 UDP 自行实现更低延迟传输。

- 构建/运行问题
  - 若 Gradle 版本或 Kotlin 版本不一致导致同步失败，请先执行 Gradle 同步并清理缓存再试。

---

## 重要限制
- 仅视频画面示例，不包含音频。
- 未做网络自适应/丢包处理；在弱网络下可能卡顿或累积延迟。
- 未做加密/鉴权；仅用于局域网内演示，请勿在不可信网络环境使用。

---
## 后续可扩展方向
- 分辨率/码率/帧率动态配置，横竖屏与方向变化适配。
- UDP/RTP 传输与自定义重传策略，降低端到端延迟。
- 音频采集/编码/同步播放。
- 简单密钥/鉴权，提升局域网安全性。
- UI 增加“手动输入 IP”与“自动重连”选项。

