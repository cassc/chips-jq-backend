use mok;

create table mkComment (
id int unsigned not null primary key auto_increment,
bid int unsigned not null comment '广播id',
account_id int null comment '评论人',
role_id int  null comment '评论人',
commenter_nickname varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
commenter_icon varchar(54) null,
ts bigint unsigned not null comment 'comment timestamp',
content text character set utf8mb4 collate utf8mb4_unicode_ci null comment '内容'
)  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 collate  utf8mb4_unicode_ci;

create table mkLikedArticle (
id bigint unsigned  primary key not null auto_increment,
bid bigint unsigned not null,
account_id int not null,
constraint fk_likes_bid foreign key (bid) references mkBroadcast(id),
constraint fk_likes_a_id foreign key (account_id) references okok.csb_account(id),
constraint uk_likes_aid_bid unique key (bid,account_id)
)  ENGINE=InnoDB DEFAULT CHARSET=utf8;
