// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 预约、隐私日历、资源、维护与管理的同源接口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final BookingService service;
  final AdminService admin;

  public ApiController(BookingService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 读取安全目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 查询可见资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/resources")
  public Object resources() {
    return service.resources();
  }

  /** 创建部门资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/resources")
  public Object createResource(@RequestBody BookingService.ResourceInput v) {
    return service.saveResource(null, v);
  }

  /** 更新资源规则。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/resources/{id}")
  public Object resource(@PathVariable Long id, @RequestBody BookingService.ResourceInput v) {
    return service.saveResource(id, v);
  }

  /** 删除无引用资源。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/resources/{id}")
  public Object deleteResource(@PathVariable Long id, @RequestParam Long version) {
    service.deleteResource(id, version);
    return Map.of("ok", true);
  }

  /** 查询部门维护时段。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/resources/{id}/blocks")
  public Object blocks(@PathVariable Long id) {
    return service.blocks(id);
  }

  /** 建立无冲突维护时段。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/resources/{id}/blocks")
  public Object addBlock(@PathVariable Long id, @RequestBody BookingService.BlockInput v) {
    return service.addBlock(id, v);
  }

  /** 取消维护并保留记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/resources/{id}/blocks/{bid}/cancel")
  public Object cancelBlock(
      @PathVariable Long id, @PathVariable Long bid, @RequestBody BookingService.BlockInput v) {
    return service.cancelBlock(id, bid, v);
  }

  /** 查询上海时区单日隐私日历。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/calendar")
  public Object calendar(@RequestParam LocalDate day) {
    return service.calendar(day);
  }

  /** 按权限分页查询预约。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/bookings")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) Long resourceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "time") String sort,
      @RequestParam(defaultValue = "false") boolean mine) {
    return service.list(search, status, resourceId, page, size, sort, mine);
  }

  /** 创建本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/bookings")
  public Object create(@RequestBody BookingService.Draft v) {
    return service.create(v);
  }

  /** 带版本保存本人草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/bookings/{id}")
  public Object save(@PathVariable Long id, @RequestBody BookingService.Draft v) {
    return service.save(id, v);
  }

  /** 删除未送审草稿。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/bookings/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 读取预约与历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/bookings/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 执行有限状态命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/bookings/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody BookingService.Command v) {
    return service.act(id, action, v);
  }

  /** 本人活动预约与指定审批待办。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 授权预约与使用时长统计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 授权操作审计。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 下载不含广告和凭据的单据报告。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/bookings/{id}/report.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=booking-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.export(id));
  }

  /** 查询系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object listAdmin(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 修改系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除无引用的系统资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
