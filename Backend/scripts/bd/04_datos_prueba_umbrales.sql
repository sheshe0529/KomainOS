-- 04 - Datos de prueba de métricas, listas de referencia y umbrales (RF16, R2.4). Solo para desarrollo, después del 03
-- Idempotente: no vuelve a cargar si ya hay métricas. Ejemplos de cada prioridad: servidor > grupo > entorno > criticidad > global
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM servidor WHERE hostname = 'SERVPRODBDDV01') THEN
        RAISE EXCEPTION 'Faltan los datos de 03_datos_prueba.sql';
    END IF;
END $$;

DO $$
DECLARE
    v_ahora timestamp := statement_timestamp() AT TIME ZONE 'UTC';
    v_umbral integer;
BEGIN
    IF EXISTS (SELECT 1 FROM metrica) THEN
        RAISE NOTICE 'Ya hay métricas registradas: no se cargan los datos de umbrales';
        RETURN;
    END IF;

    -- Métricas escalares (valor en medicion_metrica.valor) y de lista (elementos en medicion_lista_item)
    INSERT INTO metrica (codigo, nombre, unidad, tipo_dato, es_lista) VALUES
        ('cpu_uso_pct',         'Uso de CPU',                '%', 'DECIMAL', false),
        ('memoria_uso_pct',     'Uso de memoria',            '%', 'DECIMAL', false),
        ('disco_uso_pct',       'Uso de disco',              '%', 'DECIMAL', false),
        ('carga_promedio',      'Carga promedio (15 minutos)', NULL, 'DECIMAL', false),
        ('uso_swap',            'Uso de SWAP',               '%', 'DECIMAL', false),
        ('puertos_escucha',     'Puertos en escucha',        NULL, 'INTEGER', true),
        ('servicios_ejecucion', 'Servicios en ejecución',    NULL, 'STRING',  true),
        ('usuarios_locales',    'Usuarios locales',          NULL, 'STRING',  true);

    INSERT INTO lista_referencia (nombre, tipo_dato, ignora_mayusculas) VALUES
        ('Puertos web',            'INTEGER', false),
        ('Puertos base de datos',  'INTEGER', false),
        ('Puertos producción',     'INTEGER', false),
        ('Servicios base',         'STRING',  true),
        ('Usuarios autorizados',   'STRING',  false);

    INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta)
    SELECT l.id_lista_referencia, d.valor, v_ahora
    FROM (VALUES ('Puertos web', '22'), ('Puertos web', '80'), ('Puertos web', '443'),
                 ('Puertos base de datos', '22'), ('Puertos base de datos', '5432'),
                 ('Puertos producción', '22'), ('Puertos producción', '443'),
                 ('Servicios base', 'crond'), ('Servicios base', 'rsyslog'), ('Servicios base', 'sshd'),
                 ('Usuarios autorizados', 'admin'), ('Usuarios autorizados', 'deploy'), ('Usuarios autorizados', 'root'))
         AS d(lista, valor)
    JOIN lista_referencia l ON l.nombre = d.lista;

    -- Un elemento retirado: conserva su periodo de vigencia
    INSERT INTO lista_referencia_item (id_lista_referencia, valor, fecha_alta, fecha_baja)
    SELECT id_lista_referencia, '8080', v_ahora - interval '30 days', v_ahora - interval '2 days'
    FROM lista_referencia WHERE nombre = 'Puertos web';

    -- Umbrales numéricos: el global y sus excepciones
    INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
    SELECT id_metrica, 'CPU alta', 'MAYOR', 80, 90 FROM metrica WHERE codigo = 'cpu_uso_pct'
    RETURNING id_umbral INTO v_umbral;
    INSERT INTO umbral_excepcion (id_umbral, id_entorno, valor_advertencia, valor_critico, motivo)
    SELECT v_umbral, id_entorno, 75, 85, 'Producción se atiende antes de saturarse' FROM entorno WHERE nombre = 'Producción';
    INSERT INTO umbral_excepcion (id_umbral, id_nivel_criticidad, valor_advertencia, valor_critico)
    SELECT v_umbral, id_nivel_criticidad, 85, 95 FROM nivel_criticidad WHERE nombre = 'Baja';
    INSERT INTO umbral_excepcion (id_umbral, id_grupo_mantenimiento, valor_advertencia, valor_critico, motivo)
    SELECT v_umbral, id_grupo_mantenimiento, 70, 80, 'La réplica asume la carga durante el mantenimiento del primario'
    FROM grupo_mantenimiento WHERE nombre = 'Grupo Base de Datos Producción';
    INSERT INTO umbral_excepcion (id_umbral, id_servidor, valor_advertencia, valor_critico, motivo)
    SELECT v_umbral, id_servidor, 85, 95, 'Procesos batch nocturnos de alto consumo' FROM servidor WHERE hostname = 'SERVPRODAPPV01';

    INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
    SELECT id_metrica, 'Memoria alta', 'MAYOR', 85, 95 FROM metrica WHERE codigo = 'memoria_uso_pct'
    RETURNING id_umbral INTO v_umbral;
    INSERT INTO umbral_excepcion (id_umbral, id_servidor, valor_advertencia, valor_critico, motivo)
    SELECT v_umbral, id_servidor, 90, 97, 'PostgreSQL reserva la memoria compartida al iniciar'
    FROM servidor WHERE hostname IN ('SERVPRODBDDV01', 'SERVPRODBDDV02');

    INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
    SELECT id_metrica, 'Disco lleno', 'MAYOR_IGUAL', 80, 90 FROM metrica WHERE codigo = 'disco_uso_pct'
    RETURNING id_umbral INTO v_umbral;
    INSERT INTO umbral_excepcion (id_umbral, id_nivel_criticidad, valor_advertencia, valor_critico)
    SELECT v_umbral, id_nivel_criticidad, 75, 85 FROM nivel_criticidad WHERE nombre = 'Alta';

    INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
    SELECT id_metrica, 'Carga elevada', 'MAYOR', 4, 10 FROM metrica WHERE codigo = 'carga_promedio';

    -- Solo crítico: la advertencia no se evalúa
    INSERT INTO umbral (id_metrica, nombre, operador, valor_advertencia, valor_critico)
    SELECT id_metrica, 'Uso de SWAP', 'MAYOR_IGUAL', NULL, 50 FROM metrica WHERE codigo = 'uso_swap';

    -- Umbrales de lista: la lista global y el nivel para faltantes y sobrantes
    INSERT INTO umbral (id_metrica, id_lista_referencia, nombre, nivel_faltante, nivel_sobrante)
    SELECT m.id_metrica, l.id_lista_referencia, 'Puertos esperados', 'CRITICO', 'ADVERTENCIA'
    FROM metrica m, lista_referencia l WHERE m.codigo = 'puertos_escucha' AND l.nombre = 'Puertos web'
    RETURNING id_umbral INTO v_umbral;
    INSERT INTO umbral_excepcion (id_umbral, id_grupo_mantenimiento, id_lista_referencia, motivo)
    SELECT v_umbral, g.id_grupo_mantenimiento, l.id_lista_referencia, 'Solo SSH y PostgreSQL'
    FROM grupo_mantenimiento g, lista_referencia l
    WHERE g.nombre = 'Grupo Base de Datos Producción' AND l.nombre = 'Puertos base de datos';
    INSERT INTO umbral_excepcion (id_umbral, id_servidor, id_lista_referencia, motivo)
    SELECT v_umbral, s.id_servidor, l.id_lista_referencia, 'Base de datos individual'
    FROM servidor s, lista_referencia l WHERE s.hostname = 'SERVPRODBDDV01' AND l.nombre = 'Puertos base de datos';
    INSERT INTO umbral_excepcion (id_umbral, id_entorno, id_lista_referencia)
    SELECT v_umbral, e.id_entorno, l.id_lista_referencia
    FROM entorno e, lista_referencia l WHERE e.nombre = 'Producción' AND l.nombre = 'Puertos producción';

    INSERT INTO umbral (id_metrica, id_lista_referencia, nombre, nivel_faltante, nivel_sobrante)
    SELECT m.id_metrica, l.id_lista_referencia, 'Servicios obligatorios', 'CRITICO', NULL
    FROM metrica m, lista_referencia l WHERE m.codigo = 'servicios_ejecucion' AND l.nombre = 'Servicios base';

    INSERT INTO umbral (id_metrica, id_lista_referencia, nombre, nivel_faltante, nivel_sobrante)
    SELECT m.id_metrica, l.id_lista_referencia, 'Usuarios autorizados', NULL, 'CRITICO'
    FROM metrica m, lista_referencia l WHERE m.codigo = 'usuarios_locales' AND l.nombre = 'Usuarios autorizados';
END $$;

COMMIT;
