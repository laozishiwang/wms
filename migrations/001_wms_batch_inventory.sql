-- 独立 WMS 库：保留原有仓库 + SKU 汇总库存；旧库存保持未分配批次。
-- 该脚本需与批次业务代码一起发布；切勿对设备管理系统数据库执行。
CREATE TABLE `wms_batch_inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `batch_no` varchar(64) NOT NULL,
  `production_date` datetime DEFAULT NULL,
  `expiration_date` datetime DEFAULT NULL,
  `quantity` decimal(20,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wms_batch_inventory` (`tenant_id`, `warehouse_id`, `sku_id`, `batch_no`),
  KEY `idx_wms_batch_expiry` (`tenant_id`, `expiration_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
