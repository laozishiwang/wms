package cn.iocoder.yudao.module.wms.service.scan;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WmsScanRuleResolverTest {
    @Test void matchesPrefixAndRefusesAmbiguousRules() {
        var rule = new WmsScanRuleResolver.Rule("SKU", "#", Map.of("skuCode", 1, "batchNo", 2));
        assertNull(WmsScanRuleResolver.resolve("123456", List.of(rule)));
        assertEquals("LOT", WmsScanRuleResolver.resolve("SKU#LOT", List.of(rule)).get("batchNo"));
        var generic = new WmsScanRuleResolver.Rule("", "#", Map.of("skuCode", 1));
        assertThrows(IllegalArgumentException.class, () -> WmsScanRuleResolver.resolve("SKU#LOT", List.of(rule, generic)));
    }

    @Test void reportsMalformedMatchingCodes() {
        var rule = new WmsScanRuleResolver.Rule("SKU", "#", Map.of("skuCode", 1, "quantity", 2));
        assertThrows(IllegalArgumentException.class, () -> WmsScanRuleResolver.resolve("SKU#-1", List.of(rule)));
    }
}
