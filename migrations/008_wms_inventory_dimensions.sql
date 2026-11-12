-- 仅独立 WMS。必须暂停写入、先备份；本迁移会拒绝无法确定交叉维度的旧数据。
-- 0 库位及空批次均代表未分配；既有批次或库位的实际名称不被推断。
CREATE TABLE wms_inventory_dimension (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  location_key BIGINT NOT NULL DEFAULT 0, batch_key VARCHAR(64) NOT NULL DEFAULT '',
  quantity DECIMAL(20,2) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_wms_inventory_dimension (tenant_id, warehouse_id, sku_id, location_key, batch_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER $$
CREATE PROCEDURE wms_backfill_inventory_dimensions()
BEGIN
  IF EXISTS (
    SELECT 1 FROM wms_inventory i
    LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) q FROM wms_batch_inventory GROUP BY tenant_id,warehouse_id,sku_id) b
      ON b.tenant_id=i.tenant_id AND b.warehouse_id=i.warehouse_id AND b.sku_id=i.sku_id
    LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) q FROM wms_location_inventory GROUP BY tenant_id,warehouse_id,sku_id) l
      ON l.tenant_id=i.tenant_id AND l.warehouse_id=i.warehouse_id AND l.sku_id=i.sku_id
    WHERE i.deleted=0 AND (i.quantity < 0 OR (COALESCE(b.q,0)>0 AND COALESCE(l.q,0)>0)
      OR COALESCE(b.q,0)+COALESCE(l.q,0)>i.quantity)
  ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='库存投影不一致或批次与库位交叉关系未知，禁止回填'; END IF;
  IF EXISTS (SELECT 1 FROM wms_batch_inventory WHERE quantity<0 OR batch_no IS NULL OR TRIM(batch_no)='')
     OR EXISTS (SELECT 1 FROM wms_location_inventory WHERE quantity<0 OR location_id<=0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='批次或库位余额无效，禁止回填';
  END IF;
  IF EXISTS (SELECT 1 FROM wms_batch_inventory b LEFT JOIN wms_inventory i ON i.tenant_id=b.tenant_id AND i.warehouse_id=b.warehouse_id AND i.sku_id=b.sku_id AND i.deleted=0 WHERE b.quantity>0 AND i.id IS NULL)
     OR EXISTS (SELECT 1 FROM wms_location_inventory l LEFT JOIN wms_inventory i ON i.tenant_id=l.tenant_id AND i.warehouse_id=l.warehouse_id AND i.sku_id=l.sku_id AND i.deleted=0 WHERE l.quantity>0 AND i.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在无汇总库存的明细，禁止回填';
  END IF;
  START TRANSACTION;
  INSERT INTO wms_inventory_dimension (tenant_id,warehouse_id,sku_id,location_key,batch_key,quantity)
    SELECT tenant_id,warehouse_id,sku_id,location_id,'',quantity FROM wms_location_inventory WHERE quantity>0;
  INSERT INTO wms_inventory_dimension (tenant_id,warehouse_id,sku_id,location_key,batch_key,quantity)
    SELECT tenant_id,warehouse_id,sku_id,0,batch_no,quantity FROM wms_batch_inventory WHERE quantity>0;
  INSERT INTO wms_inventory_dimension (tenant_id,warehouse_id,sku_id,location_key,batch_key,quantity)
    SELECT i.tenant_id,i.warehouse_id,i.sku_id,0,'',i.quantity-COALESCE(b.q,0)-COALESCE(l.q,0)
    FROM wms_inventory i
    LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) q FROM wms_batch_inventory GROUP BY tenant_id,warehouse_id,sku_id) b
      ON b.tenant_id=i.tenant_id AND b.warehouse_id=i.warehouse_id AND b.sku_id=i.sku_id
    LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) q FROM wms_location_inventory GROUP BY tenant_id,warehouse_id,sku_id) l
      ON l.tenant_id=i.tenant_id AND l.warehouse_id=i.warehouse_id AND l.sku_id=i.sku_id
    WHERE i.deleted=0 AND i.quantity-COALESCE(b.q,0)-COALESCE(l.q,0)>0;
  COMMIT;
END$$
DELIMITER ;
CALL wms_backfill_inventory_dimensions();
DROP PROCEDURE wms_backfill_inventory_dimensions;
