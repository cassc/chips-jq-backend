use okok;

drop table if exists csb_mblog_fav;
drop table if exists csb_mblog_liked;
drop table if exists csb_relation;
drop table if exists csb_mblog;

create table csb_mblog (
id int unsigned not null auto_increment primary key,
parent_id int unsigned null,
account_id int not null,
pic text null,
ts bigint unsigned not null,
hidden enum('y', 'n') not null default 'n',
sqn int unsigned not null default 0,
msg text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_mblog add constraint `fk_mblog_aid` foreign key (account_id) references csb_account(`id`) on delete cascade;
alter table csb_mblog add constraint `fk_mblog_parent_id` foreign key (parent_id) references csb_mblog(`id`) on delete cascade;

create table csb_mblog_liked(
id int unsigned not null auto_increment primary key,
mblog_id int unsigned not null,
account_id int not null,
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_mblog_liked add constraint `fk_mblog_liked_mid` foreign key (mblog_id) references csb_mblog(`id`) on delete cascade;
alter table csb_mblog_liked add constraint `fk_mblog_liked_aid` foreign key (account_id) references csb_account(`id`) on delete cascade;
create unique index uk_mblog_liked on csb_mblog_liked (mblog_id, account_id);

create table csb_relation(
id int unsigned primary key auto_increment,
account_id int not null,
follower_id int not null,
gid varchar(32) not null,
ts bigint unsigned not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_relation add constraint `fk_relation_aid` foreign key (account_id) references csb_account(`id`) on delete cascade;
alter table csb_relation add constraint `fk_relation_flw` foreign key (follower_id) references csb_account(`id`) on delete cascade;
create unique index uk_mblog_flw on csb_relation (account_id, follower_id);
create index idx_relation_fid on csb_relation (gid(6));


-- create table csb_mblog_fav (
-- id int unsigned primary key,
-- mblog_id int unsigned not null,
-- account_id int not null,
-- ts bigint unsigned not null
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- alter table csb_mblog_fav add constraint `fk_mblog_fav_aid` foreign key (account_id) references csb_account(`id`) on delete cascade;
-- alter table csb_mblog_fav add constraint `fk_mblog_fav_mid` foreign key (mblog_id) references csb_mblog(`id`) on delete cascade;
-- create unique index uk_mblog_fav on csb_mblog_fav (mblog_id, account_id);


alter table csb_account add signature varchar(512)  character set utf8mb4 collate utf8mb4_unicode_ci default '';
