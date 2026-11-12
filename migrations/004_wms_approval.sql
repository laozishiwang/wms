-- 仅独立 WMS 库；审批默认关闭，需单独启用并给角色配置权限。
CREATE TABLE wms_order_approval_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL, order_type VARCHAR(16) NOT NULL, order_id BIGINT NOT NULL,
 action VARCHAR(16) NOT NULL, user_id BIGINT NOT NULL, `comment` VARCHAR(255) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_order_approval (tenant_id, order_type, order_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 按业务角色显式分配以下三个权限；不自动授予现有角色。
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status)
SELECT '提交仓储审批', 'wms:approval:submit', 3, 90, 1348, '', '#', 0
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='wms:approval:submit' AND deleted=0);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status)
SELECT '审批仓储单据', 'wms:approval:approve', 3, 91, 1348, '', '#', 0
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='wms:approval:approve' AND deleted=0);
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, status)
SELECT '查看仓储审批', 'wms:approval:query', 3, 92, 1348, '', '#', 0
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission='wms:approval:query' AND deleted=0);
INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, css_class, remark)
SELECT 1, '待审批', '1', 'wms_order_status', 0, 'warning', '', '单据待审批'
WHERE NOT EXISTS (SELECT 1 FROM system_dict_data WHERE dict_type='wms_order_status' AND value='1' AND deleted=0);
