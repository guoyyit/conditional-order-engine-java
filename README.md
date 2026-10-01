# 条件订单服务（Conditional Order Engine）

用户以触发价为条件提交订单，行情满足触发条件时订单自动激活并进入执行流程。
行情为本地注入的成交序列（REST 接口推送），不依赖任何外部服务。

## 核心概念

### 条件订单类型

- `STOP_LOSS` 止损、`TAKE_PROFIT` 止盈（业务标签，触发判定由比较方向表达）
- 触发条件 = 参考价格来源 `priceSource` + 比较方向 `direction` + 触发价 `triggerPrice`
- 执行方式 `execType`：`LIMIT` 限价 / `MARKET` 市价（受滑点上限 `maxSlippage` 约束）

### 触发语义

- 判定价格来源可配置：`LAST_TRADE` 最新成交价、`BID1` 买一价、`ASK1` 卖一价。
- 比较方向：`GREATER_EQUAL`（参考价 ≥ 触发价）或 `LESS_EQUAL`（参考价 ≤ 触发价）。
- **穿价激活**：价格瞬时穿越触发价时，按穿越后的首个可用价格（即当前行情价格）
  作为判定价格激活，不回溯到触发价。
- **不追价**：限价单成交价不劣于限价（买单 ≤ 限价、卖单 ≥ 限价），未触及则留在
  执行流程等待后续行情；市价单以当前对手价成交一次，滑点超限即拒绝，不向不利
  方向追价。
- 激活前订单不占用任何资金或仓位，可任意修改参数或取消。

### 激活后行为

激活后订单转为普通订单进入执行流程，其成交、撤单与过期行为与同等参数的直接
下单完全一致：

- 限价未成交的订单保持 `ACTIVATED`，后续每条行情继续尝试成交；
- `expireAt`（按行情时间判定）到达后，待触发与已激活订单均转为 `EXPIRED`；
- 任意时刻可取消（`PENDING` 与 `ACTIVATED` 状态）。

### 状态机

```
PENDING --触发--> ACTIVATED --成交--> FILLED
   |                 |
   |                 --滑点超限--> SLIPPAGE_REJECTED
   |                 |
   +--取消--> CANCELLED（两态均可取消）
   +--过期--> EXPIRED（两态均可过期）
```

## 并发语义

所有状态变更（提交/修改/取消/触发/执行）在同一把锁内串行执行，且**先写事件
日志（fsync）再改内存**。修改或取消与触发并发时结果确定：

- 触发先完成 → 按旧参数完成激活，之后的修改被拒绝（`IllegalStateException`）；
- 修改/取消先完成 → 触发按新参数判定，或订单已取消不再触发。

不存在部分生效的中间状态。

## 持久化与崩溃恢复

- 全部状态变更以 JSON Lines 事件追加写入 `conditional-engine.store-dir`，
  每条 `fsync` 落盘后才更新内存状态。
- 重启时重放事件日志重建内存：`PENDING` 继续等待触发，`ACTIVATED` 继续执行
  流程（不重复触发，触发时间保持原值），`FILLED`/`CANCELLED`/`EXPIRED`/
  `SLIPPAGE_REJECTED` 为终态（不重复成交、不可逆）。
- 崩溃前已生效的修改与取消不会丢失；崩溃导致的不完整尾部日志行在加载时跳过。

## 查询与审计

- 按账户查询：`GET /api/conditional-orders?accountId=...&symbol=...`（symbol 可选）
- 触发历史：`GET /api/conditional-orders/trigger-history?accountId=...&symbol=...`
- 每条触发记录包含：触发时间 `triggeredAt`、判定价格 `judgedPrice`、触发价
  `triggerPrice`、实际成交价 `execPrice`、结果 `outcome` 及明细 `detail`；
  `judgeDeviation()`（判定价 − 触发价）与 `execDeviation()`（成交价 − 判定价，
  即滑点）可解释判定价与成交价之间的偏差。

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `conditional-engine.store-dir` | `data/event-log` | 事件日志存储目录 |
| `conditional-engine.default-max-slippage` | `0.05` | 市价单默认滑点上限（绝对价格偏差），订单未指定 `maxSlippage` 时生效 |

## REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/conditional-orders` | 提交条件订单 |
| `PUT` | `/api/conditional-orders/{orderId}` | 激活前修改参数 |
| `DELETE` | `/api/conditional-orders/{orderId}` | 任意时刻取消 |
| `POST` | `/api/conditional-orders/market-quotes` | 注入行情快照 |
| `GET` | `/api/conditional-orders?accountId=&symbol=` | 按账户与品种查询 |
| `GET` | `/api/conditional-orders/trigger-history?accountId=&symbol=` | 查询触发历史 |

提交订单示例：

```json
{
  "accountId": "acc-1",
  "symbol": "BTCUSDT",
  "side": "SELL",
  "kind": "STOP_LOSS",
  "priceSource": "LAST_TRADE",
  "direction": "LESS_EQUAL",
  "triggerPrice": 100,
  "execType": "MARKET",
  "maxSlippage": 0.5,
  "quantity": 10,
  "expireAt": 0
}
```

## 构建与测试

```bash
mvn compile   # 编译
mvn test      # 运行全部测试
```

测试覆盖：穿价激活与价格来源配置、限价不追价、滑点超限拒绝、过期、
并发修改/取消与触发的确定性、崩溃恢复（未激活/已激活/已成交/滑点拒绝）。
