package cn.iocoder.yudao.module.wms.service.scan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.wms.controller.admin.scan.WmsScanRuleController;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import javax.sql.DataSource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WmsScanRuleControllerTest extends BaseDbUnitTest {
    @Resource private DataSource dataSource;
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void maintainsTenantRulesAndResolvesWithoutGuessing() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS wms_scan_rule (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(100), prefix VARCHAR(64), delimiter VARCHAR(16), fields_json VARCHAR(2000), enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS wms_scan_rule_sku (tenant_id BIGINT, rule_id BIGINT, sku_id BIGINT, PRIMARY KEY (tenant_id, rule_id, sku_id))");
        WmsScanRuleController controller = new WmsScanRuleController();
        ReflectionTestUtils.setField(controller, "dataSource", dataSource);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        WmsItemSkuService items = mock(WmsItemSkuService.class);
        when(items.getItemSkuExact("SKU")).thenReturn(java.util.List.of(WmsItemSkuDO.builder().id(42L).code("SKU").build()));
        when(items.validateItemSkuExists(42L)).thenReturn(WmsItemSkuDO.builder().id(42L).code("SKU").build());
        ReflectionTestUtils.setField(controller, "itemSkuService", items);
        TenantContextHolder.setTenantId(1L);
        WmsScanRuleController.Rule rule = new WmsScanRuleController.Rule();
        rule.setName("供应商"); rule.setPrefix("SKU"); rule.setDelimiter("#"); rule.setFields(Map.of("skuCode", 1, "batchNo", 2)); rule.setEnabled(true);
        assertTrue(controller.save(rule).getData());
        Long id = controller.list().getData().get(0).getId();
        assertNotEquals(0, controller.resolve("SKU#LOT", null).getCode());
        assertTrue(controller.bind(42L, id).getData());
        assertEquals("LOT", controller.resolve("SKU#LOT", null).getData().get("batchNo"));
        WmsScanRuleController.Rule other = new WmsScanRuleController.Rule();
        other.setName("其他格式"); other.setPrefix("SKU"); other.setDelimiter("#"); other.setFields(Map.of("skuCode", 1, "rollNo", 2)); other.setEnabled(true);
        assertTrue(controller.save(other).getData());
        Long otherId = controller.list().getData().stream().filter(r -> r.getName().equals("其他格式")).findFirst().orElseThrow().getId();
        assertTrue(controller.bind(43L, otherId).getData());
        assertEquals("LOT", controller.resolve("SKU#LOT", null).getData().get("batchNo"));
        assertTrue(controller.bind(42L, otherId).getData());
        assertNotEquals(0, controller.resolve("SKU#LOT", null).getCode());
        assertTrue(controller.unbind(42L, otherId).getData());
        WmsScanRuleController.Rule noSku = new WmsScanRuleController.Rule();
        noSku.setName("无物品编码"); noSku.setPrefix("LOT"); noSku.setDelimiter("#"); noSku.setFields(Map.of("batchNo", 1, "quantity", 2)); noSku.setEnabled(true);
        assertTrue(controller.save(noSku).getData());
        Long noSkuId = controller.list().getData().stream().filter(r -> r.getName().equals("无物品编码")).findFirst().orElseThrow().getId();
        assertTrue(controller.bind(42L, noSkuId).getData());
        assertNotEquals(0, controller.resolve("LOT#2", null).getCode());
        assertEquals("SKU", controller.resolve("LOT#2", 42L).getData().get("skuCode"));
        assertNotEquals(0, controller.delete(id).getCode());
        TenantContextHolder.setTenantId(2L);
        assertTrue(controller.list().getData().isEmpty());
        assertFalse(controller.delete(id).getData());
        TenantContextHolder.setTenantId(1L);
        assertTrue(controller.unbind(42L, id).getData());
        assertTrue(controller.delete(id).getData());
        assertNull(controller.resolve("SKU#LOT", null).getData());
    }
}
