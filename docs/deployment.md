# MeetFlow 部署、迁移与备份

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

## 专用本机环境

使用Docker Compose v2，Java21／Maven3.9和Node24.19.0由镜像提供。复制源码后运行 `python3 scripts/init-env.py`，再运行 `docker compose -p meetflow up -d --build --wait`。脚本拒绝覆盖已有私有配置。默认页面8102，健康入口 `/actuator/health`；MySQL和后端不暴露主机端口。数据卷为 `meetflow_mysql-data`（使用不同项目名时前缀随之改变）。

本机源代码开发见README；自建数据库须创建专用库和最小权限应用用户，配置DATABASE_URL、DATABASE_USER、DATABASE_CATALOG、DATABASE_PASSWORD及ADMIN_PASSWORD。不要把生产库接入验收脚本。

## 对外运行

商业部署需先取得书面授权。使用受控网关、可信HTTPS、访问策略、监控和备份，设置COOKIE_SECURE=true。反向代理到回环8102；使应用收到正确HTTPS方案头，Compose的Nginx示例在本机HTTP场景下使用自身scheme，外部TLS终止时须按可信网关架构调整。不要直接公开MySQL或把所有来源的转发头当作可信信息。默认数据库连接位于隔离本机网络，未要求TLS；跨主机数据库应配置证书校验的加密连接，勿沿用本机连接设置。

管理员初始密码只作用于空库。改变环境变量不会重置已有密码。不要在命令参数中填写实际密码。容器和日志命令由本机授权管理员运行；日志仅用于排错，转发前脱敏。

## 数据库升级

1. 停止写入并记录当前源码SHA和镜像；安全备份 `.env`。
2. 备份MySQL（文件含业务数据，设权限并安全保存）：

```bash
umask 077
docker compose -p meetflow exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers zhuatech_meetflow' > meetflow-backup.sql
```

3. 保持V1、V2文件不变，为结构变更新增V3及以后递增脚本；不得修改已执行文件或开启Hibernate自动改表。
4. 构建并启动新版本，等待健康，查看Flyway历史、登录和完整业务。迁移失败时停止升级，恢复已记录的镜像与备份到专用恢复环境验证，不用repair掩盖差异。
5. 备份恢复命令：在已明确清空并允许恢复的专用目标库中，将备份通过stdin导入 `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" zhuatech_meetflow`。不要覆盖未知数据。

备份必须定期演练恢复；复制容器文件系统不能替代数据库一致性备份。外键历史受到保护，取消预约及维护后保留记录。

## 验收和清理

`meetflow-check` 是专用测试项目名，与正式环境分开。全新卷启动后执行 `scripts/smoke.py --run`，其本地 `.smoke-state.json` 包含验收密码、权限0600且被Git忽略。脚本只允许127.0.0.1，测试库生成的人员与记录明确标记验收测试。重启后执行 `--verify` 验证历史不丢失。

验收完成后仅对这份可丢弃环境运行 `docker compose -p meetflow-check down -v --remove-orphans`，删除本项目 `.smoke-state.json` 和验收 `.env`。不执行全局prune，也不清理其他项目的容器、卷和文件。正式库停止用down保留卷。
