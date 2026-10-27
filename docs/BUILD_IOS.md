# iOS 构建

已用 Xcode27.0（27A5252f）、Swift6.4、iOS27 SDK、XcodeGen2.46.0。最低iOS16.4；SwiftProtobuf锁定1.38.1。内部工程/Module/Scheme保留TiebaPure，桌面显示TiebaLite。

在根目录执行：

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test scripts/config.test.mjs
bash scripts/build-ios.sh debug
bash scripts/build-ios.sh ipa
```

脚本同步 App.xcconfig 并生成 Xcode 工程；Release归档在 `.build/TiebaLite.xcarchive`。未签名IPA保存至 `releases/TiebaLite-1.0-unsigned-<SHA前12位>/TiebaLite-1.0-unsigned.ipa`，不替换当前可安装的签名IPA。包含主应用及Open In扩展，未签名。不能把ZIP检查、归档成功当成已可直接安装。

具备已安装模拟器时：

```sh
/Users/y/toolchains/bin/xcrun simctl list devices available
SIMULATOR_UDID='<已存在的模拟器UDID>' bash scripts/build-ios.sh test
```

本机没有iOS模拟器运行时；当前版本已在已登记iPhone运行宿主单元测试，覆盖真实UITextView、完整SwiftUI编辑页及中文输入焦点。独立UI Runner受免费签名应用数量限制，未计为通过。详见docs/releases/1.1.md。

签名须同时匹配 `dev.anyu.tiebalite` 和 `dev.anyu.tiebalite.open-in`。本机已通过现有开发者账号的Automatic Signing为主应用和扩展签名；其他环境须使用自己的团队和匹配profiles，可用Xcode配置，或：

```sh
DEVELOPMENT_TEAM='<Team ID>' bash scripts/build-ios.sh signed-archive
bash scripts/build-ios.sh export '/绝对路径/ExportOptions.plist'
```

签名导出先使用`.build/ios-export.*`临时目录，最终只把`TiebaLite-<版本>.ipa`存入工作区根目录下的`lastest/`，与APK放在一起。旧签名IPA自动归档到同级`releases/`，相同SHA256不重复保留，新IPA不会移走当前APK。

ExportOptions须与实际开发、Ad Hoc或分发方式匹配。不得将证书私钥、密码或描述文件放进Git/源码zip。当前1.1/build67已从最终签名IPA原位安装到已登记iPhone；签名期限和验收范围见docs/releases/1.1.md。

## 1.0/build61 设备签名记录

本轮使用本机开发者账号与Automatic Signing，为主应用和Open In扩展生成匹配描述文件，开发签名归档、原位安装和启动已在已登记iPhone上完成。开发IPA仅支持描述文件中的设备且有有效期限，不属于通用分发。签名文件和设备备份留在本机忽略目录。

需要自动配置时，在已有Xcode账号环境运行签名归档可加 `-allowProvisioningUpdates -allowProvisioningDeviceRegistration CODE_SIGN_STYLE=Automatic`，DEVELOPMENT_TEAM以自己的账号为准。更新已有应用前先停止本应用并从appDataContainer复制和校验备份；使用devicectl install app原位更新，不卸载、不清理设备容器。

iOS26/27使用系统原生TabView标签栏，不隐藏/替换系统标签栏，也不拦截其拖动选择；旧系统保留兼容悬浮栏。网页示意不代表原生玻璃的实际交互。
