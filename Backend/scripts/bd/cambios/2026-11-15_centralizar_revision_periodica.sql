BEGIN;
SET LOCAL search_path TO "KomainOS";

-- Copiar el valor de cada configuración antes de retirar las columnas de las subclases.
-- Sin valor por defecto común: la aplicación conserva true para servidores y false para grupos nuevos.
ALTER TABLE configuracion_mantenimiento ADD COLUMN revision_periodica_habilitada boolean;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM configuracion_servidor s
        JOIN configuracion_grupo g USING (id_configuracion_mantenimiento)
    ) THEN
        RAISE EXCEPTION 'Una configuración pertenece a servidor y grupo; revise los datos antes de migrar';
    END IF;
END $$;

UPDATE configuracion_mantenimiento m
SET revision_periodica_habilitada = s.revision_periodica_habilitada
FROM configuracion_servidor s
WHERE m.id_configuracion_mantenimiento = s.id_configuracion_mantenimiento;

UPDATE configuracion_mantenimiento m
SET revision_periodica_habilitada = g.revision_periodica_habilitada
FROM configuracion_grupo g
WHERE m.id_configuracion_mantenimiento = g.id_configuracion_mantenimiento;

-- Si hay una configuración sin subclase, abortar toda la transacción sin inventar su valor.
ALTER TABLE configuracion_mantenimiento ALTER COLUMN revision_periodica_habilitada SET NOT NULL;
ALTER TABLE configuracion_servidor DROP COLUMN revision_periodica_habilitada;
ALTER TABLE configuracion_grupo DROP COLUMN revision_periodica_habilitada;

COMMIT;
