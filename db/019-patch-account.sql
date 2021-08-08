use okok;

alter table csb_account modify qq varchar(32) null;
alter table csb_account modify email varchar(50) null;
alter table csb_account modify sina_blog varchar(16) null;
alter table csb_account add weixin varchar(32) null comment 'weixin openid';
