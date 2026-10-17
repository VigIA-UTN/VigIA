package dev.vigia.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;

/**
 * Los requests que mandan la misma cookie JSESSIONID comparten el SecurityContext guardado en esa sesión. Si el
 * filtro de JWT escribe al usuario del token en ese objeto, dos requests simultáneos de usuarios distintos con la
 * misma cookie se leen el usuario entre sí (lo vio la prueba de carga: cada productor recibía el establecimiento de
 * otro).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionCompartidaTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EstablecimientoRepository establecimientoRepository;
    @Autowired private InvitacionRepository invitacionRepository;
    @Autowired private AnimalRepository animalRepository;
    @Autowired private RodeoLoteRepository rodeoLoteRepository;
    @Autowired private EstadoReproductivoRepository estadoReproductivoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();
        establecimientoDe(productor("a@vigia.dev"), "Estancia A");
        establecimientoDe(productor("b@vigia.dev"), "Estancia B");
    }

    @Test
    @SuppressWarnings("null")
    void unRequestConElTokenDeB_noEscribeASuUsuarioEnElContextoQueLaSesionComparteConA() throws Exception {
        // La sesión ya guarda un contexto con A, como si A hubiera hecho un request antes con esa misma cookie.
        SecurityContext contextoDeLaSesion = SecurityContextHolder.createEmptyContext();
        contextoDeLaSesion.setAuthentication(new UsernamePasswordAuthenticationToken("a@vigia.dev", null, List.of()));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contextoDeLaSesion);

        mockMvc.perform(get("/api/v1/establecimientos").session(sesion)
                .header("Authorization", "Bearer " + token("b@vigia.dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Estancia B"));

        // Si el filtro hubiera escrito a B acá, un request de A en curso con la misma cookie pasaría a ser B.
        assertEquals("a@vigia.dev", contextoDeLaSesion.getAuthentication().getName());
    }

    @SuppressWarnings("null")
    private Usuario productor(String email) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("clave123"));
        u.setNombre("Productor");
        u.setApellido(email);
        u.setRol(Rol.PRODUCTOR);
        u.setActivo(true);
        u.setFechaAlta(LocalDate.now());
        return usuarioRepository.save(u);
    }

    @SuppressWarnings("null")
    private void establecimientoDe(Usuario usuario, String nombre) {
        Establecimiento est = new Establecimiento();
        est.setNombre(nombre);
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        est.getEmpleados().add(new Empleado(est, usuario));
        establecimientoRepository.save(est);
    }

    @SuppressWarnings("null")
    private String token(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"clave123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }
}
