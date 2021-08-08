use okok;

alter table csb_role modify `weight_goal` float(6,3) DEFAULT '55.000';
alter table csb_role modify `weight_init` float(6,3) DEFAULT NULL COMMENT '初始体重';
