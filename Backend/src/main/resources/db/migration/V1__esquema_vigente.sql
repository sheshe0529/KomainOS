-- =============================================================================
-- V1 - Esquema vigente de KomainOS (48 tablas, 30 enumerados, 78 claves foraneas)
--
-- Reproduce exactamente el esquema "KomainOS" de la base DBKomainOS, extraido
-- con pg_dump --schema-only el 2026-09-26. Es la fuente de verdad del modelo
-- (Registro de decisiones DEC-02): coincide con el diccionario de datos R2.4,
-- incluidas las restricciones ck_configuracion_sistema_unico,
-- ck_ventana_intervalo y uq_regla_politica_cambio que faltan en
-- Documentos/Docs/DDL_KOMAINOS.sql.
--
-- En DBKomainOS esta migracion NO se ejecuta: Flyway hace baseline en la
-- version 1 porque el esquema ya existia (DEC-05). En una base nueva crea el
-- esquema completo. El esquema lo crea Flyway (spring.flyway.schemas).
--
-- Una migracion aplicada nunca se edita: cualquier cambio va en V2, V3...
-- =============================================================================

CREATE TYPE "KomainOS".enum_dia_semana AS ENUM (
    'LUNES',
    'MARTES',
    'MIERCOLES',
    'JUEVES',
    'VIERNES',
    'SABADO',
    'DOMINGO'
);
CREATE TYPE "KomainOS".enum_estado_autorizacion_detalle AS ENUM (
    'PENDIENTE',
    'APROBADA',
    'RECHAZADA',
    'FECHA_PROPUESTA',
    'VENCIDA'
);
CREATE TYPE "KomainOS".enum_estado_credencial AS ENUM (
    'VIGENTE',
    'REVOCADA'
);
CREATE TYPE "KomainOS".enum_estado_detalle_orden AS ENUM (
    'PENDIENTE',
    'EN_COLA',
    'EN_EJECUCION',
    'FINALIZADO',
    'FALLIDO',
    'NO_INICIADO'
);
CREATE TYPE "KomainOS".enum_estado_evaluacion AS ENUM (
    'EN_CURSO',
    'FINALIZADA',
    'FALLIDA'
);
CREATE TYPE "KomainOS".enum_estado_grupo AS ENUM (
    'PENDIENTE_DE_CONFIGURACION',
    'ACTIVO',
    'INACTIVO'
);
CREATE TYPE "KomainOS".enum_estado_incidencia AS ENUM (
    'ABIERTA',
    'EN_ATENCION',
    'CERRADA'
);
CREATE TYPE "KomainOS".enum_estado_orden AS ENUM (
    'PROGRAMADA',
    'EN_EVALUACION',
    'SIN_IMPLEMENTACION',
    'PENDIENTE_AUTORIZACION',
    'AUTORIZADA',
    'EN_COLA',
    'EN_EJECUCION',
    'PENDIENTE_VALIDACION',
    'INCIDENCIA',
    'REPROGRAMADA',
    'RECHAZADA',
    'AUTORIZACION_VENCIDA',
    'VALIDACION_VENCIDA',
    'CANCELADA',
    'CERRADA'
);
CREATE TYPE "KomainOS".enum_estado_servidor AS ENUM (
    'PENDIENTE_DE_CONFIGURACION',
    'ACTIVO',
    'DADO_DE_BAJA'
);
CREATE TYPE "KomainOS".enum_estado_solicitud_baja AS ENUM (
    'PENDIENTE',
    'APLICADA'
);
CREATE TYPE "KomainOS".enum_estado_tarea_mop AS ENUM (
    'PENDIENTE',
    'EN_EJECUCION',
    'FINALIZADA',
    'FALLIDA',
    'NO_INICIADA'
);
CREATE TYPE "KomainOS".enum_estado_validacion_funcional AS ENUM (
    'PENDIENTE',
    'CONFORME',
    'RECHAZADA',
    'VENCIDA'
);
CREATE TYPE "KomainOS".enum_familia_so AS ENUM (
    'LINUX',
    'WINDOWS'
);
CREATE TYPE "KomainOS".enum_modalidad_planificacion AS ENUM (
    'AUTOMATICA',
    'BAJO_DEMANDA'
);
CREATE TYPE "KomainOS".enum_modo_ejecucion AS ENUM (
    'SECUENCIAL',
    'PARALELO'
);
CREATE TYPE "KomainOS".enum_nivel_afectacion AS ENUM (
    'SIN_AFECTACION',
    'PARCIAL',
    'TOTAL'
);
CREATE TYPE "KomainOS".enum_nivel_alerta AS ENUM (
    'NORMAL',
    'ADVERTENCIA',
    'CRITICO'
);
CREATE TYPE "KomainOS".enum_operador_comparacion AS ENUM (
    'MAYOR',
    'MAYOR_IGUAL',
    'MENOR',
    'MENOR_IGUAL',
    'IGUAL',
    'DISTINTO'
);
CREATE TYPE "KomainOS".enum_origen_incidencia AS ENUM (
    'PRUEBA_PILOTO',
    'FALLO_EJECUCION',
    'TIEMPO_EXCEDIDO',
    'VALIDACION_TECNICA_NO_CONFORME',
    'RECHAZO_FUNCIONAL'
);
CREATE TYPE "KomainOS".enum_origen_orden AS ENUM (
    'PLANIFICACION_AUTOMATICA',
    'SOLICITUD_BAJO_DEMANDA',
    'LANZAMIENTO_INMEDIATO',
    'CONDICION_CRITICA'
);
CREATE TYPE "KomainOS".enum_resultado_ciclo AS ENUM (
    'INCIDENCIA',
    'SALUD_REGULAR',
    'ESTADO_NORMAL',
    'RACHA_ESTABLE'
);
CREATE TYPE "KomainOS".enum_resultado_validacion_tecnica AS ENUM (
    'CONFORME',
    'NO_CONFORME'
);
CREATE TYPE "KomainOS".enum_tipo_alerta AS ENUM (
    'ADVERTENCIA_UMBRAL',
    'CONDICION_CRITICA',
    'SIN_IMPLEMENTACION',
    'SOLICITUD_AUTORIZACION',
    'AUTORIZACION_VENCIDA',
    'SOLICITUD_VALIDACION',
    'VALIDACION_VENCIDA',
    'INCIDENCIA_REGISTRADA',
    'VENTANA_EXCEDIDA',
    'CIERRE_MANTENIMIENTO'
);
CREATE TYPE "KomainOS".enum_tipo_aprobador AS ENUM (
    'RESPONSABLE',
    'ADMINISTRADOR'
);
CREATE TYPE "KomainOS".enum_tipo_autenticacion AS ENUM (
    'PASSWORD',
    'LLAVE_SSH'
);
CREATE TYPE "KomainOS".enum_tipo_dato_parametro AS ENUM (
    'STRING',
    'INTEGER',
    'DECIMAL',
    'BOOLEAN'
);
CREATE TYPE "KomainOS".enum_tipo_destinatario AS ENUM (
    'RESPONSABLE',
    'ADMINISTRADOR',
    'OPERADOR'
);
CREATE TYPE "KomainOS".enum_tipo_evaluacion AS ENUM (
    'REVISION_PERIODICA',
    'PREVIA',
    'POSTERIOR'
);
CREATE TYPE "KomainOS".enum_tipo_rol AS ENUM (
    'ADMINISTRADOR',
    'OPERADOR',
    'RESPONSABLE'
);
CREATE TYPE "KomainOS".enum_tipo_script AS ENUM (
    'ANALISIS',
    'MANTENIMIENTO'
);
CREATE TABLE "KomainOS".alerta (
    id_alerta integer NOT NULL,
    id_servidor integer,
    id_grupo_mantenimiento integer,
    id_orden integer,
    id_incidencia integer,
    tipo "KomainOS".enum_tipo_alerta NOT NULL,
    titulo character varying(255) NOT NULL,
    mensaje character varying(2000) NOT NULL,
    fecha_generacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
CREATE TABLE "KomainOS".alerta_destinatario (
    id_alerta_destinatario integer NOT NULL,
    id_alerta integer NOT NULL,
    id_usuario integer NOT NULL,
    leida boolean DEFAULT false NOT NULL,
    fecha_lectura timestamp(0) without time zone,
    CONSTRAINT ck_alerta_destinatario_lectura CHECK ((((leida = false) AND (fecha_lectura IS NULL)) OR ((leida = true) AND (fecha_lectura IS NOT NULL))))
);
ALTER TABLE "KomainOS".alerta_destinatario ALTER COLUMN id_alerta_destinatario ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".alerta_destinatario_id_alerta_destinatario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
ALTER TABLE "KomainOS".alerta ALTER COLUMN id_alerta ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".alerta_id_alerta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".auditoria (
    id_auditoria integer NOT NULL,
    id_usuario integer,
    id_servidor integer,
    id_grupo_mantenimiento integer,
    id_orden integer,
    operacion character varying(255) NOT NULL,
    entidad character varying(255) NOT NULL,
    id_entidad integer,
    valor_anterior text,
    valor_nuevo text,
    motivo character varying(1000),
    proceso character varying(255),
    fecha_hora timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_auditoria_ejecutor CHECK ((((id_usuario IS NOT NULL) AND ((proceso IS NULL) OR (TRIM(BOTH FROM proceso) = ''::text))) OR ((id_usuario IS NULL) AND (proceso IS NOT NULL) AND (TRIM(BOTH FROM proceso) <> ''::text))))
);
ALTER TABLE "KomainOS".auditoria ALTER COLUMN id_auditoria ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".auditoria_id_auditoria_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".autorizacion_detalle (
    id_autorizacion_detalle integer NOT NULL,
    id_solicitud_autorizacion integer NOT NULL,
    id_usuario_decision integer,
    tipo_aprobador "KomainOS".enum_tipo_aprobador NOT NULL,
    orden_secuencia integer NOT NULL,
    estado "KomainOS".enum_estado_autorizacion_detalle DEFAULT 'PENDIENTE'::"KomainOS".enum_estado_autorizacion_detalle NOT NULL,
    fecha_solicitud timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_decision timestamp(0) without time zone,
    fecha_propuesta timestamp(0) without time zone,
    motivo character varying(1000),
    CONSTRAINT ck_autorizacion_detalle_secuencia CHECK ((orden_secuencia >= 1))
);
ALTER TABLE "KomainOS".autorizacion_detalle ALTER COLUMN id_autorizacion_detalle ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".autorizacion_detalle_id_autorizacion_detalle_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".cierre_orden (
    id_cierre_orden integer NOT NULL,
    id_orden integer NOT NULL,
    id_factor_ciclo integer NOT NULL,
    id_orden_siguiente integer,
    periodicidad_base_dias integer NOT NULL,
    factor_aplicado numeric(10,4) NOT NULL,
    fecha_cierre timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    observaciones character varying(1000),
    CONSTRAINT ck_cierre_orden_valores CHECK (((periodicidad_base_dias > 0) AND (factor_aplicado > (0)::numeric)))
);
ALTER TABLE "KomainOS".cierre_orden ALTER COLUMN id_cierre_orden ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".cierre_orden_id_cierre_orden_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".configuracion_grupo (
    id_configuracion_mantenimiento integer NOT NULL,
    id_grupo_mantenimiento integer NOT NULL,
    modo_ejecucion "KomainOS".enum_modo_ejecucion NOT NULL
);
CREATE TABLE "KomainOS".configuracion_mantenimiento (
    id_configuracion_mantenimiento integer CONSTRAINT configuracion_mantenimiento_id_configuracion_mantenimi_not_null NOT NULL,
    id_cuenta_servicio integer,
    frecuencia_revision_dias integer NOT NULL,
    frecuencia_mantenimiento_dias integer CONSTRAINT configuracion_mantenimiento_frecuencia_mantenimiento_d_not_null NOT NULL,
    modalidad_planificacion "KomainOS".enum_modalidad_planificacion NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_config_mantenimiento_frecuencias CHECK (((frecuencia_revision_dias > 0) AND (frecuencia_mantenimiento_dias > 0)))
);
ALTER TABLE "KomainOS".configuracion_mantenimiento ALTER COLUMN id_configuracion_mantenimiento ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".configuracion_mantenimiento_id_configuracion_mantenimiento_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".configuracion_servidor (
    id_configuracion_mantenimiento integer NOT NULL,
    id_servidor integer NOT NULL
);
CREATE TABLE "KomainOS".configuracion_sistema (
    id_configuracion_sistema integer DEFAULT 1 NOT NULL,
    id_cuenta_servicio_predeterminada integer,
    max_ejecuciones_concurrentes integer NOT NULL,
    max_duracion_tarea_minutos integer NOT NULL,
    max_duracion_mop_minutos integer NOT NULL,
    min_ciclos_racha_estable integer NOT NULL,
    minutos_expiracion_token integer NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_configuracion_sistema_unico CHECK ((id_configuracion_sistema = 1)),
    CONSTRAINT ck_configuracion_sistema_valores CHECK (((max_ejecuciones_concurrentes > 0) AND (max_duracion_tarea_minutos > 0) AND (max_duracion_mop_minutos > 0) AND (min_ciclos_racha_estable > 0) AND (minutos_expiracion_token > 0)))
);
CREATE TABLE "KomainOS".confirmacion_fuera_ventana (
    id_confirmacion_fuera_ventana integer CONSTRAINT confirmacion_fuera_ventana_id_confirmacion_fuera_venta_not_null NOT NULL,
    id_orden integer NOT NULL,
    id_usuario integer NOT NULL,
    fecha_confirmacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    motivo character varying(1000) NOT NULL
);
ALTER TABLE "KomainOS".confirmacion_fuera_ventana ALTER COLUMN id_confirmacion_fuera_ventana ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".confirmacion_fuera_ventana_id_confirmacion_fuera_ventana_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".credencial (
    id_credencial integer NOT NULL,
    nombre character varying(255) NOT NULL,
    usuario_acceso character varying(255) NOT NULL,
    descripcion character varying(500),
    estado "KomainOS".enum_estado_credencial DEFAULT 'VIGENTE'::"KomainOS".enum_estado_credencial NOT NULL,
    fecha_registro timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_revocacion timestamp(0) without time zone,
    CONSTRAINT ck_credencial_revocacion CHECK ((((estado = 'VIGENTE'::"KomainOS".enum_estado_credencial) AND (fecha_revocacion IS NULL)) OR ((estado = 'REVOCADA'::"KomainOS".enum_estado_credencial) AND (fecha_revocacion IS NOT NULL))))
);
CREATE TABLE "KomainOS".credencial_documental (
    id_credencial integer NOT NULL,
    id_servidor integer NOT NULL
);
ALTER TABLE "KomainOS".credencial ALTER COLUMN id_credencial ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".credencial_id_credencial_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".credencial_version (
    id_credencial_version integer NOT NULL,
    id_credencial integer NOT NULL,
    numero_version integer NOT NULL,
    tipo_autenticacion "KomainOS".enum_tipo_autenticacion NOT NULL,
    secreto_cifrado bytea NOT NULL,
    iv_nonce bytea NOT NULL,
    tag_autenticacion bytea NOT NULL,
    algoritmo character varying(100) NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_credencial_version_numero CHECK ((numero_version >= 1)),
    CONSTRAINT credencial_version_iv_nonce_check CHECK ((octet_length(iv_nonce) <= 64)),
    CONSTRAINT credencial_version_tag_autenticacion_check CHECK ((octet_length(tag_autenticacion) <= 64))
);
ALTER TABLE "KomainOS".credencial_version ALTER COLUMN id_credencial_version ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".credencial_version_id_credencial_version_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".cuenta_servicio (
    id_credencial integer NOT NULL
);
CREATE TABLE "KomainOS".entorno (
    id_entorno integer NOT NULL,
    nombre character varying(150) NOT NULL,
    descripcion character varying(500),
    activo boolean DEFAULT true NOT NULL
);
ALTER TABLE "KomainOS".entorno ALTER COLUMN id_entorno ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".entorno_id_entorno_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".evaluacion (
    id_evaluacion integer NOT NULL,
    id_servidor integer NOT NULL,
    id_orden_detalle integer,
    id_script_version integer NOT NULL,
    id_credencial_version integer NOT NULL,
    tipo "KomainOS".enum_tipo_evaluacion NOT NULL,
    estado "KomainOS".enum_estado_evaluacion NOT NULL,
    error text,
    fecha_inicio timestamp(0) without time zone NOT NULL,
    fecha_fin timestamp(0) without time zone,
    CONSTRAINT ck_evaluacion_detalle_tipo CHECK ((((tipo = 'REVISION_PERIODICA'::"KomainOS".enum_tipo_evaluacion) AND (id_orden_detalle IS NULL)) OR ((tipo = ANY (ARRAY['PREVIA'::"KomainOS".enum_tipo_evaluacion, 'POSTERIOR'::"KomainOS".enum_tipo_evaluacion])) AND (id_orden_detalle IS NOT NULL)))),
    CONSTRAINT ck_evaluacion_fechas CHECK (((fecha_fin IS NULL) OR (fecha_fin >= fecha_inicio)))
);
ALTER TABLE "KomainOS".evaluacion ALTER COLUMN id_evaluacion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".evaluacion_id_evaluacion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".evaluacion_umbral (
    id_evaluacion_umbral integer NOT NULL,
    id_medicion_metrica integer NOT NULL,
    id_umbral integer NOT NULL,
    nivel "KomainOS".enum_nivel_alerta NOT NULL
);
ALTER TABLE "KomainOS".evaluacion_umbral ALTER COLUMN id_evaluacion_umbral ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".evaluacion_umbral_id_evaluacion_umbral_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".factor_ciclo (
    id_factor_ciclo integer NOT NULL,
    resultado "KomainOS".enum_resultado_ciclo NOT NULL,
    factor numeric(10,4) NOT NULL,
    CONSTRAINT ck_factor_ciclo_factor CHECK ((factor > (0)::numeric))
);
ALTER TABLE "KomainOS".factor_ciclo ALTER COLUMN id_factor_ciclo ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".factor_ciclo_id_factor_ciclo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".grupo_mantenimiento (
    id_grupo_mantenimiento integer NOT NULL,
    nombre character varying(255) NOT NULL,
    descripcion character varying(500),
    estado "KomainOS".enum_estado_grupo DEFAULT 'PENDIENTE_DE_CONFIGURACION'::"KomainOS".enum_estado_grupo NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".grupo_mantenimiento ALTER COLUMN id_grupo_mantenimiento ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".grupo_mantenimiento_id_grupo_mantenimiento_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".grupo_servidor (
    id_grupo_servidor integer NOT NULL,
    id_grupo_mantenimiento integer NOT NULL,
    id_servidor integer NOT NULL,
    fecha_incorporacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".grupo_servidor ALTER COLUMN id_grupo_servidor ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".grupo_servidor_id_grupo_servidor_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".historial_estado_orden (
    id_historial_estado_orden integer NOT NULL,
    id_orden integer NOT NULL,
    id_usuario integer,
    estado_anterior "KomainOS".enum_estado_orden,
    estado_nuevo "KomainOS".enum_estado_orden NOT NULL,
    motivo character varying(1000),
    fecha_hora timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".historial_estado_orden ALTER COLUMN id_historial_estado_orden ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".historial_estado_orden_id_historial_estado_orden_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".incidencia (
    id_incidencia integer NOT NULL,
    id_orden_detalle integer NOT NULL,
    id_usuario_atencion integer,
    origen "KomainOS".enum_origen_incidencia NOT NULL,
    estado "KomainOS".enum_estado_incidencia DEFAULT 'ABIERTA'::"KomainOS".enum_estado_incidencia NOT NULL,
    error text,
    diagnostico text,
    acciones_realizadas text,
    resultado character varying(1000),
    fecha_apertura timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_cierre timestamp(0) without time zone,
    CONSTRAINT ck_incidencia_cierre CHECK ((((estado <> 'CERRADA'::"KomainOS".enum_estado_incidencia) AND (fecha_cierre IS NULL)) OR ((estado = 'CERRADA'::"KomainOS".enum_estado_incidencia) AND (fecha_cierre IS NOT NULL))))
);
ALTER TABLE "KomainOS".incidencia ALTER COLUMN id_incidencia ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".incidencia_id_incidencia_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".medicion_metrica (
    id_medicion_metrica integer NOT NULL,
    id_evaluacion integer NOT NULL,
    id_metrica integer NOT NULL,
    valor numeric(20,6) NOT NULL
);
ALTER TABLE "KomainOS".medicion_metrica ALTER COLUMN id_medicion_metrica ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".medicion_metrica_id_medicion_metrica_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".metrica (
    id_metrica integer NOT NULL,
    codigo character varying(100) NOT NULL,
    nombre character varying(255) NOT NULL,
    unidad character varying(100),
    activo boolean DEFAULT true NOT NULL
);
ALTER TABLE "KomainOS".metrica ALTER COLUMN id_metrica ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".metrica_id_metrica_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".mop (
    id_mop integer NOT NULL,
    id_orden_detalle integer NOT NULL,
    fecha_generacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".mop ALTER COLUMN id_mop ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".mop_id_mop_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".mop_tarea (
    id_mop_tarea integer NOT NULL,
    id_mop integer NOT NULL,
    id_tarea integer NOT NULL,
    id_script_version integer,
    id_credencial_version integer,
    orden_ejecucion integer NOT NULL,
    prioridad_aplicada integer NOT NULL,
    duracion_estimada_minutos integer NOT NULL,
    nivel_afectacion_aplicado "KomainOS".enum_nivel_afectacion NOT NULL,
    estado "KomainOS".enum_estado_tarea_mop DEFAULT 'PENDIENTE'::"KomainOS".enum_estado_tarea_mop NOT NULL,
    codigo_retorno integer,
    salida text,
    error text,
    fecha_inicio timestamp(0) without time zone,
    fecha_fin timestamp(0) without time zone,
    CONSTRAINT ck_mop_tarea_valores CHECK (((orden_ejecucion >= 1) AND (prioridad_aplicada >= 0) AND (duracion_estimada_minutos > 0) AND ((fecha_inicio IS NULL) OR (fecha_fin IS NULL) OR (fecha_fin >= fecha_inicio))))
);
ALTER TABLE "KomainOS".mop_tarea ALTER COLUMN id_mop_tarea ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".mop_tarea_id_mop_tarea_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".nivel_criticidad (
    id_nivel_criticidad integer NOT NULL,
    nombre character varying(150) NOT NULL,
    prioridad integer NOT NULL,
    frecuencia_revision_dias integer NOT NULL,
    frecuencia_mantenimiento_dias integer NOT NULL,
    plazo_autorizacion_horas integer NOT NULL,
    plazo_validacion_horas integer NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_nivel_criticidad_valores CHECK (((prioridad >= 0) AND (frecuencia_revision_dias > 0) AND (frecuencia_mantenimiento_dias > 0) AND (plazo_autorizacion_horas > 0) AND (plazo_validacion_horas > 0)))
);
ALTER TABLE "KomainOS".nivel_criticidad ALTER COLUMN id_nivel_criticidad ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".nivel_criticidad_id_nivel_criticidad_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".notificacion_regla (
    id_notificacion_regla integer NOT NULL,
    id_regla_politica_cambio integer NOT NULL,
    destinatario "KomainOS".enum_tipo_destinatario NOT NULL
);
ALTER TABLE "KomainOS".notificacion_regla ALTER COLUMN id_notificacion_regla ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".notificacion_regla_id_notificacion_regla_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".orden (
    id_orden integer NOT NULL,
    codigo character varying(100) NOT NULL,
    id_servidor integer,
    id_grupo_mantenimiento integer,
    id_nivel_criticidad integer NOT NULL,
    id_cuenta_servicio integer,
    id_usuario_solicitante integer,
    origen "KomainOS".enum_origen_orden NOT NULL,
    estado "KomainOS".enum_estado_orden DEFAULT 'PROGRAMADA'::"KomainOS".enum_estado_orden NOT NULL,
    modo_ejecucion_aplicado "KomainOS".enum_modo_ejecucion,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_orden_modo_grupo CHECK (((id_grupo_mantenimiento IS NOT NULL) OR (modo_ejecucion_aplicado IS NULL))),
    CONSTRAINT ck_orden_objetivo_xor CHECK ((((id_servidor IS NOT NULL) AND (id_grupo_mantenimiento IS NULL)) OR ((id_servidor IS NULL) AND (id_grupo_mantenimiento IS NOT NULL))))
);
CREATE TABLE "KomainOS".orden_detalle (
    id_orden_detalle integer NOT NULL,
    id_orden integer NOT NULL,
    id_servidor integer NOT NULL,
    posicion_ejecucion integer NOT NULL,
    es_servidor_piloto boolean DEFAULT false NOT NULL,
    estado "KomainOS".enum_estado_detalle_orden DEFAULT 'PENDIENTE'::"KomainOS".enum_estado_detalle_orden NOT NULL,
    fecha_prevista_inicio timestamp(0) without time zone,
    fecha_prevista_fin timestamp(0) without time zone,
    fecha_real_inicio timestamp(0) without time zone,
    fecha_real_fin timestamp(0) without time zone,
    CONSTRAINT ck_orden_detalle_fechas CHECK ((((fecha_prevista_inicio IS NULL) OR (fecha_prevista_fin IS NULL) OR (fecha_prevista_fin >= fecha_prevista_inicio)) AND ((fecha_real_inicio IS NULL) OR (fecha_real_fin IS NULL) OR (fecha_real_fin >= fecha_real_inicio)))),
    CONSTRAINT ck_orden_detalle_posicion CHECK ((posicion_ejecucion >= 1))
);
ALTER TABLE "KomainOS".orden_detalle ALTER COLUMN id_orden_detalle ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".orden_detalle_id_orden_detalle_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
ALTER TABLE "KomainOS".orden ALTER COLUMN id_orden ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".orden_id_orden_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".programacion_orden (
    id_programacion_orden integer NOT NULL,
    id_orden integer NOT NULL,
    id_usuario_registro integer,
    numero_version integer NOT NULL,
    fecha_objetivo timestamp(0) without time zone NOT NULL,
    fecha_inicio_programada timestamp(0) without time zone NOT NULL,
    fecha_fin_programada timestamp(0) without time zone NOT NULL,
    fecha_evaluacion_programada timestamp(0) without time zone,
    inicio_ventana_aplicada timestamp(0) without time zone NOT NULL,
    fin_ventana_aplicada timestamp(0) without time zone NOT NULL,
    motivo character varying(1000),
    fecha_registro timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_programacion_orden_fechas CHECK (((numero_version >= 1) AND (fecha_fin_programada >= fecha_inicio_programada) AND (fin_ventana_aplicada >= inicio_ventana_aplicada)))
);
ALTER TABLE "KomainOS".programacion_orden ALTER COLUMN id_programacion_orden ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".programacion_orden_id_programacion_orden_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".regla_politica_cambio (
    id_regla_politica_cambio integer NOT NULL,
    id_entorno integer NOT NULL,
    nivel_afectacion "KomainOS".enum_nivel_afectacion NOT NULL,
    requiere_aprobacion_responsable boolean DEFAULT false NOT NULL,
    requiere_aprobacion_administrador boolean DEFAULT false CONSTRAINT regla_politica_cambio_requiere_aprobacion_administrado_not_null NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".regla_politica_cambio ALTER COLUMN id_regla_politica_cambio ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".regla_politica_cambio_id_regla_politica_cambio_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".script (
    id_script integer NOT NULL,
    nombre character varying(255) NOT NULL,
    tipo "KomainOS".enum_tipo_script NOT NULL,
    descripcion character varying(500),
    activo boolean DEFAULT true NOT NULL
);
ALTER TABLE "KomainOS".script ALTER COLUMN id_script ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".script_id_script_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".script_implementacion (
    id_script_implementacion integer NOT NULL,
    id_script integer NOT NULL,
    id_version_sistema_operativo integer NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".script_implementacion ALTER COLUMN id_script_implementacion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".script_implementacion_id_script_implementacion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".script_parametro (
    id_script_parametro integer NOT NULL,
    id_script_version integer NOT NULL,
    nombre character varying(255) NOT NULL,
    tipo_dato "KomainOS".enum_tipo_dato_parametro NOT NULL,
    obligatorio boolean DEFAULT false NOT NULL,
    valor_por_defecto character varying(1000),
    orden integer NOT NULL,
    CONSTRAINT ck_script_parametro_orden CHECK ((orden >= 1))
);
ALTER TABLE "KomainOS".script_parametro ALTER COLUMN id_script_parametro ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".script_parametro_id_script_parametro_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".script_version (
    id_script_version integer NOT NULL,
    id_script_implementacion integer NOT NULL,
    numero_version integer NOT NULL,
    contenido text NOT NULL,
    notas_cambio character varying(1000),
    habilitada boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    CONSTRAINT ck_script_version_numero CHECK ((numero_version >= 1))
);
ALTER TABLE "KomainOS".script_version ALTER COLUMN id_script_version ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".script_version_id_script_version_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".servidor (
    id_servidor integer NOT NULL,
    id_version_sistema_operativo integer NOT NULL,
    id_entorno integer NOT NULL,
    id_nivel_criticidad integer NOT NULL,
    id_usuario_responsable integer NOT NULL,
    hostname character varying(255) NOT NULL,
    direccion_ip character varying(45) NOT NULL,
    datacenter character varying(255),
    servidor_fisico character varying(255),
    vlan character varying(100),
    cluster character varying(255),
    dns character varying(255),
    plataforma character varying(255),
    descripcion character varying(500),
    estado "KomainOS".enum_estado_servidor DEFAULT 'PENDIENTE_DE_CONFIGURACION'::"KomainOS".enum_estado_servidor NOT NULL,
    fecha_alta timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".servidor ALTER COLUMN id_servidor ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".servidor_id_servidor_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".sistema_operativo (
    id_sistema_operativo integer NOT NULL,
    nombre character varying(150) NOT NULL,
    familia "KomainOS".enum_familia_so NOT NULL,
    activo boolean DEFAULT true NOT NULL
);
ALTER TABLE "KomainOS".sistema_operativo ALTER COLUMN id_sistema_operativo ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".sistema_operativo_id_sistema_operativo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".solicitud_autorizacion (
    id_solicitud_autorizacion integer NOT NULL,
    id_orden integer NOT NULL,
    id_regla_politica_cambio integer NOT NULL,
    numero_ronda integer NOT NULL,
    fecha_limite timestamp(0) without time zone NOT NULL,
    CONSTRAINT ck_solicitud_autorizacion_ronda CHECK ((numero_ronda >= 1))
);
ALTER TABLE "KomainOS".solicitud_autorizacion ALTER COLUMN id_solicitud_autorizacion ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".solicitud_autorizacion_id_solicitud_autorizacion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".solicitud_baja (
    id_solicitud_baja integer NOT NULL,
    id_servidor integer NOT NULL,
    id_usuario_solicitante integer NOT NULL,
    estado "KomainOS".enum_estado_solicitud_baja DEFAULT 'PENDIENTE'::"KomainOS".enum_estado_solicitud_baja NOT NULL,
    motivo character varying(500) NOT NULL,
    fecha_solicitud timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_aplicacion timestamp(0) without time zone,
    CONSTRAINT ck_solicitud_baja_fecha CHECK ((((estado = 'PENDIENTE'::"KomainOS".enum_estado_solicitud_baja) AND (fecha_aplicacion IS NULL)) OR ((estado = 'APLICADA'::"KomainOS".enum_estado_solicitud_baja) AND (fecha_aplicacion IS NOT NULL))))
);
ALTER TABLE "KomainOS".solicitud_baja ALTER COLUMN id_solicitud_baja ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".solicitud_baja_id_solicitud_baja_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".tarea (
    id_tarea integer NOT NULL,
    id_script integer NOT NULL,
    nombre character varying(255) NOT NULL,
    descripcion character varying(500),
    nivel_afectacion "KomainOS".enum_nivel_afectacion NOT NULL,
    prioridad integer NOT NULL,
    duracion_estimada_minutos integer NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_tarea_valores CHECK (((prioridad >= 0) AND (duracion_estimada_minutos > 0)))
);
ALTER TABLE "KomainOS".tarea ALTER COLUMN id_tarea ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".tarea_id_tarea_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".umbral (
    id_umbral integer NOT NULL,
    id_metrica integer NOT NULL,
    nombre character varying(255) NOT NULL,
    operador "KomainOS".enum_operador_comparacion NOT NULL,
    valor_advertencia numeric(20,6) NOT NULL,
    valor_critico numeric(20,6) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_umbral_coherencia CHECK ((((operador = ANY (ARRAY['MAYOR'::"KomainOS".enum_operador_comparacion, 'MAYOR_IGUAL'::"KomainOS".enum_operador_comparacion])) AND (valor_critico >= valor_advertencia)) OR ((operador = ANY (ARRAY['MENOR'::"KomainOS".enum_operador_comparacion, 'MENOR_IGUAL'::"KomainOS".enum_operador_comparacion])) AND (valor_critico <= valor_advertencia)) OR (operador = ANY (ARRAY['IGUAL'::"KomainOS".enum_operador_comparacion, 'DISTINTO'::"KomainOS".enum_operador_comparacion]))))
);
ALTER TABLE "KomainOS".umbral ALTER COLUMN id_umbral ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".umbral_id_umbral_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".umbral_tarea (
    id_umbral_tarea integer NOT NULL,
    id_umbral integer NOT NULL,
    id_tarea integer NOT NULL
);
ALTER TABLE "KomainOS".umbral_tarea ALTER COLUMN id_umbral_tarea ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".umbral_tarea_id_umbral_tarea_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".usuario (
    id_usuario integer NOT NULL,
    codigo character varying(100) NOT NULL,
    nombre_completo character varying(255) NOT NULL,
    hash_contrasena character varying(255) NOT NULL,
    rol "KomainOS".enum_tipo_rol NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_creacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL,
    fecha_actualizacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".usuario ALTER COLUMN id_usuario ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".usuario_id_usuario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".validacion_funcional (
    id_validacion_funcional integer NOT NULL,
    id_orden integer NOT NULL,
    id_usuario_responsable integer NOT NULL,
    estado "KomainOS".enum_estado_validacion_funcional DEFAULT 'PENDIENTE'::"KomainOS".enum_estado_validacion_funcional NOT NULL,
    fecha_solicitud timestamp(0) without time zone NOT NULL,
    fecha_limite timestamp(0) without time zone NOT NULL,
    fecha_respuesta timestamp(0) without time zone,
    observaciones text,
    CONSTRAINT ck_validacion_funcional_fechas CHECK (((fecha_limite >= fecha_solicitud) AND ((fecha_respuesta IS NULL) OR (fecha_respuesta >= fecha_solicitud))))
);
ALTER TABLE "KomainOS".validacion_funcional ALTER COLUMN id_validacion_funcional ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".validacion_funcional_id_validacion_funcional_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".validacion_tecnica (
    id_validacion_tecnica integer NOT NULL,
    id_orden_detalle integer NOT NULL,
    resultado "KomainOS".enum_resultado_validacion_tecnica NOT NULL,
    observaciones text,
    fecha_validacion timestamp(0) without time zone DEFAULT (statement_timestamp() AT TIME ZONE 'UTC'::text) NOT NULL
);
ALTER TABLE "KomainOS".validacion_tecnica ALTER COLUMN id_validacion_tecnica ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".validacion_tecnica_id_validacion_tecnica_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".ventana_mantenimiento (
    id_ventana_mantenimiento integer NOT NULL,
    id_servidor integer NOT NULL,
    dia_inicio "KomainOS".enum_dia_semana NOT NULL,
    hora_inicio time without time zone NOT NULL,
    dia_fin "KomainOS".enum_dia_semana NOT NULL,
    hora_fin time without time zone NOT NULL,
    CONSTRAINT ck_ventana_intervalo CHECK (((dia_inicio <> dia_fin) OR (hora_fin > hora_inicio)))
);
ALTER TABLE "KomainOS".ventana_mantenimiento ALTER COLUMN id_ventana_mantenimiento ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".ventana_mantenimiento_id_ventana_mantenimiento_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
CREATE TABLE "KomainOS".version_sistema_operativo (
    id_version_sistema_operativo integer NOT NULL,
    id_sistema_operativo integer NOT NULL,
    version character varying(150) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);
ALTER TABLE "KomainOS".version_sistema_operativo ALTER COLUMN id_version_sistema_operativo ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME "KomainOS".version_sistema_operativo_id_version_sistema_operativo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);
ALTER TABLE ONLY "KomainOS".alerta_destinatario
    ADD CONSTRAINT alerta_destinatario_pkey PRIMARY KEY (id_alerta_destinatario);
