use mok;

alter table mkComment modify bid bigint unsigned not null comment '广播id';

delete from mkComment where bid=0;

alter table mkComment add constraint fk_cmt_bid foreign key (bid) references mkBroadcast(id) on delete cascade;

alter table mkLikedArticle drop foreign key fk_likes_bid;
alter table mkLikedArticle add constraint fk_likes_bid foreign key (bid) references mkBroadcast(id) on delete cascade;
