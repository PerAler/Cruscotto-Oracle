with blocchi as(
    select * from fatcli.repository_blocchi
     where id_blocco=:idBlocco
     ),
     processi as (
    select * from fatcli.proc_attivita 
     where id_attivita=1
     and id_processo = (select id_processo from fatcli.blocchi)
     ),
     blocchiattr as (
    select * from repository_blocchiattr
     where id_blocco = (select id_blocco from fatcli.blocchi) 
     )
select ID_ATTRIBUTI, (select id_procatt from fatcli.processi) idProcAtt_goBack,  
substr(NUM_FATTURA, 3) NUMERO_FATTURA, 'true' bCreaMavRid, 'false' bCreaLettera from(
select ba.ID_BLOCCO,  a.ID_ATTRIBUTI, a.REP_AT_SOCIETA, a.REP_AT_CONTRATTO_UL, f.NUM_FATTURA, f.REGISTRO 
from     fatcli.blocchiattr ba
    join fatcli.repository_attributi a on a.ID_ATTRIBUTI = ba.ID_ATTRIBUTI
    join FATCLI.FATTURE_CARICATE f on f.ID_ATTRIBUTI = a.ID_ATTRIBUTI
where f.TOTDAPAGARE > 0 
and not exists (select 1 from FATCLI.DOCUMENTI_CARICATI d 
                join FATCLI.REGULARIO_TIPODOCUMENTO td on td.ID_TIPODOCUMENTO = d.ID_TIPODOCUMENTO and td.GRP_TIPODOCUMENTO = 'RID'
                where d.NUM_DOC is not null and d.ID_ATTRIBUTI = a.ID_ATTRIBUTI  )   
minus
select ba.ID_BLOCCO, a.ID_ATTRIBUTI, a.REP_AT_SOCIETA, a.REP_AT_CONTRATTO_UL, f.NUM_FATTURA, f.REGISTRO 
from     fatcli.blocchiattr ba
    join fatcli.repository_attributi a on a.ID_ATTRIBUTI = ba.ID_ATTRIBUTI
    join FATCLI.FATTURE_CARICATE f on f.ID_ATTRIBUTI = a.ID_ATTRIBUTI
    join FATCLI.REGULARIO_TIPODOCUMENTO td on td.ID_TIPOFATTURA = a.ID_TIPOFATTURA and td.GRP_TIPODOCUMENTO = 'MAV'
    join FATCLI.DOCUMENTI_CARICATI d on d.ID_ATTRIBUTI = a.ID_ATTRIBUTI and d.NUM_DOC is not null and d.ID_TIPODOCUMENTO = td.ID_TIPODOCUMENTO 
) x
/