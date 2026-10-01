# 条件订单引擎（Conditional Order Engine）

本地运行的条件订单服务：用户以“触发价 + 比较方向”提交止损 / 止盈条件订单；
当本地注入的行情满足触发条件时，订单在同一原子转换中激活为普通订单并进入
执行流程。激活前订单不成交、不占用资金或仓位，可随时修改或取消。

不依赖任何外部行情或交易服务：行情通过 `POST /api/market/ticks` 本地注入。

## 1. 订单模型

### 条件订单（`ConditionalOrder`）

| 字段 | 含义 |
| --- | --- |
| `type` | `STOP_LOSS`（止损）/ `TAKE_PROFIT`（止盈），业务分类 |
| `direction` | 触发比较方向：`ABOVE`（参考价 ≥ 触发价）或 `BELOW`（参考价 ≤ 触发价） |
| `triggerPrice` | 触发价 |
| `priceSource` | 触发判定使用的行情价格：`LAST` 最新成交价 / `BID` 买一价 / `ASK` 卖一价 |
| `executionType` | 激活后执行方式：`LIMIT` 限价 / `MARKET` 市价 |
| `limitPrice` | 激活后限价单的限价 |
| `maxSlippageBps` | 市价单允许的最大不利滑点，单位基点（1 bp = 0.01%） |
| `expireAt` | 可选的过期时间（epoch millis，按 tick 时间判定） |
| `version` | 乐观版本号，每次修改 +1，用于并发判定 |

条件订单生命周期：

```
PENDING ──触发──▶ ACTIVATED ──成交──▶ FILLED
   │                  ├──子单撤单──▶ CANCELLED
   │                  ├──子单过期──▶ EXPIRED
   │                  └──滑点超限──▶ REJECTED_SLIPPAGE
   ├──取消──▶ CANCELLED
   └──过期──▶ EXPIRED
```

### 激活后的普通订单（`OrdinaryOrder`）

激活后的子单与通过 `POST /api/orders` 直接下单走完全相同的执行代码路径
（同一个 `OrderExecutor`），因此成交、撤单、过期行为与同等参数的直接下单
完全一致。

## 2. 触发与执行语义

### 触发判定

- 每个品种维护单调递增的 tick 序号 `tickSeq`。
- 按订单配置的 `priceSource` 取参考价（`LAST`/`BID`/`ASK`）；该 tick 上
  对应价格缺失时本 tick 不触发，等待后续 tick。
- `ABOVE`：参考价 `>= triggerPrice` 触发；`BELOW`：参考价 `<= triggerPrice` 触发
  （边界相等即触发）。

### 穿价激活，不追价

价格瞬间穿越触发价时，以**穿越后的首个可用价格**激活。激活原子地：

1. 落 `CONDITIONAL_ACTIVATED`（记录触发价、判定参考价、tick 序号、时间）；
2. 落 `ORDER_PLACED` 并建立子单；
3. 在同一 tick 上用同一把锁对子单执行一次判定。

市价子单以“触发判定价格”为滑点参考价：如果穿越后的可执行价（买单取卖一、
卖单取买一，无报价时退回最新价）相对参考价的不利偏差超过 `maxSlippageBps`，
**不追价成交**，而是产生可区分的结果：

- 普通订单 `OrdinaryOrder.status = REJECTED`
- 条件订单 `ConditionalOrder.status = REJECTED_SLIPPAGE`
- 审计事件 `ORDER_REJECTED`，`reason = SLIPPAGE_EXCEEDED`，`detail` 中给出
  参考价、可执行价与实际滑点 bp

滑点计算（基点，四舍五入）：

- 买单：`(executionPrice - referencePrice) / referencePrice * 10000`
- 卖单：`(referencePrice - executionPrice) / referencePrice * 10000`
- 有利方向偏差记为 0。

### 限价执行

- 买单在可执行价 `<= limitPrice` 时成交；卖单在 `>= limitPrice` 时成交。
- 不满足时继续挂单，直到成交、撤单或过期；不会以更差价格成交。

### 过期

tick 时间晚于 `expireAt` 时，PENDING 条件单记为 `EXPIRED`；NEW 普通单同样
在 tick 处理时过期。

## 3. 并发确定性

所有写操作（条件单的创建/修改/取消、普通下单/撤单、tick 注入）都在
`TradingEngine` 的同一把 `ReentrantLock` 下串行执行，且一次转换内的所有
事件先落盘再改内存。因此“修改/取消”与“触发”并发时只有一个确定结果：

