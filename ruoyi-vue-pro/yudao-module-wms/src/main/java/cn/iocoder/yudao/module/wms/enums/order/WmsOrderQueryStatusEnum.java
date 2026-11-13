package cn.iocoder.yudao.module.wms.enums.order;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;

/** States accepted by order list filters, including orders awaiting approval. */
public enum WmsOrderQueryStatusEnum implements ArrayValuable<Integer> {
    VALUE;

    @Override
    public Integer[] array() {
        return new Integer[]{0, 1, 4, 5};
    }
}
