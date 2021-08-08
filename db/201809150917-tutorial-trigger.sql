use nh;

DROP TRIGGER IF EXISTS `insert_training_data`;
DROP TRIGGER IF EXISTS `delete_training_data`;

DELIMITER $$
-- training
CREATE TRIGGER `insert_training_data` AFTER INSERT on `csb_training`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('training', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.`measure_time`);
END;$$

CREATE TRIGGER `delete_training_data` AFTER DELETE on `csb_training`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('training', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.`measure_time` );
END;$$

DELIMITER ;
