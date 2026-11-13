package cn.iocoder.yudao.module.wms.service.inventory;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.yudao.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import cn.iocoder.yudao.module.wms.service.inventory.dto.WmsInventoryCheckReqDTO;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 序列号变更与汇总库存共用事务及库存行锁。 */
@Service
public class WmsSerialInventoryService {
    public record SerialInventory(Long skuId, String serialNo, Long warehouseId, String batchNo, Long locationId, Long arrivalId, boolean inStock) {}
    @Resource private javax.sql.DataSource dataSource;
    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    @Resource private WmsBatchInventoryService batches;
    @Resource private WmsDimensionInventoryService dimensions;
    @Resource private WmsArrivalInventoryService arrivals;
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0L : id; }
    public List<String> listInStock(Long warehouse, Long sku, String batch) {
        return jdbc().query("SELECT serial_no FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 AND ((batch_no=?) OR (batch_no IS NULL AND ? IS NULL)) ORDER BY serial_no", (rs, row) -> rs.getString(1), tenant(), warehouse, sku, batch, batch);
    }
    /** 调用前须执行 011 迁移；NULL 库位代表未分配，不能推断历史位置。 */
    public List<String> listInStockAtLocation(Long warehouse, Long sku, Long location, String batch) {
        return jdbc().query("SELECT serial_no FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 AND location_id <=> ? AND batch_no <=> ? ORDER BY serial_no",
                (rs, row) -> rs.getString(1), tenant(), warehouse, sku, location, batch);
    }
    public List<String> listInStockAtArrival(Long warehouse, Long sku, Long location, String batch, Long arrivalId) {
        return jdbc().query("SELECT serial_no FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 AND location_id <=> ? AND batch_no <=> ? AND arrival_id <=> ? ORDER BY serial_no", (rs, row) -> rs.getString(1), tenant(), warehouse, sku, location, batch, arrivalId);
    }
    public void checkSerials(WmsInventoryCheckReqDTO.Item item) {
        List<String> current = jdbc().query("SELECT serial_no FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 AND batch_no <=> ? AND location_id <=> ? AND arrival_id <=> ? FOR UPDATE", (rs, row) -> rs.getString(1), tenant(), item.getWarehouseId(), item.getSkuId(), item.getBatchNo(), item.getLocationId(), item.getArrivalId());
        List<String> book = item.getBookSerialNos(), actual = item.getActualSerialNos();
        if (book == null && actual == null) {
            if (!current.isEmpty()) throw error("含序列号库存请重新创建盘点单");
            return;
        }
        if (book == null || actual == null || new HashSet<>(book).size() != book.size() || new HashSet<>(actual).size() != actual.size() || !new HashSet<>(book).equals(new HashSet<>(current)) || actual.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 128) || BigDecimal.valueOf(book.size()).compareTo(item.getQuantity()) > 0 || BigDecimal.valueOf(actual.size()).compareTo(item.getCheckQuantity()) > 0) throw error("序列号盘点数据有误或账面已变化");
        for (String serial : book) if (!actual.contains(serial)) jdbc().update("UPDATE wms_serial_inventory SET in_stock=0 WHERE tenant_id=? AND sku_id=? AND serial_no=? AND warehouse_id=? AND in_stock=1", tenant(), item.getSkuId(), serial, item.getWarehouseId());
        for (String serial : actual) if (!book.contains(serial)) {
            List<Long> ids = jdbc().query("SELECT id FROM wms_serial_inventory WHERE tenant_id=? AND sku_id=? AND serial_no=? FOR UPDATE", (rs, row) -> rs.getLong(1), tenant(), item.getSkuId(), serial);
            if (ids.isEmpty()) jdbc().update("INSERT INTO wms_serial_inventory (tenant_id,sku_id,serial_no,warehouse_id,batch_no,location_id,arrival_id,in_stock) VALUES (?,?,?,?,?,?,?,1)", tenant(), item.getSkuId(), serial, item.getWarehouseId(), item.getBatchNo(), item.getLocationId(), item.getArrivalId());
            else if (jdbc().update("UPDATE wms_serial_inventory SET warehouse_id=?,batch_no=?,location_id=?,arrival_id=?,in_stock=1 WHERE id=? AND in_stock=0", item.getWarehouseId(), item.getBatchNo(), item.getLocationId(), item.getArrivalId(), ids.get(0)) != 1) throw error("序列号已在其他库存中");
        }
    }
    public List<SerialInventory> find(Long skuId, String serialNo) {
        return jdbc().query("SELECT sku_id, serial_no, warehouse_id, batch_no, location_id, arrival_id, in_stock FROM wms_serial_inventory WHERE tenant_id=? AND sku_id=? AND serial_no=?",
                (rs, row) -> new SerialInventory(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), (Long) rs.getObject(5), (Long) rs.getObject(6), rs.getInt(7) == 1), tenant(), skuId, serialNo);
    }
    private ServiceException error(String message) { return new ServiceException(1080001998, message); }
    private long count(Long warehouse, Long sku, Long location, String batch, Long arrivalId) {
        return jdbc().query("SELECT id FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 AND location_id <=> ? AND batch_no <=> ? AND arrival_id <=> ? FOR UPDATE", (rs, row) -> rs.getLong(1), tenant(), warehouse, sku, location, batch, arrivalId).size();
    }
    public void rejectPresent(Long warehouse, Long sku) {
        int count = jdbc().query("SELECT id FROM wms_serial_inventory WHERE tenant_id=? AND warehouse_id=? AND sku_id=? AND in_stock=1 FOR UPDATE", (rs, row) -> rs.getLong(1), tenant(), warehouse, sku).size();
        if (count > 0) throw error("存在序列号库存，暂不支持移库或盘点");
    }
    public void apply(Integer type, WmsInventoryChangeReqDTO.Item item, BigDecimal totalBefore) {
        boolean movement = Objects.equals(type, WmsOrderTypeEnum.MOVEMENT.getType());
        boolean receipt = Objects.equals(type, WmsOrderTypeEnum.RECEIPT.getType()) || (movement && item.getQuantity().signum() > 0);
        boolean shipment = Objects.equals(type, WmsOrderTypeEnum.SHIPMENT.getType()) || (movement && item.getQuantity().signum() < 0);
        if (!receipt && !shipment) { rejectPresent(item.getWarehouseId(), item.getSkuId()); return; }
        List<String> serials = item.getSerialNos();
        String batch = item.getBatchNo();
        if (serials == null || serials.isEmpty()) {
            if (shipment) {
                BigDecimal available = arrivals.lockQuantity(item.getWarehouseId(), item.getSkuId(), item.getLocationId(), batch, item.getArrivalId());
                if (available.add(item.getQuantity()).compareTo(BigDecimal.valueOf(count(item.getWarehouseId(), item.getSkuId(), item.getLocationId(), batch, item.getArrivalId()))) < 0) throw error("无序列号库存不足，请选择序列号出库");
            }
            return;
        }
        if (BigDecimal.valueOf(serials.size()).compareTo(item.getQuantity().abs()) != 0 || new HashSet<>(serials).size() != serials.size() || serials.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 128)) throw error("序列号数量必须与整数数量一致且不能重复或为空");
        for (String serial : serials) {
            List<Long> ids = jdbc().query("SELECT id FROM wms_serial_inventory WHERE tenant_id=? AND sku_id=? AND serial_no=? FOR UPDATE", (rs, n) -> rs.getLong(1), tenant(), item.getSkuId(), serial);
            if (receipt) {
                if (ids.isEmpty()) jdbc().update("INSERT INTO wms_serial_inventory (tenant_id,sku_id,serial_no,warehouse_id,batch_no,location_id,arrival_id,in_stock) VALUES (?,?,?,?,?,?,?,1)", tenant(), item.getSkuId(), serial, item.getWarehouseId(), batch, item.getLocationId(), item.getArrivalId());
                else if (jdbc().update("UPDATE wms_serial_inventory SET warehouse_id=?,batch_no=?,location_id=?,arrival_id=?,in_stock=1 WHERE id=? AND in_stock=0", item.getWarehouseId(), batch, item.getLocationId(), item.getArrivalId(), ids.get(0)) != 1) throw error("序列号已在库");
            } else {
                if (ids.isEmpty() || jdbc().update("UPDATE wms_serial_inventory SET in_stock=0 WHERE id=? AND warehouse_id=? AND in_stock=1 AND batch_no <=> ? AND location_id <=> ? AND arrival_id <=> ?", ids.get(0), item.getWarehouseId(), batch, item.getLocationId(), item.getArrivalId()) != 1) throw error("序列号不在所选仓库或批次");
            }
        }
    }
}
