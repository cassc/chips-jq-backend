
create table s_payment (
id int unsigned primary key auto_increment,
ouid varchar(64) null,
raw text not null,
ts bigint unsigned not null comment 'creation time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
