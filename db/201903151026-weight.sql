use nh;

create table w_extra (
id bigint unsigned primary key auto_increment,
wid bigint(20) not null,
BFR float(9,2) default 0,
BMR float(9,2) default 0,
FC float(9,2) default 0,
BodyAge float(9,2) default 0,
FM float(9,2) default 0,
MC float(9,2) default 0,
MSW float(9,2) default 0,
PM float(9,2) default 0,
SLM float(9,2) default 0,
SMM float(9,2) default 0,
Score float(9,2) default 0,
TF float(9,2) default 0,
TFR float(9,2) default 0,
VFR float(9,2) default 0,
WC float(9,2) default 0,
RABFR float (9,2) default 0,
RASLM float (9,2) default 0,
LABFR float (9,2) default 0,
TRBFR float (9,2) default 0,
TRSLM float (9,2) default 0,
RLBFR float (9,2) default 0,
RLSLM float (9,2) default 0,
LLBFR float (9,2) default 0,
LLSLM float (9,2) default 0,
LASLM float (9,2) default 0,
WHR float (9,2) default 0,
v10_resistance varchar(128) default ''
) ENGINE=InnoDB;

alter table w_extra add constraint fk_we foreign key (wid) references csb_weight(id) on delete cascade;

