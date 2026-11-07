package com.komainos.mantenimiento;

import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.mantenimiento.service.ServicioEvolucionDashboard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EvolucionDashboardIT extends BaseAutomatizacionIT {
    @Autowired ServicioEvolucionDashboard evolucion;

    @Test
    void agrupaDoceMesesConAcumuladoYUnConteoPorServidor() throws Exception {
        var segundo = crear("srv-evolucion-2", "10.90.1.2");
        var tercero = crear("srv-evolucion-3", "10.90.1.3");
        var grupo = grupo("Evolución IT", ModoEjecucion.SECUENCIAL, servidor, segundo, tercero);
        Integer orden = programacion.inmediata(null, grupo.getId(), false, "Datos de evolución", null, actor).getId();
        jdbc.update("update servidor set fecha_alta = '2024-01-01' where id_servidor = ?", servidor.getId());
        jdbc.update("update servidor set fecha_alta = '2026-08-15' where id_servidor = ?", segundo.getId());
        jdbc.update("update servidor set fecha_alta = '2026-09-15', estado = 'DADO_DE_BAJA' where id_servidor = ?", tercero.getId());
        jdbc.update("""
                update orden_detalle set estado = 'FINALIZADO', fecha_real_inicio = '2026-08-31 23:00:00',
                    fecha_real_fin = '2026-09-01 01:00:00' where id_orden = ? and id_servidor = ?
                """, orden, servidor.getId());
        jdbc.update("""
                update orden_detalle set estado = 'FALLIDO', fecha_real_inicio = '2026-08-15 12:00:00',
                    fecha_real_fin = '2026-08-15 12:01:00' where id_orden = ? and id_servidor = ?
                """, orden, segundo.getId());
        // Un detalle no iniciado y sin fecha de inicio no se convierte en mantenimiento ejecutado.
        jdbc.update("update orden_detalle set estado = 'NO_INICIADO' where id_orden = ? and id_servidor = ?", orden, tercero.getId());

        var serie = evolucion.consultar(AHORA);
        assertThat(serie.servidores()).hasSize(12);
        assertThat(serie.servidores().getFirst().registrados()).isEqualTo(1);
        assertThat(serie.servidores().get(10).registrados()).isEqualTo(2);
        assertThat(serie.servidores().getLast().registrados()).isEqualTo(3);
        assertThat(serie.mantenimientos().get(10).fallidos()).isEqualTo(1);
        assertThat(serie.mantenimientos().getLast().finalizados()).isEqualTo(1);
        assertThat(serie.mantenimientos().stream().mapToLong(m -> m.finalizados() + m.fallidos()).sum()).isEqualTo(2);
        mvc.perform(get("/api/automatizacion/graficos").with(como(operador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.servidoresPorMes.length()").value(12))
                .andExpect(jsonPath("$.mantenimientosEjecutadosPorMes[-1].finalizados").value(1));
        mvc.perform(get("/api/automatizacion/graficos").with(como(responsable))).andExpect(status().isForbidden());
    }
}
