-- 仅独立 WMS 库：货架、库位基础资料，不改变库存维度。
CREATE TABLE wms_storage_place (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  type VARCHAR(16) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  UNIQUE KEY uk_place_code (tenant_id, warehouse_id, code),
  KEY idx_place_parent (tenant_id, warehouse_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
