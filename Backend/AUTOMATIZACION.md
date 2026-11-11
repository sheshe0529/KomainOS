# Automatización y ejecución — objetivo 3

El componente implementa el ciclo de R2.1/R2.3/R2.4: revisión periódica, evaluación
previa, MOP, autorización, cola, ejecución, validaciones, incidencias y cierre.
Reutiliza las tablas del esquema relacional. Las bases anteriores se actualizan con los scripts fechados de
`scripts/bd/cambios`; una base nueva usa `01_esquema.sql` (DEC-34).

Cada script se ejecuta con `ansible-playbook` desde un controlador Linux: SSH para Linux y WinRM para Windows, sin
agentes. No hay un modo simulado (DEC-56): la imagen Docker del despliegue ([DESPLIEGUE.md](../DESPLIEGUE.md)) trae
Java 21, `ansible-core`, `pywinrm` y `sshpass`. En Windows el backend arranca igual, para trabajar en el panel, pero
cada operación remota falla como «Configuración del controlador» sin descifrar ninguna cuenta.

El estado del controlador se ve en **Automatización → Sala de operaciones** (barra superior desplegable) y en
`GET /api/automatizacion/modo`.

## Cuentas por finalidad

Cada servidor o grupo puede definir una cuenta para **evaluación** y otra para **mantenimiento**, con predeterminadas
independientes. Pueden ser la misma cuenta. Las revisiones y los análisis previo y posterior usan la de evaluación;
la prueba de acceso antes de ejecutar y las tareas de la MOP usan la de mantenimiento.

Las cuentas efectivas se guardan en la orden al programarla y una cuenta ya guardada no cambia cuando se modifica
la configuración. Si una orden se creó cuando todavía faltaban cuentas, solo los campos nulos pueden completarse
desde la configuración vigente del servidor/grupo o su predeterminada, inmediatamente antes de la primera evaluación.
Se comprueban ambas cuentas antes de cambiar el estado o crear análisis. Si falta alguna o no es asignable, la orden
sigue programada sin fijar valores parciales. La auditoría registra los valores antes y después de esa preparación.
Los ciclos históricos con cuenta desconocida conservan el nulo; la migración no les atribuye la predeterminada actual.

La prueba de acceso de evaluación verifica autenticación sin exigir administrador; el script de análisis debe tener
los permisos que requieren sus métricas. La prueba de mantenimiento conserva la comprobación de privilegios.

## Variables de entorno

| Variable | Predeterminado | Uso |
|---|---|---|
| `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO` | `true` (`false` en el perfil `dev`) | `false` deja solo los botones del panel |
| `KOMAINOS_AUTOMATIZACION_INTERVALO_MS` | `30000` | Cada cuánto corre el proceso automático |
| `KOMAINOS_AUTOMATIZACION_MAX_CONEXIONES` | `10` | Ejecuciones remotas simultáneas del controlador |
| `KOMAINOS_ANSIBLE_EJECUTABLE` | `ansible-playbook` | Ruta del ejecutable si no está en el `PATH` |
| `KOMAINOS_ANSIBLE_TIMEOUT_CONEXION` | `30` | Segundos para establecer la conexión |
| `KOMAINOS_ANSIBLE_SSH_PUERTO` | `22` | Puerto SSH de los servidores Linux |
| `KOMAINOS_ANSIBLE_SSH_VERIFICAR_CLAVE_HOST` | `true` | Exige la clave del host en `known_hosts` |
| `KOMAINOS_ANSIBLE_SSH_KNOWN_HOSTS` | (el del usuario) | Archivo `known_hosts` propio del controlador |
| `KOMAINOS_ANSIBLE_WINRM_PUERTO` | `5986` | Puerto WinRM |
| `KOMAINOS_ANSIBLE_WINRM_ESQUEMA` | `https` | `https` o `http` |
| `KOMAINOS_ANSIBLE_WINRM_TRANSPORTE` | `ntlm` | `ntlm`, `kerberos`, `credssp` o `basic` |
| `KOMAINOS_ANSIBLE_WINRM_VALIDAR_CERTIFICADO` | `true` | Valida el certificado HTTPS de WinRM |

