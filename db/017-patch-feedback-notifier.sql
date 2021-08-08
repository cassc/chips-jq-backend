use okok;

alter table csb_customer_feedback add notifier varchar(64) null comment '处理人邮件或手机号，为空时表示未发送给此人';
alter table csb_customer_feedback add notify_ts bigint not null default 0 comment '通知时间';
