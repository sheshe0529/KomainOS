# Automatización y ejecución — objetivo 3

El componente implementa el ciclo descrito en R2.1/R2.3/R2.4: revisión periódica,
evaluación previa, MOP, autorización, cola, ejecución, validaciones, incidencias
y cierre. Reutiliza las tablas del esquema relacional final; no necesita una
migración ni cambios en la base existente.

## Entorno local de Windows

El valor predeterminado es `KOMAINOS_AUTOMATIZACION_MODO=SIMULADO`. No se invoca
Ansible, no se abren conexiones a los servidores y no se descifran sus secretos.
El motor y las reglas de negocio sí funcionan y guardan resultados en PostgreSQL.
No se necesita ni se utiliza Docker.

El ciclo automático se procesa cada 30 segundos. Para trabajar solo con los
botones del panel, establezca `KOMAINOS_AUTOMATIZACION_PROCESO_HABILITADO=false`.
Los controles **Procesar ciclo** y **Ejecutar cola** siguen disponibles al
administrador. El perfil `test` siempre utiliza el adaptador simulado y desactiva
el programador para que las pruebas sean reproducibles.

## Recorrido de prueba desde el panel

1. En **Automatización → Sala de operaciones**, pulse **Cargar demostración**.
   Es idempotente: agrega ejemplos y conserva las configuraciones existentes.
2. Compruebe que el servidor esté activo y tenga una cuenta de servicio y una
   ventana permisiva. Puede crear una cuenta ficticia para la simulación desde
   **Configuración → Cuentas de servicio**. El modelo conserva la trazabilidad
   de esa cuenta incluso cuando no hay una conexión real.
3. Consulte **Scripts**. Cada script tiene implementaciones por versión de SO;
   cada publicación conserva código, parámetros, notas y versión anteriores.
   Desde una versión puede consultar las tareas de MOP que la utilizan.
4. Consulte **Tareas**: la prioridad menor se ejecuta primero; una tarea se
   agrega una sola vez aunque la activen varios umbrales. El máximo por tarea
   se valida contra los parámetros del sistema.
5. Abra **Configuración → Políticas de cambio**. La matriz tiene una fila por
   entorno y las columnas Sin afectación, Parcial y Total. Cada celda guarda
   independientemente la autorización y sus destinatarios. Configure las reglas por entorno y afectación. Sin aprobadores
   obligatorios, la autorización es automática. Si exige ambos, el responsable
   decide primero y el administrador después. Las dos decisiones comparten el
   mismo plazo. Cambiar una regla no sustituye las decisiones ya solicitadas.
6. Programe desde el cronograma o use **Lanzamiento inmediato**. Este último
   requiere un motivo y confirmación expresa si inicia fuera de ventana. La
   condición crítica también genera una orden, pero conserva la política de
   aprobación y no concede una excepción de ventana implícita.
7. Abra la orden y pulse **Evaluar mantenimiento** si aún está programada.
   La sección **MOP y tareas** muestra prioridad, duración, afectación, cuenta
   y versión fijada. Una implementación incompatible bloquea la ejecución.
8. Si corresponde, entre como responsable y administrador para completar las
   autorizaciones. El responsable puede proponer una fecha. El administrador
   debe aceptarla; se verifican ventana, capacidad y conflictos y se abre una
   nueva ronda de aprobaciones.
9. Una orden autorizada entra en cola cuando llega su fecha. Consulte la cola
   y procese la ejecución. Un servidor mantiene como máximo una intervención
   simultánea. Los lanzamientos inmediatos no consumen el límite global, pero
   respetan la exclusión por servidor. Un grupo paralelo ejecuta primero la
   MOP con más tareas como piloto.
10. Revise **Métricas antes y después**, las validaciones técnicas y la bitácora.
    El responsable confirma o rechaza la validación funcional dentro del plazo.
    La conformidad cierra el ciclo; el rechazo genera una incidencia.
11. En **Incidencias**, el administrador registra diagnóstico, acciones y
    resultado. Al cerrar todas, se cierra la orden; no se reinician tareas
    fallidas o no iniciadas. Los vencimientos tampoco presumen conformidad.
