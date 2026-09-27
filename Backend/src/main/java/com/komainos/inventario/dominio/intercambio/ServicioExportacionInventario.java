package com.komainos.inventario.dominio.intercambio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.dominio.FiltroServidores;
import com.komainos.inventario.dominio.Servidor;
import com.komainos.inventario.infra.EspecificacionesServidor;
import com.komainos.inventario.infra.ServidorRepositorio;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.shared.archivos.ArchivoGenerado;
import com.komainos.shared.archivos.EscritorTabular;
import com.komainos.shared.archivos.FormatoArchivo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportación del inventario (RF13, HU09).
 *
 * <p>Se exportan los servidores que el usuario puede ver, con el mismo filtro
 * del listado (RF11) y el alcance por rol aplicado en la consulta: un
 * responsable solo obtiene los suyos. Las columnas autorizadas son las de
 * {@link ColumnaInventario}; ninguna contiene secretos (RNF12).
 */
@Service
@RequiredArgsConstructor
public class ServicioExportacionInventario {

    private final ServidorRepositorio servidores;
    private final EscritorTabular escritor;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /**
     * Columnas que el usuario puede exportar (HU09 CA2). En esta iteración
     * son las mismas para todos los roles: las credenciales, únicas columnas
     * restringidas, dependen de RF08 (DEC-31).
     */
    public List<ColumnaInventario> columnasAutorizadas(AlcanceUsuario alcance) {
        return Arrays.asList(ColumnaInventario.values());
    }

    /**
     * Genera el archivo (HU09 CA1, CA3) y deja constancia en la auditoría de
     * quién exportó, qué columnas y cuántos registros.
     *
     * @param columnas vacía = todas las autorizadas, en su orden natural
     */
    @Transactional
    public ArchivoGenerado exportar(FiltroServidores filtro, List<ColumnaInventario> columnas,
                                    FormatoArchivo formato, AlcanceUsuario alcance) {
        List<ColumnaInventario> elegidas = columnas == null || columnas.isEmpty()
                ? columnasAutorizadas(alcance)
                : columnas.stream().distinct().filter(columnasAutorizadas(alcance)::contains).toList();

        List<Servidor> lista = servidores.findAll(EspecificacionesServidor.con(filtro, alcance),
                Sort.by("hostname").ascending());
        List<Map<String, ?>> filas = new ArrayList<>(lista.size());
        for (Servidor servidor : lista) {
            Map<String, Object> fila = new HashMap<>();
            elegidas.forEach(c -> fila.put(c.clave(), c.valorDe(servidor)));
            filas.add(fila);
        }
        byte[] contenido = escritor.escribir(formato,
                elegidas.stream().map(ColumnaInventario::comoColumnaArchivo).toList(), filas, "Servidores");

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("formato", formato.name());
        detalle.put("columnas", elegidas.stream().map(ColumnaInventario::clave).toList());
        detalle.put("registros", lista.size());
        detalle.put("filtro", filtro);
        auditoria.registrar(alcance.actor(), Operacion.de("EXPORTAR_INVENTARIO", "servidor", null)
                .valores(null, detalle));

        String nombre = "inventario_servidores_%s.%s".formatted(LocalDate.now(reloj), formato.extension());
        return new ArchivoGenerado(nombre, formato, contenido);
    }
}
