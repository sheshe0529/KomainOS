-- Separa las cuentas de evaluación y mantenimiento conservando las asignaciones existentes.
-- Aplicar una vez en una base anterior a este cambio. No borra órdenes ni credenciales.
BEGIN;
ALTER TABLE "KomainOS".configuracion_mantenimiento RENAME COLUMN id_cuenta_servicio TO id_cuenta_servicio_mantenimiento;
ALTER TABLE "KomainOS".configuracion_mantenimiento ADD COLUMN id_cuenta_servicio_evaluacion integer REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
UPDATE "KomainOS".configuracion_mantenimiento SET id_cuenta_servicio_evaluacion = id_cuenta_servicio_mantenimiento;
ALTER TABLE "KomainOS".configuracion_sistema RENAME COLUMN id_cuenta_servicio_predeterminada TO id_cuenta_servicio_mantenimiento_predeterminada;
ALTER TABLE "KomainOS".configuracion_sistema ADD COLUMN id_cuenta_servicio_evaluacion_predeterminada integer REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
UPDATE "KomainOS".configuracion_sistema SET id_cuenta_servicio_evaluacion_predeterminada = id_cuenta_servicio_mantenimiento_predeterminada;
ALTER TABLE "KomainOS".orden RENAME COLUMN id_cuenta_servicio TO id_cuenta_servicio_mantenimiento;
ALTER TABLE "KomainOS".orden ADD COLUMN id_cuenta_servicio_evaluacion integer REFERENCES "KomainOS".cuenta_servicio(id_credencial) ON UPDATE RESTRICT ON DELETE RESTRICT;
-- Solo se copia la cuenta original registrada. Los nulos históricos siguen desconocidos;
-- una orden PROGRAMADA puede completar los faltantes desde su configuración antes de evaluarse.
UPDATE "KomainOS".orden SET id_cuenta_servicio_evaluacion = id_cuenta_servicio_mantenimiento;
COMMIT;
