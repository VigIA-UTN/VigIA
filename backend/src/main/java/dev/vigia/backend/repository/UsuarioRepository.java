package dev.vigia.backend.repository;

import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByRolAndActivoTrue(Rol rol);
}
