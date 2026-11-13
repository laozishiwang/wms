package cn.iocoder.yudao.module.wms.controller.admin.inventory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.wms.service.inventory.WmsArrivalInventoryService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/arrival-inventory")
public class WmsArrivalInventoryController {
    @Resource private WmsArrivalInventoryService service;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<WmsArrivalInventoryService.Balance>> list(@RequestParam Long warehouseId, @RequestParam Long skuId) {
        return success(service.list(warehouseId, skuId));
    }
}
