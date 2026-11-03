package com.komainos.inventario.model;

import java.util.List;
import java.util.Optional;

/**
 * Variables para evaluar el estado de salud del servidor virtual (R3.1): las 21 métricas que informan los scripts de
 * análisis. Las de umbral se comparan con un límite; las binarias valen 1 cuando se incumple la configuración esperada;
 * las de lista informan los elementos encontrados y el sistema los contrasta con una lista de referencia. El mismo
 * catálogo se carga en la base con 02_datos_sistema.sql.
 */
public final class VariablesSalud {

    /** Una variable con su clave técnica, que es el código que informa el script */
    public record Variable(String clave, Metrica.Definicion definicion) {
    }

    public static final List<Variable> TODAS = List.of(
            linuxYWindows("cpu_uso", "Uso de CPU",
                    "Porcentaje del tiempo en que el procesador estuvo ocupado durante la ventana de muestreo.",
                    "%", TipoDato.DECIMAL),
            soloLinux("cpu_load", "Carga de CPU (load)",
                    "Carga promedio del sistema durante 5 minutos, dividida entre el número de CPU lógicas.",
                    "tareas por CPU lógica", TipoDato.DECIMAL, false),
            soloLinux("cpu_iowait", "CPU en espera de E/S (iowait)",
                    "Porcentaje del tiempo en que el procesador estuvo inactivo esperando operaciones de entrada y salida "
                            + "pendientes.",
                    "%", TipoDato.DECIMAL, false),
            linuxYWindows("disco_iops", "Operaciones de E/S por segundo",
                    "Cantidad de operaciones de lectura y escritura realizadas en un segundo, por disco.",
                    "op/s", TipoDato.DECIMAL),
            linuxYWindows("mem_uso", "Uso de memoria",
                    "Porcentaje de memoria utilizada por las aplicaciones y el sistema operativo, excluyendo la memoria "
                            + "que puede recuperarse para atender nuevas solicitudes.",
                    "%", TipoDato.DECIMAL),
            linuxYWindows("swap_uso", "Uso de espacio de intercambio",
                    "Porcentaje ocupado del área de intercambio (swap) en Linux y del archivo de paginación en Windows "
                            + "(pagefile).",
                    "%", TipoDato.DECIMAL),
            linuxYWindows("swap_act", "Actividad de intercambio",
                    "Cantidad de datos intercambiados entre la memoria física y el espacio de intercambio por segundo. En "
                            + "Windows, el valor se obtiene en páginas por segundo y se convierte a KiB/s.",
                    "KiB/s", TipoDato.DECIMAL),
            linuxYWindows("disco_uso", "Uso de disco",
                    "Porcentaje de la capacidad de almacenamiento utilizada en cada volumen o sistema de archivos.",
                    "%", TipoDato.DECIMAL),
            linuxYWindows("disco_latencia", "Latencia de E/S de disco",
                    "Tiempo promedio que tarda una operación de lectura o escritura al almacenamiento en completarse "
                            + "desde que se solicita, por disco.",
                    "ms", TipoDato.DECIMAL),
            linuxYWindows("disco_errores", "Errores de E/S de disco",
                    "Cantidad de errores de lectura y escritura de los dispositivos de almacenamiento registrados por el "
                            + "sistema durante las últimas 24 horas.",
                    "errores", TipoDato.INTEGER),
            linuxYWindows("red_error_rx", "Porcentaje de errores de recepción de red",
                    "Porcentaje de paquetes recibidos con errores respecto al total de paquetes recibidos durante un "
                            + "intervalo de medición, por interfaz de red.",
                    "%", TipoDato.DECIMAL),
            linuxYWindows("aut_fallidas", "Autenticaciones fallidas",
                    "Cantidad de intentos de inicio de sesión fallidos durante un intervalo de medición.",
                    "intentos", TipoDato.INTEGER),
            linuxYWindows("admin_acceso", "Acceso directo del administrador",
                    "Vale 1 cuando el administrador puede iniciar sesión de forma directa o cuando la cuenta de "
                            + "administrador integrada está habilitada. Vale 0 cuando ambos accesos se encuentran "
                            + "deshabilitados.",
                    null, TipoDato.BOOLEAN),
            lista("admin_no_aut", "Cuentas administradoras no autorizadas",
                    "Cuentas habilitadas con privilegios administrativos que no figuran en la lista de cuentas o grupos "
                            + "autorizados. El análisis informa las cuentas con privilegios administrativos y las que "
                            + "sobran respecto de la lista son las no autorizadas.",
                    TipoDato.STRING, true),
            lista("arch_crit_permisos", "Archivos críticos con permisos inseguros",
                    "Archivos críticos de autenticación y de configuración con permisos que incumplen la política de "
                            + "permisos. El análisis informa cada archivo con su permiso octal (ruta:modo) y la lista "
                            + "define el permiso que exige la política: los que faltan son los archivos que la incumplen.",
                    TipoDato.STRING, false),
            lista("red_puerto_no_aut", "Puertos TCP no autorizados en escucha",
                    "Puertos TCP en escucha que no están comprendidos en la lista de puertos autorizados. El análisis "
                            + "informa los puertos en escucha y los que sobran respecto de la lista son los no "
                            + "autorizados.",
                    TipoDato.INTEGER, true),
            linuxYWindows("firewall_inactivo", "Firewall del sistema inactivo",
                    "Vale 1 cuando el cortafuegos local requerido para el servidor se encuentra inactivo o deshabilitado. "
                            + "Vale 0 cuando se encuentra activo y habilitado.",
                    null, TipoDato.BOOLEAN),
            soloLinux("mac_no_estricto", "Control de acceso obligatorio no estricto",
                    "Vale 1 cuando el mecanismo de control de acceso obligatorio requerido (SELinux o AppArmor) está "
                            + "deshabilitado o no aplica estrictamente sus políticas de seguridad. Vale 0 cuando aplica "
                            + "las restricciones requeridas.",
                    null, TipoDato.BOOLEAN, false),
            linuxYWindows("uptime_dias", "Tiempo de actividad (uptime)",
                    "Días transcurridos desde el último arranque del servidor.", "días", TipoDato.DECIMAL),
            lista("serv_no_operativo", "Servicios requeridos no operativos",
                    "Servicios incluidos en la lista de servicios requeridos que no se encuentran en su estado operativo "
                            + "esperado. El análisis informa los servicios en ejecución y los que faltan respecto de la "
                            + "lista son los no operativos.",
                    TipoDato.STRING, true),
            linuxYWindows("mem_agotamiento", "Eventos de agotamiento de memoria",
                    "Cantidad de eventos de agotamiento de memoria registrados durante un intervalo de medición: en "
                            + "Linux, los procesos finalizados por el OOM killer; en Windows, los eventos 2004 del "
                            + "Resource-Exhaustion-Detector.",
                    "eventos", TipoDato.INTEGER));

    private VariablesSalud() {
    }

    public static Optional<Variable> de(String clave) {
        return TODAS.stream().filter(v -> v.clave().equals(clave)).findFirst();
    }

    private static Variable linuxYWindows(String clave, String nombre, String descripcion, String unidad, TipoDato tipo) {
        return new Variable(clave, new Metrica.Definicion(nombre, descripcion, unidad, tipo, false, null));
    }

    private static Variable soloLinux(String clave, String nombre, String descripcion, String unidad, TipoDato tipo,
                                      boolean esLista) {
        return new Variable(clave, new Metrica.Definicion(nombre, descripcion, unidad, tipo, esLista,
                FamiliaSistemaOperativo.LINUX));
    }

    /** Las de lista no llevan unidad: se cuentan los elementos que sobran o faltan respecto de la referencia */
    private static Variable lista(String clave, String nombre, String descripcion, TipoDato tipo, boolean enWindows) {
        return new Variable(clave, new Metrica.Definicion(nombre, descripcion, null, tipo, true,
                enWindows ? null : FamiliaSistemaOperativo.LINUX));
    }
}
