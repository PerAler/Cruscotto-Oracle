--@param vdata: inserire la data nel formato yyymmdd
--@param vanno_red :inserire l'anno redditi
begin
    ANAGRAFE_TERZI.comune:=:vcsoc;
    ANAGRAFE_TERZI.p_data:=to_date (:vdata,'yyyymmdd');
    ANAGRAFE_TERZI.anno_redditi :=:vanno_red;
     
    ANAGRAFE_TERZI.INIZIO;
    ANAGRAFE_TERZI.appoggio;
    for i in (select * from valori_locativi)
    loop
        update valori_locativi 
            set contratto=matter.ULTIMO_CONTRATTO(ANAGRAFE_TERZI.comune, i.uni_c_unita, null)
        where uni_c_unita=i.uni_c_unita;
    end loop;
    
    update valori_locativi
    set contratto=null 
    where exists (select 1 from matter.contratto_soggetto where cnt_c_Contratto=contratto and lsc_c_societa=ANAGRAFE_TERZI.comune and (cnt_d_finloc is not null or CNT_C_REGIME_NORMATIVO!=4));
    
--    correggo le ui 0 (zero) con O (lettera o)
    if ANAGRAFE_TERZI.comune='915' then
      update valori_locativi_appoggio
      set uni_c_unita='H264ARO004'
      where uni_c_unita='H264AR0004';
      update valori_locativi_appoggio
      set uni_c_unita='H264ARO003'
      where uni_c_unita='H264AR0003';
      update valori_locativi_appoggio
      set uni_c_unita='H264ARO002'
      where uni_c_unita='H264AR0002';
      update valori_locativi_appoggio
      set uni_c_unita='H264ARO001'
      where uni_c_unita='H264AR0001';
    end if;
--------------------------------------------------    
    ANAGRAFE_TERZI.CANONI;
    ANAGRAFE_TERZI.STORICIZZO;
end ;
