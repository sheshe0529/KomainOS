-- DEC-60: un solo origen por evento de actividad y sin fase; sin frecuencias ni cuentas de servicio predeterminadas;
-- un solo tipo enumerado para el tipo de dato. Todo o nada: cualquier verificación fallida deshace la migración completa.
BEGIN;
SET LOCAL search_path TO "KomainOS";

-- 0. Falla si ya se aplicó
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'KomainOS' AND table_name = 'actividad_ejecucion' AND column_name = 'fase') THEN
        RAISE EXCEPTION 'La migración DEC-60 ya se aplicó: actividad_ejecucion no tiene la columna fase';
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. Verificaciones: los identificadores repetidos deben coincidir con su origen antes de quitarlos
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TEMPORARY TABLE dec60_conteo ON COMMIT DROP AS SELECT count(*) AS eventos FROM actividad_ejecucion;

DO $$
DECLARE
    n integer;
BEGIN
    SELECT count(*) INTO n FROM actividad_ejecucion WHERE id_evaluacion IS NOT NULL AND id_mop_tarea IS NOT NULL;
    IF n > 0 THEN
        RAISE EXCEPTION 'actividad_ejecucion: % evento(s) con evaluación y tarea a la vez', n;
    END IF;

    SELECT count(*) INTO n
    FROM actividad_ejecucion a
    JOIN evaluacion e ON e.id_evaluacion = a.id_evaluacion
    WHERE a.id_servidor <> e.id_servidor OR a.id_orden_detalle IS DISTINCT FROM e.id_orden_detalle;
    IF n > 0 THEN
        RAISE EXCEPTION 'actividad_ejecucion: % evento(s) de análisis con servidor o detalle distinto al de su evaluación', n;
    END IF;

    SELECT count(*) INTO n
    FROM actividad_ejecucion a
    JOIN mop_tarea t ON t.id_mop_tarea = a.id_mop_tarea
    JOIN mop m ON m.id_mop = t.id_mop
    JOIN orden_detalle d ON d.id_orden_detalle = m.id_orden_detalle
    WHERE a.id_servidor <> d.id_servidor OR a.id_orden_detalle IS DISTINCT FROM d.id_orden_detalle;
    IF n > 0 THEN
        RAISE EXCEPTION 'actividad_ejecucion: % evento(s) de tarea con servidor o detalle distinto al de su MOP', n;
    END IF;

    SELECT count(*) INTO n
    FROM actividad_ejecucion a
    JOIN orden_detalle d ON d.id_orden_detalle = a.id_orden_detalle
    WHERE a.id_evaluacion IS NULL AND a.id_mop_tarea IS NULL AND a.id_servidor <> d.id_servidor;
    IF n > 0 THEN
        RAISE EXCEPTION 'actividad_ejecucion: % evento(s) de ejecución con servidor distinto al de su detalle', n;
    END IF;

    SELECT count(*) INTO n
    FROM evaluacion e
    JOIN orden_detalle d ON d.id_orden_detalle = e.id_orden_detalle
    WHERE e.id_servidor <> d.id_servidor;
    IF n > 0 THEN
        RAISE EXCEPTION 'evaluacion: % análisis con servidor distinto al de su detalle de orden', n;
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. actividad_ejecucion: un solo origen por evento, sin servidor ni fase. El CHECK anterior exige el detalle en los
--    eventos de tarea: se retira antes de dejar solo el origen
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE actividad_ejecucion DROP CONSTRAINT ck_actividad_ejecucion_origen;

UPDATE actividad_ejecucion SET id_orden_detalle = NULL
WHERE id_evaluacion IS NOT NULL OR id_mop_tarea IS NOT NULL;

ALTER TABLE actividad_ejecucion
    DROP CONSTRAINT fk_actividad_ejecucion_servidor,
    DROP COLUMN id_servidor,
    DROP COLUMN fase,
    ADD CONSTRAINT ck_actividad_ejecucion_origen CHECK (num_nonnulls(id_orden_detalle, id_evaluacion, id_mop_tarea) = 1);

