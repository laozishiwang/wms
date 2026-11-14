CREATE TABLE IF NOT EXISTS `wms_sku_barcode` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `sku_id` BIGINT NOT NULL,
  `barcode` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wms_sku_barcode_tenant_value` (`tenant_id`, `barcode`),
  KEY `idx_wms_sku_barcode_tenant_sku` (`tenant_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
