-- Cambio 2026-10-23 (R2.4 final, DEC-40) para bases creadas antes: se ejecuta una sola vez y conserva los datos.
-- Lleva el esquema de 49 a 52 tablas, igual al de 01_esquema.sql:
--   * credencial_version -> credencial_secreto: un único secreto vigente por credencial (se conserva el último;
--     las credenciales revocadas pierden el suyo, igual que al revocar desde el sistema).
--   * evaluacion y mop_tarea registran la cuenta de servicio en lugar de la versión del secreto.
--   * programacion_orden se integra en orden: queda la programación vigente (la de mayor versión).
--   * Umbrales con excepciones por servidor, grupo, entorno o criticidad y umbrales de lista
--     (lista_referencia, lista_referencia_item, medicion_lista_item, umbral_excepcion).
-- Requiere PostgreSQL 15 o superior (UNIQUE NULLS NOT DISTINCT). Haga un pg_dump antes de aplicarlo.
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'KomainOS' AND table_name = 'credencial_secreto') THEN
        RAISE EXCEPTION 'El cambio ya fue aplicado en esta base (credencial_secreto existe)';
    END IF;
    IF EXISTS (SELECT 1 FROM orden o
               WHERE NOT EXISTS (SELECT 1 FROM programacion_orden p WHERE p.id_orden = o.id_orden)) THEN
        RAISE EXCEPTION 'Hay órdenes sin programación: no se puede completar orden.fecha_objetivo';
    END IF;
    IF EXISTS (SELECT 1 FROM orden WHERE origen = 'CONDICION_CRITICA') THEN
        RAISE EXCEPTION 'Hay órdenes por condición crítica sin evaluación de origen (ck_orden_evaluacion_origen)';
    END IF;
    IF EXISTS (SELECT 1 FROM umbral GROUP BY id_metrica, nombre HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Hay umbrales con el mismo nombre en una métrica (uq_umbral_metrica_nombre)';
    END IF;
END $$;

-- 1. Secreto vigente de cada credencial ------------------------------------------------------------------

CREATE TABLE credencial_secreto (
    id_credencial integer NOT NULL,
    tipo_autenticacion enum_tipo_autenticacion NOT NULL,
    secreto_cifrado bytea NOT NULL,
    iv_nonce bytea NOT NULL,
    tag_autenticacion bytea NOT NULL,
    algoritmo character varying(100) NOT NULL,
    tipo_usuario enum_tipo_usuario,
    su_secreto_cifrado bytea,
    su_iv_nonce bytea,
    su_tag_autenticacion bytea,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_credencial_secreto_su CHECK ((((su_secreto_cifrado IS NULL) = (su_iv_nonce IS NULL)) AND ((su_secreto_cifrado IS NULL) = (su_tag_autenticacion IS NULL)) AND ((tipo_usuario IS NOT DISTINCT FROM 'GENERICO'::enum_tipo_usuario) = (su_secreto_cifrado IS NOT NULL)))),
    CONSTRAINT credencial_secreto_iv_nonce_check CHECK ((octet_length(iv_nonce) <= 64)),
    CONSTRAINT credencial_secreto_su_iv_nonce_check CHECK ((octet_length(su_iv_nonce) <= 64)),
    CONSTRAINT credencial_secreto_su_tag_autenticacion_check CHECK ((octet_length(su_tag_autenticacion) <= 64)),
    CONSTRAINT credencial_secreto_tag_autenticacion_check CHECK ((octet_length(tag_autenticacion) <= 64))
);
ALTER TABLE ONLY credencial_secreto
    ADD CONSTRAINT credencial_secreto_pkey PRIMARY KEY (id_credencial);
ALTER TABLE ONLY credencial_secreto
    ADD CONSTRAINT fk_credencial_secreto_credencial FOREIGN KEY (id_credencial) REFERENCES credencial(id_credencial) ON UPDATE RESTRICT ON DELETE CASCADE;

-- El id de la credencial es el dato asociado del cifrado (CifradorCredenciales): los secretos siguen descifrándose
INSERT INTO credencial_secreto (id_credencial, tipo_autenticacion, secreto_cifrado, iv_nonce, tag_autenticacion, algoritmo,
                                tipo_usuario, su_secreto_cifrado, su_iv_nonce, su_tag_autenticacion, fecha_actualizacion)
SELECT DISTINCT ON (v.id_credencial)
       v.id_credencial, v.tipo_autenticacion, v.secreto_cifrado, v.iv_nonce, v.tag_autenticacion, v.algoritmo,
       v.tipo_usuario, v.su_secreto_cifrado, v.su_iv_nonce, v.su_tag_autenticacion, v.fecha_creacion
FROM credencial_version v
JOIN credencial c ON c.id_credencial = v.id_credencial
WHERE c.estado = 'VIGENTE'
ORDER BY v.id_credencial, v.numero_version DESC;

-- 2. Las ejecuciones registran la cuenta de servicio ---------------------------------------------------

ALTER TABLE evaluacion ADD COLUMN id_cuenta_servicio integer;
UPDATE evaluacion e SET id_cuenta_servicio = v.id_credencial
FROM credencial_version v WHERE v.id_credencial_version = e.id_credencial_version;
ALTER TABLE evaluacion ALTER COLUMN id_cuenta_servicio SET NOT NULL;
ALTER TABLE evaluacion DROP CONSTRAINT fk_evaluacion_credencial_version, DROP COLUMN id_credencial_version;
ALTER TABLE ONLY evaluacion
    ADD CONSTRAINT fk_evaluacion_cuenta FOREIGN KEY (id_cuenta_servicio) REFERENCES cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE mop_tarea ADD COLUMN id_cuenta_servicio integer;
UPDATE mop_tarea t SET id_cuenta_servicio = v.id_credencial
FROM credencial_version v WHERE v.id_credencial_version = t.id_credencial_version;
ALTER TABLE mop_tarea DROP CONSTRAINT fk_mop_tarea_credencial_version, DROP COLUMN id_credencial_version;
ALTER TABLE ONLY mop_tarea
    ADD CONSTRAINT fk_mop_tarea_cuenta FOREIGN KEY (id_cuenta_servicio) REFERENCES cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;

DROP TABLE credencial_version;

-- 3. Programación vigente dentro de la orden ------------------------------------------------------------

ALTER TABLE orden
    ADD COLUMN id_evaluacion_origen integer,
    ADD COLUMN fecha_objetivo timestamp(0) without time zone,
    ADD COLUMN fecha_inicio_programada timestamp(0) without time zone,
    ADD COLUMN fecha_fin_programada timestamp(0) without time zone,
    ADD COLUMN fecha_evaluacion_programada timestamp(0) without time zone,
    ADD COLUMN inicio_ventana_aplicada timestamp(0) without time zone,
    ADD COLUMN fin_ventana_aplicada timestamp(0) without time zone;

-- La fecha objetivo no cambia al reprogramar: se toma de la primera versión, el resto de la vigente
UPDATE orden o
SET fecha_objetivo = primera.fecha_objetivo,
    fecha_inicio_programada = vigente.fecha_inicio_programada,
    fecha_fin_programada = vigente.fecha_fin_programada,
    fecha_evaluacion_programada = coalesce(vigente.fecha_evaluacion_programada, vigente.fecha_inicio_programada),
    inicio_ventana_aplicada = vigente.inicio_ventana_aplicada,
    fin_ventana_aplicada = vigente.fin_ventana_aplicada
FROM (SELECT DISTINCT ON (id_orden) * FROM programacion_orden ORDER BY id_orden, numero_version DESC) vigente,
     (SELECT DISTINCT ON (id_orden) * FROM programacion_orden ORDER BY id_orden, numero_version) primera
WHERE vigente.id_orden = o.id_orden AND primera.id_orden = o.id_orden;

ALTER TABLE orden
    ALTER COLUMN fecha_objetivo SET NOT NULL,
    ALTER COLUMN fecha_inicio_programada SET NOT NULL,
    ALTER COLUMN fecha_fin_programada SET NOT NULL,
    ALTER COLUMN fecha_evaluacion_programada SET NOT NULL,
    ALTER COLUMN inicio_ventana_aplicada SET NOT NULL,
    ALTER COLUMN fin_ventana_aplicada SET NOT NULL,
    ADD CONSTRAINT ck_orden_evaluacion_origen CHECK (((origen = 'CONDICION_CRITICA'::enum_origen_orden) = (id_evaluacion_origen IS NOT NULL))),
    ADD CONSTRAINT ck_orden_programacion CHECK (((fecha_fin_programada >= fecha_inicio_programada) AND (fin_ventana_aplicada >= inicio_ventana_aplicada)));
ALTER TABLE ONLY orden
    ADD CONSTRAINT fk_orden_evaluacion_origen FOREIGN KEY (id_evaluacion_origen) REFERENCES evaluacion(id_evaluacion) ON UPDATE RESTRICT ON DELETE RESTRICT;

DROP TABLE programacion_orden;

-- 4. Métricas y mediciones de lista ---------------------------------------------------------------------

-- Las métricas registradas hasta ahora son escalares y numéricas
ALTER TABLE metrica
    ADD COLUMN tipo_dato enum_tipo_dato_parametro NOT NULL DEFAULT 'DECIMAL',
    ADD COLUMN es_lista boolean DEFAULT false NOT NULL;
ALTER TABLE metrica ALTER COLUMN tipo_dato DROP DEFAULT;

ALTER TABLE medicion_metrica
    ALTER COLUMN valor DROP NOT NULL,
    ADD COLUMN instancia character varying(255) DEFAULT ''::character varying NOT NULL;
ALTER TABLE medicion_metrica DROP CONSTRAINT uq_medicion_evaluacion_metrica;
ALTER TABLE ONLY medicion_metrica
    ADD CONSTRAINT uq_medicion_evaluacion_metrica UNIQUE (id_evaluacion, id_metrica, instancia);

CREATE TABLE medicion_lista_item (
    id_medicion_metrica integer NOT NULL,
    valor character varying(255) NOT NULL
);
ALTER TABLE ONLY medicion_lista_item
    ADD CONSTRAINT medicion_lista_item_pkey PRIMARY KEY (id_medicion_metrica, valor);
ALTER TABLE ONLY medicion_lista_item
    ADD CONSTRAINT fk_medicion_lista_item_medicion FOREIGN KEY (id_medicion_metrica) REFERENCES medicion_metrica(id_medicion_metrica) ON UPDATE RESTRICT ON DELETE CASCADE;

-- 5. Listas de referencia ---------------------------------------------------------------------------------

CREATE TABLE lista_referencia (
    id_lista_referencia integer NOT NULL,
    nombre character varying(255) NOT NULL,
    tipo_dato enum_tipo_dato_parametro NOT NULL,
    ignora_mayusculas boolean DEFAULT false NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE lista_referencia ALTER COLUMN id_lista_referencia ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME lista_referencia_id_lista_referencia_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE lista_referencia_item (
    id_lista_referencia integer NOT NULL,
    valor character varying(255) NOT NULL,
    fecha_alta timestamp(6) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_baja timestamp(6) without time zone,
    CONSTRAINT ck_lista_referencia_item_vigencia CHECK (((fecha_baja IS NULL) OR (fecha_baja > fecha_alta)))
);
ALTER TABLE ONLY lista_referencia
    ADD CONSTRAINT lista_referencia_pkey PRIMARY KEY (id_lista_referencia);
ALTER TABLE ONLY lista_referencia
    ADD CONSTRAINT uq_lista_referencia_nombre UNIQUE (nombre);
ALTER TABLE ONLY lista_referencia_item
    ADD CONSTRAINT lista_referencia_item_pkey PRIMARY KEY (id_lista_referencia, valor, fecha_alta);
CREATE UNIQUE INDEX uq_lista_referencia_item_vigente ON lista_referencia_item USING btree (id_lista_referencia, valor) WHERE (fecha_baja IS NULL);
ALTER TABLE ONLY lista_referencia_item
    ADD CONSTRAINT fk_lista_referencia_item_lista FOREIGN KEY (id_lista_referencia) REFERENCES lista_referencia(id_lista_referencia) ON UPDATE RESTRICT ON DELETE CASCADE;

-- 6. Umbral numérico o de lista ---------------------------------------------------------------------------

ALTER TABLE umbral DROP CONSTRAINT ck_umbral_coherencia;
ALTER TABLE umbral
    ADD COLUMN id_lista_referencia integer,
    ALTER COLUMN operador DROP NOT NULL,
    ALTER COLUMN valor_advertencia DROP NOT NULL,
    ALTER COLUMN valor_critico DROP NOT NULL,
    ADD COLUMN nivel_faltante enum_nivel_alerta,
    ADD COLUMN nivel_sobrante enum_nivel_alerta,
    ADD COLUMN fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    ADD COLUMN fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    ADD CONSTRAINT ck_umbral_modalidad CHECK ((((operador IS NOT NULL) AND ((valor_advertencia IS NOT NULL) OR (valor_critico IS NOT NULL)) AND (id_lista_referencia IS NULL) AND (nivel_faltante IS NULL) AND (nivel_sobrante IS NULL)) OR ((operador IS NULL) AND (valor_advertencia IS NULL) AND (valor_critico IS NULL) AND (id_lista_referencia IS NOT NULL) AND ((nivel_faltante IS NOT NULL) OR (nivel_sobrante IS NOT NULL))))),
    ADD CONSTRAINT ck_umbral_coherencia CHECK (((valor_advertencia IS NULL) OR (valor_critico IS NULL) OR ((operador = ANY (ARRAY['MAYOR'::enum_operador_comparacion, 'MAYOR_IGUAL'::enum_operador_comparacion])) AND (valor_critico >= valor_advertencia)) OR ((operador = ANY (ARRAY['MENOR'::enum_operador_comparacion, 'MENOR_IGUAL'::enum_operador_comparacion])) AND (valor_critico <= valor_advertencia)) OR (operador = ANY (ARRAY['IGUAL'::enum_operador_comparacion, 'DISTINTO'::enum_operador_comparacion]))));
ALTER TABLE ONLY umbral
    ADD CONSTRAINT uq_umbral_metrica_nombre UNIQUE (id_metrica, nombre);
ALTER TABLE ONLY umbral
    ADD CONSTRAINT fk_umbral_lista FOREIGN KEY (id_lista_referencia) REFERENCES lista_referencia(id_lista_referencia) ON UPDATE RESTRICT ON DELETE RESTRICT;

CREATE TABLE umbral_excepcion (
    id_umbral_excepcion integer NOT NULL,
    id_umbral integer NOT NULL,
    id_servidor integer,
    id_grupo_mantenimiento integer,
    id_entorno integer,
    id_nivel_criticidad integer,
    id_lista_referencia integer,
    valor_advertencia numeric(20,6),
    valor_critico numeric(20,6),
    motivo character varying(500),
    activo boolean DEFAULT true NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_umbral_excepcion_alcance CHECK ((num_nonnulls(id_servidor, id_grupo_mantenimiento, id_entorno, id_nivel_criticidad) = 1)),
    CONSTRAINT ck_umbral_excepcion_valores CHECK ((((id_lista_referencia IS NOT NULL) AND (valor_advertencia IS NULL) AND (valor_critico IS NULL)) OR ((id_lista_referencia IS NULL) AND ((valor_advertencia IS NOT NULL) OR (valor_critico IS NOT NULL)))))
);
ALTER TABLE umbral_excepcion ALTER COLUMN id_umbral_excepcion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME umbral_excepcion_id_umbral_excepcion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT umbral_excepcion_pkey PRIMARY KEY (id_umbral_excepcion);
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT uq_umbral_excepcion_alcance UNIQUE NULLS NOT DISTINCT (id_umbral, id_servidor, id_grupo_mantenimiento, id_entorno, id_nivel_criticidad);
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_umbral FOREIGN KEY (id_umbral) REFERENCES umbral(id_umbral) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_servidor FOREIGN KEY (id_servidor) REFERENCES servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_entorno FOREIGN KEY (id_entorno) REFERENCES entorno(id_entorno) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_criticidad FOREIGN KEY (id_nivel_criticidad) REFERENCES nivel_criticidad(id_nivel_criticidad) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY umbral_excepcion
    ADD CONSTRAINT fk_umbral_excepcion_lista FOREIGN KEY (id_lista_referencia) REFERENCES lista_referencia(id_lista_referencia) ON UPDATE RESTRICT ON DELETE RESTRICT;

-- 7. Lo que conserva cada evaluación de umbral ------------------------------------------------------------

-- Las evaluaciones anteriores no guardaron los valores aplicados: quedan nulos y su fecha es la de su ejecución
ALTER TABLE evaluacion_umbral
    ADD COLUMN id_umbral_excepcion integer,
    ADD COLUMN operador_aplicado enum_operador_comparacion,
    ADD COLUMN valor_advertencia_aplicado numeric(20,6),
    ADD COLUMN valor_critico_aplicado numeric(20,6),
    ADD COLUMN id_lista_referencia_aplicada integer,
    ADD COLUMN nivel_faltante_aplicado enum_nivel_alerta,
    ADD COLUMN nivel_sobrante_aplicado enum_nivel_alerta,
    ADD COLUMN fecha_evaluacion timestamp(6) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    ADD CONSTRAINT ck_evaluacion_umbral_modalidad CHECK ((((id_lista_referencia_aplicada IS NULL) AND (nivel_faltante_aplicado IS NULL) AND (nivel_sobrante_aplicado IS NULL)) OR ((operador_aplicado IS NULL) AND (valor_advertencia_aplicado IS NULL) AND (valor_critico_aplicado IS NULL))));
UPDATE evaluacion_umbral eu SET fecha_evaluacion = coalesce(e.fecha_fin, e.fecha_inicio)
FROM medicion_metrica m JOIN evaluacion e ON e.id_evaluacion = m.id_evaluacion
WHERE m.id_medicion_metrica = eu.id_medicion_metrica;
ALTER TABLE ONLY evaluacion_umbral
    ADD CONSTRAINT fk_evaluacion_umbral_excepcion FOREIGN KEY (id_umbral_excepcion) REFERENCES umbral_excepcion(id_umbral_excepcion) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY evaluacion_umbral
    ADD CONSTRAINT fk_evaluacion_umbral_lista FOREIGN KEY (id_lista_referencia_aplicada) REFERENCES lista_referencia(id_lista_referencia) ON UPDATE RESTRICT ON DELETE RESTRICT;

COMMIT;
