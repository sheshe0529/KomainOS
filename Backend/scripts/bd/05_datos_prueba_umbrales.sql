-- 05 - Datos de prueba de umbrales (R2.4, DEC-41, DEC-48). Solo para desarrollo, después del 03 y del 04.
-- Ejemplos de excepciones en los alcances servidor, grupo, entorno y criticidad sobre los umbrales iniciales del 04,
-- listas propias de servidores y grupos, y un elemento retirado de una lista. Idempotente: no vuelve a cargar si ya
-- existe la lista «Puertos web».
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM servidor WHERE hostname = 'SERVPRODBDDV01') THEN
        RAISE EXCEPTION 'Faltan los datos de 03_datos_prueba.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM lista_referencia WHERE nombre = 'Puertos autorizados Linux') THEN
        RAISE EXCEPTION 'Faltan los umbrales iniciales de 04_umbrales_variables.sql';
    END IF;
END $$;

DO $$
DECLARE
    v_ahora timestamp := statement_timestamp() AT TIME ZONE 'UTC';
BEGIN
    IF EXISTS (SELECT 1 FROM lista_referencia WHERE nombre = 'Puertos web') THEN
        RAISE NOTICE 'Ya están los datos de prueba de umbrales';
        RETURN;
    END IF;

    -- Listas propias de servidores y grupos
    INSERT INTO lista_referencia (nombre, tipo_dato, ignora_mayusculas) VALUES
        ('Puertos web',             'INTEGER', false),
        ('Puertos base de datos',   'INTEGER', false),
        ('Puertos caché',           'INTEGER', false),
        ('Servicios base de datos', 'STRING',  false),
        ('Servicios web Windows',   'STRING',  true);

    INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta)
    SELECT l.id_lista_referencia, d.valor, v_ahora
    FROM (VALUES
            ('Puertos web', '22'), ('Puertos web', '80'), ('Puertos web', '443'),
            ('Puertos base de datos', '22'), ('Puertos base de datos', '5432'),
            ('Puertos caché', '22'), ('Puertos caché', '6379'),
            ('Servicios base de datos', 'auditd'), ('Servicios base de datos', 'chronyd'),
            ('Servicios base de datos', 'crond'), ('Servicios base de datos', 'postgresql'),
            ('Servicios base de datos', 'rsyslog'), ('Servicios base de datos', 'sshd'),
            ('Servicios base de datos', 'systemd-journald'),
            ('Servicios web Windows', 'eventlog'), ('Servicios web Windows', 'mpssvc'),
            ('Servicios web Windows', 'schedule'), ('Servicios web Windows', 'w32time'),
            ('Servicios web Windows', 'w3svc'), ('Servicios web Windows', 'winrm'))
         AS d(lista, valor)
    JOIN lista_referencia l ON l.nombre = d.lista;

    -- Un elemento retirado: conserva su periodo de vigencia
    INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta, fecha_baja)
    SELECT id_lista_referencia, '8080', v_ahora - interval '30 days', v_ahora - interval '2 days'
    FROM lista_referencia WHERE nombre = 'Puertos web';

    -- Cuentas administradoras de la organización de prueba, además de las de base
    INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta)
    SELECT l.id_lista_referencia, d.valor, v_ahora
    FROM (VALUES ('Cuentas administradoras Linux', 'sysadmin'), ('Cuentas administradoras Linux', 'svc_ansible'),
                 ('Cuentas administradoras Windows', 'svc_ansible'),
                 ('Cuentas administradoras Windows', 'komainos\admins. del dominio')) AS d(lista, valor)
    JOIN lista_referencia l ON l.nombre = d.lista
    WHERE NOT EXISTS (SELECT 1 FROM lista_referencia_item i
                      WHERE i.id_lista_referencia = l.id_lista_referencia AND i.valor = d.valor AND i.fecha_baja IS NULL);

    -- Excepciones numéricas y binarias, de mayor a menor prioridad: servidor, grupo, entorno y criticidad
    INSERT INTO umbral_excepcion (id_umbral, id_servidor, id_grupo_mantenimiento, id_entorno, id_nivel_criticidad,
                                  valor_advertencia, valor_critico, motivo)
    SELECT u.id_umbral, s.id_servidor, g.id_grupo_mantenimiento, e.id_entorno, c.id_nivel_criticidad,
           d.advertencia, d.critico, d.motivo
    FROM (VALUES
            ('cpu_uso', 'CPU alta', 'SERVPRODAPPV01', NULL, NULL, NULL, 85, 95, 'Procesos batch nocturnos de alto consumo'),
            ('cpu_uso', 'CPU alta', NULL, 'Grupo Base de Datos Producción', NULL, NULL, 70, 80,
             'La réplica asume la carga durante el mantenimiento del primario'),
            ('cpu_uso', 'CPU alta', NULL, NULL, 'Producción', NULL, 75, 85, 'Producción se atiende antes de saturarse'),
            ('cpu_uso', 'CPU alta', NULL, NULL, NULL, 'Baja', 85, 95, NULL),
            ('cpu_iowait', 'Espera de E/S elevada', NULL, 'Grupo Base de Datos Producción', NULL, NULL, 15, 30,
             'Los respaldos nocturnos generan E/S intensiva'),
            ('disco_iops', 'IOPS elevadas', NULL, 'Grupo Base de Datos Producción', NULL, NULL, 3000, 6000,
             'Almacenamiento de alto rendimiento dedicado a la base de datos'),
            ('mem_uso', 'Memoria alta', 'SERVPRODBDDV01', NULL, NULL, NULL, 90, 97,
             'PostgreSQL reserva la memoria compartida al iniciar'),
            ('mem_uso', 'Memoria alta', 'SERVPRODBDDV02', NULL, NULL, NULL, 90, 97,
             'PostgreSQL reserva la memoria compartida al iniciar'),
            ('disco_uso', 'Disco lleno', NULL, NULL, NULL, 'Alta', 75, 85, NULL),
            ('aut_fallidas', 'Autenticaciones fallidas', NULL, NULL, 'Producción', NULL, 10, 50,
             'Los servidores productivos están expuestos a la red de clientes'),
            ('firewall_inactivo', 'Firewall inactivo', NULL, NULL, 'Desarrollo', NULL, 1, NULL,
             'En desarrollo se permite revisar reglas con el firewall detenido'))
         AS d(codigo, umbral, servidor, grupo, entorno, criticidad, advertencia, critico, motivo)
    JOIN metrica m ON m.codigo = d.codigo
    JOIN umbral u ON u.id_metrica = m.id_metrica AND u.nombre = d.umbral
    LEFT JOIN servidor s ON s.hostname = d.servidor
    LEFT JOIN grupo_mantenimiento g ON g.nombre = d.grupo
    LEFT JOIN entorno e ON e.nombre = d.entorno
    LEFT JOIN nivel_criticidad c ON c.nombre = d.criticidad
    WHERE num_nonnulls(s.id_servidor, g.id_grupo_mantenimiento, e.id_entorno, c.id_nivel_criticidad) = 1;

    -- Excepciones de lista: la lista propia del alcance
    INSERT INTO umbral_excepcion (id_umbral, id_servidor, id_grupo_mantenimiento, id_entorno, id_lista_referencia, motivo)
    SELECT u.id_umbral, s.id_servidor, g.id_grupo_mantenimiento, e.id_entorno, l.id_lista_referencia, d.motivo
    FROM (VALUES
            ('red_puerto_no_aut', 'Puertos autorizados', 'SERVPRODCACV01', NULL, NULL, 'Puertos caché',
             'Caché de sesiones (Redis)'),
            ('red_puerto_no_aut', 'Puertos autorizados', NULL, 'Grupo Base de Datos Producción', NULL,
             'Puertos base de datos', 'Solo SSH y PostgreSQL'),
            ('red_puerto_no_aut', 'Puertos autorizados', NULL, NULL, 'Producción', 'Puertos web', NULL),
            ('serv_no_operativo', 'Servicios requeridos', 'SERVDESWEBV01', NULL, NULL, 'Servicios web Windows',
             'Publica el portal en IIS'),
            ('serv_no_operativo', 'Servicios requeridos', NULL, 'Grupo Base de Datos Producción', NULL,
             'Servicios base de datos', 'Incluye el servicio de PostgreSQL'))
         AS d(codigo, umbral, servidor, grupo, entorno, lista, motivo)
    JOIN metrica m ON m.codigo = d.codigo
    JOIN umbral u ON u.id_metrica = m.id_metrica AND u.nombre = d.umbral
    JOIN lista_referencia l ON l.nombre = d.lista
    LEFT JOIN servidor s ON s.hostname = d.servidor
    LEFT JOIN grupo_mantenimiento g ON g.nombre = d.grupo
    LEFT JOIN entorno e ON e.nombre = d.entorno
    WHERE num_nonnulls(s.id_servidor, g.id_grupo_mantenimiento, e.id_entorno) = 1;
END $$;

COMMIT;
