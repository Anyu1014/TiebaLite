import XCTest
@testable import TiebaPure

@MainActor final class CommentDeletionTests: XCTestCase {
    private let account = FixtureTiebaAPI.account
    func testEvenASinglePreviewReplyHasAnExpandPathToItsDeleteControl() {
        let reply = FixtureTiebaAPI.referenceSubpostFixtures[0]
        XCTAssertEqual(SubpostPreviewView(subposts: [reply], totalCount: 1, threadAuthorID: nil, onOpenAll: {}).openAllCount, 1)
        XCTAssertEqual(SubpostPreviewView(subposts: [reply], totalCount: 0, threadAuthorID: nil, onOpenAll: {}).openAllCount, 1)
        XCTAssertEqual(SubpostPreviewView(subposts: Array(repeating: reply, count: 3), totalCount: 3, threadAuthorID: nil, onOpenAll: {}).openAllCount, 3)
        XCTAssertNil(SubpostPreviewView(subposts: [reply], totalCount: 1, threadAuthorID: nil).openAllCount)
    }
    private func target(postID: UInt64, authorID: Int64? = nil, floor: Int = 2, parent: UInt64? = nil) -> OwnCommentDeletionTarget {
        OwnCommentDeletionTarget(forumID: FixtureTiebaAPI.forum.id, forumName: FixtureTiebaAPI.forum.name, threadID: FixtureTiebaAPI.threads[0].id, postID: postID, authorID: authorID ?? Int64(account.uid)!, floor: floor, parentPostID: parent)
    }
    func testDeletionFieldsSelectOnlyTheCommentAndRejectOtherAuthorsAndMainPost() throws {
        let t = target(postID: 9001)
        let builder = TiebaRequestBuilder(screenScale: 3, screenWidth: 1080, screenHeight: 2400, clientID: "fixture")
        let fields = try UserProfileRequestFactory.deleteCommentFields(account: account, tbs: "fixture-tbs", target: t, requestBuilder: builder, timestamp: 1234)
        XCTAssertEqual(fields["pid"], "9001")
        XCTAssertEqual(fields["delete_my_post"], "1")
        XCTAssertEqual(fields["isfloor"], "0")
        XCTAssertNil(fields["delete_my_thread"])
        XCTAssertEqual(TiebaEndpoint.deleteOwnComment.url.path, "/c/c/bawu/delpost")
        XCTAssertThrowsError(try target(postID: 9001, authorID: Int64(account.uid)! + 1).validate(account: account))
        XCTAssertThrowsError(try target(postID: 1001, floor: 1).validate(account: account))
        XCTAssertThrowsError(try target(postID: 0).validate(account: account))
        XCTAssertNoThrow(try target(postID: 9001, floor: 1, parent: 2002).validate(account: account))
    }
    func testFixtureCommentAndSubpostCanBeSentAndDeletedWithoutAffectingTheThread() async throws {
        let api = FixtureTiebaAPI()
        let thread = FixtureTiebaAPI.threads[0]
        let forum = FixtureTiebaAPI.forum
        let receipt = try await api.submitContent(account: account, request: ContentSubmissionRequest(target: .threadReply(thread: thread, forum: forum), title: "", body: "普通评论删除回归", images: []))
        let id = try XCTUnwrap(receipt.postID)
        var page = try await api.threadPage(account: account, threadID: thread.id, page: 1, seeLz: false, sortType: .ascending)
        XCTAssertTrue(page.posts.contains { $0.id == id })
        try await api.deleteOwnComment(account: account, target: target(postID: id))
        page = try await api.threadPage(account: account, threadID: thread.id, page: 1, seeLz: false, sortType: .ascending)
        XCTAssertFalse(page.posts.contains { $0.id == id })
        XCTAssertNotNil(page.mainPost)
        let parent = try XCTUnwrap(page.posts.first { $0.floor == 2 })
        let subReceipt = try await api.submitContent(account: account, request: ContentSubmissionRequest(target: .postReply(thread: thread, forum: forum, post: parent), title: "", body: "楼中楼删除回归", images: []))
        let subID = try XCTUnwrap(subReceipt.postID)
        try await api.deleteOwnComment(account: account, target: target(postID: subID, floor: 1, parent: parent.id))
        let subposts = try await api.subposts(account: account, threadID: thread.id, postID: parent.id, forumID: forum.id, page: 1, subpostID: 0)
        XCTAssertFalse(subposts.contains { $0.id == subID })
        do {
            try await api.deleteOwnComment(account: account, target: target(postID: parent.id))
            XCTFail("Forging the target author must not bypass fixture server ownership")
        } catch is CommentDeletionError {}
    }
}
