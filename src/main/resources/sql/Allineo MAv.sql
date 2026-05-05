--conn giesco/giesco@prodalias
declare

begin
giesco.PKG_INS_FATT.g_forzat := true;

    for cr in (
                select a.REP_AT_SOCIETA, a.REP_AT_CONTRATTO_UL, to_number(f.NUM_FATTURA) NUM_FATTURA, f.REGISTRO, d.NUM_DOC, d.DATASCADENZA  
				from 	 fatcli.repository_blocchiattr ba
					join fatcli.repository_attributi a on a.ID_ATTRIBUTI = ba.ID_ATTRIBUTI
					join FATCLI.FATTURE_CARICATE f on f.ID_ATTRIBUTI = a.ID_ATTRIBUTI
					join FATCLI.DOCUMENTI_CARICATI d on d.ID_ATTRIBUTI = a.ID_ATTRIBUTI and d.NUM_DOC is not null
                where ba.ID_ATTRIBUTI in ( select id_attributi from fatcli.repository_blocchiattr where id_blocco=:idBlocco)
              )
    loop
    update fat_d_mav fd
    set mav = cr.NUM_DOC, BLL_D_SCADENZA = cr.DATASCADENZA
    where fd.ID_FAT_TEST = (
    select ID_FAT_TEST from giesco.fat_test f 
      where f.LSC_C_SOCIETA = cr.REP_AT_SOCIETA
        and f.CNT_C_CONTRATTO = cr.REP_AT_CONTRATTO_UL
        and f.BLL_N_FATTU = cr.NUM_FATTURA
        and f.BLL_T_REGISTRO = cr.REGISTRO
    )    
    ;
    
    DBMS_OUTPUT.PUT_LINE(' soc=' || cr.REP_AT_SOCIETA || 
                         ' contratto=' || cr.REP_AT_CONTRATTO_UL ||
                         ' numFatt=' || cr.NUM_FATTURA ||
                         ' registro=' || cr.REGISTRO ||
                         ' mav=' || cr.NUM_DOC ||
                         ' DATA_SCADENZA=' || cr.DATASCADENZA 
         );  

    end loop;
    commit;
end;
