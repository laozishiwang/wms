package cn.iocoder.yudao.module.wms.service.md.item;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.yudao.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@Import({WmsItemSkuServiceImpl.class, WmsAdditionalBarcodeTest.JdbcConfig.class})
class WmsAdditionalBarcodeTest extends BaseDbUnitTest {
    @TestConfiguration static class JdbcConfig {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
    }
    @Resource private WmsItemSkuServiceImpl service;
    @Resource private WmsItemSkuMapper mapper;
    @Resource private JdbcTemplate jdbc;

    @AfterEach void cleanup() { jdbc.update("DELETE FROM wms_sku_barcode"); TenantContextHolder.clear(); }

    @Test void addsFindsAndDeletesAdditionalCodesWithoutReplacingPrimary() {
        TenantContextHolder.setTenantId(1L);
        WmsItemSkuDO sku = WmsItemSkuDO.builder().name("cola").itemId(1L).code("SKU").barCode("PRIMARY").build();
        mapper.insert(sku);
        service.bindBarcode(sku.getId(), "PACK-1");
        service.bindBarcode(sku.getId(), "PACK-2");
        assertEquals("PRIMARY", mapper.selectById(sku.getId()).getBarCode());
        assertEquals(2, service.getAdditionalBarcodes(sku.getId()).size());
        assertEquals(sku.getId(), service.getItemSkuExact("PACK-1").get(0).getId());
        WmsItemSkuDO other = WmsItemSkuDO.builder().name("other").itemId(1L).code("OTHER").build();
        mapper.insert(other);
        assertThrows(RuntimeException.class, () -> service.bindBarcode(other.getId(), "PACK-1"));
        jdbc.update("INSERT INTO wms_sku_barcode (tenant_id, sku_id, barcode) VALUES (2, ?, 'OTHER-TENANT')", sku.getId());
        assertTrue(service.getItemSkuExact("OTHER-TENANT").isEmpty());
        service.deleteAdditionalBarcode(sku.getId(), "PACK-1");
        assertTrue(service.getItemSkuExact("PACK-1").isEmpty());
        assertEquals(1, service.getAdditionalBarcodes(sku.getId()).size());
    }
}
