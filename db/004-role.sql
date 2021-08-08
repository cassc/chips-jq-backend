-- 添加用于记录已删除数据的表
drop table if exists csb_del_role;
create table csb_del_role (
  `id` int(11) NOT NULL comment '与原role_id相同',
  `nickname` varchar(30) NOT NULL,
  `height` int(11) NOT NULL,
  `birthday` date NOT NULL,
  `account_id` int(11) NOT NULL,
  `sex` enum('男','女') NOT NULL,
  `create_time` timestamp NOT NULL,
  `modify_time` timestamp NOT NULL,
  `icon_image_path` varchar(32) NOT NULL,
  `icon_image_create_time` timestamp NOT NULL,
  `current_state` tinyint(4) NOT NULL,
  `period_time` date NOT NULL,
  `sync_time` timestamp NOT NULL,
  `weight_goal` float(5,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`id`)
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;

drop table if exists csb_del_role_data;
create table csb_del_role_data (
  `id` int(11) NOT NULL comment '与原role_data.id相同',
  `weight` float(5,2) NOT NULL,
  `bmi` float(5,2) NOT NULL,
  `axunge` float(5,2) NOT NULL,
  `bone` float(4,2) NOT NULL,
  `muscle` float(4,2) NOT NULL,
  `water` float(4,2) NOT NULL,
  `metabolism` float(9,2) DEFAULT NULL,
  `body_age` tinyint(4) NOT NULL,
  `viscera` float(4,2) NOT NULL,
  `weight_time` timestamp NOT NULL,
  `account_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  `sync_time` timestamp NOT NULL,
  PRIMARY KEY (`id`)
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;



CREATE TABLE if not exists  `csb_del_role_day_data` (
  `id` int(11) NOT NULL ,
  `weight` float(5,2) NOT NULL DEFAULT '0.00',
  `axunge` float(5,2) NOT NULL DEFAULT '0.00',
  `bmi` float(5,2) NOT NULL DEFAULT '0.00',
  `bone` float(4,2) NOT NULL DEFAULT '0.00',
  `muscle` float(4,2) NOT NULL DEFAULT '0.00',
  `water` float(4,2) NOT NULL DEFAULT '0.00',
  `metabolism` float(4,2) NOT NULL DEFAULT '0.00',
  `body_age` tinyint(4) NOT NULL DEFAULT '0',
  `viscera` float(4,2) NOT NULL DEFAULT '0.00',
  `weight_time` date DEFAULT NULL,
  `account_id` int(11) DEFAULT NULL,
  `role_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE if not exists  `csb_del_role_month_data` (
  `id` int(11) NOT NULL ,
  `weight` float(5,2) NOT NULL DEFAULT '0.00',
  `axunge` float(5,2) NOT NULL DEFAULT '0.00',
  `bmi` float(5,2) NOT NULL DEFAULT '0.00',
  `bone` float(4,2) NOT NULL DEFAULT '0.00',
  `muscle` float(4,2) NOT NULL DEFAULT '0.00',
  `water` float(4,2) NOT NULL DEFAULT '0.00',
  `metabolism` float(4,2) NOT NULL DEFAULT '0.00',
  `body_age` tinyint(4) NOT NULL DEFAULT '0',
  `viscera` float(4,2) NOT NULL DEFAULT '0.00',
  `weight_time` date DEFAULT NULL,
  `account_id` int(11) DEFAULT NULL,
  `role_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



CREATE TABLE if not exists  `csb_del_role_week_data` (
  `id` int(11) NOT NULL,
  `weight` float(5,2) NOT NULL DEFAULT '0.00',
  `axunge` float(5,2) NOT NULL DEFAULT '0.00',
  `bmi` float(5,2) NOT NULL DEFAULT '0.00',
  `bone` float(4,2) NOT NULL DEFAULT '0.00',
  `muscle` float(4,2) NOT NULL DEFAULT '0.00',
  `water` float(4,2) NOT NULL DEFAULT '0.00',
  `metabolism` float(4,2) NOT NULL DEFAULT '0.00',
  `body_age` tinyint(4) NOT NULL DEFAULT '0',
  `viscera` float(4,2) NOT NULL DEFAULT '0.00',
  `weight_time` date DEFAULT NULL,
  `account_id` int(11) DEFAULT NULL,
  `role_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 记录增删角色
drop table if exists csb_role_log;

create table csb_role_log (
id bigint not null auto_increment,
account_id int not null,
role_id  int not null,
opt enum('d','i') comment '操作类型，d:delete, i:insert',
ts int unsigned not null comment '操作timestamp(秒)',
constraint pk_role_log primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_role_log add index (account_id,role_id,ts);


DELIMITER $$

DROP TRIGGER IF EXISTS `insert_role_log` $$

CREATE TRIGGER `insert_role_log` AFTER INSERT on `csb_role`
FOR EACH ROW
BEGIN
    INSERT INTO csb_role_log
    (`role_id` , `opt` , `ts` ,`account_id`)
    VALUES
    (NEW.id, "i", unix_timestamp(), NEW.account_id);
END;$$


DROP TRIGGER IF EXISTS `delete_role_log` $$

CREATE TRIGGER `delete_role_log` AFTER DELETE on `csb_role`
FOR EACH ROW
BEGIN
    INSERT INTO csb_role_log
    (`role_id` , `opt`, `ts`, account_id)
    VALUES
    (OLD.id, "d", unix_timestamp(), OLD.account_id);
END;$$


DELIMITER ;
