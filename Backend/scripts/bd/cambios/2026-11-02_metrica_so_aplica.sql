-- Cambio 2026-11-02 · Sistema operativo de la métrica en una sola columna (DEC-48)
-- Aplica sobre una base con cambios/2026-11-02_variables_salud_r31.sql. Reemplaza aplica_linux y aplica_windows por
-- so_aplica (enum_familia_so): LINUX o WINDOWS si la métrica se mide solo en esa familia, y nulo si se mide en ambas.
-- Después de aplicarlo, la base coincide con 01_esquema.sql.
--
-- Antes de aplicarlo, respalde la base:
--   pg_dump -h localhost -U postgres -d DBKomainOS -Fc -f respaldo_antes_so_aplica.dump
-- Aplicación (después, reinicie el backend):
--   psql -h localhost -U postgres -d DBKomainOS -v ON_ERROR_STOP=1 -f scripts/bd/cambios/2026-11-02_metrica_so_aplica.sql
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'KomainOS' AND table_name = 'metrica' AND column_name = 'aplica_linux') THEN
        RAISE EXCEPTION 'Falta aplica_linux: aplique antes cambios/2026-11-02_variables_salud_r31.sql o este cambio ya está aplicado';
    END IF;
END $$;

ALTER TABLE metrica ADD COLUMN so_aplica enum_familia_so;

UPDATE metrica SET so_aplica = CASE
    WHEN aplica_linux AND NOT aplica_windows THEN 'LINUX'::enum_familia_so
    WHEN aplica_windows AND NOT aplica_linux THEN 'WINDOWS'::enum_familia_so
END;

ALTER TABLE metrica
    DROP CONSTRAINT ck_metrica_sistema_operativo,
    DROP COLUMN aplica_linux,
    DROP COLUMN aplica_windows;

COMMIT;
