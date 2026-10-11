# Modrinth / CurseForge 项目页文案

建项目时按下面几段填写。两个平台用同一份正文（Markdown）。

## 项目名称

```
点歌姬 Jukebox Muse
```

## 简介（Summary，一句话，显示在搜索结果里）

```
In-game song requests from KuGou, NetEase Cloud Music and QQ Music, synced for the whole server. 游戏内点歌，全服同步播放。
```

## 分类建议

- Modrinth：Utility、Social（客户端 + 服务端都需要：Client side **required**、Server side **required**）
- CurseForge：Class 选 Mods，主分类 Server Utility，附加分类 Utility & QoL；Summary 必须是英文（只用上面英文那半句），正文英文部分放在前面

## 正文（两个平台通用，复制代码块里的全部内容）

CurseForge 和 Modrinth 都要求正文是英文或带英文翻译，所以英文放在前面、中文跟在后面。CurseForge 的编辑器要先切到 Markdown 模式再粘贴。

```markdown
**Jukebox Muse** brings song requests into Minecraft: search songs on KuGou, NetEase Cloud Music and QQ Music, and the whole server listens together, with scrolling lyrics and album covers.

> ⚠️ **Unofficial project.** Not affiliated with or endorsed by KuGou, NetEase Cloud Music or QQ Music / Tencent. It works through the platforms' unofficial APIs, which may change and break features at any time. For learning and personal use only — please respect each platform's terms of service and support the artists.

### Features

- Three platforms: KuGou, NetEase Cloud Music and QQ Music
- Server-wide sync: everyone hears the same song at the same position, including players who join mid-song
- Request queue with skip / remove, and server-side limits on cooldown and queue size
- Log in by scanning a QR code right inside the game (QQ Music: QQ or WeChat)
- Scrolling lyrics with translations, optionally shown at the top of the screen
- Album covers as spinning vinyl records
- Vanilla-style screens: container panel, creative-inventory tabs, a music disc per platform

### Installation

Required on **both server and client** (single player: client only). The Fabric version needs Fabric API.

### Usage

Press **K** to open the screen. An operator logs in on the Account tab (a secondary account is recommended, as the whole server shares it), then anyone can search and request songs. Commands: `/muse status`, `/muse skip`, `/muse clear`.

Note: the interface is in Chinese, as are the supported music platforms.

License: GPL-3.0-or-later · Source & issues: [GitHub](https://github.com/zi-feng-0109/JukeBoxMuse)

---

**点歌姬 Jukebox Muse** 让你在 Minecraft 里点歌：搜索酷狗音乐、网易云音乐、QQ 音乐的歌曲，全服玩家同步收听，带滚动歌词和专辑封面。

> ⚠️ **非官方项目。** 本 mod 与酷狗音乐、网易云音乐、QQ 音乐（腾讯）及其关联公司无关，也未获得其认可或授权。它通过各平台的非官方接口工作，接口随时可能变化导致功能失效。仅供学习交流使用，请遵守各平台的用户协议，支持正版音乐。

### 功能

- 🎵 **三个平台**：酷狗音乐、网易云音乐、QQ 音乐，搜索时随时切换
- 🔊 **全服同步**：所有玩家听到同一首歌、同一个进度，中途进服也从当前位置开始
- 📋 **点歌队列**：排队、切歌、删除；服主可限制点歌冷却、每人排队数和队列长度
- 📱 **扫码登录**：在游戏里直接扫码登录，不用输指令；QQ 音乐支持 QQ 和微信扫码
- 📝 **歌词**：滚动歌词，带翻译的歌曲同时显示译文，可在屏幕顶部悬浮显示
- 💿 **专辑封面**：以旋转的黑胶唱片显示
- 🧱 **原版风格界面**：灰色容器面板、创造模式式标签页，每个平台对应一张原版唱片

### 安装与使用

- **服务端和客户端都必须安装**，且版本一致（单人游戏只需装在客户端）；Fabric 版需要 Fabric API
- 按 **K** 打开点歌界面；管理员在「账号」页选择平台扫码登录（建议用小号）；在「搜索点歌」页搜歌，点一下即可加入队列
- 指令：`/muse status`、`/muse skip`、`/muse clear`
- VIP 歌曲在非 VIP 账号下通常只有试听片段，这类歌会自动跳过
- 设置文件在 `config/jukeboxmuse/`；同目录下的账号文件保存着登录凭证，请勿分享
```
