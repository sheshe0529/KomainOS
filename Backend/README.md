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

Primero por **componente** de la vista de componentes de R2.2, y dentro de cada
componente por el **rol** de cada clase (DEC-33):

```
com.komainos
├── seguridad/      autenticación JWT, usuarios, roles y alcance por usuario (RF01–RF03)
├── auditoria/      bitácora de operaciones (RNF06)
├── inventario/     catálogos, servidores, grupos, ventanas, importación y exportación (RF09–RF21, RF70–RF76)
├── mantenimiento/  órdenes de mantenimiento, su detalle, historial y máquina de estados
├── planificacion/  algoritmo voraz, cronograma y proceso automático (RF27–RF38, RF46, RF51, RF64)
│   ├── controller/     endpoints REST (@RestController): validan el borde y delegan
│   ├── dto/            contrato HTTP: *Peticion y *Respuesta
│   ├── mapper/         entidad → DTO
│   ├── service/        casos de uso y reglas de negocio (@Service); algoritmo/ es el planificador voraz
│   ├── model/          entidades JPA, enums y datos de entrada del dominio
│   ├── event/          eventos de dominio entre componentes
│   ├── repository/     acceso a datos (Spring Data) y consultas dinámicas
│   └── config/         propiedades y configuración del componente
└── shared/         lo transversal, que no es un componente
    ├── controller/     manejo global de errores
    ├── dto/            forma de error, página, referencia simple
    ├── exception/      excepciones de negocio comunes
    ├── validation/     validaciones reutilizables (dirección IP)
    ├── model/          Actor, Intervalo
    ├── util/           Tiempo; archivo/: lectura y escritura XLSX, CSV, YAML, JSON
    └── config/         reloj, OpenAPI
```

Cada componente solo tiene las carpetas que necesita.

**Reglas de dependencia**, verificadas por `ArquitecturaTest` en cada `mvn test`:

- Dentro de un componente: `controller → service → repository`. Los servicios
  devuelven entidades y no conocen DTO, mapeadores ni controladores, así que el
  mismo caso de uso sirve a la API, a los procesos programados y a la
  importación masiva. Los controladores no usan repositorios directamente.
- Entre componentes no hay ciclos: planificación → mantenimiento → inventario
  → seguridad → auditoría → shared. Cuando un componente necesita algo de uno
  que está "después", lo pide mediante un puerto (interfaz) que el otro
  implementa, como `PuertoMantenimientos` (DEC-27) o `PuertoParametrosSesion`
  (DEC-33).

El algoritmo de planificación está especificado en
`Documentos/Decisiones/Algoritmo_planificacion_voraz.md`.
