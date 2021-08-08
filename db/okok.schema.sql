-- MySQL dump 10.14  Distrib 5.5.44-MariaDB, for Linux (x86_64)
--
-- Host: 10.10.80.228    Database: okok
-- ------------------------------------------------------
-- Server version	5.6.20-ucloudrel1-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `csb_account`
--

DROP TABLE IF EXISTS `csb_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_account` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `email` varchar(50) DEFAULT NULL,
  `password` char(32) NOT NULL,
  `grade_id` int(11) NOT NULL DEFAULT '0',
  `last_login` date NOT NULL DEFAULT '2015-01-01',
  `days` int(11) NOT NULL DEFAULT '0',
  `register_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `qq` varchar(32) DEFAULT NULL,
  `weixin` varchar(32) DEFAULT NULL,
  `phone` char(11) DEFAULT NULL,
  `sina_blog` varchar(32) DEFAULT NULL,
  `Length_unit` int(11) NOT NULL DEFAULT '1400',
  `weight_unit` int(11) NOT NULL DEFAULT '1400',
  `company_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3384 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_app_info`
--

DROP TABLE IF EXISTS `csb_app_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_app_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `system_version` varchar(10) NOT NULL,
  `content` varchar(255) NOT NULL DEFAULT '',
  `version` varchar(10) NOT NULL DEFAULT '',
  `url` varchar(100) NOT NULL DEFAULT '',
  `upgrade_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `company_id` int(11) DEFAULT NULL,
  `region` enum('china','global') NOT NULL DEFAULT 'china',
  `mu_version` varchar(10) DEFAULT NULL COMMENT '最低可用版本号',
  `server` enum('chips','hawks') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_company`
--

DROP TABLE IF EXISTS `csb_company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_company` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `address` varchar(180) DEFAULT NULL,
  `phone` varchar(30) NOT NULL,
  `logo_path` varchar(50) NOT NULL DEFAULT ' ' COMMENT '厂商logo地址',
  `slogan` varchar(120) NOT NULL DEFAULT ' ' COMMENT '厂商口号',
  `short_intr` varchar(255) NOT NULL DEFAULT ' ' COMMENT '厂商简介',
  `current_state` tinyint(4) NOT NULL COMMENT '状态',
  `create_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `modify_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00' COMMENT '同步时间',
  `owner_name` varchar(20) NOT NULL COMMENT '法人名字',
  `owner_id_nmuber` char(18) DEFAULT NULL,
  `owner_birthday` date DEFAULT NULL,
  `owner_sex` enum('男','女') NOT NULL,
  `owner_origin_place` varchar(10) DEFAULT NULL,
  `owner_address` varchar(120) DEFAULT NULL,
  `business_license_number` varchar(20) DEFAULT NULL COMMENT '公司注册号',
  `organization_number` varchar(20) DEFAULT NULL COMMENT '组织架构编码',
  `name` varchar(50) NOT NULL,
  `multilang_desc` varchar(1000) DEFAULT NULL COMMENT '多语言公司描述',
  `url` varchar(100) DEFAULT NULL COMMENT '公司官网',
  `boot_img_path` varchar(50) DEFAULT NULL COMMENT 'APP启动图片',
  `wxappid` varchar(32) DEFAULT NULL COMMENT '微信公众号appID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_company_device`
--

