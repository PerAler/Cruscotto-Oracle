--@param nome: nome del file esempio '20251231 Definitivo';
declare
  p_periodo varchar2(100):= :nome; 
  vanno varchar2(4):= :anno;
  ctx      aler.ExcelGen.ctxHandle;
  sheet1   aler.ExcelGen.sheetHandle;
  sheet2   aler.ExcelGen.sheetHandle;
  sheet3   aler.ExcelGen.sheetHandle;
  table1   aler.ExcelGen.tableHandle;
  table2   aler.ExcelGen.tableHandle;
  table3   aler.ExcelGen.tableHandle;
  rc       sys_refcursor;
  rc1      sys_refcursor;
  rc2      sys_refcursor;
  v_tot    number;
  v_inc    number;
begin
  ctx := aler.ExcelGen.createContext(aler.ExcelGen.FILE_XLSX);
  
/* Primo foglio del file excel di analisi Bilancio */  
  sheet1 := aler.ExcelGen.addSheet(ctx, 'Verifiche Bilancio');

  open rc for select v.voce,null,null,null, v.valore_totale,v.valore_parziale,
                     case substr(voce, 1, 4) when '00 -' then null
                      when '08.b' then valore_totale-VALORE_PARZIALE - (select valore_totale from estrazioni.verifica_bilancio where substr(voce, 1, 4) = '08.c')
                      when '08.c' then null
                      else VALORE_TOTALE - VALORE_PARZIALE
                      end differenza 
                from estrazioni.verifica_bilancio v;
  table1:=ALER.EXCELGEN.ADDTABLE(ctx,sheet1,rc);

