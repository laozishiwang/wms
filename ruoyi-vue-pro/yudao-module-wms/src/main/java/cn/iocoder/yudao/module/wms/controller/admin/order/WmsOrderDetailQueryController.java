package cn.iocoder.yudao.module.wms.controller.admin.order;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/wms/order-detail-query")
public class WmsOrderDetailQueryController {
    @Resource private NamedParameterJdbcTemplate jdbc;

    @GetMapping("/page")
    @PreAuthorize("(#direction == 'receipt' and @ss.hasPermission('wms:receipt-order:query')) or (#direction == 'shipment' and @ss.hasPermission('wms:shipment-order:query'))")
    public CommonResult<PageResult<Map<String, Object>>> page(@RequestParam String direction,
        @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(required = false) String no, @RequestParam(required = false) String skuCode,
        @RequestParam(required = false) String itemName, @RequestParam(required = false) Long warehouseId,
        @RequestParam(required = false) String locationKeyword, @RequestParam(required = false) String batchNo, @RequestParam(required = false) String palletNo,
        @RequestParam(required = false) String boxNo, @RequestParam(required = false) String rollNo,
        @RequestParam(required = false) Integer status, @RequestParam(required = false) Integer type,
        @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime) {
        boolean receipt = "receipt".equals(direction);
        String prefix = receipt ? "receipt" : "shipment";
        String arrival = receipt ? "d" : "a";
        String joins = " FROM wms_" + prefix + "_order_detail d JOIN wms_" + prefix + "_order o ON o.id=d.order_id AND o.tenant_id=d.tenant_id AND o.deleted=0 "
          + " LEFT JOIN wms_item_sku s ON s.id=d.sku_id AND s.tenant_id=d.tenant_id AND s.deleted=0"
          + " LEFT JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=d.tenant_id AND i.deleted=0"
          + " LEFT JOIN wms_warehouse w ON w.id=d.warehouse_id AND w.tenant_id=d.tenant_id AND w.deleted=0"
          + " LEFT JOIN wms_storage_place loc ON loc.id=d.location_id AND loc.tenant_id=d.tenant_id AND loc.warehouse_id=d.warehouse_id"
          + (receipt ? "" : " LEFT JOIN wms_receipt_order_detail a ON a.id=d.arrival_id AND a.tenant_id=d.tenant_id AND a.deleted=0")
          + " WHERE d.tenant_id=:tenant AND d.deleted=0";
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenant", TenantContextHolder.getRequiredTenantId());
        StringBuilder where = new StringBuilder(joins);
        if (no != null && !no.isBlank()) { where.append(" AND o.no LIKE :no"); p.addValue("no", "%"+no+"%"); }
        if (skuCode != null && !skuCode.isBlank()) { where.append(" AND s.code LIKE :skuCode"); p.addValue("skuCode", "%"+skuCode+"%"); }
        if (itemName != null && !itemName.isBlank()) { where.append(" AND i.name LIKE :itemName"); p.addValue("itemName", "%"+itemName+"%"); }
        if (warehouseId != null) { where.append(" AND d.warehouse_id=:warehouseId"); p.addValue("warehouseId",warehouseId); }
        if (status != null) { where.append(" AND o.status=:status"); p.addValue("status",status); }
        if (type != null) { where.append(" AND o.type=:type"); p.addValue("type",type); }
        if (locationKeyword != null && !locationKeyword.isBlank()) { where.append(" AND (loc.code LIKE :locationKeyword OR loc.name LIKE :locationKeyword)"); p.addValue("locationKeyword", "%"+locationKeyword+"%"); }
        if (batchNo != null && !batchNo.isBlank()) { where.append(" AND d.batch_no LIKE :batchNo"); p.addValue("batchNo","%"+batchNo+"%"); }
        for (Map.Entry<String,String> e : Map.of("palletNo",palletNo == null ? "" : palletNo,"boxNo",boxNo == null ? "" : boxNo,"rollNo",rollNo == null ? "" : rollNo).entrySet()) {
            if (!e.getValue().isBlank()) { String col = e.getKey().replace("No", "_no"); where.append(" AND ").append(arrival).append('.').append(col).append(" LIKE :").append(e.getKey()); p.addValue(e.getKey(),"%"+e.getValue()+"%"); }
        }
        if (startTime != null && !startTime.isBlank()) { where.append(" AND o.order_time>=:startTime"); p.addValue("startTime",startTime); }
        if (endTime != null && !endTime.isBlank()) { where.append(" AND o.order_time<=:endTime"); p.addValue("endTime",endTime); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)"+where,p,Long.class);
        int size=Math.min(Math.max(pageSize,1),100); p.addValue("limit",size).addValue("offset",(long)(Math.max(pageNo,1)-1)*size);
        String select = "SELECT d.id,d.order_id AS orderId,o.no,o.type,o.status,o.order_time AS orderTime,d.warehouse_id AS warehouseId,w.name AS warehouseName,s.code AS skuCode,s.name AS skuName,i.name AS itemName,d.location_id AS locationId,loc.code AS locationCode,loc.name AS locationName,d.batch_no AS batchNo," + arrival + ".pallet_no AS palletNo,"+arrival+".box_no AS boxNo,"+arrival+".roll_no AS rollNo,"+(receipt ? "d.id" : "d.arrival_id")+" AS arrivalId,d.quantity,d.price,d.total_price AS totalPrice";
        return CommonResult.success(new PageResult<>(jdbc.queryForList(select+where+" ORDER BY d.id DESC LIMIT :limit OFFSET :offset",p), total));
    }
}
