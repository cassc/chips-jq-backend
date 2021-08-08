use nh;

alter table csb_jifen drop key uk_jifen_source_date;
alter table csb_jifen modify account_id int(11);

alter table csb_jifen add constraint fk_jf_aid foreign key (account_id) references csb_account(id) on delete cascade;
alter table csb_jifen add note varchar(64) null;
