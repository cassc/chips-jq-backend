-- Apply this before patching 006

use okok;
alter table csb_app_info modify upgrade_time timestamp default current_timestamp;
alter table csb_app_info add mu_version varchar(10) default null comment '最低可用版本号';
alter table csb_app_info add region enum('china','global') not null default 'china' after company_id;
