-- Cambio 2026-10-01 (DEC-39) para bases creadas antes: se ejecuta una sola vez y conserva los datos
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'KomainOS' AND table_name = 'credencial_version' AND column_name = 'tipo_usuario') THEN
        RAISE EXCEPTION 'El cambio ya fue aplicado en esta base (credencial_version.tipo_usuario existe)';
    END IF;
END $$;

CREATE TYPE enum_tipo_usuario AS ENUM ('ADMINISTRADOR', 'GENERICO');

ALTER TABLE credencial_version
    ADD COLUMN tipo_usuario enum_tipo_usuario,
    ADD COLUMN su_secreto_cifrado bytea,
    ADD COLUMN su_iv_nonce bytea,
    ADD COLUMN su_tag_autenticacion bytea,
    ADD CONSTRAINT credencial_version_su_iv_nonce_check CHECK ((octet_length(su_iv_nonce) <= 64)),
    ADD CONSTRAINT credencial_version_su_tag_autenticacion_check CHECK ((octet_length(su_tag_autenticacion) <= 64)),
    ADD CONSTRAINT ck_credencial_version_su CHECK ((((su_secreto_cifrado IS NULL) = (su_iv_nonce IS NULL)) AND ((su_secreto_cifrado IS NULL) = (su_tag_autenticacion IS NULL)) AND ((tipo_usuario IS NOT DISTINCT FROM 'GENERICO'::enum_tipo_usuario) = (su_secreto_cifrado IS NOT NULL))));

-- Las credenciales documentales de servidores Linux ya registradas no tenían contraseña su: quedan como Administrador
UPDATE credencial_version v
SET tipo_usuario = 'ADMINISTRADOR'
FROM credencial_documental d
JOIN servidor s ON s.id_servidor = d.id_servidor
JOIN version_sistema_operativo vso ON vso.id_version_sistema_operativo = s.id_version_sistema_operativo
JOIN sistema_operativo so ON so.id_sistema_operativo = vso.id_sistema_operativo
WHERE v.id_credencial = d.id_credencial AND so.familia = 'LINUX';

ALTER TABLE credencial_documental
    ADD COLUMN principal boolean DEFAULT false NOT NULL;
ALTER TABLE ONLY credencial_documental
    ADD CONSTRAINT ex_credencial_documental_principal EXCLUDE USING btree (id_servidor WITH =) WHERE (principal) DEFERRABLE INITIALLY DEFERRED;

-- La principal de cada servidor es su credencial vigente más antigua
UPDATE credencial_documental d
SET principal = true
WHERE d.id_credencial IN (
    SELECT DISTINCT ON (cd.id_servidor) cd.id_credencial
    FROM credencial_documental cd
    JOIN credencial c ON c.id_credencial = cd.id_credencial
    WHERE c.estado = 'VIGENTE'
    ORDER BY cd.id_servidor, c.fecha_registro, c.id_credencial);

COMMIT;
