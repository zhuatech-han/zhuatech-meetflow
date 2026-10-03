// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import jakarta.persistence.*;
import java.time.Instant;

/** 不可变预约事件与当次完整单据快照。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "booking_event")
public class BookingEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "note", nullable = false, columnDefinition = "text")
  public String note;

  @Column(name = "snapshot", nullable = false, columnDefinition = "text")
  public String snapshot;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
