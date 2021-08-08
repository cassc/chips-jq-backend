use nh;

alter table s_seller modify `phone` varchar(26) unique NOT NULL;

use mok;
alter table mkAdmin add sid int null;
