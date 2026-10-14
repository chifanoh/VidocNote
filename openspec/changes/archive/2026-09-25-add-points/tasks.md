## 1. 后端 · 两张表与领域

- [x] 1.1 `entity/UserPoints.java`：`daily` / `subscription` / `purchase` / `total` / `dailyDate` / `subscriptionExpiresAt` / `updatedAt`；**变更字段一律不开 setter**，只能经 `spend` / `grantPurchase` / `grantSubscription` / `rollDaily` / `expireSubscription`
- [x] 1.2 `entity/PointsLedger.java` 瘦身：删掉 `bucket` 与 `expiresAt`（余额已物化，流水只是历史）
- [x] 1.3 `entity/PointsReason.java` 收敛为 `CONSUME` / `REFUND` / `PURCHASE`（不再写签到）
- [x] 1.4 删除 `entity/PointsBucket.java`
- [x] 1.5 `repository/UserPointsRepository.java`：`findForUpdate`（锁定读）/ `insertIfAbsent`（`insert ignore`）/ `backfillMissing`
- [x] 1.6 `repository/PointsLedgerRepository.java`：`existsByIdempotencyKey` / `findRecentItems`（左连摘要取对外标识）/ `trim`
- [x] 1.7 `config/PointsProperties.java`：`daily-grant`（30）+ `ledger-keep`（50）

## 2. 后端 · 服务

- [x] 2.1 `PointsService.balance`：快路径只读一行；行缺失或过期才走锁定读 + 惰性结算
- [x] 2.2 `PointsService.consume`：锁定读 → **锁后**查幂等 → 惰性结算 → 余额校验 → 扣减 → 写流水 → 裁剪
- [x] 2.3 `PointsService.refund`：原额落永久积分，按摘要幂等
- [x] 2.4 `PointsService.grantPurchase` / `provision` / `backfillMissingRows` / `recent`
- [x] 2.5 `UserService.createUser` 注册即建积分行
- [x] 2.6 `PointsStartupBackfill`：启动时为存量用户补建（`ApplicationReadyEvent`，幂等）
- [x] 2.7 `SummaryProcessor`：拿到时长后扣分；失败路径统一经 `discard(summary, charged)` 退回

## 3. 后端 · 接口与配置

- [x] 3.1 `PointsDtos`：`PointsSummaryResponse(balance)` —— 三本账不出接口（`dailyExpiresAt` 已删，见 7.4）
- [x] 3.2 `PointsController`：`GET /api/points`、`GET /api/points/ledger?size=`（默认 10、上限 50）
- [x] 3.3 `application.yml` + `.env.example` 补 `VIDOC_POINTS_DAILY_GRANT` / `VIDOC_POINTS_LEDGER_KEEP`
- [x] 3.4 `GlobalExceptionHandler` 无需改动：已按 `ApiException.status` 统一出参，402 直接可用

## 4. 数据库与迁移

- [x] 4.1 `db/schema.sql` 改为 `user_points` + 瘦身后的 `points_ledger`，列顺序按 `SHOW CREATE TABLE` 实测结果对齐
- [x] 4.2 **删掉旧的 `points_ledger`**：`ddl-auto: update` 只补列不删列，旧表带着 `bucket NOT NULL` 留着会让新插入全部失败
- [x] 4.3 实测新表 DDL 与 `schema.sql` 逐字段一致（唯一键名 `UKoxip2mhi5ai7xwunajyy7vt9n`）

## 5. 前端

- [x] 5.1 `types/index.ts`：`PointsSummary` 收敛为 `{ balance }`，`PointsReason` 去掉 `DAILY_GRANT`
- [x] 5.2 `composables/usePoints.ts`、`utils/points.ts`
- [x] 5.3 `pages/dashboard/index.vue`：**下线三张构成卡**，只留可用合计 + 一行「每日赠送当日有效 · 某时刻作废」+ 最近 10 条流水
- [x] 5.4 余额与流水**串行**请求，避开首次并发建行
- [x] 5.5 `utils/plans.ts` 免费档文案改成积分口径

## 6. 验证

- [x] 6.1 `UserPointsTest`（8 例）：合计恒等于分项之和、扣减顺序、余额不足抛错、日切只在跨日时重置、月付只在过期后清零、退回落永久
- [x] 6.2 `PointsServiceTest`（14 例）：扣分/退回/购买的行内容与幂等键、余额不足不写任何行、重复操作跳过、快路径不写库、跨日先补签到再扣、裁剪 offset
- [x] 6.3 `PointsCostTest`（7 例）：阶梯边界 60→60 / 61→62 / 180→240 / 181→242、进位、时长缺失回退
- [x] 6.4 `./mvnw test`：**57 tests, 0 failures**
- [x] 6.5 启动回填：另起 8099 实测日志 `已为 1 个存量用户补建积分行`，`daily_date = 1970-01-01` 使首次访问即到账
- [x] 6.6 **退回链路**（此前一直没验过）：用**无效 LLM key** 起实例 → 真实 B 站视频 → 扣 10 后 LLM 失败 → 退 10、记录删除、余额 30→30 精确复原；账本 `CONSUME -10 / REFUND +10` 均带 `summary_id`
- [x] 6.7 成功链路：正常 key → 真实 B 站视频 18s COMPLETED → **余额 30→20**，流水 `CONSUME -10` 带摘要对外标识，`user_points` = `daily 10 / purchase 10 / total 20`
- [x] 6.8 流水裁剪：SQL 实测 62 行 → 50 行，保留的是最新 50 条

## 7. 文档与记忆

- [x] 7.1 `DESIGN.md` §6 积分小节按新页面形态改写
- [x] 7.2 更新记忆快照与 `MEMORY.md` 索引（两张表、行锁、惰性日切、死锁成因、裁剪取舍、以及被丢弃的 3 行旧流水）
- [x] 7.4 死代码清理（用户「删该删的」）：删掉 `dailyExpiresAt` 整条链（DTO 字段 / `PointsService.dailyExpiresAt()` / `PointsController` 传参 / 前端类型 / 对应单测）、从未被引用的 `PointsDtos.PointsLedgerList` 与 `PointsLedgerRepository.existsBySummaryIdAndReason`
- [x] 7.3 变更归档到 `openspec/changes/archive/` 并把主规范提升到 `openspec/specs/`
