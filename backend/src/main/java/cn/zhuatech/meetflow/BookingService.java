// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.meetflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/**
 * 预约事务、隐私日历、资源规则与维护；写事务使用 READ_COMMITTED 和资源行锁。 官网 https://www.zhuatech.cn/；微信 zhuatech /
 * zhuatech2。
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class BookingService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;
  static final String HELD = "('PENDING','CONFIRMED','IN_USE','COMPLETED')";

  public BookingService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 资源有限字段，不允许通过输入覆盖预约状态。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ResourceInput(
      Long version,
      String code,
      String name,
      String location,
      String category,
      Long departmentId,
      Long stewardId,
      int capacity,
      int openMinute,
      int closeMinute,
      int bufferMinutes,
      int maxDurationMinutes,
      int minNoticeMinutes,
      int checkInGraceMinutes,
      String weekdays,
      boolean approvalRequired,
      boolean shared,
      boolean enabled) {}

  /** 本人草稿；资源创建后不可换成另一个资源，修改时间不取得占用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      Long resourceId,
      String title,
      String purpose,
      int attendees,
      Instant startsAt,
      Instant endsAt,
      String requestKey) {}

  /** 状态命令必须带版本与独立请求键。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(Long version, String requestKey, String note) {}

  /** 维护时段与取消的版本输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record BlockInput(Long version, Instant startsAt, Instant endsAt, String reason) {}

  /** 仅返回适用的基础目录与服务时间；账号不含密码或用户名。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.current();
    var users =
        db.all(Account.class).stream()
            .filter(a -> a.enabled)
            .filter(
                a -> {
                  var role = db.get(AccessRole.class, a.roleId);
                  return role.permissions.contains("resource.manage")
                      && role.permissions.contains("booking.approve")
                      && !role.scope.equals("ASSIGNED");
                })
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "scope",
                        db.get(AccessRole.class, a.roleId).scope))
            .toList();
    return Map.of(
        "departments",
        db.all(Department.class),
        "stewards",
        users,
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class),
        "serverNow",
        clock.instant());
  }

  /** 可预约资源目录，部门私有资源不会泄露到其他部门。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<MeetingResource> resources() {
    if (!access.role().permissions.contains("resource.manage")) access.require("booking.read");
    return db.all(MeetingResource.class).stream().filter(this::eligible).toList();
  }

  /** 资源创建与带版本更新；有效占用存在时禁止改变规则。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public MeetingResource saveResource(Long id, ResourceInput v) {
    access.require("resource.manage");
    access.department(v.departmentId);
    db.get(Department.class, v.departmentId);
    var r = id == null ? new MeetingResource() : lockResource(id);
    if (id != null) {
      access.department(r.departmentId);
      version(r.version, v.version);
      if (activeCount(id) > 0) throw new Problem(409, "RESOURCE_HAS_BOOKINGS");
    }
    r.code = AdminService.text(v.code, 60).toUpperCase(Locale.ROOT);
    if (!r.code.matches("[A-Z0-9_.-]{2,60}")) throw new Problem(400, "INVALID_RESOURCE");
    r.name = AdminService.text(v.name, 120);
    r.location = AdminService.text(v.location, 200);
    r.category = AdminService.text(v.category, 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='resource' and code=?1",
            r.category)
        .isEmpty()) throw new Problem(400, "INVALID_DICTIONARY");
    if (v.capacity < 1
        || v.capacity > 10000
        || v.openMinute < 0
        || v.closeMinute > 1440
        || v.openMinute >= v.closeMinute
        || v.openMinute % 15 != 0
        || v.closeMinute % 15 != 0
        || v.bufferMinutes < 0
        || v.bufferMinutes > 120
        || v.maxDurationMinutes < 15
        || v.maxDurationMinutes > 480
        || v.minNoticeMinutes < 0
        || v.minNoticeMinutes > 1440
        || v.checkInGraceMinutes < 5
        || v.checkInGraceMinutes > 60) throw new Problem(400, "INVALID_RESOURCE");
    var days = AdminService.text(v.weekdays, 20).split(",");
    var set = new TreeSet<String>();
    for (var s : days) {
      if (!s.matches("[1-7]") || !set.add(s)) throw new Problem(400, "INVALID_RESOURCE");
    }
    r.departmentId = v.departmentId;
    r.stewardId = steward(v.stewardId, v.departmentId).id;
    r.capacity = v.capacity;
    r.openMinute = v.openMinute;
    r.closeMinute = v.closeMinute;
    r.bufferMinutes = v.bufferMinutes;
    r.maxDurationMinutes = v.maxDurationMinutes;
    r.minNoticeMinutes = v.minNoticeMinutes;
    r.checkInGraceMinutes = v.checkInGraceMinutes;
    r.weekdays = String.join(",", set);
    r.approvalRequired = v.approvalRequired;
    r.shared = v.shared;
    r.enabled = v.enabled;
    if (id == null) db.save(r);
    db.flush();
    access.audit("RESOURCE_SAVE", r.id, r.departmentId);
    return r;
  }

  /** 仅删除没有业务引用的资源，其他情况由外键保护。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteResource(Long id, Long version) {
    access.require("resource.manage");
    var r = lockResource(id);
    access.department(r.departmentId);
    version(r.version, version);
    access.audit("RESOURCE_DELETE", id, r.departmentId);
    db.delete(r);
  }

  /** 可见资源的维护历史；非管理者只通过隐私日历看忙碌时间。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<ResourceBlock> blocks(Long id) {
    access.require("resource.manage");
    var r = db.get(MeetingResource.class, id);
    access.department(r.departmentId);
    return db.query(
        ResourceBlock.class, "from ResourceBlock where resourceId=?1 order by startsAt desc", id);
  }

  /** 维护时间不能压过预约或另一维护段；与预约共用资源锁。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ResourceBlock addBlock(Long id, BlockInput v) {
    access.require("resource.manage");
    var r = lockResource(id);
    access.department(r.departmentId);
    var start = v.startsAt;
    var end = v.endsAt;
    if (start == null
        || end == null
        || !start.isBefore(end)
        || start.isBefore(clock.instant())
        || Duration.between(start, end).toDays() > 30
        || start.isAfter(clock.instant().plusSeconds(180 * 86400L)))
      throw new Problem(400, "INVALID_TIME");
    conflict(r.id, null, start, end);
    var b = new ResourceBlock();
    b.resourceId = id;
    b.reason = AdminService.text(v.reason, 1000);
    b.status = "ACTIVE";
    b.startsAt = start;
    b.endsAt = end;
    b.actor = access.current().username;
    b.createdAt = clock.instant();
    db.save(b);
    access.audit("BLOCK_CREATE", b.id, r.departmentId);
    return b;
  }

  /** 取消维护保留原始原因与记录，重试取消不会删除历史。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ResourceBlock cancelBlock(Long id, Long bid, BlockInput v) {
    access.require("resource.manage");
    var r = lockResource(id);
    access.department(r.departmentId);
    var b = db.lock(ResourceBlock.class, bid);
    if (!b.resourceId.equals(id)) throw new Problem(403, "OUT_OF_SCOPE");
    if (b.status.equals("CANCELLED")) return b;
    version(b.version, v.version);
    b.status = "CANCELLED";
    access.audit("BLOCK_CANCEL", bid, r.departmentId);
    return b;
  }

  /** 草稿创建可以安全重试，不占用日历时段。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Booking create(Draft v) {
    access.require("booking.write");
    var fp = fingerprint("create", null, v);
    var replay = replay(v.requestKey, fp);
    if (replay != null) {
      readable(replay);
      return replay;
    }
    var r = lockResource(v.resourceId);
    bookable(r);
    var a = access.current();
    var b = new Booking();
    b.resourceId = r.id;
    b.ownerId = a.id;
    b.departmentId = a.departmentId;
    b.status = "DRAFT";
    b.createdAt = clock.instant();
    b.updatedAt = b.createdAt;
    b.submitted = false;
    b.bufferMinutes = 0;
    b.checkInGraceMinutes = 0;
    b.changeCount = 0;
    assign(b, r, v);
    db.save(b);
    db.flush();
    event(b, "CREATE", "", a.username);
    stamp(b, v.requestKey, fp);
    return b;
  }

  /** 编辑仅限本人草稿或退回记录，不改变资源，不释放他人占用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Booking save(Long id, Draft v) {
    access.require("booking.write");
    var b = lockedBooking(id);
    owner(b);
    var fp = fingerprint("save", id, v);
    var replay = replay(v.requestKey, fp);
    if (replay != null) return replay;
    version(b.version, v.version);
    state(b, "DRAFT", "REJECTED");
    if (!b.resourceId.equals(v.resourceId)) throw new Problem(400, "RESOURCE_IMMUTABLE");
    var r = db.get(MeetingResource.class, b.resourceId);
    bookable(r);
    assign(b, r, v);
    changed(b);
    event(b, "SAVE", "", access.current().username);
    stamp(b, v.requestKey, fp);
    return b;
  }

  /** 未提交的草稿可以删除；已提交记录只通过状态取消。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long v) {
    access.require("booking.write");
    var b = lockedBooking(id);
    owner(b);
    version(b.version, v);
    if (b.submitted || !b.status.equals("DRAFT")) throw new Problem(409, "HISTORY_PROTECTED");
    db.query(BookingCommand.class, "from BookingCommand where bookingId=?1", id)
        .forEach(db::delete);
    db.query(BookingEvent.class, "from BookingEvent where bookingId=?1", id).forEach(db::delete);
    access.audit("DRAFT_DELETE", id, b.departmentId);
    db.delete(b);
  }

  /** 带版本与幂等的提交、审批、取消、签到和结束命令。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Booking act(Long id, String action, Command v) {
    var b = lockedBooking(id);
    readable(b);
    var r = db.get(MeetingResource.class, b.resourceId);
    authorize(action, b, r);
    var fp = fingerprint(action, id, v);
    var replay = replay(v.requestKey, fp);
    if (replay != null) return replay;
    version(b.version, v.version);
    String note = v.note == null ? "" : v.note.trim();
    if (note.length() > 1000) throw new Problem(400, "INVALID_INPUT");
    var now = clock.instant();
    switch (action) {
      case "submit" -> {
        state(b, "DRAFT", "REJECTED");
        bookable(r);
        BookingPolicy.validate(r, b.startsAt, b.endsAt, b.attendees, now, horizon());
        steward(r.stewardId, r.departmentId);
        if (r.approvalRequired && r.stewardId.equals(b.ownerId))
          throw new Problem(400, "INDEPENDENT_APPROVER");
        b.bufferMinutes = r.bufferMinutes;
        b.checkInGraceMinutes = r.checkInGraceMinutes;
        b.occupancyFrom = b.startsAt;
        b.occupiedUntil = b.endsAt.plusSeconds(r.bufferMinutes * 60L);
        b.reviewerId = r.approvalRequired ? r.stewardId : null;
        conflict(r.id, b.id, b.occupancyFrom, b.occupiedUntil);
        b.status = r.approvalRequired ? "PENDING" : "CONFIRMED";
        b.submitted = true;
      }
      case "approve" -> {
        state(b, "PENDING");
        if (!now.isBefore(b.startsAt)) throw new Problem(409, "INVALID_STATE");
        b.status = "CONFIRMED";
      }
      case "reject" -> {
        state(b, "PENDING");
        AdminService.text(note, 1000);
        b.status = "REJECTED";
      }
      case "cancel", "admin-cancel" -> {
        state(b, "DRAFT", "REJECTED", "PENDING", "CONFIRMED");
        AdminService.text(note, 1000);
        b.status = "CANCELLED";
      }
      case "check-in" -> {
        state(b, "CONFIRMED");
        if (now.isBefore(b.startsAt.minusSeconds(900))
            || !now.isBefore(b.startsAt.plusSeconds(b.checkInGraceMinutes * 60L))
            || !now.isBefore(b.endsAt)) throw new Problem(400, "CHECKIN_WINDOW");
        var from = now.isBefore(b.startsAt) ? now : b.startsAt;
        conflict(r.id, b.id, from, b.occupiedUntil);
        b.occupancyFrom = from;
        b.checkedInAt = now;
        b.status = "IN_USE";
      }
      case "finish" -> {
        state(b, "IN_USE");
        AdminService.text(note, 1000);
        b.completedAt = now;
        b.occupiedUntil = now.plusSeconds(b.bufferMinutes * 60L);
        b.status = "COMPLETED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    changed(b);
    event(b, action.toUpperCase(Locale.ROOT), note, access.current().username);
    stamp(b, v.requestKey, fp);
    return b;
  }

  /** 分页查询单据，固定排序与绑定检索值，按角色范围过滤。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(
      String search,
      String status,
      Long resourceId,
      int page,
      int size,
      String sort,
      boolean mine) {
    access.require("booking.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("time", "newest", "title").contains(sort))
      throw new Problem(400, "INVALID_INPUT");
    var clause = scope("b");
    var params = new ArrayList<Object>(clause.params);
    var where = clause.where;
    if (mine) {
      params.add(access.current().id);
      where += " and b.ownerId=?" + params.size();
    }
    if (!search.isBlank()) {
      params.add("%" + search.toLowerCase(Locale.ROOT) + "%");
      where += " and lower(b.title) like ?" + params.size();
    }
    if (!status.isBlank()) {
      params.add(status);
      where += " and b.status=?" + params.size();
    }
    if (resourceId != null) {
      params.add(resourceId);
      where += " and b.resourceId=?" + params.size();
    }
    var q =
        db.jpql(
            Booking.class,
            "from Booking b where "
                + where
                + " order by "
                + switch (sort) {
                  case "newest" -> "b.id desc";
                  case "title" -> "b.title,b.id";
                  default -> "b.startsAt desc,b.id desc";
                });
    var n = db.jpql(Long.class, "select count(b) from Booking b where " + where);
    for (int i = 0; i < params.size(); i++) {
      q.setParameter(i + 1, params.get(i));
      n.setParameter(i + 1, params.get(i));
    }
    return Map.of(
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList(),
        "total",
        n.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 获取授权单据及不可变事件。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("booking.read");
    var b = db.get(Booking.class, id);
    readable(b);
    return Map.of(
        "booking",
        b,
        "resource",
        db.get(MeetingResource.class, b.resourceId),
        "events",
        db.query(BookingEvent.class, "from BookingEvent where bookingId=?1 order by id", id));
  }

  /** 日历只返回占用段，能读单据的人才得到标题与详情链接。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object calendar(LocalDate day) {
    access.require("booking.read");
    if (day == null
        || Math.abs(
                java.time.temporal.ChronoUnit.DAYS.between(
                    clock.instant().atZone(BookingPolicy.ZONE).toLocalDate(), day))
            > 366) throw new Problem(400, "INVALID_TIME");
    var from = day.atStartOfDay(BookingPolicy.ZONE).toInstant();
    var until = from.plusSeconds(86400);
    var rows = new ArrayList<Object>();
    for (var r : resources()) {
      var slots = new ArrayList<Object>();
      for (var b :
          bounded(
              Booking.class,
              "from Booking where resourceId=?1 and status in "
                  + HELD
                  + " and occupancyFrom<?2 and occupiedUntil>?3 order by occupancyFrom",
              r.id,
              until,
              from)) {
        var s = new LinkedHashMap<String, Object>();
        s.put("startsAt", b.occupancyFrom);
        s.put("endsAt", b.occupiedUntil);
        s.put("kind", "BOOKING");
        s.put("label", canRead(b) ? b.title : "BUSY");
        if (canRead(b)) {
          s.put("bookingId", b.id);
          s.put("status", b.status);
        }
        slots.add(s);
      }
      for (var block :
          bounded(
              ResourceBlock.class,
              "from ResourceBlock where resourceId=?1 and status='ACTIVE' and startsAt<?2 and endsAt>?3",
              r.id,
              until,
              from))
        slots.add(
            Map.of(
                "startsAt",
                block.startsAt,
                "endsAt",
                block.endsAt,
                "kind",
                "MAINTENANCE",
                "label",
                "MAINTENANCE"));
      rows.add(Map.of("resource", r, "slots", slots));
    }
    return Map.of("day", day, "resources", rows, "serverNow", clock.instant());
  }

  /** 使用统计限于授权预约，计划时长与真实签到使用时长分开。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = visibleBookings();
    long planned =
        rows.stream()
            .filter(
                b -> b.submitted && !Set.of("REJECTED", "CANCELLED", "EXPIRED").contains(b.status))
            .mapToLong(b -> Duration.between(b.startsAt, b.endsAt).toMinutes())
            .sum();
    long used =
        rows.stream()
            .filter(b -> b.checkedInAt != null)
            .mapToLong(
                b ->
                    Math.max(
                        0,
                        Duration.between(
                                b.checkedInAt,
                                b.completedAt == null
                                    ? (clock.instant().isBefore(b.endsAt)
                                        ? clock.instant()
                                        : b.endsAt)
                                    : b.completedAt)
                            .toMinutes()))
            .sum();
    var by = new ArrayList<Object>();
    for (var r : db.all(MeetingResource.class).stream().filter(this::eligible).toList()) {
      var matches = rows.stream().filter(b -> b.resourceId.equals(r.id)).toList();
      by.add(
          Map.of(
              "name",
              r.name,
              "bookings",
              matches.size(),
              "completed",
              matches.stream().filter(b -> b.status.equals("COMPLETED")).count()));
    }
    return Map.of(
        "total",
        rows.size(),
        "pending",
        rows.stream().filter(b -> b.status.equals("PENDING")).count(),
        "confirmed",
        rows.stream().filter(b -> b.status.equals("CONFIRMED")).count(),
        "inUse",
        rows.stream().filter(b -> b.status.equals("IN_USE")).count(),
        "noShow",
        rows.stream().filter(b -> b.status.equals("NO_SHOW")).count(),
        "plannedMinutes",
        planned,
        "usedMinutes",
        used,
        "resources",
        by);
  }

  /** 待办分本人活动预约与指定审批，最多一万条。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("booking.read");
    var me = access.current();
    var rows = visibleBookings();
    return Map.of(
        "mine",
        rows.stream()
            .filter(
                b ->
                    b.ownerId.equals(me.id)
                        && Set.of("DRAFT", "REJECTED", "CONFIRMED", "PENDING", "IN_USE")
                            .contains(b.status))
            .toList(),
        "reviews",
        rows.stream()
            .filter(b -> Objects.equals(b.reviewerId, me.id) && b.status.equals("PENDING"))
            .toList());
  }

  /** 审计按部门或本人范围，不含密码与完整预约载荷。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    var a = access.current();
    var scope = access.role().scope;
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                scope.equals("ALL")
                    || scope.equals("DEPARTMENT") && e.departmentId.equals(a.departmentId)
                    || scope.equals("ASSIGNED") && e.actor.equals(a.username))
        .toList();
  }

  /** 导出单据与事件，无品牌广告或凭据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export(Long id) {
    access.require("export");
    return json.writeValueAsString(detail(id));
  }

  /** 调度器逐资源事务清理超时；前台写入也会在取得资源锁后执行。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void expireResource(Long id) {
    var r = db.lock(MeetingResource.class, id);
    expireLocked(r);
  }

  /** 返回有界待扫描资源编号，不需要伪造系统登录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Long> sweepIds() {
    return db.all(MeetingResource.class).stream().map(r -> r.id).toList();
  }

  private MeetingResource lockResource(Long id) {
    var r = db.lock(MeetingResource.class, id);
    db.refresh(r);
    expireLocked(r);
    return r;
  }

  private Booking lockedBooking(Long id) {
    var initial = db.get(Booking.class, id);
    lockResource(initial.resourceId);
    var b = db.lock(Booking.class, id);
    db.refresh(b);
    return b;
  }

  private void expireLocked(MeetingResource r) {
    var now = clock.instant();
    var rows =
        bounded(
            Booking.class,
            "from Booking where resourceId=?1 and status in ('PENDING','CONFIRMED','IN_USE')",
            r.id);
    for (var b : rows) {
      String action = null;
      if (b.status.equals("PENDING") && !now.isBefore(b.startsAt)) {
        b.status = "EXPIRED";
        action = "EXPIRED";
      } else if (b.status.equals("CONFIRMED")
          && (!now.isBefore(b.startsAt.plusSeconds(b.checkInGraceMinutes * 60L))
              || !now.isBefore(b.endsAt))) {
        b.status = "NO_SHOW";
        action = "NO_SHOW";
      } else if (b.status.equals("IN_USE") && !now.isBefore(b.endsAt)) {
        b.status = "COMPLETED";
        b.completedAt = b.endsAt;
        b.occupiedUntil = b.endsAt.plusSeconds(b.bufferMinutes * 60L);
        action = "AUTO_FINISH";
      }
      if (action != null) {
        changed(b);
        event(b, action, "", "SYSTEM");
      }
    }
  }

  private boolean eligible(MeetingResource r) {
    return r.shared || access.visible(r.departmentId);
  }

  private void bookable(MeetingResource r) {
    if (!eligible(r)) throw new Problem(403, "OUT_OF_SCOPE");
    if (!r.enabled) throw new Problem(409, "RESOURCE_DISABLED");
  }

  private Account steward(Long id, Long department) {
    if (id == null) throw new Problem(400, "INVALID_STEWARD");
    var a = db.get(Account.class, id);
    var role = db.get(AccessRole.class, a.roleId);
    if (!a.enabled
        || !role.permissions.containsAll(Set.of("booking.approve", "resource.manage"))
        || role.scope.equals("ASSIGNED")
        || !role.scope.equals("ALL") && !a.departmentId.equals(department))
      throw new Problem(400, "INVALID_STEWARD");
    return a;
  }

  private void assign(Booking b, MeetingResource r, Draft v) {
    b.title = AdminService.text(v.title, 200);
    b.purpose = AdminService.text(v.purpose, 2000);
    BookingPolicy.validate(r, v.startsAt, v.endsAt, v.attendees, clock.instant(), horizon());
    b.attendees = v.attendees;
    b.startsAt = v.startsAt;
    b.endsAt = v.endsAt;
    b.occupancyFrom = b.startsAt;
    b.occupiedUntil = b.endsAt;
  }

  private int horizon() {
    return Integer.parseInt(
        db.query(SystemSetting.class, "from SystemSetting where code='bookingHorizonDays'")
            .getFirst()
            .value);
  }

  private void conflict(Long resource, Long except, Instant from, Instant until) {
    var rows =
        bounded(
            Booking.class,
            "from Booking where resourceId=?1 and status in "
                + HELD
                + " and occupancyFrom<?2 and occupiedUntil>?3",
            resource,
            until,
            from);
    if (rows.stream().anyMatch(b -> !Objects.equals(b.id, except)))
      throw new Problem(409, "TIME_CONFLICT");
    if (!bounded(
            ResourceBlock.class,
            "from ResourceBlock where resourceId=?1 and status='ACTIVE' and startsAt<?2 and endsAt>?3",
            resource,
            until,
            from)
        .isEmpty()) throw new Problem(409, "MAINTENANCE_CONFLICT");
  }

  private long activeCount(Long id) {
    return db.jpql(
            Long.class,
            "select count(b) from Booking b where b.resourceId=?1 and b.status in ('PENDING','CONFIRMED','IN_USE')")
        .setParameter(1, id)
        .getSingleResult();
  }

  private boolean canRead(Booking b) {
    var a = access.current();
    var role = access.role();
    return role.scope.equals("ALL")
        || role.scope.equals("DEPARTMENT")
            && (b.departmentId.equals(a.departmentId)
                || (role.permissions.contains("resource.manage")
                    && db.get(MeetingResource.class, b.resourceId)
                        .departmentId
                        .equals(a.departmentId)))
        || b.ownerId.equals(a.id)
        || Objects.equals(b.reviewerId, a.id);
  }

  private void readable(Booking b) {
    access.require("booking.read");
    if (!canRead(b)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  private void owner(Booking b) {
    if (!b.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_OWNER");
  }

  private void authorize(String action, Booking b, MeetingResource r) {
    switch (action) {
      case "approve", "reject" -> {
        access.require("booking.approve");
        if (!Objects.equals(b.reviewerId, access.current().id)
            || b.ownerId.equals(access.current().id)) throw new Problem(403, "NOT_APPROVER");
      }
      case "admin-cancel" -> {
        access.require("resource.manage");
        access.department(r.departmentId);
      }
      case "submit", "cancel", "check-in", "finish" -> {
        access.require("booking.write");
        owner(b);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
  }

  private void version(Long a, Long b) {
    if (b == null || !Objects.equals(a, b)) throw new Problem(409, "STALE_VERSION");
  }

  private void state(Booking b, String... states) {
    if (!Set.of(states).contains(b.status)) throw new Problem(409, "INVALID_STATE");
  }

  private void changed(Booking b) {
    b.changeCount++;
    b.updatedAt = clock.instant();
    db.flush();
  }

  private void event(Booking b, String action, String note, String actor) {
    var e = new BookingEvent();
    e.bookingId = b.id;
    e.action = action;
    e.note = note;
    e.actor = actor;
    e.snapshot = json.writeValueAsString(b);
    e.createdAt = clock.instant();
    db.save(e);
    var a = new AuditEvent();
    a.actor = actor;
    a.action = action;
    a.objectId = String.valueOf(b.id);
    a.departmentId = b.departmentId;
    a.createdAt = e.createdAt;
    db.save(a);
  }

  private String fingerprint(String action, Long id, Object v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (action + ":" + id + ":" + json.writeValueAsString(v))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Booking replay(String key, String fingerprint) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{16,80}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows =
        db.query(
            BookingCommand.class,
            "from BookingCommand where actor=?1 and requestKey=?2",
            access.current().username,
            key);
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fingerprint))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return db.get(Booking.class, rows.getFirst().bookingId);
  }

  private void stamp(Booking b, String key, String fingerprint) {
    var s = new BookingCommand();
    s.bookingId = b.id;
    s.actor = access.current().username;
    s.requestKey = key;
    s.fingerprint = fingerprint;
    db.save(s);
    db.flush();
  }

  private record Clause(String where, List<Object> params) {}

  private Clause scope(String alias) {
    var a = access.current();
    var role = access.role();
    if (role.scope.equals("ALL")) return new Clause("1=1", List.of());
    if (role.scope.equals("DEPARTMENT"))
      return new Clause(
          "("
              + alias
              + ".departmentId=?1 or "
              + alias
              + ".reviewerId=?2"
              + (role.permissions.contains("resource.manage")
                  ? " or "
                      + alias
                      + ".resourceId in (select r.id from MeetingResource r where r.departmentId=?1)"
                  : "")
              + ")",
          List.of(a.departmentId, a.id));
    return new Clause("(" + alias + ".ownerId=?1 or " + alias + ".reviewerId=?1)", List.of(a.id));
  }

  private List<Booking> visibleBookings() {
    access.require("booking.read");
    var c = scope("b");
    return bounded(
        Booking.class,
        "from Booking b where " + c.where + " order by b.startsAt",
        c.params.toArray());
  }

  private <T> List<T> bounded(Class<T> type, String sql, Object... args) {
    var q = db.jpql(type, sql);
    for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
    var rows = q.setMaxResults(10001).getResultList();
    if (rows.size() > 10000) throw new Problem(400, "REPORT_LIMIT");
    return rows;
  }
}
