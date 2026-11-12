-- 仅新增菜单；字典读写仍使用 system:dict:* 权限。
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show)
SELECT '出入库类型','',2,90,w.parent_id,'order-type','ep:collection','wms/md/order-type/index','WmsOrderType',0,b'1',b'1',b'1'
FROM system_menu w
WHERE w.component='wms/md/warehouse/index' AND w.deleted=b'0'
  AND NOT EXISTS (SELECT 1 FROM system_menu x WHERE x.component='wms/md/order-type/index' AND x.deleted=b'0');
