# KomainOS — panel

Panel web de KomainOS. React 19, Vite, TypeScript y Tailwind CSS 4.

**Automatización** reúne sala de operaciones (estado del motor, órdenes por etapa,
cola por servidor y KPI), scripts, tareas, historial de ejecución e incidencias.
Los scripts se escriben en un editor con resaltado de Bash y PowerShell
(CodeMirror 6, en un archivo aparte que solo se descarga al abrir un script), con
plantillas, tabla de parámetros, vista previa del archivo que se ejecuta,
comparación entre versiones y revisión de salidas de análisis. La orden muestra
las etapas del ciclo, el siguiente paso, las decisiones disponibles para el
usuario, la MOP con la salida de cada tarea y el reporte comparativo; la bitácora
se descarga en CSV o JSON. La matriz de políticas de cambio está en
**Configuración** (`/configuracion/politicas-cambio`). El recorrido y el contrato
de los scripts están en [la guía del componente](../Backend/AUTOMATIZACION.md).

## Uso

```bash
npm install
npm run dev       # http://localhost:5180, con /api reenviado al backend (http://localhost:8081)
npm run build     # chequeo de tipos + build de producción
npm run lint      # oxlint
npm test          # Vitest: reglas del panel que no dependen del navegador (src/**/*.test.ts)
```

El puerto es fijo (`strictPort`): si está ocupado, Vite falla en lugar de
cambiar de puerto en silencio (DEC-29). `KOMAINOS_BACKEND` permite apuntar el
proxy a otro backend.

## Estructura

```
src/
├── api/          cliente HTTP (cliente.ts), tipos generados desde OpenAPI (types.ts) y un módulo por recurso
├── auth/         sesión, token y rutas protegidas por rol
├── config/       navigation.ts: única fuente de verdad del menú
├── components/
│   ├── layout/        Sidebar, Topbar, MainLayout
│   ├── ui/            botones, campos, modal, avisos, StatusPill…
│   ├── common/        PageHeader, Tarjeta
│   ├── inventario/    formularios y diálogos de servidores y grupos, importar y exportar
│   ├── catalogos/     pestañas de entornos, criticidades y sistemas operativos
│   └── planificacion/ programar, cancelar, tablas y gráficos de órdenes
├── hooks/        useConsulta, catálogos, tono de criticidad
├── pages/        una página por ruta (App.tsx debe reflejar navigation.ts)
└── utils/        formato de fechas, etiquetas y errores
```

## Tipos de la API

`src/api/types.ts` se genera desde el contrato del backend (con el backend
corriendo) con el script `generar_tipos_ts.py` de la skill de backend. Solo se
reemplaza `types.ts`; `cliente.ts` y los módulos de `src/api` se mantienen a mano.
Después de regenerar, `npm run build` marca cualquier uso desalineado.

## Tema

Los colores son variables CSS semánticas en `src/index.css` (paleta clara y
oscura). El tema se elige en *Configuración → Preferencias* (DEC-30).

## Registro e importación del inventario

Ambos recorridos tienen cinco etapas: datos o archivo, análisis, configuración
de mantenimiento, modalidad de planificación y resumen. El análisis no guarda
datos; solo el botón de confirmación final registra el inventario junto con
sus frecuencias, cuenta de servicio y ventana permisiva. La modalidad puede
ser Automática o Bajo demanda. En una importación se configuran los servidores
individualmente y se pueden aplicar frecuencias, ventanas o modalidad a todos.
Los archivos con errores deben corregirse y analizarse de nuevo antes de avanzar.

Umbrales es una sección independiente en `/umbrales`, con las pestañas Umbrales,
Métricas y Listas de referencia. Las rutas anteriores de Configuración redirigen
a la sección nueva.
