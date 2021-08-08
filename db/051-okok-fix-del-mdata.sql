use okok;

delete from csb_mdata where mtype='weight' and mid not in (select id from csb_weight);
delete from csb_mdata where mtype='bsl' and mid not in (select id from csb_bsl);
delete from csb_mdata where mtype='bp' and mid not in (select id from csb_bp);
