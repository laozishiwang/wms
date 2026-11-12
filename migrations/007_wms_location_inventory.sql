-- 仅用于独立 WMS；先备份并核对表及字段不存在。
ALTER TABLE wms_receipt_order_detail ADD COLUMN location_id BIGINT NULL;
ALTER TABLE wms_shipment_order_detail ADD COLUMN location_id BIGINT NULL;
CREATE TABLE wms_location_inventory (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 location_id BIGINT NOT NULL, quantity DECIMAL(20,2) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_location_inventory (tenant_id, warehouse_id, sku_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
