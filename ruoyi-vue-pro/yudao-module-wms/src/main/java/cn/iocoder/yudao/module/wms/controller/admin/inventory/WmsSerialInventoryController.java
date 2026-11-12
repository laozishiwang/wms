package cn.iocoder.yudao.module.wms.controller.admin.inventory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.wms.service.inventory.WmsSerialInventoryService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/serial-inventory")
public class WmsSerialInventoryController {
    @Resource private WmsSerialInventoryService service;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<String>> list(@RequestParam Long warehouseId, @RequestParam Long skuId,
                                            @RequestParam(required = false) String batchNo,
                                            @RequestParam(required = false) Long locationId) {
        return success(service.listInStockAtLocation(warehouseId, skuId, locationId, batchNo));
    }
    @GetMapping("/find")
    @PreAuthorize("@ss.hasPermission('wms:inventory:query')")
    public CommonResult<List<WmsSerialInventoryService.SerialInventory>> find(@RequestParam Long skuId, @RequestParam String serialNo) {
        return success(service.find(skuId, serialNo));
    }
}
