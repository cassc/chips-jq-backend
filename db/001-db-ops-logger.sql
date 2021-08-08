-- !!! 导入前先备份数据库 !!!
-- mysqldump -h 192.168.0.71 -u'root'  okok > okok-71.dump
drop table if exists csb_role_data_log;

create table csb_role_data_log (
id bigint not null auto_increment,
account_id int not null,
role_id  int not null,
opt enum('d','i') comment '操作类型，d:delete, i:insert',
data_id bigint not null comment 'csb_role_data的id',
weight_time timestamp not null comment '称重时间',
ts int unsigned not null comment '操作timestamp(秒)',
constraint pk_role_data_log primary key (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_role_data_log add index (account_id,role_id,ts);

-- 删除csb_role_data中(role_id + weight_time)重复的数据。
create temporary table tmpTable (id bigint);

insert  tmpTable
        (id)
select  id
from    csb_role_data yt
where   exists
        (
        select  *
        from    csb_role_data yt2
        where   yt2.role_id = yt.role_id
                and yt2.weight_time = yt.weight_time
                and yt2.id < yt.id
        );

delete
from    csb_role_data
where   ID in (select id from tmpTable);



create unique index uk_csb_role_weight_time on csb_role_data (role_id, weight_time);
