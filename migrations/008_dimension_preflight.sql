-- 只读预检：仅对独立 WMS 库执行；任何返回行均须先核对，不能自动回填。
-- 现有版本不允许同仓库/SKU 同时存在正的库位与批次余额。
SELECT i.tenant_id, i.warehouse_id, i.sku_id, i.quantity AS total_quantity,
       COALESCE(l.quantity, 0) AS location_quantity, COALESCE(b.quantity, 0) AS batch_quantity
FROM wms_inventory i
LEFT JOIN (SELECT tenant_id, warehouse_id, sku_id, SUM(quantity) quantity FROM wms_location_inventory GROUP BY tenant_id, warehouse_id, sku_id) l
  ON l.tenant_id=i.tenant_id AND l.warehouse_id=i.warehouse_id AND l.sku_id=i.sku_id
LEFT JOIN (SELECT tenant_id, warehouse_id, sku_id, SUM(quantity) quantity FROM wms_batch_inventory GROUP BY tenant_id, warehouse_id, sku_id) b
  ON b.tenant_id=i.tenant_id AND b.warehouse_id=i.warehouse_id AND b.sku_id=i.sku_id
WHERE i.deleted=0 AND (i.quantity<0 OR COALESCE(l.quantity,0)<0 OR COALESCE(b.quantity,0)<0
  OR COALESCE(l.quantity,0)+COALESCE(b.quantity,0)>i.quantity
  OR (COALESCE(l.quantity,0)>0 AND COALESCE(b.quantity,0)>0));

-- 正余额不得没有活动的汇总行；不得存在负数余额或无效库位。
SELECT 'location' AS problem, l.tenant_id,l.warehouse_id,l.sku_id,l.location_id,l.quantity
FROM wms_location_inventory l
LEFT JOIN wms_inventory i ON i.tenant_id=l.tenant_id AND i.warehouse_id=l.warehouse_id AND i.sku_id=l.sku_id AND i.deleted=0
LEFT JOIN wms_storage_place p ON p.id=l.location_id AND p.tenant_id=l.tenant_id AND p.warehouse_id=l.warehouse_id AND p.type='LOCATION'
WHERE l.quantity<0 OR l.location_id<=0 OR i.id IS NULL OR p.id IS NULL;
SELECT 'batch' AS problem,b.tenant_id,b.warehouse_id,b.sku_id,b.batch_no,b.quantity
FROM wms_batch_inventory b
LEFT JOIN wms_inventory i ON i.tenant_id=b.tenant_id AND i.warehouse_id=b.warehouse_id AND i.sku_id=b.sku_id AND i.deleted=0
WHERE b.quantity<0 OR b.batch_no IS NULL OR TRIM(b.batch_no)='' OR i.id IS NULL;

-- 序列号必须使用有效状态，空白批次与未分批语义不能混淆。
SELECT 'invalid_serial' AS problem, s.tenant_id,s.warehouse_id,s.sku_id,s.id,s.serial_no,s.batch_no,s.in_stock
FROM wms_serial_inventory s
WHERE s.in_stock NOT IN (0,1) OR TRIM(s.serial_no)='' OR (s.batch_no IS NOT NULL AND TRIM(s.batch_no)='');

-- 每个在库序列号必须有有效汇总行；按批次计数不得超过该批次余额。
SELECT 'serial_without_summary' AS problem,s.tenant_id,s.warehouse_id,s.sku_id,COUNT(*) AS serial_count
FROM wms_serial_inventory s
LEFT JOIN wms_inventory i ON i.tenant_id=s.tenant_id AND i.warehouse_id=s.warehouse_id AND i.sku_id=s.sku_id AND i.deleted=0
WHERE s.in_stock=1 AND i.id IS NULL GROUP BY s.tenant_id,s.warehouse_id,s.sku_id;
SELECT 'serial_exceeds_batch' AS problem,s.tenant_id,s.warehouse_id,s.sku_id,s.batch_no,COUNT(*) AS serial_count,COALESCE(b.quantity,0) AS batch_quantity
FROM wms_serial_inventory s
LEFT JOIN wms_batch_inventory b ON b.tenant_id=s.tenant_id AND b.warehouse_id=s.warehouse_id AND b.sku_id=s.sku_id AND b.batch_no=s.batch_no
WHERE s.in_stock=1 AND s.batch_no IS NOT NULL
GROUP BY s.tenant_id,s.warehouse_id,s.sku_id,s.batch_no,b.quantity
HAVING COUNT(*)>COALESCE(b.quantity,0);

-- 无批次的在库序列号不得超过无批次、无库位的库存。
SELECT 'serial_exceeds_unassigned' AS problem,s.tenant_id,s.warehouse_id,s.sku_id,COUNT(*) AS serial_count,
       i.quantity-COALESCE(b.quantity,0)-COALESCE(l.quantity,0) AS unassigned_quantity
FROM wms_serial_inventory s
JOIN wms_inventory i ON i.tenant_id=s.tenant_id AND i.warehouse_id=s.warehouse_id AND i.sku_id=s.sku_id AND i.deleted=0
LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) quantity FROM wms_batch_inventory GROUP BY tenant_id,warehouse_id,sku_id) b ON b.tenant_id=s.tenant_id AND b.warehouse_id=s.warehouse_id AND b.sku_id=s.sku_id
LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,SUM(quantity) quantity FROM wms_location_inventory GROUP BY tenant_id,warehouse_id,sku_id) l ON l.tenant_id=s.tenant_id AND l.warehouse_id=s.warehouse_id AND l.sku_id=s.sku_id
WHERE s.in_stock=1 AND s.batch_no IS NULL
GROUP BY s.tenant_id,s.warehouse_id,s.sku_id,i.quantity,b.quantity,l.quantity
HAVING serial_count > unassigned_quantity;
