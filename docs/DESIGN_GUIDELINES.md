# PocketDeck UI 设计规范指南

> 本文档用于统一 PocketDeck 客户端的界面尺寸、布局与交互规范，供所有开发者及 AI 编程助手（Claude / Gemini 等）在新增或重构界面时严格遵循。

---

## 1. 目标设备与屏幕基准

PocketDeck 主要运行在 Android 掌机与手机横/竖屏环境，设计时需同时兼顾以下典型逻辑尺寸（dp）：

| 设备形态 | 典型逻辑分辨率（宽 × 高） | 断点分类 |
|---|---|---|
| 竖屏手机 / 极窄窗口 | `360–430dp × 740–920dp` | `isNarrow`（`maxWidth < 460.dp`） |
| 小屏 4:3 / 3:2 掌机 | `533–640dp × 360–480dp` | `isCompact`（`460.dp <= maxWidth < 720.dp`） |
| 主流 16:9 / 20:9 横屏掌机 | `720–915dp × 360–430dp` | 标准横屏（`maxWidth >= 720.dp`） |
| 极矮横屏（如 720p 高 DPI） | `maxHeight < 340.dp` | `isShort`（需压缩纵向间距与按钮高度） |

---

## 2. 弹窗（Dialog / Overlay）尺寸与布局规范

### 2.1 四级标准宽度阶梯（严禁自造魔法数字）

全 App 所有卡片式弹窗必须归入以下 **4 个标准宽度档位** 之一，严禁随意使用 `400.dp`、`420.dp`、`460.dp`、`480.dp`、`500.dp`、`540.dp`、`560.dp`、`748.dp` 等零散数值：

| 档位 | 规范宽度（`maxWidth`） | 语义定位 | 适用场景 | 典型组件示例 |
|---|---|---|---|---|
| **S · 紧凑型** | **`340.dp`** | 轻浮层 / 快捷菜单 | 单列短操作菜单、快捷 HUD 调节浮层 | `ActionMenuDialog`、`QuickHudOverlay`、应用快捷菜单 |
| **M · 标准型** | **`440.dp`** | 常规交互 / 表单确认 | 二次确认、删除提示、单表单输入、按键捕获/冲突、PIN/凭据输入、短列表选择、任务进度 | `ConfirmActionDialog`、`NameDialog`、`DeleteFileConfirmDialog`、`BatchDeleteConfirmDialog`、`KeyBindCaptureDialog`、`KeyBindConflictDialog`、`LocalSendPinDialog`、`ScraperCredentialDialog`、`SystemTabEditDialog`、`GameCategoryPickerDialog`、`FileOpProgressDialog`、`ScrapeTaskDialog`、`AudioPlayerDialog`、`ShareMethodDialog`、`SharePeerPickerDialog`、`MoveFolderDialog`、版本更新弹窗 |
| **L · 宽版单栏** | **`520.dp`** | 信息密集 / 长列表选择器 | 复杂属性详情、手动刮削与搜索列表、启动器/核心配置、长列表多选、传输历史、文本/字体/压缩包预览 | `GamePropertiesDialog`、`ManualScrapeDialog`、`ScrapeCoverDialog`、`LauncherPickerDialog`、`ExportLogDirDialog`、`FontPickerDialog`、`CategoryPlatformsDialog`、`InstalledAppPickerDialog`、`HiddenGamesDialog`、`ArchivePreviewDialog`、`LanServiceControlDialog`、`FontViewerDialog`、`ShareTransferHistoryDialog`、`WebTransferHistoryDialog`、`TextFileViewerEditorDialog` |
| **XL · 双栏展示** | **`640.dp`** | 图文双栏 / 大型展示面板 | 左右双栏图文展示弹窗（如游戏详情）、多列向导工作台；**全 App 弹窗绝对上限，禁止超过 `640.dp`** | `GameDetailDialog`、`AutoCreateRomDirsOverlay` |
| **Full · 全屏特例** | `fillMaxSize()` | 沉浸媒体查看器 | 图片/视频等全屏查看，不套用卡片宽度上限 | `ImageViewerDialog` |

> **关键约束**：在主流 16:9 掌机（宽 `720–850dp`）上，`640.dp` 约占屏宽 `75%–88%`，能保留舒适的左右呼吸感；超过 `640.dp`（如 `720–750dp`）会贴近屏幕边缘，破坏弹窗的悬浮层级感。

