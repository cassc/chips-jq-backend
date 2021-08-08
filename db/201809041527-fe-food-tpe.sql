-- merge food types

use fe;
alter table food add column tpe varchar(64) default null ;
update food set tpe='主食' where type in ('其它谷类', '谷薯芋、杂豆、主食', '主食');
update food set tpe='蔬果' where type in ('蔬菜菌藻类', '蔬菜', '水果类', '水果');
update food set tpe='肉蛋' where type in ('蛋类、肉类及制品', '鹌鹑蛋类', '鸡蛋类', '肉蛋类');
update food set tpe='乳豆制品' where type in ('液态乳类', '含乳饮料类', '坚果、大豆及制品', '大豆及制品');
update food set tpe='佐料' where type in ('食用油、油脂及制品', '油脂类', '盐、味精及其它类', '醋类');
update food set tpe='零食' where type in ('零食', '零食、点心、冷饮', '零食，点心及冷饮');
update food set tpe='成品菜' where type in ('家常菜');

-- create table popular_food(
-- id int unsigned primary key,
-- food_id int unsigned not null
-- ) ENGINE=InnoDB ;

-- select * from food where tpe is not null order by tpe;

