use nh;

drop table if exists s_payment;
drop table if exists s_order_product;
drop table if exists s_top_product;
drop table if exists s_cart;
drop table if exists s_order;
drop table if exists s_product;
drop table if exists s_seller;
drop table if exists csb_address;


create table s_seller (
id int unsigned primary key auto_increment,
title varchar(128) not null,
description text null,
owner varchar(128) not null,
phone varchar(26) not null,
license varchar(64) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


create table s_product(
id int unsigned primary key auto_increment,
title varchar(1024) not null,
price int not null,
score int not null,
tag varchar(128) not null,
description text not null,
cover text not null comment '封面图',
images text not null comment 'content images',
sid int unsigned not null comment 'seller id',
status enum('hide','show') not null default 'show',
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_product add constraint `fk_prod_sid` foreign key (sid) references s_seller(`id`);

create table s_top_product (
id int unsigned primary key auto_increment,
pid int unsigned,
loc int not null,
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_top_product add constraint `fk_top_prod_pid` foreign key (pid) references s_product(`id`);

create table s_order(
id int unsigned primary key auto_increment,
ouid varchar(64) unique not null,
aid int(11) not null,
fullname varchar(12) not null comment 'buyer name',
phone varchar(26) not null comment 'buyer phone',
province varchar(20) not null,
city varchar(20) null,
area varchar(32) null,
address varchar(512) not null,
zipcode varchar(8) not null default '000000',
price int not null,
score int not null,
status enum ('pending', 'paid', 'complete', 'cancel', 'hide') not null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_order add constraint fk_order_aid foreign key (aid) references csb_account(id);

create table s_cart(
id int unsigned primary key auto_increment,
aid int(11) not null,
pid int unsigned not null comment 'product id',
quantity int unsigned not null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_cart add constraint fk_cart_aid foreign key (aid) references csb_account(id);
alter table s_cart add constraint fk_cart_oid foreign key (pid) references s_product(id);


create table s_order_product(
id int unsigned primary key auto_increment,
aid int(11) not null comment 'buyer id',
pid int unsigned not null,
oid int unsigned not null comment 'order id',
sid int unsigned not null comment 'seller id',
quantity int unsigned not null,
title varchar(1024) not null,
price int not null,
score int not null,
tag varchar(128) not null,
description text not null,
cover text not null comment '封面图',
images text not null comment 'content images',
ship_id varchar(64) null,
ship_provider varchar(24) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table s_order_product add constraint `fk_order_prod_sid` foreign key (sid) references s_seller(`id`);
alter table s_order_product add constraint fk_order_prod_aid foreign key (aid) references csb_account(id) on delete cascade;
alter table s_order_product add constraint fk_order_prod_oid foreign key (oid) references s_order(id) on delete cascade;


create table s_payment (
id int unsigned primary key auto_increment,
ouid varchar(64) null,
raw text not null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



create table csb_address(
id int unsigned primary key auto_increment,
aid int(11) not null comment 'account id',
fullname varchar(12) not null comment 'buyer name',
phone varchar(26) not null comment 'buyer phone',
province varchar(20) not null,
city varchar(20) null,
area varchar(32) null,
address varchar(512) not null,
zipcode varchar(8) not null,
isdefault enum('y','n') not null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

