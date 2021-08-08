use nh;

alter table csb_tutorial drop key uk_tt_dir;

insert into csb_tutorial (id, title, dir, tag, cover, rqdev, duration, advice, audience, warning, calory, ts )
       values
       (10000, '常见问题', '', '运动', 'faq.png', 'n', '0', '', '所有', '', 0, 0),
       (10001, '常见问题', '', '使用指南', 'faq.png', 'n', '0', '', '所有', '', 0, 0)
       ;

insert into csb_tutorial_video (tid, title, seq) values
       (10000, 'http://47.93.10.106:8080/faq/question.html', 1),
       (10001, 'http://47.93.10.106:8080/faq/question.html', 1);
