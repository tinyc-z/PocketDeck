# PocketDeck v0.6.0 屏幕兼容性修复与复测报告

> **测试环境**：官方 Android 模拟器（`PIXFI_API31`，Android 12 / API 31，ARM64）  
> **测试版本**：`PocketDeck-v0.6.0-b56-debug.apk`（`main` 分支）  
> **对比基线**：[v0.5.1 基线测试报告](../v0.5.1/README.md)

---

## 一、本次修复项与复测结果总览

| 编号 | 问题描述（v0.5.1） | 修复方案（v0.6.0） | 复测状态 |
| :---: | :--- | :--- | :---: |
| **P0-1** | 宽度 `< 460dp`（1:1 / 8:7 / 4:3 VGA / 9:16）时 `BottomBar` 右侧 `A Play` 文字被挤出或截断 | `BottomBar` 在 `maxWidth < 460.dp` 时隐藏 `View` 与 `Search` 文本标签，仅保留圆形图标徽章，确保 `A Play` 完整显示 | **已修复** |
| **P0-2** | 宽度 `< 460dp` 时 `Settings` 主题色行 `Color` 标签被挤掉、第 8 个颜色圆点截断 | `AccentDots` 使用 `FlowRow`，在 `screenWidthDp < 460` 时自动切换为 4×2 两行排布 | **已修复** |
| **P1** | 宽度 `< 460dp` 时 `Apps` 抽屉固定 5 列过窄导致 `Messages` 单字母换行 | `AppsScreen` 改为响应式列数（`<460dp` 用 4 列，常规 5 列，`>=900dp` 用 6 列），并同步 DPAD 上下导航步长 | **已修复** |
| **P2-1** | 8.8 寸平板（`1280×800 dp`）`Gallery` 卡片固定 `182dp` 偏左上、右下方大面积留白 | `HomeScreen` 按 `screenHeightDp * 0.42f`（并受限于宽度上限，区间 `182dp..320dp`）动态缩放画廊主卡与副卡高度 | **已修复** |
| **P2-2** | 矮屏（`320dp` 高）`GameMenu` 仅展示 2.5 项、`Edit` 被裁半 | `GameMenu` 在 `screenHeightDp <= 360` 时收紧弹窗内间距与按钮垂直 padding | **已修复** |

---

## 二、重点屏幕规格复测截图验证

### 1. 1:1 方形屏（`720×720` @ 280dpi，`411×411 dp`）

- **BottomBar**：`View` 与 `Search` 仅显示图标，右下角 **`A Play` 完整呈现**。
- **Settings**：`Color` 标签恢复完整显示，8 个主题色以 **4×2 网格**整齐排布无截断。
- **Apps**：自适应切换为 **4 列**，`Quick Share` 与 `Messages` 均单行完整显示。

| 1:1 画廊（BottomBar 修复） | 1:1 设置（4×2 主题色） | 1:1 应用抽屉（4 列布局） |
| :---: | :---: | :---: |
| ![1:1 Gallery](screenshots/1_1_square/01_gallery.png) | ![1:1 Settings](screenshots/1_1_square/02_settings.png) | ![1:1 Apps](screenshots/1_1_square/03_apps.png) |

---

### 2. 4:3 极小 VGA（`640×480` @ 240dpi，`427×320 dp`）

- **BottomBar**：`A Play` 完整显示，不再截断为 `A P`。
- **GameMenu**：`320dp` 高度下 `Play`、`Favorite`、`Details` 与 `Cancel` 全部完整居中展示，无半行裁切。

| 4:3 VGA 画廊 | 4:3 VGA 游戏菜单（紧凑间距） |
| :---: | :---: |
| ![4:3 VGA Gallery](screenshots/4_3_vga_240dpi/01_gallery.png) | ![4:3 VGA GameMenu](screenshots/4_3_vga_240dpi/04_gamemenu.png) |

---

### 3. 8:7 准方竖屏（`1080×1240`）& 9:16 竖屏（`1080×1920`）

- `BottomBar` 在 `432dp`（Pocket DMG）与 `411dp`（竖屏）下均完整展示 `A Play`；9:16 竖屏下画廊卡片也按可用宽度比例适度放大，不再局限于左上角小卡片。

| 8:7 准方竖屏画廊 | 9:16 竖屏画廊 |
| :---: | :---: |
| ![8:7 Gallery](screenshots/pocket_dmg_8_7/01_gallery.png) | ![9:16 Gallery](screenshots/9_16_portrait/01_gallery.png) |

---

### 4. 8.8 寸平板大屏（`2560×1600`）& 16:9 主流横屏回归（`1920×1080`）

- **8.8 寸平板 (`1280×800 dp`)**：画廊主卡放大至 `320dp` 高、副卡同步等比放大，视觉比例饱满协调。
- **16:9 主流横屏 (`768×432 dp`)**：回归验证完全保持原设计比例（`View` 与 `Search` 正常带文字、卡片 `182dp`），无任何副作用。

| 8.8 寸平板画廊（比例放大） | 16:9 主流横屏回归 |
| :---: | :---: |
| ![Tablet Gallery](screenshots/tablet_8_8_y700/01_gallery.png) | ![16:9 Gallery](screenshots/16_9_fhd/01_gallery.png) |
