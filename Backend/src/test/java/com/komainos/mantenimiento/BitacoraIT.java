package com.komainos.mantenimiento;

import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.Servidor;
import com.komainos.seguridad.model.Rol;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BitacoraIT extends BaseAutomatizacionIT {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    @DisplayName("RF53: la bitácora consulta por lotes aunque muestre cien órdenes")
    void cargaPorLotes() throws Exception {
        Servidor segundo = crear("srv-segundo", "10.90.0.2");
        Integer idGrupo = grupo("Grupo bitácora", ModoEjecucion.SECUENCIAL, servidor, segundo).getId();
        String rutaServidor = "/api/servidores/" + servidor.getId() + "/bitacora";
        String rutaGrupo = "/api/grupos/" + idGrupo + "/bitacora";
        Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        boolean habilitadas = estadisticas.isStatisticsEnabled();
        estadisticas.setStatisticsEnabled(true);
        try {
            agregarOrdenes(idGrupo, servidor.getId(), segundo.getId(), 1, 8);
            long servidorOcho = medir(estadisticas, rutaServidor, 8, 1);
            long grupoOcho = medir(estadisticas, rutaGrupo, 8, 2);

            agregarOrdenes(idGrupo, servidor.getId(), segundo.getId(), 9, 100);
            long servidorCien = medir(estadisticas, rutaServidor, 100, 1);
            long grupoCien = medir(estadisticas, rutaGrupo, 100, 2);

            assertThat(servidorCien).as("consultas del servidor con 100 órdenes").isEqualTo(servidorOcho);
            assertThat(grupoCien).as("consultas del grupo con 100 órdenes").isEqualTo(grupoOcho);

            agregarOrdenes(idGrupo, servidor.getId(), segundo.getId(), 101, 101);
            mvc.perform(get(rutaGrupo).with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mantenimientos.length()").value(100))
                    .andExpect(jsonPath("$.mantenimientos[0].codigo").value("BITACORA-101"))
                    .andExpect(jsonPath("$.mantenimientos[99].codigo").value("BITACORA-2"))
                    .andExpect(jsonPath("$.truncada").value(true));
        } finally {
            estadisticas.setStatisticsEnabled(habilitadas);
        }
    }

    @Test
    @DisplayName("RF53: el historial del grupo respeta el alcance antes de limitar las órdenes")
    void alcanceTrasReemplazarIntegrantes() throws Exception {
        Integer idGrupo = grupo("Grupo reasignado", ModoEjecucion.SECUENCIAL, servidor).getId();
        String ruta = "/api/grupos/" + idGrupo + "/bitacora";
        agregarOrdenes(idGrupo, servidor.getId(), null, 2, 102);

        var nuevoResponsable = usuarios.crear("resp.nuevo", "Nuevo responsable", "Clave-prueba-4", Rol.RESPONSABLE, actor);
        Servidor nuevo = crear("srv-nuevo", "10.90.0.2");
        jdbc.update("update servidor set id_usuario_responsable = ? where id_servidor = ?",
                nuevoResponsable.getId(), nuevo.getId());
        grupos.reemplazarIntegrantes(idGrupo, List.of(nuevo.getId()), actor);

        mvc.perform(get(ruta).with(como(nuevoResponsable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimientos").isEmpty())
                .andExpect(jsonPath("$.truncada").value(false));

        agregarOrdenes(idGrupo, nuevo.getId(), null, 1, 1);
        jdbc.update("""
                insert into historial_estado_orden (id_orden, estado_nuevo, fecha_hora)
                select id_orden, estado, fecha_inicio_programada from orden where id_grupo_mantenimiento = ?
                """, idGrupo);

        mvc.perform(get(ruta).with(como(nuevoResponsable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimientos.length()").value(1))
                .andExpect(jsonPath("$.mantenimientos[0].codigo").value("BITACORA-1"))
                .andExpect(jsonPath("$.mantenimientos[0].servidores[0].idServidor").value(nuevo.getId()))
                .andExpect(jsonPath("$.truncada").value(false));
        String csv = mvc.perform(get(ruta + "/exportacion").param("formato", "CSV").with(como(nuevoResponsable)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("BITACORA-1").doesNotContain("BITACORA-2", "BITACORA-102");

        for (var usuario : List.of(admin, operador)) {
            mvc.perform(get(ruta).with(como(usuario)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mantenimientos.length()").value(100))
                    .andExpect(jsonPath("$.mantenimientos[0].codigo").value("BITACORA-102"))
                    .andExpect(jsonPath("$.truncada").value(true));
        }
        mvc.perform(get(ruta).with(como(responsable))).andExpect(status().isNotFound());
        mvc.perform(get("/api/servidores/{id}/bitacora", servidor.getId()).with(como(responsable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimientos[0].codigo").value("BITACORA-102"));
    }

    private long medir(Statistics estadisticas, String ruta, int ordenes, int integrantes) throws Exception {
        // La primera solicitud prepara el contexto web y las consultas. Se mide la siguiente.
        mvc.perform(get(ruta).with(como(admin))).andExpect(status().isOk());
        estadisticas.clear();
        long inicio = System.nanoTime();
        mvc.perform(get(ruta).with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mantenimientos.length()").value(ordenes))
                .andExpect(jsonPath("$.mantenimientos[0].codigo").value("BITACORA-" + ordenes))
                .andExpect(jsonPath("$.mantenimientos[0].servidores.length()").value(integrantes))
                .andExpect(jsonPath("$.truncada").value(false));
        long consultas = estadisticas.getPrepareStatementCount();
        System.out.printf("Bitácora %s, %d órdenes: %d consultas, %.1f ms%n",
                ruta, ordenes, consultas, (System.nanoTime() - inicio) / 1_000_000.0);
        return consultas;
    }

    private void agregarOrdenes(Integer idGrupo, Integer primero, Integer segundo, int desde, int hasta) {
        jdbc.update("""
                with nuevas as (
                    insert into orden (codigo, id_grupo_mantenimiento, id_nivel_criticidad, origen,
                        modo_ejecucion_aplicado, fecha_objetivo, fecha_inicio_programada, fecha_fin_programada,
                        fecha_evaluacion_programada, inicio_ventana_aplicada, fin_ventana_aplicada)
                    select 'BITACORA-' || n, ?, ?, 'SOLICITUD_BAJO_DEMANDA', 'SECUENCIAL',
                        fecha, fecha, fecha, fecha, fecha, fecha
                    from generate_series(?, ?) as numeros(n)
                    cross join lateral (select timestamp '2026-09-28' + n * interval '1 day' as fecha) f
                    returning id_orden
                )
                insert into orden_detalle (id_orden, id_servidor, posicion_ejecucion)
                select nuevas.id_orden, integrante.id, integrante.posicion
                from nuevas cross join (values (cast(? as integer), 1), (cast(? as integer), 2)) integrante(id, posicion)
                where integrante.id is not null
                """, idGrupo, criticidad, desde, hasta, primero, segundo);
    }
}
