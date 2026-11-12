-- 保留菜单 ID、权限、路由和用户授权，仅更新基础数据菜单名称。
UPDATE system_menu SET name = '物品品牌'
WHERE component = 'wms/md/item/brand/index' AND name = '商品品牌';
UPDATE system_menu SET name = '物品分类'
WHERE component = 'wms/md/item/category/index' AND name = '商品分类';
UPDATE system_menu SET name = '物品管理'
WHERE component = 'wms/md/item/index' AND name = '商品管理';
