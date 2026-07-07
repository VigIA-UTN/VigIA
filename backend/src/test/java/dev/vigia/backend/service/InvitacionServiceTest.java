package dev.vigia.backend.service;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.impl.InvitacionServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitacionServiceTest {

        @Mock
        private InvitacionRepository invitacionRepository;

        @Mock
        private EstablecimientoRepository establecimientoRepository;

        @Mock
        private UsuarioRepository usuarioRepository;

        @Mock
        private EmailService emailService;

        @InjectMocks
        private InvitacionServiceImpl invitacionService;

        private InvitacionProductorRequest request;
        private InvitacionEmpleadoRequest empleadoRequest;
        private Usuario adminUser;
        private Usuario productorUser;
        private Establecimiento establecimiento;
        private final String adminEmail = "admin@test.com";
        private final String productorEmail = "productor@test.com";

        @BeforeEach
        void setUp() {
                request = new InvitacionProductorRequest();
                request.setEmail("productor_nuevo@test.com");
                request.setEstablecimientoId(1L);

                empleadoRequest = new InvitacionEmpleadoRequest();
                empleadoRequest.setEmail("empleado@test.com");
                empleadoRequest.setEstablecimientoId(1L);
                empleadoRequest.setRol(Rol.CAMPERO);

                adminUser = new Usuario();
                adminUser.setId(10L);
                adminUser.setEmail(adminEmail);
                adminUser.setRol(Rol.ADMINISTRADOR);

                productorUser = new Usuario();
                productorUser.setId(11L);
                productorUser.setEmail(productorEmail);
                productorUser.setRol(Rol.PRODUCTOR);

                establecimiento = new Establecimiento();
                establecimiento.setId(1L);
                establecimiento.setNombre("Estancia La Paz");
                establecimiento.setUsuarios(new java.util.ArrayList<>(java.util.List.of(productorUser)));
        }

        @Test
        @DisplayName("Debe generar invitación con éxito")
        @SuppressWarnings("null")
        void generarInvitacionProductor_Success() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                eq(request.getEmail()), eq(EstadoInvitacion.PENDIENTE), any(LocalDateTime.class)))
                                .thenReturn(false);

                when(usuarioRepository.findByEmail(adminEmail)).thenReturn(Optional.of(adminUser));
                when(establecimientoRepository.findById(request.getEstablecimientoId()))
                                .thenReturn(Optional.of(establecimiento));

                Invitacion savedInvitacion = new Invitacion();
                savedInvitacion.setId(100L);
                savedInvitacion.setEmailDestino(request.getEmail());
                savedInvitacion.setRol(Rol.PRODUCTOR);
                savedInvitacion.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
                savedInvitacion.setToken("mock-token-123");
                savedInvitacion.setFechaCreacion(LocalDateTime.now());
                savedInvitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));

                when(invitacionRepository.save(any(Invitacion.class))).thenReturn(savedInvitacion);

                InvitacionResponse response = invitacionService.generarInvitacionProductor(request, adminEmail);

                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(100L);
                assertThat(response.getEmailDestino()).isEqualTo(request.getEmail());
                assertThat(response.getEstado()).isEqualTo(EstadoInvitacion.PENDIENTE.name());
                assertThat(response.getRol()).isEqualTo(Rol.PRODUCTOR.name());

                ArgumentCaptor<Invitacion> invitacionCaptor = ArgumentCaptor.forClass(Invitacion.class);
                verify(invitacionRepository).save(invitacionCaptor.capture());
                Invitacion captured = invitacionCaptor.getValue();

                assertThat(captured.getEmailDestino()).isEqualTo(request.getEmail());
                assertThat(captured.getRol()).isEqualTo(Rol.PRODUCTOR);
                assertThat(captured.getTipoInvitacion()).isEqualTo(TipoInvitacion.PRODUCTOR);
                assertThat(captured.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE);
                assertThat(captured.getCreador()).isEqualTo(adminUser);
                assertThat(captured.getEstablecimiento()).isEqualTo(establecimiento);
                assertThat(captured.getToken()).isNotNull().isNotEmpty();

                verify(emailService).enviarInvitacionProductor(
                                eq(request.getEmail()),
                                eq(captured.getToken()),
                                eq(establecimiento.getNombre()));
        }

        @Test
        @DisplayName("Debe lanzar excepción si ya existe invitación vigente")
        @SuppressWarnings("null")
        void generarInvitacionProductor_ThrowsWhenExistsVigente() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                eq(request.getEmail()), eq(EstadoInvitacion.PENDIENTE), any(LocalDateTime.class)))
                                .thenReturn(true);

                assertThatThrownBy(() -> invitacionService.generarInvitacionProductor(request, adminEmail))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Ya existe una invitación pendiente");

                verify(invitacionRepository, never()).save(any());
                verify(emailService, never()).enviarInvitacionProductor(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Debe lanzar excepción si no encuentra administrador")
        @SuppressWarnings("null")
        void generarInvitacionProductor_ThrowsWhenAdminNotFound() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                anyString(), any(), any())).thenReturn(false);
                when(usuarioRepository.findByEmail(adminEmail)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> invitacionService.generarInvitacionProductor(request, adminEmail))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Usuario administrador no encontrado");

                verify(invitacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe lanzar excepción si no encuentra establecimiento")
        @SuppressWarnings("null")
        void generarInvitacionProductor_ThrowsWhenEstablecimientoNotFound() {

                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                anyString(), any(), any())).thenReturn(false);
                when(usuarioRepository.findByEmail(adminEmail)).thenReturn(Optional.of(adminUser));
                when(establecimientoRepository.findById(request.getEstablecimientoId())).thenReturn(Optional.empty());

                assertThatThrownBy(() -> invitacionService.generarInvitacionProductor(request, adminEmail))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Establecimiento no encontrado");

                verify(invitacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe generar invitación empleado con éxito")
        @SuppressWarnings("null")
        void generarInvitacionEmpleado_Success() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                eq(empleadoRequest.getEmail()), eq(EstadoInvitacion.PENDIENTE), any(LocalDateTime.class)))
                                .thenReturn(false);

                when(usuarioRepository.findByEmail(productorEmail)).thenReturn(Optional.of(productorUser));
                when(establecimientoRepository.findById(empleadoRequest.getEstablecimientoId()))
                                .thenReturn(Optional.of(establecimiento));

                Invitacion savedInvitacion = new Invitacion();
                savedInvitacion.setId(101L);
                savedInvitacion.setEmailDestino(empleadoRequest.getEmail());
                savedInvitacion.setRol(Rol.CAMPERO);
                savedInvitacion.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
                savedInvitacion.setToken("mock-token-456");
                savedInvitacion.setFechaCreacion(LocalDateTime.now());
                savedInvitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));

                when(invitacionRepository.save(any(Invitacion.class))).thenReturn(savedInvitacion);

                InvitacionResponse response = invitacionService.generarInvitacionEmpleado(empleadoRequest, productorEmail);

                assertThat(response).isNotNull();
                assertThat(response.getId()).isEqualTo(101L);
                assertThat(response.getEmailDestino()).isEqualTo(empleadoRequest.getEmail());
                assertThat(response.getEstado()).isEqualTo(EstadoInvitacion.PENDIENTE.name());
                assertThat(response.getRol()).isEqualTo(Rol.CAMPERO.name());

                ArgumentCaptor<Invitacion> invitacionCaptor = ArgumentCaptor.forClass(Invitacion.class);
                verify(invitacionRepository).save(invitacionCaptor.capture());
                Invitacion captured = invitacionCaptor.getValue();

                assertThat(captured.getEmailDestino()).isEqualTo(empleadoRequest.getEmail());
                assertThat(captured.getRol()).isEqualTo(Rol.CAMPERO);
                assertThat(captured.getTipoInvitacion()).isEqualTo(TipoInvitacion.EMPLEADO);
                assertThat(captured.getCreador()).isEqualTo(productorUser);
                assertThat(captured.getEstablecimiento()).isEqualTo(establecimiento);

                verify(emailService).enviarInvitacionEmpleado(
                                eq(empleadoRequest.getEmail()),
                                eq(captured.getToken()),
                                eq(establecimiento.getNombre()),
                                eq(Rol.CAMPERO.name()));
        }

        @Test
        @DisplayName("Debe lanzar excepción si el productor no pertenece al establecimiento")
        @SuppressWarnings("null")
        void generarInvitacionEmpleado_ThrowsWhenProductorNoPertenece() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                anyString(), any(), any())).thenReturn(false);
                when(usuarioRepository.findByEmail(productorEmail)).thenReturn(Optional.of(productorUser));

                Establecimiento otroEstablecimiento = new Establecimiento();
                otroEstablecimiento.setId(1L);
                otroEstablecimiento.setUsuarios(new ArrayList<>());
                
                when(establecimientoRepository.findById(empleadoRequest.getEstablecimientoId()))
                                .thenReturn(Optional.of(otroEstablecimiento));

                assertThatThrownBy(() -> invitacionService.generarInvitacionEmpleado(empleadoRequest, productorEmail))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("El productor no pertenece a este establecimiento");
        }

        @Test
        @DisplayName("Debe lanzar excepción si el rol del empleado es inválido")
        @SuppressWarnings("null")
        void generarInvitacionEmpleado_ThrowsWhenRolInvalido() {
                when(invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                                anyString(), any(), any())).thenReturn(false);
                when(usuarioRepository.findByEmail(productorEmail)).thenReturn(Optional.of(productorUser));
                when(establecimientoRepository.findById(empleadoRequest.getEstablecimientoId()))
                                .thenReturn(Optional.of(establecimiento));

                empleadoRequest.setRol(Rol.ADMINISTRADOR);

                assertThatThrownBy(() -> invitacionService.generarInvitacionEmpleado(empleadoRequest, productorEmail))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("El rol especificado no es válido para un empleado");
        }
}