ALTER TABLE ONLY "KomainOS".alerta
    ADD CONSTRAINT alerta_pkey PRIMARY KEY (id_alerta);
ALTER TABLE ONLY "KomainOS".auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id_auditoria);
ALTER TABLE ONLY "KomainOS".autorizacion_detalle
    ADD CONSTRAINT autorizacion_detalle_pkey PRIMARY KEY (id_autorizacion_detalle);
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT cierre_orden_pkey PRIMARY KEY (id_cierre_orden);
ALTER TABLE ONLY "KomainOS".configuracion_grupo
    ADD CONSTRAINT configuracion_grupo_pkey PRIMARY KEY (id_configuracion_mantenimiento);
ALTER TABLE ONLY "KomainOS".configuracion_mantenimiento
    ADD CONSTRAINT configuracion_mantenimiento_pkey PRIMARY KEY (id_configuracion_mantenimiento);
ALTER TABLE ONLY "KomainOS".configuracion_servidor
    ADD CONSTRAINT configuracion_servidor_pkey PRIMARY KEY (id_configuracion_mantenimiento);
ALTER TABLE ONLY "KomainOS".configuracion_sistema
    ADD CONSTRAINT configuracion_sistema_pkey PRIMARY KEY (id_configuracion_sistema);
ALTER TABLE ONLY "KomainOS".confirmacion_fuera_ventana
    ADD CONSTRAINT confirmacion_fuera_ventana_pkey PRIMARY KEY (id_confirmacion_fuera_ventana);
