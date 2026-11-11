#!/usr/bin/env bash
# Prueba de acceso de KomainOS (RF71, DEC-57). Si este script se ejecuta, el controlador llegó al servidor, inició
# sesión y aplicó la elevación configurada en la cuenta (ninguna, sudo o su): falta solo informar los permisos con los
# que se ejecutarán los scripts. No exige root; la última línea le dice a KomainOS si los tiene.
usuario=$(id -un)
uid=$(id -u)
equipo=$(hostname)
if [ "$uid" -eq 0 ]; then
    echo "Acceso verificado en $equipo: los scripts se ejecutarán como $usuario (uid 0), con privilegios de administrador"
    echo "KOMAINOS_ADMINISTRADOR=si"
    exit 0
fi
detalle="sin privilegios de administrador"
if sudo -n true 2>/dev/null; then
    detalle="$detalle; puede usar sudo sin contraseña: elija la elevación sudo en la cuenta para ejecutar como root"
fi
echo "Acceso verificado en $equipo: los scripts se ejecutarán como $usuario (uid $uid), $detalle"
echo "KOMAINOS_ADMINISTRADOR=no"
