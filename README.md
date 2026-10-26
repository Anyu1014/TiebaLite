# TiebaLite

基于 infinityf4p/TiebaPure-iOS 的独立双端二次开发工程。版本 1.4.15（59），应用标识 `dev.anyu.tiebalite`。iOS 保留原生 SwiftUI 工程与原有功能；Android 使用 Kotlin、Compose、Material 3 移植主要业务。未与百度、贴吧官方或其他同名项目建立隶属、授权或认可关系。

2026-10-25～26 修改：独立名称、应用标识和链接协议，接入用户指定白角桌面图标，新增 Android 客户端、构建脚本、协议验证与交付文档。上游作者、感谢名单和许可证保留于 `ios/README.md`、`ios/LICENSE`、`ios/LICENSES/`。固定上游提交及来源见 [UPSTREAM](docs/UPSTREAM.md)。本工程代码按 [GPL-3.0-only](LICENSE) 提供；用户图标与第三方动态内容的来源单独见 [资源清单](docs/RESOURCES.md)。

## 当前交付

- `releases/android/TiebaLite-1.4.15-release.apk`：本地独立密钥签名的 Android Release 包；另保留 Debug 测试包。
- `releases/ios/TiebaLite-1.4.15-unsigned.ipa`：Release 归档生成的未签名 IPA，须用匹配两个 Bundle ID 的证书和描述文件签名后安装。
- `releases/TiebaLite-1.4.15-source.zip`：包含双端源码、协议、资源、许可和构建脚本的对应源码快照。
- `releases/manifest.json`、`releases/SHA256SUMS`：对应提交、工具信息和产物校验值。构建产物与私钥不进入 Git 或源码包。

Android 已通过真实匿名接口、专用模拟器安装启动及存储测试。iOS 已通过编译、测试目标编译和归档；本机无 iOS 模拟器运行时，未做安装启动验收。登录后的发帖、资料修改等写操作尚未用真实账号验证。完整证据见 [验证报告](docs/VERIFICATION.md)。

## 开发入口

统一工具箱为 `/Users/y/toolchains`，开始前阅读其 `README.md`。复用 Xcode 27、JDK 21、Android SDK 35/36、Gradle 8.14.3；双端配置在 `app-config.json`。

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test scripts/config.test.mjs
bash scripts/build-ios.sh ipa
bash scripts/build-android.sh lintDebug
```

Android Release 签名、iOS 签名与模拟器测试另见 [iOS 构建](docs/BUILD_IOS.md)、[Android 构建](docs/BUILD_ANDROID.md)。

## 文档

- [架构](docs/ARCHITECTURE.md)、[功能与双端差异](docs/FEATURES.md)、[开发约定](docs/DEVELOPMENT.md)
- [已知问题](docs/KNOWN_ISSUES.md)、[验证报告](docs/VERIFICATION.md)、[进度](docs/PROGRESS.md)
- [协议移植笔记](docs/ANDROID_PROTOCOL_NOTES.md)、[许可证审查](docs/LICENSE_AUDIT.md)、[依赖](docs/DEPENDENCIES.md)、[资源](docs/RESOURCES.md)

右侧预览由 `previews/index.html` 提供：首页/帖子可切换。iOS 为上游参考截图；Android 为本机模拟器实际截图，均为静态画面，不能代替设备验收。

```sh
python3 -m http.server 8768 --bind 127.0.0.1 --directory previews
```

保留完整只读上游仓库 `original-source/`，开发使用 `ios/` 和 `android/`。没有推送、上传、上架或安装用户真机。
