-- 只读对账。新台账建好、回填后，在 WMS 写入暂停时执行；任何返回行均须核对。
-- 汇总库存：包括新台账中找不到活动汇总行的孤儿。
SELECT i.tenant_id, i.warehouse_id, i.sku_id, i.quantity AS expected, COALESCE(d.quantity, 0) AS actual
FROM wms_inventory i
LEFT JOIN (SELECT tenant_id, warehouse_id, sku_id, SUM(quantity) quantity FROM wms_inventory_dimension GROUP BY tenant_id, warehouse_id, sku_id) d
  ON d.tenant_id=i.tenant_id AND d.warehouse_id=i.warehouse_id AND d.sku_id=i.sku_id
WHERE i.deleted=0 AND i.quantity<>COALESCE(d.quantity,0);
SELECT d.tenant_id,d.warehouse_id,d.sku_id,SUM(d.quantity) AS orphan_quantity
FROM wms_inventory_dimension d
LEFT JOIN wms_inventory i ON i.tenant_id=d.tenant_id AND i.warehouse_id=d.warehouse_id AND i.sku_id=d.sku_id AND i.deleted=0
WHERE i.id IS NULL GROUP BY d.tenant_id,d.warehouse_id,d.sku_id;

-- 对每个旧库位余额核对交叉台账投影，并检查交叉台账里多出的库位。
SELECT l.tenant_id,l.warehouse_id,l.sku_id,l.location_id,l.quantity AS expected,COALESCE(d.quantity,0) AS actual
FROM wms_location_inventory l
LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,location_key,SUM(quantity) quantity FROM wms_inventory_dimension WHERE location_key<>0 GROUP BY tenant_id,warehouse_id,sku_id,location_key) d
  ON d.tenant_id=l.tenant_id AND d.warehouse_id=l.warehouse_id AND d.sku_id=l.sku_id AND d.location_key=l.location_id
WHERE l.quantity<>COALESCE(d.quantity,0);
SELECT d.tenant_id,d.warehouse_id,d.sku_id,d.location_key,SUM(d.quantity) AS orphan_quantity
FROM wms_inventory_dimension d
LEFT JOIN wms_location_inventory l ON l.tenant_id=d.tenant_id AND l.warehouse_id=d.warehouse_id AND l.sku_id=d.sku_id AND l.location_id=d.location_key
WHERE d.location_key<>0 AND l.id IS NULL GROUP BY d.tenant_id,d.warehouse_id,d.sku_id,d.location_key;

-- 批次投影同样双向检查；零库位代表未分配，并不是丢失数据。
SELECT b.tenant_id,b.warehouse_id,b.sku_id,b.batch_no,b.quantity AS expected,COALESCE(d.quantity,0) AS actual
FROM wms_batch_inventory b
LEFT JOIN (SELECT tenant_id,warehouse_id,sku_id,batch_key,SUM(quantity) quantity FROM wms_inventory_dimension WHERE batch_key<>'' GROUP BY tenant_id,warehouse_id,sku_id,batch_key) d
  ON d.tenant_id=b.tenant_id AND d.warehouse_id=b.warehouse_id AND d.sku_id=b.sku_id AND d.batch_key=b.batch_no
WHERE b.quantity<>COALESCE(d.quantity,0);
SELECT d.tenant_id,d.warehouse_id,d.sku_id,d.batch_key,SUM(d.quantity) AS orphan_quantity
FROM wms_inventory_dimension d
LEFT JOIN wms_batch_inventory b ON b.tenant_id=d.tenant_id AND b.warehouse_id=d.warehouse_id AND b.sku_id=d.sku_id AND b.batch_no=d.batch_key
WHERE d.batch_key<>'' AND b.id IS NULL GROUP BY d.tenant_id,d.warehouse_id,d.sku_id,d.batch_key;
SELECT tenant_id,warehouse_id,sku_id,location_key,batch_key,quantity FROM wms_inventory_dimension WHERE quantity<0;
