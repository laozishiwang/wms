package cn.iocoder.yudao.module.wms.controller.admin.order.receipt.vo.order;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import java.math.BigDecimal;
@Data
public class WmsReceiptImportRow {
    @ExcelProperty("规格编号") private String skuCode;
    @ExcelProperty("数量") private BigDecimal quantity;
    @ExcelProperty("单价") private BigDecimal price;
    @ExcelProperty("成本价") private BigDecimal costPrice;
    @ExcelProperty("金额") private BigDecimal totalPrice;
    @ExcelProperty("序列号") private String serialNumbers;
    @ExcelProperty("库位编号") private String locationCode;
    @ExcelProperty("批次号") private String batchNo;
    @ExcelProperty("生产日期") private String productionDate;
    @ExcelProperty("到期日期") private String expirationDate;
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
    @ExcelProperty("销售价") private BigDecimal salesPrice;
}
