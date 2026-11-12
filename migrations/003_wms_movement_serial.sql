-- 仅用于独立 WMS 库；执行前备份并确认列不存在。
ALTER TABLE wms_movement_order_detail ADD COLUMN serial_nos JSON NULL;
