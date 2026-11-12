-- 仅 WMS 数据库，迁移前备份。
ALTER TABLE wms_movement_order_detail ADD COLUMN source_location_id BIGINT NULL, ADD COLUMN target_location_id BIGINT NULL;
