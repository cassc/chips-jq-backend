use nh;


CREATE TABLE `csb_flash_remind` (
`id` int(11) NOT NULL primary key AUTO_INCREMENT,
`account_id` int(11) DEFAULT NULL,
`remind_time` varchar(5) DEFAULT NULL,
`is_open` tinyint(4) NOT NULL DEFAULT '0',
`mon_open` tinyint(4) NOT NULL DEFAULT '0',
`tue_open` tinyint(4) NOT NULL DEFAULT '0',
`wed_open` tinyint(4) NOT NULL DEFAULT '0',
`thu_open` tinyint(4) NOT NULL DEFAULT '0',
`fri_open` tinyint(4) NOT NULL DEFAULT '0',
`sat_open` tinyint(4) NOT NULL DEFAULT '0',
`sun_open` tinyint(4) NOT NULL DEFAULT '0',
`once_open` tinyint(4) NOT NULL DEFAULT '1'
) ENGINE=InnoDB AUTO_INCREMENT=10117 DEFAULT CHARSET=utf8;

alter table csb_flash_remind add constraint fk_fm_aid foreign key (account_id) references csb_account(id) on delete cascade;