- 取消先进入临界区：订单 `CANCELLED`，随后的 tick 不会触发；
- 触发先进入临界区：订单激活并产出子单，随后的取消返回 `409 CONFLICT`
  （已激活），只能对激活后的普通子单撤单；
- 修改携带 `expectedVersion`：版本不匹配返回 `409 CONFLICT`，不会出现
  部分字段生效的中间状态。

## 4. 持久化与崩溃恢复

所有状态变化以 JSON Lines 追加写入事件日志（`FileEventStore`），每条事件：

- 有全局单调递增的 `seq`；
- 默认每次 append 后 `fsync`（`coengine.fsync-per-append=true`）；
- 既是崩溃重放的事实来源，也是完整审计记录。

启动时 `StateRecovery` 重放日志重建内存状态，且重放幂等：

- `PENDING` / 已修改 / `CANCELLED` / `ACTIVATED` / `FILLED` 状态均可恢复；
- 崩溃发生在“激活之后、子单终态落盘之前”时，市价子单依据激活事件中已持久化
  的触发参考价被确定性补全为成交，并追加带 `reason=RECOVERED` 的成交事件；
  再次重启不会重复成交；
- tick 序号从已持久化的 tick/激活事件恢复，重启后继续递增，不会重复触发；
- 崩溃前已生效的修改（`version` 递增）与取消在重放后仍然生效。

## 5. 查询与审计

- `GET /api/conditional-orders?accountId=&symbol=`：按账户/品种查询条件订单。
- `GET /api/orders?accountId=&symbol=`：查询普通订单（含激活子单）。
- `GET /api/history?accountId=&symbol=`：返回事件审计视图 `TriggerHistoryView`，
  包含事件类型、时间、tick 序号、触发价 `triggerPrice`、触发判定价
  `referencePrice`、拒绝时可执行价 `executionPrice`、实际成交价 `fillPrice`、
  滑点上限与实际滑点，使“触发价 → 判定价 → 成交价”的偏差完全可解释。

## 6. 配置项（`application.properties`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coengine.data-dir` | `data/coengine` | 事件日志目录，启动时自动创建 |
| `coengine.event-log-file` | `events.log` | 事件日志文件名 |
| `coengine.default-price-source` | `LAST` | 订单未指定时的默认触发价格源 |
| `coengine.default-max-slippage-bps` | `50` | 市价单默认滑点上限（基点） |
| `coengine.fsync-per-append` | `true` | 是否每条事件落盘后 fsync |

## 7. HTTP 接口示例

创建止损市价条件订单：

```bash
curl -X POST localhost:8080/api/conditional-orders -H 'Content-Type: application/json' -d '{
  "accountId":"acct-1","symbol":"BTC","side":"SELL","type":"STOP_LOSS",
  "direction":"BELOW","triggerPrice":"100","priceSource":"LAST",
  "executionType":"MARKET","quantity":2,"maxSlippageBps":50
}'
```

注入行情：

```bash
curl -X POST localhost:8080/api/market/ticks -H 'Content-Type: application/json' -d '{
  "symbol":"BTC","timestamp":1700000000000,"last":"98","bid":"98","ask":"98"
}'
```

修改（激活前，携带 `expectedVersion`）：

```bash
curl -X PUT localhost:8080/api/conditional-orders/C-xxx -H 'Content-Type: application/json' -d '{
  "expectedVersion":1,"triggerPrice":"90"
}'
```

取消：`POST /api/conditional-orders/{id}/cancel`。
直接下单：`POST /api/orders`（字段与条件订单的执行部分一致）。

错误响应：`400 VALIDATION_ERROR`、`404 NOT_FOUND`、`409 CONFLICT`
（已激活/已取消/版本冲突）。

## 8. 测试

```bash
mvn test
```

覆盖场景：

- `TriggerAndExecutionTest`：价格源选择、边界/穿价判定、买卖方向不利滑点、
  滑点超限拒绝、限价不追价、过期。
- `EngineFlowTest`：穿价后首个可用价格激活且只成交一次、激活限价单与直接
  限价单行为一致、滑点超限可区分结果、修改生效、过期、30 轮
  取消/触发并发的确定性、激活后取消冲突、审计偏差解释。
- `RecoveryTest`：PENDING/已修改/ACTIVATED/FILLED 状态恢复、模拟激活后崩溃
  的确定性补全且不重复成交、取消在重启后保持生效、tick 序号跨重启延续。
- `WebApiIntegrationTest`：通过 HTTP 完成创建→注入→激活→成交→审计、
  滑点拒绝与修改后按新参数触发。
