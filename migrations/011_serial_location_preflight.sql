-- 只读检查：现有序列号尚无库位，迁移只保留为未分配库位，不推测位置。
-- 执行前应暂停 WMS 写入并备份；任何返回行均须核对。
SELECT s.tenant_id,s.warehouse_id,s.sku_id,COALESCE(s.batch_no,'') AS batch_key,
       COUNT(*) AS serial_count,COALESCE(d.quantity,0) AS unassigned_quantity
FROM wms_serial_inventory s
LEFT JOIN wms_inventory_dimension d ON d.tenant_id=s.tenant_id AND d.warehouse_id=s.warehouse_id
 AND d.sku_id=s.sku_id AND d.location_key=0 AND d.batch_key=COALESCE(s.batch_no,'')
WHERE s.in_stock=1
GROUP BY s.tenant_id,s.warehouse_id,s.sku_id,COALESCE(s.batch_no,''),d.quantity
HAVING serial_count > unassigned_quantity;

SELECT tenant_id,sku_id,serial_no,warehouse_id,batch_no,in_stock
FROM wms_serial_inventory
WHERE in_stock NOT IN (0,1) OR serial_no IS NULL OR TRIM(serial_no)=''
   OR (batch_no IS NOT NULL AND TRIM(batch_no)='');
