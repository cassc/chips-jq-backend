use okok;

-- Modify csb_role
alter table csb_role     drop period_time;
alter table csb_role     drop icon_image_create_time;
alter table csb_role     drop sync_time;
alter table csb_role     add weight_init float(5,2) default null comment '初始体重' after weight_goal;

alter table csb_del_role drop period_time;
alter table csb_del_role drop icon_image_create_time;
alter table csb_del_role drop sync_time;
alter table csb_del_role add weight_init float(5,2) default null comment '初始体重' after weight_goal;


---- Alter csb_role_data and csb_del_role_data and rename
-- CREATE TABLE `csb_role_data` (
--   `id` bigint(20) NOT NULL AUTO_INCREMENT,
--   `weight` float(5,2) NOT NULL DEFAULT '0.00',
--   `bmi` float(9,2) NOT NULL DEFAULT '0.00',
--   `axunge` float(9,2) NOT NULL DEFAULT '0.00',
--   `bone` float(9,2) NOT NULL DEFAULT '0.00',
--   `muscle` float(9,2) NOT NULL DEFAULT '0.00',
--   `water` float(9,2) NOT NULL DEFAULT '0.00',
--   `metabolism` float(9,2) DEFAULT NULL,
--   `body_age` tinyint(4) NOT NULL DEFAULT '0',
--   `viscera` float(9,2) NOT NULL DEFAULT '0.00',
--   `weight_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
--   `account_id` int(11) NOT NULL,
--   `role_id` int(11) NOT NULL,
--   `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
--   `scaleweight` varchar(20) DEFAULT NULL,
--   `scaleproperty` int(11) DEFAULT '1',
--   `productid` int(11) DEFAULT '0',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_csb_role_weight_time` (`role_id`,`weight_time`),
--   KEY `R_11` (`account_id`),
--   KEY `R_12` (`role_id`),
--   KEY `R_14` (`weight_time`),
--   CONSTRAINT `csb_role_data_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `csb_account` (`id`),
--   CONSTRAINT `csb_role_data_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `csb_role` (`id`)
-- ) ENGINE=InnoDB AUTO_INCREMENT=524810 DEFAULT CHARSET=utf8;

alter table csb_role_data change sync_time upload_time timestamp not null;
alter table csb_role_data modify weight_time timestamp not null;
alter table csb_role_data modify `bmi` float(9,2);
alter table csb_role_data modify `axunge` float(9,2);
alter table csb_role_data modify `bone` float(9,2);
alter table csb_role_data modify `muscle` float(9,2);
alter table csb_role_data modify `weight` float(5,2);
alter table csb_role_data modify `water` float(9,2);
alter table csb_role_data modify `metabolism` float(9,2);
alter table csb_role_data modify `body_age` tinyint(4);
alter table csb_role_data modify `viscera` float(9,2);
alter table csb_role_data modify `scaleweight` varchar(20);

alter table csb_del_role_data change sync_time upload_time timestamp not null;
alter table csb_del_role_data modify weight_time timestamp not null;
alter table csb_del_role_data modify `bmi` float(9,2);
alter table csb_del_role_data modify `axunge` float(9,2);
alter table csb_del_role_data modify `bone` float(9,2);
alter table csb_del_role_data modify `muscle` float(9,2);
alter table csb_del_role_data modify `weight` float(5,2);
alter table csb_del_role_data modify `water` float(9,2);
alter table csb_del_role_data modify `metabolism` float(9,2);
alter table csb_del_role_data modify `body_age` tinyint(4);
alter table csb_del_role_data modify `viscera` float(9,2);
alter table csb_del_role_data modify `scaleweight` varchar(20);


rename table csb_role_data to csb_weight;
rename table csb_del_role_data to csb_del_weight;
-- no longer store deleted measure data


-- blood pressure
create table csb_bp (
id bigint unsigned not null primary key auto_increment,
`upload_time` timestamp not null,
`account_id` int(11) NOT NULL,
`role_id` int(11) NOT NULL,
sys smallint unsigned not null,
dia smallint unsigned not null,
hb smallint unsigned not null,
`measure_time` timestamp not null,
constraint fk_bp_aid foreign key (account_id) references csb_account(id),
constraint fk_bp_rid foreign key (role_id) references csb_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- blood sugar level
create table csb_bsl (
id bigint unsigned not null primary key auto_increment,
`upload_time` timestamp not null,
`account_id` int(11) NOT NULL,
`role_id` int(11) NOT NULL,
bsl float(5,2) not null,
`description` enum('pre-meal','post-meal') not null,
`measure_time` timestamp not null,
constraint fk_bsl_aid foreign key (account_id) references csb_account(id),
constraint fk_bsl_rid foreign key (role_id) references csb_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- blood pressure
create table csb_del_bp (
id bigint unsigned not null primary key auto_increment,
`upload_time` timestamp not null,
`account_id` int(11) NOT NULL,
`role_id` int(11) NOT NULL,
sys smallint unsigned not null,
dia smallint unsigned not null,
hb smallint unsigned not null,
`measure_time` timestamp not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- blood sugar level
create table csb_del_bsl (
id bigint unsigned not null primary key auto_increment,
`upload_time` timestamp not null,
`account_id` int(11) NOT NULL,
`role_id` int(11) NOT NULL,
bsl float(5,2) not null,
`description` enum('pre-meal','post-meal') not null,
`measure_time` timestamp not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- alter role_data insert/delete logger
alter table csb_role_data_log change weight_time measure_time timestamp not null comment '称重时间';
alter table csb_role_data_log add mtype enum('weight','bp','bsl') not null comment '测量类型' after role_id;


create view v_mdata as 
select id, weight_time measure_time, account_id, role_id, 'weight' as mtype from csb_weight
union all
select id, measure_time, account_id, role_id, 'bp' as mtype from csb_bp
union all
select id, measure_time, account_id, role_id, 'bsl' as mtype from csb_bsl;