execute immediate q'[ALTER SESSION SET NLS_NUMERIC_CHARACTERS = '.,']';  
  open rc1 for Select Count(*)"Num Contratti",TIPO_CONTR "Tipo Contratto",
                Sum(SALDO_CANONE)*-1 "Canone",Sum(SALDO_SERVIZI)*-1 "Servizi",Sum(SALDO_ALTRO)*-1 "Altro",Sum(TOTALE_SALDO)*-1 "Saldo",
                Sum(bilancio_cons.TOTALE_FATTURATO) "Fatturato Anno",Sum(bilancio_cons.TOTALE_INCASSATO) "Incassato Anno"
             From estrazioni.bilancio_cons
             Group By TIPO_CONTR
             order by decode (tipo_contr,'A00',0,'L00',1,'L01',1,'L02',1,'L03',1,'H00',2,'ASS',3,'A11',4,'A22',5,'A23',6,'A24',7,'G00',8,9);
  table2 := aler.ExcelGen.addTable(ctx , sheet1, rc1
            , p_anchorRowOffset => 2
            , p_anchorColOffset => -6
            , p_anchorTableId => table1
            , p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT
            );
 
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A1:D1');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A2:D2');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A3:D3');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A4:D4');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A5:D5');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A6:D6');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A7:D7');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A8:D8');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A9:D9');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A10:D10');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A11:D11');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A12:D12');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A13:D13');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A14:D14');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A15:D15');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A16:D16');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A17:D17');
  aler.ExcelGen.mergeCells(ctx, sheet1, 'A18:D18');

  aler.ExcelGen.setTableHeader(ctx, sheet1, table1, aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;text-align:center'));
  aler.ExcelGen.setTableHeader(ctx, sheet1, table2, aler.ExcelGen.makeCellStyleCss(ctx, 'background:yellowgreen;font-weight:bold;text-align:center'));

  aler.ExcelGen.setColumnFormat(ctx, sheet1,1,'#,###',p_width => 21);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,2,'#,###.##',p_width => 21);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,3,'#,###.##',p_width => 21);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,4,'#,###.##',p_width => 21);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,5,'#,###.##',p_width => 14);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,6,'#,##0.#0',p_width => 14);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,7,'#,##0.#0',p_width => 14);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,8,'#,##0.#0',p_width => 14);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,9,'#,##0.#0',p_width => 14);
  aler.ExcelGen.setColumnFormat(ctx, sheet1,10,'#,##0.#0',p_width => 14);
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'G2:G18', aler.ExcelGen.makeCellStyleCss(ctx, 'color:red'));
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'J1:J1', aler.ExcelGen.makeCellStyleCss(ctx, 'color:red'));

  execute immediate 'Select Sum(TOTALE_SALDO) From estrazioni.bilancio_cons_'||to_char(vanno-1) into v_tot ;
  aler.ExcelGen.putStringCell(ctx, sheet1, 0, 7, 'Saldo AP', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.PUTNUMBERCELL(ctx, sheet1, 0, 8, v_tot, p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;font-style:italic'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 0, 9, 'I1+E15', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-style:italic'));

  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -7, 'SUM(A21:A31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -5, 'SUM(C21:C31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -4, 'SUM(D21:D31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -3, 'SUM(E21:E31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -2, 'SUM(F21:F31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;font-style:italic'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, -1, 'SUM(G21:G31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 1, 0, 'SUM(H21:H31)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 2, -2, 'F32-E7', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:normal;color:red'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 2, -1, 'E4+G32', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'color:red'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, 2, 0, 'H32-E3', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'color:red'));
  aler.ExcelGen.putStringCell(ctx, sheet1, 1, 1, 'Totali', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;font-style:italic;text-align:right'));
  aler.ExcelGen.putStringCell(ctx, sheet1, 2, 1, 'Verifiche', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;font-style:italic;text-align:right'));

  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'A20:H20',aler.ExcelGen.makeCellStyleCss(ctx, 'border-bottom:thin solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'A31:I31',aler.ExcelGen.makeCellStyleCss(ctx, 'border-bottom:thin solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'B21:B31',aler.ExcelGen.makeCellStyleCss(ctx, 'border-right:thin solid black;text-align:center'));
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'F21:F32',aler.ExcelGen.makeCellStyleCss(ctx, 'border-right:thin solid black;font-weight:bold')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'F33:F33',aler.ExcelGen.makeCellStyleCss(ctx, 'border-right:thin solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'F32:I32',aler.ExcelGen.makeCellStyleCss(ctx, 'border-bottom:thin solid black'));
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'G21:G25',aler.ExcelGen.makeCellStyleCss(ctx, 'background:#DDD9C4')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'H21:H32',aler.ExcelGen.makeCellStyleCss(ctx, 'border-right:thin solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'H21:H25',aler.ExcelGen.makeCellStyleCss(ctx, 'background:#C5D9F1')); 
  aler.ExcelGen.putFormulaCell(ctx, sheet1, -6, 1, 'SUM(G21:G25)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'background:#DDD9C4;font-weight:bold'));
  aler.ExcelGen.putFormulaCell(ctx, sheet1, -6, 2, 'SUM(H21:H25)', p_anchorTableId => table2, p_anchorPosition => aler.ExcelGen.BOTTOM_RIGHT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'background:#C5D9F1;font-weight:bold'));
/***********************************************************************************************/

/* Secondo  foglio del file excel di analisi Bilancio */  
  sheet2 := aler.ExcelGen.addSheet(ctx, 'Riepilogo Spalmature');
  open rc2 for Select A.ANNO,A.MESE,A.MATRICOLA MATR_A,A.DATA "Data APERTO",A.STATO Stato,C.MATRICOLA MATR_C,C.DATA "Data CHIUSURA",C.STATO Stato1 From
                (Select QUANDO Data, SOCIETA, ANNO, MESE, STATO, MATRICOLA
                From giesco.EC2010_ESERCIZIO_CONTABILE e
                Where ANNO=vanno And SOCIETA='100' And STATO='APERTO'
                and quando=(select max(quando) From giesco.EC2010_ESERCIZIO_CONTABILE 
                            Where ANNO=vanno And SOCIETA='100' And STATO='APERTO'
                            and mese= e.mese)
                ) a,
                (Select QUANDO Data, SOCIETA, ANNO, MESE,  STATO, MATRICOLA
                From giesco.EC2010_ESERCIZIO_CONTABILE e
                Where ANNO=vanno And SOCIETA='100' And STATO='CHIUSO'
                and quando=(select max(quando) From giesco.EC2010_ESERCIZIO_CONTABILE 
                            Where ANNO=vanno And SOCIETA='100' And STATO='CHIUSO'
                            and mese= e.mese)) c
                Where A.MESE=C.MESE(+)
                Order By 2,5,4,1;
  table3 := aler.ExcelGen.addTable(ctx, sheet2, rc2, p_anchorRowOffset => 1, p_anchorPosition => aler.ExcelGen.TOP_LEFT);

  aler.ExcelGen.setColumnFormat(ctx, sheet2,1,'0',p_width => 14);
  aler.excelgen.putStringCell(ctx,sheet2,0,0,'Anno', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,2,'00',p_width => 14);
  aler.excelgen.putStringCell(ctx,sheet2,0,1,'Mese', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold;text-align:center'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,3,'0',p_width => 10);
  aler.excelgen.putStringCell(ctx,sheet2,0,2,'Utente', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,4,'dd/mm/yyyy',p_width => 11);
  aler.excelgen.putStringCell(ctx,sheet2,0,3,'Data Apertura', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,5,'0',p_width => 8);
  aler.excelgen.putStringCell(ctx,sheet2,0,4,'Stato', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,6,'0',p_width => 10);
  aler.excelgen.putStringCell(ctx,sheet2,0,5,'Utente', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,7,'dd/mm/yyyy',p_width => 11);
  aler.excelgen.putStringCell(ctx,sheet2,0,6,'Data Chiusura', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.setColumnFormat(ctx, sheet2,8,'0',p_width => 8);
  aler.excelgen.putStringCell(ctx,sheet2,0,7,'Stato', p_anchorTableId => table1, p_anchorPosition => aler.ExcelGen.TOP_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));

  aler.ExcelGen.addCondFormattingRule(
    p_ctxId     => ctx
  , p_sheetId   => sheet2
  , p_type      => ALER.EXCELTYPES.CF_TYPE_EXPR --CF_TYPE_COLORSCALE
  , p_cellRange => aler.ExcelTypes.ST_Sqref('F2:H13')
  , p_style     => aler.ExcelGen.makeCondFmtStyleCss(ctx, 'background-color:#92D050')
  , p_value1    => '$F2>$A2'
  );
  aler.ExcelGen.addCondFormattingRule(
    p_ctxId     => ctx
  , p_sheetId   => sheet2
  , p_type      => ALER.EXCELTYPES.CF_TYPE_EXPR --CF_TYPE_COLORSCALE
  , p_cellRange => aler.ExcelTypes.ST_Sqref('A2:E13')
  , p_style     => aler.ExcelGen.makeCondFmtStyleCss(ctx, 'background-color:#92D050')
  , p_value1    => '$A2>$F2'
  );

  aler.ExcelGen.setRangeStyle(ctx, sheet1, 'B2:B12',aler.ExcelGen.makeCellStyleCss(ctx, 'text-align:center'));
  aler.excelgen.putStringCell(ctx,sheet2,2,0,'Data ultimo allineamento Spalmatore ', p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.excelgen.putStringCell(ctx,sheet2,3,0,'Incassi registrati ', p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.excelgen.putStringCell(ctx,sheet2,3,1,'Incassi Spalmati ', p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));

    Select 
            valore_totale,
            valore_parziale
             into v_inc,v_tot 
      From estrazioni.verifica_bilancio
     where voce like '10 %'
    ;
  aler.ExcelGen.setNumFormat(ctx, sheet2, '#,##0.#0');
  aler.excelgen.PUTNUMBERCELL(ctx,sheet2,4,0,v_inc, p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.excelgen.PUTNUMBERCELL(ctx,sheet2,4,1,v_tot, p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'font-weight:bold'));
  aler.ExcelGen.PUTNUMBERCELL(ctx, sheet2,4,2, v_inc-v_tot, p_anchorTableId => table3, p_anchorPosition => aler.ExcelGen.BOTTOM_LEFT,p_style=>aler.ExcelGen.makeCellStyleCss(ctx, 'color:red'));

  aler.ExcelGen.setRangeStyle(ctx, sheet2, 'A15:D15',aler.ExcelGen.makeCellStyleCss(ctx, 'border-top:thick solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet2, 'A16:D16',aler.ExcelGen.makeCellStyleCss(ctx, 'border-bottom:thick solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet2, 'A15:A16',aler.ExcelGen.makeCellStyleCss(ctx, 'border-left:thick solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet2, 'D15:D16',aler.ExcelGen.makeCellStyleCss(ctx, 'border-right:thick solid black')); 
  aler.ExcelGen.setRangeStyle(ctx, sheet2, 'A15:D15',aler.ExcelGen.makeCellStyleCss(ctx, 'border-bottom:thin solid black')); 

/**********************************************************************************************
    sheet3 := aler.ExcelGen.addSheet(ctx, 'Foglio COGE');
q'[Select 
    Decode (ICT_MESE_ESERCIZIO,'01','Gen', '02','Feb',
                               '03','Mar', '04','Apr',
                               '05','Mag', '06','Giu',
                               '07','Lug', '08','Ago',
                               '09','Set', '10','Ott',
                               '11','Nov', '12','Dic',
                               ICT_MESE_ESERCIZIO) MESE,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A00',ICS_I_IMPORTO)) A00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'L00',ICS_I_IMPORTO, 'L02',ICS_I_IMPORTO, 'L03',ICS_I_IMPORTO)) L0X,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'H00',ICS_I_IMPORTO)) H00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A11',ICS_I_IMPORTO)) A11,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'ASS',ICS_I_IMPORTO)) ASS,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A12',ICS_I_IMPORTO)) A12,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A13',ICS_I_IMPORTO)) A13,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A22',ICS_I_IMPORTO)) A22,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A23',ICS_I_IMPORTO)) A23,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'T00',ICS_I_IMPORTO)) T00,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A98',ICS_I_IMPORTO)) A98, --34660.3
    Sum(Decode (I.LTC_C_TIPO_CONTR,'A24',ICS_I_IMPORTO)) A24,
    Sum(Decode (I.LTC_C_TIPO_CONTR,'G00',ICS_I_IMPORTO)) G00,
    Sum(S.ICS_I_IMPORTO) As IMPORTO
From giesco.inc_incasso_spalman_m S join inc_incasso_totale i on I.ICT_ANNO_ESERCIZIO=ICS_ANNO and I.LSC_C_SOCIETA =ICS_SOC
    join (Select * From GSI_LTIPO_CONTR Where LSC_C_SOCIETA='100') B  on B.LSC_C_SOCIETA = ICS_SOC and I.LTC_C_TIPO_CONTR = B.LTC_C_TIPO_CONTR
Where   I.LSC_C_SOCIETA = '100'
    And I.ICT_ANNO_ESERCIZIO = '2025'
    And I.CNT_C_CONTRATTO != '2004000250' 
    And I.ICT_C_CONTO_INCASSO <> 000000015 And ICT_C_CONTO_INCASSO <> 000000016 
--    And I.LTC_C_TIPO_CONTR In ('A00','L00','L02','L03','A11','T00','H00','G00') --,'A22','X00')
    And Substr(S.ICS_C_INCASSO, 1, 3) = I.LSC_C_SOCIETA
    And Substr(S.ICS_C_INCASSO, 5, 4) = I.ICT_ANNO_ESERCIZIO
    And Substr(S.ICS_C_INCASSO, 10, 10) = I.CNT_C_CONTRATTO
    And Substr(S.ICS_C_INCASSO, 21) = I.ICT_ID
Group By ICT_MESE_ESERCIZIO
order by to_number(ICT_MESE_ESERCIZIO)]'
;
*/

  aler.ExcelGen.createFile(ctx, 'DATA_FILE_FILE_EXCEL', 'File Controllo Bilancio '|| p_periodo ||'.xlsx');
  aler.ExcelGen.closeContext(ctx);
end;
/