# Linux、Kubernetes 与 JVM

## 常用诊断

```bash
docker compose ps
docker compose logs --tail=100 app
docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u flashsale -D flashsale -e "SELECT stock FROM products; SELECT status,COUNT(*) FROM sale_orders GROUP BY status; SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL;"'
docker compose exec redis redis-cli INFO stats
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:19092 --describe --group notification-workers
```

Kafka 的失败消息可以先只读检查：

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 --topic order-events.DLT --from-beginning --max-messages 1
```

确认原因、修复消费者后再有选择地重放 DLT 原始 payload。不要把未知失败事件一股脑重放，event ID 需要保留才能去重。

## JVM 实验

运行镜像保留 JDK 工具，以便学习线程和内存诊断。Java 是容器 PID 1，以同一用户执行：

```bash
docker compose exec app jcmd 1 VM.flags
docker compose exec app jcmd 1 GC.heap_info
docker compose exec app jcmd 1 Thread.print
docker compose exec app jcmd 1 JFR.start name=load settings=profile duration=60s filename=/tmp/flashsale.jfr
# 这 60 秒内从另一个终端运行 k6，再导出录制（Bash）：
docker cp "$(docker compose ps -q app):/tmp/flashsale.jfr" ./flashsale.jfr
```

用 JDK Mission Control 查看热点方法、分配、锁竞争和线程。实验时记录：连接池是否耗尽、SQL 锁等待、Redis 延迟、Kafka lag、CPU 和 GC 暂停。修改连接池或线程数前后保持相同流量，再比较数据。

## 本地 Kubernetes

示例适合 Docker Desktop Kubernetes、kind 或 minikube 的学习集群，需要默认 StorageClass 供应 PVC。先确认 `kubectl config current-context` 指向你自己的本地集群。

```bash
docker build -t flash-sale-lab:local .
# kind 集群额外执行：kind load docker-image flash-sale-lab:local
# minikube 集群额外执行：minikube image load flash-sale-lab:local
kubectl apply -f deploy/k8s/namespace.yaml
kubectl -n flash-sale-lab create secret generic flash-sale-secrets \
  --from-literal=DB_PASSWORD='replace-with-your-local-password' \
  --from-literal=MYSQL_ROOT_PASSWORD='replace-with-another-local-password'
kubectl apply -k deploy/k8s
kubectl -n flash-sale-lab rollout status deployment/app --timeout=300s
kubectl -n flash-sale-lab port-forward service/app 8080:8080
```

Windows PowerShell 将多行命令改为一行，或使用 PowerShell 的续行符。Secret 示例只用于本地学习，不要提交真实凭据。PVC 中数据库初始化后，单独修改 Secret 不会自动修改已有数据库用户密码。

```bash
kubectl kustomize deploy/k8s
kubectl -n flash-sale-lab get pods,pvc
kubectl -n flash-sale-lab logs deployment/app --tail=100
kubectl -n flash-sale-lab scale deployment/app --replicas=3
kubectl -n flash-sale-lab rollout restart deployment/app
kubectl -n flash-sale-lab rollout status deployment/app
```

`port-forward service/app` 会选一个 Pod，不适合证明多副本负载均衡。要压测副本间竞争，在集群内访问 Service，或为本地集群另配入口。

本示例包含两个 API 副本、单节点 MySQL/Kafka。Redis 没有 PVC，重启后缓存和短时限流窗口会丢失，不影响 SQL 库存，但限流计数会重置。不要把这套清单直接部署到生产；它没有 TLS、NetworkPolicy、中间件 ACL、备份和高可用配置。
