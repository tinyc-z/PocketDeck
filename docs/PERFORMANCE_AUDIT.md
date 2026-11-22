# PocketDeck 性能、稳定性与刮削准确性审查清单

> 审查日期：2026-11-22  
> 目标：系统性提升冷启动速度、数据加载与全量刮削吞吐、封面与元数据识别准确率、界面运行流畅性及 App 长期运行稳定性。  
> 状态说明：`[ ]` 待处理 · `[x]` 已修复并通过测试验证

---

## 一、P0 核心缺陷与高收益优化（优先落地）

### 1. 启动速度与冷启缓存

- [x] **P0-1：消除 `Application.onCreate()` 主线程同步读盘与重型构造**
  - **位置**：`PocketDeckApp.kt:41-48`、`ScraperClient.kt:52`、`ScrapeLogStore.kt:66-79`、`CoverSource.kt:83-85`、`MetadataRepository.kt:88`、`GameTdbClient.kt:34`、`LanServerManager.kt:100-135`
  - **问题**：冷启主线程同步调用 `ScrapeLogStore.init()` 读最多 512 KB `scrape.log`，同步多次 `mkdirs()`，急切初始化 `OkHttpClient`、`WebManagerServer`、`QuickShareService` 并枚举网卡。
  - **方案**：`ScrapeLogStore.init` 仅登记路径并后台异步加载历史日志；`OkHttpClient`、`WebManagerServer`、`QuickShareService` 与目录创建改为懒加载/IO 线程执行；网卡枚举延后至打开局域网面板或启动服务。

- [x] **P0-2：消除冷启 `PackageManager` 同步阻塞与双倍查询**
  - **位置**：`GameRepository.kt:243,308-340,378-382`、`LibraryIndexCache.kt`
  - **问题**：命中二进制索引缓存后，`withAndroidGames` 因 `cachedInstalledApps` 为空同步扫描本机全部应用调用 `loadLabel(pm)`（150~500ms+），直接抵消 `<30ms` 缓存收益；扫描结束后又无条件调用第二次。同时 `LibraryIndexCache.save()` 用 `writeUTF` 写 `description`，超 64 KB 会静默失败。
  - **方案**：`LibraryIndexCache` 升级格式以长字符串写 `description` 并持久化 `AndroidAppEntry` 快照；冷启首屏直接复用缓存的 Android 游戏列表，后台仅刷新 1 次 `PackageManager`，无变更时不重复写盘。

### 2. 数据一致性与并发稳定性

- [x] **P0-3：修复隐藏游戏后全库 `gamelist.xml` 本地元数据丢失**
  - **位置**：`GameRepository.kt:263`、`HomeViewModel.kt:256-258`
  - **问题**：`repo.library` 过滤隐藏游戏时新建 `Library(...)` 漏传 `localMetadata` 和 `dirSnapshots`，导致只要隐藏任意一款游戏，本地 `gamelist.xml` 元数据全丢。
  - **方案**：改用 `full.copy(platforms = filteredPlatforms, games = filteredGames)`。

- [x] **P0-4：修复 `TopBar` 跨线程共享 `SimpleDateFormat` 竞态**
  - **位置**：`TopBar.kt:288,309,326`
  - **问题**：同一非线程安全 `SimpleDateFormat` 实例同时在 `Dispatchers.IO` 和主线程调用 `.format()`。
  - **方案**：替换为线程安全的 `java.time.format.DateTimeFormatter` + `LocalTime.now()`。

- [x] **P0-5：修复 `HomeViewModel.recomputeFromRepo(stateOverride)` 竞态覆盖弹窗状态**
  - **位置**：`HomeViewModel.kt:433,858-872`
  - **问题**：后台重算提交时直接以旧快照 `stateOverride` 覆盖 `latest`，切标签/输入搜索期间并发打开弹窗会被意外清空。
  - **方案**：仅将显式导航/搜索意图字段（`selectedTab` / `searching` / `searchQuery` / `searchAllCategories`）合并到最新 `latest` 上，不覆盖弹窗状态。

- [x] **P0-6：修复 `CoverSource` 非原子 `_indexVersion` 写与配置变更误清全库封面缓存**
  - **位置**：`CoverSource.kt:193,442-451,597,656` 等
  - **问题**：多处跨线程 `_indexVersion.value += 1` 非原子更新且未节流；用户仅微调节点配置或填 API Key 就 `db.clearCovers()` 清空全部已刮削封面，且未同步清理 `webCacheFilesById`。
  - **方案**：统一改为原子节流刷新；配置变更时仅清负缓存与被禁用节点的缓存，保留已启用节点的有效封面。

### 3. 全量刮削性能与状态隔离

