#!/usr/bin/env bash
# Liberar archivos temporales (KomainOS · mantenimiento).
# Parámetros que KomainOS define antes de este código:
#   RUTA_TEMPORAL   carpeta que se limpia, por ejemplo /tmp
#   DIAS_RETENCION  se eliminan los archivos sin modificar hace más de estos días
#   SOLO_SIMULAR    true: informa lo que eliminaría sin borrar nada
set -eu

if [ ! -d "$RUTA_TEMPORAL" ]; then
    echo "La carpeta $RUTA_TEMPORAL no existe" >&2
    exit 2
fi
case "$RUTA_TEMPORAL" in
    / | /bin | /boot | /dev | /etc | /home | /lib | /lib64 | /proc | /root | /sbin | /sys | /usr | /var)
        echo "Por seguridad no se limpia $RUTA_TEMPORAL" >&2
        exit 2
        ;;
esac

cantidad=$(find "$RUTA_TEMPORAL" -xdev -type f -mtime +"$DIAS_RETENCION" | wc -l)
if [ "$SOLO_SIMULAR" = true ]; then
    echo "Simulación: se eliminarían $cantidad archivo(s) de $RUTA_TEMPORAL sin cambios en $DIAS_RETENCION días"
    find "$RUTA_TEMPORAL" -xdev -type f -mtime +"$DIAS_RETENCION" -printf '%TY-%Tm-%Td  %10s  %p\n' | head -n 50
    exit 0
fi

libre_antes=$(df -Pk "$RUTA_TEMPORAL" | awk 'NR == 2 { print $4 }')
find "$RUTA_TEMPORAL" -xdev -type f -mtime +"$DIAS_RETENCION" -delete
libre_despues=$(df -Pk "$RUTA_TEMPORAL" | awk 'NR == 2 { print $4 }')
echo "Se eliminaron $cantidad archivo(s) de $RUTA_TEMPORAL; espacio liberado: $(((libre_despues - libre_antes) / 1024)) MB"
