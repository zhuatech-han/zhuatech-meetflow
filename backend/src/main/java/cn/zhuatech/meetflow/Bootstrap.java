// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化预约岗位、目录和管理员，不植入会议或资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${meetflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 只在空库创建管理员，重启保留账号和数据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var d = new Department();
    d.name = "总部";
    db.save(d);
    var names =
        Map.of(
            "booking.read",
            "查看授权预约",
            "booking.write",
            "预约本人资源",
            "booking.approve",
            "审批指定预约",
            "resource.manage",
            "管理部门资源",
            "dashboard",
            "预约统计",
            "export",
            "导出授权预约",
            "audit",
            "操作审计",
            "admin",
            "系统管理");
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role("员工", "ASSIGNED", Set.of("booking.read", "booking.write", "dashboard", "export"));
    role(
        "资源管理员",
        "DEPARTMENT",
        Set.of(
            "booking.read",
            "booking.write",
            "booking.approve",
            "resource.manage",
            "dashboard",
            "export",
            "audit"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"calendar", "资源日历", "Resource calendar", "booking.read"},
      {"bookings", "预约记录", "Bookings", "booking.read"},
      {"workbench", "我的待办", "My work", "booking.read"},
      {"resources", "资源与规则", "Resources", "resource.manage"},
      {"dashboard", "使用统计", "Usage", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "资源类型", "Resource types", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华会议室与共享资源预约", "bookingHorizonDays", "90")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    String[][] categories = {
      {"ROOM", "会议室", "Meeting room"},
      {"DESK", "共享工位", "Shared desk"},
      {"EQUIPMENT", "共享设备", "Shared equipment"}
    };
    for (var c : categories) {
      var e = new DictionaryEntry();
      e.type = "resource";
      e.code = c[0];
      e.name = c[1];
      e.nameEn = c[2];
      db.save(e);
    }
  }

  private void role(String name, String scope, Set<String> permissions) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    db.save(r);
  }
}