ALTER TABLE ONLY "KomainOS".credencial_documental
    ADD CONSTRAINT credencial_documental_pkey PRIMARY KEY (id_credencial);
ALTER TABLE ONLY "KomainOS".credencial
    ADD CONSTRAINT credencial_pkey PRIMARY KEY (id_credencial);
ALTER TABLE ONLY "KomainOS".credencial_version
    ADD CONSTRAINT credencial_version_pkey PRIMARY KEY (id_credencial_version);
ALTER TABLE ONLY "KomainOS".cuenta_servicio
    ADD CONSTRAINT cuenta_servicio_pkey PRIMARY KEY (id_credencial);
ALTER TABLE ONLY "KomainOS".entorno
    ADD CONSTRAINT entorno_pkey PRIMARY KEY (id_entorno);
ALTER TABLE ONLY "KomainOS".evaluacion
    ADD CONSTRAINT evaluacion_pkey PRIMARY KEY (id_evaluacion);
ALTER TABLE ONLY "KomainOS".evaluacion_umbral
    ADD CONSTRAINT evaluacion_umbral_pkey PRIMARY KEY (id_evaluacion_umbral);
ALTER TABLE ONLY "KomainOS".factor_ciclo
    ADD CONSTRAINT factor_ciclo_pkey PRIMARY KEY (id_factor_ciclo);
