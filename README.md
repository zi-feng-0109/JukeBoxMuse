# 点歌姬 Jukebox Muse

在 Minecraft 里点歌：搜索酷狗音乐、网易云音乐、QQ 音乐的歌曲，全服玩家同步收听，带歌词和专辑封面。

支持 **NeoForge** 和 **Fabric**，Minecraft **1.21.1**。

## 功能

- **三个平台**：酷狗音乐、网易云音乐、QQ 音乐，搜索时可切换
- **全服同步**：所有玩家听到的是同一首歌、同一个进度，中途进服也会从当前位置开始播放
- **点歌队列**：排队、切歌、删除；可限制点歌冷却、每人排队数量、队列长度
- **扫码登录**：在游戏里打开点歌界面直接扫码，不用输指令。QQ 音乐支持 QQ 和微信两种扫码
- **歌词**：滚动歌词，带翻译的歌曲同时显示译文；可在屏幕顶部显示当前这句
- **专辑封面**：以旋转的黑胶唱片显示
- **原版风格界面**：灰色容器面板、创造模式式标签页，每个平台用一张原版唱片区分

## 安装

1. 安装对应加载器：
   - **NeoForge**：1.21.1 版本
   - **Fabric**：Fabric Loader，以及 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 把本 mod 的 jar 放进 `mods` 文件夹。

**服务端和客户端都必须安装**，且版本一致：服务器负责登录账号、搜索和管理队列，每个玩家的客户端自己下载并播放音乐。单人游戏只需装在客户端。

## 使用

1. 按 **K** 打开点歌界面。
2. 首次使用：管理员（OP，或单人世界的房主）在「账号」页选择平台，点「扫码登录」，用对应的手机 App 扫描二维码。
3. 在「搜索点歌」页选择平台、搜索，点一首歌即可加入队列。

> 建议使用音乐平台的**小号**登录：全服共用这一个账号搜索和播放，调用过多可能触发平台风控。

### 指令

| 指令 | 说明 | 权限 |
|---|---|---|
| `/muse status` | 查看各平台登录状态、正在播放和排队情况 | 所有人 |
| `/muse skip` | 切歌（只能切自己点的，管理员可切任何歌） | 所有人 |
| `/muse clear` | 清空队列 | 管理员 |
| `/muse debug <平台> <关键词>` | 测试搜索，并把原始返回写入日志 | 管理员 |

`/muse` 是 `/jukeboxmuse` 的简写，两者完全相同。

### 设置

设置文件在 `config/jukeboxmuse/` 下，带中文注释：

- `server.json`：点歌冷却、每人排队上限、队列上限、音质（128 / 320）、调试日志。修改后重启服务器（单人游戏重新进入世界）生效。
- `client.json`：音量、静音、屏幕顶部歌词。也可以直接在点歌界面里调整。

同一目录下的 `kugou.json`、`netease.json`、`qqmusic.json` 保存的是登录凭证，**不要分享给别人**。

## 常见问题

**为什么有的歌放不了？**
平台只给这个账号能听的版本。VIP 歌曲在非 VIP 账号下通常只有试听片段，这种歌会自动跳过并在聊天栏提示；没有版权的歌也会跳过。

**320 音质没生效？**
320k 在多数平台需要 VIP 账号，拿不到时会自动退回 128k。

**排查问题时需要日志？**
在 `server.json` 里把 `debugLog` 改成 `true`，重启后复现问题。日志会包含平台的原始返回，**其中有账号登录凭证**，发给别人之前请删掉相关内容，排查完改回 `false`。

## 声明

本项目为非官方项目，与酷狗音乐、网易云音乐、QQ 音乐（腾讯）及其关联公司无关，也未获得其认可或授权。项目通过各平台的非官方接口工作，接口随时可能变化导致功能失效。仅供学习交流使用，请遵守各平台的用户协议，支持正版音乐。

## 构建

需要 JDK 25（Gradle 本身运行在它上面），以及各版本编译用的 JDK 17 / 21，Gradle 会自动查找本机安装的 JDK。

```
./gradlew build        # 构建所有版本
./gradlew collectJar   # 构建并把所有 jar 收集到 build/libs/<mod 版本>/
./gradlew :1.21.1-fabric:build   # 只构建一个版本
```

单个版本的产物在 `versions/<MC 版本>-<加载器>/build/libs/`。

多版本用 [Stonecutter](https://stonecutter.kikugie.dev/) 管理：所有版本共用 `src/` 里的同一份代码，版本差异写成 `//? if >=1.21.6 {` 这样的条件注释，加载器差异写成 `//? if fabric {` / `//? if neoforge {`。
IDE 里 `src/` 同一时间只对应一个版本（`stonecutter.gradle.kts` 里的 `active`），用 Gradle 任务 `Set active project to ...` 切换；提交前运行 `Reset active project` 切回默认版本。
每个版本用到的 MC、加载器、依赖版本写在 `stonecutter.properties.toml`，支持的版本列表在 `settings.gradle.kts`。

测试：

```
./gradlew :core:test                          # 平台接口、歌词等单元测试
./gradlew :1.21.11-fabric:runClientGameTest   # 自动打开点歌界面各页并截图（Fabric 1.21.4+），截图在 versions/<节点>/build/run/clientGameTest/screenshots/
scripts/smoke-test.sh 1.21.11-neoforge 1.21.11-fabric   # 启动专用服务器、客户端自动加入，检查两边日志（Git Bash）
```

项目结构：

| 目录 | 内容 |
|---|---|
| `core/` | 与 Minecraft 无关的纯 Java 代码：三个平台的接口、歌词、设置。有自己的单元测试（`./gradlew :core:test`），源码会编进每个版本的 jar |
| `src/` | 所有版本、所有加载器共用的 MC 代码：界面、渲染、播放、网络、点歌服务；`fabric/`、`neoforge/` 包是各加载器的入口和事件 |
| `build.fabric.gradle.kts`、`build.neoforge.gradle.kts` | 每个 Fabric / NeoForge 版本节点的构建脚本 |
| `versions/` | Stonecutter 为每个版本生成的子项目，只有构建产物 |

## 许可证

[GPL-3.0-or-later](LICENSE)。部分代码移植自 KuGouMusicApi、NeteaseCloudMusicApi、QQMusicApi，并内置 ZXing、JLayer，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
