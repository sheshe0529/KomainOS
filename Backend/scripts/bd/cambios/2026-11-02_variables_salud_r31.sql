-- Cambio 2026-11-02 · Variables de salud del R3.1 (DEC-48)
-- Aplica sobre una base en el modelo vFinal (cambios/2026-10-23). Después de aplicarlo, la base coincide con
-- 01_esquema.sql y 02_datos_sistema.sql:
--   1. metrica: descripción y sistemas operativos en los que se mide (aplica_linux, aplica_windows)
--   2. umbral_excepcion: nuevo alcance por sistema operativo (id_sistema_operativo), de menor prioridad que la criticidad
--   3. Las 21 variables del R3.1
--   4. Los umbrales de una métrica anterior equivalente (mismo significado, tipo y clase) pasan a la variable del R3.1
--      con sus excepciones y sus vínculos con tareas: por ejemplo, cpu_uso_pct → cpu_uso y puertos_escucha →
--      red_puerto_no_aut. Un umbral cuyo nombre ya exista en la variable se distingue con el código anterior
--   5. Catálogo de métricas: quedan solo las 21 variables. Las mediciones de las métricas anteriores se eliminan con
--      sus evaluaciones de umbral (las evaluaciones se conservan) y las métricas sin equivalente, con sus umbrales,
--      excepciones y vínculos con tareas. Las listas de referencia se conservan
--
-- Antes de aplicarlo, respalde la base:
--   pg_dump -h localhost -U postgres -d DBKomainOS -Fc -f respaldo_antes_r31.dump
-- Aplicación:
--   psql -h localhost -U postgres -d DBKomainOS -v ON_ERROR_STOP=1 -f scripts/bd/cambios/2026-11-02_variables_salud_r31.sql
-- Después reinicie el backend. Las versiones de los scripts de análisis que informan los códigos anteriores dejan de
-- servir en modo real (el simulado no las usa): publique una nueva versión con la plantilla del editor.
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'KomainOS' AND table_name = 'umbral_excepcion') THEN
        RAISE EXCEPTION 'La base no está en el modelo vFinal: aplique antes cambios/2026-10-23_modelo_relacional_vfinal.sql';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'KomainOS' AND table_name = 'metrica' AND column_name = 'aplica_linux') THEN
        RAISE EXCEPTION 'El cambio del R3.1 ya está aplicado';
    END IF;
END $$;

-- 1. Métrica: descripción y sistemas operativos
ALTER TABLE metrica
    ADD COLUMN descripcion character varying(500),
    ADD COLUMN aplica_linux boolean DEFAULT true NOT NULL,
    ADD COLUMN aplica_windows boolean DEFAULT true NOT NULL,
    ADD CONSTRAINT ck_metrica_sistema_operativo CHECK ((aplica_linux OR aplica_windows));

-- 2. Excepción de umbral por sistema operativo
ALTER TABLE umbral_excepcion ADD COLUMN id_sistema_operativo integer;
ALTER TABLE umbral_excepcion DROP CONSTRAINT ck_umbral_excepcion_alcance;
ALTER TABLE umbral_excepcion ADD CONSTRAINT ck_umbral_excepcion_alcance
    CHECK ((num_nonnulls(id_servidor, id_grupo_mantenimiento, id_entorno, id_nivel_criticidad, id_sistema_operativo) = 1));
ALTER TABLE umbral_excepcion DROP CONSTRAINT uq_umbral_excepcion_alcance;
ALTER TABLE umbral_excepcion ADD CONSTRAINT uq_umbral_excepcion_alcance UNIQUE NULLS NOT DISTINCT
    (id_umbral, id_servidor, id_grupo_mantenimiento, id_entorno, id_nivel_criticidad, id_sistema_operativo);
ALTER TABLE umbral_excepcion ADD CONSTRAINT fk_umbral_excepcion_sistema_operativo
    FOREIGN KEY (id_sistema_operativo) REFERENCES sistema_operativo(id_sistema_operativo) ON UPDATE RESTRICT ON DELETE RESTRICT;

