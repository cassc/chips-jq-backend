use okok;

alter table csb_role change nickname nickname varchar(30)  character set utf8mb4 collate utf8mb4_unicode_ci;
alter table csb_role modify weight_goal float(5,2) default 0 ;
alter table csb_del_role modify weight_goal float(5,2) default 0 ;
alter table csb_account add weixin varchar(32) null after qq;

alter table csb_account modify email varchar(50);
alter table csb_account modify phone char(11);
alter table csb_account modify qq varchar(32);
alter table csb_account modify sina_blog varchar(32);
alter table csb_role modify icon_image_path varchar(54);
alter table csb_del_role modify icon_image_path varchar(54);
