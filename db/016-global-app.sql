-- add global app support

use okok;
alter table csb_app_info add server enum('chips','hawks') ;
update csb_app_info set server='chips';