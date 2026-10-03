<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  CalendarDays,
  LayoutDashboard,
  Building2,
  ClipboardList,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  ChevronLeft,
  ChevronRight,
  Plus,
  Search,
  Clock3,
  ArrowUpRight,
  RefreshCw,
  X,
  CheckCircle2,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  errors,
  date,
  localInput,
  instant,
  nextSlot,
  clockTime,
  editable,
  actions,
  slotStyle,
} from "./schema.js";
const me = ref(null),
  lang = ref(localStorage.getItem("meetflow-language") || "zh"),
  login = ref({ username: "", password: "" }),
  page = ref("calendar"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  success = ref(""),
  options = ref({
    departments: [],
    stewards: [],
    dictionaries: [],
    settings: [],
  }),
  resources = ref([]),
  calendar = ref({ resources: [] }),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("time"),
  mine = ref(false),
  chosenResource = ref(""),
  day = ref(""),
  detail = ref(null),
  dialog = ref(null),
  blocks = ref(null),
  stats = ref({}),
  work = ref({ mine: [], reviews: [] });
const tr = (zh, en) => (lang.value === "zh" ? zh : en);
const pair = (v) => v?.[lang.value === "zh" ? 0 : 1] || "";
const can = (p) => me.value?.permissions.includes(p);
const adminPages = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const pageLabel = computed(() => {
  const m = me.value?.menus.find((x) => x.code === page.value);
  return m ? (lang.value === "zh" ? m.name : m.nameEn) : "";
});
const selected = computed(() => detail.value?.booking);
const selectable = computed(() => resources.value.filter((r) => r.enabled));
const currentResources = computed(() =>
  resources.value.filter(
    (r) =>
      !search.value ||
      [r.name, r.code, r.location]
        .join(" ")
        .toLowerCase()
        .includes(search.value.toLowerCase()),
  ),
);
const adminRows = computed(() =>
  rows.value.filter((r) =>
    JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
  ),
);
const adminVisible = computed(() =>
  adminRows.value.slice(offset.value * 20, offset.value * 20 + 20),
);
const icons = {
  calendar: CalendarDays,
  bookings: ClipboardList,
  workbench: CheckCircle2,
  resources: Building2,
  dashboard: LayoutDashboard,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
  departments: Building2,
  menus: ClipboardList,
  permissions: ShieldCheck,
  dictionaries: ClipboardList,
  settings: Settings,
};
const resourceName = (id) =>
  resources.value.find((r) => r.id === id)?.name || "#" + id;
const departmentName = (id) =>
  options.value.departments.find((r) => r.id === id)?.name || "#" + id;
const field = (key, type = "text", extra = {}) => ({ key, type, ...extra });
const departmentChoices = () =>
  options.value.departments
    .filter((d) => me.value.scope === "ALL" || d.id === me.value.departmentId)
    .map((d) => ({ value: d.id, label: d.name }));
const roleChoices = ref([]),
  permissionChoices = ref([]);
const adminFields = {
  users: () => [
    field("username"),
    field("displayName"),
    field("password", "password", { required: !dialog.value?.id }),
    field("roleId", "select", { options: roleChoices.value }),
    field("departmentId", "select", { options: departmentChoices() }),
    field("enabled", "checkbox"),
  ],
  roles: () => [
    field("name"),
    field("scope", "select", {
      options: [
        { value: "ALL", label: tr("全部", "All") },
        {
          value: "DEPARTMENT",
          label: tr(
            "本部门与管理的部门资源",
            "Department and managed resources",
          ),
        },
        {
          value: "ASSIGNED",
          label: tr("本人／指定审批", "Own / assigned review"),
        },
      ],
    }),
    field("permissions", "permissions", { options: permissionChoices.value }),
  ],
  departments: () => [field("name")],
  menus: () => [
    field("code", "readonly"),
    field("name"),
    field("nameEn"),
    field("permissionCode", "select", { options: permissionChoices.value }),
    field("position", "number"),
    field("enabled", "checkbox"),
  ],
  permissions: () => [field("code", "readonly"), field("name")],
  dictionaries: () => [
    field("type"),
    field("code"),
    field("name"),
    field("nameEn"),
  ],
  settings: () => [field("code", "readonly"), field("value")],
};
/** 同源错误映射为业务反馈，失效会话返回登录页。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function fail(e) {
  error.value =
    pair(errors[e.message]) ||
    tr(
      "操作未完成，请检查输入后重试",
      "Operation failed. Check inputs and retry.",
    );
  if (e.message === "UNAUTHENTICATED") {
    me.value = null;
    detail.value = null;
    dialog.value = null;
  }
}
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    const [o, r] = await Promise.all([
      api("/options"),
      can("booking.read") || can("resource.manage")
        ? api("/resources")
        : Promise.resolve([]),
    ]);
    options.value = o;
    resources.value = r;
    if (!day.value) day.value = localInput(o.serverNow).slice(0, 10);
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "calendar";
    if (detail.value) {
      detail.value = await api("/bookings/" + detail.value.booking.id);
      return;
    }
    if (page.value === "calendar")
      calendar.value = await api("/calendar?day=" + day.value);
    else if (page.value === "bookings") {
      const params = new URLSearchParams({
        search: search.value,
        status: status.value,
        sort: sort.value,
        page: offset.value,
        size: 20,
        mine: mine.value,
      });
      if (chosenResource.value) params.set("resourceId", chosenResource.value);
      const v = await api("/bookings?" + params);
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else if (page.value === "audit") rows.value = await api("/audit");
    else if (adminPages.includes(page.value))
      rows.value = await api("/admin/" + page.value);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  try {
    await api("/auth/logout", "POST");
  } catch (e) {
    fail(e);
  } finally {
    resetCsrf();
    me.value = null;
    detail.value = null;
    dialog.value = null;
  }
}
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("meetflow-language", lang.value);
}
async function navigate(p) {
  page.value = p;
  detail.value = null;
  blocks.value = null;
  search.value = "";
  status.value = "";
  offset.value = 0;
  success.value = "";
  await load();
}
async function openDetail(id) {
  error.value = "";
  try {
    detail.value = await api("/bookings/" + id);
    blocks.value = null;
  } catch (e) {
    fail(e);
  }
}
async function openBooking(record = null, rid = null) {
  error.value = "";
  const resource =
    resources.value.find((r) => r.id === (record?.resourceId || rid)) ||
    selectable.value[0];
  if (!resource) {
    error.value = tr(
      "请先由资源管理员建立启用资源",
      "Ask a resource manager to create an enabled resource.",
    );
    return;
  }
  const from = nextSlot(options.value.serverNow, resource.minNoticeMinutes);
  dialog.value = {
    kind: "booking",
    id: record?.id,
    title: tr(
      record ? "编辑预约草稿" : "新建预约",
      record ? "Edit draft" : "New booking",
    ),
    form: record
      ? {
          ...record,
          startsAt: localInput(record.startsAt),
          endsAt: localInput(record.endsAt),
          requestKey: crypto.randomUUID(),
        }
      : {
          resourceId: resource.id,
          title: "",
          purpose: "",
          attendees: 1,
          startsAt: localInput(from),
          endsAt: localInput(
            new Date(new Date(from).getTime() + 3600000).toISOString(),
          ),
          requestKey: crypto.randomUUID(),
        },
    fields: [
      field("resourceId", "select", {
        options: selectable.value.map((r) => ({
          value: r.id,
          label: r.name + " · " + r.location,
        })),
        disabled: !!record,
      }),
      field("title"),
      field("purpose", "textarea"),
      field("attendees", "number", { min: 1, max: 10000 }),
      field("startsAt", "datetime-local"),
      field("endsAt", "datetime-local"),
    ],
  };
}
function openCommand(action) {
  dialog.value = {
    kind: "command",
    action,
    id: selected.value.id,
    title: pair(commands[action]),
    form: {
      version: selected.value.version,
      requestKey: crypto.randomUUID(),
      note: "",
    },
    fields: [
      field("note", "textarea", {
        required: ["reject", "cancel", "admin-cancel", "finish"].includes(
          action,
        ),
      }),
    ],
  };
  error.value = "";
}
function openResource(record = null) {
  const form = record
    ? { ...record }
    : {
        code: "",
        name: "",
        location: "",
        category: "ROOM",
        departmentId: me.value.departmentId,
        stewardId:
          options.value.stewards.find(
            (s) => s.departmentId === me.value.departmentId,
          )?.id || options.value.stewards[0]?.id,
        capacity: 8,
        openMinute: 480,
        closeMinute: 1200,
        bufferMinutes: 15,
        maxDurationMinutes: 240,
        minNoticeMinutes: 0,
        checkInGraceMinutes: 10,
        weekdays: "1,2,3,4,5",
        approvalRequired: true,
        shared: true,
        enabled: true,
      };
  dialog.value = {
    kind: "resource",
    id: record?.id,
    title: tr(
      record ? "编辑资源" : "建立资源",
      record ? "Edit resource" : "New resource",
    ),
    form,
    fields: [
      field("code"),
      field("name"),
      field("location"),
      field("category", "select", {
        options: options.value.dictionaries
          .filter((d) => d.type === "resource")
          .map((d) => ({
            value: d.code,
            label: lang.value === "zh" ? d.name : d.nameEn,
          })),
      }),
      field("departmentId", "select", { options: departmentChoices() }),
      field("stewardId", "select", {
        options: options.value.stewards.map((s) => ({
          value: s.id,
          label: s.displayName + " · " + departmentName(s.departmentId),
        })),
      }),
      field("capacity", "number"),
      field("openMinute", "number", { min: 0, max: 1425, step: 15 }),
      field("closeMinute", "number", { min: 15, max: 1440, step: 15 }),
      field("bufferMinutes", "number", { min: 0, max: 120 }),
      field("maxDurationMinutes", "number", { min: 15, max: 480 }),
      field("minNoticeMinutes", "number", { min: 0, max: 1440 }),
      field("checkInGraceMinutes", "number", { min: 5, max: 60 }),
      field("weekdays"),
      field("approvalRequired", "checkbox"),
      field("shared", "checkbox"),
      field("enabled", "checkbox"),
    ],
  };
  error.value = "";
}
async function openBlocks(r) {
  error.value = "";
  try {
    blocks.value = {
      resource: r,
      items: await api("/resources/" + r.id + "/blocks"),
    };
    detail.value = null;
  } catch (e) {
    fail(e);
  }
}
function openBlock() {
  const start = nextSlot(options.value.serverNow);
  dialog.value = {
    kind: "block",
    id: blocks.value.resource.id,
    title: tr("新增维护时段", "New maintenance window"),
    form: {
      startsAt: localInput(start),
      endsAt: localInput(
        new Date(new Date(start).getTime() + 3600000).toISOString(),
      ),
      reason: "",
    },
    fields: [
      field("startsAt", "datetime-local"),
      field("endsAt", "datetime-local"),
      field("reason", "textarea"),
    ],
  };
}
async function cancelBlock(b) {
  saving.value = true;
  error.value = "";
  try {
    await api(
      "/resources/" + b.resourceId + "/blocks/" + b.id + "/cancel",
      "POST",
      { version: b.version },
    );
    await openBlocks(blocks.value.resource);
    success.value = tr(
      "维护时段已取消，历史保留",
      "Maintenance cancelled; history retained.",
    );
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function openAdmin(row = null) {
  error.value = "";
  try {
    if (page.value === "users")
      roleChoices.value = (await api("/admin/roles")).map((r) => ({
        value: r.id,
        label: r.name,
      }));
    if (["roles", "menus"].includes(page.value))
      permissionChoices.value = (await api("/admin/permissions")).map((p) => ({
        value: p.code,
        label: p.name,
      }));
    dialog.value = {
      kind: "admin",
      id: row?.id,
      title: tr(
        row ? "编辑资料" : "新增资料",
        row ? "Edit record" : "New record",
      ),
      form: row
        ? {
            ...row,
            password: "",
            permissions: row.permissions ? [...row.permissions] : [],
          }
        : {
            name: "",
            nameEn: "",
            username: "",
            displayName: "",
            password: "",
            enabled: true,
            roleId: roleChoices.value[0]?.value,
            departmentId: me.value.departmentId,
            scope: "ASSIGNED",
            permissions: [],
            type: "resource",
            code: "",
          },
    };
    dialog.value.fields = adminFields[page.value]();
  } catch (e) {
    fail(e);
  }
}
function openPassword() {
  dialog.value = {
    kind: "password",
    title: tr("修改密码", "Change password"),
    form: { oldPassword: "", newPassword: "" },
    fields: [
      field("oldPassword", "password"),
      field("newPassword", "password"),
    ],
  };
}
/** 提交真实接口，转换固定时区与数字；失败保留表单及请求键供安全重试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function saveDialog() {
  saving.value = true;
  error.value = "";
  try {
    const d = dialog.value;
    const form = { ...d.form };
    for (const f of d.fields) {
      if (f.type === "number") form[f.key] = Number(form[f.key]);
      if (f.type === "datetime-local") form[f.key] = instant(form[f.key]);
    }
    let result;
    if (d.kind === "booking")
      result = await api(
        "/bookings" + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        form,
      );
    else if (d.kind === "command")
      result = await api(
        "/bookings/" + d.id + "/commands/" + d.action,
        "POST",
        form,
      );
    else if (d.kind === "resource")
      await api(
        "/resources" + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        form,
      );
    else if (d.kind === "block")
      await api("/resources/" + d.id + "/blocks", "POST", form);
    else if (d.kind === "admin")
      await api(
        "/admin/" + page.value + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        form,
      );
    else if (d.kind === "password") {
      await api("/auth/password", "POST", form);
      me.value = null;
      resetCsrf();
    } else if (d.kind === "delete") {
      await api(d.path, "DELETE");
      detail.value = null;
    }
    dialog.value = null;
    success.value = tr("已保存", "Saved");
    if (result) await openDetail(result.id);
    if (me.value) await load();
    if (blocks.value) await openBlocks(blocks.value.resource);
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
function openDelete(path) {
  dialog.value = {
    kind: "delete",
    path,
    title: tr("删除未使用资料", "Delete unused record"),
    form: {},
    fields: [],
  };
  error.value = "";
}
function shiftDay(n) {
  const d = new Date(day.value + "T12:00:00+08:00");
  d.setTime(d.getTime() + n * 86400000);
  day.value = localInput(d.toISOString()).slice(0, 10);
  load();
}
async function exportBooking() {
  error.value = "";
  try {
    const body = await api("/bookings/" + selected.value.id + "/report.json");
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(body, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "booking-" + selected.value.id + ".json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fail(e);
  }
}
function eventLabel(e) {
  const management = e.action.match(/^ADMIN_(UPDATE|DELETE)_(.+)$/);
  if (management) {
    const names = {
      users: ["账号", "accounts"],
      roles: ["角色", "roles"],
      departments: ["部门", "departments"],
      menus: ["导航", "navigation"],
      permissions: ["权限目录", "permissions"],
      dictionaries: ["资源类型", "resource types"],
      settings: ["系统参数", "settings"],
    };
    return (
      tr(
        management[1] === "DELETE" ? "删除" : "管理",
        management[1] === "DELETE" ? "Delete " : "Manage ",
      ) + pair(names[management[2]])
    );
  }
  return (
    pair(commands[e.action.toLowerCase()]) ||
    pair(
      {
        LOGIN: ["登录", "Sign in"],
        PASSWORD_CHANGE: ["修改密码", "Password change"],
        RESOURCE_SAVE: ["管理资源规则", "Manage resource rules"],
        RESOURCE_DELETE: ["删除资源", "Delete resource"],
        BLOCK_CREATE: ["建立维护时段", "Create maintenance"],
        BLOCK_CANCEL: ["取消维护", "Cancel maintenance"],
        DRAFT_DELETE: ["删除未提交草稿", "Delete unsubmitted draft"],
        CREATE: ["创建草稿", "Create draft"],
        SAVE: ["修改草稿", "Save draft"],
        EXPIRED: ["审批超时释放", "Approval expired"],
        NO_SHOW: ["未签到释放", "No-show release"],
        AUTO_FINISH: ["到时自动结束", "Automatic finish"],
      }[e.action],
    ) ||
    tr("操作记录", "Operation")
  );
}
const genericColumns = computed(() => {
  if (page.value === "users")
    return ["username", "displayName", "roleId", "departmentId", "enabled"];
  if (page.value === "roles") return ["name", "scope", "permissions"];
  if (page.value === "menus")
    return ["code", "name", "permissionCode", "position", "enabled"];
  if (page.value === "permissions") return ["code", "name"];
  if (page.value === "settings") return ["code", "value"];
  if (page.value === "dictionaries") return ["type", "code", "name", "nameEn"];
  return ["name"];
});
function cell(row, key) {
  if (key === "departmentId") return departmentName(row[key]);
  if (key === "enabled")
    return tr(row[key] ? "启用" : "停用", row[key] ? "Enabled" : "Disabled");
  if (key === "permissions")
    return row[key]
      .map(
        (p) =>
          permissionChoices.value.find((v) => v.value === p)?.label ||
          {
            "booking.read": "查看预约",
            "booking.write": "预约填报",
            "booking.approve": "预约审批",
            "resource.manage": "资源管理",
            dashboard: "使用统计",
            export: "数据导出",
            audit: "操作审计",
            admin: "系统管理",
          }[p] ||
          p,
      )
      .join(" · ");
  if (key === "scope")
    return pair(
      {
        ALL: ["全部", "All"],
        DEPARTMENT: ["本部门与部门资源", "Department / resources"],
        ASSIGNED: ["本人及指定审批", "Own / assigned"],
      }[row[key]],
    );
  return row[key];
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <main v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span class="brand-sub"
        >MEETFLOW</span
      >
      <h1>
        {{ tr("会议室与", "Meeting rooms") }}<br />{{
          tr("共享资源预约", "& shared resources")
        }}
      </h1>
      <div class="brand-line"></div>
      <p class="small">
        {{
          tr(
            "公开源码学习版 · 非商业源码许可",
            "Learning source edition · Non-commercial license",
          )
        }}
      </p>
    </section>
    <section class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">{{ tr("账号登录", "ACCOUNT SIGN IN") }}</span>
        <h2>{{ tr("欢迎回来", "Welcome back") }}</h2>
        <label
          >{{ tr("登录账号", "Username")
          }}<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ tr("密码", "Password")
          }}<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <button class="primary wide" :disabled="saving">
          {{
            tr(saving ? "登录中…" : "登录", saving ? "Signing in…" : "Sign in")
          }}
        </button>
      </form>
      <footer>
        <button @click="language">
          {{ lang === "zh" ? "English" : "中文" }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ tr("知华科技官网", "ZhuaTech website") }}
          <ArrowUpRight :size="13"
        /></a>
        <p>上海如静知华信息科技有限公司 · 微信 zhuatech / zhuatech2</p>
      </footer>
    </section>
  </main>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>MeetFlow</strong
          ><small>{{ tr("会议室与共享资源", "Rooms & resources") }}</small>
        </div>
      </div>
      <nav aria-label="主导航">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || ClipboardList" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ tr("知华科技 · 商业咨询", "ZhuaTech · Business") }}
          <ArrowUpRight :size="13" /></a
        ><small>zhuatech / zhuatech2</small>
      </div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumb">MeetFlow <span>/</span> {{ pageLabel }}</div>
        <div class="account">
          <button @click="language">{{ lang === "zh" ? "EN" : "中文" }}</button
          ><button @click="openPassword">{{ me.displayName }}</button
          ><button :aria-label="tr('退出', 'Sign out')" @click="signOut">
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <div class="content">
        <div class="page-heading">
          <div>
            <span class="eyebrow">{{
              detail
                ? "BOOKING DETAIL"
                : page === "calendar"
                  ? "RESOURCE SCHEDULE"
                  : page === "workbench"
                    ? "MY WORK"
                    : "MEETFLOW"
            }}</span>
            <h1>
              {{
                detail
                  ? selected.title
                  : blocks
                    ? blocks.resource.name
                    : pageLabel
              }}
            </h1>
            <p v-if="page === 'calendar' && !detail">
              {{
                tr(
                  "上海时区 · 占用段包含预约与周转时间",
                  "Shanghai time · Occupancy includes turnaround",
                )
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button :disabled="loading" @click="load">
              <RefreshCw :size="15" />{{ tr("刷新", "Refresh") }}</button
            ><button
              v-if="
                can('booking.write') &&
                !detail &&
                ['calendar', 'bookings', 'workbench'].includes(page)
              "
              class="primary"
              @click="openBooking()"
            >
              <Plus :size="16" />{{ tr("新建预约", "New booking") }}</button
            ><button
              v-if="page === 'resources' && !blocks && !detail"
              class="primary"
              @click="openResource()"
            >
              <Plus :size="16" />{{ tr("建立资源", "New resource") }}</button
            ><button
              v-if="
                adminPages.includes(page) &&
                !['permissions', 'menus', 'settings'].includes(page)
              "
              class="primary"
              @click="openAdmin()"
            >
              <Plus :size="16" />{{ tr("新增", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error && !dialog" role="alert" class="error">{{ error }}</p>
        <p v-if="success && !dialog" role="status" class="success">
          {{ success }}
        </p>
        <p v-if="loading" class="muted">{{ tr("正在读取…", "Loading…") }}</p>
        <section v-if="detail" class="detail">
          <button
            class="back"
            @click="
              detail = null;
              load();
            "
          >
            <ChevronLeft :size="15" />{{ tr("返回列表", "Back to list") }}
          </button>
          <div class="detail-summary">
            <div>
              <span :class="['badge', selected.status]">{{
                pair(states[selected.status])
              }}</span>
              <h2>{{ detail.resource.name }}</h2>
              <p>
                {{ detail.resource.location }} · {{ selected.attendees }}
                {{ tr("人", "people") }}
              </p>
            </div>
            <div>
              <strong>{{ date(selected.startsAt) }}</strong>
              <p>{{ tr("至", "to") }} {{ date(selected.endsAt) }}</p>
              <small
                >{{ tr("周转缓冲", "Turnaround") }} {{ selected.bufferMinutes }}
                {{ tr("分钟", "min") }}</small
              >
            </div>
          </div>
          <div class="action-row">
            <button
              v-if="editable(selected, me)"
              @click="openBooking(selected)"
            >
              {{ tr("编辑草稿", "Edit draft") }}</button
            ><button
              v-for="action in actions(selected, me, detail.resource)"
              :key="action"
              :class="
                ['approve', 'submit', 'check-in'].includes(action)
                  ? 'primary'
                  : ''
              "
              @click="openCommand(action)"
            >
              {{ pair(commands[action]) }}</button
            ><button
              v-if="editable(selected, me) && !selected.submitted"
              @click="
                openDelete(
                  '/bookings/' + selected.id + '?version=' + selected.version,
                )
              "
            >
              {{ tr("删除草稿", "Delete draft") }}</button
            ><button v-if="can('export')" @click="exportBooking">
              {{ tr("导出记录", "Export record") }}
            </button>
          </div>
          <div class="detail-grid">
            <section class="panel">
              <h3>{{ tr("用途与使用记录", "Purpose & use") }}</h3>
              <p class="preserve">{{ selected.purpose }}</p>
              <dl>
                <dt>{{ tr("申请部门", "Requesting department") }}</dt>
                <dd>{{ departmentName(selected.departmentId) }}</dd>
                <dt>{{ tr("签到时间", "Check-in time") }}</dt>
                <dd>{{ date(selected.checkedInAt) }}</dd>
                <dt>{{ tr("结束时间", "Completion time") }}</dt>
                <dd>{{ date(selected.completedAt) }}</dd>
                <dt>{{ tr("当前周转结束", "Turnaround ends") }}</dt>
                <dd>{{ date(selected.occupiedUntil) }}</dd>
              </dl>
            </section>
            <section class="panel">
              <h3>{{ tr("预约历史", "Booking history") }}</h3>
              <ol class="event-list">
                <li v-for="e in detail.events" :key="e.id">
                  <div class="event-top">
                    <strong>{{ eventLabel(e) }}</strong
                    ><time>{{ date(e.createdAt) }}</time>
                  </div>
                  <small>{{
                    e.actor === "SYSTEM" ? tr("系统", "System") : e.actor
                  }}</small>
                  <p v-if="e.note">{{ e.note }}</p>
                  <details>
                    <summary>{{ tr("查看当时快照", "View snapshot") }}</summary>
                    <pre>{{
                      JSON.stringify(JSON.parse(e.snapshot), null, 2)
                    }}</pre>
                  </details>
                </li>
              </ol>
            </section>
          </div>
        </section>
        <section v-else-if="blocks">
          <button class="back" @click="blocks = null">
            <ChevronLeft :size="15" />{{ tr("返回资源", "Back to resources") }}
          </button>
          <div class="toolbar">
            <h2>{{ tr("维护与停用时段", "Maintenance windows") }}</h2>
            <button class="primary" @click="openBlock">
              <Plus :size="15" />{{ tr("新增维护", "New maintenance") }}
            </button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("开始时间", "Start") }}</th>
                  <th>{{ tr("结束时间", "End") }}</th>
                  <th>{{ tr("原因", "Reason") }}</th>
                  <th>{{ tr("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in blocks.items" :key="b.id">
                  <td>{{ date(b.startsAt) }}</td>
                  <td>{{ date(b.endsAt) }}</td>
                  <td>{{ b.reason }}</td>
                  <td>
                    {{
                      tr(
                        b.status === "ACTIVE" ? "有效" : "已取消",
                        b.status === "ACTIVE" ? "Active" : "Cancelled",
                      )
                    }}
                  </td>
                  <td>
                    <button
                      v-if="b.status === 'ACTIVE'"
                      :disabled="saving"
                      @click="cancelBlock(b)"
                    >
                      {{ tr("取消维护", "Cancel maintenance") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!blocks.items.length" class="empty">
              {{ tr("暂无维护时段", "No maintenance windows") }}
            </div>
          </div>
        </section>
        <section v-else-if="page === 'calendar'">
          <div class="calendar-toolbar">
            <div class="day-picker">
              <button
                :aria-label="tr('前一天', 'Previous day')"
                @click="shiftDay(-1)"
              >
                <ChevronLeft :size="16" /></button
              ><input
                v-model="day"
                type="date"
                :aria-label="tr('预约日期', 'Booking date')"
                @change="load"
              /><button
                :aria-label="tr('后一天', 'Next day')"
                @click="shiftDay(1)"
              >
                <ChevronRight :size="16" />
              </button>
            </div>
            <div class="legend">
              <span class="dot"></span>{{ tr("已占用", "Occupied")
              }}<span class="dot maintenance"></span
              >{{ tr("维护停用", "Maintenance") }}
            </div>
          </div>
          <div class="schedule">
            <div class="schedule-header">
              <span>{{ tr("资源", "RESOURCE") }}</span>
              <div class="hours">
                <span v-for="h in [0, 4, 8, 12, 16, 20, 24]" :key="h"
                  >{{ String(h).padStart(2, "0") }}:00</span
                >
              </div>
            </div>
            <div
              v-for="row in calendar.resources"
              :key="row.resource.id"
              class="schedule-row"
            >
              <div class="resource-caption">
                <strong>{{ row.resource.name }}</strong
                ><small
                  >{{ row.resource.location }} · {{ row.resource.capacity }}
                  {{ tr("人", "people") }}</small
                >
                <div>
                  <span
                    >{{ clockTime(row.resource.openMinute) }}–{{
                      clockTime(row.resource.closeMinute)
                    }}</span
                  ><button
                    v-if="can('booking.write') && row.resource.enabled"
                    :aria-label="tr('预约', 'Book') + row.resource.name"
                    @click="openBooking(null, row.resource.id)"
                  >
                    <Plus :size="15" />
                  </button>
                </div>
                <small v-if="!row.resource.enabled" class="muted">{{
                  tr("已停用", "Disabled")
                }}</small>
              </div>
              <div class="track">
                <div
                  class="opening"
                  :style="{
                    left: (row.resource.openMinute / 1440) * 100 + '%',
                    width:
                      ((row.resource.closeMinute - row.resource.openMinute) /
                        1440) *
                        100 +
                      '%',
                  }"
                ></div>
                <button
                  v-for="(slot, i) in row.slots"
                  :key="i"
                  :class="[
                    'slot',
                    slot.kind === 'MAINTENANCE' ? 'maintenance' : '',
                  ]"
                  :style="slotStyle(slot, day)"
                  :title="
                    (slot.label === 'BUSY'
                      ? tr('已占用', 'Busy')
                      : slot.label === 'MAINTENANCE'
                        ? tr('维护停用', 'Maintenance')
                        : slot.label) +
                    ' ' +
                    date(slot.startsAt) +
                    ' – ' +
                    date(slot.endsAt)
                  "
                  :disabled="!slot.bookingId"
                  @click="openDetail(slot.bookingId)"
                >
                  <span>{{
                    slot.label === "BUSY"
                      ? tr("已占用", "Busy")
                      : slot.label === "MAINTENANCE"
                        ? tr("维护", "Maintenance")
                        : slot.label
                  }}</span>
                </button>
              </div>
            </div>
            <div v-if="!calendar.resources.length" class="empty">
              <Building2 :size="30" />
              <p>{{ tr("暂无可见资源", "No visible resources") }}</p>
              <button
                v-if="can('resource.manage')"
                @click="navigate('resources')"
              >
                {{ tr("建立资源", "Create a resource") }}
              </button>
            </div>
          </div>
          <div class="daily-list">
            <h3>{{ tr("当日占用明细", "Daily occupancy") }}</h3>
            <template v-for="row in calendar.resources" :key="row.resource.id"
              ><div v-for="(slot, i) in row.slots" :key="i" class="occupancy">
                <strong>{{ row.resource.name }}</strong
                ><span>{{ date(slot.startsAt) }} — {{ date(slot.endsAt) }}</span
                ><span>{{
                  slot.label === "BUSY"
                    ? tr("已占用", "Busy")
                    : slot.label === "MAINTENANCE"
                      ? tr("维护停用", "Maintenance")
                      : slot.label
                }}</span
                ><button
                  v-if="slot.bookingId"
                  @click="openDetail(slot.bookingId)"
                >
                  {{ tr("详情", "Details") }}
                </button>
              </div></template
            >
            <p
              v-if="calendar.resources.every((r) => !r.slots.length)"
              class="muted"
            >
              {{ tr("此日期没有有效占用", "No occupancy on this date") }}
            </p>
          </div>
        </section>
        <section v-else-if="page === 'bookings'">
          <form
            class="filters"
            @submit.prevent="
              offset = 0;
              load();
            "
          >
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="
                  tr('搜索预约事由', 'Search booking title')
                " /></label
            ><select
              v-model="status"
              :aria-label="tr('状态筛选', 'Status filter')"
              @change="
                offset = 0;
                load();
              "
            >
              <option value="">{{ tr("全部状态", "All states") }}</option>
              <option v-for="(value, key) in states" :key="key" :value="key">
                {{ pair(value) }}
              </option></select
            ><select
              v-model="chosenResource"
              :aria-label="tr('资源筛选', 'Resource filter')"
              @change="
                offset = 0;
                load();
              "
            >
              <option value="">{{ tr("全部资源", "All resources") }}</option>
              <option v-for="r in resources" :key="r.id" :value="r.id">
                {{ r.name }}
              </option></select
            ><select
              v-model="sort"
              :aria-label="tr('排序', 'Sort')"
              @change="load"
            >
              <option value="time">{{ tr("开始时间", "Start time") }}</option>
              <option value="newest">{{ tr("最新创建", "Newest") }}</option>
              <option value="title">
                {{ tr("事由标题", "Title") }}
              </option></select
            ><label class="check-inline"
              ><input
                v-model="mine"
                type="checkbox"
                @change="
                  offset = 0;
                  load();
                "
              />{{ tr("仅我的预约", "Only mine") }}</label
            ><button>{{ tr("查询", "Search") }}</button>
          </form>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("预约事由", "Title") }}</th>
                  <th>{{ tr("资源", "Resource") }}</th>
                  <th>{{ tr("时间", "Time") }}</th>
                  <th>{{ tr("人数", "Attendees") }}</th>
                  <th>{{ tr("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in rows" :key="b.id">
                  <td>
                    <strong>{{ b.title }}</strong
                    ><small>{{ departmentName(b.departmentId) }}</small>
                  </td>
                  <td>{{ resourceName(b.resourceId) }}</td>
                  <td>
                    {{ date(b.startsAt) }}<small>{{ date(b.endsAt) }}</small>
                  </td>
                  <td>{{ b.attendees }}</td>
                  <td>
                    <span :class="['badge', b.status]">{{
                      pair(states[b.status])
                    }}</span>
                  </td>
                  <td>
                    <button @click="openDetail(b.id)">
                      {{ tr("查看", "View") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无匹配预约", "No matching bookings") }}
            </p>
          </div>
          <div class="pagination">
            <span
              >{{ tr("共", "Total") }} {{ total }}
              {{ tr("条", "records") }}</span
            ><button
              :disabled="offset === 0"
              @click="
                offset--;
                load();
              "
            >
              <ChevronLeft :size="15" /></button
            ><span>{{ offset + 1 }}</span
            ><button
              :disabled="(offset + 1) * 20 >= total"
              @click="
                offset++;
                load();
              "
            >
              <ChevronRight :size="15" />
            </button>
          </div>
        </section>
        <section v-else-if="page === 'workbench'" class="work-grid">
          <div v-for="kind in ['reviews', 'mine']" :key="kind" class="panel">
            <h2>
              {{
                kind === "reviews"
                  ? tr("待我审批", "Awaiting my review")
                  : tr("我的活动预约", "My active bookings")
              }}
              <small>{{ work[kind].length }}</small>
            </h2>
            <div v-for="b in work[kind]" :key="b.id" class="work-item">
              <div>
                <strong>{{ b.title }}</strong>
                <p>{{ resourceName(b.resourceId) }} · {{ date(b.startsAt) }}</p>
                <span :class="['badge', b.status]">{{
                  pair(states[b.status])
                }}</span>
              </div>
              <button @click="openDetail(b.id)">
                {{ tr("处理", "Open") }}
              </button>
            </div>
            <p v-if="!work[kind].length" class="empty">
              {{ tr("暂无待处理事项", "Nothing to handle") }}
            </p>
          </div>
        </section>
        <section v-else-if="page === 'resources'">
          <div class="filters">
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="
                  tr(
                    '搜索资源、代码或位置',
                    'Search resource, code or location',
                  )
                "
            /></label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("资源", "Resource") }}</th>
                  <th>{{ tr("位置与部门", "Location & department") }}</th>
                  <th>{{ tr("容量", "Capacity") }}</th>
                  <th>{{ tr("开放规则", "Opening rules") }}</th>
                  <th>{{ tr("预约方式", "Booking mode") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in currentResources" :key="r.id">
                  <td>
                    <strong>{{ r.name }}</strong
                    ><small>{{ r.code }}</small>
                  </td>
                  <td>
                    {{ r.location
                    }}<small>{{ departmentName(r.departmentId) }}</small>
                  </td>
                  <td>{{ r.capacity }}</td>
                  <td>
                    {{ clockTime(r.openMinute) }}–{{ clockTime(r.closeMinute)
                    }}<small
                      >{{ tr("星期", "Weekdays") }} {{ r.weekdays }} ·
                      {{ tr("缓冲", "Buffer") }}
                      {{ r.bufferMinutes }} min</small
                    >
                  </td>
                  <td>
                    {{
                      tr(
                        r.approvalRequired ? "负责人审批" : "自动确认",
                        r.approvalRequired ? "Steward review" : "Auto confirm",
                      )
                    }}<small
                      >{{
                        tr(
                          r.enabled ? "启用" : "停用",
                          r.enabled ? "Enabled" : "Disabled",
                        )
                      }}
                      ·
                      {{
                        tr(
                          r.shared ? "共享" : "部门私有",
                          r.shared ? "Shared" : "Department private",
                        )
                      }}</small
                    >
                  </td>
                  <td
                    v-if="
                      me.scope === 'ALL' || r.departmentId === me.departmentId
                    "
                  >
                    <button @click="openResource(r)">
                      {{ tr("编辑", "Edit") }}</button
                    ><button @click="openBlocks(r)">
                      {{ tr("维护时段", "Maintenance") }}</button
                    ><button
                      @click="
                        openDelete(
                          '/resources/' + r.id + '?version=' + r.version,
                        )
                      "
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                  <td v-else>—</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!currentResources.length" class="empty">
              {{
                tr(
                  "暂无资源，请建立资源及预约规则",
                  "Create resources and booking rules to begin",
                )
              }}
            </p>
          </div>
        </section>
        <section v-else-if="page === 'dashboard'">
          <div class="metrics">
            <article
              v-for="[key, zh, en] in [
                ['total', '授权预约', 'Visible bookings'],
                ['pending', '待审批', 'Pending review'],
                ['inUse', '使用中', 'In use'],
                ['noShow', '未签到', 'No show'],
              ]"
              :key="key"
            >
              <span>{{ tr(zh, en) }}</span
              ><strong>{{ stats[key] || 0 }}</strong>
            </article>
          </div>
          <div class="usage-summary">
            <div>
              <Clock3 :size="20" /><span>{{
                tr("计划预约时长", "Planned booking time")
              }}</span
              ><strong
                >{{ stats.plannedMinutes || 0 }} <small>min</small></strong
              >
            </div>
            <div>
              <CheckCircle2 :size="20" /><span>{{
                tr("已签到使用时长", "Checked-in use time")
              }}</span
              ><strong>{{ stats.usedMinutes || 0 }} <small>min</small></strong>
            </div>
          </div>
          <div class="panel">
            <h2>{{ tr("按资源汇总", "By resource") }}</h2>
            <div
              v-for="r in stats.resources || []"
              :key="r.name"
              class="stat-row"
            >
              <strong>{{ r.name }}</strong>
              <div class="bar">
                <span
                  :style="{
                    width:
                      Math.min(
                        100,
                        (r.bookings / Math.max(1, stats.total)) * 100,
                      ) + '%',
                  }"
                ></span>
              </div>
              <span
                >{{ r.bookings }} {{ tr("预约", "bookings") }} /
                {{ r.completed }} {{ tr("结束", "completed") }}</span
              >
            </div>
            <p v-if="!stats.resources?.length" class="empty">
              {{ tr("暂无统计数据", "No data") }}
            </p>
          </div>
        </section>
        <section v-else-if="page === 'audit'">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ tr("时间", "Time") }}</th>
                  <th>{{ tr("操作人", "Actor") }}</th>
                  <th>{{ tr("动作", "Action") }}</th>
                  <th>{{ tr("记录编号", "Record") }}</th>
                  <th>{{ tr("部门", "Department") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in rows.slice().reverse()" :key="e.id">
                  <td>{{ date(e.createdAt) }}</td>
                  <td>{{ e.actor }}</td>
                  <td>{{ eventLabel(e) }}</td>
                  <td>{{ e.objectId }}</td>
                  <td>{{ departmentName(e.departmentId) }}</td>
                </tr>
              </tbody>
            </table>
            <p v-if="!rows.length" class="empty">
              {{ tr("暂无记录", "No records") }}
            </p>
          </div>
        </section>
        <section v-else-if="adminPages.includes(page)">
          <div class="filters">
            <label class="search"
              ><Search :size="16" /><input
                v-model="search"
                :placeholder="tr('搜索资料', 'Search records')"
                @input="offset = 0"
            /></label>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="key in genericColumns" :key="key">
                    {{ pair(labels[key]) }}
                  </th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in adminVisible" :key="row.id">
                  <td v-for="key in genericColumns" :key="key">
                    {{ cell(row, key) }}
                  </td>
                  <td>
                    <button @click="openAdmin(row)">
                      {{ tr("编辑", "Edit") }}</button
                    ><button
                      v-if="
                        !['menus', 'permissions', 'settings'].includes(page)
                      "
                      @click="openDelete('/admin/' + page + '/' + row.id)"
                    >
                      {{ tr("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span>{{ tr("共", "Total") }} {{ adminRows.length }}</span
            ><button :disabled="offset === 0" @click="offset--">
              <ChevronLeft :size="15" /></button
            ><span>{{ offset + 1 }}</span
            ><button
              :disabled="(offset + 1) * 20 >= adminRows.length"
              @click="offset++"
            >
              <ChevronRight :size="15" />
            </button>
          </div>
        </section>
        <footer class="app-footer">
          <span>© 2026 上海如静知华信息科技有限公司</span
          ><span
            >MeetFlow 0.1.0 ·
            {{
              tr(
                "公开源码学习版／非商业源码版，未经书面授权不得商用",
                "Learning source edition / non-commercial; written permission required for commercial use",
              )
            }}</span
          >
        </footer>
      </div>
    </section>
  </div>
  <div
    v-if="dialog"
    class="modal-backdrop"
    @click.self="!saving && (dialog = null)"
  >
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button
          :disabled="saving"
          :aria-label="tr('关闭', 'Close')"
          @click="dialog = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="saveDialog">
        <p v-if="dialog.kind === 'delete'" class="muted">
          {{
            tr(
              "仅可删除没有业务引用的资料或未提交草稿。此操作会删除该记录。",
              "Only unused records or never-submitted drafts may be deleted. This removes the record.",
            )
          }}
        </p>
        <div class="form-grid">
          <label
            v-for="f in dialog.fields"
            :key="f.key"
            :class="{
              full: f.type === 'textarea' || f.type === 'permissions',
              'check-inline': f.type === 'checkbox',
            }"
            ><span>{{ pair(labels[f.key]) }}</span
            ><template v-if="f.type === 'permissions'"
              ><div class="permission-options">
                <label
                  v-for="p in f.options"
                  :key="p.value"
                  class="check-inline"
                  ><input
                    v-model="dialog.form[f.key]"
                    type="checkbox"
                    :value="p.value"
                  />{{ p.label }}</label
                >
              </div></template
            ><textarea
              v-else-if="f.type === 'textarea'"
              v-model="dialog.form[f.key]"
              :required="f.required !== false"
              :maxlength="f.key === 'purpose' ? 2000 : 1000"
              rows="3"
            ></textarea
            ><select
              v-else-if="f.type === 'select'"
              v-model="dialog.form[f.key]"
              :disabled="f.disabled"
              :required="f.required !== false"
            >
              <option v-for="o in f.options" :key="o.value" :value="o.value">
                {{ o.label }}
              </option></select
            ><input
              v-else-if="f.type === 'checkbox'"
              v-model="dialog.form[f.key]"
              type="checkbox" />
            <div
              v-else-if="['openMinute', 'closeMinute'].includes(f.key)"
              class="time-entry"
            >
              <select
                v-model="dialog.form[f.key]"
                :aria-label="pair(labels[f.key])"
                required
              >
                <option
                  v-for="minute in Array.from(
                    { length: 97 },
                    (_, i) => i * 15,
                  ).filter((m) => m >= f.min && m <= f.max)"
                  :key="minute"
                  :value="minute"
                >
                  {{ clockTime(minute) }}
                </option>
              </select>
            </div>
            <input
              v-else
              v-model="dialog.form[f.key]"
              :type="f.type === 'readonly' ? 'text' : f.type"
              :readonly="f.type === 'readonly'"
              :required="f.required !== false && f.type !== 'readonly'"
              :min="f.min"
              :max="f.max"
              :step="f.type === 'datetime-local' ? 900 : f.step || 1"
              :maxlength="
                ['password', 'oldPassword', 'newPassword'].includes(f.key)
                  ? 128
                  : f.key === 'title'
                    ? 200
                    : f.key === 'weekdays'
                      ? 20
                      : 120
              "
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
          /></label>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" :disabled="saving" @click="dialog = null">
            {{ tr("取消", "Cancel") }}</button
          ><button class="primary" :disabled="saving">
            {{
              tr(saving ? "保存中…" : "确认", saving ? "Saving…" : "Confirm")
            }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
