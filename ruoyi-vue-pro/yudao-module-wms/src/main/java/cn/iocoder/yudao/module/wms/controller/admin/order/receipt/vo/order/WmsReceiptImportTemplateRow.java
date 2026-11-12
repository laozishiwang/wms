package cn.iocoder.yudao.module.wms.controller.admin.order.receipt.vo.order;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import java.math.BigDecimal;

/** 模板顺序与入库明细一致；物品及规格名称供核对，导入以规格编号匹配。 */
@Data
public class WmsReceiptImportTemplateRow {
    @ExcelProperty("物品名称") private String itemName;
    @ExcelProperty("物品编号") private String itemCode;
    @ExcelProperty("规格名称") private String skuName;
    @ExcelProperty("规格编号") private String skuCode;
    @ExcelProperty("序列号") private String serialNumbers;
    @ExcelProperty("库位编号") private String locationCode;
    @ExcelProperty("批次号") private String batchNo;
    @ExcelProperty("托盘号") private String palletNo;
    @ExcelProperty("箱号") private String boxNo;
    @ExcelProperty("卷号") private String rollNo;
    @ExcelProperty("净长(cm)") private BigDecimal netLength;
    @ExcelProperty("毛长(cm)") private BigDecimal grossLength;
    @ExcelProperty("净重(kg)") private BigDecimal netWeight;
    @ExcelProperty("毛重(kg)") private BigDecimal grossWeight;
    @ExcelProperty("长(cm)") private BigDecimal length;
    @ExcelProperty("宽(cm)") private BigDecimal width;
    @ExcelProperty("高(cm)") private BigDecimal height;
    @ExcelProperty("生产日期") private String productionDate;
    @ExcelProperty("到期日期") private String expirationDate;
    @ExcelProperty("数量") private BigDecimal quantity;
    @ExcelProperty("成本价") private BigDecimal costPrice;
    @ExcelProperty("销售价") private BigDecimal salesPrice;
    @ExcelProperty("金额") private BigDecimal totalPrice;
}
