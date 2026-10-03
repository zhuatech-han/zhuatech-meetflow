// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: ["草稿", "Draft"],
  PENDING: ["待审批", "Pending"],
  CONFIRMED: ["已确认", "Confirmed"],
  IN_USE: ["使用中", "In use"],
  COMPLETED: ["已结束", "Completed"],
  REJECTED: ["已退回", "Returned"],
  CANCELLED: ["已取消", "Cancelled"],
  NO_SHOW: ["未签到", "No show"],
  EXPIRED: ["审批已过期", "Expired"],
};
export const commands = {
  submit: ["提交预约", "Submit"],
  approve: ["通过预约", "Approve"],
  reject: ["退回预约", "Return"],
  cancel: ["取消预约", "Cancel"],
  "admin-cancel": ["管理员取消", "Manager cancel"],
  "check-in": ["签到使用", "Check in"],
  finish: ["结束使用", "Finish"],
};
export const labels = {
  resourceId: ["预约资源", "Resource"],
  title: ["会议或使用事由", "Title"],
  purpose: ["用途说明", "Purpose"],
  attendees: ["使用人数", "Attendees"],
  startsAt: ["开始时间（上海时区）", "Start (Shanghai time)"],
  endsAt: ["结束时间（上海时区）", "End (Shanghai time)"],
  note: ["意见或原因", "Note / reason"],
  reason: ["维护或停用原因", "Maintenance reason"],
  code: ["资源代码", "Code"],
  name: ["名称", "Name"],
  nameEn: ["英文名称", "English name"],
  location: ["位置", "Location"],
  category: ["资源类型", "Resource type"],
  departmentId: ["所属部门", "Department"],
  stewardId: ["资源审批负责人", "Steward"],
  capacity: ["容纳人数", "Capacity"],
  openMinute: ["开放时间", "Opens"],
  closeMinute: ["关闭时间", "Closes"],
  bufferMinutes: ["周转缓冲（分钟）", "Turnaround minutes"],
  maxDurationMinutes: ["单次最长使用（分钟）", "Maximum duration"],
  minNoticeMinutes: ["最少提前预约（分钟）", "Minimum notice"],
  checkInGraceMinutes: ["签到宽限（分钟）", "Check-in grace"],
  weekdays: [
    "开放星期（1周一，7周日，逗号分隔）",
    "Weekdays (Mon=1, Sun=7, comma separated)",
  ],
  approvalRequired: ["须负责人审批", "Approval required"],
  shared: ["向其他部门开放", "Shared with other departments"],
  enabled: ["启用", "Enabled"],
  username: ["登录账号", "Username"],
  displayName: ["显示名称", "Display name"],
  password: ["初始或重置密码", "Initial / reset password"],
  roleId: ["角色", "Role"],
  scope: ["数据范围", "Scope"],
  permissions: ["权限", "Permissions"],
  permissionCode: ["菜单所需权限", "Required permission"],
  position: ["顺序", "Order"],
  type: ["字典类型", "Dictionary type"],
  value: ["参数值", "Value"],
  oldPassword: ["当前密码", "Current password"],
  newPassword: ["新密码", "New password"],
};
export const errors = {
  UNAUTHENTICATED: [
    "会话已失效，请重新登录",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号、密码错误或账号停用",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: ["请五分钟后重试登录", "Retry in five minutes."],
  FORBIDDEN: ["没有操作权限", "Permission denied."],
  OUT_OF_SCOPE: ["没有此记录的数据权限", "Outside your data scope."],
  NOT_OWNER: ["仅预约人本人可操作", "Booking owner only."],
  NOT_APPROVER: ["仅指定审批人可审批", "Designated steward only."],
  STALE_VERSION: [
    "预约或规则已更新，请刷新后操作",
    "Record changed. Refresh first.",
  ],
  INVALID_STATE: ["状态已改变，请刷新详情", "State changed. Refresh detail."],
  TIME_CONFLICT: [
    "此时段已被预约或仍在周转缓冲中",
    "Time is booked or in turnaround buffer.",
  ],
  MAINTENANCE_CONFLICT: [
    "此时段有维护或停用安排",
    "Resource is unavailable for maintenance.",
  ],
  INVALID_TIME: [
    "请检查时间顺序和15分钟刻度",
    "Check times and quarter-hour alignment.",
  ],
  OUTSIDE_OPENING_HOURS: [
    "超出开放时间、开放星期或单次使用上限，结束后须留出周转时间",
    "Outside opening hours, weekdays or duration limit. Include turnaround.",
  ],
  CAPACITY_EXCEEDED: [
    "人数超出资源容量或未填有效人数",
    "Attendance exceeds capacity or is invalid.",
  ],
  BOOKING_WINDOW: [
    "不满足提前预约时间或超出预约窗口",
    "Outside the notice or booking window.",
  ],
  RESOURCE_HAS_BOOKINGS: [
    "须先处理有效预约，再调整资源规则",
    "Resolve active bookings before changing rules.",
  ],
  RESOURCE_DISABLED: ["资源已停用", "Resource is disabled."],
  INDEPENDENT_APPROVER: [
    "申请人不能审批自己的预约",
    "Owner cannot approve their own booking.",
  ],
  INVALID_STEWARD: [
    "负责人须启用，具备资源管理和审批权限，且属于对应部门或全范围角色",
    "Choose an active authorized department or all-data steward.",
  ],
  RESOURCE_IMMUTABLE: [
    "草稿不能更换资源，请另建预约",
    "Create a new booking to change resources.",
  ],
  CHECKIN_WINDOW: [
    "只可在开始前15分钟至签到宽限截止前签到，且须在结束前",
    "Check in from 15 minutes before start until grace deadline, before end.",
  ],
  HISTORY_PROTECTED: [
    "已提交预约须取消，不能删除历史",
    "Cancel submitted bookings; history is protected.",
  ],
  IDEMPOTENCY_CONFLICT: [
    "操作内容已变更，请重新打开操作",
    "Request content changed. Reopen the action.",
  ],
  INVALID_REQUEST_KEY: ["请重新打开操作", "Reopen the action."],
  INVALID_RESOURCE: [
    "请检查资源容量、时间、代码和星期",
    "Check resource capacity, hours, code and weekdays.",
  ],
  INVALID_DICTIONARY: ["资源类型不存在", "Unknown resource type."],
  INVALID_INPUT: [
    "请检查必填项、长度和日期",
    "Check required fields, lengths and dates.",
  ],
  CONFLICT: ["代码重复或记录仍被使用", "Duplicate code or referenced record."],
  LAST_ADMIN: [
    "须保留一个启用的全范围管理员",
    "Keep an active all-data administrator.",
  ],
  WEAK_PASSWORD: [
    "密码至少12位，含大写、小写、数字，UTF-8不超过72字节",
    "Use 12+ characters, upper/lower case and digits, up to 72 UTF-8 bytes.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码不正确", "Current password is incorrect."],
  BUILTIN_RESOURCE: [
    "内建资源不能删除",
    "Built-in resource cannot be deleted.",
  ],
  NOT_FOUND: ["记录不存在", "Record not found."],
  REPORT_LIMIT: [
    "超过一万条记录，请缩小范围",
    "More than 10,000 records. Narrow scope.",
  ],
  INVALID_SETTING: [
    "参数不正确；时区固定为Asia/Shanghai，预约窗口为1–180天",
    "Invalid setting. Timezone is fixed; horizon is 1–180 days.",
  ],
};
/** 以固定上海时区显示日期时间，不依赖浏览器所在时区。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value) {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}
/** 将服务器 UTC 时间转为上海本地表单字符串。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(value) {
  if (!value) return "";
  return new Date(new Date(value).getTime() + 8 * 3600000)
    .toISOString()
    .slice(0, 16);
}
/** 上海表单时间转为带时区 ISO 时间，拒绝非法或缺失输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function instant(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value))
    throw new Error("INVALID_TIME");
  const d = new Date(value + ":00+08:00");
  if (!Number.isFinite(d.getTime()) || localInput(d.toISOString()) !== value)
    throw new Error("INVALID_TIME");
  return d.toISOString();
}
/** 新预约从下个15分钟刻度开始，最少提前量也计入建议。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function nextSlot(value, notice = 0) {
  const n = new Date(value).getTime() + notice * 60000;
  return new Date((Math.floor(n / 900000) + 1) * 900000).toISOString();
}
/** 分钟值显示为时钟字符串，包含关闭时间24:00。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function clockTime(minutes) {
  return (
    String(Math.floor(minutes / 60)).padStart(2, "0") +
    ":" +
    String(minutes % 60).padStart(2, "0")
  );
}
/** 判断本人草稿编辑入口，服务端仍独立验权。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function editable(b, me) {
  return (
    !!b &&
    !!me &&
    b.ownerId === me.id &&
    me.permissions.includes("booking.write") &&
    ["DRAFT", "REJECTED"].includes(b.status)
  );
}
/** 有限状态按钮；不授予仅通过页面隐藏获得的权限。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(b, me, r) {
  if (!b || !me) return [];
  const list = [];
  if (b.ownerId === me.id && me.permissions.includes("booking.write")) {
    if (["DRAFT", "REJECTED"].includes(b.status)) list.push("submit");
    if (["DRAFT", "REJECTED", "PENDING", "CONFIRMED"].includes(b.status))
      list.push("cancel");
    if (b.status === "CONFIRMED") list.push("check-in");
    if (b.status === "IN_USE") list.push("finish");
  }
  if (
    b.reviewerId === me.id &&
    me.permissions.includes("booking.approve") &&
    b.status === "PENDING"
  )
    list.push("approve", "reject");
  if (
    b.ownerId !== me.id &&
    me.permissions.includes("resource.manage") &&
    (me.scope === "ALL" || me.departmentId === r.departmentId) &&
    ["DRAFT", "REJECTED", "PENDING", "CONFIRMED"].includes(b.status)
  )
    list.push("admin-cancel");
  return list;
}
/** 将占用段裁剪到当日0–24时的时间轴，跨日缓冲不会溢出布局。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function slotStyle(slot, day) {
  const beginning = new Date(day + "T00:00:00+08:00").getTime();
  const start = Math.max(
    0,
    (new Date(slot.startsAt).getTime() - beginning) / 60000,
  );
  const end = Math.min(
    1440,
    (new Date(slot.endsAt).getTime() - beginning) / 60000,
  );
  return {
    left: (start / 1440) * 100 + "%",
    width: (Math.max(0, end - start) / 1440) * 100 + "%",
  };
}
