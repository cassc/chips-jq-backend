use okok;

create table csb_mdata (
id bigint unsigned not null primary key auto_increment,
mid bigint unsigned not null,
`measure_time` timestamp not null,
`account_id` int(11) not null,
`role_id` int(11) not null,
mtype enum('weight','bp','bsl') not null,
constraint uk_md unique key (mid,mtype)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_mdata add index (measure_time);

insert into csb_mdata(mid,measure_time,account_id,role_id,mtype)
select id,weight_time,account_id,role_id, 'weight' as mtype from csb_weight;

insert into csb_mdata(mid,measure_time,account_id,role_id,mtype)
select id,measure_time,account_id,role_id, 'bp' as mtype from csb_bp;

insert into csb_mdata(mid,measure_time,account_id,role_id,mtype)
select id,measure_time,account_id,role_id, 'bsl' as mtype from csb_bsl;
