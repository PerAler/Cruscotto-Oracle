Select --cnt_c_contratto,  
    Extract(Month From (BLL_D_FATTURA)) CMESE,
    Decode (Extract(Month From (BLL_D_FATTURA)),
                    '1','Gen', '2','Feb', '3','Mar', '4','Apr', 
                    '5','Mag', '6','Giu', '7','Lug', '8','Ago', 
                    '9','Set', '10','Ott', '11','Nov', '12','Dic',
                    Extract(Month From (BLL_D_FATTURA))) MESE, 
    Sum(Decode (LTC_C_TIPO_CONTR,'A00',IMPORTO)) A00,
    Sum(Decode (LTC_C_TIPO_CONTR,'L00',IMPORTO, 'L02',IMPORTO, 'L03',IMPORTO)) L0X,
    Sum(Decode (LTC_C_TIPO_CONTR,'H00',IMPORTO)) H00,
    Sum(Decode (LTC_C_TIPO_CONTR,'A11',IMPORTO)) A11,
    Sum(Decode (LTC_C_TIPO_CONTR,'ASS',IMPORTO)) ASS,
    Sum(Decode (LTC_C_TIPO_CONTR,'A12',IMPORTO)) A12,
    Sum(Decode (LTC_C_TIPO_CONTR,'A13',IMPORTO)) A13,
    Sum(Decode (LTC_C_TIPO_CONTR,'A22',IMPORTO)) A22,
    Sum(Decode (LTC_C_TIPO_CONTR,'A23',IMPORTO)) A23,
    Sum(Decode (LTC_C_TIPO_CONTR,'T00',IMPORTO)) T00,
    Sum(Decode (LTC_C_TIPO_CONTR,'A98',IMPORTO)) A98,
    Sum(Decode (LTC_C_TIPO_CONTR,'A24',IMPORTO)) A24,
    Sum(Decode (LTC_C_TIPO_CONTR,'G00',IMPORTO)) G00,
    Sum(Nvl(IMPORTO,0)) TOTALE_MESE
From giesco.VIEW_ESTRAZIONI_CONTABX 
Where BLL_D_FATTURA>=To_date('01/01/'|| :anno,'dd/mm/yyyy') 
    And bll_d_fattura<=last_day(to_date('01/12/'|| :anno,'dd/mm/yyyy'))
    And LSC_C_SOCIETA=:societa
    And ((TIPO_IMPORTO='A') Or (LDS_Q_DETSPESA=9)) 
--    and LTC_C_TIPO_CONTR in ('A00','A11','A22','A23','G00','H00','L00','L02','L03','T00')
    --and ltc_c_tipo_contr not in ('A98','A12','A13')
--    and ltc_c_tipo_contr='A00'
    And REGISTRO Not In ('X','Y') --And ID_TIPO_EC=1
Group By Extract(Month From (BLL_D_FATTURA)) 
Order By Extract(Month From (BLL_D_FATTURA))
/