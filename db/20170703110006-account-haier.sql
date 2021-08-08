use nh;

alter table csb_account add haier varchar(64) null after weixin;
alter table csb_account add status tinyint default 1 ;
alter table csb_account add appbg varchar(256) default null ;

create table csb_last_log(
id int unsigned primary key auto_increment,
ua varchar(1024) not null,
aid int(11) not null,
ip varchar(128) not null,
device varchar(255) default "",
app varchar(255) default "",
`scale` varchar(255) default "",
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table csb_last_log add constraint `fk_last_log_aid` foreign key (aid) references csb_account(`id`) on delete cascade;
create unique index uk_csb_lastlog_aid on csb_last_log (aid);


create table csb_banner(
id int unsigned primary key auto_increment,
pic varchar(512) not null,
url varchar(512) not null,
pos tinyint default 0,
content varchar(2048) not null,
title varchar(128) not null,
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


create table csb_act(
id int unsigned primary key auto_increment,
title varchar(128) not null,
slogan varchar(128) not null,
pica varchar(512) not null,
picb varchar(512) not null,
howtojoin varchar(2048) not null,
ts_start bigint unsigned default 0,
ts_end bigint unsigned default 0,
reward varchar(512) not null,
description varchar(2048) not null,
status enum('online', 'offline', 'pending') default 'online',
`position` smallint unsigned default 0,
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

alter table csb_mblog add weight int default 0;
alter table csb_mblog add ispop tinyint default 0;
alter table csb_mblog add pop_weight int default 0;

create table csb_jifen(
id int unsigned primary key auto_increment,
score int unsigned not null,
`source` int unsigned not null,
`date` varchar(12) not null,
ts bigint unsigned not null,
account_id int unsigned not null
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

create unique index uk_jifen_source_date on csb_jifen (`source`, `date`);

alter table csb_mblog add tag varchar(24) null;
alter table csb_mblog add act_id int default 0;