- [x] **P0-7：隔离 `_scrapeProgress` 避免切 Tab 覆写全量刮削进度**
  - **位置**：`MetadataRepository.kt:818-866`、`MainActivity.kt:77`
  - **问题**：静默 `ensureLoaded -> resolveGames(queryApi = false)` 强行写 `_scrapeProgress`，多平台并发互踩且在全量刮削运行中把 `running` 覆写为 `false`，导致进度条消失并解除屏幕常亮。
  - **方案**：`resolveGames(queryApi = false)` 不再操作 `_scrapeProgress`，仅保留给主动批量刮削任务使用。

- [x] **P0-8：重构全量刮削 `scrapeMissing` 为两阶段并发引擎并复用 Coil 磁盘缓存**
  - **位置**：`MetadataRepository.kt:350-487,1270-1286`、`CoverSource.kt:1332-1343,1501-1503`、`ScraperClient.kt:236-263`
  - **问题**：全量刮削纯单线程串行；`fetchWebCoverNow` 未先查 `readCoilDiskCache` 导致已浏览封面全量重下；每 4 款做 O(N) 整表 Map 拷贝唤醒全表 diff；ArcadeItalia 仅预取前 60 款且逐条写库无事务。
  - **方案**：
    1. `fetchWebCoverNow` 优先从 `readCoilDiskCache` 零网络转存到 `web_covers`；
    2. 全量刮削按平台分组，阶段 A 批量预加载索引/离线表与全量 ArcadeItalia 批次（单事务落库），阶段 B 以受控并发池（4 Worker）执行元数据 + 封面抓取；
    3. 按时间窗（~400ms / 16 项）节流刷新 `_scrapeProgress` 与 `_metadata`，并推送增量 `officialName` 通知避免全表 diff。

### 4. 刮削识别与封面准确性

- [x] **P0-9：修复 TheGamesDB / IGDB 盲兜底 `[0]` 误配并锁定 30 天**
  - **位置**：`ScraperClient.kt:614-625,732-742`
  - **问题**：所有搜索结果相似度均低于阈值时仍强取 `[0]` 写入 30 天缓存。
  - **方案**：未达阈值直接返回空 `ScrapeHit()`，并适当收紧匹配阈值至 `0.58~0.60`。

- [x] **P0-10：修复 `localCover` 永远遮挡手动刮削封面 + 手动刮削元数据重启丢失**
  - **位置**：`CoverSource.kt:905-920`、`MetadataRepository.kt:1214-1232`、`ScrapeDatabase.kt`
  - **问题**：`candidates()` 中 `game.localCover` 优先于 `resolvedUrls`，手动刮削后仍显示旧 `localCover`；手动元数据仅写内存未持久化。
  - **方案**：显式记录手动/高优在线覆盖（或将 `webCacheFilesById` / `resolvedUrls` 置于 `localCover` 之前），手动元数据以永久条目写入 `ScrapeDatabase` 并在启动/加载时恢复。

- [x] **P0-11：修复中文 `officialName` 污染后续英文源检索 & 窗口刮削丢弃官方名/跳过离线 Hash**
  - **位置**：`GameTdbClient.kt:473-478`、`ScraperClient.kt:539`、`MetadataRepository.kt:645-781`、`CoverSource.kt:663-721`
  - **问题**：中文环境下 `GameTDB`/`ScreenScraper` 把中文名塞入 `officialName`，导致后续英文 API 与 Libretro Boxarts 失配；`windowScrapeQueue` 硬编码 `officialName = null`，且未启 API 节点时直接跳过离线 CRC 识别。
  - **方案**：在 `ScrapeHit` 保留英文 `canonicalName` 供跨源检索；`CoverSource` 接入官方名回调，并在开启 Libretro Boxarts 时即使关闭外部 API 也允许窗口队列运行离线 CRC 识别。

- [x] **P0-12：修复 3DS 32KB 头截断、`.7z`/`.zip` 溢出与剥头、Switch GameTDB `<titleid>` 及 ScreenScraper 压缩包大小冲突**
  - **位置**：`RomIdentityExtractor.kt:96-139,311-316`、`GameTdbClient.kt:289-301,453`、`ScraperClient.kt:524-525`、`MetadataRepository.kt:704-711`
  - **问题**：
    - 3DS 只读 16KB 截断了位于 `0x4150` 的 `CTR-P-XXXX`；
    - `.zip`/`.7z` 条目评分 Int 溢出；`.7z` 未剥 iNES/SMC 头且未提 Serial；`.zip` 漏传 `platformId` 且未读 `.nds` 头 Game Code；
    - `GameTdbClient` 未解析 `<titleid>` 致 Switch 16 位 Title ID 失配、误把 Switch 5 位码第 4 位当区码、下载失败无冷却；
    - `ScreenScraper` 对压缩 ROM 同传解压 CRC 与压缩包体积；
    - `MetadataRepository` 先模糊标题后指纹。
  - **方案**：逐项修复以上提取与协议逻辑；对快速指纹格式（缓存命中 / zip / 7z / nds / 3ds / chd / rvz / wbfs）优先精确指纹再降级标题匹配。

