package dev.vigia.backend.deteccion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

/**
 * Los tres microservicios de ML (ml-celo, ml-salud y ml-geo) simulados en un servidor HTTP local: el worker les habla
 * con sus clientes reales (RestClient, Resilience4j y el JSON de los DTO), y cada test decide qué responde cada ruta y
 * revisa qué le mandaron. Una ruta sin respuesta cargada devuelve 501, así que una llamada que el test no esperaba no
 * pasa desapercibida.
 */
final class MlSimulado {

    static final String CELO_PREDICT = "/api/internal/ml/celo/predict";
    static final String SALUD_PREDICT = "/api/internal/ml/salud/predict";
    static final String SALUD_PERFIL = "/api/internal/ml/salud/perfil";
    static final String GEO_ANALYZE = "/api/internal/ml/geo/analyze";

    private final HttpServer servidor;
    /** Por ruta: del cuerpo recibido a la respuesta JSON. */
    private final Map<String, UnaryOperator<String>> respuestas = new ConcurrentHashMap<>();
    private final Map<String, List<String>> recibidos = new ConcurrentHashMap<>();

    MlSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    void responder(String ruta, String json) {
        responder(ruta, cuerpo -> json);
    }

    /** Respuesta armada a partir de lo que mandó el worker (ml-geo contesta por cada animal del request). */
    void responder(String ruta, UnaryOperator<String> respuesta) {
        respuestas.put(ruta, respuesta);
    }

    /** Sin respuestas cargadas ni llamadas registradas. */
    void reiniciar() {
        respuestas.clear();
        recibidos.clear();
    }

    /** Los cuerpos recibidos en la ruta, en orden de llegada. */
    List<String> recibidos(String ruta) {
        return List.copyOf(recibidos.getOrDefault(ruta, List.of()));
    }

    int llamadas(String ruta) {
        return recibidos(ruta).size();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recibidos.computeIfAbsent(ruta, r -> new CopyOnWriteArrayList<>()).add(cuerpo);

        UnaryOperator<String> respuesta = respuestas.get(ruta);
        int estado = respuesta != null ? 200 : 501;
        byte[] salida = (respuesta != null ? respuesta.apply(cuerpo)
                : "{\"detail\":\"Sin respuesta simulada para " + ruta + "\"}").getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, salida.length);
        try (OutputStream out = intercambio.getResponseBody()) {
            out.write(salida);
        }
    }
}
