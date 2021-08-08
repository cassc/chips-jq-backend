-- 增加测量数据位数


-- csb_role_data 修改前结构
-- +---------------+-------------+------+-----+---------------------+----------------+
-- | id            | bigint(20)  | NO   | PRI | NULL                | auto_increment |
-- | weight        | float(5,2)  | NO   |     | 0.00                |                |
-- | bmi           | float(5,2)  | NO   |     | 0.00                |                |
-- | axunge        | float(5,2)  | NO   |     | 0.00                |                |
-- | bone          | float(4,2)  | NO   |     | 0.00                |                |
-- | muscle        | float(4,2)  | NO   |     | 0.00                |                |
-- | water         | float(4,2)  | NO   |     | 0.00                |                |
-- | metabolism    | float(9,2)  | YES  |     | NULL                |                |
-- | body_age      | tinyint(4)  | NO   |     | 0                   |                |
-- | viscera       | float(4,2)  | NO   |     | 0.00                |                |
-- | weight_time   | timestamp   | NO   | MUL | CURRENT_TIMESTAMP   |                |
-- | account_id    | int(11)     | NO   | MUL | NULL                |                |
-- | role_id       | int(11)     | NO   | MUL | NULL                |                |
-- | sync_time     | timestamp   | NO   |     | 0000-00-00 00:00:00 |                |
-- | scaleweight   | varchar(20) | YES  |     | NULL                |                |
-- | scaleproperty | int(11)     | YES  |     | 1                   |                |
-- | productid     | int(11)     | YES  |     | 0                   |                |
-- +---------------+-------------+------+-----+---------------------+----------------+

use okok;

alter table csb_role_data modify bmi float(9,2) not null default 0;
alter table csb_role_data modify axunge float(9,2) not null default 0;
alter table csb_role_data modify bone float(9,2) not null default 0;
alter table csb_role_data modify muscle float(9,2) not null default 0;
alter table csb_role_data modify water float(9,2) not null default 0;
alter table csb_role_data modify viscera float(9,2) not null default 0;


alter table csb_del_role_data modify bmi float(9,2) not null default 0;
alter table csb_del_role_data modify axunge float(9,2) not null default 0;
alter table csb_del_role_data modify bone float(9,2) not null default 0;
alter table csb_del_role_data modify muscle float(9,2) not null default 0;
alter table csb_del_role_data modify water float(9,2) not null default 0;
alter table csb_del_role_data modify viscera float(9,2) not null default 0;

alter table csb_role_day_data modify bmi float(9,2) not null default 0;
alter table csb_role_day_data modify axunge float(9,2) not null default 0;
alter table csb_role_day_data modify bone float(9,2) not null default 0;
alter table csb_role_day_data modify muscle float(9,2) not null default 0;
alter table csb_role_day_data modify water float(9,2) not null default 0;
alter table csb_role_day_data modify viscera float(9,2) not null default 0;

alter table csb_role_week_data modify bmi float(9,2) not null default 0;
alter table csb_role_week_data modify axunge float(9,2) not null default 0;
alter table csb_role_week_data modify bone float(9,2) not null default 0;
alter table csb_role_week_data modify muscle float(9,2) not null default 0;
alter table csb_role_week_data modify water float(9,2) not null default 0;
alter table csb_role_week_data modify viscera float(9,2) not null default 0;


alter table csb_role_month_data modify bmi float(9,2) not null default 0;
alter table csb_role_month_data modify axunge float(9,2) not null default 0;
alter table csb_role_month_data modify bone float(9,2) not null default 0;
alter table csb_role_month_data modify muscle float(9,2) not null default 0;
alter table csb_role_month_data modify water float(9,2) not null default 0;
alter table csb_role_month_data modify viscera float(9,2) not null default 0;



