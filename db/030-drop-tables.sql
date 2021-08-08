-- Clean tables in okok,
-- from
--  csb_account csb_admin_info csb_all_type_info csb_app_info csb_company csb_company_device csb_company_product csb_customer_feedback csb_del_role csb_del_role_data csb_del_role_day_data csb_del_role_month_data csb_del_role_week_data csb_role csb_role_data csb_role_data_log csb_role_day_data csb_role_log csb_role_month_data csb_role_week_data csb_sms csb_weigher csb_weight_remind csf_user wd_app_weixin wd_mp_weixin wd_mpx_device wd_wxdevice wd_wxproduct            
-- to
--  csb_account csb_app_info csb_company csb_company_device csb_company_product csb_customer_feedback csb_del_role csb_del_role_data csb_role csb_role_data csb_role_data_log csb_role_log csb_weight_remind     

-- removes these tables:
drop table csb_del_role_day_data;
drop table csb_role_week_data;
drop table wd_wxproduct;
drop table csb_role_month_data;
drop table csb_weigher;
drop table csb_role_day_data;
drop table wd_mp_weixin;
drop table wd_wxdevice;
drop table csb_del_role_month_data;
drop table csb_admin_info;
drop table csb_del_role_week_data;
drop table csb_sms;
drop table csb_all_type_info;
drop table wd_mpx_device;
drop table wd_app_weixin;
drop table csf_user;
