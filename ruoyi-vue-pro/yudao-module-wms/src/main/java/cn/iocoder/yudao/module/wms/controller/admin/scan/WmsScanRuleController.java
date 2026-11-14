package cn.iocoder.yudao.module.wms.controller.admin.scan;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.wms.service.scan.WmsDelimitedScanParser;
import cn.iocoder.yudao.module.wms.service.scan.WmsScanRuleResolver;
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

    @Data
    public static class Rule {
        private Long id;
        private String name;
        private String prefix;
        private String delimiter;
        private Map<String, Integer> fields;
        private Boolean enabled;
        private String sample;
    }

    private JdbcTemplate jdbc() { return new JdbcTemplate(dataSource); }
    private long tenant() { Long id = TenantContextHolder.getTenantId(); return id == null ? 0 : id; }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<List<Rule>> list() {
        return success(jdbc().query("SELECT id,name,prefix,delimiter,fields_json,enabled FROM wms_scan_rule WHERE tenant_id=? ORDER BY id DESC", (rs, row) -> {
            Rule rule = new Rule();
            rule.setId(rs.getLong("id")); rule.setName(rs.getString("name")); rule.setPrefix(rs.getString("prefix"));
            rule.setDelimiter(rs.getString("delimiter")); rule.setEnabled(rs.getBoolean("enabled"));
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
        return success(jdbc().update("DELETE FROM wms_scan_rule WHERE id=? AND tenant_id=?", id, tenant()) > 0);
    }

    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<Map<String, String>> preview(@RequestBody Rule rule) {
        try { return success(WmsDelimitedScanParser.parse(rule.getSample(), rule.getPrefix(), rule.getDelimiter(), rule.getFields())); }
        catch (IllegalArgumentException e) { return CommonResult.error(400, e.getMessage()); }
    }

    @GetMapping("/resolve")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<Map<String, String>> resolve(@RequestParam String value) {
        List<WmsScanRuleResolver.Rule> rules = list().getData().stream().filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .map(rule -> new WmsScanRuleResolver.Rule(rule.getPrefix(), rule.getDelimiter(), rule.getFields())).toList();
        try { return success(WmsScanRuleResolver.resolve(value, rules)); }
        catch (IllegalArgumentException e) { return CommonResult.error(400, e.getMessage()); }
    }
}