### 5. 界面流畅性与主线程优化

- [x] **P0-13：拆分 `HomeScreen` / `HomeOverlays` / `TopBar` 高频订阅消除整树重组**
  - **位置**：`HomeScreen.kt:114-119,371,420-421`、`HomeOverlays.kt:53`、`TopBar.kt:140-168`
  - **问题**：`HomeScreen` 顶层订阅 `lanManager.state`/`fileTasks`/`fullLibrary`/`filesRevision` 并在 `Column` 中两次订阅 `scrapeProgress`，`HomeOverlays` 又订阅一次；`state::platformFor` 每次重组重新绑定；`ScrapeStatusPill` 每帧重组 60~120 次。
  - **方案**：高频流全部下沉至独立窄边界子 Composable（`TopBarScrapeSlot`、`TopBarLanSlot`、按需激活 Overlay 内订阅）；复用稳定 `stablePlatformOf`；`ScrapeStatusPill` 延迟到 `graphicsLayer` 读旋转角；`LocalAsyncImageModelEqualityDelegate` 提升到 `HomeScreen` 顶层一次提供。

- [x] **P0-14：优化 `CoverSource` 缩略图主线程哈希、缓存失效 Bug 与并发解码上限**
  - **位置**：`CoverSource.kt:1806-1878`、`CoverCard.kt:148-156`
  - **问题**：主线程 `preferredThumbOrOriginal` 频繁调 `MessageDigest` + 20 次 `String.format`；缩略图 key 不含 `mtime/size` 致原地换图永久命中旧缓存；无界并发 WebP 解码/缩放易 OOM 和掉帧；`indexVersion` 无合并节流导致每完成 1 张封面全屏重算。
  - **方案**：查表法零分配 Hex 编码 + 按路径异步校验 `mtime/size` 失效；缩略图生成限制 `Dispatchers.IO.limitedParallelism(2)`；合并节流 `indexVersion` 通知并缓存计算后的候选结果减少主线程重复分词/模糊匹配。

---

### P0 实测基准对比（Before `HEAD 3bef0d1` vs After P0）

由同一套可复现基准套件 [`P0BenchmarkTest.kt`](../app/src/test/java/com/pocketdeck/launcher/P0BenchmarkTest.kt) 分别在未修改的 `HEAD (3bef0d1)` 与 P0 落地后的工作区运行测得（同时 16 项 [`P0RegressionTest.kt`](../app/src/test/java/com/pocketdeck/launcher/P0RegressionTest.kt) 回归用例全部通过）：

| 基准项 | 优化前 (`3bef0d1`) | 优化后 (P0) | 改善幅度 |
| --- | --- | --- | --- |
| `ScrapeLogStore.init()` 512KB 日志主线程耗时 | median **0.507ms** / mean 0.808ms | median **0.023ms** / mean 0.025ms | **~22.0x 更快**（零主线程读盘，异步载入 500 条） |
| 冷启构造 (`ScraperClient`+`CoverSource`+`MetadataRepository`) | **0.308ms**（eagerDirs=4, eagerOkHttp=true） | **0.067ms**（eagerDirs=0, eagerOkHttp=false） | **~4.6x 更快**（零同步 `mkdirs` / 零急切 OkHttp） |
| `LibraryIndexCache` 2000 款读写 & >64KB 描述 | save 1.662ms / load 1.317ms，**>64KB 静默丢弃 (`false`)** | save 1.626ms / load 1.324ms，**>64KB 完整恢复 (`true`)** | 保持 ~1.3ms 读写速度并修复 64KB 溢出丢缓存 |
| `CoverSource.candidates()` 1200 次调用（真实 5961 条 GBA 索引） | median **43.849ms** / mean 45.117ms | median **1.838ms** / mean 2.573ms | **~23.9x 更快**（命中缓存零重复分词匹配） |
| `preferredThumbOrOriginal()` 20,000 次路径查表 | median **114.622ms** / mean 125.120ms | median **18.742ms** / mean 18.351ms | **~6.1x 更快**（消除 `String.format` 与主线程 `SHA-1` 热点） |
| 全量刮削 `scrapeMissing()`（40 款 / 40ms RTT Mock） | **1910.079ms**（并发 1，进度发射 42 次） | **589.236ms**（并发 4，进度发射 6 次） | **~3.24x 提速**，StateFlow 进度发射 **-85.7%** |
| 刮削识别准确度（7 项真实 ROM/Switch/压缩包用例） | **0 / 7**（Switch 区码误判 `JA`） | **7 / 7**（Switch 区码正确 `US`） | **0/7 → 7/7 全部修复** |

---

## 二、P1 进阶性能优化（第二批）

