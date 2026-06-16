--@param tipo: 1 provvisorio 0 Definitivo
declare
    provvisorio int := :tipo;
    v_anno number(4) := :anno; 

    ctxId    aler.ExcelGen.ctxHandle;
    sheet1   aler.ExcelGen.sheetHandle;
    alignment1  aler.ExcelTypes.CT_CellAlignment := aler.ExcelGen.makeAlignment(p_horizontal => 'center', p_vertical => 'center');

    report varchar2(200);
  rc         sys_refcursor;
    v_SQL varchar2(32000);
    v_SQL1 varchar2(32000);
    v_SQL2 varchar2(32000);
    v_SQL3 varchar2(32000);
    v_SQL4 varchar2(32000);
    v_from varchar2(10000);
begin
execute immediate q'[ALTER SESSION SET NLS_NUMERIC_CHARACTERS = '.,']';  

    ctxId := aler.ExcelGen.createContext();
    aler.ExcelGen.setDebug(false);
    ALER.EXCELGEN.SETDATEFORMAT(ctxId,'dd/mm/yyyy');

    v_SQL1 := q'[SELECT c.anno
, c.lsc_c_societa
, c.uni_c_unita
, c.tipo_ui
, c.tipo_ui_descr
, c.ind_v_indirizzo
, c.ind_x_numcivico
, c.cmn_c_cap
, c.cmn_v_comune
, c.ind_x_numscala
, c.ind_x_interno
, c.lpn_c_piano
, c.cmp_c_complesso
, c.lfi_c_filiale
, c.proprieta
, c.proprieta_descr
, uni.uniimm_tippos_codice TIPPOS_CODICE
, uni.tippos_descrizione
, c.assegnazione
, c.assegnazione_descr
, c.categ_cat
, c.categ_cat_descr
, c.classe_cat
, c.rendita_cat
, c.unita_aler_monza
, c.unita_aler_lodi
, c.sup_s_netta
, c.sup_s_sucon
, c.cnt_c_contratto
, c.sgg_c_oldkey
, c.normativa
, c.normativa_descr
, c.tipologia
, c.tipologia_descr
, c.tipo_contr
, c.tipo_contr_descr
, c.proprieta
, c.proprieta_descr
, c.data_inizio_loc
, c.data_sloggio
, c.abusivo
, c.tipo_iva
, c.sgg_v_nominativo
, c.nuf_v_area
, c.nuf_v_classe
, c.soggetto_pa
, c.cnt_f_prat_legale
, c.ple_c_tipo_pl
, c.ple_c_cod_legale
, NVL (c.cnt_n_accessi_ug, 0) CNT_N_ACCESSI_UG
, NVL (c.cnt_n_accessi_ug_anno, 0) CNT_N_ACCESSI_UG_ANNO
, c.cnt_d_primo_acc_ug
, c.cnt_d_ult_acc_ug
, NVL (c.cnt_n_mm, 0) CNT_N_MM
, NVL (c.cnt_n_mm_anno, 0) CNT_N_MM_ANNO
, c.cnt_d_ultima_mm
, c.cnt_i_ultima_mm
, c.pass_perdita
, c.pass_perdita_anno
, c.pres_moros_inc
, c.pres_moros_inc_d"MOROS_INC FINACC"
, DECODE (c.cnt_n_richieste_cs, 0, 'NO', NULL, 'NO', 'SI')C_F_RICHIESTE_CS
, c.cs_flag_istruttoria
, c.num_solleciti_tot NUM_SOLLECITI
, c.num_solleciti_anno
, c.importo_cambiali_attive "Cambiali Attive"
, c.importo_cambiali_anomale "Cambiali Protestate" ]';
	V_SQL2 := (q'[, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
 AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 11), 'dd/mm/yyyy')) "EMESSO -11"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
 AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 11), 'dd/mm/yyyy')) "INCASSO -11"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
 AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 11), 'dd/mm/yyyy')) "SALDO PROGR -11"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
 AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 10), 'dd/mm/yyyy')) "EMESSO -10"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 10), 'dd/mm/yyyy')) "INCASSO -10"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
 AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 10), 'dd/mm/yyyy')) "SALDO PROGR -10"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto 
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 9), 'dd/mm/yyyy')) "EMESSO -9"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 9), 'dd/mm/yyyy')) "INCASSO -9"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 9), 'dd/mm/yyyy')) "SALDO PROGR -9"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 8), 'dd/mm/yyyy')) "EMESSO -8"
 , (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 8), 'dd/mm/yyyy')) "INCASSO -8"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 8), 'dd/mm/yyyy')) "SALDO PROGR -8"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 7), 'dd/mm/yyyy')) "EMESSO -7"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 7), 'dd/mm/yyyy')) "INCASSO -7"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 7), 'dd/mm/yyyy')) "SALDO PROGR -7"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto AND s.lsc_c_societa = c.lsc_c_societa
AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 6), 'dd/mm/yyyy')) "EMESSO -6"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 6), 'dd/mm/yyyy')) "INCASSO -6"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 6), 'dd/mm/yyyy')) "SALDO PROGR -6"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 5), 'dd/mm/yyyy')) "EMESSO -5"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 5), 'dd/mm/yyyy')) "INCASSO -5"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 5), 'dd/mm/yyyy')) "SALDO PROGR -5"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 4), 'dd/mm/yyyy')) "EMESSO -4"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 4), 'dd/mm/yyyy')) "INCASSO -4"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 4), 'dd/mm/yyyy')) "SALDO PROGR -4"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 3), 'dd/mm/yyyy')) "EMESSO -3"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 3), 'dd/mm/yyyy')) "INCASSO -3"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 3), 'dd/mm/yyyy')) "SALDO PROGR -3"
, (SELECT -s.emesso_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 2), 'dd/mm/yyyy')) "EMESSO -2"
, (SELECT s.incassato_anno FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 2), 'dd/mm/yyyy')) "INCASSO -2"
, (SELECT -s.saldo FROM giesco.bilancio_cons_saldi s WHERE s.cnt_c_contratto = c.cnt_c_contratto
AND s.lsc_c_societa = c.lsc_c_societa AND s.ltc_c_tipo_contr = c.tipo_contr AND s.data_al = TO_DATE ('31/12/'||to_char(to_number(c.anno) - 2), 'dd/mm/yyyy')) "SALDO PROGR -2" ]');
    V_SQL3 :=trim(q'[, -ROUND ( (c14.fat_canone + c14.fat_servizi + c14.fat_altro), 2) "FAT TOTALE -1"
, -c14.fat_canone "FAT CANONE -1"
, -c14.fat_servizi "FAT SERVIZI -1"
, -c14.fat_altro "FAT ALTRO -1"
, -c14.fat_canone_ac_per_ap "FAT CANONE -1 PER -2"
, -c14.fat_servizi_ac_per_ap "FAT SERVIZI -1 PER -2"
, -c14.fat_altro_ac_per_ap "FAT ALTRO -1 PER -2"
, c14.totale_incassato "TOTALE INCASSATO -1"
, ROUND ((c14.incassato_canone+ c14.incassato_servizi+ c14.incassato_altro),2) "INCASSATO -1"
, c14.incassato_canone "INCASSATO CANONE -1"
, c14.incassato_servizi "INCASSATO SERVIZI -1"
, c14.incassato_altro "INCASSATO ALTRO -1"
, ROUND ((c14.incassato_canone_ap+ c14.incassato_servizi_ap+ c14.incassato_altro_ap),2) "INCASSATO -1 RES"
, c14.incassato_canone_ap "INCASSATO CANONE -1 RES"
, c14.incassato_servizi_ap "INCASSATO SERVIZI -1 RES"
, c14.incassato_altro_ap "INCASSATO ALTRO -1 RES"
, -c14.saldo_canone "SALDO CANONE -1"
, -c14.saldo_servizi "SALDO SERVIZI -1"
, -c14.saldo_altro "SALDO ALTRO -1"
, c14.de_bollettato "DE BOLLETTATO -1"
, -c14.de_fatturato "DE INC FAT -1"
, -NVL (c14.lc_fatturato, 0) "LC FAT -1 COMP"
, c14.contributo_solidarieta "RID SERV REDD -1"
, -c14.contr_can_lt "CONTR CAN LT EMESSO -1"
, -c14.fatturato_am "FATTURATO -1 AM"
, -c14.totale_saldo "SALDO TOTALE -1"
, -ROUND ( (c.fat_canone + c.fat_servizi + c.fat_altro), 2) "FAT TOTALE -0"
, -c.fat_canone "FAT CANONE -0"
, -c.fat_servizi "FAT SERVIZI -0"
, -c.fat_altro "FAT ALTRO -0"
, -c.fat_canone_ac_per_ap "FAT CANONE -1 PER"
, -c.fat_servizi_ac_per_ap "FAT SERVIZI -1 PER"
, -c.fat_altro_ac_per_ap "FAT ALTRO -1 PER"
, c.totale_incassato "TOTALE INCASSATO -0"
, ROUND ((c.incassato_canone+ c.incassato_servizi+ c.incassato_altro),2) "INCASSATO -0"
, c.incassato_canone "INCASSATO CANONE -0"
, c.incassato_servizi "INCASSATO SERVIZI -0"
, c.incassato_altro "INCASSATO ALTRO -0"
, ROUND ((  c.incassato_canone_ap+ c.incassato_servizi_ap+ c.incassato_altro_ap),2) "INCASSATO -0 RES"
, c.incassato_canone_ap "INCASSATO CANONE -0 RES "
, c.incassato_servizi_ap "INCASSATO SERVIZI -0 RES"
, c.incassato_altro_ap "INCASSATO ALTRO -0 RES"
, -c.saldo_canone "SALDO CANONE -0"
, -c.saldo_servizi "SALDO SERVIZI -0"
, -c.saldo_altro "SALDO ALTRO -0"
, c.de_bollettato "DE BOLLETTATO -0"
, -c.de_fatturato "DE INC FAT -0"
, -NVL (c.lc_fatturato, 0) "LC FAT -1 COMP -0"
, c.contributo_solidarieta "RID SERV REDD -0"
, -c.contr_can_lt "CONTR CAN LT EMESSO -0"
, -c.fatturato_am "FATTURATO -0 AM"
, -c.totale_saldo "SALDO TOTALE -0"
, -c.bollettato_anno "BOLLETTATO -0"
, c.incasso_ec_tot "INCASSATO EC -0"
, c.incasso_ec_pp "INCASSATO EC PP -0"
, c.incasso_ec_spc "INCASSATO EC SPC -0"
, c.incasso_ec_effinc "INCASSATO EC EFFINC -0"
, c.incasso_ec_gc "INCASSATO EC GC -0"
, c.incasso_ec_contr "INCASSATO EC CONTR -0"
, c.incasso_ec_sind "INCASSATO EC SIND -0"
, c.incasso_ec_altro "INCASSATO EC ALTRO -0"
, c.incasso_cont_pp "INCASSATO CONT PP -0"
, c.incasso_cont_spc "INCASSATO CONT SPC -0"
, c.incasso_cont_effinc "INCASSATO CONT EFFINC -0"
, c.incasso_cont_gc "INCASSATO CONT GC -0"
, c.incasso_cont_contr "INCASSATO CONT CONTR -0"
, c.incasso_cont_sind "INCASSATO CONT SIND -0"
, c.incasso_cont_altro "INCASSATO CONT ALTRO -0"
, pm.ict_n_mav_pagati "ICT N MAV PAGATI"
, pm.pagati_ante_num "PAGATI ANTE NUM"
, pm.pagati_ante_imp "PAGATI ANTE IMP"
, pm.pagati_1_90_num "PAGATI 1 90 NUM"
, pm.pagati_1_90_imp "PAGATI 1 90 IMP"
, pm.pagati_91_180_num "PAGATI 91 180 NUM"
, pm.pagati_91_180_imp "PAGATI 91 180 IMP"
, pm.pagati_over180_num "PAGATI OVER180 NUM"
, pm.pagati_over180_imp "PAGATI OVER180 IMP"
, pm.pagati_altro_num "PAGATI ALTRO NUM"
, pm.pagati_altro_imp "PAGATI ALTRO IMP"
, pm.ict_n_mav_non_pagati "ICT N MAV NON PAGATI"
, pm.non_pagati_1_90 "NON PAGATI 1 90"
, pm.non_pagati_91_180 "NON PAGATI 91 180"
, pm.non_pagati_over180 "NON PAGATI OVER180"
, pm.non_pagati_altro "NON PAGATI ALTRO"
, mm.num_mav_emessi "EMESSI FM"
, mm.importo_mav_emesso "IMPORTO EMESSI FM"
, mm.num_mav_inc "INCASSATI FM"
, mm.importo_mav_inc "IMPORTO INC FM"
, mm.num_mav_1bim "EMESSI FM 1BIM"
, mm.importo_mav_1bim "IMPORTO EMESSI FM 1BIM"
, mm.num_mav_2bim "EMESSI FM 2BIM"
, mm.importo_mav_2bim "IMPORTO EMESSI FM 2BIM"
, mm.num_mav_3bim "EMESSI FM 3BIM"
, mm.importo_mav_3bim "IMPORTO EMESSI FM 3BIM"
, mm.num_mav_4bim "EMESSI FM 4BIM"
, mm.importo_mav_4bim "IMPORTO EMESSI FM 4BIM"
, mm.num_mav_5bim "EMESSI FM 5BIM"
, mm.importo_mav_5bim "IMPORTO EMESSI FM 5BIM"
, mm.num_mav_6bim "EMESSI FM 6BIM"
, mm.importo_mav_6bim "IMPORTO EMESSI FM 6BIM"
, mm.num_mav_inc_1bim "INCASSATI FM 1BIM"
, mm.importo_mav_inc_1bim "IMPORTO INCASSATI FM 1BIM"
, mm.num_mav_inc_2bim "INCASSATI FM 2BIM"
, mm.importo_mav_inc_2bim "IMPORTO INCASSATI FM 2BIM"
, mm.num_mav_inc_3bim "INCASSATI FM 3BIM"
, mm.importo_mav_inc_3bim "IMPORTO INCASSATI FM 3BIM"
, mm.num_mav_inc_4bim "INCASSATI FM 4BIM"
, mm.importo_mav_inc_4bim "IMPORTO INCASSATI FM 4BIM"
, mm.num_mav_inc_5bim "INCASSATI FM 5BIM"
, mm.importo_mav_inc_5bim "IMPORTO INCASSATI FM 5BIM"
, mm.num_mav_inc_6bim "INCASSATI FM 6BIM"
, mm.importo_mav_inc_6bim "IMPORTO INCASSATI FM 6BIM"
, c.saldo_ec_oggi "SALDO EC AD OGGI"
, c.data_saldo_oggi "DATA SALDO AD OGGI"
, c.VALORIISEE_V "VALORE ISEE"
, c.ANNOREDDITI_L04 "ANNO REDDITI ANISEE"
, -(c.totale_saldo - NVL (c.incasso_mav_dopo_d_rif, 0)) "SALDO TOT POST"
, -c.debito_occup_abusiva "DEBITO OA"
, -c.fat_amministrazione "FAT AMMINISTRAZIONE"
, c.FAT_CANONE_AC_COMP "FATTURATO CANONE COMPE A.C."]');

