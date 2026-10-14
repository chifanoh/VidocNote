## 1. 支付宝侧前置（人工，可与编码并行）

- [x] 1.1 在支付宝开放平台创建网页应用并记录 APPID —— 验证：APPID `2021007103626273`，订单能走到生产收银台
- [x] 1.2 配置 RSA2 公钥加签：生成应用私钥、上传应用公钥、记录支付宝公钥 —— 验证：用同一套密钥对生产网关做 `alipay.trade.query` 签名请求返回 `10000 Success`，加签配置正确
- [ ] 1.3 应用网关填 `https://vidocnote.com/api/payments/alipay/notify` —— 验证：保存后详情页回显该地址（**未确认**；交易通知走下单请求里的 `notify_url`，应用网关只影响消息服务）
- [x] 1.4 提交上线审核（约 1 个工作日）→ 绑定商家账号 → 开通「电脑网站支付」 —— 验证：生产环境真实付款成功，说明三件事都已完成
- [ ] 1.5 备齐沙箱与线上两套凭据（APPID / 应用私钥 / 支付宝公钥）—— 验证：两套凭据都能在配置中列出且互不覆盖（**只备了线上**）

## 2. 后端 · 数据模型

- [x] 2.1 新增 `entity/Order.java`：`outTradeNo` / `userId` / `planCode` / `amount` / `periods` / `periodPoints` / `status` / `alipayTradeNo` / `paidAt` / `expireAt` / `createdAt`，状态变更只经方法、不开 setter —— 验证：`OrderTest` 6 例断言合法跃迁成功、非法跃迁不生效
- [x] 2.2 新增 `entity/OrderStatus`（`PENDING` / `PAID` / `CLOSED`）—— 验证：编译通过且被 `OrderTest` 覆盖
- [x] 2.3 `db/schema.sql` 增加 `orders` 表，`out_trade_no` 建唯一键，列顺序按实测 `SHOW CREATE TABLE` 对齐 —— 验证：实测 DDL 与 `schema.sql` 逐字段一致（唯一键 `UK5cm7vy4jvx3rd9jeehm8s4pqt`）
- [x] 2.4 `entity/UserPoints.java` 增加订阅期字段（`subscriptionPeriodPoints` / `subscriptionPeriodsLeft` / `subscriptionPeriodSeconds`），仍不开 setter —— 验证：`SHOW CREATE TABLE user_points` 出现三列
- [x] 2.5 新增 `repository/OrderRepository`：按订单号查、锁定读、按用户倒序分页 —— 验证：锁定读在 `confirmPaid` 内被真实调用，端到端三次重放通知只发放一次

## 3. 后端 · 支付宝对接

- [x] 3.1 `pom.xml` 引入 `com.alipay.sdk:alipay-sdk-java` 最新稳定版 —— 验证：`4.40.272.ALL` 下载并编译通过
- [x] 3.2 新增 `config/AlipayProperties`，`application.yml` 与 `.env.example` 补 APPID / 应用私钥 / 支付宝公钥 / 网关地址 / 沙箱开关 / 功能开关 —— 验证：启动日志只打印脱敏 APPID，不含私钥
- [x] 3.3 `AlipayClient` 在 `AlipayGateway` 内建一次；凭据不全或私钥非法时退化为「未配置」而不阻断启动 —— 验证：`AlipayGatewayTest` 断言未配置时 `isConfigured()` 为 false、网关随沙箱开关切换
- [x] 3.4 新增 `AlipayGateway`：`pagePay`（返回自提交表单 HTML）/ `query`（返回交易状态与实付金额）/ `verifyNotify`（用支付宝公钥验签）—— 验证：`AlipayGatewayTest` 用自建密钥对验签通过，篡改金额、换密钥签名、缺公钥三种情况均拒绝

## 4. 后端 · 订单与通知

