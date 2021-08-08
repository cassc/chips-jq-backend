USE `okok`

ALTER TABLE `okok`.`csb_role_data`   
  ADD COLUMN `scaleweight` VARCHAR(20) NULL AFTER `sync_time`,
  ADD COLUMN `scaleproperty` INT DEFAULT 1  NULL AFTER `scaleweight`,
  ADD COLUMN `productid` INT DEFAULT 0  NULL AFTER `scaleproperty`;

ALTER TABLE `okok`.`csb_del_role_data`   
  ADD COLUMN `scaleweight` VARCHAR(20) NULL AFTER `sync_time`,
  ADD COLUMN `scaleproperty` INT DEFAULT 1  NULL AFTER `scaleweight`,
  ADD COLUMN `productid` INT DEFAULT 0  NULL AFTER `scaleproperty`;