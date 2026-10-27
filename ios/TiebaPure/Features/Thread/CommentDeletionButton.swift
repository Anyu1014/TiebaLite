import SwiftUI

struct CommentDeletionButton: View {
    @EnvironmentObject private var environment: AppEnvironment
    let account: Account
    let target: OwnCommentDeletionTarget
    let placement: ThreadPostMetadataPlacement
    let onDeleted: () -> Void
    @State private var confirms = false
    @State private var deleting = false
    @State private var resultUnknown = false
    @State private var errorMessage: String?

    var body: some View {
        if (try? target.validate(account: account)) != nil {
            Button(deleting ? "删除中…" : "删除") { confirms = true }
                .font(.caption)
                .foregroundStyle(Color.secondary)
                .buttonStyle(.plain)
                .frame(minWidth: 44, minHeight: placement.visualHeight)
                .contentShape(.interaction, Rectangle().inset(by: -placement.hitExpansion))
                .disabled(deleting || resultUnknown)
                .accessibilityIdentifier("delete-comment-\(target.postID)")
                .confirmationDialog("删除这条评论？", isPresented: $confirms, titleVisibility: .visible) {
                    Button("确认删除", role: .destructive) { Task { await delete() } }
                    Button("取消", role: .cancel) {}
                } message: {
                    Text("仅删除你发布的这条评论，此操作无法撤销。")
                }
                .alert("删除评论", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                    Button("好", role: .cancel) {}
                } message: { Text(errorMessage ?? "") }
        }
    }

    @MainActor private func delete() async {
        guard !deleting, !resultUnknown else { return }
        deleting = true
        defer { deleting = false }
        do {
            guard try await environment.accountStore.load()?.sessionIdentity == account.sessionIdentity else {
                throw CommentDeletionError.notOwner
            }
            try await environment.contentSubmissionCoordinator.performAccountWrite(account: account, target: .deleteComment(target.postID), coalescesConcurrentCalls: true) {
                try await environment.api.deleteOwnComment(account: account, target: target)
            }
            guard try await environment.accountStore.load()?.sessionIdentity == account.sessionIdentity else { return }
            onDeleted()
        } catch is CancellationError {
            return
        } catch {
            if case .resultPending = UserProfileMutationPresentationPolicy.failure(for: error) {
                resultUnknown = true
            }
            errorMessage = ReaderErrorMessage.message(for: error)
        }
    }
}
