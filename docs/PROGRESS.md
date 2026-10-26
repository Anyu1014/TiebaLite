# 当前进展

## 2026-10-26 1.0 / build61 发布

当前配置为1.0/build61。关于页删除指定二改说明，项目名称/许可条目改为TiebaLite，源码链接为https://github.com/Anyu1014/TiebaLite；仓库中保留上游版权、来源和第三方许可。源码发布目标为公开仓库的1.0分支，发行标签1.0，安装包命名TiebaLite-1.0.apk与TiebaLite-1.0-unsigned.ipa。

iOS26/27恢复系统原生TabView标签栏，使用系统液态玻璃及拖动选择；此版本不挂接自定义底栏或拦截系统标签栏代理。旧版保留兼容底栏。底部内容继续延伸到屏幕边缘，普通切换不会刷新首页，顶部下拉手动刷新。已添加原生拖动UITest；无模拟器运行时，仅编译该测试目标，尚未自动执行拖动手感验收。

Android首页缓存内容、页码、失败重试状态及独立滚动位置；账号变化清理缓存，失败续页仍可按原追加模式重试。列表与帖子支持顶部下拉刷新、底部自动续载。评论区按用户IMG_0114.PNG调整筛选/排序条、头像、等级、点赞数、日期地区和灰色楼中楼预览；数据来自已有公共protobuf字段，保留屏蔽、回复开关、离线全文及展开入口，增加独立wire fixture和旧备份兼容测试。

iOS开发签名主应用与扩展验证通过；已在连接的iPhone18ProMax上从build60原位更新至build61并启动。更新前完整容器备份123文件/26643071字节，更新后10个持久化文件SHA256不变，无卸载或容器清理。设备备份、描述文件、私钥和安装日志不公开。开发签名仅支持登记设备，过期需重新签名；本机签名IPA保存在releases/ios/TiebaLite-1.0.ipa，公开发行使用未签名包。

验证与安装记录：本机logs/release-1.0-*，公开范围见docs/releases/1.0.md。工具均使用/Users/y/toolchains；没有下载/安装新SDK或运行时，也未操作其他手机应用。自动配置本项目匹配的Apple描述文件不等同于安装新工具。

## 2026-10-26 自动续载与 1.0 关于页面

双端共用版本改为1.0，build59保持；关于页项目作者显示Anyu，按用户截图展示版本、作者、介绍和开源许可分组，保留infinityf4p/TiebaPure-iOS来源署名、GPL和SwiftProtobuf许可正文。Android另保留实际依赖通知和Protobuf BSD正文。许可证在独立页面本地读取，Android中的SwiftProtobuf明确标注为上游iOS依赖。

Android 列表、帖子回复和楼中楼改为触底自动追加，保留加载指示及失败重试，重复/空页停止续载，连续阅读按可见楼层所属页保存位置。iOS 保留原有可见行自动预加载并移除正常列表/回复的手动加载按钮；过滤后的空状态保留有界继续操作，防止全部内容被屏蔽时无限请求。

验收：共享配置3项通过；Android20项JVM单测通过、1项默认真实网络测试跳过，Lint与Debug构建通过；另在原生emulator-5580实际执行关于内容、GPL/Apache正文和滚动自动追加测试，1/1通过（7.493秒）。iOS应用与测试目标编译通过，未执行XCTest/UITest。证据见UI_1.0_VERIFICATION.md。验证用Debug已原位更新至专用可操作Android模拟器，无新增工具或依赖。

右侧打开ios-layout.html，标题和页内注明为布局示意，不能代替原生iOS模拟器；本机仍无iOS运行时。releases内的旧交付包及源码zip未更新，未上传或发布、未安装用户真机。

## 2026-10-26 Android 原生布局与可操作模拟器

Android 首页、进吧、我的、设置、阅读设置及帖子工具栏已按 iOS 布局调整；顶部和底部悬浮层采样真实内容，磨砂参数参考 Anyu 的 blur20/saturate120%。内容视口延伸至屏幕底部。实现与验收边界见 ANDROID_IOS_LAYOUT.md；尚未完成双端同尺寸逐像素验收。

