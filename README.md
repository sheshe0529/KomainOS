# KomainOS

Sistema de información para la gestión automatizada del mantenimiento de servidores virtuales mediante scripts.

## Estado

**Iteración 1 — Inventario y Planificación** (más las dependencias mínimas: autenticación, roles, catálogos y
auditoría). El alcance, los requisitos cubiertos y el plan por bloques están en
[`Documentos/Decisiones/00_Analisis_inicial_y_plan_Iteracion_1.md`](Documentos/Decisiones/00_Analisis_inicial_y_plan_Iteracion_1.md);
cada decisión tomada ante un vacío o una contradicción de la documentación, en
[`Registro_de_decisiones.md`](Documentos/Decisiones/Registro_de_decisiones.md).

## Estructura

```
Backend/      API REST — Java 21, Spring Boot 3, PostgreSQL (scripts de base de datos en Backend/scripts/bd)
Frontend/     Panel web — React 19, Vite, TypeScript, Tailwind CSS 4
Documentos/
├── Docs/         especificación (fuente de verdad): requisitos, arquitectura, diseño, modelo relacional
└── Decisiones/   análisis, plan de la iteración, algoritmo de planificación y registro de decisiones
UI_preliminar/    pantallas preliminares de referencia
```

## Puesta en marcha local

Requisitos: Java 21, Maven 3.9, Node.js 20 o superior y PostgreSQL 18.

1. **Bases de datos.** Crear `DBKomainOS` (desarrollo) y, para las pruebas de integración, `dbkomainos_test`.
   En una `DBKomainOS` vacía, ejecutar `Backend/scripts/bd/01_esquema.sql`, `02_datos_sistema.sql` y, si se
   quieren datos de demostración, `03_datos_prueba.sql` (usuarios con contraseña `Cambiar.2026`).
   El backend se conecta a la base existente y no modifica su estructura.
2. **Backend.** Copiar `Backend/.env.example` a `Backend/.env`, completar las claves y ejecutar:
   ```bash
   cd Backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8081
   ```
3. **Panel.**
   ```bash
   cd Frontend
   npm install
   npm run dev                                            # http://localhost:5180
   ```
   Vite reenvía `/api` al backend, así que no hace falta configurar CORS en desarrollo.

El detalle de cada parte está en [`Backend/README.md`](Backend/README.md) y [`Frontend/README.md`](Frontend/README.md).