El perfil `dev` deja el proceso automático desactivado porque en Windows cada operación remota fallaría.

## Doble del adaptador en las pruebas

Las pruebas de integración no se conectan a servidores: `PruebaIntegracion` reemplaza el adaptador por
`AnsibleSimulado`, que solo existe en `src/test` y nunca llega al JAR desplegado. Para cada métrica que exige un umbral
activo del servidor y que se mide en su sistema operativo calcula un valor normal, de advertencia o crítico según los
límites que rigen a ese servidor en el contexto de su evaluación (con sus excepciones, incluida la del grupo de la
orden). Así las pruebas funcionan con cualquier catálogo de métricas y umbrales.

| Escenario | Revisión periódica | Evaluación previa | Tareas | Validación técnica |
|---|---|---|---|---|
| `ESTANDAR` (predeterminado) | normal | advertencia → genera MOP | correctas | normal |
| `SALUDABLE` | normal | normal → cierra sin intervenir | — | — |
| `ADVERTENCIA` | advertencia → solo alerta | advertencia | correctas | normal |
| `CRITICO` | crítico → orden de prioridad alta | crítico | correctas | normal |
| `NO_CONFORME` | normal | advertencia | correctas | advertencia → incidencia |
| `FALLO_TAREA` | normal | advertencia | la tarea falla → incidencia | — |
| `SIN_CONEXION` / `SIN_AUTENTICACION` / `SIN_PERMISOS` | falla con ese tipo de error (RF71) | | | |

Se asignan por hostname, sin distinguir mayúsculas, en `src/test/resources/application-test.yml`:

```yaml
komainos:
  pruebas:
    escenarios:
      srv-fallo: FALLO_TAREA
      srv-sin-autenticacion: SIN_AUTENTICACION
```

## Variables de salud (R3.1)

El catálogo de métricas son las 21 variables del documento R3.1
(`Documentos/R3_1_Lista_de_variables_corregido`). Se cargan con
`02_datos_sistema.sql` y están en `VariablesSalud.java`; el código es la clave
técnica que informa el script de análisis. Cada métrica indica en qué sistemas
operativos se mide: en los demás no se exige, no se evalúa ni admite excepciones.

| Código | Variable | Unidad | Activación | Se mide en |
|---|---|---|---|---|
| `cpu_uso` | Uso de CPU | % | Umbral | Linux y Windows |
| `cpu_load` | Carga de CPU (load) | tareas por CPU lógica | Umbral | Linux |
| `cpu_iowait` | CPU en espera de E/S (iowait) | % | Umbral | Linux |
| `disco_iops` | Operaciones de E/S por segundo (por disco) | op/s | Umbral | Linux y Windows |
| `mem_uso` | Uso de memoria | % | Umbral | Linux y Windows |
| `swap_uso` | Uso de espacio de intercambio | % | Umbral | Linux y Windows |
| `swap_act` | Actividad de intercambio | KiB/s | Umbral | Linux y Windows |
| `disco_uso` | Uso de disco (por volumen) | % | Umbral | Linux y Windows |
| `disco_latencia` | Latencia de E/S de disco (por disco) | ms | Umbral | Linux y Windows |
| `disco_errores` | Errores de E/S de disco | errores | Umbral | Linux y Windows |
| `red_error_rx` | Porcentaje de errores de recepción de red (por interfaz) | % | Umbral | Linux y Windows |
| `aut_fallidas` | Autenticaciones fallidas | intentos | Umbral | Linux y Windows |
| `admin_acceso` | Acceso directo del administrador | binaria | Umbral (= 1) | Linux y Windows |
| `admin_no_aut` | Cuentas administradoras no autorizadas | — | Lista (sobrantes) | Linux y Windows |
| `arch_crit_permisos` | Archivos críticos con permisos inseguros | — | Lista (faltantes) | Linux |
| `red_puerto_no_aut` | Puertos TCP no autorizados en escucha | — | Lista (sobrantes) | Linux y Windows |
| `firewall_inactivo` | Firewall del sistema inactivo | binaria | Umbral (= 1) | Linux y Windows |
| `mac_no_estricto` | Control de acceso obligatorio no estricto | binaria | Umbral (= 1) | Linux |
| `uptime_dias` | Tiempo de actividad (uptime) | días | Umbral | Linux y Windows |
| `serv_no_operativo` | Servicios requeridos no operativos | — | Lista (faltantes) | Linux y Windows |
| `mem_agotamiento` | Eventos de agotamiento de memoria | eventos | Umbral | Linux y Windows |

