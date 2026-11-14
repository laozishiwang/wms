package cn.iocoder.yudao.module.wms.service.scan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.wms.controller.admin.scan.WmsScanRuleController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WmsScanRuleControllerTest extends BaseDbUnitTest {
    @Resource private DataSource dataSource;
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void maintainsTenantRulesAndResolvesWithoutGuessing() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS wms_scan_rule (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(100), prefix VARCHAR(64), delimiter VARCHAR(16), fields_json VARCHAR(2000), enabled BOOLEAN)");
        WmsScanRuleController controller = new WmsScanRuleController();
        ReflectionTestUtils.setField(controller, "dataSource", dataSource);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        TenantContextHolder.setTenantId(1L);
        WmsScanRuleController.Rule rule = new WmsScanRuleController.Rule();
        rule.setName("供应商"); rule.setPrefix("SKU"); rule.setDelimiter("#"); rule.setFields(Map.of("skuCode", 1, "batchNo", 2)); rule.setEnabled(true);
        assertTrue(controller.save(rule).getData());
        assertEquals("LOT", controller.resolve("SKU#LOT").getData().get("batchNo"));
        Long id = controller.list().getData().get(0).getId();
        TenantContextHolder.setTenantId(2L);
        assertTrue(controller.list().getData().isEmpty());
        assertFalse(controller.delete(id).getData());
        TenantContextHolder.setTenantId(1L);
        assertTrue(controller.delete(id).getData());
        assertNull(controller.resolve("SKU#LOT").getData());
    }
}
