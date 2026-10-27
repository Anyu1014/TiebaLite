import Foundation

@MainActor
final class ComposerFocusUpdate {
    private var pending: (() -> Void)?
    private var scheduled = false

    func schedule(_ update: @escaping () -> Void) {
        pending = update
        guard !scheduled else { return }
        scheduled = true
        // UIKit focus changes query SwiftUI's focus graph; leave its update pass first.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            let update = self.pending
            self.pending = nil
            self.scheduled = false
            update?()
        }
    }
}