- **Binarias.** Valen 1 cuando se incumple la configuración esperada; su umbral
  usa `=` o `≠` con un solo nivel (advertencia o crítico).
- **Listas.** El script informa lo que encuentra y el sistema lo contrasta con la
  lista de referencia del umbral: las cuentas administradoras y los puertos que
  **sobran** son los no autorizados; los servicios requeridos que **faltan** son
  los no operativos. En `arch_crit_permisos` el script informa cada archivo como
  `ruta:modo` (permiso octal, por ejemplo `/etc/shadow:640`) y la lista tiene el
  par que exige la política: el que falta es el archivo que la incumple.
- **Por sistema operativo.** Los nombres de servicios, cuentas y permisos cambian
  entre distribuciones (`ssh`/`sshd`, `cron`/`crond`, `/etc/shadow` 640 en Ubuntu y
  000 en RHEL). Por eso las excepciones de umbral tienen un quinto alcance,
  **Sistema operativo**, de menor prioridad que la criticidad: servidor > grupo >
  entorno > criticidad > sistema operativo > global.
- **Umbrales iniciales.** `04_umbrales_variables.sql` deja un umbral global por
  variable (numérico, binario o de lista), las listas de referencia de base y las
  excepciones por sistema operativo de Windows Server y la familia de RHEL; no
  modifica lo existente y se vuelve a ejecutar al registrar un sistema operativo.
  `05_datos_prueba_umbrales.sql` agrega ejemplos sobre los servidores de prueba.
- **Límites.** Los del `04` son genéricos (por ejemplo, CPU ≥ 80/90 %, carga > 1/2 tareas por
  CPU, uptime ≥ 90/180 días) y se ajustan en **Umbrales**.

Cómo las obtienen los scripts de referencia del R3.2, en `Documentos/Scripts/` (carpeta de documentos del proyecto, fuera del repositorio):

| Variable | Linux (`analisis_linux.sh`) | Windows (`analisis_windows.ps1`) |
|---|---|---|
| CPU, iowait | `/proc/stat` en dos lecturas | `Win32_PerfRawData_PerfOS_Processor` en dos lecturas |
| Carga | `/proc/loadavg` (5 min) entre `nproc` | — |
| IOPS y latencia | `/proc/diskstats` por disco (sin `dm-*`, `loop`, `md`) | `Win32_PerfRawData_PerfDisk_PhysicalDisk` |
| Memoria | `MemAvailable` de `/proc/meminfo` | `AvailableBytes` de `PerfOS_Memory` |
| Intercambio | `SwapTotal`/`SwapFree`; `pswpin`+`pswpout` de `/proc/vmstat` | `Win32_PageFileUsage`; `PagesPersec` × tamaño de página |
| Disco | `df` por punto de montaje (sin sistemas en memoria) | `Win32_LogicalDisk` locales |
| Errores de disco | registro del núcleo (`journalctl -k`) | eventos `disk` y `Ntfs` del registro System |
| Red | `/sys/class/net/*/statistics` por interfaz | `Get-NetAdapterStatistics` |
| Autenticaciones | registros auth y authpriv (sshd y PAM) | evento 4625 del registro Security |
| Administrador | `PermitRootLogin` (`sshd -T`) o contraseña de root utilizable | cuenta integrada (RID 500) habilitada |
| Cuentas administradoras | uid 0 y miembros de sudo, wheel o admin con shell | miembros del grupo S-1-5-32-544 |
| Puertos | `ss -lnt` sin la interfaz de bucle local | `Get-NetTCPConnection -State Listen` |
| Firewall | firewalld, ufw, nftables o iptables | servicio MpsSvc y perfiles efectivos |
| MAC | SELinux en Enforcing o AppArmor con perfiles en enforce | — |
| Servicios | `systemctl` en ejecución | `Get-Service` en ejecución |
| Agotamiento de memoria | `Killed process` del OOM killer | evento 2004 del Resource-Exhaustion-Detector |

