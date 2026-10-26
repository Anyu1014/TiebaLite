# iOS 构建

已用 Xcode27.0（27A5252f）、Swift6.4、iOS27 SDK、XcodeGen2.46.0。最低iOS16.4；SwiftProtobuf锁定1.38.1。内部工程/Module/Scheme保留TiebaPure，桌面显示TiebaLite。

在根目录执行：

```sh
source /Users/y/toolchains/env.sh
/Users/y/toolchains/bin/node --test scripts/config.test.mjs
bash scripts/build-ios.sh debug
bash scripts/build-ios.sh ipa
```

脚本同步 App.xcconfig 并生成 Xcode 工程；Release归档在 `.build/TiebaLite.xcarchive`，IPA在 `releases/ios/TiebaLite-1.4.15-unsigned.ipa`。包含主应用及Open In扩展，未签名。不能把ZIP检查、归档成功当成已可直接安装。

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
