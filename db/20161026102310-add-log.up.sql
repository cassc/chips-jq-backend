use okok;

drop table if exists csb_api_log;

create table csb_api_log(
`id` bigint(20) not null primary key auto_increment,
`aid` bigint(20) unsigned null,
`method` varchar(10)  not null,
`uri` varchar(256) not null,
`start` bigint unsigned not null,
`time` int unsigned not null,
`deviceid` varchar(256) null,
`msg` varchar(1024) null
) engine=innodb default charset=utf8;

