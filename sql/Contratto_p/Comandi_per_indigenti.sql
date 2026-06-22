SELECT * FROM anaso.an_anagrafica_soggetti where AN_CODICEFISCALE=''
/
SELECT * FROM aurolnet.au_compositione_nucleo  where CODICE_SOGGETTO=3392124
/
declare err varchar2(200);
begin
  PROC_INDIGENTI('20261102430',:vnucleo,'A',err);
  dbms_output.put_line('Errore '||err);
end;
/
select * from proce.tavolaprocedimenti where tp_cp_s11='20261102430'
/
select * from INDIGENTI_TESTATA where cod_nucleo=:vnucleo
/
delete from indigenti_testata where cod_nucleo=:vnucleo
/
select * from table(fn_indigenti_ric()) where nucleo=:vnucleo
/
update INDIGENTI_TESTATA 
set CNT_D_INILOC=trunc(sysdate),CNT_D_FINEROG= trunc(sysdate-1)
where cod_nucleo=:vnucleo and stato='C'
/