Parámetros del análisis: `MUESTREO_SEGUNDOS` (5) y `VENTANA_HORAS` (24). Requieren
privilegios de administrador.

**Listas como parámetros explícitos (RF25).** En **Agregar parámetro**, seleccione el tipo **Lista**,
el nombre que leerá el script y una lista de referencia del catálogo. KomainOS resuelve sus elementos
vigentes al preparar el archivo y genera un arreglo Bash o PowerShell seguro. No copia valores a la
versión ni deduce listas de los umbrales: una excepción de umbral cambia la comparación, no la entrada
elegida por el script. Para Linux, declare `ARCHIVOS_CRITICOS` y seleccione la política de archivos que
corresponda al alcance que quiere inspeccionar. Puertos y cuentas se descubren directamente en el servidor.

```bash
ARCHIVOS_CRITICOS=('/etc/passwd:644' '/etc/shadow:0')   # Bash
$ARCHIVOS_CRITICOS = @('/etc/passwd:644', '/etc/shadow:0')  # PowerShell
```

Las versiones publicadas antes de `2026-11-06_parametros_listas.sql` conservan la inyección automática
`KOMAINOS_LISTA_<CÓDIGO>` mediante `script_version.listas_explicitas = false`. Su código y parámetros no
se reescriben. Las versiones nuevas usan únicamente parámetros declarados; al partir de una versión
anterior, adapte el nombre de la variable y declare la lista. Los escalares históricos vacíos conservan
su comportamiento (`''` en Bash y `$null` en PowerShell), pero no se permiten en publicaciones nuevas.

Una base creada antes de este cambio se actualiza con
`scripts/bd/cambios/2026-11-02_variables_salud_r31.sql` (respaldo previo con
`pg_dump`): agrega las columnas y deja solo las 21 variables. Los umbrales de una
métrica anterior equivalente (`cpu_uso_pct` → `cpu_uso`, `puertos_escucha` →
`red_puerto_no_aut`…) pasan a la variable con sus excepciones y tareas; las
mediciones anteriores y las métricas sin equivalente se eliminan. Después hay que
reiniciar el backend y publicar una versión del análisis que
informe los códigos nuevos (el de `Documentos/Scripts/` ya lo hace).

## Recorrido de prueba desde el panel

1. **Datos iniciales**: `scripts/bd/instalar_base.sql` (o `02_datos_sistema.sql`)
   registra las 21 variables del R3.1, sin umbrales: se registran en **Umbrales** o,
   para partir de uno genérico por variable, con `04_umbrales_variables.sql`. Las reglas de aprobación se definen en **Configuración →
   Políticas de cambio** y los scripts y tareas en **Scripts** y **Tareas** (los de
   referencia están en `Documentos/Scripts/`). El backend desplegado ya no tiene la
   carga de datos de demostración (`POST /api/automatizacion/demostracion`, DEC-56).