- [x] **P1-1：增量扫描完善与 `gamelist.xml` 流式解析**
  - 仅在目录或本机应用发生真实变化时写 `library_index_v1.bin`；根目录散落 ROM 纳入快照复用；目录快照纳入封面子目录（`media`/`images`/`covers`/`boxart` 等）与外置 `gamelist.xml` mtime 指纹；`parseGamelistXml` 改 `XmlPullParser` 流式解析（2000 款 XML 实测约 ~10ms），静态化 `RomScanner` 处 `Regex`。
- [x] **P1-2：消除离线表 2~4 倍重复内存拷贝与无效 `I3` 二次 API 重试**
  - `OfflineGameDb` 按平台单飞锁加载并以只读零拷贝视图 + 懒构建分词索引避免 `MetadataRepository` 重复建表拷贝；`MetadataRepository` 收窄 I3 第二别名回退仅对支持按名查询、尚未命中负缓存且确实存在不同英文别名的中文 ROM 触发，彻底消除重复发包与负缓存击穿；GitHub Trees JSON 改流式字符扫描器（`CoverSource.parseGitHubTreeBoxarts`），避免 40~80MB DOM 堆尖峰且解析速度更快。
- [x] **P1-3：标题清洗与区域匹配打磨**
  - 修复 `TitleNormalizer.LEADING_INDEX` 误剥 `007 -`/`1941 -`/`1942 -`/`1943 -`/`2020 -` 等真名前缀并保持 `001 -`/`No.007 -`/`007.` 整合包编号正常剥离；修复 `mayHaveCjkOrPackSuffix` 漏掉 `.6`~`.9` 版本号后缀；`BoxartIndex` 多地区命中优先匹配 ROM 自身区域标签（兼容 No-Intro、GoodTools 及中文地区标）并抑制合集再版；`TitleNormalizer.selectFuzzyQueries` 优先保留英文别名避免被截断。
- [x] **P1-4：空闲 3D 呼吸动画节流 + `TopBar` 回调切 IO + `CoverGlow`/`WaveBackground` 微调**
  - `GalleryView`/`GridView`/`BubblesView` 统一复用 `rememberIdleBreathingClock`（20Hz 节流 + `STARTED` 生命周期自动暂停 + 零每帧 lambda）；`TopBar` 系统回调切 `statusScope` 并拆分为 `TopBarTabsSection` 与 `TopBarStatusCluster` 隔离高频重组；`CustomImageEffect` 将 `File.isFile`/`lastModified()` 移出主线程至 IO；`CoverGlow` 响应 `LocalReduceMotion`。
- [x] **P1-5：内存上限治理与网络层合并**
  - 新增线程安全 `BoundedLruCache`，将 `ScraperClient.memHits`/`hasheousHashCache`、`RomIdentityExtractor.memCache`、`GameTdbClient.tables` 改为有界并统一接入 `PocketDeckApp.onTrimMemory`；`GameTdbClient` 补全下载失败冷却窗口；Coil 与 Scraper 共享底层 `OkHttpClient` 的 `ConnectionPool` 与 `Dispatcher`，`HttpLogInterceptor` 仅在 `BuildConfig.DEBUG` 启用。

### P1 验证与实测指标

由 [`P1RegressionTest.kt`](../app/src/test/java/com/pocketdeck/launcher/P1RegressionTest.kt) 覆盖全部 5 组 P1 能力与回归验证：

| 验证项 | 实测结果 |
| --- | --- |
| GitHub Trees 流式解析（18,000 条目 / 3.36MB JSON，提取 6,000 Boxart） | DOM `JSONObject` **~45.9ms** vs 流式扫描器 **~26.1ms**（**~1.76x 更快**，零 `JSONObject`/`JSONArray` 整树堆分配，结果 100% 一致） |
| `gamelist.xml` 流式解析（2,000 款游戏 XML） | `XmlPullParser` 全量流式解析 **~10.4ms**（零整文件 `String` + `Regex` DOM 分配） |
| 增量快照覆盖范围 | 根目录散落 ROM 复用、封面子目录与外置 `gamelist.xml` 变更失效均通过验证 |
| 离线表并发与负缓存隔离 | `OfflineGameDb` 平台单飞锁 + 零拷贝视图 + 懒分词索引、I3 第二别名不击穿负缓存均通过验证 |
| 标题清洗与区域匹配 | `007`/`1941`/`1942`/`1943`/`2020` 真名保护、`.6`~`.9` 版本号剥离、多区 ROM 原区优先、英文别名防截断均通过验证 |
| 内存与网络层治理 | `BoundedLruCache` 淘汰与受保护裁剪、`onTrimMemory` 联动清理、`GameTDB` 失败冷却、Coil/Scraper 共享连接池与调度器、`BuildConfig.DEBUG` 日志门控均通过验证 |
