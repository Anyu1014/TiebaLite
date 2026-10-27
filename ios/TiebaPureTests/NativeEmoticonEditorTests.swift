import UIKit
import SwiftUI
import XCTest
@testable import TiebaPure

final class NativeEmoticonEditorTests: XCTestCase {
    @MainActor func testFocusUpdateIsDeferredAndOnlyAppliesTheNewestRequest() async {
        let focus = ComposerFocusUpdate()
        var updatingView = true
        var calls: [Int] = []
        let applied = expectation(description: "Focus applied after view update")
        for index in 0..<100 {
            focus.schedule {
                XCTAssertFalse(updatingView)
                calls.append(index)
                applied.fulfill()
            }
        }
        XCTAssertTrue(calls.isEmpty)
        updatingView = false
        await fulfillment(of: [applied], timeout: 1)
        XCTAssertEqual(calls, [99])
    }

    @MainActor func testHostedReplyComposerKeepsFocusWhileTypingAndComposingChinese() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        let target = ContentSubmissionTarget(kind: .threadReply, forumID: 1, forumName: "测试", forumDisplayName: "测试吧", threadID: 42, threadTitle: "输入检查", parentPostID: nil, parentFloor: nil, subpostID: nil, replyUserID: nil, replyUserDisplayName: nil)
        window.rootViewController = UIHostingController(rootView: ContentComposerView(target: target, onCancel: {}, onSaveDraft: { _ in }, onSend: { _ in throw CancellationError() }))
        window.makeKeyAndVisible()
        defer { window.isHidden = true; window.rootViewController = nil; previousWindow?.makeKey() }
        func editor(in view: UIView) -> ComposerTextView? {
            if let view = view as? ComposerTextView { return view }
            return view.subviews.lazy.compactMap { editor(in: $0) }.first
        }
        try await Task.sleep(nanoseconds: 150_000_000)
        let view = try XCTUnwrap(editor(in: window))
        XCTAssertTrue(view.becomeFirstResponder())
        try await Task.sleep(nanoseconds: 150_000_000)
        var expected = ""
        for text in ["连", "续", "输", "入", "🙂", "#(酷)", "表情后继续"] {
            view.insertText(text)
            expected += text
            try await Task.sleep(nanoseconds: 30_000_000)
            XCTAssertTrue(view.isFirstResponder)
            XCTAssertEqual(ComposerEmoticonText.raw(view.attributedText), expected)
        }
        view.setMarkedText("ni", selectedRange: NSRange(location: 2, length: 0))
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertNotNil(view.markedTextRange)
        XCTAssertTrue(view.isFirstResponder)
        view.setMarkedText("你好", selectedRange: NSRange(location: 2, length: 0))
        view.unmarkText()
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(ComposerEmoticonText.raw(view.attributedText), expected + "你好")
        view.resignFirstResponder()
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertFalse(view.isFirstResponder)
    }

    @MainActor func testActualTextViewInsertionAndBackspaceKeepWireTokens() {
        var raw = "开头#(酷)结尾"
        var selection = NSRange(location: 6, length: 0)
        let editor = NativeEmoticonEditor(text: Binding(get: { raw }, set: { raw = $0 }), selection: Binding(get: { selection }, set: { selection = $0 }), isFocused: false, onFocus: {})
        let coordinator = editor.makeCoordinator()
        let view = ComposerTextView(frame: CGRect(x: 0, y: 0, width: 350, height: 180))
        view.backgroundColor = .systemBackground
        view.delegate = coordinator
        coordinator.render(view, raw: raw, range: selection)
        XCTAssertEqual(view.selectedRange.location, 3)
        view.insertText("#(哈哈)")
        XCTAssertEqual(raw, "开头#(酷)#(哈哈)结尾")
        XCTAssertEqual(view.attributedText.string, "开头\u{fffc}\u{fffc}结尾")
        view.deleteBackward()
        XCTAssertEqual(raw, "开头#(酷)结尾")
        XCTAssertEqual(view.attributedText.string, "开头\u{fffc}结尾")
        let image = UIGraphicsImageRenderer(size: view.bounds.size).image { _ in view.drawHierarchy(in: view.bounds, afterScreenUpdates: true) }
        let attachment = XCTAttachment(image: image)
        attachment.name = "native-emoticon-editor"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    @MainActor func testNativeArtworkRoundTripsTokensSelectionAndUnicode() throws {
        let raw = "你好🙂#(酷)[哈哈](#滑稽)[未知]"
        let text = ComposerEmoticonText.attributed(raw, font: .preferredFont(forTextStyle: .body))
        XCTAssertEqual(text.string, "你好🙂\u{fffc}\u{fffc}\u{fffc}[未知]")
        XCTAssertEqual(ComposerEmoticonText.raw(text), raw)
        let selection = NSRange(location: ("你好🙂#(酷)" as NSString).length, length: 0)
        let display = ComposerEmoticonText.displayRange(selection, in: text)
        XCTAssertEqual(display.location, 5)
        XCTAssertEqual(ComposerEmoticonText.rawRange(display, in: text), selection)
        let edited = NSMutableAttributedString(attributedString: text)
        edited.deleteCharacters(in: NSRange(location: 4, length: 1))
        XCTAssertEqual(ComposerEmoticonText.raw(edited), "你好🙂[哈哈](#滑稽)[未知]")
        let copied = text.attributedSubstring(from: NSRange(location: 4, length: 3))
        XCTAssertEqual(ComposerEmoticonText.raw(copied), "#(酷)[哈哈](#滑稽)")
        let preview = InlineContentText(blocks: [.text("预览#(酷)🙂[哈哈]")], style: .preview)
        XCTAssertEqual(preview.attributedString().string, "预览\u{fffc}🙂\u{fffc}")
        XCTAssertEqual(preview.accessibilityText(), "预览[酷]🙂[哈哈]")
    }

    func testNativeImageURLsStayInlineButPhotosAndUntrustedURLsRemainImages() {
        var native = Tieba_PbContent()
        native.type = 3
        native.src = "https://tb2.bdstatic.com/tb/editor/images/client/image_emoticon25.png"
        XCTAssertEqual(PostMapper.blocks(from: native), [.emoticon(code: "image_emoticon25")])
        native.src = "https://imgsrc.baidu.com/forum/product.jpg"
        guard case .image = PostMapper.blocks(from: native).first else { return XCTFail("Ordinary photo lost") }
        XCTAssertNil(TiebaEmoticon.imageName(fromURL: "https://evil.test/tb/editor/images/client/image_emoticon25.png"))
        XCTAssertNil(TiebaEmoticon.imageName(fromURL: "https://tb2.bdstatic.com.evil.test/tb/editor/images/client/image_emoticon25.png"))
        native.type = 2
        native.text = "(_ImageToUpload_)"
        native.c = "哈哈"
        XCTAssertEqual(PostMapper.blocks(from: native), [.emoticon(code: "哈哈")])
    }

    func testUploadMarkerRequiresAnActualPhotoAndUnknownTextIsPreserved() {
        var text = Tieba_PbContent()
        text.text = "(_ImageToUpload_)\n正文"
        XCTAssertEqual(PostMapper.blocks(from: [text]), [.text(text.text)])
        var photo = Tieba_PbContent()
        photo.type = 3
        photo.src = "https://imgsrc.baidu.com/forum/product.jpg"
        XCTAssertEqual(PostMapper.blocks(from: [text, photo]).first, .text("\n正文"))
        XCTAssertEqual(TiebaEmoticon.normalizedBlocks([.text("🙂#(酷)[未知]")]), [.text("🙂"), .emoticon(code: "酷"), .text("[未知]")])
    }
}