2. En la ficha del servidor, **Preparación para el ciclo automático** dice qué le
   falta: estado, cuenta de servicio, análisis compatible, política, ventana,
   umbrales, tareas y transporte. **Probar acceso** comprueba conexión,
   autenticación y permisos con la cuenta asignada o con una candidata.
3. **Scripts**: cada script tiene una implementación por versión de sistema
   operativo y versiones inmutables. La ficha muestra la línea de versiones, el
   código, el archivo exacto que se ejecuta, los parámetros, los cambios respecto
   de la versión anterior y las MOP que usaron cada versión.
4. **Tareas**: script de mantenimiento, afectación, prioridad (menor número, antes),
   duración estimada (es también el tiempo máximo de ejecución) y umbrales que la
   activan.
5. **Configuración → Políticas de cambio**: quién autoriza por entorno y afectación.
6. Programe desde el cronograma o use **Lanzamiento inmediato**. El proceso
   automático revisa cada `KOMAINOS_AUTOMATIZACION_INTERVALO_MS` los plazos
   vencidos, las evaluaciones previas que llegaron a su fecha, las revisiones
   periódicas pendientes y la cola; `POST /api/automatizacion/procesar` lo adelanta
   sin esperar (útil en pruebas o con el proceso desactivado). La sala ya no ofrece
   ese botón ni el de despachar la cola.
7. La orden muestra el **ciclo de mantenimiento**: etapas, el siguiente paso en una
   frase y los botones que el usuario puede usar ahora (evaluar, aprobar, rechazar,
   proponer fecha, aceptar la propuesta, validar, cerrar sin intervenir, despachar
   la cola). Cada etapa alcanzada se abre desde el recorrido: qué se encontró
   (análisis previo), qué se va a hacer y quién lo aprueba (plan de trabajo y
   aprobaciones), qué se hizo (salida de cada tarea), si quedó bien (antes y
   después, validaciones e incidencias) y cómo terminó. **Exportar bitácora**
   descarga todos los eventos en Excel, CSV o JSON, sin secretos. El responsable
   puede proponer una fecha fuera de la ventana permisiva si lo confirma
   expresamente (queda en `confirmacion_fuera_ventana`); al aceptarla, la ronda
   anterior se cierra y se abre otra con su plazo. **Despachar ahora** solo aparece
   cuando la orden alcanzó su inicio; si no inicia ningún servidor, el mensaje dice
   por qué (ninguna orden llegó a su inicio, servidor ocupado por la exclusión,
   límite de ejecuciones simultáneas o piloto en curso).
8. La ficha del servidor y la del grupo muestran la **bitácora de mantenimiento**
   como línea de tiempo (un punto por mantenimiento, coloreado por su resultado),
   con la salud antes y después y las tareas de cada ciclo; la del grupo agrega un
   mapa de resultados por integrante. Se exporta completa o, en un grupo, la de un
   integrante (`/api/servidores/{id}/bitacora`, `/api/grupos/{id}/bitacora` y sus
   `/exportacion?formato=XLSX|CSV|JSON`). Debajo, el **análisis** de cada uno: en
   el grupo, una fila por revisión periódica del grupo y por evaluación previa o
   validación técnica de sus órdenes, con el análisis de cada integrante
   (`/api/grupos/{id}/evaluaciones`).
9. **Historial de ejecución** lista cada tarea ejecutada con versión, cuenta,
   duración, código de salida y salida; se filtra y se exporta a CSV.
10. **Incidencias**: diagnóstico, acciones y resultado; al cerrar la última, la
   orden continúa a su cierre sin reanudar tareas.
11. **Órdenes** (todos los roles): el responsable ve solo las de sus servidores. El
   filtro por estado muestra las que esperan una decisión (pendiente de
   autorización o de validación); cada fila abre la orden, donde se decide. El
   rechazo y los vencimientos pasan a Cerrada en la misma operación, así que no
   figuran en el filtro por estado: el filtro **Motivo de cierre**
   (`motivoCierre=NO_AUTORIZADO|VALIDACION_VENCIDA`) las busca en el historial.
   No autorizado agrupa el rechazo y el vencimiento de la autorización.

