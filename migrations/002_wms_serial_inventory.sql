-- 仅用于独立 WMS 库；先备份并检查列和表不存在。
ALTER TABLE wms_receipt_order_detail ADD COLUMN serial_nos JSON NULL;
ALTER TABLE wms_shipment_order_detail ADD COLUMN serial_nos JSON NULL;
CREATE TABLE wms_serial_inventory (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, serial_no VARCHAR(128) NOT NULL,
 warehouse_id BIGINT NOT NULL, batch_no VARCHAR(64) NULL, in_stock TINYINT NOT NULL,
 UNIQUE KEY uk_serial (tenant_id, sku_id, serial_no),
 KEY idx_serial_stock (tenant_id, warehouse_id, sku_id, in_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
