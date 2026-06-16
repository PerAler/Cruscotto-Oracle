SELECT *
FROM (
SELECT *
FROM log_msg
WHERE trunc(datainizio)=trunc(sysdate)
ORDER BY 1 desc )
WHERE ROWNUM <= :limite