v_Sql4:=q'[ ,nvl(E.e_11501020001,0) Emesso_11501020001,nvl(I.i_11501020001,0) Incasso_11501020001
     		,nvl(E.e_23410010001,0) Emesso_23410010001,nvl(I.i_23410010001,0) Incasso_23410010001
     		,nvl(E.e_23410020001,0) Emesso_23410020001,nvl(I.i_23410020001,0) Incasso_23410020001
     		,nvl(E.e_23411010001,0) Emesso_23411010001,nvl(I.i_23411010001,0) Incasso_23411010001
     		,nvl(E.e_23411010005,0) Emesso_23411010005,nvl(I.i_23411010005,0) Incasso_23411010005
     		,nvl(E.e_23413030001,0) Emesso_23413030001,nvl(I.i_23413030001,0) Incasso_23413030001
     		,nvl(E.e_23413040008,0) Emesso_23413040008,nvl(I.i_23413040008,0) Incasso_23413040008
     		,nvl(E.e_23413040009,0) Emesso_23413040009,nvl(I.i_23413040009,0) Incasso_23413040009
--      	,nvl(E.e_23413060005,0) Emesso_23413060005,nvl(I.i_23413060005,0) Incasso_23413060005
--      	,nvl(E.e_23413060013,0) Emesso_23413060013,nvl(I.i_23413060013,0) Incasso_23413060013
--      	,nvl(E.e_23413060014,0) Emesso_23413060014,nvl(I.i_23413060014,0) Incasso_23413060014
--      	,nvl(E.e_23413060016,0) Emesso_23413060016,nvl(I.i_23413060016,0) Incasso_23413060016
     		,nvl(E.e_44101020001,0) Emesso_44101020001,nvl(I.i_44101020001,0) Incasso_44101020001
     		,nvl(E.e_44101020002,0) Emesso_44101020002,nvl(I.i_44101020002,0) Incasso_44101020002
     		,nvl(E.e_44101020006,0) Emesso_44101020006,nvl(I.i_44101020006,0) Incasso_44101020006
     		,nvl(E.e_44101020007,0) Emesso_44101020007,nvl(I.i_44101020007,0) Incasso_44101020007
     		,nvl(E.e_44101020008,0) Emesso_44101020008,nvl(I.i_44101020008,0) Incasso_44101020008
     		,nvl(E.e_44101020009,0) Emesso_44101020009,nvl(I.i_44101020009,0) Incasso_44101020009
     		,nvl(E.e_44101020010,0) Emesso_44101020010,nvl(I.i_44101020010,0) Incasso_44101020010
     		,nvl(E.e_44101030002,0) Emesso_44101030002,nvl(I.i_44101030002,0) Incasso_44101030002
     		,nvl(E.e_44101040001,0) Emesso_44101040001,nvl(I.i_44101040001,0) Incasso_44101040001
     		,nvl(E.e_44101040004,0) Emesso_44101040004,nvl(I.i_44101040004,0) Incasso_44101040004
     		,nvl(E.e_44101040008,0) Emesso_44101040008,nvl(I.i_44101040008,0) Incasso_44101040008
     		,nvl(E.e_44101040010,0) Emesso_44101040010,nvl(I.i_44101040010,0) Incasso_44101040010
     		,nvl(E.e_44101050001,0) Emesso_44101050001,nvl(I.i_44101050001,0) Incasso_44101050001
     		,nvl(E.e_44101050002,0) Emesso_44101050002,nvl(I.i_44101050002,0) Incasso_44101050002
     		,nvl(E.e_44101050003,0) Emesso_44101050003,nvl(I.i_44101050003,0) Incasso_44101050003
     		,nvl(E.e_44101050005,0) Emesso_44101050005,nvl(I.i_44101050005,0) Incasso_44101050005
     		,nvl(E.e_44101060001,0) Emesso_44101060001,nvl(I.i_44101060001,0) Incasso_44101060001
