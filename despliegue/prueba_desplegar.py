"""Verifica el orden de respaldo y migracion sin Docker ni una base real."""
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


def ruta_bash(ruta):
    texto = Path(ruta).as_posix()
    return '/' + texto[0].lower() + texto[2:] if os.name == 'nt' else texto


class RespaldoAntesDeMigrarTest(unittest.TestCase):
    def ejecutar(self, fallo='', migracion=True):
        with tempfile.TemporaryDirectory(prefix='komainos-despliegue-test-') as temporal:
            raiz = Path(temporal)
            repo = raiz / 'repo'
            cambios = repo / 'Backend/scripts/bd/cambios'
            cambios.mkdir(parents=True)
            if migracion:
                (cambios / '2026-11-15_prueba.sql').write_text('BEGIN; COMMIT;\n', encoding='utf-8')
            (repo / '.env').touch()
            (repo / 'iniciar.sh').write_text('#!/usr/bin/env bash\nprintf "iniciar\\n" >> "$EVENTOS"\n',
                                           encoding='utf-8', newline='\n')
            estado = raiz / 'estado'
            estado.mkdir()
            (estado / 'migraciones_aplicadas').touch()
            eventos = raiz / 'eventos'
            entorno = dict(os.environ, KOMAINOS_REPO=ruta_bash(repo), KOMAINOS_ESTADO=ruta_bash(estado),
                           EVENTOS=ruta_bash(eventos), FALLO=fallo)
            guion = r'''
source "$1"
git() { printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n'; }
flock() { return 0; }
cargar_bd() { BD_HOST=prueba; BD_PUERTO=5432; BD_NOMBRE=prueba; BD_USUARIO=prueba; BD_CLAVE=prueba; }
esperar_version() { return 0; }
psql_bd() {
    if [[ $* == *server_version_num* ]]; then printf '180003\n'; return 0; fi
    printf 'migracion\n' >> "$EVENTOS"
    [[ $FALLO != migracion ]]
}
docker() {
    if [[ $* == *pg_dump* ]]; then
        printf 'volcado\n' >> "$EVENTOS"
        [[ $FALLO != volcado ]] || return 1
        touch "$ESTADO/respaldos/$(basename "${@: -1}")"
    elif [[ $* == *pg_restore* ]]; then
        printf 'verificar\n' >> "$EVENTOS"
        [[ $FALLO != verificar ]] || return 1
    elif [[ $1 == stop ]]; then
        printf 'detener\n' >> "$EVENTOS"
    elif [[ $1 == start ]]; then
        printf 'reiniciar\n' >> "$EVENTOS"
    elif [[ $1 == image && $2 == inspect ]]; then
        printf 'imagen\n'
    fi
    return 0
}
chmod +x "$REPO/iniciar.sh"
main aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
'''
            bash = os.environ.get('BASH_PRUEBAS') or shutil.which('bash')
            resultado = subprocess.run([bash, '-c', guion, 'prueba', ruta_bash(Path(__file__).with_name('desplegar.sh').resolve())],
                                       env=entorno, capture_output=True, text=True, timeout=30)
            eventos_leidos = eventos.read_text().splitlines() if eventos.exists() else []
            aplicadas = (estado / 'migraciones_aplicadas').read_text().splitlines()
            return resultado, eventos_leidos, aplicadas

    def test_detiene_antes_del_respaldo_y_verifica_antes_de_migrar(self):
        resultado, eventos, aplicadas = self.ejecutar()
        self.assertEqual(resultado.returncode, 0, resultado.stdout + resultado.stderr)
        self.assertEqual(eventos, ['detener', 'volcado', 'verificar', 'migracion', 'iniciar'])
        self.assertEqual(aplicadas, ['2026-11-15_prueba.sql'])

    def test_volcado_fallido_reinicia_sin_migrar(self):
        resultado, eventos, aplicadas = self.ejecutar('volcado')
        self.assertNotEqual(resultado.returncode, 0)
        self.assertEqual(eventos, ['detener', 'volcado', 'reiniciar'])
        self.assertEqual(aplicadas, [])

    def test_respaldo_ilegible_reinicia_sin_migrar(self):
        resultado, eventos, aplicadas = self.ejecutar('verificar')
        self.assertNotEqual(resultado.returncode, 0)
        self.assertEqual(eventos, ['detener', 'volcado', 'verificar', 'reiniciar'])
        self.assertEqual(aplicadas, [])

    def test_migracion_fallida_reinicia_sin_marcarla_aplicada(self):
        resultado, eventos, aplicadas = self.ejecutar('migracion')
        self.assertNotEqual(resultado.returncode, 0)
        self.assertEqual(eventos, ['detener', 'volcado', 'verificar', 'migracion', 'reiniciar'])
        self.assertEqual(aplicadas, [])

    def test_sin_migraciones_no_detiene_para_respaldar(self):
        resultado, eventos, aplicadas = self.ejecutar(migracion=False)
        self.assertEqual(resultado.returncode, 0, resultado.stdout + resultado.stderr)
        self.assertEqual(eventos, ['volcado', 'verificar', 'iniciar'])
        self.assertEqual(aplicadas, [])


if __name__ == '__main__':
    unittest.main()
