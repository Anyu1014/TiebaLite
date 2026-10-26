# iOS 构建

已用 Xcode27.0（27A5252f）、Swift6.4、iOS27 SDK、XcodeGen2.46.0。最低iOS16.4；SwiftProtobuf锁定1.38.1。内部工程/Module/Scheme保留TiebaPure，桌面显示TiebaLite。

在根目录执行：

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test scripts/config.test.mjs
bash scripts/build-ios.sh debug
bash scripts/build-ios.sh ipa
```

脚本同步 App.xcconfig 并生成 Xcode 工程；Release归档在 `.build/TiebaLite.xcarchive`，IPA在 `releases/ios/TiebaLite-1.0-unsigned.ipa`。包含主应用及Open In扩展，未签名。不能把ZIP检查、归档成功当成已可直接安装。

具备已安装模拟器时：

```sh
/Users/y/toolchains/bin/xcrun simctl list devices available
SIMULATOR_UDID='<已存在的模拟器UDID>' bash scripts/build-ios.sh test
```

本机本轮没有任何iOS模拟器运行时。仅执行了Generic Simulator的build-for-testing，未跑XCTest/UITest。右侧iOS图为上游参考，非本版运行截图。

签名须同时匹配 `dev.anyu.tiebalite` 和 `dev.anyu.tiebalite.open-in`。现有本机描述文件不匹配，未自动创建Apple账号资料或上传签名请求。准备正确团队与profiles后可用Xcode配置，或：

```sh
DEVELOPMENT_TEAM='<Team ID>' bash scripts/build-ios.sh signed-archive
bash scripts/build-ios.sh export '/绝对路径/ExportOptions.plist'
```

ExportOptions须与实际开发、Ad Hoc或分发方式匹配。不得将证书私钥、密码或描述文件放进Git/源码zip。本次没有安装用户iPhone。

## 1.0/build61 设备签名记录

本轮使用本机开发者账号与Automatic Signing，为主应用和Open In扩展生成匹配描述文件，开发签名归档、原位安装和启动已在已登记iPhone上完成。开发IPA仅支持描述文件中的设备且有有效期限，不属于通用分发。签名文件和设备备份留在本机忽略目录。

需要自动配置时，在已有Xcode账号环境运行签名归档可加 `-allowProvisioningUpdates -allowProvisioningDeviceRegistration CODE_SIGN_STYLE=Automatic`，DEVELOPMENT_TEAM以自己的账号为准。更新已有应用前先停止本应用并从appDataContainer复制和校验备份；使用devicectl install app原位更新，不卸载、不清理设备容器。

iOS26/27使用系统原生TabView标签栏，不隐藏/替换系统标签栏，也不拦截其拖动选择；旧系统保留兼容悬浮栏。网页示意不代表原生玻璃的实际交互。
