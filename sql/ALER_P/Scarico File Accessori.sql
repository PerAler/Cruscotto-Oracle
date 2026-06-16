--@param PANNO: Anno del bilancio

declare
	V_ANNO number := :PANNO ;
begin
	declare
		provvisorio boolean := false;
		--v_anno number(4) := 2025;

		ctxId    aler.ExcelGen.ctxHandle;
		sheet   aler.ExcelGen.sheetHandle;
		alignment1  aler.ExcelTypes.CT_CellAlignment := aler.ExcelGen.makeAlignment(p_horizontal => 'center', p_vertical => 'center');

		report varchar2(200);
		rc     sys_refcursor;
		v_SQL  varchar2(32000);
	begin
		ctxId := aler.ExcelGen.createContext();
		aler.ExcelGen.setDebug(false);
		ALER.EXCELGEN.SETDATEFORMAT(ctxId,'dd/mm/yyyy');

		select 'Billancio_cong_'||v_anno||descr||'.xlsx', query into report,v_sql from estrazioni.bilancio_query where cod_query='ACUI';
		v_SQL:=replace(v_SQL,'-0',to_char(v_anno));
		v_SQL:=replace(v_SQL,'-1',to_char(v_anno-1));

		open rc for v_sql;
		sheet := aler.ExcelGen.addSheetFromCursor(ctxId, 'File Accesori Totali per UI', rc); --v_sql,p_tabColor => 'gold');
		aler.ExcelGen.setHeader(ctxId, 
								sheet, 
								p_autoFilter => true, 
								p_frozen => true, 
								p_style =>aler.ExcelGen.makeCellStyle(ctxid, 
																	  p_fill => aler.ExcelGen.makePatternFill('solid','gold'),
																	  p_alignment => alignment1)
							   );
	--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
		aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL',report);
		aler.ExcelGen.closeContext(ctxId);
	end;

	declare
		provvisorio boolean := false;
		--v_anno number(4) := 2025;

		ctxId    aler.ExcelGen.ctxHandle;
		sheet   aler.ExcelGen.sheetHandle;
		alignment1  aler.ExcelTypes.CT_CellAlignment := aler.ExcelGen.makeAlignment(p_horizontal => 'center', p_vertical => 'center');

		report varchar2(200);
		rc     sys_refcursor;
		v_SQL  varchar2(32000);
	begin
		ctxId := aler.ExcelGen.createContext();
		aler.ExcelGen.setDebug(false);
		ALER.EXCELGEN.SETDATEFORMAT(ctxId,'dd/mm/yyyy');

		select 'Billancio_cong_'||v_anno||descr||'.xlsx', query into report,v_sql from estrazioni.bilancio_query where cod_query='ACSS';
		v_sql:=replace(v_sql,'-1', to_char(v_anno-1));
		v_sql:=replace(v_sql,'-0', to_char(v_anno));

		open rc for v_sql;
		sheet := aler.ExcelGen.addSheetFromCursor(ctxId, 'File Accesori Sfittanze Servizi', rc); --v_sql,p_tabColor => 'gold');
		aler.ExcelGen.setHeader(ctxId, 
								sheet, 
								p_autoFilter => true, 
								p_frozen => true, 
								p_style =>aler.ExcelGen.makeCellStyle(ctxid, 
																	  p_fill => aler.ExcelGen.makePatternFill('solid','gold'),
																	  p_alignment => alignment1)
							   );
	--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
		aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL',report);
		aler.ExcelGen.closeContext(ctxId);
	end;

	declare
		provvisorio boolean := false;
		--v_anno number(4) := 2025;

		ctxId    aler.ExcelGen.ctxHandle;
		sheet   aler.ExcelGen.sheetHandle;
		alignment1  aler.ExcelTypes.CT_CellAlignment := aler.ExcelGen.makeAlignment(p_horizontal => 'center', p_vertical => 'center');

		report varchar2(200);
		rc     sys_refcursor;
		v_SQL  varchar2(32000);
	begin
		ctxId := aler.ExcelGen.createContext();
		aler.ExcelGen.setDebug(false);
		ALER.EXCELGEN.SETDATEFORMAT(ctxId,'dd/mm/yyyy');

		select 'Billancio_cong_'||v_anno||descr||'.xlsx', query into report,v_sql from estrazioni.bilancio_query where cod_query='ACSF';

		open rc for v_sql;
		sheet := aler.ExcelGen.addSheetFromCursor(ctxId, 'File Accesori Sfittanze Canoni', rc); --v_sql,p_tabColor => 'gold');
		aler.ExcelGen.setHeader(ctxId, 
								sheet, 
								p_autoFilter => true, 
								p_frozen => true, 
								p_style =>aler.ExcelGen.makeCellStyle(ctxid, 
																	  p_fill => aler.ExcelGen.makePatternFill('solid','gold'),
																	  p_alignment => alignment1)
							   );
	--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
		aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL',report);
		aler.ExcelGen.closeContext(ctxId);
	end;

	declare
		provvisorio boolean := false;
		--v_anno number(4) := 2025;

		ctxId    aler.ExcelGen.ctxHandle;
		sheet   aler.ExcelGen.sheetHandle;
		alignment1  aler.ExcelTypes.CT_CellAlignment := aler.ExcelGen.makeAlignment(p_horizontal => 'center', p_vertical => 'center');

		report varchar2(200);
		rc     sys_refcursor;
		v_SQL  varchar2(32000);
	begin
		ctxId := aler.ExcelGen.createContext();
		aler.ExcelGen.setDebug(false);
		ALER.EXCELGEN.SETDATEFORMAT(ctxId,'dd/mm/yyyy');

		select 'Billancio_cong_'||v_anno||descr||'.xlsx', query into report,v_sql from estrazioni.bilancio_query where cod_query='ACRR';

		open rc for v_sql;
		sheet := aler.ExcelGen.addSheetFromCursor(ctxId, 'File Accesori Rid. per Redd.', rc); --v_sql,p_tabColor => 'gold');
		aler.ExcelGen.setHeader(ctxId, 
								sheet, 
								p_autoFilter => true, 
								p_frozen => true, 
								p_style =>aler.ExcelGen.makeCellStyle(ctxid, 
																	  p_fill => aler.ExcelGen.makePatternFill('solid','gold'),
																	  p_alignment => alignment1)
							   );
	--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
		aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL',report);
		aler.ExcelGen.closeContext(ctxId);
	end;

end;

--edit bilancio_query;


