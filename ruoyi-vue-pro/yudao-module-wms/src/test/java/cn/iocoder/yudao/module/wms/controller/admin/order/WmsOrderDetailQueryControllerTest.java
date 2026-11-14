package cn.iocoder.yudao.module.wms.controller.admin.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WmsOrderDetailQueryControllerTest {
    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void shipmentDateFilterUsesArrivalWhenShipmentDateIsEmpty() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:detail_dates;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var sql = new JdbcTemplate(dataSource);
        sql.execute("CREATE TABLE wms_shipment_order (id BIGINT, tenant_id BIGINT, deleted INT, no VARCHAR(64), type INT, status INT, order_time TIMESTAMP, biz_order_no VARCHAR(64), remark VARCHAR(64), merchant_id BIGINT)");
        String detail = "id BIGINT, tenant_id BIGINT, deleted INT, order_id BIGINT, sku_id BIGINT, warehouse_id BIGINT, location_id BIGINT, batch_no VARCHAR(64), arrival_id BIGINT, quantity DECIMAL, price DECIMAL, total_price DECIMAL, serial_nos VARCHAR(100), production_date TIMESTAMP, expiration_date TIMESTAMP, pallet_no VARCHAR(64), box_no VARCHAR(64), roll_no VARCHAR(64), net_length DECIMAL, gross_length DECIMAL, net_weight DECIMAL, gross_weight DECIMAL, length DECIMAL, width DECIMAL, height DECIMAL, sales_price DECIMAL";
        sql.execute("CREATE TABLE wms_shipment_order_detail (" + detail + ")");
        sql.execute("CREATE TABLE wms_receipt_order_detail (" + detail + ")");
        sql.execute("CREATE TABLE wms_item_sku (id BIGINT, tenant_id BIGINT, deleted INT, code VARCHAR(64), name VARCHAR(64), item_id BIGINT)");
        sql.execute("CREATE TABLE wms_item (id BIGINT, tenant_id BIGINT, deleted INT, code VARCHAR(64), name VARCHAR(64), unit VARCHAR(64))");
        sql.execute("CREATE TABLE wms_warehouse (id BIGINT, tenant_id BIGINT, deleted INT, name VARCHAR(64))");
        sql.execute("CREATE TABLE wms_merchant (id BIGINT, tenant_id BIGINT, deleted INT, name VARCHAR(64))");
        sql.execute("CREATE TABLE wms_storage_place (id BIGINT, tenant_id BIGINT, warehouse_id BIGINT, code VARCHAR(64), name VARCHAR(64))");
        sql.update("INSERT INTO wms_shipment_order(id,tenant_id,deleted,no) VALUES(1,1,0,'OUT-1'),(2,2,0,'OUT-2')");
        sql.update("INSERT INTO wms_receipt_order_detail(id,tenant_id,deleted,production_date,expiration_date) VALUES(10,1,0,'2026-01-01 00:00:00','2027-01-01 00:00:00'),(20,2,0,'2026-03-01 00:00:00','2027-03-01 00:00:00')");
        sql.update("INSERT INTO wms_shipment_order_detail(id,tenant_id,deleted,order_id,arrival_id,quantity) VALUES(101,1,0,1,10,1),(102,2,0,2,20,1)");
        var controller = new WmsOrderDetailQueryController();
        ReflectionTestUtils.setField(controller, "jdbc", new NamedParameterJdbcTemplate(dataSource));
        TenantContextHolder.setTenantId(1L);

        var matching = controller.page("shipment", 1, 10, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of("productionDateFrom", "2026-01-01 00:00:00", "expirationDateTo", "2027-01-01 23:59:59")).getData();
        assertEquals(1L, matching.getTotal());
        assertEquals("2026-01-01 00:00:00.0", matching.getList().get(0).get("PRODUCTIONDATE").toString());
        var excluded = controller.page("shipment", 1, 10, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, Map.of("productionDateFrom", "2026-02-01 00:00:00")).getData();
        assertEquals(0L, excluded.getTotal());
    }
}
