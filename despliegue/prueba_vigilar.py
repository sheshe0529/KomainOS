"""Pruebas de la decisión de vigilar.py: cuándo un commit de main se despliega (DEC-58). python3 -m unittest"""
import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
os.environ.setdefault("KOMAINOS_ESTADO", tempfile.mkdtemp(prefix="komainos-estado-"))
import vigilar  # noqa: E402


def check(nombre, estado="completed", conclusion="success", inicio="2026-11-11T10:00:00Z"):
    return {"name": nombre, "status": estado, "conclusion": conclusion, "started_at": inicio}


TODAS = [check("pruebas / Pruebas del backend"), check("pruebas / Pruebas del panel"),
         check("pruebas / Pruebas del despliegue")]


class DecisionTest(unittest.TestCase):

    def test_todas_las_pruebas_aprobadas(self):
        self.assertEqual(vigilar.decidir(TODAS, 5), "aprobado")

    def test_el_job_que_espera_el_despliegue_no_cuenta(self):
        en_curso = TODAS + [check("Despliegue en el EC2", "in_progress", None)]
        self.assertEqual(vigilar.decidir(en_curso, 5), "aprobado")

    def test_una_prueba_en_curso_espera(self):
        runs = TODAS[:2] + [check("pruebas / Pruebas del despliegue", "in_progress", None)]
        self.assertEqual(vigilar.decidir(runs, 5), "pendiente")

    def test_falta_una_prueba(self):
        self.assertEqual(vigilar.decidir(TODAS[:2], 5), "pendiente")

    def test_una_prueba_fallida_rechaza(self):
        runs = [check("pruebas / Pruebas del backend", conclusion="failure")] + TODAS[1:]
        self.assertEqual(vigilar.decidir(runs, 5), "rechazado")

    def test_cancelada_rechaza(self):
        runs = TODAS[:2] + [check("pruebas / Pruebas del despliegue", conclusion="cancelled")]
        self.assertEqual(vigilar.decidir(runs, 5), "rechazado")

    def test_vale_la_ultima_ejecucion_de_un_job_repetido(self):
        runs = TODAS + [check("pruebas / Pruebas del backend", conclusion="failure", inicio="2026-11-11T09:00:00Z")]
        self.assertEqual(vigilar.decidir(runs, 5), "aprobado")

    def test_sin_pruebas_espera_y_luego_rechaza(self):
        self.assertEqual(vigilar.decidir([], 10), "pendiente")
        self.assertEqual(vigilar.decidir([], vigilar.MINUTOS_SIN_PRUEBAS + 1), "rechazado")


class PausaEntreConsultasTest(unittest.TestCase):

    def setUp(self):
        (vigilar.ESTADO / "consulta.json").unlink(missing_ok=True)

    def test_commit_nuevo_se_consulta(self):
        self.assertTrue(vigilar.debe_consultar("a" * 40, time.time()))

    def test_pendiente_espera_unos_dos_minutos(self):
        ahora = time.time()
        vigilar.escribir("consulta.json", json.dumps({"sha": "a" * 40, "estado": "pendiente", "momento": ahora}))
        self.assertFalse(vigilar.debe_consultar("a" * 40, ahora + 60))
        self.assertTrue(vigilar.debe_consultar("a" * 40, ahora + 120))
        self.assertTrue(vigilar.debe_consultar("b" * 40, ahora + 1))

    def test_rechazado_espera_quince_minutos(self):
        ahora = time.time()
        vigilar.escribir("consulta.json", json.dumps({"sha": "a" * 40, "estado": "rechazado", "momento": ahora}))
        self.assertFalse(vigilar.debe_consultar("a" * 40, ahora + 600))
        self.assertTrue(vigilar.debe_consultar("a" * 40, ahora + 901))


if __name__ == "__main__":
    unittest.main()
