set echo off
set feedback on
set verify off
set pagesize 5000
set linesize 300
set serveroutput on

-- Parametri di test (adatta se necessario)
define societa = '100'
define anno = '2025'

prompt =========================================
prompt TEST A: VERSIONE PRECEDENTE
prompt =========================================

alter session set statistics_level = all;

var societa varchar2(20)
var anno number

begin
  :societa := '&societa';
  :anno := to_number('&anno');
end;
/

select /* TEST_A */
       decode(i.ict_mese_esercizio,'01','Gen','02','Feb','03','Mar','04','Apr',
                                   '05','Mag','06','Giu','07','Lug','08','Ago',
                                   '09','Set','10','Ott','11','Nov','12','Dic',
                                   i.ict_mese_esercizio) as mese,
       sum(decode(i.ltc_c_tipo_contr,'A00',s.s_i_importo)) as a00,
       sum(decode(i.ltc_c_tipo_contr,'L00',s.s_i_importo,'L02',s.s_i_importo,'L03',s.s_i_importo)) as l0x,
       sum(decode(i.ltc_c_tipo_contr,'H00',s.s_i_importo)) as h00,
       sum(decode(i.ltc_c_tipo_contr,'A11',s.s_i_importo)) as a11,
       sum(decode(i.ltc_c_tipo_contr,'ASS',s.s_i_importo)) as ass,
       sum(decode(i.ltc_c_tipo_contr,'A12',s.s_i_importo)) as a12,
       sum(decode(i.ltc_c_tipo_contr,'A13',s.s_i_importo)) as a13,
       sum(decode(i.ltc_c_tipo_contr,'A22',s.s_i_importo)) as a22,
       sum(decode(i.ltc_c_tipo_contr,'A23',s.s_i_importo)) as a23,
       sum(decode(i.ltc_c_tipo_contr,'T00',s.s_i_importo)) as t00,
       sum(decode(i.ltc_c_tipo_contr,'A98',s.s_i_importo)) as a98,
       sum(decode(i.ltc_c_tipo_contr,'A24',s.s_i_importo)) as a24,
       sum(decode(i.ltc_c_tipo_contr,'G00',s.s_i_importo)) as g00,
       sum(s.s_i_importo) as importo
from (
        select s.ics_c_contratto,
               s.ics_anno,
               s.ics_mese,
               s.ics_soc,
               sum(s.ics_i_importo) as s_i_importo
          from giesco.inc_incasso_spalman_m s
         where s.ics_soc = :societa
           and s.ics_anno = :anno
         group by s.ics_c_contratto,
                  s.ics_anno,
                  s.ics_mese,
                  s.ics_soc
     ) s
join (
        select i.cnt_c_contratto,
               i.lsc_c_societa,
               i.ict_anno_esercizio,
               i.ict_mese_esercizio,
               i.ltc_c_tipo_contr
          from giesco.inc_incasso_totale i
         where i.lsc_c_societa = :societa
           and i.ict_anno_esercizio = :anno
           and i.cnt_c_contratto != '2004000250'
           and i.ict_c_conto_incasso not in ('15', '16')
           and exists (
               select 1
                 from giesco.gsi_ltipo_contr b
                where b.lsc_c_societa = i.lsc_c_societa
                  and b.ltc_c_tipo_contr = i.ltc_c_tipo_contr
           )
         group by i.lsc_c_societa,
                  i.ict_anno_esercizio,
                  i.ict_mese_esercizio,
                  i.cnt_c_contratto,
                  i.ltc_c_tipo_contr
     ) i
  on i.lsc_c_societa = s.ics_soc
 and i.ict_anno_esercizio = s.ics_anno
 and i.ict_mese_esercizio = s.ics_mese
 and i.cnt_c_contratto = s.ics_c_contratto
group by i.ict_mese_esercizio
order by i.ict_mese_esercizio;

select *
from table(dbms_xplan.display_cursor(null, null, 'ALLSTATS LAST +PEEKED_BINDS +OUTLINE +ALIAS'));

prompt =========================================
prompt TEST B: VERSIONE ATTUALE (con spalm_keys)
prompt =========================================

