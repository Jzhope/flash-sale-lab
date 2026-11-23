# REST API

默认 Base URL：`http://localhost:8080`。金额统一使用整数分。用户名为 3–32 位小写字母、数字或下划线；密码为 8–64 位可打印 ASCII 字符。

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 否 | 注册并返回 token，201 |
| POST | `/api/auth/login` | 否 | 登录，200 |
| POST | `/api/auth/logout` | Bearer | 撤销当前 token，204 |
| GET | `/api/products` | 否 | 前 100 个商品 |
| GET | `/api/products/{id}` | 否 | 商品元数据，优先 Redis |
| POST | `/api/orders` | Bearer | 下单，要求 Idempotency-Key，201 |
| GET | `/api/orders` | Bearer | 当前用户最近 100 笔订单 |
| GET | `/api/orders/{id}` | Bearer | 本人订单 |
| POST | `/api/orders/{id}/simulate-payment` | Bearer | 模拟付款，200 |
| POST | `/api/orders/{id}/cancel` | Bearer | 取消未支付订单，200 |
| GET | `/api/notifications` | Bearer | 本人最近 100 条站内通知 |
| GET | `/actuator/health/liveness` | 否 | 进程存活 |
| GET | `/actuator/health/readiness` | 否 | 应用、SQL、Redis 就绪 |

下单示例（Bash，需要 jq）：

```bash
BASE=http://localhost:8080
BUYER=demo_$(date +%s)
TOKEN=$(curl -fsS "$BASE/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$BUYER\",\"password\":\"student-password\"}" | jq -r .accessToken)
curl -fsS "$BASE/api/products/1"
ORDER=$(curl -fsS "$BASE/api/orders" \
  -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: demo-order-001' \
  -H 'Content-Type: application/json' -d '{"productId":1,"quantity":1}')
printf '%s\n' "$ORDER"
ID=$(printf '%s' "$ORDER" | jq -r .id)
curl -fsS -X POST "$BASE/api/orders/$ID/simulate-payment" -H "Authorization: Bearer $TOKEN"
curl -fsS "$BASE/api/notifications" -H "Authorization: Bearer $TOKEN"
```

通知异步到达，刚支付后查询可能还没有记录。Windows 可以直接运行 `scripts/smoke.ps1`，它包含通知等待和幂等校验。

订单响应示例：

```json
{
  "id": "<order UUID>",
  "userId": "<user UUID>",
  "productId": 1,
  "quantity": 1,
  "totalCents": 19900,
  "status": "PENDING",
  "createdAt": "2026-01-01T00:00:00Z",
  "expiresAt": "2026-01-01T00:15:00Z"
}
```

`Idempotency-Key` 接受 1–64 位字母、数字、`_`、`-`，作用域是用户。只要订单保留，key 就保留。同请求重试仍返回 201 和原订单的当前状态；不同请求复用 key 返回 409。限流发生在幂等查询之前，重试也会计入限流；429/503 后稍后用原 key 重试。

模拟支付和取消均为幂等状态操作。已过期订单调用支付接口时，会提交过期关单和库存释放，并返回 200、`status=EXPIRED`，客户端必须检查状态，不能把任何 200 都视为付款成功。已支付订单不能取消，已取消订单不能支付。

错误采用 Problem Details 风格：`status`、`detail`，业务错误另有标准 `type`、`title`、`instance` 字段。常见状态码：400 参数错误、401 未认证、404 不存在或不属于当前用户、409 库存/状态/幂等冲突、429 限流、503 依赖暂不可用。响应不返回 SQL、密码或 token 摘要。
