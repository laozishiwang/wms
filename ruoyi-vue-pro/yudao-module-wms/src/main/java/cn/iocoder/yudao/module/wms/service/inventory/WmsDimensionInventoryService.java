package cn.iocoder.yudao.module.wms.service.inventory;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

/** 交叉库存台账。写入调用方须在同一事务先锁定仓库和 SKU 的汇总库存。 */
@Service
public class WmsDimensionInventoryService {
    public record LocationBalance(Long locationId, String code, String name, BigDecimal quantity) {}
    public record DimensionBalance(Long locationId, String batchNo, BigDecimal quantity) {}
    @Resource private DataSource dataSource;

    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }
    private long location(Long id) { return id == null ? 0 : id; }
    private String batch(String value) { return value == null ? "" : value; }

    public boolean hasCross(Long warehouseId, Long skuId) {
        Long count = jdbc().queryForObject("SELECT COUNT(*) FROM wms_inventory_dimension WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_key<>0 AND batch_key<>'' AND quantity>0", Long.class, tenant(), warehouseId, skuId);
        return count != null && count > 0;
    }

    public BigDecimal lockQuantity(Long warehouseId, Long skuId, Long locationId, String batchNo) {
        List<BigDecimal> rows = jdbc().query("SELECT quantity FROM wms_inventory_dimension WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_key=? AND batch_key=? FOR UPDATE",
                (rs, n) -> rs.getBigDecimal(1), tenant(), warehouseId, skuId, location(locationId), batch(batchNo));
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    public void change(Long warehouseId, Long skuId, Long locationId, String batchNo, BigDecimal delta) {
        BigDecimal next = lockQuantity(warehouseId, skuId, locationId, batchNo).add(delta);
        if (next.signum() < 0) throw new ServiceException(1080001995, "所选库位和批次库存不足");
        jdbc().update("INSERT INTO wms_inventory_dimension (tenant_id,warehouse_id,sku_id,location_key,batch_key,quantity) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)",
                tenant(), warehouseId, skuId, location(locationId), batch(batchNo), next);
    }

    public List<LocationBalance> listLocations(Long warehouseId, Long skuId, String batchNo) {
        return jdbc().query("SELECT d.location_key,p.code,p.name,d.quantity FROM wms_inventory_dimension d LEFT JOIN wms_storage_place p ON p.id=d.location_key AND p.tenant_id=d.tenant_id AND p.warehouse_id=d.warehouse_id WHERE d.tenant_id=? AND d.warehouse_id=? AND d.sku_id=? AND d.batch_key=? AND d.quantity>0 ORDER BY d.location_key",
                (rs, n) -> new LocationBalance(rs.getLong(1) == 0 ? null : rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), tenant(), warehouseId, skuId, batch(batchNo));
    }
    public List<DimensionBalance> listDimensions(Long warehouseId, Long skuId) {
        return jdbc().query("SELECT location_key,batch_key,quantity FROM wms_inventory_dimension WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND quantity>0 ORDER BY location_key,batch_key", (rs, n) -> new DimensionBalance(rs.getLong(1) == 0 ? null : rs.getLong(1), rs.getString(2).isEmpty() ? null : rs.getString(2), rs.getBigDecimal(3)), tenant(), warehouseId, skuId);
    }
}