- [x] 4.1 创建订单接口：校验套餐在售、生成 UUIDv7 商户订单号、金额与期数只取服务端定义、写入支付超时 —— 验证：端到端夹带 `amount:1 / periodPoints:999999 / periods:99` 下单，落库仍是 1990 / 2000 / 1 期
- [x] 4.2 订阅生效期内拒绝订阅类套餐下单 —— 验证：端到端第二次下单返回 409
- [x] 4.3 发起支付接口：仅 `PENDING` 可发起、校验订单归属、调 `pagePay` 返回表单 HTML —— 验证：`OrderServiceTest` 断言他人订单按「不存在」处理
- [x] 4.4 `SaTokenConfigure` 放行 `POST /api/payments/alipay/notify` —— 验证：匿名 POST 返回 200 + `failure`，同批匿名 GET 订单接口仍是 401
- [x] 4.5 通知处理：验签 → 核对 `app_id` / `out_trade_no` / `total_amount` → 锁定读订单 → 仅当 `PENDING` 且交易成功才跃迁并发积分 → 以 `text/plain` 返回 `success` / `failure` —— 验证：`OrderServiceTest` 覆盖四条路径，端到端再验一次
- [x] 4.6 主动查询接口：按订单向支付宝查询并把订单确认为已支付 —— 验证：`OrderServiceTest` 断言查询确认与通知确认落到同一结果
- [x] 4.7 订单查询接口：列表与详情，只返回本人数据 —— 验证：端到端越权查询返回 404，未认证返回 401
- [x] 4.8 惰性关闭：订单已过支付超时且未支付则置为已关闭 —— 验证：`OrderServiceTest` 断言超时订单被关闭且不会调用支付宝

## 5. 后端 · 订阅滴灌

- [x] 5.1 `PointsService` 新增按订单发放订阅：首期立即到账、剩余期数与每期额度写入余额行、幂等键取订单号 —— 验证：`PointsServiceTest` 断言重复发放只到账一次；端到端三次重放通知流水仍只有一条
- [x] 5.2 `expireSubscription` 改为按期循环推进：跨越多期只发当前期，期数发完清零 —— 验证：`UserPointsTest` 覆盖「刚好到期」「跨 3 期未访问」「最后一期到期」
- [x] 5.3 周期长度常量：月付 30 天、年付 365/12 天，12 期累计等于 365 天 —— 验证：`PlanTest` 断言累计天数
- [x] 5.4 订阅购买写入流水 —— 验证：端到端流水一条 `PURCHASE +2000`，与余额合计一致

## 6. 前端

- [x] 6.1 `types/index.ts` 与 `composables/usePayment.ts`：下单、发起支付、订单列表、订阅状态 —— 验证：类型检查中本次改动涉及的文件零错误
- [x] 6.2 `pages/dashboard/plans.vue` 接上下单：点击时**先同步** `window.open` 占住标签页，再请求并把返回的表单 HTML 写入该窗口 —— 验证：CDP 实测点击后新标签跳到 `openapi.alipay.com/gateway.do?...method=alipay.trade.page.pay`，当前页未被导航，零控制台报错
- [x] 6.3 订阅生效期内点「订阅」即时查询并提示，而非置灰 —— 验证：CDP 实测订阅生效时点月付/年付均不建订单、占位标签页自动关闭、toast 出现「当前处于订阅期内」；订阅过期后同一次点击正常走到收银台
- [x] 6.4 `pages/dashboard/billing.vue` 改为真实订单列表（套餐 / 金额 / 状态 / 时间）—— 验证：CDP 实测空态显示「暂无账单记录」，有数据时套餐名 / ￥19.90 / 待支付三者齐备，零控制台报错
- [x] 6.5 从收银台返回站点后主动查询一次并刷新订单状态 —— 验证：CDP 实测带 `?out_trade_no=` 落地时依次发出 `POST .../confirm` 与 `GET .../orders`，零控制台报错（回执形态按 design 的 Open Question 定，未单独做结果页）

## 7. 验证

