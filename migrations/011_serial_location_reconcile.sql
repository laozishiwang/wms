-- 只读对账：在库序列号数不得超过所属交叉维度余额。
SELECT s.tenant_id,s.warehouse_id,s.sku_id,COALESCE(s.location_id,0) AS location_key,
       COALESCE(s.batch_no,'') AS batch_key,COUNT(*) AS serial_count,COALESCE(d.quantity,0) AS dimension_quantity
FROM wms_serial_inventory s
LEFT JOIN wms_inventory_dimension d ON d.tenant_id=s.tenant_id AND d.warehouse_id=s.warehouse_id
 AND d.sku_id=s.sku_id AND d.location_key=COALESCE(s.location_id,0) AND d.batch_key=COALESCE(s.batch_no,'')
WHERE s.in_stock=1
GROUP BY s.tenant_id,s.warehouse_id,s.sku_id,COALESCE(s.location_id,0),COALESCE(s.batch_no,''),d.quantity
HAVING serial_count > dimension_quantity;

SELECT s.tenant_id,s.warehouse_id,s.sku_id,s.serial_no,s.location_id
FROM wms_serial_inventory s
LEFT JOIN wms_storage_place p ON p.id=s.location_id AND p.tenant_id=s.tenant_id
 AND p.warehouse_id=s.warehouse_id AND p.type='LOCATION'
WHERE s.in_stock=1 AND s.location_id IS NOT NULL AND p.id IS NULL;