with
spalm as (
  select /*+ qb_name(spalm_qb) materialize */
         s.ics_c_contratto,
         s.ics_anno,
         s.ics_mese,
         s.ics_soc,
         sum(s.ics_i_importo) as s_i_importo
    from giesco.inc_incasso_spalman_m s
   where s.ics_soc = :societa
     and s.ics_anno = :anno
   group by s.ics_c_contratto,
            s.ics_anno,
            s.ics_mese,
            s.ics_soc
),
spalm_keys as (
  select /*+ qb_name(spalm_keys_qb) materialize */
         distinct
         s.ics_soc,
         s.ics_anno,
         s.ics_mese,
         s.ics_c_contratto
    from spalm s
),
incasso as (
  select /*+ qb_name(inc_qb) materialize INDEX(i, IDX1_INCASSI) */
         i.cnt_c_contratto,
         i.lsc_c_societa,
         i.ict_anno_esercizio,
         i.ict_mese_esercizio,
         i.ltc_c_tipo_contr
    from giesco.inc_incasso_totale i
   where i.lsc_c_societa = :societa
     and i.ict_anno_esercizio = :anno
     and i.cnt_c_contratto != '2004000250'
     and i.ict_c_conto_incasso not in ('15', '16')
     and exists (
         select 1
           from giesco.gsi_ltipo_contr b
          where b.lsc_c_societa = i.lsc_c_societa
            and b.ltc_c_tipo_contr = i.ltc_c_tipo_contr
     )
     and exists (
         select 1
           from spalm_keys sk
          where sk.ics_soc = i.lsc_c_societa
            and sk.ics_anno = i.ict_anno_esercizio
            and sk.ics_mese = i.ict_mese_esercizio
            and sk.ics_c_contratto = i.cnt_c_contratto
     )
   group by i.lsc_c_societa,
            i.ict_anno_esercizio,
            i.ict_mese_esercizio,
            i.cnt_c_contratto,
            i.ltc_c_tipo_contr
)
select /*+ qb_name(main_qb) LEADING(s i) USE_HASH(i) NO_PUSH_PRED(s) NO_PUSH_PRED(i) TEST_B */
       decode(i.ict_mese_esercizio,'01','Gen','02','Feb','03','Mar','04','Apr',
                                   '05','Mag','06','Giu','07','Lug','08','Ago',
                                   '09','Set','10','Ott','11','Nov','12','Dic',
                                   i.ict_mese_esercizio) as mese,
       sum(decode(i.ltc_c_tipo_contr,'A00',s.s_i_importo)) as a00,
       sum(decode(i.ltc_c_tipo_contr,'L00',s.s_i_importo,'L02',s.s_i_importo,'L03',s.s_i_importo)) as l0x,
       sum(decode(i.ltc_c_tipo_contr,'H00',s.s_i_importo)) as h00,
       sum(decode(i.ltc_c_tipo_contr,'A11',s.s_i_importo)) as a11,
       sum(decode(i.ltc_c_tipo_contr,'ASS',s.s_i_importo)) as ass,
       sum(decode(i.ltc_c_tipo_contr,'A12',s.s_i_importo)) as a12,
       sum(decode(i.ltc_c_tipo_contr,'A13',s.s_i_importo)) as a13,
       sum(decode(i.ltc_c_tipo_contr,'A22',s.s_i_importo)) as a22,
       sum(decode(i.ltc_c_tipo_contr,'A23',s.s_i_importo)) as a23,
       sum(decode(i.ltc_c_tipo_contr,'T00',s.s_i_importo)) as t00,
       sum(decode(i.ltc_c_tipo_contr,'A98',s.s_i_importo)) as a98,
       sum(decode(i.ltc_c_tipo_contr,'A24',s.s_i_importo)) as a24,
       sum(decode(i.ltc_c_tipo_contr,'G00',s.s_i_importo)) as g00,
       sum(s.s_i_importo) as importo
  from spalm s
  join incasso i
    on i.lsc_c_societa = s.ics_soc
   and i.ict_anno_esercizio = s.ics_anno
   and i.ict_mese_esercizio = s.ics_mese
   and i.cnt_c_contratto = s.ics_c_contratto
group by i.ict_mese_esercizio
order by i.ict_mese_esercizio;

select *
from table(dbms_xplan.display_cursor(null, null, 'ALLSTATS LAST +PEEKED_BINDS +OUTLINE +ALIAS'));