- [x] 7.1 `./mvnw test` 全绿 —— 验证：**94 tests, 0 failures**（原 57，新增 37）
- [ ] 7.2 沙箱端到端：下单 → 余额付款 → 收到通知 → 积分到账 —— 验证：`orders.status`、`user_points` 订阅字段、流水三处互相对得上（**需支付宝沙箱凭据**；本地已用自建密钥对验证过后半段）
- [x] 7.3 重复通知不重复发放 —— 验证：同一通知重放 3 次，流水只有一条 `ORDER:<no>`，余额 2000 未变
- [x] 7.4 篡改通知被拒 —— 验证：改 `total_amount` 后重放返回 `failure`，订单与余额均不变
- [x] 7.5 线上真实支付方式小额验收 —— 验证：真实支付 ￥0.01 → 支付宝侧 `TRADE_SUCCESS` → 主动查询确认 → 订单 `PAID`（`alipay_trade_no` 一致）→ `user_points` 订阅 2000 / 30 天到期 → 流水一条 `PURCHASE +2000`，三处对得上
- [x] 7.6 用 `db/schema.sql` 新建空库后启动，`ddl-auto` 不做任何改动 —— 验证：开启 `org.hibernate.tool.schema=DEBUG` 后全新库首次启动**零 DDL 输出**，`SHOW CREATE TABLE` 内容不变

## 8. 文档与记忆

- [x] 8.1 `DESIGN.md` 补订单与付费流程小节（状态机 + 滴灌规则）—— 验证：新增 §10，含订单状态机、订阅滴灌表、前端形态三节
- [x] 8.2 `CLAUDE.md` 补支付硬性约定：通知端点匿名但安全边界只有验签、金额与额度只由服务端套餐定义决定 —— 验证：新增「## 支付（2026-09-25 起）」5 条；顺带把过期的「当前无活跃 change」改成指向 `add-alipay-payment`
- [x] 8.3 更新记忆快照与 `MEMORY.md` 索引 —— 验证：`vidoc-seo-progress.md` 增「支付实现完成」小节（含 4 个工程坑与测试未入库的警告），`MEMORY.md` 已登记

## 9. 积分充值（2026-09-26 追加）

定价遵循用户指定的「像手机流量套餐，单独买更贵」：订阅 ≈ 100~190 分/元（打包），充值按 50 / 60 / 70 分每元递阶。

- [x] 9.1 `entity/Plan` 加商品类型 `PlanKind{SUBSCRIPTION, TOPUP}` 与三档充值（`topup_10` ￥10/500、`topup_50` ￥50/3000、`topup_100` ￥100/7000）—— 验证：`PlanTest` 覆盖三档的金额字符串（`10.00` / `50.00` / `100.00`）与商品类型；`TOPUP` 档的 `periods == 1`、`periodSeconds == 0`
- [x] 9.2 `payment/OrderService.create`：生效期内的拦截只对 `SUBSCRIPTION` 生效 —— 验证：订阅生效时下单充值档返回 201 且建出订单；下单订阅档仍返回 409
- [x] 9.3 `payment/OrderService.confirmPaid` 按商品类型分支发放：`SUBSCRIPTION → grantSubscription`、`TOPUP → grantPurchase(points, orderId)` —— 验证：`OrderServiceTest` 断言充值订单确认后调用 `grantPurchase` 而非 `grantSubscription`，且重复确认只发一次
- [x] 9.4 `app/utils/plans.ts` 拆出 `subscribePlans` / `topupPlans`，`VidocPlan` 加 `kind` —— 验证：两区价格与文案与服务端枚举逐项对齐
- [x] 9.5 `pages/dashboard/plans.vue` 用 `UTabs`（`variant="link" color="primary"`）分「订阅 / 充值」两区，两区共用一个 `buy(plan)` —— 验证：CDP 实测两个 Tab 各自渲染对应档位、切换正常、无横向溢出、控制台零报错；订阅期内充值按钮**不置灰**且点击能下单
- [x] 9.6 验证：`./mvnw clean test` 全绿；CDP 实测充值链路 `subscription` 不被查询、`createOrder`/`pay` 各一次
- [x] 9.7 文档：`DESIGN.md` §10 补两种商品与前端形态、`CLAUDE.md` 支付小节补「两种商品」一条 —— 验证：两处都能读到「充值 = 永久积分、随时可买；订阅 = 限时积分、生效期内不可再买」
