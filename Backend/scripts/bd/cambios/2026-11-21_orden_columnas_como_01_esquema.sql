-- Deja configuracion_mantenimiento y evaluacion con las columnas en el mismo orden que 01_esquema.sql. Las migraciones
-- del DEC-59 (evaluacion.salida) y de la revisión periódica común (revision_periodica_habilitada) agregaron su columna al
-- final de la tabla, mientras que 01_esquema.sql la declara en medio. PostgreSQL no permite mover columnas: se copian los
-- datos, se vuelven a crear ambas tablas tal como las declara 01_esquema.sql y se restauran los datos, las secuencias de
-- identidad y las claves foráneas que las referencian. Todo o nada: cualquier verificación fallida deshace la migración.
BEGIN;
SET LOCAL search_path TO "KomainOS";

-- ---------------------------------------------------------------------------------------------------------------------
-- 0. Falla si ya se aplicó o si las tablas no tienen exactamente las columnas esperadas
-- ---------------------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    cm text;
    ev text;
BEGIN
    SELECT string_agg(column_name, ',' ORDER BY ordinal_position) INTO cm
    FROM information_schema.columns
    WHERE table_schema = current_schema() AND table_name = 'configuracion_mantenimiento';
    SELECT string_agg(column_name, ',' ORDER BY ordinal_position) INTO ev
    FROM information_schema.columns
    WHERE table_schema = current_schema() AND table_name = 'evaluacion';

    IF cm = 'id_configuracion_mantenimiento,id_cuenta_servicio_evaluacion,id_cuenta_servicio_mantenimiento,'
            'frecuencia_revision_dias,revision_periodica_habilitada,frecuencia_mantenimiento_dias,'
            'modalidad_planificacion,fecha_creacion,fecha_actualizacion'
       AND ev = 'id_evaluacion,id_servidor,id_orden_detalle,id_revision_periodica_grupo,id_script_version,'
            'id_cuenta_servicio,tipo,estado,error,salida,fecha_inicio,fecha_fin' THEN
        RAISE EXCEPTION 'La migración ya se aplicó: las columnas ya están en el orden de 01_esquema.sql';
    END IF;
    IF cm IS DISTINCT FROM 'id_configuracion_mantenimiento,id_cuenta_servicio_evaluacion,'
            'id_cuenta_servicio_mantenimiento,frecuencia_revision_dias,frecuencia_mantenimiento_dias,'
            'modalidad_planificacion,fecha_creacion,fecha_actualizacion,revision_periodica_habilitada' THEN
        RAISE EXCEPTION 'configuracion_mantenimiento no tiene las columnas esperadas: %', cm;
    END IF;
    IF ev IS DISTINCT FROM 'id_evaluacion,id_servidor,id_orden_detalle,id_revision_periodica_grupo,id_script_version,'
            'id_cuenta_servicio,tipo,estado,error,fecha_inicio,fecha_fin,salida' THEN
        RAISE EXCEPTION 'evaluacion no tiene las columnas esperadas: %', ev;
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. Copia de los datos y del estado de las secuencias de identidad (las secuencias no se reinician)
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TEMPORARY TABLE orden_cm ON COMMIT DROP AS SELECT * FROM configuracion_mantenimiento;
CREATE TEMPORARY TABLE orden_ev ON COMMIT DROP AS SELECT * FROM evaluacion;
CREATE TEMPORARY TABLE orden_secuencias ON COMMIT DROP AS
    SELECT 'configuracion_mantenimiento'::text AS tabla, last_value, is_called
    FROM configuracion_mantenimiento_id_configuracion_mantenimiento_seq
    UNION ALL
    SELECT 'evaluacion', last_value, is_called FROM evaluacion_id_evaluacion_seq;

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. Retira las claves foráneas que apuntan a las dos tablas y las elimina. Sin CASCADE: si otro objeto dependiera de
--    ellas, la migración falla en lugar de borrarlo
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE configuracion_grupo DROP CONSTRAINT fk_config_grupo_base;
ALTER TABLE configuracion_servidor DROP CONSTRAINT fk_config_servidor_base;
ALTER TABLE actividad_ejecucion DROP CONSTRAINT fk_actividad_ejecucion_evaluacion;
ALTER TABLE medicion_metrica DROP CONSTRAINT fk_medicion_evaluacion;
ALTER TABLE orden DROP CONSTRAINT fk_orden_evaluacion_origen;

