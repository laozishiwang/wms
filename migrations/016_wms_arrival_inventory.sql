-- 待完整接入四类单据并在隔离库对账后执行。0 表示历史未分配到货记录。
CREATE TABLE IF NOT EXISTS wms_arrival_inventory (
  tenant_id bigint NOT NULL,
  warehouse_id bigint NOT NULL,
  sku_id bigint NOT NULL,
  location_key bigint NOT NULL DEFAULT 0,
  batch_key varchar(64) NOT NULL DEFAULT '',
  arrival_key bigint NOT NULL DEFAULT 0 COMMENT '入库明细 ID；0 为未分配',
  quantity decimal(20,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, warehouse_id, sku_id, location_key, batch_key, arrival_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到货维度库存余额';

-- 仅在停写并完成一致性预检后执行；重复执行不会覆盖已发生的业务。
INSERT INTO wms_arrival_inventory (tenant_id, warehouse_id, sku_id, location_key, batch_key, arrival_key, quantity)
SELECT d.tenant_id, d.warehouse_id, d.sku_id, d.location_key, d.batch_key, 0, d.quantity
FROM wms_inventory_dimension d
WHERE NOT EXISTS (
  SELECT 1 FROM wms_arrival_inventory a
  WHERE a.tenant_id=d.tenant_id AND a.warehouse_id=d.warehouse_id AND a.sku_id=d.sku_id
    AND a.location_key=d.location_key AND a.batch_key=d.batch_key
);

ALTER TABLE wms_shipment_order_detail ADD COLUMN arrival_id BIGINT NULL;

ALTER TABLE wms_movement_order_detail ADD COLUMN arrival_id BIGINT NULL;
ALTER TABLE wms_check_order_detail ADD COLUMN arrival_id BIGINT NULL;
ALTER TABLE wms_serial_inventory ADD COLUMN arrival_id BIGINT NULL;
