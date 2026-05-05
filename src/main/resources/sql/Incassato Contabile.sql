--@param sociata:insicare la società per l'estrazione
--@param anno: anno di estrazione dati
Select 
    Decode (ICT_MESE_ESERCIZIO,'01','Gen', '02','Feb',
                               '03','Mar', '04','Apr',
                               '05','Mag', '06','Giu',
                               '07','Lug', '08','Ago',
                               '09','Set', '10','Ott',
                               '11','Nov', '12','Dic',
                               ICT_MESE_ESERCIZIO) MESE,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A00',ICT_I_INCASSO_EU)) A00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'L00',ICT_I_INCASSO_EU, 'L02',ICT_I_INCASSO_EU, 'L03',ICT_I_INCASSO_EU)) L0X,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'H00',ICT_I_INCASSO_EU)) H00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A11',ICT_I_INCASSO_EU)) A11,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'ASS',ICT_I_INCASSO_EU)) ASS,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A12',ICT_I_INCASSO_EU)) A12,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A13',ICT_I_INCASSO_EU)) A13,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A22',ICT_I_INCASSO_EU)) A22,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A23',ICT_I_INCASSO_EU)) A23,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'T00',ICT_I_INCASSO_EU)) T00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A98',ICT_I_INCASSO_EU)) A98,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A24',ICT_I_INCASSO_EU)) A24,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'G00',ICT_I_INCASSO_EU)) G00,
    Sum(ICT_I_INCASSO_EU) As IMPORTO
From --giesco.inc_incasso_spalman_m S, 
        giesco.inc_incasso_totale i 
    join giesco.GSI_LTIPO_CONTR B on  I.LTC_C_TIPO_CONTR = B.LTC_C_TIPO_CONTR And B.LSC_C_SOCIETA = i.lsc_c_societa
Where   I.LSC_C_SOCIETA = :societa
    And I.ICT_ANNO_ESERCIZIO = :anno
    And I.CNT_C_CONTRATTO != '2004000250' 
    And I.ICT_C_CONTO_INCASSO <> 000000015 And ICT_C_CONTO_INCASSO <> 000000016 
--    And I.LTC_C_TIPO_CONTR In ('A00','L00','L02','L03','A11','T00','H00','G00') --,'A22','X00')
Group By ICT_MESE_ESERCIZIO
order by to_number(ICT_MESE_ESERCIZIO)
