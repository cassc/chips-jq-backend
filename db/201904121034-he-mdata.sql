use nh;

CREATE TABLE `h_weight` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `aid` int(11) NOT NULL,
  `rid` varchar(24) NOT NULL,
  `weight` double DEFAULT NULL,
  `r1` double DEFAULT NULL,
  `age` tinyint(3) unsigned DEFAULT '0',
  `sex` tinyint(3) unsigned DEFAULT '0',
  `height` tinyint(3) unsigned DEFAULT '0',
  `rn8` varchar(200) DEFAULT NULL,
  `readable` varchar(1024) default null,
  ts bigint unsigned not null ,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hw_a_r_t` (`aid`,`rid`,`ts`),
  CONSTRAINT `fk_hw_aid` FOREIGN KEY (`aid`) REFERENCES `csb_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

alter table h_weight add index(ts);