-- 3. Las 21 variables del R3.1 (las que ya existieran con el mismo código se conservan)
INSERT INTO metrica (codigo, nombre, descripcion, unidad, tipo_dato, es_lista, aplica_linux, aplica_windows) VALUES
    ('cpu_uso', 'Uso de CPU',
     'Porcentaje del tiempo en que el procesador estuvo ocupado durante la ventana de muestreo.',
     '%', 'DECIMAL', false, true, true),
    ('cpu_load', 'Carga de CPU (load)',
     'Carga promedio del sistema durante 5 minutos, dividida entre el número de CPU lógicas.',
     'tareas por CPU lógica', 'DECIMAL', false, true, false),
    ('cpu_iowait', 'CPU en espera de E/S (iowait)',
     'Porcentaje del tiempo en que el procesador estuvo inactivo esperando operaciones de entrada y salida pendientes.',
     '%', 'DECIMAL', false, true, false),
    ('disco_iops', 'Operaciones de E/S por segundo',
     'Cantidad de operaciones de lectura y escritura realizadas en un segundo, por disco.',
     'op/s', 'DECIMAL', false, true, true),
    ('mem_uso', 'Uso de memoria',
     'Porcentaje de memoria utilizada por las aplicaciones y el sistema operativo, excluyendo la memoria que puede recuperarse para atender nuevas solicitudes.',
     '%', 'DECIMAL', false, true, true),
    ('swap_uso', 'Uso de espacio de intercambio',
     'Porcentaje ocupado del área de intercambio (swap) en Linux y del archivo de paginación en Windows (pagefile).',
     '%', 'DECIMAL', false, true, true),
    ('swap_act', 'Actividad de intercambio',
     'Cantidad de datos intercambiados entre la memoria física y el espacio de intercambio por segundo. En Windows, el valor se obtiene en páginas por segundo y se convierte a KiB/s.',
     'KiB/s', 'DECIMAL', false, true, true),
    ('disco_uso', 'Uso de disco',
     'Porcentaje de la capacidad de almacenamiento utilizada en cada volumen o sistema de archivos.',
     '%', 'DECIMAL', false, true, true),
    ('disco_latencia', 'Latencia de E/S de disco',
     'Tiempo promedio que tarda una operación de lectura o escritura al almacenamiento en completarse desde que se solicita, por disco.',
     'ms', 'DECIMAL', false, true, true),
    ('disco_errores', 'Errores de E/S de disco',
     'Cantidad de errores de lectura y escritura de los dispositivos de almacenamiento registrados por el sistema durante las últimas 24 horas.',
     'errores', 'INTEGER', false, true, true),
    ('red_error_rx', 'Porcentaje de errores de recepción de red',
     'Porcentaje de paquetes recibidos con errores respecto al total de paquetes recibidos durante un intervalo de medición, por interfaz de red.',
     '%', 'DECIMAL', false, true, true),
    ('aut_fallidas', 'Autenticaciones fallidas',
     'Cantidad de intentos de inicio de sesión fallidos durante un intervalo de medición.',
     'intentos', 'INTEGER', false, true, true),
    ('admin_acceso', 'Acceso directo del administrador',
     'Vale 1 cuando el administrador puede iniciar sesión de forma directa o cuando la cuenta de administrador integrada está habilitada. Vale 0 cuando ambos accesos se encuentran deshabilitados.',
     NULL, 'BOOLEAN', false, true, true),
    ('admin_no_aut', 'Cuentas administradoras no autorizadas',
     'Cuentas habilitadas con privilegios administrativos que no figuran en la lista de cuentas o grupos autorizados. El análisis informa las cuentas con privilegios administrativos y las que sobran respecto de la lista son las no autorizadas.',
     NULL, 'STRING', true, true, true),
    ('arch_crit_permisos', 'Archivos críticos con permisos inseguros',
     'Archivos críticos de autenticación y de configuración con permisos que incumplen la política de permisos. El análisis informa cada archivo con su permiso octal (ruta:modo) y la lista define el permiso que exige la política: los que faltan son los archivos que la incumplen.',
     NULL, 'STRING', true, true, false),
    ('red_puerto_no_aut', 'Puertos TCP no autorizados en escucha',
     'Puertos TCP en escucha que no están comprendidos en la lista de puertos autorizados. El análisis informa los puertos en escucha y los que sobran respecto de la lista son los no autorizados.',
     NULL, 'INTEGER', true, true, true),
    ('firewall_inactivo', 'Firewall del sistema inactivo',
     'Vale 1 cuando el cortafuegos local requerido para el servidor se encuentra inactivo o deshabilitado. Vale 0 cuando se encuentra activo y habilitado.',
     NULL, 'BOOLEAN', false, true, true),
    ('mac_no_estricto', 'Control de acceso obligatorio no estricto',
     'Vale 1 cuando el mecanismo de control de acceso obligatorio requerido (SELinux o AppArmor) está deshabilitado o no aplica estrictamente sus políticas de seguridad. Vale 0 cuando aplica las restricciones requeridas.',
     NULL, 'BOOLEAN', false, true, false),
    ('uptime_dias', 'Tiempo de actividad (uptime)',
     'Días transcurridos desde el último arranque del servidor.',
     'días', 'DECIMAL', false, true, true),
    ('serv_no_operativo', 'Servicios requeridos no operativos',
     'Servicios incluidos en la lista de servicios requeridos que no se encuentran en su estado operativo esperado. El análisis informa los servicios en ejecución y los que faltan respecto de la lista son los no operativos.',
     NULL, 'STRING', true, true, true),
    ('mem_agotamiento', 'Eventos de agotamiento de memoria',
     'Cantidad de eventos de agotamiento de memoria registrados durante un intervalo de medición: en Linux, los procesos finalizados por el OOM killer; en Windows, los eventos 2004 del Resource-Exhaustion-Detector.',
     'eventos', 'INTEGER', false, true, true)
