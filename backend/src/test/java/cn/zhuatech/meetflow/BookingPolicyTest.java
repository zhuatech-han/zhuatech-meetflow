// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.*;

/** 验证时间边界、容量、周转和预约窗口，覆盖实际业务拒绝条件。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class BookingPolicyTest {
  final Instant now = Instant.parse("2026-10-05T01:45:00Z"),
      start = Instant.parse("2026-10-05T02:00:00Z"),
      end = start.plusSeconds(3600);
  MeetingResource r;

  @BeforeEach
  void setup() {
    r = new MeetingResource();
    r.capacity = 8;
    r.openMinute = 480;
    r.closeMinute = 1080;
    r.maxDurationMinutes = 240;
    r.minNoticeMinutes = 0;
    r.bufferMinutes = 15;
    r.weekdays = "1,2,3,4,5";
  }

  @Test
  void touchingIntervalsAllowed() {
    assertFalse(BookingPolicy.overlaps(now, start, start, end));
  }

  @Test
  void overlapDetected() {
    assertTrue(BookingPolicy.overlaps(now, end, start, end));
  }

  @Test
  void containedIntervalDetected() {
    assertTrue(BookingPolicy.overlaps(now, end, start, start.plusSeconds(900)));
  }

  @Test
  void validShanghaiBooking() {
    assertDoesNotThrow(() -> BookingPolicy.validate(r, start, end, 8, now, 90));
  }

  @Test
  void capacityCannotBeExceeded() {
    assertThrows(Problem.class, () -> BookingPolicy.validate(r, start, end, 9, now, 90));
  }

  @Test
  void emptyAttendanceRejected() {
    assertThrows(Problem.class, () -> BookingPolicy.validate(r, start, end, 0, now, 90));
  }

  @Test
  void quarterHourRequired() {
    assertThrows(
        Problem.class, () -> BookingPolicy.validate(r, start.plusSeconds(60), end, 1, now, 90));
  }

  @Test
  void zeroDurationRejected() {
    assertThrows(Problem.class, () -> BookingPolicy.validate(r, start, start, 1, now, 90));
  }

  @Test
  void closingIncludesBuffer() {
    var late = Instant.parse("2026-10-05T09:00:00Z");
    assertThrows(
        Problem.class, () -> BookingPolicy.validate(r, late, late.plusSeconds(3600), 1, now, 90));
  }

  @Test
  void weekendRejected() {
    assertThrows(
        Problem.class,
        () ->
            BookingPolicy.validate(
                r, start.plusSeconds(5 * 86400), end.plusSeconds(5 * 86400), 1, now, 90));
  }

  @Test
  void noticeRequired() {
    r.minNoticeMinutes = 30;
    assertThrows(Problem.class, () -> BookingPolicy.validate(r, start, end, 1, now, 90));
  }

  @Test
  void horizonRequired() {
    assertThrows(
        Problem.class,
        () ->
            BookingPolicy.validate(
                r, start.plusSeconds(91 * 86400), end.plusSeconds(91 * 86400), 1, now, 90));
  }

  @Test
  void crossDayRejected() {
    r.closeMinute = 1440;
    var late = Instant.parse("2026-10-05T15:45:00Z");
    assertThrows(
        Problem.class, () -> BookingPolicy.validate(r, late, late.plusSeconds(1800), 1, now, 90));
  }

  @Test
  void maxDurationRejected() {
    assertThrows(
        Problem.class,
        () -> BookingPolicy.validate(r, start, start.plusSeconds(5 * 3600), 1, now, 90));
  }
}
