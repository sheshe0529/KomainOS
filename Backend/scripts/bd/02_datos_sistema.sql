-- 02 - Datos que el sistema necesita para operar (DEC-21, DEC-34). Es idempotente

-- RF64: factor que ajusta la periodicidad base según el resultado del ciclo
INSERT INTO "KomainOS".factor_ciclo (resultado, factor) VALUES
    ('INCIDENCIA',    0.5000),
    ('SALUD_REGULAR', 0.7500),
    ('ESTADO_NORMAL', 1.0000),
    ('RACHA_ESTABLE', 1.2500)
ON CONFLICT (resultado) DO NOTHING;

-- Valores iniciales editables por el administrador: concurrencia, duraciones máximas (RF68), racha estable y vigencia del token
INSERT INTO "KomainOS".configuracion_sistema (
    id_configuracion_sistema,
    id_cuenta_servicio_predeterminada,
    max_ejecuciones_concurrentes,
    max_duracion_tarea_minutos,
    max_duracion_mop_minutos,
    min_ciclos_racha_estable,
    minutos_expiracion_token
) VALUES (1, NULL, 10, 60, 240, 5, 60)
ON CONFLICT (id_configuracion_sistema) DO NOTHING;

-- R3.1: variables para evaluar el estado de salud del servidor virtual. El código es la clave técnica que informa el
-- script de análisis; las de umbral se comparan con un límite, las binarias valen 1 cuando se incumple la configuración
-- esperada y las de lista se contrastan con una lista de referencia. Las mismas que VariablesSalud.java
-- so_aplica: la familia en la que se mide; nula si se mide en Linux y en Windows
INSERT INTO "KomainOS".metrica (codigo, nombre, descripcion, unidad, tipo_dato, es_lista, so_aplica) VALUES
    ('cpu_uso', 'Uso de CPU',
     'Porcentaje del tiempo en que el procesador estuvo ocupado durante la ventana de muestreo.',
     '%', 'DECIMAL', false, NULL),
    ('cpu_load', 'Carga de CPU (load)',
     'Carga promedio del sistema durante 5 minutos, dividida entre el número de CPU lógicas.',
     'tareas por CPU lógica', 'DECIMAL', false, 'LINUX'),
    ('cpu_iowait', 'CPU en espera de E/S (iowait)',
     'Porcentaje del tiempo en que el procesador estuvo inactivo esperando operaciones de entrada y salida pendientes.',
     '%', 'DECIMAL', false, 'LINUX'),
    ('disco_iops', 'Operaciones de E/S por segundo',
     'Cantidad de operaciones de lectura y escritura realizadas en un segundo, por disco.',
     'op/s', 'DECIMAL', false, NULL),
    ('mem_uso', 'Uso de memoria',
     'Porcentaje de memoria utilizada por las aplicaciones y el sistema operativo, excluyendo la memoria que puede recuperarse para atender nuevas solicitudes.',
     '%', 'DECIMAL', false, NULL),
    ('swap_uso', 'Uso de espacio de intercambio',
     'Porcentaje ocupado del área de intercambio (swap) en Linux y del archivo de paginación en Windows (pagefile).',
     '%', 'DECIMAL', false, NULL),
    ('swap_act', 'Actividad de intercambio',
     'Cantidad de datos intercambiados entre la memoria física y el espacio de intercambio por segundo. En Windows, el valor se obtiene en páginas por segundo y se convierte a KiB/s.',
     'KiB/s', 'DECIMAL', false, NULL),
    ('disco_uso', 'Uso de disco',
     'Porcentaje de la capacidad de almacenamiento utilizada en cada volumen o sistema de archivos.',
     '%', 'DECIMAL', false, NULL),
    ('disco_latencia', 'Latencia de E/S de disco',
     'Tiempo promedio que tarda una operación de lectura o escritura al almacenamiento en completarse desde que se solicita, por disco.',
     'ms', 'DECIMAL', false, NULL),
    ('disco_errores', 'Errores de E/S de disco',
     'Cantidad de errores de lectura y escritura de los dispositivos de almacenamiento registrados por el sistema durante las últimas 24 horas.',
     'errores', 'INTEGER', false, NULL),
    ('red_error_rx', 'Porcentaje de errores de recepción de red',
     'Porcentaje de paquetes recibidos con errores respecto al total de paquetes recibidos durante un intervalo de medición, por interfaz de red.',
     '%', 'DECIMAL', false, NULL),
    ('aut_fallidas', 'Autenticaciones fallidas',
     'Cantidad de intentos de inicio de sesión fallidos durante un intervalo de medición.',
     'intentos', 'INTEGER', false, NULL),
    ('admin_acceso', 'Acceso directo del administrador',
     'Vale 1 cuando el administrador puede iniciar sesión de forma directa o cuando la cuenta de administrador integrada está habilitada. Vale 0 cuando ambos accesos se encuentran deshabilitados.',
     NULL, 'BOOLEAN', false, NULL),
    ('admin_no_aut', 'Cuentas administradoras no autorizadas',
     'Cuentas habilitadas con privilegios administrativos que no figuran en la lista de cuentas o grupos autorizados. El análisis informa las cuentas con privilegios administrativos y las que sobran respecto de la lista son las no autorizadas.',
     NULL, 'STRING', true, NULL),
    ('arch_crit_permisos', 'Archivos críticos con permisos inseguros',
     'Archivos críticos de autenticación y de configuración con permisos que incumplen la política de permisos. El análisis informa cada archivo con su permiso octal (ruta:modo) y la lista define el permiso que exige la política: los que faltan son los archivos que la incumplen.',
     NULL, 'STRING', true, 'LINUX'),
    ('red_puerto_no_aut', 'Puertos TCP no autorizados en escucha',
     'Puertos TCP en escucha que no están comprendidos en la lista de puertos autorizados. El análisis informa los puertos en escucha y los que sobran respecto de la lista son los no autorizados.',
     NULL, 'INTEGER', true, NULL),
    ('firewall_inactivo', 'Firewall del sistema inactivo',
     'Vale 1 cuando el cortafuegos local requerido para el servidor se encuentra inactivo o deshabilitado. Vale 0 cuando se encuentra activo y habilitado.',
     NULL, 'BOOLEAN', false, NULL),
    ('mac_no_estricto', 'Control de acceso obligatorio no estricto',
     'Vale 1 cuando el mecanismo de control de acceso obligatorio requerido (SELinux o AppArmor) está deshabilitado o no aplica estrictamente sus políticas de seguridad. Vale 0 cuando aplica las restricciones requeridas.',
     NULL, 'BOOLEAN', false, 'LINUX'),
    ('uptime_dias', 'Tiempo de actividad (uptime)',
     'Días transcurridos desde el último arranque del servidor.',
     'días', 'DECIMAL', false, NULL),
    ('serv_no_operativo', 'Servicios requeridos no operativos',
     'Servicios incluidos en la lista de servicios requeridos que no se encuentran en su estado operativo esperado. El análisis informa los servicios en ejecución y los que faltan respecto de la lista son los no operativos.',
     NULL, 'STRING', true, NULL),
    ('mem_agotamiento', 'Eventos de agotamiento de memoria',
     'Cantidad de eventos de agotamiento de memoria registrados durante un intervalo de medición: en Linux, los procesos finalizados por el OOM killer; en Windows, los eventos 2004 del Resource-Exhaustion-Detector.',
     'eventos', 'INTEGER', false, NULL)
ON CONFLICT (codigo) DO NOTHING;
