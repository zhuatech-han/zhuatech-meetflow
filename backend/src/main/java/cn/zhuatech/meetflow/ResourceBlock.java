// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import jakarta.persistence.*;
import java.time.Instant;

/** 维护与停用时段；取消留存历史，不覆盖有效预约。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "resource_block")
public class ResourceBlock {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version;

  @Column(name = "resource_id", nullable = false)
  public Long resourceId;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason;

  @Column(name = "status", nullable = false, length = 20)
  public String status;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