## Escribir scripts

El editor (Scripts → Nueva implementación / Nueva versión) usa resaltado de Bash o
PowerShell según el sistema operativo y ofrece plantillas que ya cumplen estas
reglas:

- **Parámetros como variables.** KomainOS antepone al código un bloque que define
  cada parámetro con su valor: `DIAS=7` y `RUTA='/var/tmp'` en Bash, `$DIAS = 7` y
  `$RUTA = 'C:\Temp'` en PowerShell (los textos van entre comillas simples escapadas). El
  script los usa directamente (`"$DIAS"` / `$DIAS`), sin leer argumentos. En
  PowerShell no se declara `param()` si la versión tiene parámetros. El bloque es
  idéntico en la vista previa y en la ejecución; la pestaña **Archivo que se
  ejecuta** lo muestra destacado y permite descargarlo para probarlo a mano.
- **Valores.** Las MOP se ejecutan sin intervención: cada parámetro toma su valor
  en todos los servidores. Todo escalar necesita valor y toda lista una referencia de catálogo. Los nombres
  admiten letras sin tilde, números y guion bajo, y no pueden pisar variables del
  intérprete (`PATH`, `HOME`, `Error`…).
- **Código de salida.** `0` es éxito; cualquier otro marca la tarea como fallida y
  abre una incidencia. La salida estándar se guarda en la bitácora y la de error
  como causa del fallo, ambas sin contraseñas ni llaves.
- **Tiempo.** La duración estimada de la tarea es su límite: al superarlo se
  detiene y se registra como tiempo excedido.
- **Privilegios (DEC-57).** Cada cuenta separa la conexión (usuario con contraseña o llave SSH) de
  la elevación, que Ansible aplica tal cual en todas sus ejecuciones, análisis incluido:

  | Elevación | Variables de Ansible | Contraseña de elevación |
  |---|---|---|
  | Ninguna | `ansible_become: false` | — |
  | sudo (Linux) | `become` con `sudo`, `become_user: root` | La que pide sudo (la del propio usuario), o ninguna si es NOPASSWD |
  | su (Linux) | `become` con `su`, `become_user: root` | La de root |
  | runas (Windows) | `become` con `runas` y la misma cuenta | La de la cuenta (no se registra aparte) |

  sudoers debe permitirle una shell como root (Ansible ejecuta `sudo /bin/sh -c '…'`), no una lista de
  comandos. runas le da a un administrador local el token completo que WinRM le filtra. Para el mínimo
  privilegio de la evaluación (DEC-53), configure su cuenta sin elevación: el script de análisis
  informa lo que no pueda leer. La prueba de acceso comprueba conexión, inicio de sesión y elevación, e
  informa si los scripts se ejecutarán con privilegios de administrador, sin exigirlo.
  El `.ps1` viaja en UTF-8 con BOM para Windows PowerShell 5.1.
- **Análisis.** Solo lee. Su última línea es el JSON de métricas, con códigos del
  catálogo; el texto anterior se conserva como salida:

  ```json
  {"metricas":[{"codigo":"cpu_uso","valor":42.5},
   {"codigo":"disco_uso","instancia":"/","valor":71},
   {"codigo":"firewall_inactivo","valor":0},
   {"codigo":"red_puerto_no_aut","elementos":["22","443"]}]}
  ```

  `valor` para métricas numéricas y binarias, `elementos` para las de lista e
  `instancia` opcional (disco, volumen, interfaz). Código e instancia no se
  repiten. Si falta una métrica que exige un umbral activo y se mide en el
  sistema operativo del servidor, el análisis falla. La pestaña **Probar una
  salida** del editor revisa un texto pegado y **Probar en un servidor** ejecuta
  una versión publicada sin registrar una evaluación.

