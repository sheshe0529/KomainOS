# KomainOS — backend

API REST del sistema de gestión automatizada del mantenimiento de servidores
virtuales. Java 21, Spring Boot 3.4, PostgreSQL 18.

## Puesta en marcha

El backend se conecta directamente a una base existente: no crea ni modifica
tablas (DEC-34). La base se prepara una vez con los scripts de `scripts/bd/`.

1. Crear las bases en el PostgreSQL local (puerto 5432):
   - `DBKomainOS` — desarrollo.
   - `dbkomainos_test` — pruebas de integración. Las pruebas **borran y recrean**
     su esquema `KomainOS` en cada corrida (DEC-22).
2. Crear el esquema y los datos del sistema en `DBKomainOS` (solo si la base está vacía):
   ```bash
   psql -h localhost -U postgres -d DBKomainOS -f scripts/bd/01_esquema.sql
   psql -h localhost -U postgres -d DBKomainOS -f scripts/bd/02_datos_sistema.sql
   psql -h localhost -U postgres -d DBKomainOS -f scripts/bd/03_datos_prueba.sql   # opcional
   ```
3. Copiar `.env.example` a `.env` y completar la clave de la base, el secreto JWT
   (`openssl rand -base64 32`), la llave maestra de las credenciales
   (`KOMAINOS_CREDENCIALES_LLAVE`, también con `openssl rand -base64 32`) y la
   contraseña del administrador inicial. El archivo `.env` está ignorado por git:
   ningún secreto se versiona (RNF03). Si la llave maestra se pierde o cambia, las
   credenciales ya registradas no se pueden descifrar (DEC-38).
4. Arrancar:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

Al arrancar, Hibernate comprueba que las entidades coinciden con las tablas
(`spring.jpa.hibernate.ddl-auto: validate`). Si no coinciden, la aplicación no
arranca: es intencional, para detectar de inmediato una base desactualizada.

- API: http://localhost:8081/api
- Documentación navegable: http://localhost:8081/swagger-ui.html
- Contrato para generar los tipos del panel: http://localhost:8081/v3/api-docs

El puerto 8081 evita el 8080, ocupado en la máquina de desarrollo (DEC-29);
se cambia con `KOMAINOS_PUERTO`.

### Scripts de base de datos (`scripts/bd/`)

| Script | Uso |
|---|---|
| `01_esquema.sql` | Crea el esquema `KomainOS` completo (49 tablas, 30 enumerados). Es el esquema real de `DBKomainOS`; `Documentos/Docs/DDL_KOMAINOS.sql` está desactualizado respecto de él (DEC-02). |
| `02_datos_sistema.sql` | Datos sin los cuales el sistema no opera: factores de ciclo y parámetros globales. Idempotente. |
| `03_datos_prueba.sql` | Datos de demostración: un usuario por rol (contraseña `Cambiar.2026`), catálogos, 8 servidores con ventanas y configuración, un grupo, órdenes en distintos estados y una baja. Las fechas de las órdenes se calculan al ejecutarlo. Solo sobre una base sin datos de negocio. |
| `limpiar_datos_desarrollo.sql` | Vacía los datos de negocio conservando el esquema y los datos del sistema, para volver a cargar `03`. Solo para desarrollo. |
| `cambios/AAAA-MM-DD_*.sql` | Cambios de estructura para una base creada antes de esa fecha (por ejemplo, `DBKomainOS`). Se aplican una vez, en orden de fecha, y conservan los datos; una base nueva no los necesita porque `01_esquema.sql` ya los incluye. |

`scripts/cargar_datos_ejemplo.py` es una alternativa anterior que carga un
conjunto parecido a través de la API REST, con el backend corriendo.

## Ciclo de verificación

```bash
mvn test                      # unitarias + slices web. Sin base de datos.
mvn verify -Pintegracion      # + integración (*IT) contra dbkomainos_test
```

`mvn test` es la señal de cada cambio: incluye las reglas de arquitectura
(`ArquitecturaTest`). Las pruebas `*IT` recrean el esquema de `dbkomainos_test`
con `01_esquema.sql` y `02_datos_sistema.sql` y levantan el contexto completo:
que arranquen ya prueba que los scripts crean una base válida y que las
entidades coinciden con las tablas.

`ContratoApiIT` deja además el contrato OpenAPI en `target/openapi.json`. Con él
se regeneran los tipos del panel sin levantar el backend:

```bash
python <skill komainos-backend>/scripts/generar_tipos_ts.py --origen target/openapi.json --destino ../Frontend/src/api
```

## Estructura

Primero por **componente** de la vista de componentes de R2.2, y dentro de cada
componente por el **rol** de cada clase (DEC-33):

```
com.komainos
├── seguridad/      autenticación JWT, usuarios, roles y alcance por usuario (RF01–RF03)
├── auditoria/      bitácora de operaciones (RNF06)
├── inventario/     catálogos, servidores, grupos, ventanas, importación y exportación (RF09–RF21, RF70–RF76)
├── credencial/     credenciales documentales, cuentas de servicio, cifrado y revelado (RF04–RF08)
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
