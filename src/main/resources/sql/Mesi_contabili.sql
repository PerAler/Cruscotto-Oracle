Select A.ANNO,A.MESE,A.MATRICOLA MATR_A,A.DATA ULTIMO_APERTO,A.STATO APERTO,C.MATRICOLA MATR_C,C.DATA ULTIMA_CHIUSURA,C.STATO CHIUSO From
(Select QUANDO Data, SOCIETA, ANNO, MESE, STATO, MATRICOLA
From giesco.EC2010_ESERCIZIO_CONTABILE e
Where ANNO=:anno And SOCIETA=:societa And STATO='APERTO'
and quando=(select max(quando) From giesco.EC2010_ESERCIZIO_CONTABILE 
            Where ANNO=:anno And SOCIETA=:societa And STATO='APERTO'
            and mese= e.mese)
) a,
(Select QUANDO Data, SOCIETA, ANNO, MESE,  STATO, MATRICOLA
From giesco.EC2010_ESERCIZIO_CONTABILE e
Where ANNO=:anno And SOCIETA=:societa And STATO='CHIUSO'
and quando=(select max(quando) From giesco.EC2010_ESERCIZIO_CONTABILE 
            Where ANNO=:anno And SOCIETA=:societa And STATO='CHIUSO'
            and mese= e.mese)) c
Where A.MESE=C.MESE(+)
Order By 2,5,4,1
