use okok;

-- 李勋 05-16 09:28
-- age年龄 tinyint
-- sex性别 tinyint
-- height身高    tinyint
-- rn8电极电阻 varchar(200)

alter table csb_weight add age tinyint unsigned default 0;
alter table csb_weight add sex tinyint unsigned default 0;
alter table csb_weight add height tinyint unsigned default 0;
alter table csb_weight add rn8 varchar(200) default null;
