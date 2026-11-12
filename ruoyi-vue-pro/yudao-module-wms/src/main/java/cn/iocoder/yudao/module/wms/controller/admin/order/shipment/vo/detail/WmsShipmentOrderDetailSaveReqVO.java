package cn.iocoder.yudao.module.wms.controller.admin.order.shipment.vo.detail;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - WMS 出库单明细保存 Request VO")
@Data
public class WmsShipmentOrderDetailSaveReqVO {

    @Schema(description = "编号", example = "1024")
    private Long id;

    @Schema(description = "SKU 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @Schema(description = "出库数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "100.00")
    @NotNull(message = "出库数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "出库数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "单价", example = "100.00")
    @DecimalMin(value = "0", message = "单价不能小于 0")
    private BigDecimal price;

    @Schema(description = "行金额", example = "1000.00")
    @DecimalMin(value = "0", message = "行金额不能小于 0")
    private BigDecimal totalPrice;

    /** 库位编号。 */
    private Long locationId;
    /** 序列号列表。 */
    private List<String> serialNos;
    /** 批次号；为空表示历史未分配库存。 */
    @Size(max = 64, message = "批次号不能超过 64 个字符")
    private String batchNo;

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo == null || batchNo.isBlank() ? null : batchNo;
    }
    /** 生产日期。 */
    private LocalDateTime productionDate;
    /** 到期日期。 */
    private LocalDateTime expirationDate;

}
