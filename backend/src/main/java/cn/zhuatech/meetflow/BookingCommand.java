// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import jakarta.persistence.*;

/** 按操作者与请求键去重，拒绝同键不同载荷。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "booking_command")
public class BookingCommand {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "booking_id", nullable = false)
  public Long bookingId;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;
}
