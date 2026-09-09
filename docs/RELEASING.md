# 发布 APK 到 GitHub

正式给手机下载的 APK 放在 GitHub Releases，不把 `dist/` 或 APK 文件提交进 Git 历史。

## 发布前

1. 确认版本需求已经冻结。
2. 在 `app/build.gradle.kts` 中递增 `versionCode` 和 `versionName`。
3. 使用同一签名密钥执行测试并生成 APK。
4. 用 `apksigner verify --verbose` 验证安装包。
5. 计算并记录 SHA-256。

在尚未配置正式发布签名之前，只从固定的开发电脑生成安装包。不同电脑临时生成的
debug 签名不一致，会导致 Android 无法覆盖安装新版。

## 创建 Release

先登录 GitHub CLI：

```powershell
gh auth login -h github.com -w
```

完成构建和验证后，以 0.3.0 为例：

```powershell
gh release create v0.3.0 .\dist\wordbook-0.3.0.apk `
  --title "生词本 0.3.0" `
  --generate-notes
```

发布成功后，在手机浏览器打开仓库的 **Releases** 页面，只下载 `.apk` 文件；
`Source code (zip)` 和 `Source code (tar.gz)` 不是安装包。

## GitHub Actions

`.github/workflows/android-ci.yml` 会在推送和拉取请求时运行单元测试、构建临时调试
APK，并把它保留为短期 Actions artifact。正式移动端下载仍以经过本机签名验证的
Release 附件为准。
