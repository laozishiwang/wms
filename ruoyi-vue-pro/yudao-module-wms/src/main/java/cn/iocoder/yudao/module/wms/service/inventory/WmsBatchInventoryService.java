package cn.iocoder.yudao.module.wms.service.inventory;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.yudao.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 批次库存。调用方必须在同一事务中持有仓库与 SKU 的汇总库存行锁。 */
@Service
public class WmsBatchInventoryService {
    public record BatchInventory(Long id, Long warehouseId, Long skuId, String batchNo,
                                 LocalDateTime productionDate, LocalDateTime expirationDate, BigDecimal quantity) {}
    public record ExpiryInventory(Long id, Long warehouseId, String warehouseName, Long skuId, String skuCode,
                                  String skuName, Long itemId, String itemName, String unit, String batchNo,
                                  LocalDateTime productionDate, LocalDateTime expirationDate, BigDecimal quantity, String status) {}
    private record BatchRow(long id, BigDecimal quantity, LocalDateTime productionDate, LocalDateTime expirationDate) {}
    @Resource
    private javax.sql.DataSource dataSource;

    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }

    private long tenantId() {
        Long id = TenantContextHolder.getTenantId();
        return id == null ? 0L : id;
    }

    public BigDecimal allocated(Long warehouseId, Long skuId) {
        return jdbc().query("SELECT quantity FROM wms_batch_inventory "
                        + "WHERE tenant_id = ? AND warehouse_id = ? AND sku_id = ? FOR UPDATE",
                (rs, row) -> rs.getBigDecimal(1), tenantId(), warehouseId, skuId)
                .stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<BatchInventory> list(Long warehouseId, Long skuId) {
        return jdbc().query("SELECT id, warehouse_id, sku_id, batch_no, production_date, expiration_date, quantity "
                        + "FROM wms_batch_inventory WHERE tenant_id = ? AND warehouse_id = ? AND sku_id = ? AND quantity > 0 ORDER BY expiration_date, id",
                (rs, row) -> new BatchInventory(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getLong("sku_id"),
                        rs.getString("batch_no"), rs.getObject("production_date", LocalDateTime.class),
                        rs.getObject("expiration_date", LocalDateTime.class), rs.getBigDecimal("quantity")),
                tenantId(), warehouseId, skuId);
    }

    public PageResult<ExpiryInventory> expiry(int pageNo, int pageSize, int days) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime end = today.plusDays(days + 1L).atStartOfDay();
        String from = " FROM wms_batch_inventory b JOIN wms_warehouse w ON w.id = b.warehouse_id AND w.tenant_id = b.tenant_id AND w.deleted = 0 "
                + "JOIN wms_item_sku s ON s.id = b.sku_id AND s.tenant_id = b.tenant_id AND s.deleted = 0 "
                + "JOIN wms_item i ON i.id = s.item_id AND i.tenant_id = b.tenant_id AND i.deleted = 0 "
                + "WHERE b.tenant_id = ? AND b.quantity > 0 AND b.expiration_date IS NOT NULL AND b.expiration_date < ?";
        Long total = jdbc().queryForObject("SELECT COUNT(*)" + from, Long.class, tenantId(), end);
        List<ExpiryInventory> rows = jdbc().query("SELECT b.id, b.warehouse_id, w.name warehouse_name, b.sku_id, "
                        + "s.code sku_code, s.name sku_name, i.id item_id, i.name item_name, i.unit, b.batch_no, "
                        + "b.production_date, b.expiration_date, b.quantity" + from + " ORDER BY b.expiration_date, b.id LIMIT ? OFFSET ?",
                (rs, row) -> {
                    LocalDateTime expiration = rs.getObject("expiration_date", LocalDateTime.class);
                    return new ExpiryInventory(rs.getLong("id"), rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
                            rs.getLong("sku_id"), rs.getString("sku_code"), rs.getString("sku_name"), rs.getLong("item_id"),
                            rs.getString("item_name"), rs.getString("unit"), rs.getString("batch_no"),
                            rs.getObject("production_date", LocalDateTime.class), expiration, rs.getBigDecimal("quantity"),
                            expiration.toLocalDate().isBefore(today) ? "EXPIRED" : "EXPIRING");
                }, tenantId(), end, pageSize, (long) (pageNo - 1) * pageSize);
        return new PageResult<>(rows, total);
    }

    public void rejectAllocated(Long warehouseId, Long skuId) {
        if (allocated(warehouseId, skuId).signum() > 0) {
            throw new ServiceException(1080001999, "存在批次库存，请先处理批次库存后再移库或盘点");
        }
    }

    /** Called after locking the corresponding summary inventory row. */
    public void checkBatch(Long warehouseId, Long skuId, String batchNo, BigDecimal expected, BigDecimal actual) {
        List<BatchRow> rows = jdbc().query("SELECT id, quantity, production_date, expiration_date FROM wms_batch_inventory "
                        + "WHERE tenant_id = ? AND warehouse_id = ? AND sku_id = ? AND batch_no = ? FOR UPDATE",
                (rs, row) -> new BatchRow(rs.getLong("id"), rs.getBigDecimal("quantity"),
                        rs.getObject("production_date", LocalDateTime.class), rs.getObject("expiration_date", LocalDateTime.class)),
                tenantId(), warehouseId, skuId, batchNo);
        if (rows.isEmpty() || rows.get(0).quantity().compareTo(expected) != 0) {
            throw new ServiceException(1080001999, "批次账面库存已变化，请重新盘点");
        }
        jdbc().update("UPDATE wms_batch_inventory SET quantity = ? WHERE id = ?", actual, rows.get(0).id());
    }

    public void adjustBatch(Long warehouseId, Long skuId, String batchNo, BigDecimal delta) {
        int count = jdbc().update("UPDATE wms_batch_inventory SET quantity=quantity+? WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND batch_no=? AND quantity+?>=0", delta, tenantId(), warehouseId, skuId, batchNo, delta);
        if (count != 1) throw new ServiceException(1080001999, "批次库存不足或不存在");
    }

    public void apply(Integer orderType, WmsInventoryChangeReqDTO.Item item, BigDecimal totalBefore) {
        BigDecimal allocated = allocated(item.getWarehouseId(), item.getSkuId());
        String batchNo = item.getBatchNo();
        if (batchNo != null && batchNo.isBlank()) throw new ServiceException(1080001999, "批次号不能仅包含空格");
        if (item.getProductionDate() != null && item.getExpirationDate() != null
                && item.getExpirationDate().isBefore(item.getProductionDate())) {
            throw new ServiceException(1080001999, "到期日期不能早于生产日期");
        }
        if (batchNo == null || batchNo.isBlank()) {
            if (item.getProductionDate() != null || item.getExpirationDate() != null) {
                throw new ServiceException(1080001999, "填写生产日期或到期日期时必须填写批次号");
            }
            if (item.getQuantity().signum() < 0
                    && totalBefore.add(item.getQuantity()).compareTo(allocated) < 0) {
                throw new ServiceException(1080001999, "未分配批次的库存不足，请选择批次出库");
            }
            return;
        }
        if (!Objects.equals(orderType, WmsOrderTypeEnum.RECEIPT.getType())
                && !Objects.equals(orderType, WmsOrderTypeEnum.SHIPMENT.getType())
                && !Objects.equals(orderType, WmsOrderTypeEnum.MOVEMENT.getType())) {
            throw new ServiceException(1080001999, "当前单据不支持批次库存");
        }
        if (batchNo.length() > 64) throw new ServiceException(1080001999, "批次号不能超过 64 个字符");
        List<BatchRow> rows = jdbc().query("SELECT id, quantity, production_date, expiration_date "
                        + "FROM wms_batch_inventory WHERE tenant_id = ? AND warehouse_id = ? AND sku_id = ? AND batch_no = ? FOR UPDATE",
                (rs, row) -> new BatchRow(rs.getLong("id"), rs.getBigDecimal("quantity"),
                        rs.getObject("production_date", LocalDateTime.class), rs.getObject("expiration_date", LocalDateTime.class)),
                tenantId(), item.getWarehouseId(), item.getSkuId(), batchNo);
        if (rows.isEmpty()) {
            if (item.getQuantity().signum() < 0) throw new ServiceException(1080001999, "批次库存不足");
            jdbc().update("INSERT INTO wms_batch_inventory (tenant_id, warehouse_id, sku_id, batch_no, production_date, expiration_date, quantity) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    tenantId(), item.getWarehouseId(), item.getSkuId(), batchNo, toSeconds(item.getProductionDate()), toSeconds(item.getExpirationDate()), item.getQuantity());
            return;
        }
        BatchRow row = rows.get(0);
        if (Objects.equals(orderType, WmsOrderTypeEnum.MOVEMENT.getType()) && item.getQuantity().signum() < 0
                && (!Objects.equals(row.productionDate(), toSeconds(item.getProductionDate()))
                || !Objects.equals(row.expirationDate(), toSeconds(item.getExpirationDate())))) {
            throw new ServiceException(1080001999, "批次日期与来源库存不一致，请重新选择批次");
        }
        if (item.getQuantity().signum() > 0 && (!Objects.equals(row.productionDate(), toSeconds(item.getProductionDate()))
                || !Objects.equals(row.expirationDate(), toSeconds(item.getExpirationDate())))) {
            throw new ServiceException(1080001999, "同一批次的生产日期和到期日期必须一致");
        }
        BigDecimal after = row.quantity().add(item.getQuantity());
        if (after.signum() < 0) throw new ServiceException(1080001999, "批次库存不足");
        jdbc().update("UPDATE wms_batch_inventory SET quantity = ? WHERE id = ?", after, row.id());
    }

    private static LocalDateTime toSeconds(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS);
    }
}