最新 Debug 的单元测试、Lint、应用与设备测试目标构建通过（logs/android-ios-layout-final-build.log，BUILD SUCCESSFUL）。最新 Debug 已更新到专用 emulator-5580 / TiebaLite-lab 并启动；按用户要求关闭 headless 实例，改为有原生窗口的 Android Emulator，允许用户直接点击、滑动和输入。当前交互交给用户，不再自动运行会抢占操作的 UI 驱动。此前设备测试结果见该布局报告，最新搜索流程尚未重复交互验收。

iOS 本机仍无模拟器运行时（simctl list runtimes 为空），磁盘剩余约5.6GiB，未下载新组件；网页示意图不能替代原生模拟器。releases 中安装包和源码zip仍为此前交付版本，不包含当前 UI 修改。未发布、未安装用户真机。

## 2026-10-26 iOS界面修改（未更新安装包）

按用户截图修改顶部居中标题/玻璃圆形搜索按钮，以及底部悬浮玻璃胶囊。列表视口延伸到屏幕底边，胶囊以overlay覆盖内容，根列表保留112pt滚动尾部余量；没有底部整块不透明背景。首页/进吧/我的切换、首页重复点击返回/刷新逻辑保留；当前导航末项为帖子时隐藏胶囊。

代码在Core/UI/FloatingRootTabBar.swift、RootView及三个根页面。iOS26使用原生glassEffect，旧版使用ultraThinMaterial，减少透明度设置使用不透明系统色。新增全屏内容/悬浮栏UI回归用例，并更新现有标签定位与截图边界断言。日志logs/ios-floating-chrome-final-build.log为TEST BUILD SUCCEEDED，仅测试目标编译，未执行测试（本机仍无iOS模拟器运行时）。

右侧iOS改为previews/ios-layout.html布局示意，可滚动检查内容覆盖关系；不是原生运行画面。Android未改。releases中的安装包与源码zip仍为此前4d85efb的交付版本，不包含本轮UI修改，后续打包时需包含本轮修改。

## 先前双端打包交付

2026-10-26：TiebaLite双端本地开发、打包和文档交付已收尾。根目录 /Users/y/Downloads/Codex/Mac工作区/TiebaLite，版本1.4.15/build59，新ID dev.anyu.tiebalite，用户指定白角图标已接入两端。固定上游5a87225190d6a79b1e3ad3b7d846edd407d416c7，完整只读克隆在original-source；可编辑iOS与新增Android均在根Git。

- iOS最终Release归档/未签名IPA成功，主应用与扩展ID/名称/版本/Scheme/许可核对通过。测试目标编译成功、Mac路由断言通过；没有iOS运行时和匹配profile，未安装启动或执行XCTest/UITest。
- Android Debug/Release构建、Lint和签名通过；12项JVM单测通过；另1项真实匿名流程实际通过。专用AVD设备存储5/5通过，含2.3MB文件快照及损坏校验；正文保存后断网重开通过。最终本地签名Release已在专用AVD安装/冷启动/打开真实帖子。
- Android主要业务已实现；真实账号登录/写操作、媒体库保存与完整媒体离线、平板/多版本/无障碍矩阵尚未验收，不能称双端全功能验证完成。差异和限额见FEATURES/KNOWN_ISSUES。
- 原作者、GPL与依赖许可保留；129依赖元数据节点/90归档/44条NOTICE提取完成。APK许可assets、IPA GPL和SwiftProtobuf正文与源码字节一致；完整素材清权并未完成。
- 右侧预览 http://127.0.0.1:8768：iOS原项目参考截图，Android模拟器实际截图，静态首页/帖子切换。server仅提供previews目录。README/ARCHITECTURE/DEVELOPMENT/BUILD_IOS/BUILD_ANDROID齐全；VERIFICATION列实际证据。
- 最终安装包、对应源码zip、manifest与SHA256SUMS在releases。对应提交以manifest为准；未来开发先核对本地Git状态，不重建或覆盖原始仓库。

新增XcodeGen2.46.0于工具箱；没有另装SDK/JDK/Xcode/iOS运行时。AndroidRelease新密钥/密码仅在私有.build/signing，未进Git/源码包；未来升级须保留。Debug与Release签名不互通，不能通过卸载用户应用解决冲突。

未推送、未上传、未发布、未安装用户真机。专用AVD数据均为本任务测试数据。后续优先做匹配iOS签名和实际运行、真实账号由用户手动登录后的逐项验收，不自动执行发帖等写操作。
