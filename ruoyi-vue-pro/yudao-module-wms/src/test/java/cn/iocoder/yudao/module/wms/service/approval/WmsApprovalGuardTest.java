package cn.iocoder.yudao.module.wms.service.approval;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class WmsApprovalGuardTest {
    @Test
    void enabledGuardAllowsOnlyApprovedCompletionAndClearsContext() {
        WmsApprovalGuard guard = new WmsApprovalGuard();
        ReflectionTestUtils.setField(guard, "enabled", true);
        assertThrows(ServiceException.class, guard::checkCompletion);
        guard.completeApproved(guard::checkCompletion);
        assertThrows(ServiceException.class, guard::checkCompletion);
        assertThrows(IllegalStateException.class, () -> guard.completeApproved(() -> { throw new IllegalStateException(); }));
        assertThrows(ServiceException.class, guard::checkCompletion);
    }

    @Test
    void disabledGuardKeepsExistingCompletion() {
        new WmsApprovalGuard().checkCompletion();
    }
}