DROP TYPE enum_fase_actividad;

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. evaluacion: el servidor del análisis de una orden es el de su detalle
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE orden_detalle ADD CONSTRAINT uq_orden_detalle_id_servidor UNIQUE (id_orden_detalle, id_servidor);
ALTER TABLE evaluacion DROP CONSTRAINT fk_evaluacion_orden_detalle;
ALTER TABLE evaluacion ADD CONSTRAINT fk_evaluacion_orden_detalle FOREIGN KEY (id_orden_detalle, id_servidor)
    REFERENCES orden_detalle (id_orden_detalle, id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
CREATE INDEX ix_evaluacion_orden_detalle ON evaluacion (id_orden_detalle) WHERE id_orden_detalle IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. Un solo tipo de dato: un parámetro de lista toma el tipo de su lista de referencia y no guarda otro
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE script_parametro DROP CONSTRAINT ck_script_parametro_fuente;
ALTER TABLE script_parametro ALTER COLUMN tipo_dato DROP NOT NULL;
ALTER TABLE script_parametro ALTER COLUMN tipo_dato TYPE enum_tipo_dato_parametro
    USING (CASE WHEN tipo_dato = 'LISTA' THEN NULL ELSE tipo_dato::text::enum_tipo_dato_parametro END);
ALTER TABLE script_parametro ADD CONSTRAINT ck_script_parametro_fuente CHECK (
    (tipo_dato IS NULL AND id_lista_referencia IS NOT NULL AND valor_por_defecto IS NULL)
    OR (tipo_dato IS NOT NULL AND id_lista_referencia IS NULL));
DROP TYPE enum_tipo_parametro_script;

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. Sin cuentas de servicio predeterminadas. Las configuraciones que usaban la predeterminada la reciben como propia,
--    para que sigan conectándose con la misma cuenta; cada asignación queda en la auditoría
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TEMPORARY TABLE dec60_asignacion ON COMMIT DROP AS
SELECT c.id_configuracion_mantenimiento,
       s.id_servidor,
       g.id_grupo_mantenimiento,
       CASE WHEN c.id_cuenta_servicio_evaluacion IS NULL THEN p.id_cuenta_servicio_evaluacion_predeterminada END AS evaluacion,
       CASE WHEN c.id_cuenta_servicio_mantenimiento IS NULL THEN p.id_cuenta_servicio_mantenimiento_predeterminada END AS mantenimiento
FROM configuracion_mantenimiento c
CROSS JOIN configuracion_sistema p
LEFT JOIN configuracion_servidor s ON s.id_configuracion_mantenimiento = c.id_configuracion_mantenimiento
LEFT JOIN configuracion_grupo g ON g.id_configuracion_mantenimiento = c.id_configuracion_mantenimiento
WHERE (c.id_cuenta_servicio_evaluacion IS NULL AND p.id_cuenta_servicio_evaluacion_predeterminada IS NOT NULL)
   OR (c.id_cuenta_servicio_mantenimiento IS NULL AND p.id_cuenta_servicio_mantenimiento_predeterminada IS NOT NULL);

UPDATE configuracion_mantenimiento c
SET id_cuenta_servicio_evaluacion = COALESCE(c.id_cuenta_servicio_evaluacion, a.evaluacion),
    id_cuenta_servicio_mantenimiento = COALESCE(c.id_cuenta_servicio_mantenimiento, a.mantenimiento)
FROM dec60_asignacion a
WHERE a.id_configuracion_mantenimiento = c.id_configuracion_mantenimiento;

INSERT INTO auditoria (id_servidor, id_grupo_mantenimiento, operacion, entidad, id_entidad, valor_anterior, valor_nuevo,
                       motivo, proceso)
SELECT a.id_servidor, a.id_grupo_mantenimiento, 'ASIGNAR_CUENTA_SERVICIO',
       CASE WHEN a.id_servidor IS NOT NULL THEN 'configuracion_servidor' ELSE 'configuracion_grupo' END,
       a.id_configuracion_mantenimiento, '{"idCuentaServicio":null}',
       json_build_object('idCuentaServicio', cuenta.id)::text,
       cuenta.finalidad || ': la cuenta predeterminada del sistema pasa a ser la propia (DEC-60)', 'MIGRACION_DEC60'
FROM dec60_asignacion a
CROSS JOIN LATERAL (VALUES ('EVALUACION', a.evaluacion), ('MANTENIMIENTO', a.mantenimiento)) AS cuenta(finalidad, id)
WHERE cuenta.id IS NOT NULL;

ALTER TABLE configuracion_sistema
    DROP CONSTRAINT fk_config_sistema_cuenta_predeterminada,
    DROP CONSTRAINT fk_config_sistema_cuenta_evaluacion,
    DROP COLUMN id_cuenta_servicio_evaluacion_predeterminada,
    DROP COLUMN id_cuenta_servicio_mantenimiento_predeterminada;

-- ---------------------------------------------------------------------------------------------------------------------
-- 6. Sin frecuencias predeterminadas por criticidad: cada configuración define las suyas. La revisión periódica
--    inactiva puede no tener frecuencia, y un cierre sin configuración no tiene periodicidad
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE nivel_criticidad
    DROP CONSTRAINT ck_nivel_criticidad_valores,
    DROP COLUMN frecuencia_revision_dias,
    DROP COLUMN frecuencia_mantenimiento_dias,
    ADD CONSTRAINT ck_nivel_criticidad_valores
        CHECK (prioridad >= 0 AND plazo_autorizacion_horas > 0 AND plazo_validacion_horas > 0);

ALTER TABLE configuracion_mantenimiento
    ALTER COLUMN frecuencia_revision_dias DROP NOT NULL,
    ADD CONSTRAINT ck_config_mantenimiento_revision
        CHECK (NOT revision_periodica_habilitada OR frecuencia_revision_dias IS NOT NULL);

ALTER TABLE cierre_orden ALTER COLUMN periodicidad_base_dias DROP NOT NULL;

-- ---------------------------------------------------------------------------------------------------------------------
-- 7. Ningún evento de actividad se perdió
-- ---------------------------------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF (SELECT eventos FROM dec60_conteo) <> (SELECT count(*) FROM actividad_ejecucion) THEN
        RAISE EXCEPTION 'actividad_ejecucion: cambió la cantidad de eventos durante la migración';
    END IF;
END $$;

COMMIT;
