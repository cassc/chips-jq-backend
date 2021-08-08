-- WARN NOT APPLIED
-- Fri 13 Nov 2015 04:18:30 PM HKT

-- Step 1:
-- Edit my.cnf, add the following
-- [mysqld]
-- character_set_client_handshake = FALSE
-- character_set_server = utf8mb4
-- collation_server = utf8mb4_unicode_ci

-- [client]
-- default_character_set = utf8mb4

-- [mysql]
-- default_character_set = utf8mb4

-- Step 1.5: restart mysql server and check all above variables are set.
-- SHOW VARIABLES WHERE Variable_name LIKE 'character\_set\_%' OR Variable_name LIKE 'collation%';

-- Step 2: import the following sql
use okok;
-- role nickname
alter database okok character set = utf8mb4 collate = utf8mb4_unicode_ci;
alter table csb_role convert to character set utf8mb4 collate utf8mb4_unicode_ci;
alter table csb_role change nickname nickname varchar(30)  character set utf8mb4 collate utf8mb4_unicode_ci;

-- feedback
alter table csb_customer_feedback convert to character set utf8mb4 collate utf8mb4_unicode_ci;
alter table csb_customer_feedback change feedback_content feedback_content text character set utf8mb4 collate utf8mb4_unicode_ci;


-- Step 3: repair tables
-- mysqlcheck -u root -p --auto-repair --optimize --all-databases
