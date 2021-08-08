-- Tue 10 Nov 2015 09:41:58 AM HKT
-- !!!!!APPLIES to intl_okok ONLY!!!!!

-- apply 001, 002, 004 patches

-- fix time incorrectly auto updates 
alter table csb_account modify register_time timestamp default current_timestamp;

alter table csb_company modify create_time timestamp default '0000-00-00 00:00:00';
alter table csb_company modify modify_time timestamp default current_timestamp on update CURRENT_TIMESTAMP;
