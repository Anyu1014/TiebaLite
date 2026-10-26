# 当前进展

2026-10-26：TiebaLite双端本地开发、打包和文档交付已收尾。根目录 /Users/y/Downloads/Codex/Mac工作区/TiebaLite，版本1.4.15/build59，新ID dev.anyu.tiebalite，用户指定白角图标已接入两端。固定上游5a87225190d6a79b1e3ad3b7d846edd407d416c7，完整只读克隆在original-source；可编辑iOS与新增Android均在根Git。

- iOS最终Release归档/未签名IPA成功，主应用与扩展ID/名称/版本/Scheme/许可核对通过。测试目标编译成功、Mac路由断言通过；没有iOS运行时和匹配profile，未安装启动或执行XCTest/UITest。
- Android Debug/Release构建、Lint和签名通过；12项JVM单测通过；另1项真实匿名流程实际通过。专用AVD设备存储5/5通过，含2.3MB文件快照及损坏校验；正文保存后断网重开通过。最终本地签名Release已在专用AVD安装/冷启动/打开真实帖子。
- Android主要业务已实现；真实账号登录/写操作、媒体库保存与完整媒体离线、平板/多版本/无障碍矩阵尚未验收，不能称双端全功能验证完成。差异和限额见FEATURES/KNOWN_ISSUES。
- 原作者、GPL与依赖许可保留；129依赖元数据节点/90归档/44条NOTICE提取完成。APK许可assets、IPA GPL和SwiftProtobuf正文与源码字节一致；完整素材清权并未完成。
- 右侧预览 http://127.0.0.1:8768：iOS原项目参考截图，Android模拟器实际截图，静态首页/帖子切换。server仅提供previews目录。README/ARCHITECTURE/DEVELOPMENT/BUILD_IOS/BUILD_ANDROID齐全；VERIFICATION列实际证据。
- 最终安装包、对应源码zip、manifest与SHA256SUMS在releases。对应提交以manifest为准；未来开发先核对本地Git状态，不重建或覆盖原始仓库。

新增XcodeGen2.46.0于工具箱；没有另装SDK/JDK/Xcode/iOS运行时。AndroidRelease新密钥/密码仅在私有.build/signing，未进Git/源码包；未来升级须保留。Debug与Release签名不互通，不能通过卸载用户应用解决冲突。

未推送、未上传、未发布、未安装用户真机。专用AVD数据均为本任务测试数据。后续优先做匹配iOS签名和实际运行、真实账号由用户手动登录后的逐项验收，不自动执行发帖等写操作。
