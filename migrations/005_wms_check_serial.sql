-- 仅用于独立 WMS 数据库；备份并核查列不存在后执行。
ALTER TABLE wms_check_order_detail ADD COLUMN book_serial_nos JSON NULL, ADD COLUMN actual_serial_nos JSON NULL;
