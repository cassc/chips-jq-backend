use nh;

alter table csb_tutorial modify cover varchar(1024) null comment '封面图';

alter table csb_tutorial_video add images text null comment '分解图';
