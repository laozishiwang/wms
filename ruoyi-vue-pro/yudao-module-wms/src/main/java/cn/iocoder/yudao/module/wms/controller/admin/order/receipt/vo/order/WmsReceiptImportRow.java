package cn.iocoder.yudao.module.wms.controller.admin.order.receipt.vo.order;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import java.math.BigDecimal;
@Data
public class WmsReceiptImportRow {
    @ExcelProperty("规格编号") private String skuCode;
    @ExcelProperty("数量") private BigDecimal quantity;
    @ExcelProperty("单价") private BigDecimal price;
    @ExcelProperty("库位编号") private String locationCode;
    @ExcelProperty("批次号") private String batchNo;
    @ExcelProperty("生产日期") private String productionDate;
    @ExcelProperty("到期日期") private String expirationDate;
}
