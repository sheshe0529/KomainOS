-- Conserva las revisiones de servidores existentes. Las de grupo se habilitan expresamente.
BEGIN;
ALTER TABLE "KomainOS".configuracion_servidor
    ADD COLUMN revision_periodica_habilitada boolean DEFAULT true NOT NULL;
ALTER TABLE "KomainOS".configuracion_grupo
    ADD COLUMN revision_periodica_habilitada boolean DEFAULT false NOT NULL;
COMMIT;
