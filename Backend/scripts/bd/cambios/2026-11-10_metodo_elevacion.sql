-- Cambio 2026-11-10 (DEC-57) para bases creadas antes: se ejecuta una sola vez y conserva los datos.
-- El «tipo de usuario» (Administrador o Genérico con contraseña su) pasa a ser el método de elevación de privilegios
-- que Ansible aplica en cada ejecución: NINGUNO, SUDO (con o sin contraseña), SU (contraseña de root) o RUNAS (Windows,
-- con la misma cuenta). La contraseña su pasa a ser la contraseña de elevación, con el mismo cifrado: el dato asociado
-- del cifrado no cambia, así que los secretos ya guardados se siguen descifrando.
--
-- Equivalencias:
--   Genérico (con contraseña su)                     -> SU, conserva la contraseña
--   Administrador, credencial documental, no root    -> SUDO sin contraseña (lo que documentaba: admin por sudo)
--   Administrador root, o cuenta de servicio sin su  -> NINGUNO (las cuentas de servicio nunca se elevaban sin su)
--   Sin tipo (Windows)                               -> NINGUNO
--
-- La columna tipo_usuario se convierte y se renombra en lugar de reemplazarse, para que la tabla conserve el mismo
-- orden de columnas que 01_esquema.sql.
BEGIN;
SET LOCAL search_path TO "KomainOS";

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'KomainOS' AND table_name = 'credencial_secreto'
                 AND column_name = 'metodo_elevacion') THEN
        RAISE EXCEPTION 'El cambio ya fue aplicado en esta base (credencial_secreto.metodo_elevacion existe)';
    END IF;
END $$;

CREATE TYPE enum_metodo_elevacion AS ENUM ('NINGUNO', 'SUDO', 'SU', 'RUNAS');

ALTER TABLE credencial_secreto DROP CONSTRAINT ck_credencial_secreto_su;
ALTER TABLE credencial_secreto ALTER COLUMN tipo_usuario TYPE text USING tipo_usuario::text;

UPDATE credencial_secreto s
SET tipo_usuario = CASE
        WHEN s.tipo_usuario = 'GENERICO' THEN 'SU'
        WHEN s.tipo_usuario = 'ADMINISTRADOR'
             AND EXISTS (SELECT 1 FROM credencial_documental d WHERE d.id_credencial = s.id_credencial)
             AND lower(c.usuario_acceso) <> 'root' THEN 'SUDO'
        ELSE 'NINGUNO'
    END
FROM credencial c
WHERE c.id_credencial = s.id_credencial;

ALTER TABLE credencial_secreto
    ALTER COLUMN tipo_usuario TYPE enum_metodo_elevacion USING tipo_usuario::enum_metodo_elevacion;
ALTER TABLE credencial_secreto RENAME COLUMN tipo_usuario TO metodo_elevacion;
-- Después de renombrar: PostgreSQL 18 nombra la restricción NOT NULL con el nombre de la columna
ALTER TABLE credencial_secreto ALTER COLUMN metodo_elevacion SET NOT NULL;
ALTER TABLE credencial_secreto RENAME COLUMN su_secreto_cifrado TO elevacion_secreto_cifrado;
ALTER TABLE credencial_secreto RENAME COLUMN su_iv_nonce TO elevacion_iv_nonce;
ALTER TABLE credencial_secreto RENAME COLUMN su_tag_autenticacion TO elevacion_tag_autenticacion;
ALTER TABLE credencial_secreto
    RENAME CONSTRAINT credencial_secreto_su_iv_nonce_check TO credencial_secreto_elevacion_iv_nonce_check;
ALTER TABLE credencial_secreto
    RENAME CONSTRAINT credencial_secreto_su_tag_autenticacion_check TO credencial_secreto_elevacion_tag_autenticacion_check;

-- SU exige la contraseña de root; NINGUNO y RUNAS no guardan contraseña de elevación; SUDO la admite opcionalmente
ALTER TABLE credencial_secreto ADD CONSTRAINT ck_credencial_secreto_elevacion CHECK ((((elevacion_secreto_cifrado IS NULL) = (elevacion_iv_nonce IS NULL)) AND ((elevacion_secreto_cifrado IS NULL) = (elevacion_tag_autenticacion IS NULL)) AND ((metodo_elevacion <> 'SU'::enum_metodo_elevacion) OR (elevacion_secreto_cifrado IS NOT NULL)) AND ((metodo_elevacion = ANY (ARRAY['SUDO'::enum_metodo_elevacion, 'SU'::enum_metodo_elevacion])) OR (elevacion_secreto_cifrado IS NULL))));

DROP TYPE enum_tipo_usuario;

SELECT metodo_elevacion, count(*) AS credenciales, count(elevacion_secreto_cifrado) AS con_contrasena_de_elevacion
FROM credencial_secreto GROUP BY metodo_elevacion ORDER BY metodo_elevacion;

COMMIT;
