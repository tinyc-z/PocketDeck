# PocketDeck 安卓游戏掌机屏幕比例兼容性测试报告（全尺寸覆盖版）

> **测试环境**：官方 Android 模拟器（`PIXFI_API31`，Android 12 / API 31，ARM64）  
> **测试基线包**：`PocketDeck-v0.5.1-b48-debug-baseline.apk`  
> **测试范围**：共覆盖 **11 种** 市面常见安卓游戏掌机与平板/手机手柄屏幕规格，涵盖 **16:9 / 16:10 / 20:9 / 3:2 / 4:3 / 1:1 / 8:7 / 9:16** 比例，实测 **Gallery（画廊）、List（列表）、Grid（网格）、Bubbles（气泡）** 四大视图及 **Settings（设置）、Apps（应用抽屉）、GameMenu（游戏菜单）、Search（搜索栏）** 细节。

---

## 一、全尺寸测试矩阵与结论总览

| 屏幕比例 | 测试分辨率 & DPI | 逻辑尺寸 (dp) | 代表掌机 / 设备 | 综合评级 | 核心兼容性发现 |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **16:9 主流横屏** | `1920×1080` @ 400dpi | `768×432 dp` | Odin 2 / Retroid Pocket 5 / AYANEO Pocket S | **优秀** | 基准比例，全视图、底栏、设置、弹窗无任何截断 |
| **16:9 紧凑 720p** | `1280×720` @ 320dpi | `640×360 dp` | 5 寸 720p 掌机 / RP3+ / RG505（同逻辑尺寸） | **良好** | TopBar 右侧标签渐隐正常；因高度 `360dp`，画廊底部日期胶囊隐藏 |
| **16:10 宽屏** | `1920×1200` @ 400dpi | `768×480 dp` | 16:10 掌机 / 串流设备 | **优秀** | 垂直空间更充裕，画廊日期胶囊、游戏菜单、搜索栏均完整居中 |
| **20:9 超宽横屏** | `2400×1080` @ 420dpi | `914×411 dp` | Razer Edge / 手机 + 拉伸手柄 | **优秀** | 横向空间充裕，TopBar 全量平台标签完整展示 |
| **16:10 平板大屏** | `2560×1600` @ 320dpi | `1280×800 dp` | 联想拯救者 Y700 等 8.8 寸平板 + 手柄 | **待优化** | **卡片为固定 dp 尺寸**，在 `1280×800 dp` 大屏仅占左上角约 35% 区域，右下方大面积留白 |
| **3:2 紧凑小屏** | `960×640` @ 320dpi | `480×320 dp` | AYANEO Pocket Micro | **良好** | 底栏单行完整；画廊日期胶囊隐藏；设置左侧 `System` 需滚动 |
| **4:3 复古 HD** | `960×720` @ 320dpi | `480×360 dp` | Anbernic RG406H / RG405M | **良好** | 底栏与设置 8 个主题色圆点完整显示；画廊日期胶囊隐藏 |
| **4:3 极小 VGA** | `640×480` @ 240dpi | `427×320 dp` | Retroid Pocket 2S / 3.5 寸 4:3 掌机 | **存在问题** | **底栏 `A Play` 截断为 `A P`**；**设置第 8 个主题色半截断**；**游戏菜单仅显 2.5 项**（`Edit` 被裁半） |
| **8:7 准方竖屏** | `1080×1240` @ 400dpi | `432×496 dp` | AYANEO Pocket DMG | **存在问题** | **底栏 `A Play` 截断为 `A P`**；设置 `Color` 标签被挤掉；**Apps 抽屉 `Messages` 单字母换行** |
| **1:1 方形屏** | `720×720` @ 280dpi | `411×411 dp` | Anbernic RG Cube / ZPG A1 / RGB30 | **存在问题** | **底栏 `Play` 文字完全挤出**；**设置第 8 个主题色截断**；**Apps `Messages` 单字母换行** |
| **9:16 竖屏** | `1080×1920` @ 420dpi | `411×731 dp` | 竖屏掌机 / 手机竖持 | **存在问题** | 同 `411dp` 窄屏三项问题；List 模式左栏游戏名过窄截断；下半屏大量留白 |

---

## 二、主流横屏规格实测（16:9 / 16:10 / 20:9 / 平板大屏）

### 1. 16:9 主流横屏（`1920×1080` @ 400dpi，`768×432 dp`）

作为设计基准比例，**画廊、列表、网格、气泡**四种视图以及**设置、应用抽屉**均表现完美：

| 画廊视图（Gallery） | 网格视图（Grid） |
| :---: | :---: |
| ![16:9 Gallery](screenshots/16_9_fhd/03_grid.png) | ![16:9 Grid](screenshots/16_9_fhd/01_gallery.png) |

| 列表视图（List） | 气泡视图（Bubbles） |
| :---: | :---: |
| ![16:9 List](screenshots/16_9_fhd/04_bubbles.png) | ![16:9 Bubbles](screenshots/16_9_fhd/02_list.png) |

