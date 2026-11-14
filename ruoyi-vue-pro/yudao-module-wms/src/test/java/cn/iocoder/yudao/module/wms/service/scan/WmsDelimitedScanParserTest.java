package cn.iocoder.yudao.module.wms.service.scan;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WmsDelimitedScanParserTest {
    @Test void parsesDifferentLiteralDelimiters() {
        var fields = Map.of("skuCode", 1, "batchNo", 2, "rollNo", 3, "netLength", 4);
        var parsed = WmsDelimitedScanParser.parse("SKU#LOT#R1#30", "SKU", "#", fields);
        assertEquals("LOT", parsed.get("batchNo"));
        assertEquals("30", parsed.get("netLength"));
        assertEquals("LOT", WmsDelimitedScanParser.parse("SKU|LOT|R1|30", "SKU", "|", fields).get("batchNo"));
    }

    @Test void rejectsIncompleteOrInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> WmsDelimitedScanParser.parse("SKU#LOT", "", "#", Map.of("skuCode", 1, "quantity", 3)));
        assertThrows(IllegalArgumentException.class, () -> WmsDelimitedScanParser.parse("SKU#-1", "", "#", Map.of("skuCode", 1, "quantity", 2)));
        assertThrows(IllegalArgumentException.class, () -> WmsDelimitedScanParser.parse("OTHER#1", "SKU", "#", Map.of("skuCode", 1)));
    }
}
