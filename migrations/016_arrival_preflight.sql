-- 只读检查：必须连接 WMS 独立库；上线前暂停 WMS 写入并备份。
-- 各项结果均应为 0；任一非零不得直接执行 016。
SELECT COUNT(*) AS existing_arrival_table FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'wms_arrival_inventory';
SELECT COUNT(*) AS existing_arrival_columns FROM information_schema.columns
 WHERE table_schema = DATABASE() AND column_name = 'arrival_id'
 AND table_name IN ('wms_shipment_order_detail','wms_movement_order_detail','wms_check_order_detail','wms_serial_inventory');
SELECT COUNT(*) AS negative_dimension_balances FROM wms_inventory_dimension WHERE quantity < 0;
SELECT COUNT(*) AS serials_without_dimension FROM wms_serial_inventory s
 LEFT JOIN wms_inventory_dimension d ON d.tenant_id=s.tenant_id AND d.warehouse_id=s.warehouse_id
 AND d.sku_id=s.sku_id AND d.location_key=COALESCE(s.location_id,0)
 AND d.batch_key=COALESCE(s.batch_no,'')
 WHERE s.in_stock=1 AND d.sku_id IS NULL;
SELECT COUNT(*) AS serial_groups_exceeding_balance FROM (
 SELECT s.tenant_id, s.warehouse_id, s.sku_id, COALESCE(s.location_id,0) AS location_key,
        COALESCE(s.batch_no,'') AS batch_key, COUNT(*) AS serial_count
 FROM wms_serial_inventory s WHERE s.in_stock=1
 GROUP BY s.tenant_id,s.warehouse_id,s.sku_id,COALESCE(s.location_id,0),COALESCE(s.batch_no,'')
) s JOIN wms_inventory_dimension d ON d.tenant_id=s.tenant_id AND d.warehouse_id=s.warehouse_id
 AND d.sku_id=s.sku_id AND d.location_key=s.location_key AND d.batch_key=s.batch_key
 WHERE s.serial_count > d.quantity;