| 设置面板（Settings） | 应用抽屉（Apps） |
| :---: | :---: |
| ![16:9 Settings](screenshots/16_9_fhd/05_settings.png) | ![16:9 Apps](screenshots/16_9_fhd/06_apps.png) |

---

### 2. 16:9 紧凑 720p（`1280×720` @ 320dpi）& 16:10 宽屏（`1920×1200` @ 400dpi）

- **16:9 720p (`640×360 dp`)**：TopBar 右侧 `OTHER` 标签自动渐隐避免撞上时间；卡片比例协调，仅底部日期胶囊因 `360dp` 高度被省略。
- **16:10 (`768×480 dp`)**：所有细节（包括 `GameMenu` 弹窗与 `Search` 搜索栏 + 软键盘）均舒展无遮挡。

| 16:9 720p 画廊（TopBar 渐隐正常） | 16:10 画廊（日期胶囊完整） |
| :---: | :---: |
| ![720p Gallery](screenshots/16_9_hd_720p/01_gallery.png) | ![16:10 Gallery](screenshots/16_10_wuxga/01_gallery.png) |

| 16:10 游戏菜单（GameMenu） | 16:10 搜索栏（Search + 输入法） |
| :---: | :---: |
| ![16:10 GameMenu](screenshots/16_10_wuxga/07_gamemenu.png) | ![16:10 Search](screenshots/16_10_wuxga/08_search.png) |

---

### 3. 20:9 超宽横屏（`2400×1080`）& 8.8 寸平板大屏（`2560×1600` @ 320dpi）

- **20:9 (`914×411 dp`)**：TopBar 全量平台标签清晰排布，List 视图左右分栏协调。
- **8.8 寸平板 (`1280×800 dp`)**：Settings（甚至能一次性平铺全部 7 个背景卡片）和 Apps 抽屉表现良好；但**主界面画廊卡片使用固定 `dp` 尺寸，导致在大屏上仅占据左上角小块区域，视觉重心偏左上**。

| 20:9 画廊视图 | 20:9 列表视图 |
| :---: | :---: |
| ![20:9 Gallery](screenshots/20_9_ultrawide/03_grid.png) | ![20:9 List](screenshots/20_9_ultrawide/04_bubbles.png) |

| 8.8寸平板画廊（卡片偏左上、下方留白大） | 8.8寸平板设置（7个背景项全展开） |
| :---: | :---: |
| ![Tablet Gallery](screenshots/tablet_8_8_y700/01_gallery.png) | ![Tablet Settings](screenshots/tablet_8_8_y700/05_settings.png) |

---

## 三、复古小屏横屏实测（3:2 / 4:3 HD / 4:3 VGA）

### 1. 3:2 紧凑（`960×640`，`480×320 dp`）& 4:3 HD（`960×720`，`480×360 dp`）

- 两者逻辑宽度均为 `480dp`，**BottomBar 完整不溢出**，Settings 8 个主题色圆点完整。
- 区别在于高度：`320dp` 高度下 Settings 左侧第 7 项 `System` 需向下滚动，而 `360dp` 高度下 7 个分类首屏基本全可见。

| 3:2 画廊视图（`480×320 dp`） | 3:2 列表视图（`480×320 dp`） |
| :---: | :---: |
| ![3:2 Gallery](screenshots/3_2_compact/03_grid.png) | ![3:2 List](screenshots/3_2_compact/04_bubbles.png) |

| 4:3 HD 画廊视图（`480×360 dp`） | 4:3 HD 设置面板（`480×360 dp`） |
| :---: | :---: |
| ![4:3 HD Gallery](screenshots/4_3_retro/03_grid.png) | ![4:3 HD Settings](screenshots/4_3_retro/05_settings.png) |

---

### 2. 4:3 极小 VGA（`640×480` @ 240dpi，`427×320 dp`）⚠️

代表 **Retroid Pocket 2S** 等 3.5 寸 640×480 掌机，同时触发了**窄屏（`427dp`）+ 矮屏（`320dp`）**双重极限边界：
1. **BottomBar 截断**：右下角 `A Play` 被截断为 **`A P`**。
2. **Settings 主题色截断**：`Color` 标签被挤掉，第 8 个深灰圆点被卡片右边界切掉一半。
3. **GameMenu 弹窗高度局促**：`320dp` 高度下，游戏菜单仅能展示 `Play`、`Favorite` 和半行 `Edit`（需滚动查看 `Properties`）。

| 4:3 VGA 画廊（底栏 `A P` 截断） | 4:3 VGA 设置（主题色半截断） |
| :---: | :---: |
| ![4:3 VGA Gallery](screenshots/4_3_vga_240dpi/01_gallery.png) | ![4:3 VGA Settings](screenshots/4_3_vga_240dpi/05_settings.png) |

