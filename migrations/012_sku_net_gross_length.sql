-- 独立 WMS 库；仅新增可空字段，保留现有尺寸和商品数据。
ALTER TABLE wms_item_sku ADD COLUMN net_length DECIMAL(10,1) NULL, ADD COLUMN gross_length DECIMAL(10,1) NULL;
