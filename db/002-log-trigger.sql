-- a trigger for logging csb_role_data deletion and insertion

use okok;

DELIMITER $$

DROP TRIGGER IF EXISTS `insert_data_role_data` $$

CREATE TRIGGER `insert_data_role_data` AFTER INSERT on `csb_role_data`
FOR EACH ROW
BEGIN
    INSERT INTO csb_role_data_log
    (`role_id` , `opt` , `data_id` , `ts` ,`account_id`, weight_time)
    VALUES
    (NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.weight_time);
END;$$


DROP TRIGGER IF EXISTS `delete_data_role_data` $$

CREATE TRIGGER `delete_data_role_data` AFTER DELETE on `csb_role_data`
FOR EACH ROW
BEGIN
    INSERT INTO csb_role_data_log
    (`role_id` , `opt` , `data_id` , `ts`, account_id,weight_time )
    VALUES
    (OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.weight_time );
END;$$


DELIMITER ;
select trigger_schema, trigger_name, action_statement from information_schema.triggers;