Los scripts viven en el catálogo (`script_version.contenido`): es lo único que se
ejecuta. El backend no trae scripts de mantenimiento ni de análisis; solo las
comprobaciones internas de la prueba de acceso (`ansible/comprobaciones/`) y el
plugin que da formato a la salida de Ansible. Los scripts de referencia del R3.2
(análisis, liberar temporales, revisar memoria) están en `Documentos/Scripts/`;
son de diagnóstico y no destructivos, y el análisis informa las variables del
R3.1 (21 en Linux, 17 en Windows). El editor ofrece plantillas cortas que
muestran el contrato y permite partir de otra versión del catálogo.

## Puesta en marcha con servidores reales

El adaptador no se ha probado todavía contra servidores reales (el entorno de
desarrollo es Windows, sin Ansible): la primera vez conviene seguir estos pasos en
orden.

1. **Controlador Linux.** La imagen Docker del despliegue ya lo trae. Fuera de
   Docker: Java 21, Python 3 y `ansible-core` 2.15 o superior. Para cuentas con contraseña en
   Linux, `sshpass`. Para Windows, `pywinrm` (`pip install pywinrm`; con
   `kerberos`, `pywinrm[kerberos]`; con `credssp`, `pywinrm[credssp]`). No se usan
   colecciones adicionales: el playbook usa `ansible.builtin.script`, que no
   necesita Python en el servidor gestionado.
2. **Claves de host SSH.** Con `KOMAINOS_ANSIBLE_SSH_VERIFICAR_CLAVE_HOST=true`
   (recomendado) registre cada servidor en el `known_hosts` del usuario del
   backend o en el archivo de `KOMAINOS_ANSIBLE_SSH_KNOWN_HOSTS`
   (`ssh-keyscan -H <ip> >> known_hosts`, verificando la huella).
3. **WinRM** en los servidores Windows: escucha HTTPS en 5986 con un certificado
   que el controlador valide (o `KOMAINOS_ANSIBLE_WINRM_VALIDAR_CERTIFICADO=false`
   solo en laboratorio) y el transporte acordado (NTLM por defecto).
4. **Cuentas de servicio** en **Configuración → Cuentas de servicio**, asignadas a
   cada servidor o grupo. Linux: contraseña o llave SSH y, si corresponde, la
   clave de `su` como credencial documental. Windows: usuario del grupo
   Administradores.
5. **Arrancar** con `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=false` mientras se valida.
6. **Verificar** en este orden: el diagnóstico de la sala (controlador compatible,
   versión de Ansible, sin avisos); **Probar acceso** en un servidor de cada
   sistema operativo; **Probar en un servidor** con la versión de análisis y
   revisar que reconozca todas las métricas; **Revisar ahora** en la ficha del
   servidor; un lanzamiento inmediato sobre un servidor de pruebas.
7. Con todo correcto, activar el proceso automático
   (`KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=true`).

Cada ejecución crea un directorio privado (`0700`, archivos `0600`) con el
inventario, el playbook, el script preparado y el callback `komainos_json`, y lo
borra al terminar. Los secretos solo están en ese inventario: nunca en la línea de
comandos, los registros ni la bitácora. Las conexiones SSH no se reutilizan entre
ejecuciones (`ControlMaster=no`).

### Diagnóstico de fallos (RF71)

| Fallo | Causa habitual |
|---|---|
| Error de comunicación | IP, puerto, firewall o servicio SSH/WinRM detenido |
| Error de autenticación | Usuario, contraseña o llave inválidos o vencidos |
| Permisos insuficientes | La cuenta entra, pero la elevación configurada falla: contraseña de sudo o de root incorrecta o faltante, usuario fuera de sudoers o sin permiso para una shell, runas sin sesión completa |
| Tiempo excedido | El script superó la duración estimada de la tarea |
| Error de ejecución | El script terminó con código distinto de cero |
| Salida no válida | El análisis no imprimió el JSON de métricas en su última línea |
| Configuración del controlador | Ansible ausente, versión incompatible o dependencias faltantes |

