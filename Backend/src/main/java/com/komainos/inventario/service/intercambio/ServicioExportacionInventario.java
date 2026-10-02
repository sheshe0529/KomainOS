package com.komainos.inventario.service.intercambio;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.EspecificacionesServidor;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.util.archivo.ArchivoGenerado;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FormatoArchivo;
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

@Service
@RequiredArgsConstructor
public class ServicioExportacionInventario {

    private final ServidorRepositorio servidores;
    private final EscritorTabular escritor;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /** Iguales para todos los roles hasta implementar las credenciales (RF08, DEC-31) */
    public List<ColumnaInventario> columnasAutorizadas(AlcanceUsuario alcance) {
        return Arrays.asList(ColumnaInventario.values());
    }

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
