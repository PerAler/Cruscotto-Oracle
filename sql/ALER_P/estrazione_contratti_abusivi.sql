select estrazioni.f_cambiali(176301,'N') from dual;
select * from contratti_abus where DATA_EMISSIONE is not null;

SELECT contratto, sk_key, sk_stato, Accodata, Presentata, gest_006,Incassata,gest_009,protestata ,SK_IMP_TOTALE
FROM (
  SELECT 
    s.inq_contratto     contratto,
    c.sk_key,
    c.pr_s_gest,
    s.sk_stato,
    SUM(c.pr_imp) imp,
    max(s.SK_IMP_TOTALE) SK_IMP_TOTALE
  FROM  camb.cambiali c 
  INNER JOIN camb.schede s ON c.sk_key = s.sk_key 
        join contratti_abus ca on s.inq_sogg_cod = ca.soggetto and s.inq_contratto = ca.cnt_c_contratto
  WHERE s.societa_cod = 100
    AND s.inq_sogg_cod = ca.soggetto
    and s.inq_contratto = ca.cnt_c_contratto
    and s.sk_key=(select max(sk_key) from camb.schede s WHERE s.societa_cod = 100
                    AND s.inq_sogg_cod = ca.soggetto
                    and s.inq_contratto = ca.cnt_c_contratto
                 )
--    AND c.pr_s_gest NOT IN ('009', '005')
  GROUP BY s.inq_contratto, c.sk_key, c.pr_s_gest, s.sk_stato
  )
PIVOT (
  SUM(imp)
  FOR pr_s_gest IN ('003' AS Accodata, '004' AS Presentata, '006' AS gest_006,'005' as Incassata,'009' gest_009,'010' protestata)
)
--        where sk_stato='A'
ORDER BY contratto, sk_stato;


DECLARE
   vAccodata number;
   vPresentata number;
   vgest_006 number;
   vIncassata number;
   vgest_009 number;
   v_tot number;
   vprotestata number;
BEGIN
    for i in (select * from CONTRATTI_ABUS where data_emissione is not null)
    loop
    begin
        SELECT Accodata, Presentata, gest_006,Incassata,gest_009,SK_IMP_TOTALE,protestata
        into  vaccodata,vpresentata,vgest_006,vincassata,vgest_009,v_tot,vprotestata
        FROM (
          SELECT
            s.inq_contratto     contratto,
            c.sk_key,
            c.pr_s_gest,
            s.sk_stato,
            SUM(c.pr_imp) imp,
            max(s.SK_IMP_TOTALE) SK_IMP_TOTALE
          FROM camb.cambiali c
          INNER JOIN camb.schede s ON c.sk_key = s.sk_key
          WHERE s.societa_cod = 100
            AND s.inq_sogg_cod = i.soggetto
            and s.inq_contratto =i.cnt_c_contratto
            and s.sk_key=(select max(sk_key) from camb.schede s WHERE s.societa_cod = 100
                            AND s.inq_sogg_cod = i.soggetto
                            and s.inq_contratto = i.cnt_c_contratto
                         )
        --    AND c.pr_s_gest NOT IN ('009', '005')
          GROUP BY s.inq_contratto, c.sk_key, c.pr_s_gest, s.sk_stato
        )
        PIVOT (
          SUM(imp)
          FOR pr_s_gest IN ('003' AS Accodata, '004' AS Presentata, '006' AS gest_006,'005' as Incassata,'009' gest_009,'010' protestata)
        )
--        where sk_stato='A'
        ;

        update CONTRATTI_ABUS set accodata=vaccodata,presentata=vpresentata,saldata=vgest_006,incassata=vincassata,annullata=vgest_009,totale_em=v_tot,protestata=vprotestata
        where CONTRATTI_ABUS.CNT_C_CONTRATTO=i.CNT_C_CONTRATTO;

        exception when others then
          dbms_output.put_line('Contratto: '||i.cnt_c_Contratto) ;
--        end;  
    end;
    end loop;
END;

alter table contratti_abus drop column ultimo_mov;
alter table contratti_abus add (ultimo_mov_em date, ultimo_mov_inc date);

update contratti_abus c
    set ultimo_mov_EM=(select max(BLL_D_FATTURA) from fat_test where cnt_c_Contratto=c.cnt_c_contratto and bll_t_registro!='X'),
        ultimo_mov_inc=(select max(ICT_D_INCASSO) from inc_incasso_totale where cnt_c_Contratto=c.cnt_c_contratto)
--where ULTIMO_MOV is null
;

select max(BLL_D_FATTURA) from fat_test where cnt_c_Contratto=1972008189 and bll_t_registro!='X';

select max(ICT_D_INCASSO) from inc_incasso_totale where cnt_c_Contratto=1972008189;


select * from contratti_abus order by cnt_c_Contratto;

--2020129848	N	3338967	840	280	30-gen-2025	24745.84	700	0	1110	8856.45
select estrazioni.f_cambiali(3338967,'A') from dual
 -- 24745.84