DROP TABLE IF EXISTS `csb_company_device`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_company_device` (
  `id` int(11) NOT NULL,
  `company_id` int(11) NOT NULL,
  `device_intr` varchar(255) NOT NULL DEFAULT '' COMMENT '设备简介',
  `device_name` varchar(10) NOT NULL COMMENT '设备名称',
  `device_type` int(11) NOT NULL COMMENT '设备类型',
  `mac` char(17) DEFAULT NULL COMMENT 'mac地址',
  `device_doc_path` varchar(50) NOT NULL DEFAULT '' COMMENT '文档路径',
  KEY `csb_role_company_1` (`company_id`),
  CONSTRAINT `csb_role_company_1` FOREIGN KEY (`company_id`) REFERENCES `csb_company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_company_product`
--

DROP TABLE IF EXISTS `csb_company_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_company_product` (
  `product_id` int(11) unsigned NOT NULL COMMENT '产品编码',
  `company_id` int(11) NOT NULL COMMENT '产品所属公司编码',
  `product_model` varchar(200) DEFAULT NULL COMMENT '产品型号(厂家型号)',
  `product_desc` varchar(200) NOT NULL COMMENT '产品描述',
  `logo_path` varchar(50) DEFAULT NULL COMMENT 'product logo',
  PRIMARY KEY (`product_id`),
  KEY `csb_role_product_ibfk_1` (`company_id`),
  CONSTRAINT `csb_role_product_ibfk_1` FOREIGN KEY (`company_id`) REFERENCES `csb_company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_customer_feedback`
--

DROP TABLE IF EXISTS `csb_customer_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_customer_feedback` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `feedback_content` text NOT NULL,
  `feedback_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `content_state` smallint(6) NOT NULL DEFAULT '0',
  `return_content` text,
  `account_id` int(11) NOT NULL,
  `platform` varchar(8) NOT NULL DEFAULT '',
  `version` varchar(10) NOT NULL DEFAULT '',
  `sdk` varchar(10) NOT NULL DEFAULT '',
  `notifier` varchar(64) DEFAULT NULL COMMENT '处理人邮件或手机号，为空时表示未发送给此人',
  `notify_ts` bigint(20) NOT NULL DEFAULT '0' COMMENT '通知时间',
  `ua` varchar(256) DEFAULT NULL COMMENT '客户端类型',
  PRIMARY KEY (`id`),
  KEY `R_13` (`account_id`)
) ENGINE=InnoDB AUTO_INCREMENT=265 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_db_op`
--

DROP TABLE IF EXISTS `csb_db_op`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_db_op` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `company_id` int(11) DEFAULT NULL,
  `ops` blob NOT NULL,
  `ts` bigint(20) unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_del_role`
--

DROP TABLE IF EXISTS `csb_del_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_del_role` (
  `id` int(11) NOT NULL COMMENT 'ä¸ŽåŽŸrole_idç›¸åŒ',
  `nickname` varchar(30) NOT NULL,
  `height` int(11) NOT NULL,
  `birthday` date NOT NULL,
  `account_id` int(11) NOT NULL,
  `sex` enum('男','女') DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `modify_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `icon_image_path` varchar(54) DEFAULT NULL,
  `icon_image_create_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `current_state` tinyint(4) NOT NULL,
  `period_time` date NOT NULL,
  `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `weight_goal` float(5,2) DEFAULT '0.00',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_del_role_data`
--

DROP TABLE IF EXISTS `csb_del_role_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_del_role_data` (
  `id` int(11) NOT NULL COMMENT 'ä¸ŽåŽŸrole_data.idç›¸åŒ',
  `weight` float(5,2) NOT NULL,
  `bmi` float(9,2) NOT NULL DEFAULT '0.00',
  `axunge` float(9,2) NOT NULL DEFAULT '0.00',
  `bone` float(9,2) NOT NULL DEFAULT '0.00',
  `muscle` float(9,2) NOT NULL DEFAULT '0.00',
  `water` float(9,2) NOT NULL DEFAULT '0.00',
  `metabolism` float(9,2) DEFAULT NULL,
  `body_age` tinyint(4) NOT NULL,
  `viscera` float(9,2) NOT NULL DEFAULT '0.00',
  `weight_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `account_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `scaleweight` varchar(20) DEFAULT NULL,
  `scaleproperty` int(11) DEFAULT '1',
  `productid` int(11) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_role`
--

