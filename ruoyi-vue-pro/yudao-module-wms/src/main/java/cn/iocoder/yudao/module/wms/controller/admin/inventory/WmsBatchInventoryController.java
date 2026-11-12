package cn.iocoder.yudao.module.wms.controller.admin.inventory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.wms.service.inventory.WmsBatchInventoryService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/batch-inventory")
@Validated
public class WmsBatchInventoryController {
    @Resource
    private WmsBatchInventoryService service;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<WmsBatchInventoryService.BatchInventory>> list(@RequestParam Long warehouseId,
                                                                              @RequestParam Long skuId) {
        return success(service.list(warehouseId, skuId));
    }

    @GetMapping("/expiry")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<PageResult<WmsBatchInventoryService.ExpiryInventory>> expiry(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "30") @Min(0) @Max(3650) int days) {
        return success(service.expiry(pageNo, pageSize, days));
    }
}
