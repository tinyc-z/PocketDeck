# PocketDeck 全功能回归测试用例库

> 本用例库作为 PocketDeck 的长期回归基线维护。每次新增、修改或下线功能时，均需同步更新本文件中的对应条目。
>
> - **适用范围**：单元测试（JVM）、掌机端 UI & 功能（Android 真机）、Web 控制台 & SSE 实时同步（浏览器 E2E）
> - **默认真机环境**：KONKR Pocket Block（`960×720`, `arm64-v8a`, Android 12 API 31）

---

## 模块一：单元测试（JVM Unit Tests）

运行命令：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`

| 用例编号 | 测试类 | 覆盖模块 / 路径 | 测试要点 | 状态 |
| :--- | :--- | :--- | :--- | :--- |
| **UT-01** | `EmulatorRegistryTest` | `launch/EmulatorRegistry.kt` | 模拟器 ID 唯一性、包名反查（含 `com.sky.SkyEmu` / `Lemuroid` / `EmuEX` / `PPSSPP` / `melonDS` / `Azahar` / `Flycast`）、核心平台映射、RetroArch 核心与自定义 App 编解码 | 已有 |
| **UT-02** | `RomScannerMultiDiscTest` | `data/RomScanner.kt` | 多光盘 `.m3u` 识别与折叠、忽略媒体目录与非 ROM 文件、隐藏 `.cue` 引用的 `.bin`、解析 ES-DE `gamelist.xml` 与本地封面 | 已有 |
| **UT-03** | `TitleNormalizerTest` | `data/TitleNormalizer.kt`, `data/CjkAliases.kt`, `data/UpdateChecker.kt` | 括号/编号清理、冠词处理、CJK 标签与别名展开、日韩文保留、罗马数字变体、游玩统计编解码、Semver 版本比较 | 已有 |
| **UT-04** | `WebManagerApiTest` | `lan/CategoryValidator.kt` | 分类保存 payload 校验、路径穿越与保留 ID 拦截、批量操作 payload 校验与非法拦截 | 已有 |
| **UT-05** | `PlatformCatalogTest` | `data/PlatformCatalog.kt` | 平台别名映射（`fc`/`ps1`/`md`/`ns`/`apk`/`gc` 等）、`isKnown`、`forFolder`、`sortKey` 排序一致性 | 已有 |
| **UT-06** | `RetroArchCoreCatalogTest` | `launch/RetroArchCoreCatalog.kt` | `extractCoreIdFromSo` 解析（合法 `.so` 与非法文件名）、`displayName`/`info` 映射、`retroarch.cfg` 中 `libretro_directory` 解析 | 已有 |
| **UT-07** | `ArchiveManagerTest` | `data/ArchiveManager.kt` | ZIP 压缩、条目列举、安全解压、Zip Slip 路径穿越（`../`）防护 | 已有 |
| **UT-08** | `ScrapeLogStoreTest` | `data/ScrapeLogStore.kt` | URL 敏感参数脱敏（`apikey`/`sspassword`/`token`）、节点识别 `nodeFromUrl` | 已有 |
| **UT-09** | `HttpMiniServerSseTest` | `lan/HttpMiniServer.kt` | `startEventStream()` 响应头（`text/event-stream` / `no-cache` / `X-Accel-Buffering`）与 `sendSseRaw()` 写入/断链返回 `false` | 已有 |

---

## 模块二：掌机端 UI & 功能回归测试（Handheld E2E）

### 2.1 首页与视图（`ui/home/HomeScreen.kt`）

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **APP-01** | 首页 → 顶部标签栏 | 核对标签顺序、数量、自定义名、L1/R1 切换流畅、状态栏时间/电量/Wi-Fi/Web 图标显示正常 |
| **APP-02** | 首页 → 视图切换（画廊 / 网格 / 列表 / 气泡）与可见窗口封面预刮削 | 依次切换 4 种视图模式，确认封面渲染、焦点框、滚动与返回无错位或崩溃；非聚焦卡片通过 2 并发后台窗口预刮削自动补齐封面 |
| **APP-03** | 首页 → 搜索（含「全分类」开关） | 打开搜索栏输入关键词，验证「全分类」开关默认开启且可持久化切换，实时过滤结果准确 |
| **APP-04** | 首页 → 游戏详情弹窗（`GameDetailDialog`） | 核对横屏布局不误判为窄屏、封面右上角铅笔按钮清晰、元数据完整；打开属性弹窗后关闭能正确恢复焦点 |
| **APP-05** | 首页 → 游戏属性 / 编辑 / 手动刮削 | 重命名、收藏/取消收藏、隐藏/取消隐藏、修改所属分类、手动刮削（关键词 + Hash 并发命中带 `Hash` 徽章）；确认 Android APK 条目隐藏刮削入口 |

### 2.2 游戏启动与模拟器兼容（`launch/GameLauncher.kt`）

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **APP-06** | RetroArch 核心启动（原生 + `.zip` + `.7z`） | 覆盖 NES (`FCEUmm`)、SNES (`Snes9x 2002`)、GB (`Gambatte`)、GBC (`Gambatte`)、GBA (`mGBA`) 启动无白屏/闪退 |
| **APP-07** | 独立模拟器识别与启动 | 验证已安装的 SkyEmu (`com.sky.SkyEmu`)、Lemuroid、NES.emu、Snes9x EX+、GBC.emu、GBA.emu、PPSSPP、melonDS、Azahar、Flycast 在对应平台自动识别；验证 SkyEmu 实机启动 GBA ROM 正常 |

### 2.3 设置中心全 7 分区（`ui/settings/SettingsScreen.kt`）

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **APP-08** | 设置 → 外观 | 切换亮/暗/跟随系统、主题色、5 种背景、减少动态效果、字体，UI 实时生效无闪烁 |
| **APP-09** | 设置 → 主页（标签分类管理） | 增删改分类、调整排序、隐藏/显示系统标签，返回首页后标签顺序与数量一致 |
| **APP-10** | 设置 → 游戏库 → 目录浏览与「一键建目录」 | 验证目录浏览点击有按键音；打开「一键建目录」核对单关闭键、父文件夹名输入、目标路径预览、已存在目录琥珀色提示与「已存在」角标、仅常用/全选/清空、批量创建只增不删 |
| **APP-11** | 设置 → 封面与信息 → 刮削日志 | 检查凭据配置项、打开 `ScrapeLogsScreen` 正常查看/清空/导出日志 |
| **APP-12** | 设置 → 模拟器 | 检查「全部平台」开关、RetroArch 核心目录自动识别（解析 `retroarch.cfg` 显示核心数 + 副标题动态包名路径提示）、点击 `TEST` 自定义平台进入 RetroArch 核心选择页能列出全部已检测核心 |
| **APP-13** | 设置 → 操控 & 系统 | 检查 ABXY 互换/自定义按键/界面音效/状态栏开关；打开「局域网与快传」启停 Web 控制台与快传、刷新 PIN、踢下线 |

### 2.4 本机文件管理与应用页（`ui/library/GameLibraryScreen.kt`, `ui/apps/AppsScreen.kt`）

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **APP-14** | 首页 B 键 → 应用列表 | 正常加载已装应用列表、支持搜索与启动 |
| **APP-15** | 游戏库管理 → 目录文件 Tab | 浏览存储目录、新建/重命名/移动/复制/删除、压缩包预览与解压、文本/图片查看正常 |

---

## 模块三：Web 控制台 & SSE 实时同步回归测试（Browser E2E）

### 3.1 登录、鉴权与 SSE 生命周期

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **WEB-01** | 未登录鉴权拦截 | 未登录请求 `/api/events`、`/api/info`、`/api/games` 均返回 `401`；验证 `/api/theme` 与 `GET /api/files/tasks` 已彻底下线（返回 `404`） |
| **WEB-02** | 登录与会话保持 | 正确 4 位 PIN 登录进入控制台，SSE `/api/events` 建连成功并收到首帧 `state` + `tasks`，全站无常驻业务轮询 |
| **WEB-03** | 鉴权吊销 & 断线重连 | 掌机刷新 PIN / 踢下线 / Web 退出登录时，SSE 推送 `auth_revoked` 并立即退回登录页；服务短暂断开时提示离线并退避重连 |

### 3.2 游戏库与 SSE 双向同步

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **WEB-04** | 游戏库 → 吸顶头 & 分类芯片 | 宽屏下毛玻璃吸顶栏左右铺满无缺口；滚动时仅下方卡片滚动；分类芯片首位「全部」+ 后续顺序与数量严格匹配掌机（含「最近」无缺 key） |
| **WEB-05** | 游戏库 → 搜索 / 排序 / 视图切换 | IME 防抖搜索 + Enter 立即搜 + 清除按钮；排序切换（默认最新更新）；封面网格 / 详情列表切换；封面 ETag / `coverVersion` 缓存正常 |
| **WEB-06** | 游戏详情 / 在线选图 / 批量操作 | 打开详情弹窗自动补齐元数据；在线选图显示 Hash 徽章与 IndexedDB 索引缓存；批量选择/隐藏/加入分类/重新刮削正常；APK 隐藏刮削操作 |
| **WEB-07** | 拖入 ROM 上传 + 有界自动刮削 | 拖入合法扩展名 ROM 上传成功并触发 2 并发后台队列自动刮削，刮削完成后 SSE `game_updated` 实时刷新封面与元数据；非法扩展名拦截返回 `400` |
| **WEB-08** | 掌机 → Web 实时 SSE 同步 | 掌机端切换主题/亮暗、换封面、收藏/取消收藏、修改分类后，Web 端无需刷新即实时同步 |

### 3.3 标签分类、文件管理与刮削日志

| 用例编号 | 功能路径 | 操作步骤与预期结果 |
| :--- | :--- | :--- |
| **WEB-09** | 标签分类 Tab | 列表展示与掌机一致、拖拽排序/新增/编辑/隐藏/保存后实时同步到掌机与游戏库芯片 |
| **WEB-10** | 文件管理 → 吸顶头 & 一键建平台目录 | 吸顶头紧凑固定；在「存储根目录」打开目标路径为 `/storage/emulated/0/roms`（无 `roms/roms`）；在已有 ROM 根目录打开默认留空父目录名并弹出琥珀色已存在提示；真实创建 14 个平台目录只增不删 |
| **WEB-11** | 文件管理 → 常规操作与任务 SSE | 新建/重命名/移动复制弹窗样式完整（`.dir-pick`）/压缩/解压/文本编辑（`.editor`）/下载/删除；大操作进度条通过 SSE `tasks` 实时推送 |
| **WEB-12** | 刮削日志浮窗 | 首次打开拉取历史，后续通过 SSE `scrape_log` 增量追加，筛选/暂停/下载/清空（`scrape_log_cleared`）正常 |
