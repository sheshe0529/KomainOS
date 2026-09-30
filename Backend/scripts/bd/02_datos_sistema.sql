-- =============================================================================
-- 02 - Datos que el sistema necesita para operar (DEC-21, DEC-34)
--
-- Solo valores con respaldo documental o parametros globales sin los cuales la
-- planificacion no puede funcionar. Se ejecuta una vez, despues de 01_esquema.sql;
-- es idempotente (ON CONFLICT DO NOTHING). Los catalogos de negocio (entornos,
-- criticidades, sistemas operativos) los registra el administrador o el script
-- de datos de prueba 03.
-- =============================================================================

-- RF64: factor que ajusta la periodicidad base segun el resultado del ciclo.
INSERT INTO "KomainOS".factor_ciclo (resultado, factor) VALUES
    ('INCIDENCIA',    0.5000),
    ('SALUD_REGULAR', 0.7500),
    ('ESTADO_NORMAL', 1.0000),
    ('RACHA_ESTABLE', 1.2500)
ON CONFLICT (resultado) DO NOTHING;

-- RF68: duraciones maximas por tarea y por MOP, inicialmente 60 y 240 minutos.
-- Concurrencia (10) y racha estable (5 ciclos) se toman de la pantalla
-- preliminar "Politicas y parametros"; la vigencia del token (60 min) es un
-- supuesto. Todos son editables por el administrador.
INSERT INTO "KomainOS".configuracion_sistema (
    id_configuracion_sistema,
    id_cuenta_servicio_predeterminada,
    max_ejecuciones_concurrentes,
    max_duracion_tarea_minutos,
    max_duracion_mop_minutos,
    min_ciclos_racha_estable,
    minutos_expiracion_token
) VALUES (1, NULL, 10, 60, 240, 5, 60)
ON CONFLICT (id_configuracion_sistema) DO NOTHING;
