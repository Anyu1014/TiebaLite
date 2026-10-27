import SwiftUI
import UIKit

final class ComposerEmoticonAttachment: NSTextAttachment {
    var token = ""
}

enum ComposerEmoticonText {
    static func attributed(_ raw: String, font: UIFont) -> NSAttributedString {
        let result = NSMutableAttributedString(string: "")
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.label]
        let expression = try! NSRegularExpression(pattern: #"#\(([^)\n]+)\)|\(#([^)\n]+)\)|\[([^\]\n]{1,32})\]"#)
        let source = raw as NSString
        var cursor = 0
        for match in expression.matches(in: raw, range: NSRange(location: 0, length: source.length)) {
            let token = source.substring(with: match.range)
            guard let image = TiebaEmoticon.cachedImage(for: token) else { continue }
            result.append(NSAttributedString(string: source.substring(with: NSRange(location: cursor, length: match.range.location - cursor)), attributes: attributes))
            let attachment = ComposerEmoticonAttachment()
            attachment.token = token
            attachment.image = image
            let size = font.lineHeight
            attachment.bounds = CGRect(x: 0, y: font.descender, width: size, height: size)
            result.append(NSAttributedString(attachment: attachment))
            cursor = NSMaxRange(match.range)
        }
        result.append(NSAttributedString(string: source.substring(from: cursor), attributes: attributes))
        return result
    }

    static func raw(_ text: NSAttributedString) -> String {
        var result = ""
        text.enumerateAttributes(in: NSRange(location: 0, length: text.length)) { attributes, range, _ in
            if let attachment = attributes[.attachment] as? ComposerEmoticonAttachment {
                result += attachment.token
            } else { result += (text.string as NSString).substring(with: range) }
        }
        return result
    }

    static func rawRange(_ range: NSRange, in text: NSAttributedString) -> NSRange {
        let start = min(range.location, text.length)
        let end = min(NSMaxRange(range), text.length)
        let before = raw(text.attributedSubstring(from: NSRange(location: 0, length: start)))
        let selected = raw(text.attributedSubstring(from: NSRange(location: start, length: end - start)))
        return NSRange(location: (before as NSString).length, length: (selected as NSString).length)
    }

    static func displayRange(_ range: NSRange, in text: NSAttributedString) -> NSRange {
        func offset(_ target: Int) -> Int {
            var rawOffset = 0
            var displayOffset = 0
            for index in 0..<text.length {
                if rawOffset >= target { break }
                let attachment = text.attribute(.attachment, at: index, effectiveRange: nil) as? ComposerEmoticonAttachment
                rawOffset += attachment.map { ($0.token as NSString).length } ?? 1
                displayOffset = index + 1
            }
            return displayOffset
        }
        let start = offset(range.location)
        return NSRange(location: start, length: max(0, offset(NSMaxRange(range)) - start))
    }
}

final class ComposerTextView: UITextView {
    override func paste(_ sender: Any?) {
        if let text = UIPasteboard.general.string { insertText(text) }
    }
    override func copy(_ sender: Any?) {
        UIPasteboard.general.string = ComposerEmoticonText.raw(attributedText.attributedSubstring(from: selectedRange))
    }
    override func cut(_ sender: Any?) {
        copy(sender)
        if selectedRange.length > 0 { insertText("") }
    }
}

struct NativeEmoticonEditor: UIViewRepresentable {
    @Environment(\.isEnabled) private var isEnabled
    @Binding var text: String
    @Binding var selection: NSRange
    var isFocused: Bool
    var onFocus: () -> Void
    var onBlur: () -> Void = {}

    func makeUIView(context: Context) -> ComposerTextView {
        let view = ComposerTextView()
        view.backgroundColor = .clear
        view.font = .preferredFont(forTextStyle: .body)
        view.adjustsFontForContentSizeCategory = true
        view.textContainerInset = UIEdgeInsets(top: 11, left: 4, bottom: 11, right: 4)
        view.accessibilityLabel = "正文内容"
        view.attributedText = NSAttributedString(string: "")
        view.delegate = context.coordinator
        return view
    }

    func updateUIView(_ view: ComposerTextView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.updateFocus(view, focused: isFocused, enabled: isEnabled)
        guard view.markedTextRange == nil else { return }
        if ComposerEmoticonText.raw(view.attributedText) != text {
            context.coordinator.render(view, raw: text, range: selection)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate {
        var parent: NativeEmoticonEditor
        var rendering = false
        let focusUpdate = ComposerFocusUpdate()
        private var wantsFocus = false
        var artworkToken: NSObjectProtocol?
        weak var view: UITextView?
        init(_ parent: NativeEmoticonEditor) {
            self.parent = parent
            super.init()
            artworkToken = NotificationCenter.default.addObserver(forName: TiebaEmoticonArtwork.didChangeNotification, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, let view = self.view, view.markedTextRange == nil else { return }
                    self.render(view, raw: self.parent.text, range: self.parent.selection)
                }
            }
        }
        deinit { if let artworkToken { NotificationCenter.default.removeObserver(artworkToken) } }
        func updateFocus(_ view: UITextView, focused: Bool, enabled: Bool) {
            wantsFocus = focused
            focusUpdate.schedule { [weak self, weak view] in
                guard let self, let view, view.window != nil else { return }
                if view.isEditable != enabled { view.isEditable = enabled }
                if self.wantsFocus && enabled && !view.isFirstResponder { view.becomeFirstResponder() }
                if (!self.wantsFocus || !enabled) && view.isFirstResponder { view.resignFirstResponder() }
            }
        }
        func render(_ view: UITextView, raw: String, range: NSRange) {
            self.view = view
            rendering = true
            let attributed = ComposerEmoticonText.attributed(raw, font: .preferredFont(forTextStyle: .body))
            view.attributedText = attributed
            view.typingAttributes = [.font: UIFont.preferredFont(forTextStyle: .body), .foregroundColor: UIColor.label]
            view.selectedRange = ComposerEmoticonText.displayRange(range, in: attributed)
            rendering = false
        }
        func textViewDidChange(_ textView: UITextView) {
            guard !rendering else { return }
            view = textView
            let raw = ComposerEmoticonText.raw(textView.attributedText)
            let range = ComposerEmoticonText.rawRange(textView.selectedRange, in: textView.attributedText)
            if parent.text != raw { parent.text = raw }
            if parent.selection != range { parent.selection = range }
            guard textView.markedTextRange == nil else { return }
            let rendered = ComposerEmoticonText.attributed(raw, font: .preferredFont(forTextStyle: .body))
            if rendered.string != textView.attributedText.string { render(textView, raw: raw, range: range) }
        }
        func textViewDidChangeSelection(_ textView: UITextView) {
            guard !rendering, textView.markedTextRange == nil else { return }
            let range = ComposerEmoticonText.rawRange(textView.selectedRange, in: textView.attributedText)
            if parent.selection != range { parent.selection = range }
        }
        func textViewDidBeginEditing(_ textView: UITextView) { view = textView; wantsFocus = true; parent.onFocus() }
        func textViewDidEndEditing(_ textView: UITextView) { wantsFocus = false; parent.onBlur() }
    }
}
