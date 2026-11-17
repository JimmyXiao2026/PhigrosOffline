# Phigros 存档离线分析 App

**Phi Rank Query 离线版** — 完全离线的 Phigros 存档分析工具，界面、查询卡片、评级图标与字体仿照开源机器人插件 [Catrong/phi-plugin](https://github.com/Catrong/phi-plugin)，共四个视图：**B30**、**X30（1 Good）**、**越30**、**所有成绩**；唯一区别是数据来源改为本地离线读取，不联网、不请求 Phigros 服务器。

## 核心功能
- 通过 **Shizuku** 获取 adb shell 权限，直接读取 Phigros 本地存档（不需要联网、不需要登录、不碰服务器）。
- 存档路径（依次尝试）：
  - `/data/data/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml`（Android 9+ 常用，实测可用）
  - `/storage/emulated/0/Android/data/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml`（部分机型）
- 玩家昵称来自本地账号缓存 `.userdata`（LeanCloud `_User` 对象的明文 JSON 落盘副本，依次尝试）：
  - `/storage/emulated/0/Android/data/com.PigeonGames.Phigros/files/.userdata`
  - `/data/data/com.PigeonGames.Phigros/files/.userdata`
  - 取 `nickname`，回退 `authData.taptap.name`，并附带 `shortId`；`.userdata` 缺失或解析失败时回退 playerprefs 里的 `playerID`。该文件是本地已落盘缓存，读取属于离线范围，App 不读取也不上传其中的会话票据。
- 计算逻辑与 phi.iris.al 完全一致：
  - 单曲 RKS：`acc==100 → 定数`；`acc<70 → 0`；否则 `定数×((acc-55)/45)²`
  - B30 = 按 RKS 取 top27 + 3 个 φ(100%准) 之和 / 30
  - 界面：沿用 phi-plugin 暗色卡片，标题用 Aldrich 发光字体，正文用 PHI 混合字体；列表卡片含曲绘、名次徽章、曲名、难度定数徽章（EZ/HD/IN/AT/Legacy 配色）、acc 条、分数与评级图标（φ / FC / V / S / A / B / C / F / NEW），B30 / X30 / 越30 / 所有成绩四个 Tab。
- **歌曲封面**：从 Phigros 官方 APK 的 Unity AssetBundle（Addressables，`assets/aa/Android/*.bundle`）提取全部 327 首曲绘（原始纹理 2048×1080，含按难度变体），由 UnityPy 解码并自动翻正后按原比例压缩为 1024×540 JPEG 内嵌进本 App，完全离线显示；详情页完整 16:9 展示、不裁切，随页面上滑滚出。
- 曲目定数表（difficulty.tsv）、曲名表（info.tsv）与谱面物量表（notes.tsv：每难度 combo / Drag / Flick 数，提取自 phi-plugin notesInfo.json）内置在 App 内，离线可用。

## X30（1 Good）与越级榜口径
- **X30（1 Good Mode）**：复刻 `fCompute.comJust1Good`。阈值分数 `tar = 900000×(1 - 0.35/combo) + 100000`，`|score - tar| ≤ 2` 判定为恰好 1 个 Good；按 RKS 降序取前 30。combo 取自内嵌 notes.tsv。
- **越级榜**：复刻 `fCompute.getOverLevelLine`。越级线 `= 98.5 + 1.5×Drag/combo`（默认不计 Flick，与插件命令不带 flick 参数时一致）；`acc < 越级线` 判为越级，按 RKS 降序取前 30。卡片右侧显示该曲越级线，并按线高低分六档配色（红 / 橙 / 绿 / 青 / 紫），与插件 suggestType 一致。
- 未实现 p30 的 P（满分）、FC（非满分 Full Combo）独立榜；插件「越级 +flick」计入 Flick 的选项在 App 内固定为不计 Flick。

## 歌曲详情
- 点击列表卡片进入详情页，可切换 **EZ / HD / IN / AT**（仅显示曲库中有定数的难度；本地无记录的难度标注「未游玩」）。
- 当前记录展示：分数、acc、Full Combo、评级图标、单曲 RKS、物量（combo / Drag / Flick）。
- **判定明细（理论估算）**：本地存档只保存分数、acc、FC，不存判定个数与最高连击。Perfect / Good 在「未 Perfect 的 note 全部记为 Good（无 Bad/Miss）」的最优假设下按 `acc/100 = (P + 0.65·G)/N` 反推；Bad、Miss 与最高连击存档未保存，显示「—」。
- **提升建议**：按物量 N 估算再提升 0.01% acc 所需操作——把 Good 打成 Perfect 每个提升 `35/N` 个百分点，把 Bad/Miss 打成 Perfect 每个提升 `100/N` 个百分点，分别向上取整给出个数。
- **无法推分**：复刻 phi-plugin `getSuggest` / `fCompute.suggest` 口径，按 B30 第 27 名门槛与总 RKS 四舍五入余量反推打进榜单所需 acc；所需 acc ≥100%（即使满分也够不到门槛），或所需 acc 已不高于当前 acc（已达标），即判为「无法推分」，列表卡片与详情页同步标注，灰色显示。可推分曲目卡片右侧显示反推所需 acc（`>> 目标 acc%`），区别于左侧当前 acc。
- 数据约束：本地每条难度仅保留最近一次成绩，没有历史「分数最高 / acc 最高」分榜；详情页展示的即本地当前记录。

## 安装与使用
1. 安装并启动 **Shizuku**（`moe.shizuku.manager`），可用 adb / 无线调试 / root 方式启动。
   - adb 方式：`adb shell sh /data/local/tmp/shizuku_starter.sh`
2. 安装本 App（APK 已用密钥签名，可直接安装）。
   - 注意：若设备上装有旧版且提示签名冲突，请先卸载旧版再安装（本次签名密钥为重新生成）。
3. 打开 App，首次会弹出 Shizuku 授权对话框，点击允许（授予 adb shell 权限）。
4. 点「分析存档」即可切换查看 B30、X30（1 Good）、越30 与所有成绩。

## 从源码构建
- 环境：JDK 17、Android SDK 35（compileSdk 35 / minSdk 26 / targetSdk 34）、Gradle 8.13（仓库内已带 Wrapper）。
- 在仓库根目录下执行：
  - Windows：`gradlew.bat assembleRelease`
  - macOS / Linux：`./gradlew assembleRelease`
- 产物：`app/build/outputs/apk/release/app-release.apk`。
- **签名**：Release 签名信息不入库。在 `local.properties`（已被 .gitignore 忽略）中配置，或使用同名环境变量：
  - `keystore.file`、`keystore.password`、`keystore.alias`、`keystore.keyPassword`
  - 未配置签名时，Release 包回退使用默认 debug 签名；自行打包请使用自己的密钥库。

## 说明
- 本地存档里每个 `<string>` 键名和值都单独用 Phigros **自定义块加密**（非标准 AES）混淆。App 内置与该算法 1:1 一致的解密实现（依据开源项目 yt6983138/PhigrosLibraryCSharp 移植）：键 `playerID` 是玩家 ID，键形如 `曲名.作者.版本.Record.难度` 的值是成绩 JSON `{"s":分数,"a":acc,"c":是否FC}`。
- 完全离线，不联网、不需 Session Token、不涉及服务器数据。
- minSdk 26（Android 8.0+），targetSdk 34。

## 版权与免责
- 本项目为非官方第三方学习工具，与 PigeonGames / Phigros 官方无关联。
- 内嵌曲绘、字体、曲目定数与谱面物量等素材版权归原权利人所有，仅用于离线本地展示与研究，请勿用于商业用途；如涉版权问题请联系移除。
- 分析口径参考开源项目 [Catrong/phi-plugin](https://github.com/Catrong/phi-plugin) 与 phi.iris.al 的公开实现；存档解密思路参考 yt6983138/PhigrosLibraryCSharp。
- 本 App 仅读取设备本地已落盘的存档缓存，不读取、不上传任何会话票据或账号数据。
