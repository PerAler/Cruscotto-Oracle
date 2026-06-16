--@param PANNO: Anno calcolo
	declare
		provvisorio boolean := false;
		VANNO number(4) := :PANNO;

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

		v_SQL:='select * from estrazioni.morosita_estrazioni';
		open rc for v_sql;
		sheet := aler.ExcelGen.addSheetFromCursor(ctxId, 'Morisita '||VANNO, rc); --v_sql,p_tabColor => 'gold');
		aler.ExcelGen.setHeader(ctxId, 
								sheet, 
								p_autoFilter => true, 
								p_frozen => true, 
								p_style =>aler.ExcelGen.makeCellStyle(ctxid, 
																	  p_fill => aler.ExcelGen.makePatternFill('solid','gold'),
																	  p_alignment => alignment1)
							   );
	--    aler.ExcelGen.putCell(ctxid, sheet1, 2, 3, null, aler.ExcelGen.makeCellStyle(ctxid, p_fill => aler.ExcelGen.makePatternFill('solid','chartreuse'), p_alignment => alignment1));
		aler.ExcelGen.createFile(ctxId, 'DATA_FILE_FILE_EXCEL','Morisita '||VANNO||'.xlsx');
		aler.ExcelGen.closeContext(ctxId);
	end;
