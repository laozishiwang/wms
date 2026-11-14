package cn.iocoder.yudao.module.wms.service.scan;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses one configured delimited code; field positions are one-based. */
public final class WmsDelimitedScanParser {
    private static final Set<String> FIELDS = Set.of("skuCode", "batchNo", "palletNo", "boxNo", "rollNo", "quantity", "netLength", "grossLength", "netWeight", "grossWeight", "length", "width", "height", "price", "salesPrice");
    private static final Set<String> NUMERIC = Set.of("quantity", "netLength", "grossLength", "netWeight", "grossWeight", "length", "width", "height", "price", "salesPrice");

    private WmsDelimitedScanParser() {}

    public static void validate(String delimiter, Map<String, Integer> fields) {
        if (delimiter == null || delimiter.isEmpty() || delimiter.length() > 16 || fields == null || !fields.containsKey("skuCode")) throw new IllegalArgumentException("请设置分隔符和物品规格字段");
        for (Map.Entry<String, Integer> field : fields.entrySet()) {
            if (!FIELDS.contains(field.getKey()) || field.getValue() == null || field.getValue() < 1 || field.getValue() > 100) throw new IllegalArgumentException("规则字段或位置不正确");
        }
    }

    public static Map<String, String> parse(String text, String prefix, String delimiter, Map<String, Integer> fields) {
        validate(delimiter, fields);
        if (text == null) throw new IllegalArgumentException("扫码内容不能为空");
        if (prefix != null && !prefix.isEmpty() && !text.startsWith(prefix)) throw new IllegalArgumentException("二维码不符合规则前缀");
        String[] parts = text.split(Pattern.quote(delimiter), -1);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> field : fields.entrySet()) {
            String name = field.getKey(); Integer position = field.getValue();
            if (!FIELDS.contains(name) || position == null || position < 1) throw new IllegalArgumentException("规则字段或位置不正确");
            if (position > parts.length) throw new IllegalArgumentException("二维码缺少第 " + position + " 段");
            String value = parts[position - 1].trim();
            if (name.equals("skuCode") && value.isEmpty()) throw new IllegalArgumentException("物品规格字段不能为空");
            if (!value.isEmpty() && NUMERIC.contains(name)) {
                try {
                    BigDecimal number = new BigDecimal(value);
                    if (number.signum() < 0 || (name.equals("quantity") && number.signum() == 0)) throw new NumberFormatException();
                } catch (NumberFormatException e) { throw new IllegalArgumentException("字段 " + name + " 的数值不正确"); }
            }
            if (!value.isEmpty()) result.put(name, value);
        }
        return result;
    }
}
