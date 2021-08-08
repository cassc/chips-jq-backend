use nh;


drop table if exists csb_tutorial_fav;

create table csb_tutorial_fav (
id int unsigned primary key auto_increment,
tid int unsigned not null comment 'training id',
aid int(11) not null
)ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table csb_tutorial_fav add constraint fk_tf_aid foreign key (aid) references csb_account(id) on delete cascade;
alter table csb_tutorial_fav add constraint fk_tf_tid foreign key (tid) references csb_tutorial(id) on delete cascade;
alter table csb_tutorial_fav add constraint uk_tf_aid_tid unique key (tid,aid);
