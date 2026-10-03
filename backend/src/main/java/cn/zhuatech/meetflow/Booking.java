// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import jakarta.persistence.*;
import java.time.Instant;

/** 预约单与提交时规则快照；审批等待也占用资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "meeting_booking")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "resource_id", nullable = false)
  public Long resourceId;

  @Column(name = "owner_id", nullable = false)
  public Long ownerId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "reviewer_id")
  public Long reviewerId;

  @Column(name = "title", nullable = false, length = 200)
  public String title;

  @Column(name = "purpose", nullable = false, length = 2000)
  public String purpose;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "attendees", nullable = false)
  public int attendees;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "occupancy_from", nullable = false)
  public Instant occupancyFrom;

  @Column(name = "occupied_until", nullable = false)
  public Instant occupiedUntil;

  @Column(name = "buffer_minutes", nullable = false)
  public int bufferMinutes;

  @Column(name = "check_in_grace_minutes", nullable = false)
  public int checkInGraceMinutes;

  @Column(name = "checked_in_at")
  public Instant checkedInAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(name = "submitted", nullable = false)
  public boolean submitted;

  @Column(name = "change_count", nullable = false)
  public long changeCount;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
