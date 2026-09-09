# 生词本（Android）

一个离线优先、个人使用的极简英汉生词本。

## 手机安装

稳定版本通过 [GitHub Releases](https://github.com/xsy070709/wordbook/releases/latest) 发布。
手机端请下载文件名为 `wordbook-版本号.apk` 的附件；GitHub 自动生成的
`Source code` 压缩包不能安装。

当前版本：[下载 wordbook-0.4.0.apk](https://github.com/xsy070709/wordbook/releases/download/v0.4.0/wordbook-0.4.0.apk)。
发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 已实现

- 从内置 ECDICT 英汉词库精确查询和前缀联想
- 持久保存最近查词记录，支持快速再次查询和一键清空
- 收藏生词，按英文字典序展示
- 手动填写英文词或短语、中文释义、可选音标和所属分册；重复词可直接更新
- 搜索英文单词或中文释义
- 查看中文释义、音标、加入日期和所属分册
- 为每个生词添加、修改或清空个人批注，并可按批注内容检索
- 新建、重命名、删除分册，以及移动生词
- 从其他 Android 应用的选中文本菜单或“分享”菜单进入查词
- 个人数据与只读词典数据分离，删除分册不会删除生词
- 通过 JSON 文件导入、导出完整生词本，便于迁移和批量操作
- 打印全部生词或指定分册，可选择仅打印原词或同时打印释义

JSON 文件格式见 [docs/IMPORT_EXPORT_FORMAT.md](docs/IMPORT_EXPORT_FORMAT.md)。导入、导出和打印入口位于“生词本”页面右上角；打印指定分册前，请先在页面顶部选中该分册。

## 首次搭建（Windows）

项目自带可复现的安装脚本，会把 JDK 17、Android SDK 和便携版 Android Studio
放进项目的 `.tools` 目录，不修改系统级 Java 配置。运行脚本前请先阅读并接受
[Android SDK License](https://developer.android.com/studio/terms)：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-android.ps1 -AcceptAndroidSdkLicense
```

若不需要 IDE，可追加 `-SkipAndroidStudio`。

## 词库

安装包默认内置完整 ECDICT 英汉词库，共约 76.8 万个带中文释义的可检索词条，
包含音标、常用词、短语、派生词与大量专有名词。词库查询完全离线。

### 重新生成完整词库

ECDICT 数据不重复提交进本仓库。下载或克隆
[skywind3000/ECDICT](https://github.com/skywind3000/ECDICT) 后运行：

```powershell
python .\scripts\build_dictionary.py C:\path\to\ECDICT\ecdict.csv
```

生成的 `app/src/main/assets/database/dictionary.db` 会自动打包进 APK。

## 构建应用

```powershell
.\gradlew.bat test assembleDebug
```

调试 APK 位于 `app\build\outputs\apk\debug\app-debug.apk`。连接已开启 USB 调试的
安卓手机后，可运行 `.tools\android-sdk\platform-tools\adb.exe install -r <apk>` 安装。

## 数据来源

英汉词典使用 [ECDICT](https://github.com/skywind3000/ECDICT)，MIT License。
早期精简数据生成工具使用的 [pocket_dict_5000](https://github.com/FirepadCN/pocket_dict_5000)
同为 MIT License，工具继续保留用于制作小体积版本。