DROP TABLE IF EXISTS `csb_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_role` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nickname` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `height` int(11) NOT NULL DEFAULT '0',
  `birthday` date NOT NULL DEFAULT '2015-01-01',
  `account_id` int(11) NOT NULL,
  `sex` enum('男','女') NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modify_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `icon_image_path` varchar(54) DEFAULT NULL,
  `icon_image_create_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `current_state` tinyint(4) NOT NULL DEFAULT '1',
  `period_time` date NOT NULL DEFAULT '0000-00-00',
  `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `weight_goal` float(5,2) DEFAULT '0.00',
  PRIMARY KEY (`id`),
  KEY `R_9` (`account_id`),
  CONSTRAINT `csb_role_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `csb_account` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5987 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_role_data`
--

DROP TABLE IF EXISTS `csb_role_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_role_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `weight` float(5,2) NOT NULL DEFAULT '0.00',
  `bmi` float(9,2) NOT NULL DEFAULT '0.00',
  `axunge` float(9,2) NOT NULL DEFAULT '0.00',
  `bone` float(9,2) NOT NULL DEFAULT '0.00',
  `muscle` float(9,2) NOT NULL DEFAULT '0.00',
  `water` float(9,2) NOT NULL DEFAULT '0.00',
  `metabolism` float(9,2) DEFAULT NULL,
  `body_age` tinyint(4) NOT NULL DEFAULT '0',
  `viscera` float(9,2) NOT NULL DEFAULT '0.00',
  `weight_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `account_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  `sync_time` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `scaleweight` varchar(20) DEFAULT NULL,
  `scaleproperty` int(11) DEFAULT '1',
  `productid` int(11) DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_csb_role_weight_time` (`role_id`,`weight_time`),
  KEY `R_11` (`account_id`),
  KEY `R_12` (`role_id`),
  KEY `R_14` (`weight_time`),
  CONSTRAINT `csb_role_data_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `csb_account` (`id`),
  CONSTRAINT `csb_role_data_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `csb_role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=524810 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_role_data_log`
--

DROP TABLE IF EXISTS `csb_role_data_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_role_data_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  `opt` enum('d','i') DEFAULT NULL COMMENT 'æ“ä½œç±»åž‹ï¼Œd:delete, i:insert',
  `data_id` bigint(20) NOT NULL COMMENT 'csb_role_dataçš„id',
  `weight_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'ç§°é‡æ—¶é—´',
  `ts` int(10) unsigned NOT NULL COMMENT 'æ“ä½œtimestamp(ç§’)',
  PRIMARY KEY (`id`),
  KEY `account_id` (`account_id`,`role_id`,`ts`)
) ENGINE=InnoDB AUTO_INCREMENT=44608 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_role_log`
--

DROP TABLE IF EXISTS `csb_role_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_role_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  `opt` enum('d','i') DEFAULT NULL COMMENT 'æ“ä½œç±»åž‹ï¼Œd:delete, i:insert',
  `ts` int(10) unsigned NOT NULL COMMENT 'æ“ä½œtimestamp(ç§’)',
  PRIMARY KEY (`id`),
  KEY `account_id` (`account_id`,`role_id`,`ts`)
) ENGINE=InnoDB AUTO_INCREMENT=3925 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `csb_weight_remind`
--

DROP TABLE IF EXISTS `csb_weight_remind`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `csb_weight_remind` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `account_id` int(11) DEFAULT NULL,
  `remind_time` varchar(5) DEFAULT NULL,
  `is_open` tinyint(4) NOT NULL DEFAULT '0',
  `mon_open` tinyint(4) NOT NULL DEFAULT '0',
  `tue_open` tinyint(4) NOT NULL DEFAULT '0',
  `wed_open` tinyint(4) NOT NULL DEFAULT '0',
  `thu_open` tinyint(4) NOT NULL DEFAULT '0',
  `fri_open` tinyint(4) NOT NULL DEFAULT '0',
  `sat_open` tinyint(4) NOT NULL DEFAULT '0',
  `sun_open` tinyint(4) NOT NULL DEFAULT '0',
  `once_open` tinyint(4) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `csb_role_data_ibfk_remind` (`account_id`),
  CONSTRAINT `csb_role_data_ibfk_remind` FOREIGN KEY (`account_id`) REFERENCES `csb_account` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10117 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2016-05-17  9:39:33
