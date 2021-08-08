use nh;


create table j_jifen_coupon (
`id` int(10) unsigned NOT NULL AUTO_INCREMENT,
`coupon` varchar(24) unique NOT NULL,
`score` int unsigned NULL,
`ts` bigint(20) unsigned NOT NULL,
status enum('valid', 'expired', 'taken') null,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1948 DEFAULT CHARSET=utf8mb4;
