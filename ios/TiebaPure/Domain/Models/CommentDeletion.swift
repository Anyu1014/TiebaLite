import Foundation

struct OwnCommentDeletionTarget: Equatable, Sendable {
    let forumID: Int64
    let forumName: String
    let threadID: Int64
    let postID: UInt64
    let authorID: Int64
    let floor: Int
    var parentPostID: UInt64? = nil

    func validate(account: Account) throws {
        guard Int64(account.uid) == authorID, authorID > 0 else { throw CommentDeletionError.notOwner }
        guard forumID > 0, threadID > 0, postID > 0,
              !forumName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, forumName.count <= 100,
              parentPostID.map({ $0 > 0 && $0 != postID }) ?? (floor > 1) else {
            throw CommentDeletionError.invalidTarget
        }
    }
}

enum CommentDeletionError: Error, LocalizedError {
    case notOwner
    case invalidTarget
    var errorDescription: String? {
        switch self {
        case .notOwner: return "只能删除本人发布的评论。"
        case .invalidTarget: return "评论或贴吧信息不完整，无法删除；主楼请使用删除主题。"
        }
    }
}