| 4:3 VGA 游戏菜单（`Edit` 裁半） | 4:3 VGA 搜索栏（可用但输入法占大半） |
| :---: | :---: |
| ![4:3 VGA GameMenu](screenshots/4_3_vga_240dpi/07_gamemenu.png) | ![4:3 VGA Search](screenshots/4_3_vga_240dpi/08_search.png) |

---

## 四、方屏与竖屏掌机实测（8:7 / 1:1 / 9:16）⚠️

### 1. 8:7 准方竖屏（`1080×1240` @ 400dpi，`432×496 dp` — AYANEO Pocket DMG）

- `432dp` 宽度下，Settings 的 8 个主题色圆点刚好塞进卡片（无截断，但 `Color` 标签仍消失），`GameMenu` 完整展示。
- 但 **BottomBar 依然截断为 `A P`**，**Apps 抽屉 5 列依然触发 `Message / s` 单字母换行**。

| 8:7 画廊（底栏 `A P` 截断） | 8:7 设置（8色勉强塞下但无标签） |
| :---: | :---: |
| ![8:7 Gallery](screenshots/pocket_dmg_8_7/01_gallery.png) | ![8:7 Settings](screenshots/pocket_dmg_8_7/05_settings.png) |

| 8:7 游戏菜单（完整展示） | 8:7 应用抽屉（`Message / s` 换行） |
| :---: | :---: |
| ![8:7 GameMenu](screenshots/pocket_dmg_8_7/07_gamemenu.png) | ![8:7 Apps](screenshots/pocket_dmg_8_7/06_apps.png) |

---

### 2. 1:1 方屏（`720×720` @ 280dpi，`411×411 dp`）& 9:16 竖屏（`1080×1920`，`411×731 dp`）

在 `411dp` 宽度下，窄屏问题最集中：
1. **BottomBar `Play` 文字完全消失**（仅剩红色 `A` 圆圈）。
2. **Settings 第 8 个主题色被裁出卡片外**。
3. **Apps 固定 5 列导致 `Messages` 断字为 `Message / s`**。
4. 9:16 竖屏下 List 视图左侧游戏名截断为 `Super Mario ...`。

| 1:1 方屏画廊（`Play` 消失） | 1:1 方屏设置（第8色被裁） |
| :---: | :---: |
| ![1:1 Gallery](screenshots/1_1_square/03_grid.png) | ![1:1 Settings](screenshots/1_1_square/05_settings.png) |

| 9:16 竖屏网格视图 | 9:16 竖屏列表视图（左侧标题截断） |
| :---: | :---: |
| ![9:16 Grid](screenshots/9_16_portrait/01_gallery.png) | ![9:16 List](screenshots/9_16_portrait/04_bubbles.png) |

---

## 五、优化建议汇总（供后续重构参考）

| 优先级 | 影响范围 | 问题描述 | 建议修改位置与方案 |
| :---: | :--- | :--- | :--- |
| **P0** | 宽度 `< 460dp`（1:1 / 8:7 / 9:16 / 640×480） | `BottomBar` 右侧 `A Play` 被挤出屏幕 | [BottomBar.kt](file:///Users/ibcker/Desktop/dev/PocketDeck/app/src/main/java/com/pocketdeck/launcher/ui/components/BottomBar.kt)：窄屏下缩小按键项间距/字号，或对次要按钮折叠文字只保留徽章图标，优先保证 `A Play` 完整可见 |
| **P0** | 宽度 `< 460dp` | `Settings` 主题色行 `Color` 标签消失、第 8 个色块截断 | [SettingsSections.kt](file:///Users/ibcker/Desktop/dev/PocketDeck/app/src/main/java/com/pocketdeck/launcher/ui/settings/SettingsSections.kt)：改用 `FlowRow` 换行或窄屏自适应缩小圆点直径/间距 |
| **P1** | 宽度 `< 460dp` | `Apps` 抽屉固定 5 列导致 `Message / s` 断字 | [AppsScreen.kt](file:///Users/ibcker/Desktop/dev/PocketDeck/app/src/main/java/com/pocketdeck/launcher/ui/apps/AppsScreen.kt)：按宽度响应式切为 `4` 列（`< 460dp`）/ `5` 列 / `6` 列（平板），并限制 `maxLines = 1` + `Ellipsis` |
| **P2** | 大屏平板（`1280×800 dp`） | `Gallery` 卡片固定 `dp` 偏左上、下方大面积留白 | [GalleryView.kt](file:///Users/ibcker/Desktop/dev/PocketDeck/app/src/main/java/com/pocketdeck/launcher/ui/home/GalleryView.kt)：卡片高度按可用高度比例动态计算（如占中间内容区 `55%~65%` 并设置合理上下限） |
| **P2** | 矮屏（`320dp` 高） | `GameMenu` 在 `320dp` 下仅露 2.5 项、`Edit` 裁半 | [GameMenu.kt](file:///Users/ibcker/Desktop/dev/PocketDeck/app/src/main/java/com/pocketdeck/launcher/ui/components/GameMenu.kt)：矮屏时适当减小菜单项垂直 padding，或增加滚动条提示 |
