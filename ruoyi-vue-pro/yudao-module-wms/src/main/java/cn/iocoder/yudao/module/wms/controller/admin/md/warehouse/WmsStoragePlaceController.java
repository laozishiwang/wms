package cn.iocoder.yudao.module.wms.controller.admin.md.warehouse;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.service.md.warehouse.WmsWarehouseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Statement;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/storage-place")
public class WmsStoragePlaceController {
    @Resource private javax.sql.DataSource dataSource;
    @Resource private WmsWarehouseService warehouseService;
    @Resource private cn.iocoder.yudao.module.wms.service.inventory.WmsDimensionInventoryService dimensionInventoryService;
    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }
    public record Place(Long id, Long warehouseId, Long parentId, String type, String code, String name) {}
    public record Balance(Long locationId, String code, String name, String shelfName, java.math.BigDecimal quantity) {}
    public record Create(@NotNull Long warehouseId, Long parentId, @NotBlank String type,
                         @NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 100) String name) {}

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:query')")
    public CommonResult<List<Place>> list(@RequestParam Long warehouseId) {
        warehouseService.validateWarehouseExists(warehouseId);
        return success(jdbc().query("SELECT id,warehouse_id,parent_id,type,code,name FROM wms_storage_place WHERE tenant_id=? AND warehouse_id=? ORDER BY id",
                (rs, row) -> new Place(rs.getLong(1), rs.getLong(2), (Long) rs.getObject(3, Long.class), rs.getString(4), rs.getString(5), rs.getString(6)), tenant(), warehouseId));
    }

    @GetMapping("/inventory")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<Balance>> inventory(@RequestParam Long warehouseId, @RequestParam Long skuId) {
        warehouseService.validateWarehouseExists(warehouseId);
        return success(jdbc().query("SELECT p.id,p.code,p.name,s.name,i.quantity FROM wms_location_inventory i JOIN wms_storage_place p ON p.id=i.location_id AND p.tenant_id=i.tenant_id LEFT JOIN wms_storage_place s ON s.id=p.parent_id AND s.tenant_id=p.tenant_id WHERE i.tenant_id=? AND i.warehouse_id=? AND i.sku_id=? AND i.quantity>0 ORDER BY p.code", (rs, n) -> new Balance(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5)), tenant(), warehouseId, skuId));
    }

    @GetMapping("/dimension-inventory")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<cn.iocoder.yudao.module.wms.service.inventory.WmsDimensionInventoryService.LocationBalance>> dimensionInventory(@RequestParam Long warehouseId, @RequestParam Long skuId, @RequestParam(defaultValue = "") String batchNo) {
        warehouseService.validateWarehouseExists(warehouseId);
        return success(dimensionInventoryService.listLocations(warehouseId, skuId, batchNo));
    }

    @GetMapping("/dimensions")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<cn.iocoder.yudao.module.wms.service.inventory.WmsDimensionInventoryService.DimensionBalance>> dimensions(@RequestParam Long warehouseId, @RequestParam Long skuId) {
        warehouseService.validateWarehouseExists(warehouseId);
        return success(dimensionInventoryService.listDimensions(warehouseId, skuId));
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:create')")
    public CommonResult<Long> create(@Valid @RequestBody Create req) {
        warehouseService.validateWarehouseExists(req.warehouseId());
        if ("SHELF".equals(req.type())) {
            if (req.parentId() != null) throw new ServiceException(1080001996, "库区不能设置上级");
        } else if ("LOCATION".equals(req.type())) {
            Long count = jdbc().queryForObject("SELECT COUNT(*) FROM wms_storage_place WHERE tenant_id=? AND warehouse_id=? AND id=? AND type='SHELF'", Long.class, tenant(), req.warehouseId(), req.parentId());
            if (count == null || count == 0) throw new ServiceException(1080001996, "请选择当前仓库的库区");
        } else throw new ServiceException(1080001996, "位置类型无效");
        var keys = new GeneratedKeyHolder();
        jdbc().update(con -> { var ps = con.prepareStatement("INSERT INTO wms_storage_place (tenant_id,warehouse_id,parent_id,type,code,name) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1, tenant()); ps.setLong(2, req.warehouseId()); if (req.parentId() == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, req.parentId()); ps.setString(4, req.type()); ps.setString(5, req.code()); ps.setString(6, req.name()); return ps; }, keys);
        return success(keys.getKey().longValue());
    }
}
