--@param Scelta: B bilancio, R Riepilogo, A Accessori, M Morosita 
--@param anno: anno del bilancio
--@param mese: mese del bilancio
DECLARE
Scelta varchar2(1) := :Scelta;
begin
	if Scelta='B' THEN
		estrazioni.bilancio_contabile.lancio_base(:anno,:mese);
	elsif Scelta='R' THEN
		estrazioni.bilancio_contabile.lancio_riepilogo(:anno,:mese);
	elsif Scelta='A' THEN
		estrazioni.BILANCIO_CONTABILE.LANCIO_FILE_ACCESSORI(:anno,:mese);
	elsif Scelta='M' THEN
		estrazioni.ESTRAZIONE_MOROSITA(:anno, to_char(:anno-1)||'1231',to_date('31/12/'||to_char(:anno-1),'dd/mm/yyyy'));
	end if;
end;