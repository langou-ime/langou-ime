# 懒狗输入法单服务器生产基础设施

生产服务只部署到 `122.51.32.117`。仓库中的配置不包含真实密钥；运行时密钥统一写入
`/etc/langou/production-v1.env`，权限必须为 `0600`。

## 固定边界

- 新版本目录：`/opt/langou/production-v1`，Compose 项目名 `langou-v1`。
- API 容器使用 UID/GID `10001`，只映射 `127.0.0.1:18000`。
- PostgreSQL 和 Redis 只连接 Compose 私有网络，没有宿主机端口。
- 当前官网继续使用 `/opt/langou/website-v3`，安装包继续使用
  `/opt/langou/downloads`；非下载区不得修改。
- `api.langou.tech` 使用独立 Let’s Encrypt 证书和 Nginx 虚拟主机。
- 不使用香港服务器、WireGuard、站外备份或外发告警。

## 首次部署

1. 创建 `/opt/langou/production-v1/{backend,infra,releases,data/postgres,data/redis}`，
   并为数据目录设置容器所需所有权。
2. 从 `single-server/backend.env.example` 生成
   `/etc/langou/production-v1.env`，注入随机内部密钥、MiMo 和阿里云短信配置。
3. 将 `single-server/compose.production.yaml` 安装到
   `/opt/langou/production-v1/compose.production.yaml`，使用
   `docker compose --env-file /etc/langou/production-v1.env` 构建并启动。
4. 首先启用 `api.langou.tech.bootstrap-http.conf`，通过 Certbot webroot 模式签发
   `api.langou.tech` 单域名证书；签发成功后切换到完整 HTTPS 配置。
5. 用 `nginx -t`、容器健康状态、`http://127.0.0.1:18000/ready` 和
   `https://api.langou.tech/ready` 四层验证部署。
6. 安装本机健康检查 timer 和日志轮转。健康异常只写 systemd journal，不向外部发送。

## 并行切换与回滚

旧 `langou-backend.service`、旧 PostgreSQL 和旧 Redis 在新版本公开后继续原地保留
48 小时。新版本使用独立端口和数据目录，不读取旧库中的三个测试账户。

后端镜像按 RC/正式标签保持不可变；Nginx 配置替换前保存上一份文件。发生故障时回滚
到上一新版本镜像或上一 Nginx 配置，旧 API 不作为 v1 客户端的兼容回滚目标。

用户已明确选择不建立站外备份。服务器磁盘或实例损坏时，生产数据可能无法恢复。
