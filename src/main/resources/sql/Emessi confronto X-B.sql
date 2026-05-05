select cnt_c_Contratto, BLL_n_FATTu, 
    sum(IMPORTO) from giesco.VIEW_ESTRAZIONI_CONTABX 
Where BLL_D_FATTURA>=To_date('01/01/'|| :anno,'dd/mm/yyyy') 
    And bll_d_fattura<=last_day(to_date('01/12/'|| :anno,'dd/mm/yyyy'))
    And LSC_C_SOCIETA=:societa
    And ((TIPO_IMPORTO='A') Or (LDS_Q_DETSPESA=9)) 
    --and ltc_c_tipo_contr='A98'
    And REGISTRO Not In ('X','Y') --And ID_TIPO_EC=1
group by cnt_c_Contratto,BLL_n_FATTU  
minus
select cnt_c_Contratto, BLL_n_FATTu, 
    sum(VBL_I_IMPORTO) from giesco.VIEW_EMESSO 
Where BLL_D_FATTURA>=To_date('01/01/'|| :anno,'dd/mm/yyyy') 
    And bll_d_fattura<=last_day(to_date('01/12/'|| :anno,'dd/mm/yyyy'))
    And LSC_C_SOCIETA=:societa
    And ((vbl_f_contabile='S') Or (LDS_Q_DETSPESA=9))
    --and ltc_c_tipo_contr='A98'
    And bll_t_REGISTRO Not In ('X','Y') --And ID_TIPO_EC=1
group by cnt_c_Contratto,BLL_n_FATTU  
/