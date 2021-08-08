drop database if exists fe;
create database fe default character set utf8mb4 COLLATE utf8mb4_unicode_ci;

use fe;

create table `food` (
id int unsigned primary key,
code varchar(254) not null,
`name` varchar(128) not null,
`thumb_image_url` varchar(512) null,
is_liquid boolean not null,
health_light int unsigned null,
weight varchar(10) null,
calory varchar(10) null comment 'kCal for every 100 ml or g',
fat varchar(10) null,
protein varchar(10) null comment 'mg',
fiber_dietary varchar(10) null,
carbohydrate varchar(10) null,
vitamin_a varchar(10) null,
thiamine varchar(10) null,
lactoflavin varchar(10) null,
vitamin_c varchar(10) null,
vitamin_e varchar(10) null,
niacin varchar(10) null,
natrium varchar(10) null,
calcium varchar(10) null,
iron varchar(10) null,
kalium varchar(10) null,
iodine varchar(10) null,
zinc varchar(10) null,
selenium varchar(10) null,
magnesium varchar(10) null,
copper varchar(10) null,
manganese varchar(10) null,
cholesterol varchar(10) null,
phosphor varchar(10) null,
carotene varchar(10) null,
`type` varchar(128) null,
status smallint null,
usedegree smallint null,
-- `units` varchar(1024) DEFAULT NULL,
brand varchar(48) null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

create index food_name on food(`name`);
create index food_code on food(`code`);
create index food_usedegree on food(`usedegree`);

CREATE TABLE exercise (
id int unsigned primary key,
`name` varchar(256) not null,
met decimal(5,2) null comment 'kCal for every minute',
`type` varchar(128) null,
`status` smallint null,
usedegree smallint null
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

create index exercise_name on exercise(`name`);
create index exercise_usedegree on exercise(`usedegree`);

-- CREATE TABLE food_bak LIKE food; 
-- INSERT food_bak SELECT * FROM food;
drop table if exists food_units;
drop table if exists units;

create table units (
unit_id int unsigned primary key,
amount varchar(10) not null,
unit varchar(20) not null,
weight varchar(10) not null,
eat_weight varchar(10) null,
calory varchar(10) not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


create table food_units (
id int unsigned primary key auto_increment ,
food_id int unsigned not null,
unit_id int unsigned not null
);

alter table food_units add constraint `fk_food_units_fid` foreign key (food_id) references food(`id`) on delete cascade;
alter table food_units add constraint `fk_food_units_uid` foreign key (unit_id) references units(`unit_id`) on delete cascade;

