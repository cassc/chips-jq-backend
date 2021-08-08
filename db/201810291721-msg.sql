use nh;


create table m_msg (
id int unsigned primary key auto_increment,
aid int(11) not null,
title varchar(384) null,
msg varchar(1024) not null,
img varchar(1024) null,
ts bigint not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


create table m_status(
id int unsigned primary key auto_increment,
mid int unsigned not null,
aid int(11) not null,
status enum('read','unread', 'delete') not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
