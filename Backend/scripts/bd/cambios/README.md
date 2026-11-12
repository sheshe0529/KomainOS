# Cambios de esquema para la base en servicio

Esta carpeta está vacía a propósito: la base en servicio (RDS) ya tiene el esquema de `../01_esquema.sql`, que es el
único DDL vigente. Aquí va cada cambio de esquema **nuevo**, y el despliegue automático lo aplica en RDS al hacer merge a
`main`, después de respaldar la base (`despliegue/desplegar.sh`, DEC-58).

Para cambiar el esquema:

1. Modifique `../01_esquema.sql`: es lo que usan una base nueva y las pruebas de integración.
2. Agregue aquí el mismo cambio como `AAAA-MM-DD_descripcion.sql` para la base que ya existe. Se aplican en orden de
   nombre y cada uno una sola vez (el EC2 lleva la cuenta en `~/komainos-estado/migraciones_aplicadas`).
3. Escríbalo dentro de `BEGIN; ... COMMIT;` con `SET LOCAL search_path TO "KomainOS";`, y que falle si ya se aplicó
   (por ejemplo, comprobando en un bloque `DO` que la tabla o columna nueva no existe). Si falla, se deshace completo y
   la versión anterior sigue en servicio.
4. Pruébelo antes sobre una copia de la base, nunca sobre la base en servicio.
