-- =============================================================================
-- Vacia los datos de negocio de la base de DESARROLLO de KomainOS.
--
-- Conserva el esquema y los datos del sistema de 02_datos_sistema.sql
-- (factor_ciclo y configuracion_sistema). Despues se puede cargar
-- 03_datos_prueba.sql, o arrancar el backend: si no queda ningun administrador,
-- lo crea con KOMAINOS_ADMIN_CLAVE_INICIAL (DEC-21).
--
-- NUNCA ejecutar en una base con datos reales: la auditoria es de solo
-- insercion por diseño (RNF06) y este script la vacia.
--
--   psql -h localhost -U postgres -d DBKomainOS -f scripts/bd/limpiar_datos_desarrollo.sql
-- =============================================================================
BEGIN;
SET LOCAL search_path TO "KomainOS";

TRUNCATE TABLE
    alerta_destinatario, alerta, auditoria, incidencia, validacion_funcional, validacion_tecnica,
    autorizacion_detalle, solicitud_autorizacion, notificacion_regla, regla_politica_cambio,
    mop_tarea, mop, evaluacion_umbral, medicion_metrica, evaluacion, cierre_orden,
    confirmacion_fuera_ventana, orden_detalle, historial_estado_orden, programacion_orden, orden,
    umbral_tarea, tarea, script_parametro, script_version, script_implementacion, script, umbral, metrica,
    configuracion_grupo, configuracion_servidor, configuracion_mantenimiento,
    grupo_servidor, grupo_mantenimiento, solicitud_baja, ventana_mantenimiento, credencial_documental,
    direccion_ip, servidor, version_sistema_operativo, sistema_operativo, nivel_criticidad, entorno, usuario
RESTART IDENTITY;

-- cuenta_servicio la referencia configuracion_sistema, que se conserva: por eso
-- las credenciales se borran con DELETE (cascada a sus especializaciones y
-- versiones) despues de soltar la cuenta predeterminada.
UPDATE configuracion_sistema SET id_cuenta_servicio_predeterminada = NULL;
DELETE FROM credencial;
COMMIT;
