package cn.iocoder.yudao.module.wms.service.md.item;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.yudao.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WmsItemSkuExactTest extends BaseDbUnitTest {
    @Resource private WmsItemSkuMapper mapper;

    @Test
    void exactMatchingIsNotTruncatedBySimilarCodesAndPreservesAmbiguity() {
        mapper.insert(WmsItemSkuDO.builder().name("exact").itemId(1L).code("SKU").barCode("BAR").build());
        for (int i = 0; i < 25; i++) {
            mapper.insert(WmsItemSkuDO.builder().name("similar").itemId(1L).code("SKU-" + i).build());
        }
        assertEquals("SKU", mapper.selectExactCodeOrBarcode("SKU").get(0).getCode());
        assertEquals(1, mapper.selectExactCodeOrBarcode("BAR").size());
        mapper.insert(WmsItemSkuDO.builder().name("ambiguous").itemId(1L).code("BAR").build());
        assertEquals(2, mapper.selectExactCodeOrBarcode("BAR").size());
    }

    @Test
    void bindOnlyUpdatesEmptyBarcodeOnSelectedSku() {
        WmsItemSkuDO first = WmsItemSkuDO.builder().name("first").itemId(1L).code("A").build();
        WmsItemSkuDO second = WmsItemSkuDO.builder().name("second").itemId(1L).code("B").barCode("OLD").build();
        mapper.insert(first); mapper.insert(second);
        assertEquals(1, mapper.bindEmptyBarcode(first.getId(), "NEW"));
        assertEquals("NEW", mapper.selectById(first.getId()).getBarCode());
        assertEquals(0, mapper.bindEmptyBarcode(second.getId(), "OTHER"));
        assertEquals("OLD", mapper.selectById(second.getId()).getBarCode());
    }
}