---

### 2.2 响应式宽度写法（禁止写死 `Modifier.width(X.dp)`）

**严禁**对弹窗根容器直接使用固定宽度 `Modifier.width(440.dp)`（在竖屏或小屏窗口下会导致贴边或溢出）。

所有弹窗必须使用「外边距 + 最大宽度限制 + 撑满可用空间」的响应式组合：

```kotlin
// 推荐标准写法（使用 BoxWithConstraints 或外层 padding）
Modifier
    .padding(horizontal = outerHPad, vertical = outerVPad)
    .widthIn(max = 440.dp) // 按档位取 340.dp / 440.dp / 520.dp / 640.dp
    .fillMaxWidth()
    .heightIn(max = maxDialogHeight)
```

对于双栏展示型弹窗（**XL · `640.dp`**，如 `GameDetailDialog`）：
- 横屏（`maxWidth >= 460.dp`）：
  - 弹窗宽度：`val dialogW = (maxWidth - outerHPad * 2).coerceAtMost(640.dp)`
  - 左侧视觉栏占比：`38%–41%`（推荐 `(dialogW * 0.40f).coerceIn(180.dp, 260.dp)`），保证右侧信息与操作栏拥有 `>= 360.dp` 的充裕空间。
  - 弹窗高度：`val maxTargetH = if (hasCompactContent) 284.dp else 340.dp`，且不超过 `maxHeight * 0.86f`（极矮屏 `< 340.dp` 可放宽至 `0.90f`）。
- 窄屏（`maxWidth < 460.dp`）：自动退化为上下结构，宽度走 **M 档（`widthIn(max = 440.dp).fillMaxWidth()`）**。

---

### 2.3 安全外边距与高度上限

| 规则项 | 窄屏（`< 460.dp`） | 标准横屏（`>= 460.dp`） | 极矮屏（`maxHeight < 340.dp`） |
|---|---|---|---|
| 水平外边距 `outerHPad` | `16.dp` | `24.dp` | 同宽度规则 |
| 垂直外边距 `outerVPad` | `12.dp` | `16.dp` | `10.dp` |
| 最大高度 `maxDialogHeight` | `maxHeight * 0.88f` | `maxHeight * 0.86f` | `maxHeight * 0.90f` |

- 弹窗内部有可变长度内容（简介、列表、表单）时，内容区必须可滚动（`verticalScroll` / `LazyColumn`），标题栏与底部操作栏固定不随内容滚走。

---

### 2.4 统一视觉容器与交互基线

1. **优先复用 `DialogPanelCard`**：普通单栏弹窗应直接使用 `ui/components/DialogCloseButton.kt` 中的 `DialogPanelCard`，自动获得一致的圆角、阴影、背景色与右上角关闭钮。
2. **统一圆角**：所有弹窗外轮廓统一使用 `RoundedCornerShape(DeckBrand.RadiusXL)`（`22.dp`），禁止私自写 `24.dp`、`20.dp` 或 `18.dp`。
3. **统一底板与遮罩**：
   - 全屏半透明遮罩：`DeckColors.CardShadow` + `.scrimTapDismiss(onDismiss)`。
   - 弹窗底色：`DeckColors.DialogBg`；边框：`1.dp` `DeckColors.Line`；海拔阴影：`cardSurface(shape, elevation = 6.dp..10.dp)`。
4. **统一关闭按钮**：右上角使用 `DialogCloseButton`（`size = 30.dp`，距顶部与右侧等距 `14.dp`）。
5. **手柄导航**：每个弹窗必须支持 `B / Back` 关闭、方向键聚焦与 `A / Confirm` 触发，焦点环统一使用 `Modifier.brandFocusRing(...)`。

---

### 2.5 代码常量（Token）与自动化守卫

统一尺寸常量已在 `app/src/main/java/com/pocketdeck/launcher/ui/components/DialogCloseButton.kt` 中落地，并由单测 `DialogDesignGuidelineTest` 强约束（覆盖 `DeckDialogSize` 档位、`fillMaxWidth()`、22dp 圆角、640dp 绝对上限及遮罩 `scrimTapDismiss`）。所有新增或修改的弹窗**必须直接引用以下常量**，禁止手写魔法数字：

