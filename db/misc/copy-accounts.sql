insert csb_account (password,phone,last_login,days,weight_unit,company_id,grade_id,register_time,length_unit)
select password,'11122223333',last_login,days,weight_unit,company_id,grade_id,register_time,length_unit
from   csb_account where phone='18129930760' and company_id=1;

SELECT @aaid := account_id from csb_account where  phone='18129930760' and company_id=1;
SELECT @baid := account_id from csb_account where  phone='11122223333' and company_id=1;

insert csb_role (current_state,sex,weight_goal,account_id,icon_image_create_time,nickname,birthday,icon_image_path,sync_time,create_time,modify_time,height,period_time)
select current_state,sex,weight_goal,@baid,icon_image_create_time,nickname,birthday,icon_image_path,sync_time,create_time,modify_time,height,period_time
from csb_role where account_id=@aaid;


insert csb_role_data (account_id,bone,muscle,sync_time,weight,bmi,body_age,role_id,weight_time,water,metabolism,viscera,axunge)
select @baid,bone,muscle,sync_time,weight,bmi,body_age,'',weight_time,water,metabolism,viscera,axunge
from csb_role_data where account_id=a
