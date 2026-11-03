-- 04 - Umbrales iniciales de las variables de salud del R3.1 (RF16, DEC-48). Después del 02 y, si hay datos de prueba,
-- del 03. Sirve para cualquier base y es idempotente: no modifica umbrales, listas ni excepciones existentes.
--   1. Listas de referencia de base (sus elementos solo se cargan al crear la lista)
--   2. Un umbral global por cada variable que todavía no tenga ninguno: numérico, binario (= 1) o de lista
--   3. Excepciones por sistema operativo para los sistemas registrados y activos: Windows Server y la familia de RHEL
--      usan otros nombres de servicios, cuentas, puertos y permisos que Ubuntu, cuyos valores son los globales
-- Vuelva a ejecutarlo después de registrar un sistema operativo para agregar sus excepciones. Los límites son genéricos
-- y se ajustan en Umbrales según la política de la organización.
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF (SELECT count(*) FROM metrica WHERE codigo IN ('cpu_uso', 'serv_no_operativo', 'mem_agotamiento')) < 3 THEN
        RAISE EXCEPTION 'Faltan las variables del R3.1 de 02_datos_sistema.sql';
    END IF;
END $$;

DO $$
DECLARE
    v_ahora timestamp := statement_timestamp() AT TIME ZONE 'UTC';
    v_lista integer;
    v_cantidad integer;
    r record;