ALTER TABLE ONLY "KomainOS".grupo_mantenimiento
    ADD CONSTRAINT grupo_mantenimiento_pkey PRIMARY KEY (id_grupo_mantenimiento);
ALTER TABLE ONLY "KomainOS".grupo_servidor
    ADD CONSTRAINT grupo_servidor_pkey PRIMARY KEY (id_grupo_servidor);
ALTER TABLE ONLY "KomainOS".historial_estado_orden
    ADD CONSTRAINT historial_estado_orden_pkey PRIMARY KEY (id_historial_estado_orden);
ALTER TABLE ONLY "KomainOS".incidencia
    ADD CONSTRAINT incidencia_pkey PRIMARY KEY (id_incidencia);
ALTER TABLE ONLY "KomainOS".medicion_metrica
    ADD CONSTRAINT medicion_metrica_pkey PRIMARY KEY (id_medicion_metrica);
ALTER TABLE ONLY "KomainOS".metrica
    ADD CONSTRAINT metrica_pkey PRIMARY KEY (id_metrica);
ALTER TABLE ONLY "KomainOS".mop
    ADD CONSTRAINT mop_pkey PRIMARY KEY (id_mop);
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT mop_tarea_pkey PRIMARY KEY (id_mop_tarea);
ALTER TABLE ONLY "KomainOS".nivel_criticidad
    ADD CONSTRAINT nivel_criticidad_pkey PRIMARY KEY (id_nivel_criticidad);
