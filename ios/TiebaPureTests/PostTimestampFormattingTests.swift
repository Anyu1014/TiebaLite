import Foundation
import XCTest
@testable import TiebaPure

final class PostTimestampFormattingTests: XCTestCase {
    func testMainPostAndSubpostUseAbsoluteTimeAndKeepIPLocation() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Shanghai"))
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-10-25T00:58:07Z"))
        XCTAssertEqual(ReaderDateText.threadMetadataString(from: date, calendar: calendar, format: .mainPost), "08:58:07「2026-10-25」")
        XCTAssertEqual(ReaderDateText.threadMetadataString(from: date, calendar: calendar, format: .subpost), "08:58「10-25」")
        XCTAssertEqual(ThreadPostMetadataText.text(createdAt: date, ipAddress: "IP属地：墨西哥", calendar: calendar, format: .mainPost), "08:58:07「2026-10-25」  墨西哥")
        XCTAssertEqual(ThreadPostMetadataText.text(createdAt: date, ipAddress: "河北", calendar: calendar, format: .subpost), "08:58「10-25」  河北")
    }
    func testRolloverUsesLocalGregorianDateEvenWithAnotherSystemCalendar() throws {
        var calendar = Calendar(identifier: .buddhist)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Shanghai"))
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-12-31T16:02:03Z"))
        XCTAssertEqual(ReaderDateText.threadMetadataString(from: date, calendar: calendar, format: .mainPost), "00:02:03「2027-01-01」")
        XCTAssertEqual(ReaderDateText.threadMetadataString(from: date, calendar: calendar, format: .subpost), "00:02「01-01」")
        calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
        XCTAssertEqual(ReaderDateText.threadMetadataString(from: date, calendar: calendar, format: .mainPost), "16:02:03「2026-12-31」")
    }
    func testMissingDateDoesNotInventATimestampAndOrdinaryReplyKeepsItsFormat() throws {
        XCTAssertEqual(ThreadPostMetadataText.text(createdAt: nil, ipAddress: "来自 浙江", format: .mainPost), "浙江")
        XCTAssertEqual(ThreadPostMetadataText.text(createdAt: nil, ipAddress: nil, format: .subpost), "")
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-07-12T09:30:00Z"))
        let now = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-07-13T16:00:00Z"))
        XCTAssertEqual(ThreadPostMetadataText.text(createdAt: date, ipAddress: "湖南", now: now, calendar: calendar), "昨天 09:30  湖南")
    }
}
