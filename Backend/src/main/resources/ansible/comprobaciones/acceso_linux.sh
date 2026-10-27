#!/usr/bin/env bash
# Comprobación de acceso de KomainOS (RF71): conectividad, autenticación y permisos de administrador.
# Termina con 0 si la cuenta es root o puede usar sudo sin contraseña, y con 3 si no tiene privilegios.
if [ "$(id -u)" -eq 0 ] || sudo -n true 2>/dev/null; then
    echo "Acceso verificado como $(id -un) en $(hostname)"
    exit 0
fi
echo "La cuenta $(id -un) se autenticó, pero no es root ni puede usar sudo sin contraseña" >&2
exit 3