ON CONFLICT (codigo) DO NOTHING;

-- 4. Umbrales de las métricas anteriores equivalentes: los códigos de los ejemplos del sistema y los registrados en
--    DBKomainOS. Solo si coinciden el tipo de dato y la clase (valor o lista)
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT a.id_metrica AS anterior, a.codigo AS codigo_anterior, n.id_metrica AS nueva, n.codigo AS codigo_nuevo
        FROM (VALUES ('cpu_uso_pct', 'cpu_uso'), ('uso_cpu', 'cpu_uso'), ('memoria_uso_pct', 'mem_uso'),
                     ('disco_uso_pct', 'disco_uso'), ('uso_swap', 'swap_uso'), ('puertos_escucha', 'red_puerto_no_aut'),
                     ('puertos_permitidos', 'red_puerto_no_aut'), ('servicios_ejecucion', 'serv_no_operativo'))
             AS e(anterior, nueva)
        JOIN metrica a ON a.codigo = e.anterior
        JOIN metrica n ON n.codigo = e.nueva
        WHERE a.tipo_dato = n.tipo_dato AND a.es_lista = n.es_lista
        ORDER BY a.id_metrica
    LOOP
        UPDATE umbral u SET nombre = left(u.nombre || ' (' || r.codigo_anterior || ')', 255)
        WHERE u.id_metrica = r.anterior
          AND EXISTS (SELECT 1 FROM umbral o WHERE o.id_metrica = r.nueva AND o.nombre = u.nombre);
        UPDATE umbral SET id_metrica = r.nueva, fecha_actualizacion = statement_timestamp() AT TIME ZONE 'UTC'
        WHERE id_metrica = r.anterior;
        IF FOUND THEN
            RAISE NOTICE 'Umbrales de % pasan a %', r.codigo_anterior, r.codigo_nuevo;
        END IF;
    END LOOP;
END $$;

-- 5. Solo las variables del R3.1: las mediciones arrastran sus evaluaciones de umbral y sus elementos de lista, y las
--    métricas sin equivalente, sus umbrales con excepciones y vínculos con tareas
DELETE FROM medicion_metrica WHERE id_metrica IN (SELECT id_metrica FROM metrica WHERE codigo NOT IN (
    'cpu_uso', 'cpu_load', 'cpu_iowait', 'disco_iops', 'mem_uso', 'swap_uso', 'swap_act', 'disco_uso',
    'disco_latencia', 'disco_errores', 'red_error_rx', 'aut_fallidas', 'admin_acceso', 'admin_no_aut',
    'arch_crit_permisos', 'red_puerto_no_aut', 'firewall_inactivo', 'mac_no_estricto', 'uptime_dias',
    'serv_no_operativo', 'mem_agotamiento'));
DELETE FROM metrica WHERE codigo NOT IN (
    'cpu_uso', 'cpu_load', 'cpu_iowait', 'disco_iops', 'mem_uso', 'swap_uso', 'swap_act', 'disco_uso',
    'disco_latencia', 'disco_errores', 'red_error_rx', 'aut_fallidas', 'admin_acceso', 'admin_no_aut',
    'arch_crit_permisos', 'red_puerto_no_aut', 'firewall_inactivo', 'mac_no_estricto', 'uptime_dias',
    'serv_no_operativo', 'mem_agotamiento');

DO $$
BEGIN
    IF (SELECT count(*) FROM metrica) <> 21 THEN
        RAISE EXCEPTION 'El catálogo de métricas no quedó con las 21 variables del R3.1';
    END IF;
END $$;

COMMIT;
