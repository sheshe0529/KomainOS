-- =====================================================================================================================
-- Instalación de la base de KomainOS sobre una base vacía (por ejemplo, en Amazon RDS for PostgreSQL 15 o superior)
-- =====================================================================================================================
-- Se ejecuta con psql desde esta carpeta, porque incluye los demás scripts por ruta relativa:
--
--   cd Backend/scripts/bd
--   psql "host=<endpoint> port=5432 dbname=komainos user=<usuario> sslmode=require" -f instalar_base.sql
--
-- 01_esquema.sql (DDL) y 02_datos_sistema.sql en una sola transacción: si algo falla no queda un esquema a medias.
-- 02 solo trae lo que el backend necesita para operar: los parámetros del sistema (sin ellos no se emiten tokens),
-- los factores de ciclo (la planificación los exige) y las 21 variables de salud del R3.1 que informan los scripts de
-- análisis. No carga umbrales ni listas de referencia (se registran en el panel; 04_umbrales_variables.sql, opcional,
-- trae unos genéricos), ni datos de demostración (03 y 05), ni crea el administrador: lo crea el backend en su primer
-- arranque con KOMAINOS_ADMIN_CLAVE_INICIAL (DEC-21).
-- =====================================================================================================================

\set ON_ERROR_STOP on

SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'KomainOS') AS esquema_existe \gset
\if :esquema_existe
    \echo 'El esquema "KomainOS" ya existe en esta base: la instalación solo se ejecuta sobre una base vacía.'
    \quit
\endif

\echo 'Creando el esquema KomainOS y los datos del sistema...'
BEGIN;
\ir 01_esquema.sql
\ir 02_datos_sistema.sql
COMMIT;

SELECT count(*) AS tablas FROM pg_tables WHERE schemaname = 'KomainOS';
\echo 'Base de KomainOS instalada. Inicie el backend con KOMAINOS_ADMIN_CLAVE_INICIAL para crear el administrador.'