```kotlin
object DeckDialogSize {
    val WidthS = 340.dp   // 快捷菜单 / 轻浮层 HUD
    val WidthM = 440.dp   // 确认框 / 单表单 / 短选择 / 进度
    val WidthL = 520.dp   // 复杂属性 / 长列表 / 刮削 / 文件预览
    val WidthXL = 640.dp  // 双栏图文展示（全 App 上限）
}
```

---

## 3. 色彩系统与主题令牌（`DeckColors`）

PocketDeck 采用 **Sunset Arcade** 动态双模主题体系（`ui/theme/Color.kt`），支持浅色/深色模式以及 8 套 `ThemeAccent` 预设（`Sunset`、`Berry`、`Ocean`、`Mint`、`Lemon`、`Sakura`、`Grape`、`Graphite`）实时联动。

### 3.1 核心语义令牌

业务界面必须通过 `DeckColors.*`（或 `LocalDeckColors.current`）取色，禁止直接硬编码浅色或深色十六进制色值：

| 语义层级 | 令牌 | 用途说明 |
|---|---|---|
| 页面底色 | `DeckColors.Background` | 根页面底色（浅色暖白 / 深色深紫） |
| 卡片表面 | `DeckColors.Surface` | 白卡 / 深紫卡主容器（配合 `cardSurface` 使用） |
| 次级表面 | `DeckColors.SurfaceAlt` | 输入框、次级按钮、Chip、内嵌分组底色 |
| 弹窗底板 | `DeckColors.DialogBg` | 弹窗面板底色（边框使用 `DeckColors.DialogBorder` / `DeckColors.Line`） |
| 分割与描边 | `DeckColors.Line` | 发丝分割线（`1.dp`）与默认中性边框 |
| 主/次/标签文字 | `TextPrimary` / `TextSecondary` / `TextLabel` / `TextMeta` | 按信息主次分级，自动保证明暗对比度 |
| 品牌主色 | `DeckColors.Ruby` / `DeckColors.AccentSoft` | 强调文字、图标底座、选中态柔和底色 |
| 品牌三色渐变 | `BrandStart` / `BrandMid` / `BrandEnd`（或 `DeckBrand.brandBrush`） | 主按钮、选中 Tab/Chip、开关开启态、进度条、手柄焦点环 |
| 阴影与遮罩 | `DeckColors.CardShadow` | 卡片投影基准色与弹窗全屏半透明遮罩 |
| 语义状态色 | `StatusDanger` / `StatusSuccess` / `StatusInfo` / `StatusWarning` | 错误/删除/低电、在线/充电/上传、传输/下载、暂停/警告（浅深模式自动适配高对比度） |

### 3.2 硬编码色值约束

- **禁止**在 `ui/` 业务页面中直接写 `Color(0xFF...)` 作为背景、文字、边框、卡片或常规状态色（主题/动态背景渲染模块 `ui/theme/`、`ui/components/background/` 以及暗色终端语法高亮除外）。
- **允许例外**：品牌渐变底上的纯白前景（`Color.White`）、纯透明占位（`Color.Transparent`）与半透明遮罩微调。
- 业务状态反馈统一使用 `DeckColors.StatusDanger`、`DeckColors.StatusSuccess`、`DeckColors.StatusInfo`、`DeckColors.StatusWarning`（柔和底色取对应令牌 `.copy(alpha = 0.12f..0.16f)`）。

---

## 4. 字体与排版体系（`DeckType`）

排版规范定义在 `ui/theme/Type.kt`，默认使用可变字体 **`InterFamily`**（显式注册 `400`–`800` 字重与 `opsz` 光学尺寸），并通过 `LocalDeckFontFamily` 支持用户切换系统自定义字体。

### 4.1 语义字号阶梯

所有文本必须基于 `DeckType.*`（禁止直接裸写 `TextStyle(fontSize = ...)` 导致丢失全局自定义字体）：