ALTER TABLE ONLY "KomainOS".notificacion_regla
    ADD CONSTRAINT notificacion_regla_pkey PRIMARY KEY (id_notificacion_regla);
ALTER TABLE ONLY "KomainOS".orden_detalle
    ADD CONSTRAINT orden_detalle_pkey PRIMARY KEY (id_orden_detalle);
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT orden_pkey PRIMARY KEY (id_orden);
ALTER TABLE ONLY "KomainOS".programacion_orden
    ADD CONSTRAINT programacion_orden_pkey PRIMARY KEY (id_programacion_orden);
ALTER TABLE ONLY "KomainOS".regla_politica_cambio
    ADD CONSTRAINT regla_politica_cambio_pkey PRIMARY KEY (id_regla_politica_cambio);
ALTER TABLE ONLY "KomainOS".script_implementacion
    ADD CONSTRAINT script_implementacion_pkey PRIMARY KEY (id_script_implementacion);
ALTER TABLE ONLY "KomainOS".script_parametro
    ADD CONSTRAINT script_parametro_pkey PRIMARY KEY (id_script_parametro);
ALTER TABLE ONLY "KomainOS".script
    ADD CONSTRAINT script_pkey PRIMARY KEY (id_script);
ALTER TABLE ONLY "KomainOS".script_version
    ADD CONSTRAINT script_version_pkey PRIMARY KEY (id_script_version);
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT servidor_pkey PRIMARY KEY (id_servidor);
ALTER TABLE ONLY "KomainOS".sistema_operativo
    ADD CONSTRAINT sistema_operativo_pkey PRIMARY KEY (id_sistema_operativo);
