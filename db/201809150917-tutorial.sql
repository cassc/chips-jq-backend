use nh;

drop table if exists csb_training;
drop table if exists csb_tutorial_video;
drop table if exists csb_video;
drop table if exists csb_tutorial;


create table csb_tutorial(
id int unsigned primary key auto_increment,
title varchar(64) not null comment '标题',
dir varchar(14) not null comment '文件夹标题yyyyMMddHHmmSS',
tag varchar(128) not null comment '标签以逗号分开',
cover varchar(64) null comment '封面图',
rqdev enum('y', 'n') not null comment '是否要器械',
duration int unsigned not null comment '课程总长度',
advice varchar(1024) null comment '建议',
audience varchar(128) not null comment '适合人群',
warning varchar(1024) null comment '注意事项',
calory float not null comment '热量消耗',
vmeta text null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table csb_tutorial add constraint uk_tt_dir unique key (dir);

create table csb_tutorial_video (
id int unsigned primary key auto_increment,
tid int unsigned not null comment 'training id',
title varchar(64) not null comment 'video title',
seq smallint unsigned not null comment 'video sequence id' 
)ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_tutorial_video add constraint fk_tv_tid foreign key (tid) references csb_tutorial(id) on delete cascade;


alter table csb_role_data_log modify `mtype` enum('weight','bp','bsl', 'food', 'exercise', 'training') not null comment '测量类型';
alter table csb_mdata modify `mtype` enum('weight','bp','bsl','food', 'exercise', 'training') not null comment '测量类型';

create table csb_training(
`id` bigint(20) not null primary key auto_increment,
tid int unsigned not null comment 'tutorial id',
calory float not null comment 'total calory in kcal',
metabolism float(9,2) default 0,
`measure_time` timestamp not null,
`upload_time` timestamp not null default current_timestamp,
account_id int(11) not null,
role_id int(11) not null
) engine=innodb default charset=utf8;

alter table csb_training add constraint fk_tn_aid foreign key (account_id) references csb_account(id) on delete cascade;
alter table csb_training add constraint fk_tn_rid foreign key (role_id) references csb_role(id) on delete cascade;
alter table csb_training add constraint uk_tn_rid_time unique key (role_id, measure_time);

