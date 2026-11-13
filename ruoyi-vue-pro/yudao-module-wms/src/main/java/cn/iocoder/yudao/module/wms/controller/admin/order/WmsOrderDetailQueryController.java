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
        @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime, @RequestParam(required = false) String optionField, @RequestParam(required = false) String optionSearch) {
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
        if (!"no".equals(optionField) && no != null && !no.isBlank()) { where.append(" AND o.no = :no"); p.addValue("no", no); }
        if (!"skuCode".equals(optionField) && skuCode != null && !skuCode.isBlank()) { where.append(" AND s.code = :skuCode"); p.addValue("skuCode", skuCode); }
        if (!"itemName".equals(optionField) && itemName != null && !itemName.isBlank()) { where.append(" AND i.name = :itemName"); p.addValue("itemName", itemName); }
        if (!"warehouseId".equals(optionField) && warehouseId != null) { where.append(" AND d.warehouse_id=:warehouseId"); p.addValue("warehouseId",warehouseId); }
        if (status != null) { where.append(" AND o.status=:status"); p.addValue("status",status); }
        if (type != null) { where.append(" AND o.type=:type"); p.addValue("type",type); }
        if (!"locationKeyword".equals(optionField) && locationKeyword != null && !locationKeyword.isBlank()) { where.append(" AND loc.id=:locationKeyword"); p.addValue("locationKeyword", Long.valueOf(locationKeyword)); }
        if (!"batchNo".equals(optionField) && batchNo != null && !batchNo.isBlank()) { where.append(" AND d.batch_no = :batchNo"); p.addValue("batchNo",batchNo); }
        for (Map.Entry<String,String> e : Map.of("palletNo",palletNo == null ? "" : palletNo,"boxNo",boxNo == null ? "" : boxNo,"rollNo",rollNo == null ? "" : rollNo).entrySet()) {
            if (!e.getKey().equals(optionField) && !e.getValue().isBlank()) { String col = e.getKey().replace("No", "_no"); where.append(" AND ").append(arrival).append('.').append(col).append(" = :").append(e.getKey()); p.addValue(e.getKey(),e.getValue()); }
        }
        if (startTime != null && !startTime.isBlank()) { where.append(" AND o.order_time>=:startTime"); p.addValue("startTime",startTime); }
        if (endTime != null && !endTime.isBlank()) { where.append(" AND o.order_time<=:endTime"); p.addValue("endTime",endTime); }
        if (optionField != null && !optionField.isBlank()) {
            Map<String,String> fields = Map.of("warehouseId", "w.id", "no", "o.no", "skuCode", "s.code", "itemName", "i.name", "locationKeyword", "loc.id", "batchNo", "d.batch_no", "palletNo", arrival + ".pallet_no", "boxNo", arrival + ".box_no", "rollNo", arrival + ".roll_no");
            String col = fields.get(optionField);
            if (col == null) throw new IllegalArgumentException("Unknown option field");
            String label = "locationKeyword".equals(optionField) ? "CONCAT(loc.code, ' ', loc.name)" : ("warehouseId".equals(optionField) ? "w.name" : col);
            where.append(" AND ").append(col).append(" IS NOT NULL AND ").append(label).append(" <> ''");
            if (optionSearch != null && !optionSearch.isBlank()) { where.append(" AND ").append(label).append(" LIKE :optionSearch"); p.addValue("optionSearch", "%" + optionSearch + "%"); }
            List<Map<String,Object>> options = jdbc.queryForList("SELECT DISTINCT " + col + " AS value," + label + " AS label" + where + " ORDER BY label LIMIT 100", p);
            return CommonResult.success(new PageResult<>(options, (long) options.size()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)"+where,p,Long.class);
        int size=Math.min(Math.max(pageSize,1),100); p.addValue("limit",size).addValue("offset",(long)(Math.max(pageNo,1)-1)*size);
        String select = "SELECT d.id,d.order_id AS orderId,o.no,o.type,o.status,o.order_time AS orderTime,d.warehouse_id AS warehouseId,w.name AS warehouseName,s.code AS skuCode,s.name AS skuName,i.name AS itemName,d.location_id AS locationId,loc.code AS locationCode,loc.name AS locationName,d.batch_no AS batchNo," + arrival + ".pallet_no AS palletNo,"+arrival+".box_no AS boxNo,"+arrival+".roll_no AS rollNo,"+(receipt ? "d.id" : "d.arrival_id")+" AS arrivalId,d.quantity,d.price,d.total_price AS totalPrice,d.serial_nos AS serialNos,d.production_date AS productionDate,d.expiration_date AS expirationDate,o.biz_order_no AS bizOrderNo,o.remark,"+arrival+".net_length AS netLength,"+arrival+".gross_length AS grossLength,"+arrival+".net_weight AS netWeight,"+arrival+".gross_weight AS grossWeight,"+arrival+".length AS length,"+arrival+".width AS width,"+arrival+".height AS height,"+arrival+".sales_price AS salesPrice";
        return CommonResult.success(new PageResult<>(jdbc.queryForList(select+where+" ORDER BY d.id DESC LIMIT :limit OFFSET :offset",p), total));
    }
}