ALTER TABLE ONLY "KomainOS".solicitud_autorizacion
    ADD CONSTRAINT solicitud_autorizacion_pkey PRIMARY KEY (id_solicitud_autorizacion);
ALTER TABLE ONLY "KomainOS".solicitud_baja
    ADD CONSTRAINT solicitud_baja_pkey PRIMARY KEY (id_solicitud_baja);
ALTER TABLE ONLY "KomainOS".tarea
    ADD CONSTRAINT tarea_pkey PRIMARY KEY (id_tarea);
ALTER TABLE ONLY "KomainOS".umbral
    ADD CONSTRAINT umbral_pkey PRIMARY KEY (id_umbral);
ALTER TABLE ONLY "KomainOS".umbral_tarea
    ADD CONSTRAINT umbral_tarea_pkey PRIMARY KEY (id_umbral_tarea);
ALTER TABLE ONLY "KomainOS".alerta_destinatario
    ADD CONSTRAINT uq_alerta_destinatario UNIQUE (id_alerta, id_usuario);
ALTER TABLE ONLY "KomainOS".autorizacion_detalle
    ADD CONSTRAINT uq_autorizacion_detalle_aprobador UNIQUE (id_solicitud_autorizacion, tipo_aprobador);
ALTER TABLE ONLY "KomainOS".autorizacion_detalle
    ADD CONSTRAINT uq_autorizacion_detalle_secuencia UNIQUE (id_solicitud_autorizacion, orden_secuencia);
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT uq_cierre_orden UNIQUE (id_orden);
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT uq_cierre_orden_siguiente UNIQUE (id_orden_siguiente);
ALTER TABLE ONLY "KomainOS".configuracion_grupo
    ADD CONSTRAINT uq_configuracion_grupo UNIQUE (id_grupo_mantenimiento);
ALTER TABLE ONLY "KomainOS".configuracion_servidor
    ADD CONSTRAINT uq_configuracion_servidor UNIQUE (id_servidor);
ALTER TABLE ONLY "KomainOS".confirmacion_fuera_ventana
    ADD CONSTRAINT uq_confirmacion_fuera_ventana_orden UNIQUE (id_orden);
ALTER TABLE ONLY "KomainOS".credencial_version
    ADD CONSTRAINT uq_credencial_version UNIQUE (id_credencial, numero_version);
ALTER TABLE ONLY "KomainOS".entorno
    ADD CONSTRAINT uq_entorno_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".evaluacion_umbral
    ADD CONSTRAINT uq_evaluacion_umbral UNIQUE (id_medicion_metrica, id_umbral);
ALTER TABLE ONLY "KomainOS".factor_ciclo
    ADD CONSTRAINT uq_factor_ciclo_resultado UNIQUE (resultado);
ALTER TABLE ONLY "KomainOS".grupo_mantenimiento
    ADD CONSTRAINT uq_grupo_mantenimiento_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".grupo_servidor
    ADD CONSTRAINT uq_grupo_servidor UNIQUE (id_grupo_mantenimiento, id_servidor);
ALTER TABLE ONLY "KomainOS".medicion_metrica
    ADD CONSTRAINT uq_medicion_evaluacion_metrica UNIQUE (id_evaluacion, id_metrica);
ALTER TABLE ONLY "KomainOS".metrica
    ADD CONSTRAINT uq_metrica_codigo UNIQUE (codigo);
ALTER TABLE ONLY "KomainOS".mop
    ADD CONSTRAINT uq_mop_orden_detalle UNIQUE (id_orden_detalle);
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT uq_mop_tarea_orden UNIQUE (id_mop, orden_ejecucion);
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT uq_mop_tarea_tarea UNIQUE (id_mop, id_tarea);
ALTER TABLE ONLY "KomainOS".nivel_criticidad
    ADD CONSTRAINT uq_nivel_criticidad_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".nivel_criticidad
    ADD CONSTRAINT uq_nivel_criticidad_prioridad UNIQUE (prioridad);
