CREATE TABLE IF NOT EXISTS `wms_scan_rule_sku` (
  `tenant_id` bigint NOT NULL,
  `rule_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`, `rule_id`, `sku_id`),
  KEY `idx_scan_rule_sku` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
