-- RNF06/RF67: índices para la consulta administrativa y la evolución temporal.
-- No altera datos ni crea un historial retrospectivo. Aplicar sobre una base existente (DEC-34).
BEGIN;
CREATE INDEX IF NOT EXISTS ix_auditoria_fecha_id ON "KomainOS".auditoria (fecha_hora DESC, id_auditoria DESC);
CREATE INDEX IF NOT EXISTS ix_auditoria_usuario_fecha ON "KomainOS".auditoria (id_usuario, fecha_hora DESC);
CREATE INDEX IF NOT EXISTS ix_auditoria_operacion_fecha ON "KomainOS".auditoria (operacion, fecha_hora DESC);
CREATE INDEX IF NOT EXISTS ix_auditoria_entidad_fecha ON "KomainOS".auditoria (entidad, fecha_hora DESC);
CREATE INDEX IF NOT EXISTS ix_servidor_fecha_alta ON "KomainOS".servidor (fecha_alta);
CREATE INDEX IF NOT EXISTS ix_detalle_fin_ejecutado ON "KomainOS".orden_detalle (fecha_real_fin)
    WHERE fecha_real_inicio IS NOT NULL AND estado IN ('FINALIZADO', 'FALLIDO');
COMMIT;
