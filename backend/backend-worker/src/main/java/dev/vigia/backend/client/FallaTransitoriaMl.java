package dev.vigia.backend.client;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.function.Predicate;

/**
 * Decide qué fallas de un microservicio ML se reintentan (resilience4j.retry.*.retryExceptionPredicate): las
 * pasajeras, que al repetir pueden andar. Son el 503, el 504 y los errores de red: timeout, conexión rechazada o
 * cortada (el contenedor reiniciando) y el host que todavía no resuelve (un redeploy).
 * <p>
 * Los errores de red se buscan en la cadena de causas y no por la clase de la excepción: según en qué momento
 * corta, RestClient informa un timeout de lectura como {@link ResourceAccessException} o como un
 * {@code RestClientException} "Error while extracting response" con el {@link SocketTimeoutException} adentro.
 * <p>
 * No se reintentan un 500, un 4xx ni un cuerpo que no se puede leer: darían lo mismo. Tampoco el circuito abierto.
 */
public class FallaTransitoriaMl implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        if (t instanceof HttpServerErrorException.ServiceUnavailable
                || t instanceof HttpServerErrorException.GatewayTimeout
                || t instanceof ResourceAccessException) {
            return true;
        }
        for (Throwable causa = t; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException
                    || causa instanceof HttpTimeoutException
                    || causa instanceof SocketException
                    || causa instanceof UnknownHostException) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }
}