| 令牌 | 字重 / 字号 / 行高 | 适用场景 |
|---|---|---|
| `DeckType.gameTitle` | SemiBold · `22sp / 26sp` | 主页焦点游戏标题、全屏页主标题 |
| `DeckType.menuTitle` / `settingsSection` | SemiBold · `20sp / 24sp` | 弹窗标题（`DeckSheetTitle`）、设置分组大标题 |
| `DeckType.tabSelected` / `tabInactive` | Bold / Normal · `16sp` | 顶栏分类标签 |
| `DeckType.menuButton` / `settingsValue` / `settingsNav` / `listItem` | Normal–SemiBold · `16sp / 20sp` | 菜单按钮、列表行主标题、导航项 |
| `DeckType.bottomBar` / `statusInfo` / `metaValue` | Normal–Medium · `14sp` | 底栏操作项、顶栏时钟电量、游戏元数据值 |
| `DeckType.settingsLabel` | Normal · `12sp / 16sp` | 副标题、说明文案、列表辅助描述（`Text` 默认样式） |
| `DeckType.metaLabel` | Normal · `10sp / 12sp` | 元数据极小标签、状态微标（`DeckBadge`） |

### 4.2 截断与换行规范

- 单行标题、按钮文本、Chip、标签与状态徽标**必须**显式指定 `maxLines = 1, overflow = TextOverflow.Ellipsis`，防止窄屏或多语言（如俄语/德语/英文长词）撑破布局。
- 列表行副标题、弹窗副标题建议限制 `maxLines = 2, overflow = TextOverflow.Ellipsis`；长简介或日志文本放入可滚动容器。

---

## 5. 圆角、阴影与表面层级（`DeckBrand` / `DeckShapes`）

### 5.1 统一圆角刻度

圆角统一引用 `DeckBrand`（`ui/theme/Brand.kt`）与 `DeckShapes`（`ui/theme/Shape.kt`）：

| 令牌 | 数值 | 适用场景 |
|---|---|---|
| `DeckBrand.RadiusS` | `10.dp` | 行首图标底座（`DeckIconBadge`）、紧凑徽标/内嵌小块 |
| `DeckBrand.RadiusM` | `14.dp` | 列表焦点行（`brandFocusRow`）、输入框、子面板、标签胶囊（`DeckShapes.tabPill`） |
| `DeckBrand.RadiusL` | `18.dp` | 标准白卡分组容器（`DeckCard`）、通用卡片表面（`cardSurface`） |
| `DeckBrand.RadiusXL` | `22.dp` | **全 App 弹窗统一外圆角**（`DeckShapes.menuPanel`、`DialogPanelCard`） |
| `DeckPillShape` | `50%` | 胶囊按钮（`DeckButton`）、`DeckChip`、`DeckBadge`、底栏悬浮岛 |

### 5.2 卡片表面与投影性能策略

卡片与弹窗底板统一使用 `Modifier.cardSurface(shape, elevation)`，严禁在长列表中直接堆砌 `Modifier.shadow(...)`：

- **低海拔列表卡片（`elevation < 6.dp`）**：内部自动走 `drawWithCache` 轻量化投影，避免批量创建 OpenGL RenderNode 导致切页或快速滚动掉帧。
- **高海拔弹窗/浮层（`elevation >= 6.dp`）**：自动切换硬件 `Modifier.shadow`，保证大卡片拥有自然高斯模糊阴影、底部无生硬色块。
- **零开销降级**：当 `elevation == 0.dp` 或 `LocalReduceMotion.current == true` 时，自动跳过投影仅绘制 `DeckColors.Surface`。

---

## 6. 基础控件与复用规范（`DeckControls` vs `DeckMaterialCompat`）

PocketDeck 为控制包体与渲染开销，**完全移除了 `compose-material3` 依赖**，采用两层自研轻量组件：

1. **Foundation 兼容底层（`ui/components/DeckMaterialCompat.kt`）**：提供 `Text`、`TextField`、`Slider`、`HorizontalDivider`、`CircularProgressIndicator`、`LinearProgressIndicator`、`Button` / `OutlinedButton`。
2. **品牌共享控件库（`ui/components/DeckControls.kt`）**：基于 Sunset Arcade 视觉语言封装的高层控件。

### 6.1 优先复用清单

新增或重构页面时，**优先使用 `DeckControls` 共享控件**，避免重复手写样式：