BEGIN
    -- 1. Listas de referencia de base. Ruta y permiso octal de la política: Ubuntu protege shadow con el grupo shadow
    --    (640) y RHEL sin permisos (000). Las listas de Windows no distinguen mayúsculas
    FOR r IN
        SELECT * FROM (VALUES
            ('Cuentas administradoras Linux', 'STRING', false, ARRAY['root']),
            ('Cuentas administradoras Windows', 'STRING', true, ARRAY['administrador', 'administrator']),
            ('Permisos de archivos críticos', 'STRING', false,
             ARRAY['/etc/passwd:644', '/etc/group:644', '/etc/shadow:640', '/etc/gshadow:640', '/etc/sudoers:440',
                   '/etc/ssh/sshd_config:600', '/etc/crontab:600']),
            ('Permisos de archivos críticos RHEL', 'STRING', false,
             ARRAY['/etc/passwd:644', '/etc/group:644', '/etc/shadow:0', '/etc/gshadow:0', '/etc/sudoers:440',
                   '/etc/ssh/sshd_config:600', '/etc/crontab:600']),
            ('Puertos autorizados Linux', 'INTEGER', false, ARRAY['22']),
            ('Puertos autorizados Windows', 'INTEGER', false, ARRAY['135', '445', '3389', '5985', '5986']),
            ('Servicios requeridos Ubuntu', 'STRING', false, ARRAY['cron', 'rsyslog', 'ssh', 'systemd-journald']),
            ('Servicios requeridos RHEL', 'STRING', false,
             ARRAY['auditd', 'chronyd', 'crond', 'rsyslog', 'sshd', 'systemd-journald']),
            ('Servicios requeridos Windows', 'STRING', true, ARRAY['eventlog', 'mpssvc', 'schedule', 'w32time', 'winrm'])
        ) AS l(nombre, tipo, ignora, elementos)
    LOOP
        INSERT INTO lista_referencia (nombre, tipo_dato, ignora_mayusculas)
        VALUES (r.nombre, r.tipo::enum_tipo_dato_parametro, r.ignora)
        ON CONFLICT (nombre) DO NOTHING
        RETURNING id_lista_referencia INTO v_lista;
        IF v_lista IS NOT NULL THEN
            INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta)
            SELECT v_lista, e, v_ahora FROM unnest(r.elementos) AS e;
            RAISE NOTICE 'Lista «%» creada', r.nombre;
        END IF;
    END LOOP;

    -- 2a. Umbrales numéricos y binarios. Con un solo nivel, el otro no se evalúa; las binarias valen 1 cuando se
    --     incumple la configuración esperada
    FOR r IN
        SELECT * FROM (VALUES
            ('cpu_uso', 'CPU alta', 'MAYOR', 80, 90),
            ('cpu_load', 'Carga por CPU elevada', 'MAYOR', 1, 2),
            ('cpu_iowait', 'Espera de E/S elevada', 'MAYOR_IGUAL', 10, 20),
            ('disco_iops', 'IOPS elevadas', 'MAYOR_IGUAL', 1000, 3000),
            ('mem_uso', 'Memoria alta', 'MAYOR', 85, 95),
            ('swap_uso', 'Intercambio ocupado', 'MAYOR_IGUAL', NULL, 50),
            ('swap_act', 'Actividad de intercambio', 'MAYOR_IGUAL', 256, 1024),
            ('disco_uso', 'Disco lleno', 'MAYOR_IGUAL', 80, 90),
            ('disco_latencia', 'Latencia de disco', 'MAYOR_IGUAL', 20, 50),
            ('disco_errores', 'Errores de disco', 'MAYOR_IGUAL', 1, 10),
            ('red_error_rx', 'Errores de recepción de red', 'MAYOR_IGUAL', 0.5, 2),
            ('aut_fallidas', 'Autenticaciones fallidas', 'MAYOR_IGUAL', 20, 100),
            ('admin_acceso', 'Acceso directo del administrador', 'IGUAL', NULL, 1),
            ('firewall_inactivo', 'Firewall inactivo', 'IGUAL', NULL, 1),
            ('mac_no_estricto', 'Control de acceso obligatorio no estricto', 'IGUAL', 1, NULL),
            ('uptime_dias', 'Tiempo sin reiniciar', 'MAYOR_IGUAL', 90, 180),
            ('mem_agotamiento', 'Agotamiento de memoria', 'MAYOR_IGUAL', NULL, 1)
        ) AS u(codigo, nombre, operador, advertencia, critico)
    LOOP
        INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
        SELECT m.id_metrica, r.nombre, r.operador::enum_operador_comparacion, r.advertencia, r.critico
        FROM metrica m
        WHERE m.codigo = r.codigo AND NOT EXISTS (SELECT 1 FROM umbral u WHERE u.id_metrica = m.id_metrica);
        GET DIAGNOSTICS v_cantidad = ROW_COUNT;
        IF v_cantidad > 0 THEN
            RAISE NOTICE 'Umbral «%» creado para %', r.nombre, r.codigo;
        END IF;
    END LOOP;

    -- 2b. Umbrales de lista: alertan los sobrantes (cuentas y puertos no autorizados) o los faltantes (servicios que
    --     no están en ejecución y archivos cuyo par ruta:permiso no coincide con la política)
    FOR r IN
        SELECT * FROM (VALUES
            ('admin_no_aut', 'Cuentas administradoras autorizadas', 'Cuentas administradoras Linux', NULL, 'CRITICO'),
            ('arch_crit_permisos', 'Política de permisos de archivos críticos', 'Permisos de archivos críticos',
             'ADVERTENCIA', NULL),
            ('red_puerto_no_aut', 'Puertos autorizados', 'Puertos autorizados Linux', NULL, 'ADVERTENCIA'),
            ('serv_no_operativo', 'Servicios requeridos', 'Servicios requeridos Ubuntu', 'CRITICO', NULL)
        ) AS u(codigo, nombre, lista, faltante, sobrante)
    LOOP
        INSERT INTO umbral (id_metrica, id_lista_referencia, nombre, nivel_faltante, nivel_sobrante)
        SELECT m.id_metrica, l.id_lista_referencia, r.nombre, r.faltante::enum_nivel_alerta,
               r.sobrante::enum_nivel_alerta
        FROM metrica m
        JOIN lista_referencia l ON l.nombre = r.lista AND l.tipo_dato = m.tipo_dato
        WHERE m.codigo = r.codigo AND NOT EXISTS (SELECT 1 FROM umbral u WHERE u.id_metrica = m.id_metrica);
        GET DIAGNOSTICS v_cantidad = ROW_COUNT;
        IF v_cantidad > 0 THEN
            RAISE NOTICE 'Umbral «%» creado para %', r.nombre, r.codigo;
        END IF;
    END LOOP;

    -- 3. Excepciones por sistema operativo de los umbrales de este script (por métrica y nombre)
    FOR r IN
        SELECT * FROM (VALUES
            ('admin_no_aut', 'Cuentas administradoras autorizadas', 'WINDOWS', 'Cuentas administradoras Windows',
             NULL::numeric, NULL::numeric, 'Grupo Administradores local y del dominio'),
            ('arch_crit_permisos', 'Política de permisos de archivos críticos', 'RHEL',
             'Permisos de archivos críticos RHEL', NULL, NULL, 'RHEL protege shadow y gshadow sin permisos (000)'),
            ('red_puerto_no_aut', 'Puertos autorizados', 'WINDOWS', 'Puertos autorizados Windows', NULL, NULL,
             'RPC, SMB, escritorio remoto y WinRM'),
            ('serv_no_operativo', 'Servicios requeridos', 'RHEL', 'Servicios requeridos RHEL', NULL, NULL,
             'En la familia de RHEL los servicios se llaman sshd y crond'),
            ('serv_no_operativo', 'Servicios requeridos', 'WINDOWS', 'Servicios requeridos Windows', NULL, NULL,
             'Servicios de Windows Server'),
            ('uptime_dias', 'Tiempo sin reiniciar', 'WINDOWS', NULL, 35, 60,
             'Las actualizaciones acumulativas mensuales requieren reinicio')
        ) AS e(codigo, umbral, sistemas, lista, advertencia, critico, motivo)
    LOOP
        INSERT INTO umbral_excepcion (id_umbral, id_sistema_operativo, id_lista_referencia, valor_advertencia,
                                      valor_critico, motivo)
        SELECT u.id_umbral, so.id_sistema_operativo, l.id_lista_referencia, r.advertencia, r.critico, r.motivo
        FROM umbral u
        JOIN metrica m ON m.id_metrica = u.id_metrica
        JOIN sistema_operativo so ON so.activo
             AND CASE r.sistemas WHEN 'WINDOWS' THEN so.familia = 'WINDOWS'
                                 ELSE so.familia = 'LINUX' AND so.nombre ~* '(rhel|red ?hat|centos|rocky|alma|oracle linux|fedora)' END
             AND (m.so_aplica IS NULL OR m.so_aplica = so.familia)
        LEFT JOIN lista_referencia l ON l.nombre = r.lista
        WHERE m.codigo = r.codigo AND u.nombre = r.umbral
          AND (r.lista IS NULL) = (u.id_lista_referencia IS NULL)
          AND (r.lista IS NULL OR l.id_lista_referencia IS NOT NULL)
        ON CONFLICT ON CONSTRAINT uq_umbral_excepcion_alcance DO NOTHING;
        GET DIAGNOSTICS v_cantidad = ROW_COUNT;
        IF v_cantidad > 0 THEN
            RAISE NOTICE 'Umbral «%»: % excepción(es) por sistema operativo', r.umbral, v_cantidad;
        END IF;
    END LOOP;
END $$;

COMMIT;
