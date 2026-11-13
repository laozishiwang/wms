package cn.iocoder.yudao.module.wms.controller.admin.order.shipment.vo.order;

import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import java.math.BigDecimal;

@Data
public class WmsShipmentImportRow {
    @ExcelProperty("规格编号") private String skuCode;
    @ExcelProperty("序列号") private String serialNumbers;
    @ExcelProperty("库位编号") private String locationCode;
    @ExcelProperty("批次号") private String batchNo;
    @ExcelProperty("到货记录ID") private Long arrivalId;
    @ExcelProperty("出库数量") private BigDecimal quantity;
    @ExcelProperty("单价") private BigDecimal price;
    @ExcelProperty("金额") private BigDecimal totalPrice;
}
