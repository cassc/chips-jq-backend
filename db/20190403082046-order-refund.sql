use nh;

-- alter table s_order modify `status` enum('pending','paid','complete','cancel','hide','delivery', 'refunding', 'refunded') NOT NULL;

create table s_order_product_return (
id int unsigned primary key auto_increment,
aid int(11) not null comment 'buyer id',
oid int unsigned not null comment 'order id',
`sid` int(10) unsigned NOT NULL,
ouid varchar(64) not null,
pid int unsigned not null,
quantity int unsigned not null,
tpe enum('return', 'exchange', 'refund'),
ship_id varchar(64) null,
ship_provider varchar(24) null,
status enum('pending', 'success', 'processing', 'reject') not null,
reason text null,
ts bigint unsigned not null comment 'creation time',
refund_payment_id int unsigned not null default 0,
refund_success_ts bigint unsigned not null default 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_order_product_return add constraint fk_opr_oid foreign key (oid) references s_order(id) on delete cascade;
alter table s_order_product_return add constraint fk_opr_aid foreign key (aid) references csb_account(id) on delete cascade;
alter table s_order_product_return add index (ouid, sid,pid);

alter table s_payment add index(ouid);
alter table s_payment add column `source` enum('yl','kjt') null;
alter table s_payment add column `event` enum('pay-init', 'pay-success','refund-int', 'refund-success') null;

alter table s_seller add `address` varchar(512) DEFAULT NULL;
alter table s_seller add `zipcode` varchar(8) DEFAULT NULL;
