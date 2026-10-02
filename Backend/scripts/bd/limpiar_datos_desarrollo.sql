-- Vacía los datos de negocio de la base de DESARROLLO: NUNCA en una base real, la auditoría es de solo inserción (RNF06)
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

-- configuracion_sistema referencia la cuenta predeterminada: se suelta antes de borrar las credenciales
UPDATE configuracion_sistema SET id_cuenta_servicio_predeterminada = NULL;
DELETE FROM credencial;
COMMIT;
