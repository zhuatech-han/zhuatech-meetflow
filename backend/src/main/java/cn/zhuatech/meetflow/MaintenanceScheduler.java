// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期释放审批过期、爽约与自然结束时段；每个资源独立事务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class MaintenanceScheduler {
  final BookingService service;

  public MaintenanceScheduler(BookingService service) {
    this.service = service;
  }

  /** 每分钟扫描，故障交由调度框架报告；写操作仍会主动更新当前资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(
      fixedDelayString = "${meetflow.sweep-millis:60000}",
      initialDelayString = "${meetflow.sweep-millis:60000}")
  public void sweep() {
    for (var id : service.sweepIds()) service.expireResource(id);
  }
}
