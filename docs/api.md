# MeetFlow 接口说明

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

全部同源 `/api`，JSON。先GET `/auth/csrf` 获得header与token，Cookie保留在客户端，写请求携带该header。GET `/auth/me` 返回安全身份、权限、范围和可见导航；POST `/auth/login` 输入username/password，POST `/auth/logout` 注销，POST `/auth/password` 输入oldPassword/newPassword。凭据不返回。目录 `/options` 对登录成员提供基础部门、负责人安全资料、字典、参数与serverNow，不包含用户名或密码。

| 路径 | 方法与用途 | 权限 |
| --- | --- | --- |
| /resources | GET目录，POST资源 | booking.read或resource.manage；写resource.manage＋部门 |
| /resources/{id} | PUT规则；DELETE?version=当前版本 | resource.manage＋部门，历史引用保护 |
| /resources/{id}/blocks | GET维护；POST维护 | resource.manage＋部门 |
| /resources/{id}/blocks/{bid}/cancel | POST取消维护 | resource.manage＋部门 |
| /calendar?day=YYYY-MM-DD | GET单日资源及脱敏占用 | booking.read |
| /bookings | GET数据库分页；POST本人草稿 | booking.read；写booking.write |
| /bookings/{id} | GET详情，PUT草稿，DELETE?version=当前版本 | 阅读范围／写本人范围，已提交禁止删除 |
| /bookings/{id}/commands/{action} | POST状态动作 | 动作权限及范围 |
| /bookings/{id}/report.json | GET下载单据JSON | export＋阅读范围 |
| /workbench | GET本人活动及指定审批 | booking.read |
| /dashboard | GET授权统计 | dashboard＋booking.read |
| /audit | GET授权操作审计 | audit |
| /admin/{type} | GET/POST；/{id} PUT/DELETE | admin |

`type` 只允许users、roles、departments、menus、permissions、dictionaries、settings。代码固定和只读条目不能任意新增删除；目录最大10000条。角色配置应给审批／预约人员同时授予booking.read来查看详情；resource.manage角色须部门或全范围才能成为负责人。

草稿输入：version（更新时）、resourceId、title、purpose、attendees、startsAt、endsAt、requestKey。时间为ISO8601 UTC或带偏移值，起止按15分钟刻度。资源创建输入见操作手册字段。维护输入为startsAt、endsAt、reason；取消维护带version。

动作submit、approve、reject、cancel、admin-cancel、check-in、finish，输入version、requestKey、note。意见及原因按动作要求非空。requestKey为每次动作独立的UUID或符合服务要求的键，失败后同内容重试保留键，改变内容须新键。成功返回单据；详情返回booking/resource/events。预约读取列表的search/status/resourceId/page/size/sort/mine均由服务验证；page从0起、size为1–100，sort为time/newest/title。

常见错误：401 UNAUTHENTICATED、403 FORBIDDEN/OUT_OF_SCOPE/NOT_OWNER/NOT_APPROVER、400 INVALID_TIME/BOOKING_WINDOW/CAPACITY_EXCEEDED/CHECKIN_WINDOW、409 TIME_CONFLICT/MAINTENANCE_CONFLICT/STALE_VERSION/INVALID_STATE/HISTORY_PROTECTED/IDEMPOTENCY_CONFLICT。错误仅含code，页面负责业务文案。内部异常不返回详细栈。HTTP客户端需在旧会话失效后重新登录并取得新CSRF令牌。
