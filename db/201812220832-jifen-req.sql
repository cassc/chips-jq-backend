use nh;


create table csb_jifen_coupon (
`id` int(10) unsigned NOT NULL AUTO_INCREMENT,
`coupon` varchar(24) unique NOT NULL,
`score` int unsigned NULL,
`ts` bigint(20) unsigned NOT NULL,
`aid` int(11) DEFAULT NULL,
status enum('accept', 'pending', 'reject') null,
reason text null,
PRIMARY KEY (`id`),
KEY `fk_jfc_aid` (`aid`),
CONSTRAINT `fk_jfc_aid` FOREIGN KEY (`aid`) REFERENCES `csb_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1948 DEFAULT CHARSET=utf8mb4;

ALTER TABLE j_jifen_coupon MODIFY coupon varchar(24)  NOT NULL COLLATE utf8_bin;
ALTER TABLE csb_jifen_coupon MODIFY coupon varchar(24)  NOT NULL COLLATE utf8_bin;