ALTER TABLE ONLY "KomainOS".notificacion_regla
    ADD CONSTRAINT uq_notificacion_regla UNIQUE (id_regla_politica_cambio, destinatario);
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT uq_orden_codigo UNIQUE (codigo);
ALTER TABLE ONLY "KomainOS".orden_detalle
    ADD CONSTRAINT uq_orden_detalle_posicion UNIQUE (id_orden, posicion_ejecucion);
ALTER TABLE ONLY "KomainOS".orden_detalle
    ADD CONSTRAINT uq_orden_detalle_servidor UNIQUE (id_orden, id_servidor);
ALTER TABLE ONLY "KomainOS".programacion_orden
    ADD CONSTRAINT uq_programacion_orden_version UNIQUE (id_orden, numero_version);
ALTER TABLE ONLY "KomainOS".regla_politica_cambio
    ADD CONSTRAINT uq_regla_politica_cambio UNIQUE (id_entorno, nivel_afectacion);
ALTER TABLE ONLY "KomainOS".script_implementacion
    ADD CONSTRAINT uq_script_implementacion UNIQUE (id_script, id_version_sistema_operativo);
ALTER TABLE ONLY "KomainOS".script
    ADD CONSTRAINT uq_script_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".script_parametro
    ADD CONSTRAINT uq_script_parametro_nombre UNIQUE (id_script_version, nombre);
ALTER TABLE ONLY "KomainOS".script_parametro
    ADD CONSTRAINT uq_script_parametro_orden UNIQUE (id_script_version, orden);
ALTER TABLE ONLY "KomainOS".script_version
    ADD CONSTRAINT uq_script_version UNIQUE (id_script_implementacion, numero_version);
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT uq_servidor_direccion_ip UNIQUE (direccion_ip);
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT uq_servidor_hostname UNIQUE (hostname);
ALTER TABLE ONLY "KomainOS".sistema_operativo
    ADD CONSTRAINT uq_sistema_operativo_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".solicitud_autorizacion
    ADD CONSTRAINT uq_solicitud_autorizacion_ronda UNIQUE (id_orden, numero_ronda);
ALTER TABLE ONLY "KomainOS".tarea
    ADD CONSTRAINT uq_tarea_nombre UNIQUE (nombre);
ALTER TABLE ONLY "KomainOS".umbral_tarea
    ADD CONSTRAINT uq_umbral_tarea UNIQUE (id_umbral, id_tarea);
ALTER TABLE ONLY "KomainOS".usuario
    ADD CONSTRAINT uq_usuario_codigo UNIQUE (codigo);
ALTER TABLE ONLY "KomainOS".validacion_funcional
    ADD CONSTRAINT uq_validacion_funcional_orden UNIQUE (id_orden);
ALTER TABLE ONLY "KomainOS".validacion_tecnica
    ADD CONSTRAINT uq_validacion_tecnica_detalle UNIQUE (id_orden_detalle);
ALTER TABLE ONLY "KomainOS".version_sistema_operativo
    ADD CONSTRAINT uq_version_so UNIQUE (id_sistema_operativo, version);
ALTER TABLE ONLY "KomainOS".usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario);
ALTER TABLE ONLY "KomainOS".validacion_funcional
    ADD CONSTRAINT validacion_funcional_pkey PRIMARY KEY (id_validacion_funcional);
ALTER TABLE ONLY "KomainOS".validacion_tecnica
    ADD CONSTRAINT validacion_tecnica_pkey PRIMARY KEY (id_validacion_tecnica);
ALTER TABLE ONLY "KomainOS".ventana_mantenimiento
    ADD CONSTRAINT ventana_mantenimiento_pkey PRIMARY KEY (id_ventana_mantenimiento);
ALTER TABLE ONLY "KomainOS".version_sistema_operativo
    ADD CONSTRAINT version_sistema_operativo_pkey PRIMARY KEY (id_version_sistema_operativo);
