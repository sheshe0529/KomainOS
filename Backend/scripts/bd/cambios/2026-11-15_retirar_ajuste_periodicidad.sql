BEGIN;
SET LOCAL search_path TO "KomainOS";

ALTER TABLE cierre_orden
    DROP CONSTRAINT fk_cierre_factor,
    DROP CONSTRAINT ck_cierre_orden_valores,
    DROP COLUMN id_factor_ciclo,
    DROP COLUMN factor_aplicado,
    ADD CONSTRAINT ck_cierre_orden_valores CHECK (periodicidad_base_dias > 0);

ALTER TABLE configuracion_sistema
    DROP CONSTRAINT ck_configuracion_sistema_valores,
    DROP COLUMN min_ciclos_racha_estable,
    ADD CONSTRAINT ck_configuracion_sistema_valores CHECK (
        max_ejecuciones_concurrentes > 0 AND max_duracion_tarea_minutos > 0
        AND max_duracion_mop_minutos > 0 AND minutos_expiracion_token > 0
    );

DROP TABLE factor_ciclo;
DROP TYPE enum_resultado_ciclo;

COMMIT;
