use okok;

create unique index uk_csb_bp_rt on csb_bp(role_id,measure_time);
create unique index uk_csb_bsl_rt on csb_bsl(role_id,measure_time);
