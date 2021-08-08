use okok;
alter table csb_bsl drop description;
alter table csb_del_bsl drop description;

alter table csb_bsl add `description` tinyint not null after bsl;
alter table csb_del_bsl add `description` tinyint not null after bsl;
