Select --cnt_c_contratto  ,
  Extract(Month From (BLL_D_FATTURA)) CMESE,
    Decode (Extract(Month From (BLL_D_FATTURA)),
                    '1','Gen', '2','Feb', '3','Mar', '4','Apr', 
                    '5','Mag', '6','Giu', '7','Lug', '8','Ago', 
                    '9','Set', '10','Ott', '11','Nov', '12','Dic',
                    Extract(Month From (BLL_D_FATTURA))) MESE, 
    Sum(Decode (LTC_C_TIPO_CONTR,'A00',VBL_I_IMPORTO)) A00,
    Sum(Decode (LTC_C_TIPO_CONTR,'L00',VBL_I_IMPORTO, 'L02',VBL_I_IMPORTO, 'L03',VBL_I_IMPORTO)) L0X,
    Sum(Decode (LTC_C_TIPO_CONTR,'H00',VBL_I_IMPORTO)) H00,
    Sum(Decode (LTC_C_TIPO_CONTR,'A11',VBL_I_IMPORTO)) A11,
    Sum(Decode (LTC_C_TIPO_CONTR,'ASS',VBL_I_IMPORTO)) ASS,
    Sum(Decode (LTC_C_TIPO_CONTR,'A12',VBL_I_IMPORTO)) A12,
    Sum(Decode (LTC_C_TIPO_CONTR,'A13',VBL_I_IMPORTO)) A13,
    Sum(Decode (LTC_C_TIPO_CONTR,'A22',VBL_I_IMPORTO)) A22,
    Sum(Decode (LTC_C_TIPO_CONTR,'A23',VBL_I_IMPORTO)) A23,
    Sum(Decode (LTC_C_TIPO_CONTR,'T00',VBL_I_IMPORTO)) T00,
    Sum(Decode (LTC_C_TIPO_CONTR,'A98',VBL_I_IMPORTO)) A98,
    Sum(Decode (LTC_C_TIPO_CONTR,'A24',VBL_I_IMPORTO)) A24,
    Sum(Decode (LTC_C_TIPO_CONTR,'G00',VBL_I_IMPORTO)) G00,
    Sum(Nvl(VBL_I_IMPORTO,0)) TOTALE_MESE
From Giesco.VIEW_EMESSO 
Where BLL_D_FATTURA>=To_date('01/01/'|| :anno,'dd/mm/yyyy') 
    And bll_d_fattura<=last_day(to_date('01/12/'|| :anno,'dd/mm/yyyy'))
    And LSC_C_SOCIETA=:societa
    And ((vbl_f_contabile='S') Or (LDS_Q_DETSPESA=9)) 
    And bll_t_REGISTRO Not In ('X','Y') 
Group By Extract(Month From (BLL_D_FATTURA)) 
Order By 1
/