DROP TABLE configuracion_mantenimiento;
DROP TABLE evaluacion;

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. Las dos tablas tal como las declara 01_esquema.sql
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE configuracion_mantenimiento (
    id_configuracion_mantenimiento integer CONSTRAINT configuracion_mantenimiento_id_configuracion_mantenimi_not_null NOT NULL,
    id_cuenta_servicio_evaluacion integer,
    id_cuenta_servicio_mantenimiento integer,
    frecuencia_revision_dias integer,
    revision_periodica_habilitada boolean NOT NULL,
    frecuencia_mantenimiento_dias integer CONSTRAINT configuracion_mantenimiento_frecuencia_mantenimiento_d_not_null NOT NULL,
    modalidad_planificacion enum_modalidad_planificacion NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_config_mantenimiento_frecuencias CHECK (((frecuencia_revision_dias > 0) AND (frecuencia_mantenimiento_dias > 0))),
    -- Sin frecuencia predeterminada: la revisión periódica activa exige la suya
    CONSTRAINT ck_config_mantenimiento_revision CHECK (((NOT revision_periodica_habilitada) OR (frecuencia_revision_dias IS NOT NULL)))
);
ALTER TABLE configuracion_mantenimiento ALTER COLUMN id_configuracion_mantenimiento ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME configuracion_mantenimiento_id_configuracion_mantenimiento_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE evaluacion (
    id_evaluacion integer NOT NULL,
    id_servidor integer NOT NULL,
    id_orden_detalle integer,
    id_revision_periodica_grupo integer,
    id_script_version integer NOT NULL,
    id_cuenta_servicio integer NOT NULL,
    tipo enum_tipo_evaluacion NOT NULL,
    estado enum_estado_evaluacion NOT NULL,
    error text,
    salida text,
    fecha_inicio timestamp(0) without time zone NOT NULL,
    fecha_fin timestamp(0) without time zone,
    CONSTRAINT ck_evaluacion_detalle_tipo CHECK ((((tipo = 'REVISION_PERIODICA'::enum_tipo_evaluacion) AND (id_orden_detalle IS NULL)) OR ((tipo = ANY (ARRAY['PREVIA'::enum_tipo_evaluacion, 'POSTERIOR'::enum_tipo_evaluacion])) AND (id_orden_detalle IS NOT NULL)))),
    CONSTRAINT ck_evaluacion_revision_grupo_tipo CHECK (id_revision_periodica_grupo IS NULL OR tipo = 'REVISION_PERIODICA'),
    CONSTRAINT ck_evaluacion_fechas CHECK (((fecha_fin IS NULL) OR (fecha_fin >= fecha_inicio)))
);
ALTER TABLE evaluacion ALTER COLUMN id_evaluacion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME evaluacion_id_evaluacion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. Datos y secuencias. Las columnas se nombran: la copia conserva el orden anterior
-- ---------------------------------------------------------------------------------------------------------------------
INSERT INTO configuracion_mantenimiento (
    id_configuracion_mantenimiento, id_cuenta_servicio_evaluacion, id_cuenta_servicio_mantenimiento,
    frecuencia_revision_dias, revision_periodica_habilitada, frecuencia_mantenimiento_dias, modalidad_planificacion,
    fecha_creacion, fecha_actualizacion)
