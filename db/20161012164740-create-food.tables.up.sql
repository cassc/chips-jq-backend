use okok;

drop table if exists csb_food;
drop table if exists csb_exercise;

create table csb_food(
`id` bigint(20) not null primary key auto_increment,
food_id int(10) unsigned not null,
`name` varchar(128) not null,
quantity decimal(6,2) not null,
unit varchar(512) null,
metabolism mediumint unsigned not null,
calory mediumint unsigned not null comment 'total calory in kcal',
`date` date not null,
`upload_time` timestamp not null default current_timestamp,
`ftype` enum('breakfast','lunch','dinner','snacks') not null,
account_id int(11) not null,
role_id int(11) not null
) engine=innodb default charset=utf8;

alter table csb_food add constraint fk_rf_aid foreign key (account_id) references csb_account(id) on delete cascade;
alter table csb_food add constraint fk_rf_rid foreign key (role_id) references csb_role(id) on delete cascade;

create table csb_exercise(
`id` bigint(20) not null primary key auto_increment,
ex_id int(10) unsigned not null,
`name` varchar(256) not null,
duration smallint unsigned not null comment 'in mins',
calory mediumint  unsigned not null,
metabolism mediumint unsigned not null,
`date` date not null,
`upload_time` timestamp not null default current_timestamp,
account_id int(11) not null,
role_id int(11) not null
) engine=innodb default charset=utf8;

alter table csb_role_data_log modify `mtype` enum('weight','bp','bsl', 'food', 'exercise') not null comment '测量类型';
alter table csb_mdata modify `mtype` enum('weight','bp','bsl','food', 'exercise') not null;

alter table csb_exercise add constraint fk_ex_aid foreign key (account_id) references csb_account(id) on delete cascade;
alter table csb_exercise add constraint fk_ex_rid foreign key (role_id) references csb_role(id) on delete cascade;
alter table csb_food add index (date);
alter table csb_exercise add index (date);


