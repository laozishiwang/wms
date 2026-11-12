-- 独立 WMS：暂停写入、备份并通过 011_serial_location_preflight.sql 后执行。
-- 历史序列号位置无法推断，保持 NULL（未分配），不要根据仓库库存猜测。
ALTER TABLE wms_serial_inventory ADD COLUMN location_id BIGINT NULL;
