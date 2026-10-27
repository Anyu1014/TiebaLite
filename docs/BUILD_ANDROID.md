# Android 构建

已用JDK21.0.12.1、Gradle8.14.3、AGP8.13.0、Kotlin2.2.20、Compile/Target SDK36；Min SDK26。唯一SDK目录 `/Users/y/toolchains/android-sdk`。Gradle wrapper复用已有工具，依赖来自Google/Maven Central。

```sh
source /Users/y/toolchains/env.sh
bash scripts/build-android.sh lintDebug assembleDebugAndroidTest
```

Debug包归档到 `releases/TiebaLite-1.1-debug-<SHA前12位>/TiebaLite-1.1-debug.apk`，不替换当前Release。脚本执行单测和APK签名校验。网络需要本机代理时按环境传入Gradle的http/https.proxyHost/proxyPort，不将代理凭据硬编码。

Release配置从密钥和密码文件环境变量读取本地密钥和密码文件，不在源码中保存密码。本机既有私有密钥在 `.build/signing/tiebalite-release.jks`，alias `tiebalite`，密码文件同目录 `release-password.txt`；未来升级要复用这份密钥。

```sh
TIEBALITE_ANDROID_MODE=release \
TIEBALITE_KEYSTORE="$PWD/.build/signing/tiebalite-release.jks" \
TIEBALITE_KEY_PASSWORD_FILE="$PWD/.build/signing/release-password.txt" \
bash scripts/build-android.sh lintRelease
```

输出 `lastest/TiebaLite-1.1.apk`，与当前签名IPA放在同一文件夹。内容变化时旧APK自动移至同级`releases/`，相同SHA256只保留一份，新APK不会移走当前IPA。文件存储逻辑见scripts/store-package.mjs，验证命令为`/Users/y/toolchains/bin/node --test scripts/store-package.test.mjs`。

Release未开启代码混淆；Debug和Release签名不同，不能互相直接覆盖，不能在有用户数据的设备上以卸载作为修复方法。

从源码包重建时，私钥不会随包提供。可先用工具箱的`keytool -genkeypair -alias tiebalite -keyalg RSA -keysize 3072 -validity 10000 -keystore /自选私有路径/release.jks`交互创建自己的密钥，再把相应密码保存到仅本人可读的文件并设置上述变量。勿覆盖已有升级签名，勿把私钥和密码发到仓库或日志。Debug重建不需要Release密钥。

独立线上匿名验证（仅公开读取）：

```sh
cd android
RUN_ANONYMOUS_LIVE_SMOKE=1 ./gradlew testDebugUnitTest --rerun-tasks
```

设备验证只使用本项目私有AVD `TiebaLite-lab`、端口5580、Android35 ARM64，AVD目录在 `.build/avd`。设备存储测试命令：

```sh
/Users/y/toolchains/bin/adb -s emulator-5580 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/y/toolchains/bin/adb -s emulator-5580 shell am instrument -w -r dev.anyu.tiebalite.test/androidx.test.runner.AndroidJUnitRunner
```

该测试要求先安装同源码Debug主包；它不针对Release签名包运行。Release需独立做安装/启动检查。Android35模拟器结果不能替代Android26/36与各厂商真机验收。
