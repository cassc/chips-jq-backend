use okok;

update csb_weight set body_age=null where body_age >0;
alter table csb_weight modify `body_age` tinyint(4)  unsigned DEFAULT NULL;
alter table csb_weight add `r1` float(6,2)  DEFAULT NULL;
alter table csb_del_weight add `r1` float(6,2)  DEFAULT NULL;
