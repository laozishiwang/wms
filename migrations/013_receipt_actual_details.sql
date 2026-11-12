ALTER TABLE wms_receipt_order_detail
  ADD COLUMN pallet_no varchar(64) NULL,
  ADD COLUMN box_no varchar(64) NULL,
  ADD COLUMN roll_no varchar(64) NULL,
  ADD COLUMN net_length decimal(20, 4) NULL,
  ADD COLUMN gross_length decimal(20, 4) NULL,
  ADD COLUMN net_weight decimal(20, 4) NULL,
  ADD COLUMN gross_weight decimal(20, 4) NULL,
  ADD COLUMN length decimal(20, 4) NULL,
  ADD COLUMN width decimal(20, 4) NULL,
  ADD COLUMN height decimal(20, 4) NULL,
  ADD COLUMN sales_price decimal(20, 4) NULL;
