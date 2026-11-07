#!/usr/bin/env bash
# Comprueba autenticación sin exigir root; los permisos sobre cada métrica los comprueba el análisis.
set -e
usuario=$(id -un)
equipo=$(hostname)
printf 'Acceso de evaluación verificado como %s en %s\n' "$usuario" "$equipo"