| 组件 | 说明 |
|---|---|
| `DeckButton` | 标准胶囊按钮，支持 `Primary`（品牌渐变）/ `Secondary`（`SurfaceAlt`）/ `Danger`（`AccentSoft` + `Ruby`）/ `Ghost` 四种样式及 `compact` 紧凑态 |
| `DeckIconButton` | 圆形图标按钮（支持 `filled` / `elevated` / 默认浅底与焦点环） |
| `DeckSwitch` / `DeckCheckbox` / `DeckRadio` | 品牌渐变态开关、复选框、单选钮（内置触屏音效与降动效适配） |
| `DeckCard` / `DeckSectionHeader` / `DeckDivider` | 分组白卡容器、大写小号分组标题（`11sp Bold`）、`1.dp` 发丝分割线 |
| `DeckRow` / `DeckIconBadge` | 标准设置/列表行（最小高度 `48.dp`，内置 `brandFocusRow`）与 `32.dp` 图标底座 |
| `DeckChip` / `DeckBadge` / `DeckProgressBar` | 筛选/模式胶囊标签、状态微标、品牌渐变进度条 |
| `DeckScreenHeader` / `DeckSheetTitle` / `DeckEmptyState` | 全屏面板页头、弹窗标题行、空状态占位 |

> **外观与交互解耦约定**：`DeckControls` 组件只负责视觉呈现，点击事件与手柄焦点链由调用方通过 `Modifier`（`dpadNavItem` / `gamepadClickable` / `soundClickable`）传入，不要在共享外观控件内部写死特定页面的导航逻辑。

---

## 7. 图标体系（`DeckIcons` / `HomeIcons` / `PanelIcons`）

全 App 统一使用自绘矢量线性图标，遵循 **Lucide 24×24 几何规范**（默认线宽 `1.75f`、`StrokeCap.Round`、`StrokeJoin.Round`），零外部重型图标库依赖，通过 `DeckIcon(icon, tint, size)` 渲染：

| 命名空间 | 文件位置 | 职责范围 |
|---|---|---|
| `DeckIcons` | `ui/components/DeckIcons.kt` | 通用核心图标（主页、游戏库、媒体、设置、文件等） |
| `HomeIcons` | `ui/components/icons/HomeIcons.kt` | 主页顶栏、底栏操作项与视图模式专用图标 |
| `PanelIcons` | `ui/components/icons/PanelIcons.kt` | 弹窗、局域网/快传、游戏库、应用列表、编辑与设置面板补充图标 |

- **规范要求**：顶栏、底栏、按钮与菜单项统一使用线性矢量图标，**禁止**使用 Emoji 或纯文字热键胶囊替代功能图标。

---

## 8. 手柄焦点、触控音效与防穿透规范

### 8.1 点击与手柄导航修饰符选型（`ui/components/GamepadFocus.kt`）

| 修饰符 / 工具 | 适用场景 | 核心能力 |
|---|---|---|
| `DpadNavController` + `Modifier.dpadNavItem(nav, id)` | **首选**：含 `verticalScroll` / `LazyColumn` 的弹窗与全屏页面 | 显式有序焦点链，不依赖 Android Touch Mode 下的 Compose 2D 空间寻焦，避免从桌面进入无焦点或滚到底部丢焦点；自动 `BringIntoView` 与触屏音效 |
| `Modifier.gamepadClickable(...)` | 独立弹窗按钮、`DialogCloseButton` 等非列表控件 | 支持 D-pad/Tab 聚焦、自动 `BringIntoView`、同时消费确认键 `KeyDown/KeyUp` 防止双触发与冒泡、带触屏音效 |
| `Modifier.soundClickable(...)` / `soundClickableNoIndication(...)` | 纯触控区域（不参与 D-pad 焦点链） | 自动接入 `KeySound.playTouch(context)`，保证全局触屏反馈一致 |

- **安全请求焦点**：所有 `FocusRequester.requestFocus()` 调用必须改用 `requestFocusSafely()`，避免节点尚未组合或已被移除时抛出 `IllegalStateException`。

### 8.2 焦点视觉分级

| 修饰符 | 适用场景 | 视觉表现 |
|---|---|---|
| `Modifier.brandFocusRing(...)` | 封面卡、胶囊按钮、圆形图标按钮、开关 | 品牌三色渐变外环 + 间隙 + 可选辉光（`LocalReduceMotion` 下自动关闭辉光） |
| `Modifier.brandFocusRow(...)` | 密集列表行、设置行、菜单选项 | `Surface` 底色 + `1.5.dp` 品牌渐变描边 |
| `Modifier.brandBorder(active)` | 输入框、筛选框、选中子卡片 | 激活态 `1.4.dp` 品牌渐变描边，非激活态 `0.75.dp` `DeckColors.Line` |

