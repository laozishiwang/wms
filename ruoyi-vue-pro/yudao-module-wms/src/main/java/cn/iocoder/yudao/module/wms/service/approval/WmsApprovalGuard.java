package cn.iocoder.yudao.module.wms.service.approval;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WmsApprovalGuard {
    @Value("${wms.approval.enabled:false}")
    private boolean enabled;
    private final ThreadLocal<Boolean> approval = new ThreadLocal<>();

    public boolean isEnabled() { return enabled; }
    public void checkCompletion() {
        if (enabled && !Boolean.TRUE.equals(approval.get())) {
            throw new ServiceException(1080001997, "已启用审批，请提交审批后完成单据");
        }
    }
    void completeApproved(Runnable action) {
        approval.set(true);
        try { action.run(); } finally { approval.remove(); }
    }
}
