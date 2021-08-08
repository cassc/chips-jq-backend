use okok;

alter table csb_mdata add constraint fk_mdata_a_id foreign key (account_id) references csb_account(id);
alter table csb_mdata add constraint fk_mdata_r_id foreign key (role_id) references csb_role(id);
