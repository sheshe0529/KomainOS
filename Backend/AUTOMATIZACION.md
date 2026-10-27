# Automatización y ejecución — objetivo 3

El componente implementa el ciclo de R2.1/R2.3/R2.4: revisión periódica, evaluación
previa, MOP, autorización, cola, ejecución, validaciones, incidencias y cierre.
Reutiliza las tablas del esquema relacional final; no necesita migraciones.

Hay dos modos, con el mismo motor y las mismas reglas de negocio:

| Modo | Qué hace | Dónde |
|---|---|---|
| `SIMULADO` (predeterminado) | No abre conexiones ni descifra secretos. Responde con valores genéricos calculados desde los umbrales vigentes de cada servidor. | Windows local, pruebas, demostraciones |
| `REAL` | Ejecuta cada script con `ansible-playbook`: SSH para Linux y WinRM para Windows, sin agentes. | Un controlador Linux con Ansible |

El modo y el estado del controlador se ven en **Automatización → Sala de
operaciones** (barra superior desplegable) y en `GET /api/automatizacion/modo`.

## Variables de entorno

| Variable | Predeterminado | Uso |
|---|---|---|
| `KOMAINOS_AUTOMATIZACION_MODO` | `SIMULADO` | `REAL` activa el adaptador Ansible |
| `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO` | `true` | `false` deja solo los botones del panel |
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
| `KOMAINOS_SIMULACION_RETARDO_MS` | `0` (`800` en el perfil `dev`) | Demora de cada operación simulada, para ver las etapas |

El perfil `test` usa siempre el simulador y desactiva el proceso automático.

## Simulación genérica

Como las métricas y los umbrales definitivos (R3.1–R3.3) aún no están cerrados, el
simulador no tiene valores fijos: para cada métrica que exige un umbral activo del
servidor calcula un valor normal, de advertencia o crítico según los límites que
rigen a ese servidor (con sus excepciones). Así cualquier catálogo de métricas
funciona sin tocar código.

| Escenario | Revisión periódica | Evaluación previa | Tareas | Validación técnica |
|---|---|---|---|---|
| `ESTANDAR` (predeterminado) | normal | advertencia → genera MOP | correctas | normal |
| `SALUDABLE` | normal | normal → cierra sin intervenir | — | — |
| `ADVERTENCIA` | advertencia → solo alerta | advertencia | correctas | normal |
| `CRITICO` | crítico → orden de prioridad alta | crítico | correctas | normal |
| `NO_CONFORME` | normal | advertencia | correctas | advertencia → incidencia |
| `FALLO_TAREA` | normal | advertencia | la tarea falla → incidencia | — |
| `SIN_CONEXION` / `SIN_AUTENTICACION` / `SIN_PERMISOS` | falla con ese tipo de error (RF71) | | | |

Se asignan por hostname, sin distinguir mayúsculas:

```yaml
komainos:
  automatizacion:
    escenarios:
      SERVPRODBDDV02: FALLO_TAREA
      SERVPRODCACV01: SIN_AUTENTICACION
```

o al arrancar: `--komainos.automatizacion.escenarios.SERVPRODBDDV02=FALLO_TAREA`.

## Recorrido de prueba desde el panel

1. **Sala de operaciones → Cargar demostración** (el panel lo ofrece en modo simulado): agrega
   métricas, umbrales, tres scripts con implementaciones para los sistemas
   operativos activos, dos tareas y las reglas de política que falten. Es
   idempotente y no modifica lo existente.
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
6. Programe desde el cronograma o use **Lanzamiento inmediato**.
7. La orden muestra el **ciclo de mantenimiento**: etapas, el siguiente paso en una
   frase y los botones que el usuario puede usar ahora (evaluar, aprobar, rechazar,
   proponer fecha, aceptar la propuesta, validar, cerrar sin intervenir, despachar
   la cola). Pestañas: MOP y ejecución (salida de cada tarea), análisis con el
   reporte comparativo, autorizaciones, validaciones e incidencias. La bitácora se
   descarga en CSV o JSON, sin secretos.
8. **Historial de ejecución** lista cada tarea ejecutada con versión, cuenta,
   duración, código de salida y salida; se filtra y se exporta a CSV.
9. **Incidencias**: diagnóstico, acciones y resultado; al cerrar la última, la
   orden continúa a su cierre sin reanudar tareas.

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
  en todos los servidores, y un parámetro obligatorio debe tenerlo. Los nombres
  admiten letras sin tilde, números y guion bajo, y no pueden pisar variables del
  intérprete (`PATH`, `HOME`, `Error`…).
- **Código de salida.** `0` es éxito; cualquier otro marca la tarea como fallida y
  abre una incidencia. La salida estándar se guarda en la bitácora y la de error
  como causa del fallo, ambas sin contraseñas ni llaves.
- **Tiempo.** La duración estimada de la tarea es su límite: al superarlo se
  detiene y se registra como tiempo excedido.
- **Privilegios.** En Linux corre como root si la cuenta de servicio tiene clave de
  `su`; si no, corre con la cuenta, que la prueba de acceso exige que sea root o
  pueda usar `sudo` sin contraseña. En Windows la cuenta debe pertenecer al grupo
  Administradores. El `.ps1` viaja en UTF-8 con BOM para Windows PowerShell 5.1.
- **Análisis.** Solo lee. Su última línea es el JSON de métricas, con códigos del
  catálogo; el texto anterior se conserva como salida:

  ```json
  {"metricas":[{"codigo":"cpu_uso_pct","valor":42.5},
   {"codigo":"disco_uso_pct","instancia":"/","valor":71},
   {"codigo":"puertos_escucha","elementos":["22","443"]}]}
  ```

  `valor` para métricas numéricas, `elementos` para las de lista e `instancia`
  opcional (disco, interfaz). Código e instancia no se repiten. Si falta una
  métrica que exige un umbral activo, el análisis falla. La pestaña **Probar una
  salida** del editor revisa un texto pegado y **Probar en un servidor** ejecuta
  una versión publicada sin registrar una evaluación.

Los ejemplos de `src/main/resources/ansible/scripts/` (análisis, liberar
temporales, revisar memoria) son de diagnóstico y no destructivos; sus métricas y
límites son provisionales.

## Habilitar Ansible real

El adaptador está completo pero no se ha probado contra servidores: este entorno
de desarrollo es Windows y no tiene Ansible. `AnsibleReal` se niega a arrancar en
Windows. Pasos para habilitarlo:

1. **Controlador Linux** (servidor o contenedor del despliegue) con Java 21,
   Python 3 y `ansible-core` 2.15 o superior. Para cuentas con contraseña en
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
5. **Arrancar** con `KOMAINOS_AUTOMATIZACION_MODO=REAL` y, mientras se valida,
   `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=false`.
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
| Permisos insuficientes | La cuenta entra pero no es root/sudo ni Administrador |
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
`DBKomainOS`. Recorren el ciclo completo con el simulador y sus escenarios:
versiones fijadas, autorización secuencial y propuesta de fecha, exclusión
concurrente, piloto fallido, límites de ventana, vencimientos, incidencias,
alertas por usuario, revisión periódica crítica, preparación del servidor,
pruebas de acceso clasificadas y el siguiente ciclo automático. Los artefactos de
Ansible (inventario, playbook, entorno, script preparado) tienen pruebas
unitarias propias.

En el frontend: `npm run build`, `npm run lint` y `npm test`.
