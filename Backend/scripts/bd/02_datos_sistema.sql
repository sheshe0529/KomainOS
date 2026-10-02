-- 02 - Datos que el sistema necesita para operar (DEC-21, DEC-34). Es idempotente

-- RF64: factor que ajusta la periodicidad base según el resultado del ciclo
INSERT INTO "KomainOS".factor_ciclo (resultado, factor) VALUES
    ('INCIDENCIA',    0.5000),
    ('SALUD_REGULAR', 0.7500),
    ('ESTADO_NORMAL', 1.0000),
    ('RACHA_ESTABLE', 1.2500)
ON CONFLICT (resultado) DO NOTHING;

-- Valores iniciales editables por el administrador: concurrencia, duraciones máximas (RF68), racha estable y vigencia del token
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