--     		,nvl(E.e_44105010005,0) Emesso_44105010005,nvl(I.i_44105010005,0) Incasso_44105010005
--     		,nvl(E.e_44105040007,0) Emesso_44105040007,nvl(I.i_44105040007,0) Incasso_44105040007
     		,nvl(E.e_44316010001,0) Emesso_44316010001,nvl(I.i_44316010001,0) Incasso_44316010001
     		,nvl(E.e_44316040004,0) Emesso_44316040004,nvl(I.i_44316040004,0) Incasso_44316040004
     		,nvl(E.e_44316040006,0) Emesso_44316040006,nvl(I.i_44316040006,0) Incasso_44316040006
     		,nvl(E.e_44520020001,0) Emesso_44520020001,nvl(I.i_44520020001,0) Incasso_44520020001
     		,nvl(E.e_44520020004,0) Emesso_44520020004,nvl(I.i_44520020004,0) Incasso_44520020004
     		,nvl(E.e_44520020005,0) Emesso_44520020005,nvl(I.i_44520020005,0) Incasso_44520020005
     		,nvl(E.e_55207010004,0) Emesso_55207010004,nvl(I.i_55207010004,0) Incasso_55207010004
     		,nvl(E.e_55207020002,0) Emesso_55207020002,nvl(I.i_55207020002,0) Incasso_55207020002
     		,nvl(E.e_55317040002,0) Emesso_55317040002,nvl(I.i_55317040002,0) Incasso_55317040002
     		,nvl(E.e_55207010019,0) Emesso_55207010019,nvl(I.i_55207010019,0) Incasso_55207010019
     		,nvl(E.e_55317040003,0) Emesso_55317040003,nvl(I.i_55317040003,0) Incasso_55317040003
     		,nvl(E.e_55207010013,0) Emesso_55207010013,nvl(I.i_55207010013,0) Incasso_55207010013
     		,nvl(E.e_4101050603 ,0) Emesso_4101050603 ,nvl(I.i_4101050603 ,0) Incasso_4101050603 
     		,nvl(E.e_3413060017 ,0) Emesso_3413060017 ,nvl(I.i_3413060017 ,0) Incasso_3413060017 
            ,nvl(E.e_4101060203 ,0) Emesso_4101060203 ,nvl(I.i_4101060203 ,0) Incasso_4101060203
            ,nvl(e.e_44101040009,0) Emesso_44101040009,nvl(I.i_44101040009,0) Incasso_44101040009
     	  				           ,nvl(I.i_11701030001,0) Incasso_11701030001
     	  				           ,nvl(I.i_11701020001,0) Incasso_11701020001
     	 				           ,nvl(I.i_11701010005,0) Incasso_11701010005
     	 				           ,nvl(I.i_11501100001,0) Incasso_11501100001
     	 				           ,nvl(I.i_11501040001,0) Incasso_11501040001
     	 				           ,nvl(I.i_3413060022 ,0) Incasso_3413060022 
     	 				           ,nvl(I.i_23413060018,0) Incasso_23413060018
      	 				           ,nvl(I.i_3413060016 ,0) Incasso_3413060016 
      	 				           ,nvl(I.i_3413060026 ,0) Incasso_3413060026 
     	 					       ,nvl(I.i_3413060030 ,0) Incasso_3413060030 
    		,nvl(E.e_NULL,0) Emesso_NULL,nvl(I.i_NULL,0)    Incasso_Null ]';

