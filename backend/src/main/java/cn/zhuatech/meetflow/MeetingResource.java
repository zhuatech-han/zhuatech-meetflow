// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import jakarta.persistence.*;

/** 会议室与共享设备的预约规则；历史引用阻止物理删除。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "meeting_resource")
public class MeetingResource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "location", nullable = false, length = 200)
  public String location;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "steward_id", nullable = false)
  public Long stewardId;

  @Column(name = "capacity", nullable = false)
  public int capacity;

  @Column(name = "open_minute", nullable = false)
  public int openMinute;

  @Column(name = "close_minute", nullable = false)
  public int closeMinute;

  @Column(name = "buffer_minutes", nullable = false)
  public int bufferMinutes;

  @Column(name = "max_duration_minutes", nullable = false)
  public int maxDurationMinutes;

  @Column(name = "min_notice_minutes", nullable = false)
  public int minNoticeMinutes;

  @Column(name = "check_in_grace_minutes", nullable = false)
  public int checkInGraceMinutes;

  @Column(name = "weekdays", nullable = false, length = 20)
  public String weekdays;

  @Column(name = "approval_required", nullable = false)
  public boolean approvalRequired;

  @Column(name = "shared", nullable = false)
  public boolean shared;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;
}
