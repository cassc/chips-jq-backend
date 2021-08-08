-- merge food types

use fe;

create table popular_food(
id int unsigned primary key auto_increment,
food_id int unsigned not null,
tpe varchar(64) not null
) ENGINE=InnoDB ;


insert into popular_food (food_id, tpe) values ('63', '主食');
insert into popular_food (food_id, tpe) values ('92', '主食');
insert into popular_food (food_id, tpe) values ('27008', '主食');
insert into popular_food (food_id, tpe) values ('23', '主食');
insert into popular_food (food_id, tpe) values ('56', '主食');
insert into popular_food (food_id, tpe) values ('88', '主食');
insert into popular_food (food_id, tpe) values ('219', '蔬果');
insert into popular_food (food_id, tpe) values ('236', '蔬果');
insert into popular_food (food_id, tpe) values ('288', '蔬果');
insert into popular_food (food_id, tpe) values ('333', '蔬果');
insert into popular_food (food_id, tpe) values ('1575', '蔬果');
insert into popular_food (food_id, tpe) values ('294', '蔬果');
insert into popular_food (food_id, tpe) values ('307', '蔬果');
insert into popular_food (food_id, tpe) values ('241', '蔬果');
insert into popular_food (food_id, tpe) values ('285', '蔬果');
insert into popular_food (food_id, tpe) values ('355', '蔬果');
insert into popular_food (food_id, tpe) values ('231', '蔬果');
insert into popular_food (food_id, tpe) values ('469', '蔬果');
insert into popular_food (food_id, tpe) values ('626', '蔬果');
insert into popular_food (food_id, tpe) values ('559', '蔬果');
insert into popular_food (food_id, tpe) values ('608', '蔬果');
insert into popular_food (food_id, tpe) values ('488', '蔬果');
insert into popular_food (food_id, tpe) values ('525', '蔬果');
insert into popular_food (food_id, tpe) values ('583', '蔬果');
insert into popular_food (food_id, tpe) values ('1624', '蔬果');
insert into popular_food (food_id, tpe) values ('611', '蔬果');
insert into popular_food (food_id, tpe) values ('557', '蔬果');
insert into popular_food (food_id, tpe) values ('542', '蔬果');
insert into popular_food (food_id, tpe) values ('910', '肉蛋');
insert into popular_food (food_id, tpe) values ('820', '肉蛋');
insert into popular_food (food_id, tpe) values ('753', '肉蛋');
insert into popular_food (food_id, tpe) values ('684', '肉蛋');
insert into popular_food (food_id, tpe) values ('778', '肉蛋');
insert into popular_food (food_id, tpe) values ('932', '肉蛋');
insert into popular_food (food_id, tpe) values ('999', '肉蛋');
insert into popular_food (food_id, tpe) values ('983', '肉蛋');
insert into popular_food (food_id, tpe) values ('106', '乳豆制品');
insert into popular_food (food_id, tpe) values ('872', '乳豆制品');
insert into popular_food (food_id, tpe) values ('114', '乳豆制品');
insert into popular_food (food_id, tpe) values ('119', '乳豆制品');

