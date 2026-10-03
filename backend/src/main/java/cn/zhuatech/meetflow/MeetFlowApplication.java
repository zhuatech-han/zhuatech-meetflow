// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** 企业共享资源预约入口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class MeetFlowApplication {
  /** 启动预约服务。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(MeetFlowApplication.class, args);
  }

  /** 统一可替换业务时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
