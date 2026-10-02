-- 03 - Datos de prueba solo para desarrollo: NUNCA en una base con datos reales (ver README)
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM usuario) OR EXISTS (SELECT 1 FROM servidor) THEN
        RAISE EXCEPTION 'La base ya tiene usuarios o servidores: ejecute antes scripts/bd/limpiar_datos_desarrollo.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM configuracion_sistema) THEN
        RAISE EXCEPTION 'Faltan los datos del sistema: ejecute antes scripts/bd/02_datos_sistema.sql';
    END IF;
END $$;

-- Uno por rol más un inactivo. Como ya existe un administrador, el backend no crea el inicial (DEC-21)
INSERT INTO usuario (codigo, nombre_completo, hash_contrasena, rol, activo) VALUES
    ('admin',     'Administrador del sistema', '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'ADMINISTRADOR', true),
    ('m.herrera', 'M. Herrera',                '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'RESPONSABLE',   true),
    ('j.paredes', 'J. Paredes',                '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'RESPONSABLE',   true),
    ('c.rojas',   'C. Rojas',                  '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'RESPONSABLE',   true),
    ('operador',  'Operador de turno',         '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'OPERADOR',      true),
    ('a.soto',    'A. Soto',                   '$2a$10$Cwf9zKiXIwEyfO1ojeGMwutM/pMHzoYxXwwHND14H9./6gWgM8Kem', 'OPERADOR',      false);

INSERT INTO entorno (nombre, descripcion) VALUES
    ('Producción', 'Servicios productivos'),
    ('Staging',    'Preproducción'),
    ('QA',         'Pruebas');

-- Menor prioridad = más crítico (DEC-13)
INSERT INTO nivel_criticidad (nombre, prioridad, frecuencia_revision_dias, frecuencia_mantenimiento_dias,
                              plazo_autorizacion_horas, plazo_validacion_horas) VALUES
    ('Alta',  1,  7, 30, 24, 24),
    ('Media', 2, 14, 45, 48, 48),
    ('Baja',  3, 30, 90, 72, 72);

INSERT INTO sistema_operativo (nombre, familia) VALUES
    ('Ubuntu',         'LINUX'),
    ('RHEL',           'LINUX'),
    ('Windows Server', 'WINDOWS');

-- Ubuntu 20.04 queda inactiva: fin de soporte, solo la usa el servidor dado de baja
INSERT INTO version_sistema_operativo (id_sistema_operativo, version, activo)
SELECT so.id_sistema_operativo, d.version, d.activo
FROM (VALUES ('Ubuntu', '20.04', false),
             ('Ubuntu', '22.04', true),
             ('Ubuntu', '24.04', true),
             ('RHEL', '9', true),
             ('Windows Server', '2022', true)) AS d(sistema, version, activo)
JOIN sistema_operativo so ON so.nombre = d.sistema;

-- ACTIVO si tiene configuración (DEC-14), srv-batch-01 queda pendiente de configuración y srv-legacy-01 dado de baja
INSERT INTO servidor (id_version_sistema_operativo, id_entorno, id_nivel_criticidad, id_usuario_responsable,
                      hostname, vdc, servidor_fisico, vlan, cluster, dns, plataforma,
                      descripcion, estado, cantidad_cpu, ram_gb, hd_virtual_gb, fecha_alta, fecha_actualizacion)
SELECT v.id_version_sistema_operativo, e.id_entorno, n.id_nivel_criticidad, u.id_usuario,
       d.hostname, d.vdc, d.fisico, d.vlan, d.cluster, d.hostname || '.komainos.local', d.plataforma,
       d.descripcion, d.estado::enum_estado_servidor, d.cpu, d.ram, d.disco,
       date_trunc('second', now() AT TIME ZONE 'UTC') - make_interval(days => d.dias_alta),
       date_trunc('second', now() AT TIME ZONE 'UTC') - make_interval(days => d.dias_alta)
FROM (VALUES
    ('srv-app-01',    'VDC-Norte', 'esx-blade-04', 'VLAN 120', 'cl-app-norte', 'Ubuntu', '22.04', 'Producción', 'Media', 'm.herrera', 'VMware',  'ACTIVO',                     120,  4,   16.00,  120.00, 'Aplicación del portal de clientes (nodo 1)'),
    ('srv-app-02',    'VDC-Norte', 'esx-blade-04', 'VLAN 120', 'cl-app-norte', 'Ubuntu', '22.04', 'Producción', 'Media', 'm.herrera', 'VMware',  'ACTIVO',                     120,  4,   16.00,  120.00, 'Aplicación del portal de clientes (nodo 2)'),
    ('srv-db-01',     'VDC-Norte', 'esx-blade-02', 'VLAN 210', 'cl-db-norte',  'RHEL',   '9',     'Producción', 'Alta',  'j.paredes', 'VMware',  'ACTIVO',                     200, 16,   64.00, 1024.00, 'Base de datos transaccional, primaria'),
    ('srv-db-02',     'VDC-Norte', 'esx-blade-02', 'VLAN 210', 'cl-db-norte',  'RHEL',   '9',     'Producción', 'Alta',  'j.paredes', 'VMware',  'ACTIVO',                     200, 16,   64.00, 1024.00, 'Base de datos transaccional, réplica'),
    ('srv-cache-01',  'VDC-Sur',   'esx-blade-09', 'VLAN 130', 'cl-app-sur',   'Ubuntu', '22.04', 'Producción', 'Alta',  'm.herrera', 'VMware',  'ACTIVO',                      90,  2,    8.00,   60.00, 'Caché distribuida de sesiones'),
    ('srv-web-01',    'VDC-Sur',   'esx-blade-11', 'VLAN 300', 'cl-web-sur',   'Windows Server', '2022', 'Staging', 'Media', 'c.rojas', 'Hyper-V', 'ACTIVO',                  60,  4,   12.50,  200.00, 'Servidor web de preproducción'),
    ('srv-batch-01',  'VDC-Norte', 'esx-blade-06', 'VLAN 400', 'cl-qa',        'Ubuntu', '24.04', 'QA',         'Baja',  'c.rojas',   'VMware',  'PENDIENTE_DE_CONFIGURACION',  10, NULL,  NULL,    NULL, 'Procesos batch de pruebas'),
    ('srv-legacy-01', 'VDC-Norte', 'esx-blade-01', 'VLAN 120', 'cl-app-norte', 'Ubuntu', '20.04', 'Producción', 'Baja',  'm.herrera', 'VMware',  'DADO_DE_BAJA',               400,  2,    4.00,   40.00, 'Antiguo servidor de aplicaciones')
) AS d(hostname, vdc, fisico, vlan, cluster, sistema, version, entorno, criticidad, responsable,
       plataforma, estado, dias_alta, cpu, ram, disco, descripcion)
JOIN sistema_operativo so ON so.nombre = d.sistema
JOIN version_sistema_operativo v ON v.id_sistema_operativo = so.id_sistema_operativo AND v.version = d.version
JOIN entorno e ON e.nombre = d.entorno
JOIN nivel_criticidad n ON n.nombre = d.criticidad
JOIN usuario u ON u.codigo = d.responsable;

-- Una principal por servidor y ninguna IP se repite entre servidores (DEC-37)
INSERT INTO direccion_ip (id_servidor, direccion, principal)
SELECT s.id_servidor, d.direccion, d.principal
FROM (VALUES
    ('srv-app-01',    '10.20.1.11', true),
    ('srv-app-01',    '10.90.1.11', false),
    ('srv-app-02',    '10.20.1.12', true),
    ('srv-app-02',    '10.90.1.12', false),
    ('srv-db-01',     '10.20.2.10', true),
    ('srv-db-01',     '10.21.2.10', false),
    ('srv-db-01',     '10.90.2.10', false),
    ('srv-db-02',     '10.20.2.11', true),
    ('srv-db-02',     '10.21.2.11', false),
    ('srv-cache-01',  '10.20.3.10', true),
    ('srv-web-01',    '10.30.1.20', true),
    ('srv-web-01',    'fd00:30::20', false),
    ('srv-batch-01',  '10.40.1.15', true),
    ('srv-legacy-01', '10.20.9.5',  true)
) AS d(hostname, direccion, principal)
JOIN servidor s ON s.hostname = d.hostname;

-- En hora de Lima (DEC-06), un intervalo que cruza la medianoche termina al día siguiente
INSERT INTO ventana_mantenimiento (id_servidor, dia_inicio, hora_inicio, dia_fin, hora_fin)
SELECT s.id_servidor, d.dia_inicio::enum_dia_semana, d.hora_inicio::time, d.dia_fin::enum_dia_semana, d.hora_fin::time
FROM (VALUES
    ('srv-app-01',   'SABADO',  '01:00', 'SABADO',  '05:00'),
    ('srv-app-01',   'DOMINGO', '01:00', 'DOMINGO', '05:00'),
    ('srv-app-02',   'SABADO',  '01:00', 'SABADO',  '05:00'),
    ('srv-app-02',   'DOMINGO', '01:00', 'DOMINGO', '05:00'),
    ('srv-db-01',    'SABADO',  '22:00', 'DOMINGO', '04:00'),
    ('srv-db-02',    'SABADO',  '22:00', 'DOMINGO', '04:00'),
    ('srv-cache-01', 'SABADO',  '01:00', 'SABADO',  '05:00'),
    ('srv-cache-01', 'DOMINGO', '01:00', 'DOMINGO', '05:00'),
    ('srv-web-01',   'MIERCOLES', '23:00', 'JUEVES', '03:00'),
    ('srv-web-01',   'SABADO',  '22:00', 'DOMINGO', '04:00')
) AS d(hostname, dia_inicio, hora_inicio, dia_fin, hora_fin)
JOIN servidor s ON s.hostname = d.hostname;

-- Se fecha para que el primer ciclo automático (DEC-09) venza en dos días
DO $$
DECLARE
    r record;
    v_config integer;
BEGIN
    FOR r IN
        SELECT s.id_servidor, n.frecuencia_revision_dias, n.frecuencia_mantenimiento_dias,
               d.modalidad::enum_modalidad_planificacion AS modalidad
        FROM (VALUES ('srv-app-01', 'AUTOMATICA'), ('srv-app-02', 'AUTOMATICA'),
                     ('srv-db-01', 'AUTOMATICA'), ('srv-db-02', 'AUTOMATICA'),
                     ('srv-cache-01', 'BAJO_DEMANDA'), ('srv-web-01', 'BAJO_DEMANDA')) AS d(hostname, modalidad)
        JOIN servidor s ON s.hostname = d.hostname
        JOIN nivel_criticidad n ON n.id_nivel_criticidad = s.id_nivel_criticidad
    LOOP
        INSERT INTO configuracion_mantenimiento (frecuencia_revision_dias, frecuencia_mantenimiento_dias,
                                                 modalidad_planificacion, fecha_creacion, fecha_actualizacion)
        VALUES (r.frecuencia_revision_dias, r.frecuencia_mantenimiento_dias, r.modalidad,
                date_trunc('minute', now() AT TIME ZONE 'UTC') - make_interval(days => r.frecuencia_mantenimiento_dias - 2),
                date_trunc('minute', now() AT TIME ZONE 'UTC') - make_interval(days => r.frecuencia_mantenimiento_dias - 2))
        RETURNING id_configuracion_mantenimiento INTO v_config;
        INSERT INTO configuracion_servidor (id_configuracion_mantenimiento, id_servidor) VALUES (v_config, r.id_servidor);
    END LOOP;
END $$;

-- Mismo responsable, entorno y sistema operativo en todos sus integrantes (RF20)
DO $$
DECLARE
    v_grupo integer;
    v_config integer;
BEGIN
    INSERT INTO grupo_mantenimiento (nombre, descripcion, estado)
    VALUES ('Grupo DB Producción', 'Bases de datos productivas de J. Paredes', 'ACTIVO')
    RETURNING id_grupo_mantenimiento INTO v_grupo;

    INSERT INTO grupo_servidor (id_grupo_mantenimiento, id_servidor)
    SELECT v_grupo, id_servidor FROM servidor WHERE hostname IN ('srv-db-01', 'srv-db-02');

    INSERT INTO configuracion_mantenimiento (frecuencia_revision_dias, frecuencia_mantenimiento_dias, modalidad_planificacion)
    SELECT frecuencia_revision_dias, frecuencia_mantenimiento_dias, 'BAJO_DEMANDA'::enum_modalidad_planificacion
    FROM nivel_criticidad WHERE nombre = 'Alta'
    RETURNING id_configuracion_mantenimiento INTO v_config;
    INSERT INTO configuracion_grupo (id_configuracion_mantenimiento, id_grupo_mantenimiento, modo_ejecucion)
    VALUES (v_config, v_grupo, 'SECUENCIAL');
END $$;

-- RF72: la baja conserva el historial con el motivo y quién la pidió
INSERT INTO solicitud_baja (id_servidor, id_usuario_solicitante, estado, motivo, fecha_solicitud, fecha_aplicacion)
SELECT s.id_servidor, u.id_usuario, 'APLICADA'::enum_estado_solicitud_baja,
       'Reemplazado por srv-app-02; Ubuntu 20.04 sin soporte del fabricante',
       date_trunc('second', now() AT TIME ZONE 'UTC') - interval '15 days',
       date_trunc('second', now() AT TIME ZONE 'UTC') - interval '15 days'
FROM servidor s, usuario u
WHERE s.hostname = 'srv-legacy-01' AND u.codigo = 'admin';

-- Hora de Lima a UTC, como se guardan todos los TIMESTAMP (DEC-06)
CREATE FUNCTION pg_temp.a_utc(p_hora_local timestamp) RETURNS timestamp LANGUAGE sql AS
$$ SELECT (p_hora_local AT TIME ZONE 'America/Lima') AT TIME ZONE 'UTC' $$;

-- Crea una orden PROGRAMADA como lo hace el backend: código OM-<año>-<id> (DEC-07) y reserva de max_duracion_mop_minutos (DEC-08)
CREATE FUNCTION pg_temp.crear_orden(p_hostname text, p_origen text, p_solicitante text,
                                    p_inicio_local timestamp, p_ventana_inicio_local timestamp,
                                    p_ventana_fin_local timestamp, p_motivo text) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    v_servidor integer;
    v_criticidad integer;
    v_plazo integer;
    v_id integer := nextval('orden_id_orden_seq');
    v_solicitante integer := (SELECT id_usuario FROM usuario WHERE codigo = p_solicitante);
    v_ahora timestamp := date_trunc('second', now() AT TIME ZONE 'UTC');
    v_inicio timestamp := pg_temp.a_utc(p_inicio_local);
    v_fin timestamp := pg_temp.a_utc(p_inicio_local) + make_interval(mins => (SELECT max_duracion_mop_minutos FROM configuracion_sistema));
    v_objetivo timestamp;
BEGIN
    SELECT s.id_servidor, s.id_nivel_criticidad, n.plazo_autorizacion_horas
    INTO v_servidor, v_criticidad, v_plazo
    FROM servidor s JOIN nivel_criticidad n ON n.id_nivel_criticidad = s.id_nivel_criticidad
    WHERE s.hostname = p_hostname;

    -- Primer ciclo automático: creación de la configuración más la frecuencia (DEC-09)
    SELECT cm.fecha_creacion + make_interval(days => cm.frecuencia_mantenimiento_dias)
    INTO v_objetivo
    FROM configuracion_servidor cs
    JOIN configuracion_mantenimiento cm ON cm.id_configuracion_mantenimiento = cs.id_configuracion_mantenimiento
    WHERE cs.id_servidor = v_servidor AND p_origen = 'PLANIFICACION_AUTOMATICA';

    INSERT INTO orden (id_orden, codigo, id_servidor, id_nivel_criticidad, id_usuario_solicitante, origen, estado, fecha_creacion)
    VALUES (v_id, format('OM-%s-%s', extract(year FROM v_ahora)::int, lpad(v_id::text, 4, '0')), v_servidor, v_criticidad,
            v_solicitante, p_origen::enum_origen_orden, 'PROGRAMADA', v_ahora);

    INSERT INTO orden_detalle (id_orden, id_servidor, posicion_ejecucion, es_servidor_piloto, estado,
                               fecha_prevista_inicio, fecha_prevista_fin)
    VALUES (v_id, v_servidor, 1, false, 'PENDIENTE', v_inicio, v_fin);

    INSERT INTO programacion_orden (id_orden, id_usuario_registro, numero_version, fecha_objetivo, fecha_inicio_programada,
                                    fecha_fin_programada, fecha_evaluacion_programada, inicio_ventana_aplicada,
                                    fin_ventana_aplicada, motivo, fecha_registro)
    VALUES (v_id, v_solicitante, 1, coalesce(v_objetivo, v_inicio), v_inicio, v_fin,
            greatest(v_inicio - make_interval(hours => v_plazo), v_ahora),
            pg_temp.a_utc(p_ventana_inicio_local), pg_temp.a_utc(p_ventana_fin_local), p_motivo, v_ahora);

    INSERT INTO historial_estado_orden (id_orden, id_usuario, estado_anterior, estado_nuevo, motivo, fecha_hora)
    VALUES (v_id, v_solicitante, NULL, 'PROGRAMADA', p_motivo, v_ahora);
    RETURN v_id;
END $$;

DO $$
DECLARE
    v_ahora_local timestamp := now() AT TIME ZONE 'America/Lima';
    v_sabado timestamp := date_trunc('week', now() AT TIME ZONE 'America/Lima') + interval '5 days';
    v_id integer;
BEGIN
    -- Próximo sábado con al menos 3 días de anticipación: la evaluación previa también queda en el futuro
    WHILE v_sabado < v_ahora_local + interval '3 days' LOOP
        v_sabado := v_sabado + interval '7 days';
    END LOOP;

    -- Ciclos automáticos (RF27) dentro de la ventana de cada servidor
    PERFORM pg_temp.crear_orden('srv-app-01', 'PLANIFICACION_AUTOMATICA', NULL,
            v_sabado + interval '1 hour', v_sabado + interval '1 hour', v_sabado + interval '5 hours',
            'Ciclo automático generado por el Sistema');
    PERFORM pg_temp.crear_orden('srv-db-01', 'PLANIFICACION_AUTOMATICA', NULL,
            v_sabado + interval '22 hours', v_sabado + interval '22 hours', v_sabado + interval '28 hours',
            'Ciclo automático generado por el Sistema');
    PERFORM pg_temp.crear_orden('srv-db-02', 'PLANIFICACION_AUTOMATICA', NULL,
            v_sabado + interval '22 hours', v_sabado + interval '22 hours', v_sabado + interval '28 hours',
            'Ciclo automático generado por el Sistema');

    -- Servidor bajo demanda: la programa el administrador (RF30)
    PERFORM pg_temp.crear_orden('srv-cache-01', 'SOLICITUD_BAJO_DEMANDA', 'admin',
            v_sabado + interval '25 hours', v_sabado + interval '25 hours', v_sabado + interval '29 hours',
            'Actualización de parches de seguridad solicitada por el responsable');

    -- Ciclo cancelado por el administrador (DEC-19): deja de ocupar el servidor
    v_id := pg_temp.crear_orden('srv-app-02', 'PLANIFICACION_AUTOMATICA', NULL,
            v_sabado + interval '1 hour', v_sabado + interval '1 hour', v_sabado + interval '5 hours',
            'Ciclo automático generado por el Sistema');
    UPDATE orden SET estado = 'CANCELADA' WHERE id_orden = v_id;
    UPDATE orden_detalle SET estado = 'NO_INICIADO' WHERE id_orden = v_id;
    INSERT INTO historial_estado_orden (id_orden, id_usuario, estado_anterior, estado_nuevo, motivo, fecha_hora)
    SELECT v_id, id_usuario, 'PROGRAMADA'::enum_estado_orden, 'CANCELADA'::enum_estado_orden,
           'Congelamiento de cambios por cierre de mes; se programará en el siguiente ciclo',
           date_trunc('second', now() AT TIME ZONE 'UTC')
    FROM usuario WHERE codigo = 'admin';
END $$;

COMMIT;
