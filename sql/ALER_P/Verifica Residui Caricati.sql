DECLARE
    v_s440_calc  NUMBER;
    v_s208_calc  NUMBER;
    v_s341_calc  NUMBER;
    v_esito      VARCHAR2(30);

    CURSOR c_contratti IS
        SELECT LSC_C_SOCIETA, TIPO_CONTR, CNT_C_CONTRATTO, saldo_canone, saldo_servizi, saldo_altro, ANNO
        FROM giesco.carico_residui_ap where caricato is null;

BEGIN
    FOR r IN c_contratti LOOP

        SELECT
            SUM(CASE WHEN LDS_Q_DETSPESA = 440 THEN VBL_I_BOLDE_EU ELSE 0 END),
            SUM(CASE WHEN LDS_Q_DETSPESA = 208 THEN VBL_I_BOLDE_EU ELSE 0 END),
            SUM(CASE WHEN LDS_Q_DETSPESA = 341 THEN VBL_I_BOLDE_EU ELSE 0 END)
        INTO v_s440_calc, v_s208_calc, v_s341_calc
        FROM giesco.view_estrazioni_contabx
        WHERE cnt_c_Contratto = r.CNT_C_CONTRATTO
          AND pcb_n_anno      = r.ANNO
          AND BLL_D_FATTURA   = TO_DATE(:data_fattura, 'DD-MON-YYYY')
          AND registro        = 'X'
          AND LDS_Q_DETSPESA IN (440, 208, 341);

        IF    NVL(v_s440_calc, 0) = NVL(r.saldo_canone, 0)
          AND NVL(v_s208_calc, 0) = NVL(r.saldo_servizi, 0)
          AND NVL(v_s341_calc, 0) = NVL(r.saldo_altro, 0)
        THEN
            v_esito := 'X';
        ELSE
            v_esito := null;
        END IF;

        UPDATE giesco.carico_residui_ap
        SET    CARICATO = v_esito
        WHERE  CNT_C_CONTRATTO = r.CNT_C_CONTRATTO
          AND  ANNO            = r.ANNO
          AND  LSC_C_SOCIETA   = r.LSC_C_SOCIETA
          AND  TIPO_CONTR      = r.TIPO_CONTR;

    END LOOP;

    COMMIT;
END;
/
