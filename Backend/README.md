# KomainOS — backend

API REST del sistema de gestión automatizada del mantenimiento de servidores
virtuales. Java 21, Spring Boot 3.4, PostgreSQL 18, Flyway.

## Puesta en marcha

1. Crear las bases en el PostgreSQL local (puerto 5432):
   - `DBKomainOS` — desarrollo.
   - `dbkomainos_test` — pruebas de integración. Flyway **limpia** su esquema en cada corrida (DEC-22).
2. Copiar `.env.example` a `.env` y completar la clave de la base, el secreto JWT
   (`openssl rand -base64 32`) y la contraseña del administrador inicial. El archivo
   `.env` está ignorado por git: ningún secreto se versiona (RNF03).
3. Arrancar:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

Flyway aplica las migraciones al arrancar (`V1` es el esquema vigente del
modelo relacional; `V2`, los datos iniciales del sistema). Si el esquema y las
entidades no coinciden, la aplicación no arranca: es intencional
(`spring.jpa.hibernate.ddl-auto: validate`).

- API: http://localhost:8081/api
- Documentación navegable: http://localhost:8081/swagger-ui.html
- Contrato para generar los tipos del panel: http://localhost:8081/v3/api-docs

El puerto 8081 evita el 8080, ocupado en la máquina de desarrollo (DEC-29);
se cambia con `KOMAINOS_PUERTO`.

### Scripts de desarrollo

| Script | Uso |
|---|---|
| `scripts/cargar_datos_ejemplo.py` | Carga usuarios, catálogos, servidores y grupos de ejemplo a través de la API. Idempotente. |
| `scripts/limpiar_datos_desarrollo.sql` | Vacía los datos de negocio de `DBKomainOS` conservando el esquema. Solo para desarrollo. |

## Ciclo de verificación

```bash
mvn test                      # unitarias + slices web. Sin base de datos.
mvn verify -Pintegracion      # + integración (*IT) contra dbkomainos_test
```

`mvn test` es la señal de cada cambio. Las pruebas `*IT` levantan el contexto
completo: que arranquen ya prueba que Flyway migró y que las entidades
coinciden con las tablas.

## Estructura

Un paquete por módulo de negocio, cortado en capas:

```
com.komainos
├── shared/         errores, archivos de intercambio, reloj, OpenAPI, validaciones comunes
├── seguridad/      autenticación JWT, usuarios, roles y alcance por usuario (RF01–RF03)
├── auditoria/      bitácora de operaciones (RNF06)
├── inventario/     catálogos, servidores, grupos, ventanas, importación y exportación (RF09–RF21, RF70–RF76)
├── mantenimiento/  órdenes de mantenimiento, su detalle, historial y máquina de estados
└── planificacion/  algoritmo voraz, cronograma y proceso automático (RF27–RF38, RF46, RF51, RF64)
    ├── api/        controlador, DTO, mapeador   → depende de dominio
    ├── dominio/    entidades, enums, servicios  → depende de infra
    └── infra/      repositorios, especificaciones
```

Las dependencias apuntan en una sola dirección: `api → dominio → infra`. El
dominio no importa nada de `api`, por eso los servicios devuelven entidades y
el mapeo a DTO ocurre en el controlador. Entre módulos, el inventario no
depende de la planificación: se comunican por eventos y puertos (DEC-27).

El algoritmo de planificación está especificado en
`Documentos/Decisiones/Algoritmo_planificacion_voraz.md`.
