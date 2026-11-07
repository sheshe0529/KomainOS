# Auditoría administrativa y evolución del dashboard

La pantalla **Configuración › Auditoría** está reservada al administrador, tanto en el menú y la ruta del panel como
en los endpoints. Consulta la tabla `auditoria` existente; la bitácora de cada mantenimiento conserva los detalles
de análisis, MOP, ejecución y validaciones.

## Consulta del historial

- `GET /api/auditoria`: devuelve `contenido`, `pagina`, `tamano`, `totalElementos` y `totalPaginas`.
- Filtros combinables: `texto` (hasta 200 caracteres), `idUsuario`, `sistema`, `operacion`, `entidad`, `desde` y `hasta`.
  Las fechas son instantes ISO; `desde` es inclusivo y `hasta` exclusivo. El panel convierte las fechas locales
  seleccionadas e incluye todo el día final.
- Paginación: `page` y `size`, con un máximo de 100 eventos por respuesta. Orden estable: fecha descendente y, a igual
  fecha, identificador descendente. El cliente no puede introducir expresiones SQL de ordenación.
- `GET /api/auditoria/filtros`: autores humanos, operaciones y entidades que ya tienen eventos. Los procesos
  automáticos se seleccionan como **Sistema** y conservan su nombre en `proceso`.

La búsqueda usa parámetros enlazados, trata `%` y `_` como texto y recorre los metadatos del evento: actor, proceso,
operación, entidad, motivo e identificadores. No busca en los valores anteriores y nuevos. El detalle muestra las
instantáneas que registró la operación, con defensa adicional para ocultar campos de autenticación conocidos y sin
devolver texto legado que no sea JSON válido. Cada productor de auditoría sigue obligado a excluir los secretos.

Se conservan los eventos de altas, ediciones, configuración, autorizaciones, bajas, reactivaciones y demás cambios
que los servicios ya auditan. Se agrega el registro de creación automática de incidencias. Los cambios realizados
directamente por SQL fuera de la aplicación no generan eventos retroactivos; tampoco se inventan valores anteriores
que no fueron registrados. La consulta no permite modificar ni eliminar el historial.

## Dos series temporales de datos reales

`GET /api/automatizacion/graficos` mantiene los gráficos existentes y agrega:

- **`servidoresPorMes`**: doce meses, incluido el actual parcial, con `mes` (`AAAA-MM`), `registrados` acumulados y
  `altas` del mes, calculados exclusivamente con `servidor.fecha_alta`. Incluye los servidores dados de baja porque
  sus registros se conservan. El gráfico se llama **Servidores registrados en el tiempo**: no afirma reconstruir el
  número histórico de activos ni cuenta reactivaciones como altas nuevas.
- **`mantenimientosEjecutadosPorMes`**: los mismos doce meses, con `finalizados` y `fallidos`. Cada detalle de orden
  representa un servidor en un ciclo; debe tener inicio real y fin real dentro del mes. El mes se determina por el
  fin de ejecución, aunque el ciclo empezara en el mes anterior. No se cuentan tareas individuales, detalles no
  iniciados ni cierres administrativos sin intervención.

Los meses sin eventos tienen conteo cero. La agrupación es UTC, coherente con los instantes persistidos. El mes
actual se corta en la actualización, y los filtros de periodo de los KPI no cambian esta ventana fija de doce meses.
Los gráficos conservan el acceso de los KPI: administrador y operador; el responsable no recibe estadísticas globales.

## Base de datos y comprobaciones

No se añade ninguna tabla ni se requiere reconstruir datos históricos. El script
`scripts/bd/cambios/2026-11-06_indicadores_auditoria.sql` agrega índices para las consultas sobre bases existentes
(DEC-34). Es idempotente y no modifica registros.

Las pruebas `AuditoriaControllerTest`, `ProtectorValoresAuditoriaTest` y `AuditoriaIT` comprueban permisos, validación,
filtros combinados, fin exclusivo, orden, paginación y ocultación de secretos. `EvolucionDashboardIT` verifica doce
meses, altas previas al periodo, servidores retirados, el límite entre meses y el conteo por servidor. La navegación
prueba que el acceso administrativo no aparezca para otros roles.
