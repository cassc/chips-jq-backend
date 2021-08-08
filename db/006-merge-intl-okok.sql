-- Tue 10 Nov 2015 04:10:51 PM HKT
-- 只能执行一次
-- 合并国际版数据库

use okok;

DELIMITER $$

DROP PROCEDURE IF EXISTS import_intl_data;

CREATE PROCEDURE import_intl_data ()

    BEGIN
      DECLARE MIDOFFSET INT DEFAULT 10000;
      DECLARE BIGOFFSET BIGINT DEFAULT 30000;

      DECLARE exit handler for sqlexception
      BEGIN
        SELECT 'Error, rolling back.';
        ROLLBACK;
      END;

   START TRANSACTION;

    -- import users
    insert ignore  csb_account (id,email,password,phone,last_login,days,weight_unit,company_id,qq,sina_blog,grade_id,register_time,length_unit)
    -- on duplicate key ignore
    select  id+MIDOFFSET,email,password,phone,last_login,days,weight_unit,company_id,qq,sina_blog,grade_id,register_time,length_unit
    from    intl_okok.csb_account;

    -- import feedbacks
    insert ignore csb_customer_feedback (id,sdk,version,platform,account_id,return_content,content_state,feedback_time,feedback_content)
    -- on duplicate key ignore
    select id+MIDOFFSET,sdk,version,platform,account_id+MIDOFFSET,return_content,content_state,feedback_time,feedback_content
    from intl_okok.csb_customer_feedback;


    -- roles
    insert ignore csb_role (current_state,sex,weight_goal,account_id,icon_image_create_time,nickname,birthday,icon_image_path,sync_time,id,create_time,modify_time,height,period_time)
    -- on duplicate key ignore
    select current_state,sex,weight_goal,account_id+MIDOFFSET,icon_image_create_time,nickname,birthday,icon_image_path,sync_time,id+MIDOFFSET,create_time,modify_time,height,period_time
    from intl_okok.csb_role;


    -- role data
    insert ignore csb_role_data (account_id,bone,muscle,sync_time,weight,id,bmi,body_age,role_id,weight_time,water,metabolism,viscera,axunge)
    -- on duplicate key ignore
    select account_id+MIDOFFSET,bone,muscle,sync_time,weight,id+BIGOFFSET,bmi,body_age,role_id+MIDOFFSET,weight_time,water,metabolism,viscera,axunge
    from intl_okok.csb_role_data;


    -- day data
    insert ignore csb_role_day_data (account_id,bone,muscle,weight,id,bmi,body_age,role_id,weight_time,water,metabolism,viscera,axunge)
    -- on duplicate key ignore
    select account_id+MIDOFFSET,bone,muscle,weight,id+BIGOFFSET,bmi,body_age,role_id+MIDOFFSET,weight_time,water,metabolism,viscera,axunge
    from intl_okok.csb_role_day_data;


    -- month data
    insert ignore csb_role_month_data (account_id,bone,muscle,weight,id,bmi,body_age,role_id,weight_time,water,metabolism,viscera,axunge)
    -- on duplicate key ignore
    select account_id+MIDOFFSET,bone,muscle,weight,id+BIGOFFSET,bmi,body_age,role_id+MIDOFFSET,weight_time,water,metabolism,viscera,axunge
    from intl_okok.csb_role_month_data;


    -- week data
    insert ignore csb_role_week_data (account_id,bone,muscle,weight,id,bmi,body_age,role_id,weight_time,water,metabolism,viscera,axunge)
    -- on duplicate key ignore
    select account_id+MIDOFFSET,bone,muscle,weight,id+BIGOFFSET,bmi,body_age,role_id+MIDOFFSET,weight_time,water,metabolism,viscera,axunge
    from intl_okok.csb_role_week_data;


    -- csb_weight_remind
    insert ignore csb_weight_remind (mon_open,tue_open,account_id,wed_open,thu_open,sun_open,id,once_open,is_open,fri_open,remind_time,sat_open)
    -- on duplicate key ignore
    select mon_open,tue_open,account_id+MIDOFFSET,wed_open,thu_open,sun_open,id+MIDOFFSET,once_open,is_open,fri_open,remind_time,sat_open
    from intl_okok.csb_weight_remind;

    COMMIT;
END$$
DELIMITER ;

use okok;
call import_intl_data ();

-- app info table
-- ./btWeigh/Data/Setting/App/btWeigh.apk
-- ./btWeigh/Data/Setting/Intl_App/intlbtWeigh.apk
insert ignore csb_app_info (company_id,upgrade_time,url,version,content,system_version,id, mu_version, region)
select company_id,upgrade_time,url,version,content,system_version,id+100,null,'global'
from intl_okok.csb_app_info;
