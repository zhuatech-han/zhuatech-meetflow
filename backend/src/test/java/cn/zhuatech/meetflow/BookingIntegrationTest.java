// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 会话与真实接口验收；可控时钟验证爽约与自动结束，不向运行服务提供调时接口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(BookingIntegrationTest.TimeConfig.class)
class BookingIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("meetflow.admin-password", () -> password);
    r.add("meetflow.sweep-millis", () -> 86400000);
  }

  /** 测试专用可控时钟，生产使用系统 UTC 时钟。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class MutableClock extends Clock {
    Instant value = Instant.parse("2026-10-05T01:45:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  /** 测试环境时钟绑定。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  @Autowired BookingService service;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, employee, manager, stranger;
  long dept, employeeId, managerId, resource, id;
  JsonNode b, r;
  String employeeName, managerName;
  long employeeRole;
  final Instant start = Instant.parse("2026-10-05T02:00:00Z"), end = start.plusSeconds(3600);

  @BeforeEach
  void setup() throws Exception {
    clock.value = start.minusSeconds(900);
    admin = login("admin", password);
    var suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "预约验收（虚构）-" + suffix))
            .path("id")
            .asLong();
    long managerRole = 0;
    for (var x : ok(admin, "GET", "/admin/roles", null)) {
      if (x.path("name").asString().equals("员工")) employeeRole = x.path("id").asLong();
      if (x.path("name").asString().equals("资源管理员")) managerRole = x.path("id").asLong();
    }
    employeeName = "employee-" + suffix;
    managerName = "manager-" + suffix;
    employeeId = user(employeeName, employeeRole, dept);
    managerId = user(managerName, managerRole, dept);
    user("stranger-" + suffix, employeeRole, 1);
    employee = login(employeeName, password);
    manager = login(managerName, password);
    stranger = login("stranger-" + suffix, password);
    r = ok(manager, "POST", "/resources", resource(true, true, 15));
    resource = r.path("id").asLong();
    b = ok(employee, "POST", "/bookings", draft(resource, start, end));
    id = b.path("id").asLong();
  }

  long user(String name, long role, long department) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                name,
                "displayName",
                "验收测试 " + name,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                department,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> resource(boolean review, boolean shared, int buffer) {
    var v = new LinkedHashMap<String, Object>();
    v.put("version", r == null ? null : r.path("version").asLong());
    v.put("code", "ROOM-" + UUID.randomUUID().toString().substring(0, 8));
    v.put("name", "验收测试会议室");
    v.put("location", "验收测试位置");
    v.put("category", "ROOM");
    v.put("departmentId", dept);
    v.put("stewardId", managerId);
    v.put("capacity", 8);
    v.put("openMinute", 480);
    v.put("closeMinute", 1200);
    v.put("bufferMinutes", buffer);
    v.put("maxDurationMinutes", 240);
    v.put("minNoticeMinutes", 0);
    v.put("checkInGraceMinutes", 10);
    v.put("weekdays", "1,2,3,4,5,6,7");
    v.put("approvalRequired", review);
    v.put("shared", shared);
    v.put("enabled", true);
    return v;
  }

  Map<String, Object> draft(long resource, Instant from, Instant to) {
    var v = new LinkedHashMap<String, Object>();
    v.put("resourceId", resource);
    v.put("title", "验收测试预约");
    v.put("purpose", "隔离测试库验证");
    v.put("attendees", 3);
    v.put("startsAt", from.toString());
    v.put("endsAt", to.toString());
    v.put("requestKey", UUID.randomUUID().toString());
    return v;
  }

  Map<String, Object> cmd() {
    return new LinkedHashMap<>(
        Map.of(
            "version",
            b.path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            "验收测试原因"));
  }

  JsonNode act(MockHttpSession who, String action) throws Exception {
    b = ok(who, "POST", "/bookings/" + id + "/commands/" + action, cmd());
    return b;
  }

  void approve() throws Exception {
    act(employee, "submit");
    act(manager, "approve");
  }

  JsonNode current() throws Exception {
    return ok(employee, "GET", "/bookings/" + id, null).path("booking");
  }

  void expect(MockHttpSession who, String method, String path, Object body, int status)
      throws Exception {
    assertEquals(status, call(who, method, path, body).getResponse().getStatus());
  }

  MockHttpSession login(String name, String pw) throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", name, "password", pw))))
            .andReturn();
    assertEquals(200, result.getResponse().getStatus());
    return (MockHttpSession) result.getRequest().getSession();
  }

  MvcResult call(MockHttpSession who, String method, String path, Object body) throws Exception {
    var req =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    req.session(who).with(csrf());
    if (body != null) req.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(req).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object body) throws Exception {
    var res = call(who, method, path, body);
    assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
    return json.readTree(res.getResponse().getContentAsString());
  }

  @Test
  void approvalCheckinFinishRetainsSnapshots() throws Exception {
    approve();
    act(employee, "check-in");
    assertEquals("IN_USE", b.path("status").asString());
    clock.value = start;
    act(employee, "finish");
    assertEquals("COMPLETED", b.path("status").asString());
    var events = ok(employee, "GET", "/bookings/" + id, null).path("events");
    assertEquals(5, events.size());
    assertEquals(
        "DRAFT",
        json.readTree(events.get(0).path("snapshot").asString()).path("status").asString());
  }

  @Test
  void pendingBookingHoldsSlot() throws Exception {
    act(employee, "submit");
    var other = ok(stranger, "POST", "/bookings", draft(resource, start, end));
    var v =
        Map.of(
            "version",
            other.path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            "");
    expect(stranger, "POST", "/bookings/" + other.path("id").asLong() + "/commands/submit", v, 409);
  }

  @Test
  void draftDoesNotHoldSlot() throws Exception {
    var other = ok(stranger, "POST", "/bookings", draft(resource, start, end));
    assertEquals("DRAFT", other.path("status").asString());
    act(employee, "submit");
  }

  @Test
  void capacityRejected() throws Exception {
    var v = draft(resource, start, end);
    v.put("attendees", 9);
    expect(employee, "POST", "/bookings", v, 400);
  }

  @Test
  void outsiderCannotReadDetails() throws Exception {
    expect(stranger, "GET", "/bookings/" + id, null, 403);
  }

  @Test
  void outsiderCalendarRedactsTitleAndId() throws Exception {
    approve();
    var s = ok(stranger, "GET", "/calendar?day=2026-10-05", null).path("resources");
    boolean found = false;
    for (var row : s)
      if (row.path("resource").path("id").asLong() == resource) {
        var slot = row.path("slots").get(0);
        assertEquals("BUSY", slot.path("label").asString());
        assertFalse(slot.has("bookingId"));
        assertFalse(slot.has("status"));
        found = true;
      }
    assertTrue(found);
  }

  @Test
  void privateResourceCannotBeBookedAcrossDepartments() throws Exception {
    var x = ok(manager, "POST", "/resources", resource(false, false, 0));
    expect(stranger, "POST", "/bookings", draft(x.path("id").asLong(), start, end), 403);
  }

  @Test
  void employeeCannotManageResources() throws Exception {
    expect(employee, "POST", "/resources", resource(false, true, 0), 403);
  }

  @Test
  void employeeCannotAdministerAccounts() throws Exception {
    expect(employee, "GET", "/admin/users", null, 403);
  }

  @Test
  void applicantCannotApproveSelf() throws Exception {
    act(employee, "submit");
    expect(employee, "POST", "/bookings/" + id + "/commands/approve", cmd(), 403);
  }

  @Test
  void unrelatedManagerCannotApprove() throws Exception {
    act(employee, "submit");
    expect(admin, "POST", "/bookings/" + id + "/commands/approve", cmd(), 403);
  }

  @Test
  void duplicateCommandDoesNotDuplicateEvents() throws Exception {
    var command = cmd();
    b = ok(employee, "POST", "/bookings/" + id + "/commands/submit", command);
    var again = ok(employee, "POST", "/bookings/" + id + "/commands/submit", command);
    assertEquals(b.path("version").asLong(), again.path("version").asLong());
    assertEquals(2, ok(employee, "GET", "/bookings/" + id, null).path("events").size());
    command.put("note", "变更的内容");
    expect(employee, "POST", "/bookings/" + id + "/commands/submit", command, 409);
  }

  @Test
  void rejectedCommandCanBeRetried() throws Exception {
    act(employee, "submit");
    var command = cmd();
    b = ok(manager, "POST", "/bookings/" + id + "/commands/reject", command);
    ok(manager, "POST", "/bookings/" + id + "/commands/reject", command);
    act(employee, "submit");
    assertEquals("PENDING", b.path("status").asString());
  }

  @Test
  void staleVersionCannotCancel() throws Exception {
    var old = cmd();
    act(employee, "submit");
    expect(employee, "POST", "/bookings/" + id + "/commands/cancel", old, 409);
  }

  @Test
  void cancellationReleasesSlot() throws Exception {
    approve();
    act(employee, "cancel");
    var other = ok(stranger, "POST", "/bookings", draft(resource, start, end));
    ok(
        stranger,
        "POST",
        "/bookings/" + other.path("id").asLong() + "/commands/submit",
        Map.of(
            "version",
            other.path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            ""));
  }

  @Test
  void bufferBlocksTouchingMeeting() throws Exception {
    approve();
    var other = ok(stranger, "POST", "/bookings", draft(resource, end, end.plusSeconds(1800)));
    expect(
        stranger,
        "POST",
        "/bookings/" + other.path("id").asLong() + "/commands/submit",
        Map.of(
            "version",
            other.path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            ""),
        409);
  }

  @Test
  void maintenanceCannotOverwriteBooking() throws Exception {
    act(employee, "submit");
    expect(
        manager,
        "POST",
        "/resources/" + resource + "/blocks",
        Map.of("startsAt", start.toString(), "endsAt", end.toString(), "reason", "验收测试维护"),
        409);
  }

  @Test
  void maintenanceBlocksBookingUntilCancelled() throws Exception {
    var block =
        ok(
            manager,
            "POST",
            "/resources/" + resource + "/blocks",
            Map.of("startsAt", start.toString(), "endsAt", end.toString(), "reason", "验收测试维护"));
    expect(employee, "POST", "/bookings/" + id + "/commands/submit", cmd(), 409);
    ok(
        manager,
        "POST",
        "/resources/" + resource + "/blocks/" + block.path("id").asLong() + "/cancel",
        Map.of("version", block.path("version").asLong()));
    act(employee, "submit");
  }

  @Test
  void activeReservationProtectsResourceRules() throws Exception {
    act(employee, "submit");
    expect(manager, "PUT", "/resources/" + resource, resource(false, true, 0), 409);
  }

  @Test
  void submittedBookingCannotBeDeleted() throws Exception {
    act(employee, "submit");
    expect(
        employee,
        "DELETE",
        "/bookings/" + id + "?version=" + b.path("version").asLong(),
        null,
        409);
  }

  @Test
  void untouchedDraftCanBeDeleted() throws Exception {
    ok(employee, "DELETE", "/bookings/" + id + "?version=" + b.path("version").asLong(), null);
    expect(employee, "GET", "/bookings/" + id, null, 404);
  }

  @Test
  void pendingExpiresAtStart() throws Exception {
    act(employee, "submit");
    clock.value = start;
    service.expireResource(resource);
    assertEquals("EXPIRED", current().path("status").asString());
  }

  @Test
  void noShowExpiresAtGraceBoundary() throws Exception {
    approve();
    clock.value = start.plusSeconds(600);
    service.expireResource(resource);
    assertEquals("NO_SHOW", current().path("status").asString());
  }

  @Test
  void checkedInMeetingFinishesAtScheduledEnd() throws Exception {
    approve();
    act(employee, "check-in");
    clock.value = end;
    service.expireResource(resource);
    assertEquals("COMPLETED", current().path("status").asString());
    assertEquals(end.toString(), current().path("completedAt").asString());
  }

  @Test
  void systemManagerWithoutBookingReadCanUseAdministration() throws Exception {
    long role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of("name", "独立系统管理测试", "scope", "ALL", "permissions", List.of("admin")))
            .path("id")
            .asLong();
    String name = "sys-" + UUID.randomUUID().toString().substring(0, 8);
    long account = user(name, role, dept);
    var who = login(name, password);
    expect(who, "GET", "/options", null, 200);
    expect(who, "GET", "/admin/users", null, 200);
    expect(who, "GET", "/resources", null, 403);
    expect(who, "GET", "/bookings", null, 403);
    ok(
        admin,
        "PUT",
        "/admin/users/" + account,
        Map.of(
            "username",
            name,
            "displayName",
            "独立管理验收",
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            false));
  }

  @Test
  void automaticResourceConfirmsWithoutApproval() throws Exception {
    var x = ok(manager, "POST", "/resources", resource(false, true, 0));
    b = ok(employee, "POST", "/bookings", draft(x.path("id").asLong(), start, end));
    id = b.path("id").asLong();
    act(employee, "submit");
    assertEquals("CONFIRMED", b.path("status").asString());
  }

  @Test
  void checkinTooEarlyRejected() throws Exception {
    approve();
    clock.value = start.minusSeconds(901);
    expect(employee, "POST", "/bookings/" + id + "/commands/check-in", cmd(), 400);
  }

  @Test
  void earlyCheckinCannotOverlapPreviousMeeting() throws Exception {
    var x = ok(manager, "POST", "/resources", resource(false, true, 0));
    long rid = x.path("id").asLong();
    clock.value = start.minusSeconds(3600);
    b = ok(employee, "POST", "/bookings", draft(rid, start.minusSeconds(1800), start));
    id = b.path("id").asLong();
    act(employee, "submit");
    clock.value = start.minusSeconds(1800);
    act(employee, "check-in");
    b = ok(employee, "POST", "/bookings", draft(rid, start, end));
    id = b.path("id").asLong();
    act(employee, "submit");
    clock.value = start.minusSeconds(900);
    expect(employee, "POST", "/bookings/" + id + "/commands/check-in", cmd(), 409);
  }

  @Test
  void simultaneousSubmissionsHaveSingleWinner() throws Exception {
    var x = ok(manager, "POST", "/resources", resource(false, true, 0));
    long rid = x.path("id").asLong();
    var first = ok(employee, "POST", "/bookings", draft(rid, start, end));
    var second = ok(stranger, "POST", "/bookings", draft(rid, start, end));
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      var jobs = new ArrayList<Future<Integer>>();
      for (var pair : List.of(Map.entry(employee, first), Map.entry(stranger, second)))
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  return call(
                          pair.getKey(),
                          "POST",
                          "/bookings/" + pair.getValue().path("id").asLong() + "/commands/submit",
                          Map.of(
                              "version",
                              pair.getValue().path("version").asLong(),
                              "requestKey",
                              UUID.randomUUID().toString(),
                              "note",
                              ""))
                      .getResponse()
                      .getStatus();
                }));
      gate.countDown();
      var codes = new ArrayList<Integer>();
      for (var job : jobs) codes.add(job.get(30, TimeUnit.SECONDS));
      Collections.sort(codes);
      assertEquals(List.of(200, 409), codes);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void exportHasNoCredentialsOrAdvertising() throws Exception {
    var text =
        call(employee, "GET", "/bookings/" + id + "/report.json", null)
            .getResponse()
            .getContentAsString();
    assertFalse(text.contains("password"));
    assertFalse(text.contains("zhuatech2"));
    assertTrue(text.contains("events"));
  }

  @Test
  void lastAdministratorCannotBeDisabled() throws Exception {
    long aid = ok(admin, "GET", "/auth/me", null).path("id").asLong();
    long role =
        ok(admin, "GET", "/auth/me", null).path("permissions").size() > 0
            ? ok(admin, "GET", "/admin/users", null).get(0).path("roleId").asLong()
            : 0;
    expect(
        admin,
        "PUT",
        "/admin/users/" + aid,
        Map.of(
            "username",
            "admin",
            "displayName",
            "管理员",
            "roleId",
            role,
            "departmentId",
            1,
            "enabled",
            false),
        409);
  }

  @Test
  void disabledSessionIsRejected() throws Exception {
    ok(
        admin,
        "PUT",
        "/admin/users/" + employeeId,
        Map.of(
            "username",
            employeeName,
            "displayName",
            "验收测试停用",
            "roleId",
            employeeRole,
            "departmentId",
            dept,
            "enabled",
            false));
    expect(employee, "GET", "/auth/me", null, 401);
  }

  @Test
  void csrfRequiredForWrite() throws Exception {
    var result =
        mvc.perform(
                post("/api/bookings")
                    .session(employee)
                    .contentType("application/json")
                    .content(json.writeValueAsString(draft(resource, start, end))))
            .andReturn();
    assertEquals(403, result.getResponse().getStatus());
  }

  @Test
  void anonymousReadRequiresLogin() throws Exception {
    assertEquals(401, mvc.perform(get("/api/bookings")).andReturn().getResponse().getStatus());
  }
}
