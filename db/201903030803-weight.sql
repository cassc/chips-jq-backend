


alter table csb_weight add resistance varchar(512) null, ALGORITHM=INPLACE, LOCK=NONE;
alter table csb_weight add iseightr enum('y','n') default 'n', ALGORITHM=INPLACE, LOCK=NONE;
