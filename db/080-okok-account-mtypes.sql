use okok;

alter table csb_account modify `mtypes` varchar(255) DEFAULT 'weight';
update csb_account set mtypes='weight';
