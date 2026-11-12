# KomainOS

Sistema de información para la gestión automatizada del mantenimiento de servidores virtuales mediante scripts.

## Estado

**Inventario, planificación, automatización y ejecución.** El objetivo 3 incorpora
scripts versionados, tareas, políticas de cambio, MOP, cola, validaciones,
incidencias, alertas y KPI. Los scripts se ejecutan en los servidores con Ansible
(SSH y WinRM); el modo simulado se retiró del código desplegado (DEC-56). El
recorrido y la configuración están en [Automatización y ejecución](Backend/AUTOMATIZACION.md),
y el despliegue en EC2 con Amazon RDS y Docker, en [DESPLIEGUE.md](DESPLIEGUE.md).

El alcance inicial de inventario y planificación está en
[`Documentos/Decisiones/00_Analisis_inicial_y_plan_Iteracion_1.md`](Documentos/Decisiones/00_Analisis_inicial_y_plan_Iteracion_1.md);
cada decisión tomada ante un vacío o una contradicción de la documentación, en
[`Registro_de_decisiones.md`](Documentos/Decisiones/Registro_de_decisiones.md).

## Estructura

```
Backend/      API REST — Java 21, Spring Boot 3, PostgreSQL (DDL y scripts de base de datos en Backend/scripts/bd)
Frontend/     Panel web — React 19, Vite, TypeScript, Tailwind CSS 4
Documentos/
├── Docs/         especificación (fuente de verdad): requisitos, arquitectura, diseño, modelo relacional
└── Decisiones/   análisis, plan de la iteración, algoritmo de planificación y registro de decisiones
UI_preliminar/    pantallas preliminares de referencia
```

## Puesta en marcha local

Requisitos: Java 21, Maven 3.9, Node.js 20 o superior y PostgreSQL 18.

1. **Bases de datos.** Crear `DBKomainOS` (desarrollo) y, para las pruebas de integración, `dbkomainos_test`.
   En una `DBKomainOS` vacía, ejecutar `Backend/scripts/bd/01_esquema.sql` (DDL) y `02_datos_sistema.sql` (datos
   iniciales); después se registran los datos propios (servidores, cuentas, umbrales) desde el panel. La base se crea
   desde cero con esos dos scripts: no hay scripts de migración desde versiones anteriores.
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

En Windows no hay Ansible: todo funciona salvo las operaciones remotas (análisis, pruebas de acceso y tareas), que
fallan como «Configuración del controlador». Por eso el perfil `dev` deja desactivado el proceso automático.

El detalle de cada parte está en [`Backend/README.md`](Backend/README.md) y [`Frontend/README.md`](Frontend/README.md).

## Despliegue

Una sola imagen Docker (`Dockerfile` en la raíz) con el panel compilado, la API y Ansible, en una instancia EC2, y la
base en Amazon RDS for PostgreSQL. La base se crea con `Backend/scripts/bd/01_esquema.sql` (DDL) y
`02_datos_sistema.sql` (datos iniciales). Cada merge a `main` se despliega solo (DESPLIEGUE.md, sección 20). Pasos, variables y verificación en [DESPLIEGUE.md](DESPLIEGUE.md).