### 8.3 遮罩与全屏覆盖层防穿透

1. **弹窗半透明遮罩**：统一使用 `.background(DeckColors.CardShadow).scrimTapDismiss(onDismiss)`，**禁止**直接挂 `.clickable { onDismiss() }`（会产生水波纹且误入 D-pad 焦点链）。
2. **全屏覆盖页**（如设置页、游戏库页、应用页、编辑页、全屏查看器等叠加在 `HomeScreen` 之上的页面）：根容器**必须**挂载 `Modifier.blockOverlayTouchThrough()`（`ui/theme/Brand.kt`），拦截空白区域触控事件，防止点击穿透到底层误触游戏封面或应用图标。

---

## 9. 降动效与性能基线（`LocalReduceMotion`）

PocketDeck 面向各档位 Android 掌机，全局提供 `LocalReduceMotion`（`ui/theme/Brand.kt`）开关用于「减少动态效果 / 换取最低延迟与最佳帧率」。新增任何动画或高开销视觉效果时必须遵循：

1. **状态过渡动画**：读取 `val reduceMotion = LocalReduceMotion.current`，为 `true` 时直接返回目标值（跳过 `animateFloatAsState` / `animateColorAsState` / 弹簧动画），参考 `DeckSwitch` 与主页各视图实现。
2. **光晕与阴影**：`brandFocusRing` 与 `cardSurface` 已内置 `LocalReduceMotion` 降级；自定义 `Canvas` / `drawBehind` 辉光、流光或毛玻璃层（如 `BottomBar` 悬浮岛、`GalleryView` 芯片）也必须检查 `reduceMotion` 并提供低耗静态回退。
3. **绘制期零分配**：高频重绘修饰符（焦点环、卡片阴影、进度条、自定义背景）优先使用 `Modifier.drawWithCache` 缓存 `Brush`、`Stroke` 与几何参数。

---

## 10. 自动化守卫与新增/重构 UI 自检清单

### 10.1 现有自动化守卫

- **单元测试**：`app/src/test/java/com/pocketdeck/launcher/DialogDesignGuidelineTest.kt`（强约束 `DeckDialogSize` 档位、响应式 `.fillMaxWidth()`、禁固定宽/魔法宽、`22.dp` 圆角、`640.dp` 绝对上限及遮罩 `scrimTapDismiss`）。
- **真机/模拟器回归清单**：`docs/REGRESSION_TEST_CASES.md`（用例 `APP-16` 覆盖四档弹窗横竖屏抽检）。

### 10.2 新增或重构界面自检清单

- [ ] **多端断点**：已兼顾竖屏窄屏（`< 460dp`）、4:3 小屏掌机、16:9 横屏及极矮屏（`maxHeight < 340dp`），无截断或溢出。
- [ ] **弹窗尺寸**：卡片式弹窗使用 `DeckDialogSize.WidthS/M/L/XL` + `.widthIn(max = ...).fillMaxWidth()` + `DeckBrand.RadiusXL`（`22.dp`），不超 `640.dp`。
- [ ] **色彩令牌**：全部使用 `DeckColors.*` / `DeckBrand.brandBrush`，未新增硬编码浅/深色 `Color(0x...)`。
- [ ] **排版规范**：全部基于 `DeckType.*`，单行文本已加 `maxLines = 1, overflow = TextOverflow.Ellipsis`。
- [ ] **控件与图标**：优先复用 `DialogPanelCard`、`DeckControls` 与 `DeckIcon`（`DeckIcons` / `HomeIcons` / `PanelIcons`），未使用 Emoji 替代图标。
- [ ] **手柄导航**：支持 `B / Back` 返回、方向键有序寻焦（滚动页用 `DpadNavController`）与 `A / Confirm` 触发，使用 `requestFocusSafely()`。
- [ ] **防穿透与音效**：弹窗遮罩用 `scrimTapDismiss`，全屏覆盖页根容器用 `blockOverlayTouchThrough()`，点击操作带触屏音效。
- [ ] **降动效与性能**：新增动画/光晕/投影已适配 `LocalReduceMotion`，长列表卡片不使用高耗 `Modifier.shadow`。
- [ ] **测试验证**：本地运行 `./gradlew testDebugUnitTest --tests "com.pocketdeck.launcher.DialogDesignGuidelineTest"` 通过。

