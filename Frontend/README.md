# KomainOS — panel

Panel web de KomainOS. React 19, Vite, TypeScript y Tailwind CSS 4.

## Uso

```bash
npm install
npm run dev       # http://localhost:5180, con /api reenviado al backend (http://localhost:8081)
npm run build     # chequeo de tipos + build de producción
npm run lint      # oxlint
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
