select * from (
select * from log_msg
where trunc(datainizio)=trunc(sysdate)
order by 1 desc
) where rownum <= :limite
