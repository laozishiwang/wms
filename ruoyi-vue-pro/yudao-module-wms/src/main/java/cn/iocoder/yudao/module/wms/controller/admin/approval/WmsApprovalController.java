package cn.iocoder.yudao.module.wms.controller.admin.approval;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.wms.service.approval.WmsApprovalGuard;
import cn.iocoder.yudao.module.wms.service.approval.WmsApprovalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/approval")
@Validated
public class WmsApprovalController {
    @Resource private WmsApprovalService service;
    @Resource private WmsApprovalGuard guard;
    public record Request(@NotBlank String type, @NotNull Long id, @Size(max = 255) String comment) {}

    @GetMapping("/config")
    public CommonResult<Map<String, Boolean>> config() { return success(Map.of("enabled", guard.isEnabled())); }
    @PostMapping("/submit")
    @PreAuthorize("@ss.hasPermission('wms:approval:submit')")
    public CommonResult<Boolean> submit(@Valid @RequestBody Request request) {
        service.submit(request.type(), request.id(), request.comment()); return success(true);
    }
    @PostMapping("/approve")
    @PreAuthorize("@ss.hasPermission('wms:approval:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody Request request) {
        service.approve(request.type(), request.id(), request.comment()); return success(true);
    }
    @PostMapping("/reject")
    @PreAuthorize("@ss.hasPermission('wms:approval:approve')")
    public CommonResult<Boolean> reject(@Valid @RequestBody Request request) {
        service.reject(request.type(), request.id(), request.comment()); return success(true);
    }
    @GetMapping("/history")
    @PreAuthorize("@ss.hasPermission('wms:approval:query')")
    public CommonResult<List<Map<String, Object>>> history(@RequestParam String type, @RequestParam Long id) {
        return success(service.history(type, id));
    }
}
