// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import java.time.*;
import java.util.*;

/** 半开时间区间与上海时区规则，资源周转时间计入冲突。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class BookingPolicy {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private BookingPolicy() {}

  /** 相接时段允许预约；重叠以起点包含、终点排除判断。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static boolean overlaps(Instant a, Instant b, Instant c, Instant d) {
    return a.isBefore(d) && c.isBefore(b);
  }

  /** 验证时长、15分钟刻度、开放时间、人数、提前量与预约窗口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validate(
      MeetingResource r, Instant start, Instant end, int attendees, Instant now, int horizon) {
    if (start == null
        || end == null
        || !start.isBefore(end)
        || start.getNano() != 0
        || end.getNano() != 0
        || start.getEpochSecond() % 900 != 0
        || end.getEpochSecond() % 900 != 0) throw new Problem(400, "INVALID_TIME");
    long minutes = Duration.between(start, end).toMinutes();
    var s = start.atZone(ZONE);
    var e = end.atZone(ZONE);
    if (minutes < 15
        || minutes > r.maxDurationMinutes
        || !s.toLocalDate().equals(e.toLocalDate())
        || s.getHour() * 60 + s.getMinute() < r.openMinute
        || e.getHour() * 60 + e.getMinute() + r.bufferMinutes > r.closeMinute
        || !Arrays.asList(r.weekdays.split(","))
            .contains(String.valueOf(s.getDayOfWeek().getValue())))
      throw new Problem(400, "OUTSIDE_OPENING_HOURS");
    if (attendees < 1 || attendees > r.capacity) throw new Problem(400, "CAPACITY_EXCEEDED");
    if (start.isBefore(now.plusSeconds(r.minNoticeMinutes * 60L))
        || start.isAfter(now.plusSeconds(horizon * 86400L)))
      throw new Problem(400, "BOOKING_WINDOW");
  }
}
