use nh;

alter table s_order modify `status` enum('pending','paid','complete','cancel','hide', 'delivery') NOT NULL;
