use okok;

DROP TRIGGER IF EXISTS `insert_food_data`;
DROP TRIGGER IF EXISTS `delete_food_data`;

DROP TRIGGER IF EXISTS `insert_exercise_data`;
DROP TRIGGER IF EXISTS `delete_exercise_data`;


DELIMITER $$
-- food
CREATE TRIGGER `insert_food_data` AFTER INSERT on `csb_food`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('food', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.`date`);
END;$$

CREATE TRIGGER `delete_food_data` AFTER DELETE on `csb_food`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('food', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.`date` );
END;$$

-- exercise
CREATE TRIGGER `insert_exercise_data` AFTER INSERT on `csb_exercise`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('exercise', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.`date`);
END;$$

CREATE TRIGGER `delete_exercise_data` AFTER DELETE on `csb_exercise`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('exercise', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.`date`);
END;$$

DELIMITER ;
