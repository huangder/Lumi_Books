# Git 构建产物跟踪清理

## 原因

仓库原有的 `/build` 规则只忽略根目录，未覆盖 Android 模块的 `app/build`，`app/release` 也没有忽略规则，导致调试截图、编译产物和 APK 被 Git 跟踪。

## 修改

- 使用 `**/build/` 忽略根目录及所有模块的 Gradle 构建目录。
- 使用 `/app/release/` 忽略本地 Release APK 输出目录。
- 从 Git 索引取消跟踪 `app/build` 和 `app/release`，本地文件保持不变。

## 结果

后续构建、截图验证和 APK 生成不会再次自动进入 Git 提交范围。