SELECT id_configuracion_mantenimiento, id_cuenta_servicio_evaluacion, id_cuenta_servicio_mantenimiento,
    frecuencia_revision_dias, revision_periodica_habilitada, frecuencia_mantenimiento_dias, modalidad_planificacion,
    fecha_creacion, fecha_actualizacion
FROM orden_cm;

INSERT INTO evaluacion (
    id_evaluacion, id_servidor, id_orden_detalle, id_revision_periodica_grupo, id_script_version, id_cuenta_servicio,
    tipo, estado, error, salida, fecha_inicio, fecha_fin)
SELECT id_evaluacion, id_servidor, id_orden_detalle, id_revision_periodica_grupo, id_script_version, id_cuenta_servicio,
    tipo, estado, error, salida, fecha_inicio, fecha_fin
FROM orden_ev;

DO $$
BEGIN
    PERFORM setval('configuracion_mantenimiento_id_configuracion_mantenimiento_seq', last_value, is_called)
    FROM orden_secuencias WHERE tabla = 'configuracion_mantenimiento';
    PERFORM setval('evaluacion_id_evaluacion_seq', last_value, is_called)
    FROM orden_secuencias WHERE tabla = 'evaluacion';
END $$;

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. Claves, índices y claves foráneas de 01_esquema.sql (las foráneas validan los datos restaurados)
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE ONLY configuracion_mantenimiento
    ADD CONSTRAINT configuracion_mantenimiento_pkey PRIMARY KEY (id_configuracion_mantenimiento);
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT evaluacion_pkey PRIMARY KEY (id_evaluacion);

