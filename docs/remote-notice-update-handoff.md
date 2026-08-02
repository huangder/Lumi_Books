# 远程通知与更新弹窗交接文档

本文说明 LumiBooks 如何通过 GitHub 仓库中的静态 JSON 配置，在 App 启动时展示通知、普通更新弹窗、强制更新弹窗。

## 1. 配置文件位置

客户端读取的远程配置地址是：

```text
https://raw.githubusercontent.com/huangder/Lumi_Books/main/docs/app-config.json
```

对应仓库文件：

```text
docs/app-config.json
```

注意：客户端不使用自定义域名，避免部分手机网络无法访问自定义域名。

## 2. 客户端行为

App 启动时会请求 `docs/app-config.json`，并按以下优先级弹窗：

```text
App 更新弹窗 > 通知弹窗 > 用户协议/隐私政策弹窗
```

其中 App 更新弹窗又分为：

- 普通更新：有“下载”和“稍后”。
- 强制更新：只有“下载新版本”，不能点外部关闭，不能按返回关闭。

通知弹窗结构：

```text
标题
通知内容色块，可滚动
收到
```

更新弹窗结构：

```text
标题
更新说明
“更新日志”标题
更新日志色块，可滚动
下载 / 稍后
```

液态玻璃主题与普通主题都使用同一个弹窗组件，色块透明度会按主题自动适配。

## 3. 当前 JSON 格式

```json
{
  "config_version": 1,
  "generated_at": "2026-07-28T00:00:00Z",
  "latest_version": "1.1.00",
  "latest_version_code": 5,
  "release_url": "https://github.com/huangder/Lumi_Books/releases/latest",
  "terms_version": 0,
  "privacy_version": 0,
  "update": {
    "latest_version_code": 5,
    "latest_version_name": "1.1.00",
    "download_url": "https://github.com/huangder/Lumi_Books/releases/latest",
    "title": "发现新版本",
    "message": "新版本已经发布，建议前往下载更新。",
    "changelog": "暂无更新日志。",
    "force_update_below_version_code": 0
  },
  "notice": {
    "id": "notice-20260728-placeholder",
    "enabled": false,
    "min_version_code": 1,
    "max_version_code": 5,
    "title": "通知",
    "message": "这里填写通知内容。"
  }
}
```

## 4. 如何发布普通通知

编辑 `docs/app-config.json` 的 `notice`：

```json
"notice": {
  "id": "notice-20260728-bug-tip",
  "enabled": true,
  "min_version_code": 1,
  "max_version_code": 5,
  "title": "重要通知",
  "message": "当前版本存在一个已知问题。我们已经在新版本中修复，建议尽快更新。"
}
```

说明：

- `id` 必须每次通知都换新的。用户点“收到”后，App 会记录这个 ID，不会重复展示同一个通知。
- `enabled` 为 `true` 才展示。
- `min_version_code` / `max_version_code` 控制哪些版本能看到通知。
- `message` 可以写多行，弹窗内容区域会滚动。

关闭通知：

```json
"enabled": false
```

## 5. 如何发布普通更新弹窗

假设当前线上 App 是：

```text
versionCode = 5
versionName = 1.1.00
```

新版本是：

```text
versionCode = 6
versionName = 1.1.01
```

配置：

```json
"update": {
  "latest_version_code": 6,
  "latest_version_name": "1.1.01",
  "download_url": "https://github.com/huangder/Lumi_Books/releases/latest",
  "title": "发现新版本",
  "message": "新版本已经发布，建议前往下载更新。",
  "changelog": "- 修复启动时偶发崩溃\n- 优化阅读页滑动体验\n- 改进部分设置项文案",
  "force_update_below_version_code": 0
}
```

效果：

- `versionCode < 6` 的用户打开 App 会看到更新弹窗。
- 弹窗有“下载”和“稍后”。
- 用户点“下载”会打开 `download_url`。

## 6. 如何发布强制更新

如果旧版本有严重问题，需要所有旧版本必须更新到 `versionCode = 6`，配置：

```json
"update": {
  "latest_version_code": 6,
  "latest_version_name": "1.1.01",
  "download_url": "https://github.com/huangder/Lumi_Books/releases/latest",
  "title": "必须更新",
  "message": "当前版本存在严重问题，请下载新版本后继续使用。",
  "changelog": "- 修复严重问题\n- 提升应用稳定性",
  "force_update_below_version_code": 6
}
```