v_from:=q'[  FROM estrazioni.bilancio_cons c,
       estrazioni.bilancio_cons_-1 c14,
       estrazioni.bilancio_cons_pag_mav pm,
       clark.bilancio_mav_massiva mm,
       (select * from estrazioni.bilancio_cons_xconto_emesso
		pivot(
			sum(importo) for conto_incassi in (
 11501020001 e_11501020001,23410010001 e_23410010001,23410020001 e_23410020001,23411010001 e_23411010001,23411010005 e_23411010005,23413030001 e_23413030001,23413040008 e_23413040008,23413040009 e_23413040009
,44101020001 e_44101020001,44101020002 e_44101020002,44101020006 e_44101020006,44101020007 e_44101020007,44101020008 e_44101020008,44101020009 e_44101020009,44101020010 e_44101020010,44101030002 e_44101030002
,44101040001 e_44101040001,44101040004 e_44101040004,44101040008 e_44101040008,44101040010 e_44101040010,44101050001 e_44101050001,44101050002 e_44101050002,44101050003 e_44101050003,44101050005 e_44101050005
,44101060001 e_44101060001,44316010001 e_44316010001,44316040004 e_44316040004,44316040006 e_44316040006,44520020001 e_44520020001,44520020004 e_44520020004,44520020005 e_44520020005,55207010004 e_55207010004
,55207020002 e_55207020002,55317040002 e_55317040002,55207010019 e_55207010019,55317040003 e_55317040003,55207010013 e_55207010013,4101050603  e_4101050603 ,3413060017  e_3413060017 ,4101060203  e_4101060203 
,44101040009 e_44101040009,'NULL' e_null)
			)
		where anno=-0)  e,
       (select * from estrazioni.bilancio_cons_xconto_incassi
		pivot(
			sum(importo) for conto_incassi in (
 11501020001 i_11501020001,23410010001 i_23410010001,23410020001 i_23410020001,23411010001 i_23411010001,23411010005 i_23411010005,23413030001 i_23413030001,23413040008 i_23413040008,23413040009 i_23413040009
,44101020001 i_44101020001,44101020002 i_44101020002,44101020006 i_44101020006,44101020007 i_44101020007,44101020008 i_44101020008,44101020009 i_44101020009,44101020010 i_44101020010,44101030002 i_44101030002
,44101040001 i_44101040001,44101040004 i_44101040004,44101040008 i_44101040008,44101040010 i_44101040010,44101050001 i_44101050001,44101050002 i_44101050002,44101050003 i_44101050003,44101050005 i_44101050005
,44101060001 i_44101060001,44316010001 i_44316010001,44316040004 i_44316040004,44316040006 i_44316040006,44520020001 i_44520020001,44520020004 i_44520020004,44520020005 i_44520020005,55207010004 i_55207010004
,55207020002 i_55207020002,55317040002 i_55317040002,55207010019 i_55207010019,55317040003 i_55317040003,55207010013 i_55207010013,4101050603  i_4101050603 ,3413060017  i_3413060017 ,4101060203  i_4101060203 
,44101040009 i_44101040009,11701030001 i_11701030001,11701020001 i_11701020001,11701010005 i_11701010005,11501100001 i_11501100001,11501040001 i_11501040001,3413060022  i_3413060022 ,23413060018 i_23413060018
,3413060016  i_3413060016 ,3413060026  i_3413060026 ,3413060030  i_3413060030 ,'NULL' i_null)
			)
		where anno=-0) i,
	   matter.uniimm_indiri_full uni
 WHERE     c.cnt_c_contratto = c14.cnt_c_contratto(+)
       AND c.lsc_c_societa = c14.lsc_c_societa(+)
       AND c.tipo_contr = c14.tipo_contr(+)
       AND c.tipo_contr NOT IN ('A98', 'T00', 'A12')
       AND c.cnt_c_contratto = pm.cnt_c_contratto(+)
       AND c.cnt_c_contratto = mm.cnt_c_contratto(+)
       AND c.cnt_c_contratto = e.cnt_c_contratto(+)
       AND c.tipo_contr = e.ltc_c_tipo_contr(+)
       AND c.cnt_c_contratto = i.cnt_c_contratto(+)
       AND c.tipo_contr = i.ltc_c_tipo_contr(+)
       AND c.lsc_c_societa = uni.uniimm_propra_id(+)
       AND c.UNI_C_UNITA = uni.uniimm_codice(+) 
 ]';
	v_sql3 := replace(v_sql3,'-0',to_char(v_anno));
	v_sql3 := replace(v_sql3,'-1',to_char(v_anno-1));
	v_sql3 := replace(v_sql3,'-2',to_char(v_anno-2));
	v_from := replace(v_from,'-1',to_char(v_anno-1));
	v_from := replace(v_from,'-0',to_char(v_anno));
	if provvisorio=0 then
        v_sql2 := replace(v_sql2,'-11',to_char(v_anno-11));
        v_sql2 := replace(v_sql2,'-10',to_char(v_anno-10));
        v_sql2 := replace(v_sql2,'-9',to_char(v_anno-9));
        v_sql2 := replace(v_sql2,'-8',to_char(v_anno-8));
        v_sql2 := replace(v_sql2,'-7',to_char(v_anno-7));
        v_sql2 := replace(v_sql2,'-6',to_char(v_anno-6));
        v_sql2 := replace(v_sql2,'-5',to_char(v_anno-5));
        v_sql2 := replace(v_sql2,'-4',to_char(v_anno-4));
        v_sql2 := replace(v_sql2,'-3',to_char(v_anno-3));
        v_sql2 := replace(v_sql2,'-2',to_char(v_anno-2));
       v_sql := v_sql1||v_sql2||v_SQL3||V_SQL4||V_from;   
    else