ALTER TABLE ONLY configuracion_mantenimiento
    ADD CONSTRAINT fk_config_mantenimiento_cuenta FOREIGN KEY (id_cuenta_servicio_mantenimiento) REFERENCES cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY configuracion_mantenimiento
    ADD CONSTRAINT fk_configuracion_mantenimiento_cuenta_evaluacion FOREIGN KEY (id_cuenta_servicio_evaluacion) REFERENCES cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY configuracion_grupo
    ADD CONSTRAINT fk_config_grupo_base FOREIGN KEY (id_configuracion_mantenimiento) REFERENCES configuracion_mantenimiento(id_configuracion_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY configuracion_servidor
    ADD CONSTRAINT fk_config_servidor_base FOREIGN KEY (id_configuracion_mantenimiento) REFERENCES configuracion_mantenimiento(id_configuracion_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_cuenta FOREIGN KEY (id_cuenta_servicio) REFERENCES cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_orden_detalle FOREIGN KEY (id_orden_detalle, id_servidor) REFERENCES orden_detalle(id_orden_detalle, id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_revision_grupo FOREIGN KEY (id_revision_periodica_grupo) REFERENCES revision_periodica_grupo(id_revision_periodica_grupo) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_script_version FOREIGN KEY (id_script_version) REFERENCES script_version(id_script_version) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_servidor FOREIGN KEY (id_servidor) REFERENCES servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY actividad_ejecucion
    ADD CONSTRAINT fk_actividad_ejecucion_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES evaluacion(id_evaluacion) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY medicion_metrica
    ADD CONSTRAINT fk_medicion_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES evaluacion(id_evaluacion) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY orden
    ADD CONSTRAINT fk_orden_evaluacion_origen FOREIGN KEY (id_evaluacion_origen) REFERENCES evaluacion(id_evaluacion) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_evaluacion_revision_grupo_servidor ON evaluacion (id_revision_periodica_grupo, id_servidor)
    WHERE id_revision_periodica_grupo IS NOT NULL;
CREATE INDEX ix_evaluacion_servidor_fecha_sin_revision_grupal ON evaluacion (id_servidor, fecha_inicio DESC)
    WHERE id_revision_periodica_grupo IS NULL;
CREATE INDEX ix_evaluacion_orden_detalle ON evaluacion (id_orden_detalle) WHERE id_orden_detalle IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------------------------
-- 6. Verificaciones: mismas filas, mismas secuencias y el orden de 01_esquema.sql
-- ---------------------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    n bigint;
BEGIN
    IF (SELECT count(*) FROM configuracion_mantenimiento) <> (SELECT count(*) FROM orden_cm) THEN
        RAISE EXCEPTION 'configuracion_mantenimiento: la cantidad de filas cambió';
    END IF;
    SELECT count(*) INTO n FROM (
        SELECT id_configuracion_mantenimiento, id_cuenta_servicio_evaluacion, id_cuenta_servicio_mantenimiento,
            frecuencia_revision_dias, revision_periodica_habilitada, frecuencia_mantenimiento_dias,
            modalidad_planificacion, fecha_creacion, fecha_actualizacion FROM orden_cm
        EXCEPT ALL
        SELECT id_configuracion_mantenimiento, id_cuenta_servicio_evaluacion, id_cuenta_servicio_mantenimiento,
            frecuencia_revision_dias, revision_periodica_habilitada, frecuencia_mantenimiento_dias,
            modalidad_planificacion, fecha_creacion, fecha_actualizacion FROM configuracion_mantenimiento) d;
    IF n > 0 THEN
        RAISE EXCEPTION 'configuracion_mantenimiento: % fila(s) distintas a las originales', n;
    END IF;

    IF (SELECT count(*) FROM evaluacion) <> (SELECT count(*) FROM orden_ev) THEN
        RAISE EXCEPTION 'evaluacion: la cantidad de filas cambió';
    END IF;
    SELECT count(*) INTO n FROM (
        SELECT id_evaluacion, id_servidor, id_orden_detalle, id_revision_periodica_grupo, id_script_version,
            id_cuenta_servicio, tipo, estado, error, salida, fecha_inicio, fecha_fin FROM orden_ev
        EXCEPT ALL
        SELECT id_evaluacion, id_servidor, id_orden_detalle, id_revision_periodica_grupo, id_script_version,
            id_cuenta_servicio, tipo, estado, error, salida, fecha_inicio, fecha_fin FROM evaluacion) d;
    IF n > 0 THEN
        RAISE EXCEPTION 'evaluacion: % fila(s) distintas a las originales', n;
    END IF;

    SELECT count(*) INTO n
    FROM orden_secuencias s
    JOIN (SELECT 'configuracion_mantenimiento'::text AS tabla, last_value, is_called
          FROM configuracion_mantenimiento_id_configuracion_mantenimiento_seq
          UNION ALL
          SELECT 'evaluacion', last_value, is_called FROM evaluacion_id_evaluacion_seq) a USING (tabla)
    WHERE (s.last_value, s.is_called) IS NOT DISTINCT FROM (a.last_value, a.is_called);
    IF n <> 2 THEN
        RAISE EXCEPTION 'Una secuencia de identidad no conservó su valor';
    END IF;

    IF (SELECT string_agg(column_name, ',' ORDER BY ordinal_position) FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'configuracion_mantenimiento')
       IS DISTINCT FROM 'id_configuracion_mantenimiento,id_cuenta_servicio_evaluacion,id_cuenta_servicio_mantenimiento,'
            'frecuencia_revision_dias,revision_periodica_habilitada,frecuencia_mantenimiento_dias,'
            'modalidad_planificacion,fecha_creacion,fecha_actualizacion'
       OR (SELECT string_agg(column_name, ',' ORDER BY ordinal_position) FROM information_schema.columns
           WHERE table_schema = current_schema() AND table_name = 'evaluacion')
       IS DISTINCT FROM 'id_evaluacion,id_servidor,id_orden_detalle,id_revision_periodica_grupo,id_script_version,'
            'id_cuenta_servicio,tipo,estado,error,salida,fecha_inicio,fecha_fin' THEN
        RAISE EXCEPTION 'Las columnas no quedaron en el orden de 01_esquema.sql';
    END IF;
END $$;

ANALYZE configuracion_mantenimiento;
ANALYZE evaluacion;

COMMIT;
