package cn.iocoder.yudao.module.wms.controller.admin.scan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.service.scan.WmsDelimitedScanParser;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/wms/scan-rule")
public class WmsScanRuleController {
    @Resource private DataSource dataSource;
    @Resource private ObjectMapper objectMapper;
    @Resource private WmsItemSkuService itemSkuService;

    @Data
    public static class Rule {
        private Long id;
        private String name;
        private String prefix;
        private String delimiter;
        private Map<String, Integer> fields;
        private Boolean enabled;
        private String sample;
        private Long bindingCount;
    }

    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<List<Rule>> list() {
        return success(jdbc().query("SELECT r.id,r.name,r.prefix,r.delimiter,r.fields_json,r.enabled,(SELECT COUNT(*) FROM wms_scan_rule_sku b WHERE b.tenant_id=r.tenant_id AND b.rule_id=r.id) binding_count FROM wms_scan_rule r WHERE r.tenant_id=? ORDER BY r.id DESC", (rs, row) -> {
            Rule rule = new Rule();
            rule.setId(rs.getLong("id")); rule.setName(rs.getString("name")); rule.setPrefix(rs.getString("prefix"));
            rule.setDelimiter(rs.getString("delimiter")); rule.setEnabled(rs.getBoolean("enabled"));
            rule.setBindingCount(rs.getLong("binding_count"));
            try { rule.setFields(objectMapper.readValue(rs.getString("fields_json"), new TypeReference<Map<String, Integer>>() {})); }
            catch (Exception e) { throw new IllegalStateException("二维码规则数据无效", e); }
            return rule;
        }, tenant()));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('wms:item:update')")
    public CommonResult<Boolean> save(@RequestBody Rule rule) throws Exception {
        try {
            if (rule.getName() == null || rule.getName().isBlank() || rule.getName().length() > 100 || (rule.getPrefix() != null && rule.getPrefix().length() > 64)) throw new IllegalArgumentException("请填写有效的规则名称和前缀");
            WmsDelimitedScanParser.validate(rule.getDelimiter(), rule.getFields());
        } catch (IllegalArgumentException e) { return CommonResult.error(400, e.getMessage()); }
        String fields = objectMapper.writeValueAsString(rule.getFields());
        String prefix = rule.getPrefix() == null ? "" : rule.getPrefix();
        boolean enabled = Boolean.TRUE.equals(rule.getEnabled());
        if (rule.getId() == null) {
            jdbc().update("INSERT INTO wms_scan_rule (tenant_id,name,prefix,delimiter,fields_json,enabled) VALUES (?,?,?,?,?,?)", tenant(), rule.getName().trim(), prefix, rule.getDelimiter(), fields, enabled);
        } else if (jdbc().update("UPDATE wms_scan_rule SET name=?,prefix=?,delimiter=?,fields_json=?,enabled=? WHERE id=? AND tenant_id=?", rule.getName().trim(), prefix, rule.getDelimiter(), fields, enabled, rule.getId(), tenant()) == 0) {
            return CommonResult.error(400, "规则不存在");
        }
        return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('wms:item:update')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        if (jdbc().queryForObject("SELECT COUNT(*) FROM wms_scan_rule_sku WHERE tenant_id=? AND rule_id=?", Long.class, tenant(), id) > 0) return CommonResult.error(400, "请先解除此规则关联的物品规格");
        return success(jdbc().update("DELETE FROM wms_scan_rule WHERE id=? AND tenant_id=?", id, tenant()) > 0);
    }

    @GetMapping("/sku-rules")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<List<Long>> skuRules(@RequestParam Long skuId) {
        return success(jdbc().queryForList("SELECT rule_id FROM wms_scan_rule_sku WHERE tenant_id=? AND sku_id=?", Long.class, tenant(), skuId));
    }

    @GetMapping("/bindings")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<List<Map<String, Object>>> bindings(@RequestParam Long ruleId) {
        return success(jdbc().queryForList("SELECT b.sku_id AS skuId,s.code AS skuCode,s.name AS skuName,i.name AS itemName FROM wms_scan_rule_sku b LEFT JOIN wms_item_sku s ON s.id=b.sku_id AND s.tenant_id=b.tenant_id LEFT JOIN wms_item i ON i.id=s.item_id AND i.tenant_id=b.tenant_id WHERE b.tenant_id=? AND b.rule_id=?", tenant(), ruleId));
    }

    @PutMapping("/sku-rule")
    @PreAuthorize("@ss.hasPermission('wms:item:update')")
    public CommonResult<Boolean> bind(@RequestParam Long skuId, @RequestParam Long ruleId) {
        itemSkuService.validateItemSkuExists(skuId);
        if (list().getData().stream().noneMatch(rule -> rule.getId().equals(ruleId))) return CommonResult.error(400, "规则不存在");
        if (jdbc().queryForObject("SELECT COUNT(*) FROM wms_scan_rule_sku WHERE tenant_id=? AND rule_id=? AND sku_id=?", Long.class, tenant(), ruleId, skuId) == 0)
            jdbc().update("INSERT INTO wms_scan_rule_sku (tenant_id,rule_id,sku_id) VALUES (?,?,?)", tenant(), ruleId, skuId);
        return success(true);
    }

    @DeleteMapping("/sku-rule")
    @PreAuthorize("@ss.hasPermission('wms:item:update')")
    public CommonResult<Boolean> unbind(@RequestParam Long skuId, @RequestParam Long ruleId) {
        return success(jdbc().update("DELETE FROM wms_scan_rule_sku WHERE tenant_id=? AND rule_id=? AND sku_id=?", tenant(), ruleId, skuId) > 0);
    }

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<Map<String, String>> preview(@RequestBody Rule rule) {
        try { return success(WmsDelimitedScanParser.parse(rule.getSample(), rule.getPrefix(), rule.getDelimiter(), rule.getFields())); }
        catch (IllegalArgumentException e) { return CommonResult.error(400, e.getMessage()); }
    }

    @GetMapping("/resolve")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<Map<String, String>> resolve(@RequestParam String value, @RequestParam(required = false) Long skuId) {
        Map<String, String> matched = null;
        boolean candidate = false;
        for (Rule rule : list().getData()) {
            if (!Boolean.TRUE.equals(rule.getEnabled()) || !value.startsWith(rule.getPrefix()) || !value.contains(rule.getDelimiter())) continue;
            candidate = true;
            Map<String, String> parsed;
            try { parsed = WmsDelimitedScanParser.parse(value, rule.getPrefix(), rule.getDelimiter(), rule.getFields()); }
            catch (IllegalArgumentException e) { continue; }
            var skus = parsed.containsKey("skuCode") ? itemSkuService.getItemSkuExact(parsed.get("skuCode")) : skuId == null ? List.<cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO>of() : List.of(itemSkuService.validateItemSkuExists(skuId));
            if (skus.size() != 1 || (skuId != null && !skuId.equals(skus.get(0).getId()))) continue;
            Long count = jdbc().queryForObject("SELECT COUNT(*) FROM wms_scan_rule_sku WHERE tenant_id=? AND rule_id=? AND sku_id=?", Long.class, tenant(), rule.getId(), skus.get(0).getId());
            if (count == 0) continue;
            if (matched != null) return CommonResult.error(400, "二维码匹配多个已关联的规则，请检查规则设置");
            parsed.put("skuCode", skus.get(0).getCode());
            matched = parsed;
        }
        if (matched == null && candidate) return CommonResult.error(400, "二维码未匹配到物品关联的规则，请检查规则及规格编号");
        return success(matched);
    }
}
