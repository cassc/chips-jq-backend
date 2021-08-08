use nh;

alter table csb_food modify `ftype` enum('breakfast','lunch','dinner','snacks', 'unknown') NOT NULL default 'unknown';