Referencias: [módulo script](https://docs.ansible.com/ansible/latest/collections/ansible/builtin/script_module.html),
[WinRM](https://docs.ansible.com/ansible/latest/os_guide/windows_winrm.html).

## Pruebas

```powershell
mvn test
mvn verify -Pintegracion
```

Las integraciones usan solo `dbkomainos_test`, cuyo esquema se recrea; nunca
`DBKomainOS`. Recorren el ciclo completo con el doble del adaptador y sus escenarios:
versiones fijadas, autorización secuencial y propuesta de fecha, exclusión
concurrente, piloto fallido, límites de ventana, vencimientos, incidencias,
alertas por usuario, revisión periódica crítica, preparación del servidor,
pruebas de acceso clasificadas y el siguiente ciclo automático. Los artefactos de
Ansible (inventario, playbook, entorno, script preparado) tienen pruebas
unitarias propias.

En el frontend: `npm run build`, `npm run lint` y `npm test`.

## Cuentas de servicio por finalidad

Cada servidor y grupo configura por separado **Cuenta para evaluaciones** y **Cuenta para
mantenimiento**. Cada asignación puede heredar su predeterminada del sistema, y ambas pueden
seleccionar la misma cuenta. La asignación masiva y la prueba de acceso indican la finalidad.

En **Nueva cuenta de servicio** o **Nuevo secreto**, la sección **Elevación de privilegios** define
cómo obtiene privilegios Ansible (ninguna, sudo con o sin contraseña, su con la contraseña de root o
runas), y el formulario muestra al final la configuración de Ansible que resulta, con los secretos
enmascarados. La elevación se aplica en todas las ejecuciones de la cuenta: si se comparte entre
finalidades, el análisis también se eleva (DEC-57).

| Etapa | Cuenta |
|---|---|
| Revisión periódica y análisis manual por API | Evaluaciones efectiva en ese momento |
| Evaluación previa de una orden | Evaluaciones conservada en la orden |
| Prueba de acceso antes de ejecutar tareas | Mantenimiento conservada en la orden |
| Tareas de la MOP | Mantenimiento conservada en la orden y en cada tarea |
| Validación técnica posterior | Evaluaciones conservada en la orden |

La orden fija las dos cuentas resueltas al generarse. Los cambios posteriores de configuración
no sustituyen identificadores ya fijados. Si una orden se creó antes de disponer de una cuenta,
solo sus identificadores todavía vacíos se completan antes de la primera evaluación; se exige
que ambas cuentas sean utilizables antes de iniciar esa evaluación. La revocación considera
las dos asignaciones, las dos predeterminadas y ambas referencias de las órdenes pendientes.

Las tablas y detalles del ciclo muestran el nombre de la cuenta de servicio asociada a cada análisis y tarea.
La validación técnica muestra la cuenta del análisis posterior del servidor.

### Actualizar una base existente

Aplicar una vez, con el backend detenido, los scripts siguientes. Las bases nuevas ya incluyen
estos cambios en `01_esquema.sql` y no deben ejecutar estos scripts de actualización:

1. `scripts/bd/cambios/2026-11-06_cuentas_por_finalidad.sql`.
2. `scripts/bd/cambios/2026-11-06_parametros_listas.sql`.
3. `scripts/bd/cambios/2026-11-06_indicadores_auditoria.sql`.

La cuenta anterior se copia a las dos finalidades. Las versiones publicadas conservan su código,
los parámetros históricos vacíos y la inyección implícita anterior. Las nuevas versiones declaran
sus listas como parámetros; el campo `obligatorio` deja de existir. La lista elegida como entrada
es independiente de la lista efectiva con la que un umbral compara los resultados.
