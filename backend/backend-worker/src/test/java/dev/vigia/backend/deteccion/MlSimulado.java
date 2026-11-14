package dev.vigia.backend.deteccion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * Los tres microservicios de ML (ml-celo, ml-salud y ml-geo) simulados en un servidor HTTP local: el worker les habla
 * con sus clientes reales (RestClient, Resilience4j y el JSON de los DTO), y cada test decide qué responde cada ruta y
 * revisa qué le mandaron. Una ruta sin respuesta cargada devuelve 501, así que una llamada que el test no esperaba no
 * pasa desapercibida.
 * <p>
 * Para los casos de resiliencia (INT02) una respuesta puede tener otro status y una demora, y se pueden encolar
 * respuestas que se usan una sola vez antes de la fija (por ejemplo, un 503 y después un 200). Como los microservicios
 * reales, devuelve el {@code X-Correlation-ID} que recibe, y registra el de cada llamada.
 */
public final class MlSimulado {

    public static final String CELO_PREDICT = "/api/internal/ml/celo/predict";
    public static final String SALUD_PREDICT = "/api/internal/ml/salud/predict";
    public static final String SALUD_PERFIL = "/api/internal/ml/salud/perfil";
    public static final String GEO_ANALYZE = "/api/internal/ml/geo/analyze";

    private static final String CORRELATION_ID = "X-Correlation-ID";

    private record Respuesta(int status, UnaryOperator<String> cuerpo, long demoraMs) {
    }

    private final HttpServer servidor;
    /** Por ruta: la respuesta fija. */
    private final Map<String, Respuesta> respuestas = new ConcurrentHashMap<>();
    /** Por ruta: respuestas de un solo uso, que se consumen antes de la fija. */
    private final Map<String, Deque<Respuesta>> unaVez = new ConcurrentHashMap<>();
    private final Map<String, List<String>> recibidos = new ConcurrentHashMap<>();
    private final Map<String, List<String>> correlationIds = new ConcurrentHashMap<>();

    public MlSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", this::atender);
        // Un hilo por request: con una respuesta demorada, el reintento del cliente no espera a que termine la primera.
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
    }

    public String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    public void responder(String ruta, String json) {
        responder(ruta, cuerpo -> json);
    }

    /** Respuesta armada a partir de lo que mandó el worker (ml-geo contesta por cada animal del request). */
    public void responder(String ruta, UnaryOperator<String> respuesta) {
        respuestas.put(ruta, new Respuesta(200, respuesta, 0));
    }

    /** Respuesta fija con otro status y una demora antes de contestar. */
    public void responder(String ruta, int status, String json, long demoraMs) {
        respuestas.put(ruta, new Respuesta(status, cuerpo -> json, demoraMs));
    }

    /** Respuesta que se usa una sola vez, antes de la fija (se pueden encolar varias). */
    public void responderUnaVez(String ruta, int status, String json) {
        unaVez.computeIfAbsent(ruta, r -> new ConcurrentLinkedDeque<>()).add(new Respuesta(status, cuerpo -> json, 0));
    }

    /** Sin respuestas cargadas ni llamadas registradas. */
    public void reiniciar() {
        respuestas.clear();
        unaVez.clear();
        recibidos.clear();
        correlationIds.clear();
    }

    /** Los cuerpos recibidos en la ruta, en orden de llegada. */
    public List<String> recibidos(String ruta) {
        return List.copyOf(recibidos.getOrDefault(ruta, List.of()));
    }

    public int llamadas(String ruta) {
        return recibidos(ruta).size();
    }

    /** El X-Correlation-ID de cada llamada a la ruta, en orden de llegada (null si no vino). */
    public List<String> correlationIds(String ruta) {
        return List.copyOf(correlationIds.getOrDefault(ruta, List.of()));
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String correlationId = intercambio.getRequestHeaders().getFirst(CORRELATION_ID);
        recibidos.computeIfAbsent(ruta, r -> new CopyOnWriteArrayList<>()).add(cuerpo);
        correlationIds.computeIfAbsent(ruta, r -> new CopyOnWriteArrayList<>()).add(correlationId);

        Deque<Respuesta> pendientes = unaVez.get(ruta);
        Respuesta respuesta = pendientes != null && !pendientes.isEmpty() ? pendientes.poll() : respuestas.get(ruta);
        int estado = respuesta != null ? respuesta.status() : 501;
        byte[] salida = (respuesta != null ? respuesta.cuerpo().apply(cuerpo)
                : "{\"detail\":\"Sin respuesta simulada para " + ruta + "\"}").getBytes(StandardCharsets.UTF_8);

        try {
            if (respuesta != null && respuesta.demoraMs() > 0) {
                Thread.sleep(respuesta.demoraMs());
            }
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            if (correlationId != null) {
                intercambio.getResponseHeaders().add(CORRELATION_ID, correlationId);
            }
            intercambio.sendResponseHeaders(estado, salida.length);
            try (OutputStream out = intercambio.getResponseBody()) {
                out.write(salida);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // El cliente cortó por timeout antes de la respuesta: es lo que el test quería provocar.
        } finally {
            intercambio.close();
        }
    }
}
