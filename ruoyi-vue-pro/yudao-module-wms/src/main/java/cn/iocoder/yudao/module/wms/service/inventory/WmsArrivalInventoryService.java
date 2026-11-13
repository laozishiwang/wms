package cn.iocoder.yudao.module.wms.service.inventory;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

/** 到货维度余额；调用方必须在已有库存事务中持有仓库与规格的库存锁。 */
@Service
public class WmsArrivalInventoryService {
    @Resource private DataSource dataSource;

    public record Balance(Long warehouseId, Long skuId, Long locationId, String batchNo,
                          Long arrivalId, BigDecimal quantity, String palletNo, String boxNo,
                          String rollNo, BigDecimal netLength, BigDecimal grossLength,
                          BigDecimal netWeight, BigDecimal grossWeight, BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal costPrice, BigDecimal salesPrice) {}

    /** 仅供后续接入；空 arrivalId 表示历史未分配。 */
    public List<Balance> list(Long warehouseId, Long skuId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return new JdbcTemplate(dataSource).query("SELECT a.warehouse_id,a.sku_id,a.location_key,a.batch_key,a.arrival_key,a.quantity,r.pallet_no,r.box_no,r.roll_no,r.net_length,r.gross_length,r.net_weight,r.gross_weight,r.length,r.width,r.height,r.price,r.sales_price FROM wms_arrival_inventory a LEFT JOIN wms_receipt_order_detail r ON r.id=a.arrival_key AND r.tenant_id=a.tenant_id WHERE a.tenant_id=? AND a.warehouse_id=? AND a.sku_id=? AND a.quantity>0 ORDER BY a.arrival_key,a.location_key", (rs, n) -> new Balance(rs.getLong(1), rs.getLong(2), rs.getLong(3)==0 ? null : rs.getLong(3), rs.getString(4).isEmpty() ? null : rs.getString(4), rs.getLong(5)==0 ? null : rs.getLong(5), rs.getBigDecimal(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBigDecimal(14), rs.getBigDecimal(15), rs.getBigDecimal(16), rs.getBigDecimal(17), rs.getBigDecimal(18)), tenantId == null ? 0 : tenantId, warehouseId, skuId);
    }

    public BigDecimal lockQuantity(Long warehouseId, Long skuId, Long locationId, String batchNo, Long arrivalId) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<BigDecimal> rows = new JdbcTemplate(dataSource).query("SELECT quantity FROM wms_arrival_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_key=? AND batch_key=? AND arrival_key=? FOR UPDATE", (rs, n) -> rs.getBigDecimal(1), tenantId == null ? 0 : tenantId, warehouseId, skuId, locationId == null ? 0 : locationId, batchNo == null ? "" : batchNo, arrivalId == null ? 0 : arrivalId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    public void change(Long warehouseId, Long skuId, Long locationId, String batchNo, Long arrivalId, BigDecimal delta) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long tenantId = TenantContextHolder.getTenantId();
        long tenant = tenantId == null ? 0 : tenantId;
        long location = locationId == null ? 0 : locationId;
        String batch = batchNo == null ? "" : batchNo;
        long arrival = arrivalId == null ? 0 : arrivalId;
        List<BigDecimal> rows = jdbc.query("SELECT quantity FROM wms_arrival_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND location_key=? AND batch_key=? AND arrival_key=? FOR UPDATE",
                (rs, n) -> rs.getBigDecimal(1), tenant, warehouseId, skuId, location, batch, arrival);
        BigDecimal next = (rows.isEmpty() ? BigDecimal.ZERO : rows.get(0)).add(delta);
        if (next.signum() < 0) throw new ServiceException(1080001995, "所选到货记录的库存不足");
        jdbc.update("INSERT INTO wms_arrival_inventory (tenant_id,warehouse_id,sku_id,location_key,batch_key,arrival_key,quantity) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)",
                tenant, warehouseId, skuId, location, batch, arrival, next);
    }
}
