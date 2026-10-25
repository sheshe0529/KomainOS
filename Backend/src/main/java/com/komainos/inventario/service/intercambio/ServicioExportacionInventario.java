package com.komainos.inventario.service.intercambio;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.EspecificacionesServidor;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.service.CredencialArchivo;
import com.komainos.inventario.service.PuertoCredencialesInventario;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.service.ServicioReautenticacion;
import com.komainos.shared.util.archivo.ColumnaArchivo;
import com.komainos.shared.util.archivo.DestinoArchivo;
import com.komainos.shared.util.archivo.EscritorTabular;
import com.komainos.shared.util.archivo.FormatoArchivo;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ServicioExportacionInventario {

    static final int LOTE = 500;
    /** El id desempata: con un orden total ningún servidor se repite ni se salta entre lotes */
    private static final Sort ORDEN = Sort.by("hostname").ascending().and(Sort.by("id").ascending());

    private final ServidorRepositorio servidores;
    private final EscritorTabular escritor;
    private final PuertoCredencialesInventario credenciales;
    private final ServicioReautenticacion reautenticacion;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;
    private final EntityManager entidades;

    /** Iguales para todos los roles: la credencial principal se agrega aparte, solo para el administrador (DEC-39) */
    public List<ColumnaInventario> columnasAutorizadas(AlcanceUsuario alcance) {
        return Arrays.asList(ColumnaInventario.values());
    }

    /**
     * Escribe el archivo en el destino a medida que lee el inventario, de a {@value #LOTE} servidores: ni las entidades
     * ni el archivo completo quedan en memoria. REPEATABLE_READ hace que todos los lotes vean el mismo inventario.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void exportar(FiltroServidores filtro, List<ColumnaInventario> columnas, FormatoArchivo formato,
                         AlcanceUsuario alcance, DestinoArchivo destino) throws IOException {
        generar(filtro, columnas, formato, alcance, false, destino);
    }

    /** RF13: la credencial principal va al final, en claro, y exige reautenticar al administrador (HU05 CA3) */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void exportarConCredencial(FiltroServidores filtro, List<ColumnaInventario> columnas, FormatoArchivo formato,
                                      AlcanceUsuario alcance, String contrasena, DestinoArchivo destino)
            throws IOException {
        reautenticacion.exigir(alcance.usuarioId(), contrasena);
        generar(filtro, columnas, formato, alcance, true, destino);
    }

    /**
     * Si la descarga se corta (el cliente cierra la conexión) la auditoría igual se registra, marcada como interrumpida:
     * pudo recibir parte del archivo. Por eso el IOException se propaga como excepción comprobada, que no revierte.
     */
    private void generar(FiltroServidores filtro, List<ColumnaInventario> columnas, FormatoArchivo formato,
                         AlcanceUsuario alcance, boolean conCredencial, DestinoArchivo destino) throws IOException {
        List<ColumnaInventario> elegidas = columnas == null || columnas.isEmpty()
                ? columnasAutorizadas(alcance)
                : columnas.stream().distinct().filter(columnasAutorizadas(alcance)::contains).toList();
        List<ColumnaArchivo> encabezado = new ArrayList<>(elegidas.stream().map(ColumnaInventario::comoColumnaArchivo).toList());
        if (conCredencial) {
            Arrays.stream(ColumnaCredencial.values()).map(ColumnaCredencial::comoColumnaArchivo).forEach(encabezado::add);
        }
        FilasPorLotes filas = new FilasPorLotes(EspecificacionesServidor.con(filtro, alcance), elegidas, conCredencial);
        String nombre = "inventario_servidores_%s.%s".formatted(LocalDate.now(reloj), formato.extension());

        try {
            escritor.escribir(formato, encabezado, filas, "Servidores", destino.abrir(nombre, formato));
        } catch (IOException ex) {
            auditar(alcance, formato, elegidas, filtro, conCredencial, filas, true);
            throw ex;
        }
        auditar(alcance, formato, elegidas, filtro, conCredencial, filas, false);
    }

    private void auditar(AlcanceUsuario alcance, FormatoArchivo formato, List<ColumnaInventario> elegidas,
                         FiltroServidores filtro, boolean conCredencial, FilasPorLotes filas, boolean interrumpida) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("formato", formato.name());
        detalle.put("columnas", elegidas.stream().map(ColumnaInventario::clave).toList());
        detalle.put("registros", filas.registros);
        detalle.put("credencialPrincipal", conCredencial);
        detalle.put("credencialesExportadas", filas.credenciales);
        detalle.put("filtro", filtro);
        if (interrumpida) {
            detalle.put("interrumpida", true);
        }
        auditoria.registrar(alcance.actor(), Operacion.de("EXPORTAR_INVENTARIO", "servidor", null)
                .valores(null, detalle));
    }

    /** Lee el lote siguiente cuando el escritor terminó el anterior, y suelta sus entidades del contexto de persistencia */
    private final class FilasPorLotes implements Iterator<Map<String, ?>> {

        private final Specification<Servidor> especificacion;
        private final List<ColumnaInventario> columnas;
        private final boolean conCredencial;
        private Iterator<Map<String, ?>> lote = Collections.emptyIterator();
        private int pagina;
        private boolean hayMas = true;
        private int registros;
        private int credenciales;

        private FilasPorLotes(Specification<Servidor> especificacion, List<ColumnaInventario> columnas,
                              boolean conCredencial) {
            this.especificacion = especificacion;
            this.columnas = columnas;
            this.conCredencial = conCredencial;
        }

        @Override
        public boolean hasNext() {
            while (!lote.hasNext() && hayMas) {
                lote = siguienteLote();
            }
            return lote.hasNext();
        }

        @Override
        public Map<String, ?> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return lote.next();
        }

        private Iterator<Map<String, ?>> siguienteLote() {
            Slice<Servidor> leidos = servidores.findAll(especificacion, PageRequest.of(pagina++, LOTE, ORDEN));
            hayMas = leidos.hasNext();
            List<Servidor> lista = leidos.getContent();
            Map<Integer, CredencialArchivo> principales = conCredencial
                    ? principalesDe(lista.stream().map(Servidor::getId).toList()) : Map.of();
            List<Map<String, ?>> filas = new ArrayList<>(lista.size());
            for (Servidor servidor : lista) {
                Map<String, Object> fila = new HashMap<>();
                columnas.forEach(c -> fila.put(c.clave(), c.valorDe(servidor)));
                if (conCredencial) {
                    fila.putAll(ColumnaCredencial.valores(principales.get(servidor.getId())));
                }
                filas.add(fila);
            }
            registros += lista.size();
            credenciales += principales.size();
            // El lote siguiente no arrastra las entidades del anterior. flush antes de soltarlas: si la exportación corre
            // dentro de otra transacción con cambios pendientes, no se pierden (en la propia no hay nada que escribir)
            entidades.flush();
            entidades.clear();
            return filas.iterator();
        }
    }

    private Map<Integer, CredencialArchivo> principalesDe(List<Integer> idsServidores) {
        return credenciales.principales(idsServidores);
    }
}