效果：

- `versionCode < 6` 的用户打开 App 会看到强制更新弹窗。
- 弹窗只有“下载新版本”。
- 不能点外部关闭。
- 不能按返回键关闭。
- 点下载后会打开 `download_url`，但如果用户返回旧 App，弹窗仍会继续保留。

取消强制更新：

```json
"force_update_below_version_code": 0
```

## 7. 更新日志字段写法

支持单个字符串：

```json
"changelog": "- 修复 A 问题\n- 优化 B 功能"
```

也支持数组：

```json
"changelog_items": [
  "修复 A 问题",
  "优化 B 功能",
  "提升稳定性"
]
```

客户端会把数组展示为项目符号列表。

## 8. 涉及代码文件

```text
app/src/main/java/com/huangder/lumibooks/util/UpdateChecker.kt
app/src/main/java/com/huangder/lumibooks/MainActivity.kt
app/src/main/java/com/huangder/lumibooks/ui/components/AppUpdateDialog.kt
app/src/main/java/com/huangder/lumibooks/ui/components/RemoteNoticeDialog.kt
app/src/main/java/com/huangder/lumibooks/data/local/DataStoreManager.kt
app/src/main/java/com/huangder/lumibooks/ui/settings/SettingsUiState.kt
app/src/main/java/com/huangder/lumibooks/ui/settings/SettingsViewModel.kt
```

## 9. 注意事项

1. 这是“启动时拉配置”，不是实时推送；用户只有打开 App 才会看到。
2. 没有内置这套逻辑的更老版本无法被远程控制。
3. 判断更新以 `versionCode` 为准，`versionName` 只用于展示。
4. 紧急强更时，应先发布 GitHub Release，再更新 `docs/app-config.json`。
5. `docs/app-config.json` 走 GitHub Raw 地址；提交到 `main` 后，客户端下一次启动即可拉取。
## 10. 远程字体托管（按需下载字体）

仿宋字体已从 APK 移除，改为应用内按需下载。客户端读取 `docs/app-config.json` 的 `fonts` 段（数组，可扩展更多字体），按 `key` 匹配下载。

### 首次上线操作步骤

1. 将 `fandol_fang.ttf`（8.8MB，低于 GitHub raw 100MB 限制）放入仓库 `docs/fonts/` 目录（与现有 MiSans 字体同目录），提交推送到 main。
2. 确认 `docs/app-config.json` 的 `fonts` 段已包含 fangsong 条目（对应 PR 已写好，见下）。提交推送后客户端下一次启动/打开字体设置即可生效，**无需发版**。

```json
"fonts": [{
  "key": "fangsong",
  "version": 1,
  "fileName": "fandol_fang.ttf",
  "sizeBytes": 8824084,
  "urls": [
    "https://raw.githubusercontent.com/huangder/Lumi_Books/main/docs/fonts/fandol_fang.ttf",
    "https://cdn.jsdelivr.net/gh/huangder/Lumi_Books@main/docs/fonts/fandol_fang.ttf"
  ]
}]
```

### 更新字体（换版本）

1. 新文件覆盖 `docs/fonts/fandol_fang.ttf`（或新增 URL）。
2. `fonts` 条目中 `version` 加 1，并按新文件更新 `sizeBytes`（必须是字节数，客户端会做大小校验）。
3. 推送 main 即生效；客户端检测到 version 不一致会重新下载。

### 新增字体

在 `fonts` 数组追加条目：`key` 为 App 内字体标识（与 `ThemeSettingsSheet` 中 FontSelector 的 key 对应），`urls` 至少一个 https 地址（目前白名单：raw.githubusercontent.com / cdn.jsdelivr.net）。App 内还需在字体选择 UI 增加对应条目并复用 `FontDownloadManager` 的下载流程。

### 降级行为

- 字体未下载/下载失败时，阅读器自动回退系统字体（所有渲染路径均有 `Typeface.DEFAULT` 兜底）。
- 用户选择仿宋时若本地无文件，按钮显示"下载中…"；失败显示"下载失败"，点击可重试。
