package com.komainos.mantenimiento;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.mantenimiento.service.ServicioCierreMantenimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resultado del ciclo y factor de ajuste (RF45, RF64, RF65)")
class ReglasCierreTest {

    @Test
    @DisplayName("Una incidencia manda sobre la racha y la salud (factor 0,50)")
    void incidencia() {
        assertThat(ServicioCierreMantenimiento.resultado(true, true, NivelAlerta.NORMAL, 20, 5))
                .isEqualTo(ServicioCierreMantenimiento.INCIDENCIA);
    }

    @Test
    @DisplayName("Métricas fuera de lo normal al cerrar acortan el ciclo (factor 0,75)")
    void saludRegular() {
        assertThat(ServicioCierreMantenimiento.resultado(false, true, NivelAlerta.ADVERTENCIA, 20, 5))
                .isEqualTo(ServicioCierreMantenimiento.SALUD_REGULAR);
    }

    @Test
    @DisplayName("La racha estable exige conformidad y la cantidad mínima de ciclos (factor 1,25)")
    void racha() {
        assertThat(ServicioCierreMantenimiento.resultado(false, false, NivelAlerta.NORMAL, 20, 5))
                .isEqualTo(ServicioCierreMantenimiento.ESTADO_NORMAL);
        assertThat(ServicioCierreMantenimiento.resultado(false, true, NivelAlerta.NORMAL, 4, 5))
                .isEqualTo(ServicioCierreMantenimiento.ESTADO_NORMAL);
        assertThat(ServicioCierreMantenimiento.resultado(false, true, NivelAlerta.NORMAL, 5, 5))
                .isEqualTo(ServicioCierreMantenimiento.RACHA_ESTABLE);
    }

    @Test
    @DisplayName("La afectación de la orden es la mayor de sus tareas")
    void afectacion() {
        assertThat(NivelAfectacion.SIN_AFECTACION.mayor(NivelAfectacion.TOTAL)).isEqualTo(NivelAfectacion.TOTAL);
        assertThat(NivelAfectacion.PARCIAL.mayor(NivelAfectacion.SIN_AFECTACION)).isEqualTo(NivelAfectacion.PARCIAL);
    }
}