12. En **Sala de operaciones → Indicadores de gestión**, consulte KPI-01 a KPI-05,
    con periodo, numeradores, denominadores y fecha de actualización. Los gráficos
    muestran CPU y RAM de las últimas 24 horas mediante áreas superpuestas y
    ejecuciones de scripts de los últimos siete días mediante barras apiladas (UTC). Cada detalle
    de servidor es un mantenimiento; los periodos incluyen el inicio y excluyen
    el final. El inventario se cuenta a la fecha actual. Sin registros se muestra
    un valor vacío, no un éxito del 100 %. La meta del tiempo de cierre depende
    del SLA de la organización.
13. La **campana** muestra las notificaciones recientes y el contador sin leer.
    Al abrir una alerta se consulta su mensaje completo y se marca como leída.
    **Mis alertas** permite consultar el historial y volver a abrir cada detalle.
    Solo se muestran notificaciones del usuario actual.
    La bitácora de cada orden puede descargarse en JSON, sin secretos.

## Ejemplos de métricas y scripts

Los ejemplos usan `cpu_uso_pct`, `memoria_uso_pct` y `disco_uso_pct`, con advertencia
al alcanzar 80 % y nivel crítico al alcanzar 95 %. La simulación informa antes
58 %, 82 % y 86 %; después 35 %, 52 % y 55 %. También informa las métricas y listas
del catálogo de pruebas anterior, para poder reutilizar sus umbrales.

Los controles de demostración comprueban disco y memoria, son de diagnóstico y
no realizan cambios destructivos. Los scripts de análisis Bash/PowerShell están
en `src/main/resources/ansible/scripts/`. Sus variables y límites son ejemplos
provisionales que deben sustituirse por los resultados aprobados de R3.1–R3.3.

Contrato de salida de un script de análisis:

```json
{"metricas":[{"codigo":"cpu_uso_pct","valor":58},
 {"codigo":"disco_uso_pct","instancia":"sistema","valor":86},
 {"codigo":"usuarios_locales","elementos":["admin","root"]}]}
```

La combinación código/instancia no se repite. Las métricas escalares son
numéricas; las listas usan `elementos`. La evaluación aplica la precedencia
servidor → grupo de la orden → entorno → criticidad → global y conserva los
valores aplicados. Si falta una métrica requerida por un umbral activo, el
análisis falla explícitamente.

Para reproducir fallos locales, puede agregar un mapa `escenarios` a
`komainos.automatizacion` en la configuración, con hostname como clave y
`FALLO_TAREA`, `SIN_CONEXION`, `NO_CONFORME` o `CRITICO` como valor. El perfil de
pruebas contiene ejemplos. Estos escenarios no ejecutan comandos reales.

## Adaptador Ansible preparado, sin activar

`AnsibleReal` solo se instancia con modo `REAL` y un controlador Linux. Windows
nativo lo rechaza al iniciar. No se ha habilitado ni probado el transporte real.
Para un futuro entorno Linux se necesita `ansible-playbook`, las dependencias
SSH/WinRM y la colección de Windows correspondiente. Se utiliza inventario
transitorio con permisos privados, callback estructurado, parámetros citados,
validación de certificados WinRM y de claves de host SSH, y limpieza del material
sensible. Los secretos no se pasan como argumentos del proceso ni se registran
como parte de la bitácora; las salidas se sanean antes de persistirlas.

Referencias oficiales utilizadas para preparar el adaptador:
- [Módulo script](https://docs.ansible.com/projects/ansible/latest/collections/ansible/builtin/script_module.html)
- [WinRM](https://docs.ansible.com/projects/ansible/latest/os_guide/windows_winrm.html)

## Pruebas

```powershell
mvn test
mvn verify -Pintegracion
```

Las integraciones usan exclusivamente `dbkomainos_test`, cuyo esquema se recrea;
nunca `DBKomainOS`. Incluyen el recorrido completo, versiones fijadas, autorización
secuencial, exclusión concurrente, piloto fallido, límites de ventana, vencimientos,
incidencias, alertas por usuario y generación del siguiente ciclo automático.

En el frontend: `npm run build`, `npm run lint` y `npm test`.
