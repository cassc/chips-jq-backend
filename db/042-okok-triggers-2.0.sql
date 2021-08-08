use okok;

---- Reinstall triggers for weight, bp, bsl
DROP TRIGGER IF EXISTS `insert_data_role_data`;
DROP TRIGGER IF EXISTS `delete_data_role_data`;
DROP TRIGGER IF EXISTS `insert_weight_data`;
DROP TRIGGER IF EXISTS `delete_weight_data`;
DROP TRIGGER IF EXISTS `insert_bp_data`;
DROP TRIGGER IF EXISTS `delete_bp_data`;
DROP TRIGGER IF EXISTS `insert_bsl_data`;
DROP TRIGGER IF EXISTS `delete_bsl_data`;

DELIMITER $$
-- weight
CREATE TRIGGER `insert_weight_data` AFTER INSERT on `csb_weight`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('weight', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.weight_time);
END;$$

CREATE TRIGGER `delete_weight_data` AFTER DELETE on `csb_weight`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('weight', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.weight_time );
END;$$

-- bp
CREATE TRIGGER `insert_bp_data` AFTER INSERT on `csb_bp`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('bp', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.measure_time);
END;$$

CREATE TRIGGER `delete_bp_data` AFTER DELETE on `csb_bp`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('bp', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.measure_time );
END;$$

-- bsl
CREATE TRIGGER `insert_bsl_data` AFTER INSERT on `csb_bsl`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts` ,`account_id`, measure_time)
VALUES
('bsl', NEW.role_id, "i", NEW.id, unix_timestamp(), NEW.account_id, NEW.measure_time);
END;$$

CREATE TRIGGER `delete_bsl_data` AFTER DELETE on `csb_bsl`
FOR EACH ROW
BEGIN
INSERT INTO csb_role_data_log
(`mtype`, `role_id` , `opt` , `data_id` , `ts`, account_id,measure_time )
VALUES
('bsl', OLD.role_id, "d", OLD.id, unix_timestamp(), OLD.account_id, OLD.measure_time );
END;$$

DELIMITER ;

select trigger_schema, trigger_name, action_statement from information_schema.triggers;

