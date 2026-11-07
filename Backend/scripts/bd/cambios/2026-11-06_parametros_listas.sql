-- RF25/RF26: entradas explícitas de script, escalares con valor y listas del catálogo.
-- Aplicar una vez sobre una base existente; no modifica código ni valores históricos.
BEGIN;

CREATE TYPE "KomainOS".enum_tipo_parametro_script AS ENUM
    ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'LISTA');

ALTER TABLE "KomainOS".script_parametro
    ALTER COLUMN tipo_dato TYPE "KomainOS".enum_tipo_parametro_script
        USING tipo_dato::text::"KomainOS".enum_tipo_parametro_script,
    DROP COLUMN obligatorio,
    ADD COLUMN id_lista_referencia integer,
    ADD CONSTRAINT fk_script_parametro_lista FOREIGN KEY (id_lista_referencia)
        REFERENCES "KomainOS".lista_referencia(id_lista_referencia) ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT ck_script_parametro_fuente CHECK (
        (tipo_dato = 'LISTA' AND id_lista_referencia IS NOT NULL AND valor_por_defecto IS NULL)
        OR (tipo_dato <> 'LISTA' AND id_lista_referencia IS NULL)
    );

-- false conserva la inyección automática de listas para versiones previamente publicadas.
-- No se reescriben versiones fijadas en MOP ni los escalares históricos NULL: siguen usando vacío/null.
ALTER TABLE "KomainOS".script_version ADD COLUMN listas_explicitas boolean NOT NULL DEFAULT false;
ALTER TABLE "KomainOS".script_version ALTER COLUMN listas_explicitas SET DEFAULT true;

COMMIT;
