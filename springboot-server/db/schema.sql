-- Vidoc 建库建表脚本：新环境（本地重建 / 部署到托管 MySQL）用这份初始化。
-- 日常开发不需要它——表结构与索引仍由 Hibernate 的 ddl-auto=update 维护，本文件是权威参照。
-- public_id 必须写死 ascii_bin：二进制比较更快、排序即字节序（= UUIDv7 时间序，游标 tie-breaker 依赖它）、
-- 且大小写敏感。用库默认排序规则（utf8mb4_unicode_ci）建表不会报错，只会静默退化，所以这里显式声明。
-- 唯一键名沿用 Hibernate 生成的隐式名，否则下次 ddl-auto=update 会再补一个重复的唯一索引。

CREATE DATABASE IF NOT EXISTS `vidoc`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `vidoc`;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `video_summaries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `duration` bigint DEFAULT NULL,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `status` enum('COMPLETED','FAILED','PENDING','PROCESSING') NOT NULL,
  `summary_text` longtext COLLATE utf8mb4_unicode_ci,
  `thumbnail_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `truncated` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  `video_url` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress_step` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl176bchfmpgo0cb8iip3lxr78` (`public_id`),
  KEY `idx_video_summaries_user_created` (`user_id`,`created_at`),
  KEY `idx_video_summaries_user_status_created` (`user_id`,`status`,`created_at`,`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户积分余额：一用户一行，唯一真源。两个分项分开存，total 由分项重算，故不可能与分项漂移。
-- subscription_expires_at 是**当前订阅期**的结束时刻；订阅一次付款分多期到账，
-- 每期额度与期长随订阅一起物化，过期结算时无需反查订单。都不依赖定时任务。
-- 注册时一次性赠送 purchase（VIDOC_POINTS_SIGNUP_GRANT），没有每日赠送，也没有档位列。
CREATE TABLE `user_points` (
  `user_id` bigint NOT NULL,
  `purchase` int NOT NULL,
  `subscription` int NOT NULL,
  `subscription_expires_at` datetime(6) DEFAULT NULL,
  `total` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `subscription_period_points` int NOT NULL,
  `subscription_period_seconds` bigint NOT NULL,
  `subscription_periods_left` int NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 支付宝电脑网站支付订单。out_trade_no 就是对外订单号（UUIDv7 去横线），不另设 public_id。
-- 幂等靠状态机：只有 PENDING 能被 markPaid 改写一次，重复通知与并发查询都挡在这里。
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alipay_trade_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expire_at` datetime(6) NOT NULL,
  `out_trade_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `period_points` int NOT NULL,
  `period_seconds` bigint NOT NULL,
  `periods` int NOT NULL,
  `plan_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CLOSED','PAID','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5cm7vy4jvx3rd9jeehm8s4pqt` (`out_trade_no`),
  KEY `idx_orders_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 积分流水：只作展示与追溯（余额真源是 user_points），每个用户只保留最近若干条。
-- 不记注册赠送 —— 那行每人都一样、信息量为零。
CREATE TABLE `points_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `delta` int NOT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` enum('CONSUME','PURCHASE','REFUND') COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoxip2mhi5ai7xwunajyy7vt9n` (`idempotency_key`),
  KEY `idx_points_ledger_user` (`user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
