package cn.iocoder.yudao.module.wms.service.inventory;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.yudao.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** 调用方须在同一事务中先锁定仓库+SKU汇总库存。 */
@Service
public class WmsLocationInventoryService {
    @Resource private javax.sql.DataSource dataSource;
    @Resource private WmsBatchInventoryService batchService;
    @Resource private WmsSerialInventoryService serialService;
    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }
    private ServiceException error(String text) { return new ServiceException(1080001995, text); }
    private BigDecimal allocated(Long warehouse, Long sku) {
        return jdbc().query("SELECT quantity FROM wms_location_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? FOR UPDATE",
                (rs, n) -> rs.getBigDecimal(1), tenant(), warehouse, sku).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public void rejectAllocated(Long warehouse, Long sku) {
        if (allocated(warehouse, sku).signum() > 0) throw error("存在库位库存，暂不支持移库或盘点");
    }
    public void checkLocation(cn.iocoder.yudao.module.wms.service.inventory.dto.WmsInventoryCheckReqDTO.Item item, BigDecimal nonBatch) {
        BigDecimal sum = allocated(item.getWarehouseId(), item.getSkuId());
        BigDecimal book;
        if (item.getLocationId() == null) {
            book = nonBatch.subtract(sum);
        } else {
            if (batchService.allocated(item.getWarehouseId(), item.getSkuId()).signum() > 0) throw error("暂不支持库位与批次混用");
            Long valid = jdbc().queryForObject("SELECT COUNT(*) FROM wms_storage_place WHERE tenant_id=? AND warehouse_id=? AND id=? AND type='LOCATION'", Long.class, tenant(), item.getWarehouseId(), item.getLocationId());
            if (valid == null || valid == 0) throw error("库位不存在或不属于当前仓库");
            List<BigDecimal> rows = jdbc().query("SELECT quantity FROM wms_location_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_id=? FOR UPDATE", (rs,n) -> rs.getBigDecimal(1), tenant(), item.getWarehouseId(), item.getSkuId(), item.getLocationId());
            book = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
        }
        if (book.compareTo(item.getQuantity()) != 0) throw error("盘点账面库存已变化");
        if (item.getLocationId() != null) {
            BigDecimal delta = item.getCheckQuantity().subtract(item.getQuantity());
            if (delta.signum() != 0) {
                WmsInventoryChangeReqDTO.Item change = new WmsInventoryChangeReqDTO.Item().setWarehouseId(item.getWarehouseId()).setSkuId(item.getSkuId()).setLocationId(item.getLocationId()).setQuantity(delta);
                apply(null, change, nonBatch);
            }
        }
    }
    public void apply(Integer type, WmsInventoryChangeReqDTO.Item item, BigDecimal before) {
        BigDecimal allocated = allocated(item.getWarehouseId(), item.getSkuId());
        if (item.getLocationId() == null) {
            if (item.getQuantity().signum() < 0 && before.add(item.getQuantity()).compareTo(allocated) < 0) throw error("未分配库位库存不足，请选择库位");
            return;
        }
        Long valid = jdbc().queryForObject("SELECT COUNT(*) FROM wms_storage_place WHERE tenant_id=? AND warehouse_id=? AND id=? AND type='LOCATION'", Long.class, tenant(), item.getWarehouseId(), item.getLocationId());
        if (valid == null || valid == 0) throw error("库位不存在或不属于当前仓库");
        List<BigDecimal> rows = jdbc().query("SELECT quantity FROM wms_location_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_id=? FOR UPDATE", (rs, n) -> rs.getBigDecimal(1), tenant(), item.getWarehouseId(), item.getSkuId(), item.getLocationId());
        if (rows.isEmpty()) {
            if (item.getQuantity().signum() < 0) throw error("库位库存不足");
            jdbc().update("INSERT INTO wms_location_inventory (tenant_id,warehouse_id,sku_id,location_id,quantity) VALUES (?,?,?,?,?)", tenant(), item.getWarehouseId(), item.getSkuId(), item.getLocationId(), item.getQuantity());
        } else {
            BigDecimal next = rows.get(0).add(item.getQuantity());
            if (next.signum() < 0) throw error("库位库存不足");
            jdbc().update("UPDATE wms_location_inventory SET quantity=? WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_id=?", next, tenant(), item.getWarehouseId(), item.getSkuId(), item.getLocationId());
        }
    }
}