--        v_sql := v_sql1||v_SQL3||V_SQL4||v_from;  
        v_sql := v_sql1||v_sql3||v_sql4||v_from;  
	end if;   
--    dbms_output.put_line('v_sql1: '||(v_sql1));
--    dbms_output.put_line('v_sql2: '||(v_sql2));
--    dbms_output.put_line('v_sql3: '||(v_sql3));
--    dbms_output.put_line('v_sql4: '||(v_sql4));
--    dbms_output.put_line('v_sfrom: '||(v_from));
--    dbms_output.put_line('v_sql: '||v_sql);
    if provvisorio=0 then
        report := 'Billancio_cong_'||v_anno||'_Definitivo.xlsx';
    else
        report := 'Billancio_cong_'||v_anno||'_Provvisorio.xlsx';
    end if;
    open rc for v_sql;
    sheet1 := aler.ExcelGen.addSheetFromCursor(ctxId, 'Bilancio Contabile', rc); --v_sql,p_tabColor => 'gold');
--    aler.ExcelGen.setHeader(ctxId, sheet1);
---------------- gestione formato colonne ----------------------------------------------
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,1,'0',p_width => 8);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,3,p_width => 12);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,6,p_width => 20);

    aler.ExcelGen.setColumnFormat(ctxId, sheet1,29,p_width => 19);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,30,p_width => 16);
    
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,39,'dd/mm/yyyy',p_width => 18);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,40,'dd/mm/yyyy',p_width => 18);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,56,'dd/mm/yyyy',p_width => 18);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,52,'dd/mm/yyyy',p_width => 20);
    aler.ExcelGen.setColumnFormat(ctxId, sheet1,53,'dd/mm/yyyy',p_width => 20);
	if provvisorio=0 then
        aler.ExcelGen.setColumnFormat(ctxId, sheet1,211,'dd/mm/yyyy',p_width => 18);
    else
        aler.ExcelGen.setColumnFormat(ctxId, sheet1,181,'dd/mm/yyyy',p_width => 18);
    end if;
----------------------------------------------------------------------------------------    
----    aler.ExcelGen.setTableFormat(ctxId, sheet1, 'TableStyleLight1');
----    ALER.EXCELGEN.PUTCELL(ctxid, sheet1, 1, 10, null, aler.ExcelGen.makeCellStyle(ctxid,p_fill =>aler.ExcelGen.makePatternFill('solid','chartreuse')),p_alignment => alignment1);
----    ExcelGen.putCell(ctxid, sheet1, 1, 1,p_style =>aler.ExcelGen.makeCellStyle(ctxId, p_fill => ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
    aler.ExcelGen.setHeader(ctxId, sheet1, p_autoFilter => true, p_frozen => true, p_style =>aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','gold'),p_alignment => alignment1));
--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
    aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL',report);
    aler.ExcelGen.closeContext(ctxId);

end;

