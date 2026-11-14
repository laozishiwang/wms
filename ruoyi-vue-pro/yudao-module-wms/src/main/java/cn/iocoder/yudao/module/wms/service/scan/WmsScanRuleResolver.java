package cn.iocoder.yudao.module.wms.service.scan;

import java.util.List;
import java.util.Map;

/** Selects an unambiguous enabled rule before interpreting the scanned code. */
public final class WmsScanRuleResolver {
    private WmsScanRuleResolver() {}

    public record Rule(String prefix, String delimiter, Map<String, Integer> fields) {}

    public static Map<String, String> resolve(String code, List<Rule> rules) {
        List<Rule> matching = rules.stream()
                .filter(rule -> rule.delimiter() != null && !rule.delimiter().isEmpty())
                .filter(rule -> code.contains(rule.delimiter()))
                .filter(rule -> rule.prefix() == null || code.startsWith(rule.prefix()))
                .toList();
        if (matching.isEmpty()) return null;
        if (matching.size() > 1) throw new IllegalArgumentException("二维码匹配到多条规则，请调整规则前缀");
        Rule rule = matching.get(0);
        return WmsDelimitedScanParser.parse(code, rule.prefix(), rule.delimiter(), rule.fields());
    }
}
