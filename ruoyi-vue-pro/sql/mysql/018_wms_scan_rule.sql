CREATE TABLE IF NOT EXISTS `wms_scan_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `prefix` varchar(64) NOT NULL DEFAULT '',
  `delimiter` varchar(16) NOT NULL,
  `fields_json` text NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_wms_scan_rule_tenant_enabled` (`tenant_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
