use okok;

create table csb_db_op (
id bigint unsigned not null primary key auto_increment,
company_id int null,
ops blob not null,
ts bigint unsigned not null
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;

