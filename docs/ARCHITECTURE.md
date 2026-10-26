# 架构

双端直接访问百度的 HTTPS 服务，没有新增自有后端。非官方协议可能变化，客户端不能控制百度风控与服务可用性。

| 层 | iOS | Android |
| --- | --- | --- |
| 界面与导航 | SwiftUI，Features，RootView/ExternalRoute | Compose/Material3，MainActivity/AccountScreens/LoginView |
| 状态与任务 | AppEnvironment 与各 ViewModel | AppViewModel：导航、分页、账号代次、取消与错误呈现 |
| 协议与模型 | Core/Network、Domain/Mapping/Models | TiebaApi、Models、SubmissionPolicy |
| 持久化 | 原 Keychain、SwiftData/受保护文件方案 | LocalStore：SQLite 索引，文件快照，Android Keystore 加密凭据 |
| 离线与媒体 | 原 SavedThreads、图片/视频下载服务 | OfflineLibrary：逐页抓取、楼中楼、分级媒体、SHA-256 文件名与快照校验 |
| 字体 | 原 ReadingPreferences | FontStore：私有字体导入、校验与选择 |

`ios/Protos` 是两端共享的协议输入。iOS 保留上游生成的 Swift；Android Gradle 用 protoc 3.25.5 生成 Java。不得随意改字段编号以迁就 UI。`app-config.json` 提供名称、Bundle/application ID、版本、build 和 Scheme；`sync-config.mjs` 生成 iOS App.xcconfig，Android Gradle 读取同一 JSON。内部 Swift 模块/产品仍叫 TiebaPure，避免破坏测试及原存储结构。

Android 网络限定 HTTPS、可信 API/媒体主机，禁止自动跳转和自动重试写操作，限制响应大小。会话由 UID、BDUSS、STOKEN 与账号代次隔离；换号/退出取消旧任务，凭据不进入备份。提交结果只有通过严格状态和回执校验后才按成功处理；挑战、冲突、超时保留草稿并交由用户核对。

离线保存先建立独立临时目录，完整成功后切换索引，失败保留旧版本。大正文放文件，SQLite 只存小索引，避免 CursorWindow 限制。导入备份先完整校验，合并时保留已有记录，不导入账号、草稿和媒体路径。具体限额与双端差异见 FEATURES。