ALTER TABLE ONLY "KomainOS".alerta_destinatario
    ADD CONSTRAINT fk_alerta_destinatario_alerta FOREIGN KEY (id_alerta) REFERENCES "KomainOS".alerta(id_alerta) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".alerta_destinatario
    ADD CONSTRAINT fk_alerta_destinatario_usuario FOREIGN KEY (id_usuario) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".alerta
    ADD CONSTRAINT fk_alerta_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES "KomainOS".grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".alerta
    ADD CONSTRAINT fk_alerta_incidencia FOREIGN KEY (id_incidencia) REFERENCES "KomainOS".incidencia(id_incidencia) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".alerta
    ADD CONSTRAINT fk_alerta_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".alerta
    ADD CONSTRAINT fk_alerta_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".auditoria
    ADD CONSTRAINT fk_auditoria_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES "KomainOS".grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".auditoria
    ADD CONSTRAINT fk_auditoria_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".auditoria
    ADD CONSTRAINT fk_auditoria_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".auditoria
    ADD CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".autorizacion_detalle
    ADD CONSTRAINT fk_autorizacion_detalle_solicitud FOREIGN KEY (id_solicitud_autorizacion) REFERENCES "KomainOS".solicitud_autorizacion(id_solicitud_autorizacion) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".autorizacion_detalle
    ADD CONSTRAINT fk_autorizacion_detalle_usuario FOREIGN KEY (id_usuario_decision) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT fk_cierre_factor FOREIGN KEY (id_factor_ciclo) REFERENCES "KomainOS".factor_ciclo(id_factor_ciclo) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT fk_cierre_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".cierre_orden
    ADD CONSTRAINT fk_cierre_orden_siguiente FOREIGN KEY (id_orden_siguiente) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".configuracion_grupo
    ADD CONSTRAINT fk_config_grupo_base FOREIGN KEY (id_configuracion_mantenimiento) REFERENCES "KomainOS".configuracion_mantenimiento(id_configuracion_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".configuracion_grupo
    ADD CONSTRAINT fk_config_grupo_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES "KomainOS".grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".configuracion_mantenimiento
    ADD CONSTRAINT fk_config_mantenimiento_cuenta FOREIGN KEY (id_cuenta_servicio) REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".configuracion_servidor
    ADD CONSTRAINT fk_config_servidor_base FOREIGN KEY (id_configuracion_mantenimiento) REFERENCES "KomainOS".configuracion_mantenimiento(id_configuracion_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".configuracion_servidor
    ADD CONSTRAINT fk_config_servidor_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".configuracion_sistema
    ADD CONSTRAINT fk_config_sistema_cuenta_predeterminada FOREIGN KEY (id_cuenta_servicio_predeterminada) REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".confirmacion_fuera_ventana
    ADD CONSTRAINT fk_confirmacion_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".confirmacion_fuera_ventana
    ADD CONSTRAINT fk_confirmacion_usuario FOREIGN KEY (id_usuario) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".credencial_documental
    ADD CONSTRAINT fk_credencial_documental_credencial FOREIGN KEY (id_credencial) REFERENCES "KomainOS".credencial(id_credencial) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".credencial_documental
    ADD CONSTRAINT fk_credencial_documental_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".credencial_version
    ADD CONSTRAINT fk_credencial_version_credencial FOREIGN KEY (id_credencial) REFERENCES "KomainOS".credencial(id_credencial) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".cuenta_servicio
    ADD CONSTRAINT fk_cuenta_servicio_credencial FOREIGN KEY (id_credencial) REFERENCES "KomainOS".credencial(id_credencial) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".evaluacion
    ADD CONSTRAINT fk_evaluacion_credencial_version FOREIGN KEY (id_credencial_version) REFERENCES "KomainOS".credencial_version(id_credencial_version) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".evaluacion
    ADD CONSTRAINT fk_evaluacion_orden_detalle FOREIGN KEY (id_orden_detalle) REFERENCES "KomainOS".orden_detalle(id_orden_detalle) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".evaluacion
    ADD CONSTRAINT fk_evaluacion_script_version FOREIGN KEY (id_script_version) REFERENCES "KomainOS".script_version(id_script_version) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".evaluacion
    ADD CONSTRAINT fk_evaluacion_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".evaluacion_umbral
    ADD CONSTRAINT fk_evaluacion_umbral_medicion FOREIGN KEY (id_medicion_metrica) REFERENCES "KomainOS".medicion_metrica(id_medicion_metrica) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".evaluacion_umbral
    ADD CONSTRAINT fk_evaluacion_umbral_umbral FOREIGN KEY (id_umbral) REFERENCES "KomainOS".umbral(id_umbral) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".grupo_servidor
    ADD CONSTRAINT fk_grupo_servidor_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES "KomainOS".grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".grupo_servidor
    ADD CONSTRAINT fk_grupo_servidor_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".historial_estado_orden
    ADD CONSTRAINT fk_historial_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".historial_estado_orden
    ADD CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".incidencia
    ADD CONSTRAINT fk_incidencia_orden_detalle FOREIGN KEY (id_orden_detalle) REFERENCES "KomainOS".orden_detalle(id_orden_detalle) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".incidencia
    ADD CONSTRAINT fk_incidencia_usuario_atencion FOREIGN KEY (id_usuario_atencion) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".medicion_metrica
    ADD CONSTRAINT fk_medicion_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES "KomainOS".evaluacion(id_evaluacion) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".medicion_metrica
    ADD CONSTRAINT fk_medicion_metrica FOREIGN KEY (id_metrica) REFERENCES "KomainOS".metrica(id_metrica) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".mop
    ADD CONSTRAINT fk_mop_orden_detalle FOREIGN KEY (id_orden_detalle) REFERENCES "KomainOS".orden_detalle(id_orden_detalle) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT fk_mop_tarea_credencial_version FOREIGN KEY (id_credencial_version) REFERENCES "KomainOS".credencial_version(id_credencial_version) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT fk_mop_tarea_mop FOREIGN KEY (id_mop) REFERENCES "KomainOS".mop(id_mop) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT fk_mop_tarea_script_version FOREIGN KEY (id_script_version) REFERENCES "KomainOS".script_version(id_script_version) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".mop_tarea
    ADD CONSTRAINT fk_mop_tarea_tarea FOREIGN KEY (id_tarea) REFERENCES "KomainOS".tarea(id_tarea) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".notificacion_regla
    ADD CONSTRAINT fk_notificacion_regla_politica FOREIGN KEY (id_regla_politica_cambio) REFERENCES "KomainOS".regla_politica_cambio(id_regla_politica_cambio) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT fk_orden_criticidad FOREIGN KEY (id_nivel_criticidad) REFERENCES "KomainOS".nivel_criticidad(id_nivel_criticidad) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT fk_orden_cuenta FOREIGN KEY (id_cuenta_servicio) REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".orden_detalle
    ADD CONSTRAINT fk_orden_detalle_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".orden_detalle
    ADD CONSTRAINT fk_orden_detalle_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT fk_orden_grupo FOREIGN KEY (id_grupo_mantenimiento) REFERENCES "KomainOS".grupo_mantenimiento(id_grupo_mantenimiento) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT fk_orden_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".orden
    ADD CONSTRAINT fk_orden_usuario_solicitante FOREIGN KEY (id_usuario_solicitante) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".programacion_orden
    ADD CONSTRAINT fk_programacion_orden_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".programacion_orden
    ADD CONSTRAINT fk_programacion_orden_usuario FOREIGN KEY (id_usuario_registro) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".regla_politica_cambio
    ADD CONSTRAINT fk_regla_politica_entorno FOREIGN KEY (id_entorno) REFERENCES "KomainOS".entorno(id_entorno) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".script_implementacion
    ADD CONSTRAINT fk_script_implementacion_script FOREIGN KEY (id_script) REFERENCES "KomainOS".script(id_script) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".script_implementacion
    ADD CONSTRAINT fk_script_implementacion_version_so FOREIGN KEY (id_version_sistema_operativo) REFERENCES "KomainOS".version_sistema_operativo(id_version_sistema_operativo) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".script_parametro
    ADD CONSTRAINT fk_script_parametro_version FOREIGN KEY (id_script_version) REFERENCES "KomainOS".script_version(id_script_version) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".script_version
    ADD CONSTRAINT fk_script_version_implementacion FOREIGN KEY (id_script_implementacion) REFERENCES "KomainOS".script_implementacion(id_script_implementacion) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT fk_servidor_criticidad FOREIGN KEY (id_nivel_criticidad) REFERENCES "KomainOS".nivel_criticidad(id_nivel_criticidad) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT fk_servidor_entorno FOREIGN KEY (id_entorno) REFERENCES "KomainOS".entorno(id_entorno) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT fk_servidor_responsable FOREIGN KEY (id_usuario_responsable) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".servidor
    ADD CONSTRAINT fk_servidor_version_so FOREIGN KEY (id_version_sistema_operativo) REFERENCES "KomainOS".version_sistema_operativo(id_version_sistema_operativo) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".solicitud_autorizacion
    ADD CONSTRAINT fk_solicitud_autorizacion_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".solicitud_autorizacion
    ADD CONSTRAINT fk_solicitud_autorizacion_regla FOREIGN KEY (id_regla_politica_cambio) REFERENCES "KomainOS".regla_politica_cambio(id_regla_politica_cambio) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".solicitud_baja
    ADD CONSTRAINT fk_solicitud_baja_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".solicitud_baja
    ADD CONSTRAINT fk_solicitud_baja_usuario FOREIGN KEY (id_usuario_solicitante) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".tarea
    ADD CONSTRAINT fk_tarea_script FOREIGN KEY (id_script) REFERENCES "KomainOS".script(id_script) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".umbral
    ADD CONSTRAINT fk_umbral_metrica FOREIGN KEY (id_metrica) REFERENCES "KomainOS".metrica(id_metrica) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".umbral_tarea
    ADD CONSTRAINT fk_umbral_tarea_tarea FOREIGN KEY (id_tarea) REFERENCES "KomainOS".tarea(id_tarea) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".umbral_tarea
    ADD CONSTRAINT fk_umbral_tarea_umbral FOREIGN KEY (id_umbral) REFERENCES "KomainOS".umbral(id_umbral) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".validacion_funcional
    ADD CONSTRAINT fk_validacion_funcional_orden FOREIGN KEY (id_orden) REFERENCES "KomainOS".orden(id_orden) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".validacion_funcional
    ADD CONSTRAINT fk_validacion_funcional_responsable FOREIGN KEY (id_usuario_responsable) REFERENCES "KomainOS".usuario(id_usuario) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE ONLY "KomainOS".validacion_tecnica
    ADD CONSTRAINT fk_validacion_tecnica_detalle FOREIGN KEY (id_orden_detalle) REFERENCES "KomainOS".orden_detalle(id_orden_detalle) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".ventana_mantenimiento
    ADD CONSTRAINT fk_ventana_servidor FOREIGN KEY (id_servidor) REFERENCES "KomainOS".servidor(id_servidor) ON UPDATE RESTRICT ON DELETE CASCADE;
ALTER TABLE ONLY "KomainOS".version_sistema_operativo
    ADD CONSTRAINT fk_version_so_sistema_operativo FOREIGN KEY (id_sistema_operativo) REFERENCES "KomainOS".sistema_operativo(id_sistema_operativo) ON UPDATE RESTRICT ON DELETE RESTRICT;
