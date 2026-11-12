package cn.iocoder.yudao.module.wms.service.approval;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.yudao.module.wms.service.order.shipment.WmsShipmentOrderService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderService;
import cn.iocoder.yudao.module.wms.service.order.check.WmsCheckOrderService;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class WmsApprovalService {
    @Resource private javax.sql.DataSource dataSource;
    @Resource private WmsApprovalGuard guard;
    @Resource private WmsReceiptOrderService receipt;
    @Resource private WmsShipmentOrderService shipment;
    @Resource private WmsMovementOrderService movement;
    @Resource private WmsCheckOrderService check;
    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }
    private ServiceException error(String text) { return new ServiceException(1080001997, text); }
    private String table(String type) {
        return switch (type) {
            case "receipt" -> "wms_receipt_order";
            case "shipment" -> "wms_shipment_order";
            case "movement" -> "wms_movement_order";
            case "check" -> "wms_check_order";
            default -> throw error("未知单据类型");
        };
    }
    private void transition(String type, Long id, int from, int to) {
        int count = jdbc().update("UPDATE " + table(type) + " SET status=? WHERE id=? AND tenant_id=? AND deleted=0 AND status=?", to, id, tenant(), from);
        if (count != 1) throw error("单据状态已变化，请刷新后重试");
    }
    private void record(String type, Long id, String action, String comment) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) throw error("请先登录");
        jdbc().update("INSERT INTO wms_order_approval_log (tenant_id,order_type,order_id,action,user_id,`comment`) VALUES (?,?,?,?,?,?)",
                tenant(), type, id, action, userId, comment);
    }
    @Transactional(rollbackFor = Exception.class)
    public void submit(String type, Long id, String comment) {
        if (!guard.isEnabled()) throw error("审批未启用");
        transition(type, id, 0, 1);
        record(type, id, "SUBMIT", comment);
    }
    @Transactional(rollbackFor = Exception.class)
    public void reject(String type, Long id, String comment) {
        if (comment == null || comment.isBlank()) throw error("请填写驳回原因");
        transition(type, id, 1, 0);
        record(type, id, "REJECT", comment);
    }
    @Transactional(rollbackFor = Exception.class)
    public void approve(String type, Long id, String comment) {
        transition(type, id, 1, 0);
        guard.completeApproved(() -> {
            switch (type) {
                case "receipt" -> receipt.completeReceiptOrder(id);
                case "shipment" -> shipment.completeShipmentOrder(id);
                case "movement" -> movement.completeMovementOrder(id);
                case "check" -> check.completeCheckOrder(id);
                default -> throw error("未知单据类型");
            }
        });
        record(type, id, "APPROVE", comment);
    }
    public List<Map<String, Object>> history(String type, Long id) {
        table(type);
        return jdbc().queryForList("SELECT action,user_id AS userId,`comment`,create_time AS createTime FROM wms_order_approval_log WHERE tenant_id=? AND order_type=? AND order_id=? ORDER BY id", tenant(), type, id);
    }
}
