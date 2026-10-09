import { useEffect, useState } from 'react';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import Form from 'react-bootstrap/Form';
import { Link, useNavigate } from 'react-router-dom';
import logoImg from '../assets/logo.png';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { getEstablecimientos } from '../api/establecimientosApi';
import 'bootstrap/dist/css/bootstrap.min.css';

function NavScroll() {
    const { rol, logout } = useAuthStore();
    const isAdmin = rol === 'ADMINISTRADOR';
    const isProductor = rol === 'PRODUCTOR';
    const isVeterinario = rol === 'VETERINARIO';
    const navigate = useNavigate();

    const [drawerOpen, setDrawerOpen] = useState(false);

    const {
        establecimientos,
        selectedId,
        setEstablecimientos,
        setSelectedId,
    } = useEstablecimientoStore();

    // Load establecimientos on mount (non-admin only)
    useEffect(() => {
        if (isAdmin || !rol) return;
        (async () => {
            try {
                const data = await getEstablecimientos();
                setEstablecimientos(
                    data.map((e) => ({ id: e.id, nombre: e.nombre, activo: e.activo, latitud: e.latitud, longitud: e.longitud }))
                );
            } catch {
                // silently fail — the pages will show their own errors
            }
        })();
    }, [isAdmin, rol, setEstablecimientos]);

    // Cierra el drawer al cambiar de ruta
    const handleNavLink = (to: string) => {
        setDrawerOpen(false);
        navigate(to);
    };

    const activeEstablecimientos = establecimientos.filter((e) => e.activo);

    const getDashboardRoute = () => {
        if (isAdmin) return '/establecimientos';
        return '/mapa';
    };

    const establecimientoSelector = !isAdmin && activeEstablecimientos.length > 0 && (
        <Form.Select
            size="sm"
            value={selectedId ?? ''}
            onChange={(e) => setSelectedId(Number(e.target.value))}
            style={{
                backgroundColor: 'rgba(255,255,255,0.12)',
                color: '#fff',
                border: '1px solid rgba(255,255,255,0.25)',
                borderRadius: '8px',
                fontSize: '0.82rem',
                fontWeight: 500,
                maxWidth: '220px',
                minWidth: '140px',
                cursor: 'pointer',
                backdropFilter: 'blur(4px)',
            }}
            aria-label="Seleccionar establecimiento"
            id="navbar-establecimiento-selector"
        >
            {activeEstablecimientos.map((e) => (
                <option key={e.id} value={e.id} style={{ color: '#1E3A5F', backgroundColor: '#fff' }}>
                    {e.nombre}
                </option>
            ))}
        </Form.Select>
    );

    return (
        <>
            {/* ── Overlay del drawer (solo mobile) ── */}
            {drawerOpen && (
                <div
                    className="d-lg-none"
                    onClick={() => setDrawerOpen(false)}
                    style={{
                        position: 'fixed',
                        inset: 0,
                        backgroundColor: 'rgba(0,0,0,0.5)',
                        zIndex: 1040,
                    }}
                />
            )}

            {/* ── Drawer lateral (solo mobile) ── */}
            <div
                className="d-lg-none mobile-drawer"
                style={{
                    transform: drawerOpen ? 'translateX(0)' : 'translateX(-100%)',
                }}
            >
                {/* Header del drawer con logo */}
                <div className="mobile-drawer-header">
                    {/* Logo centrado absolutamente, no afectado por la X */}
                    <Link
                        to={getDashboardRoute()}
                        onClick={() => setDrawerOpen(false)}
                        style={{ position: 'absolute', left: '50%', transform: 'translateX(-50%)' }}
                    >
                        <img src={logoImg} alt="VigIA" style={{ maxHeight: '38px', display: 'block' }} />
                    </Link>
                    {/* Botón cerrar a la derecha */}
                    <button
                        className="mobile-drawer-close ms-auto"
                        onClick={() => setDrawerOpen(false)}
                        aria-label="Cerrar menú"
                    >
                        ✕
                    </button>
                </div>

                {/* Links de navegación */}
                <nav className="mobile-drawer-nav">
                    {isAdmin ? (
                        <>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/establecimientos')}>
                                <i className="bi bi-building me-2" />Establecimientos
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/caravanas')}>
                                <i className="bi bi-broadcast me-2" />Caravanas
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/usuarios')}>
                                <i className="bi bi-people me-2" />Usuarios
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/invitaciones')}>
                                <i className="bi bi-envelope me-2" />Invitaciones
                            </button>
                        </>
                    ) : rol === 'PENDIENTE' ? (
                        <>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/invitaciones')}>
                                <i className="bi bi-envelope me-2" />Invitaciones
                            </button>
                        </>
                    ) : (
                        <>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/dashboard')}>
                                <i className="bi bi-speedometer2 me-2" />Dashboard
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/mapa')}>
                                <i className="bi bi-map me-2" />Mapa
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/alertas')}>
                                <i className="bi bi-bell me-2" />Alertas
                            </button>
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/animales')}>
                                <i className="bi bi-heart-pulse me-2" />Animales
                            </button>
                            {isVeterinario && (
                                <button className="mobile-drawer-link" onClick={() => handleNavLink('/diagnosticos')}>
                                    <i className="bi bi-clipboard2-pulse me-2" />Diagnósticos
                                </button>
                            )}
                            {isProductor && (
                                <>
                                    <button className="mobile-drawer-link" onClick={() => handleNavLink('/empleados')}>
                                        <i className="bi bi-person-badge me-2" />Empleados
                                    </button>
                                    <button className="mobile-drawer-link" onClick={() => handleNavLink('/establecimientos')}>
                                        <i className="bi bi-building me-2" />Establecimientos
                                    </button>
                                </>
                            )}
                            <button className="mobile-drawer-link" onClick={() => handleNavLink('/invitaciones')}>
                                <i className="bi bi-envelope me-2" />Invitaciones
                            </button>
                        </>
                    )}
                </nav>

                {/* Footer del drawer: selector + logout */}
                <div className="mobile-drawer-footer">
                    <div style={{ display: 'flex', justifyContent: 'center' }}>
                        {establecimientoSelector}
                    </div>
                    <div className="mt-3">
                        <button
                            className="btn btn-outline-primary btn-sm w-100 mb-2"
                            onClick={() => { setDrawerOpen(false); navigate('/perfil'); }}
                        >
                            Mi Perfil
                        </button>
                        <button
                            className="btn btn-outline-danger btn-sm w-100"
                            onClick={() => { setDrawerOpen(false); logout(); }}
                        >
                            Cerrar sesión
                        </button>
                    </div>
                </div>
            </div>

            {/* ── Navbar principal ── */}
            <Navbar expand="lg" style={{ backgroundColor: '#1E3A5F' }} variant="dark" className="p-0">
                <Container fluid className="px-0 px-lg-3 position-relative">

                    {/* Botón hamburguesa (mobile) — abre el drawer custom */}
                    <button
                        className="d-lg-none navbar-hamburger"
                        onClick={() => setDrawerOpen(true)}
                        aria-label="Abrir menú"
                    >
                        <span /><span /><span />
                    </button>

                    {/* Logo centrado (mobile) */}
                    <Navbar.Brand as={Link} to={getDashboardRoute()} className="d-lg-none mx-auto p-0" style={{ position: 'absolute', left: '50%', transform: 'translateX(-50%)', zIndex: 40 }}>
                        <div className="brand-trapezoid" style={{ padding: '10px 28px' }}>
                            <img src={logoImg} alt="VigIA" className="navbar-logo" style={{ maxHeight: '44px' }} />
                        </div>
                    </Navbar.Brand>

                    {/* Selector (Desktop) posicionado absoluto a la izquierda */}
                    <div className="d-none d-lg-flex align-items-center gap-2 position-absolute start-0 ps-4" style={{ top: '50%', transform: 'translateY(-50%)', zIndex: 20 }}>
                        {establecimientoSelector}
                    </div>

                    {/* Opciones de usuario (Desktop) posicionadas absoluto a la derecha */}
                    <div className="d-none d-lg-flex align-items-center gap-3 position-absolute end-0 pe-4" style={{ top: '50%', transform: 'translateY(-50%)', zIndex: 20 }}>
                        <Nav>
                            <Nav.Link as={Link} to="/perfil" className="fw-semibold px-0">
                                <i className="bi bi-person-circle me-1" /> Mi Perfil
                            </Nav.Link>
                        </Nav>
                        <button 
                            className="btn btn-sm d-flex align-items-center gap-1 btn-salir-navbar" 
                            onClick={logout}
                            style={{
                                backgroundColor: 'rgba(255,255,255,0.12)',
                                color: '#fff',
                                border: '1px solid rgba(255,255,255,0.25)',
                                borderRadius: '8px',
                                fontSize: '0.82rem',
                                fontWeight: 500,
                                backdropFilter: 'blur(4px)',
                            }}
                        >
                            <i className="bi bi-box-arrow-right" /> Salir
                        </button>
                    </div>

                    {/* Collapse solo para Desktop */}
                    <Navbar.Collapse id="navbarScroll" className="text-center">
                        <Nav className="me-auto align-items-center flex-grow-1 justify-content-lg-end pe-lg-4 mb-2 mb-lg-0 mt-3 mt-lg-0" style={{ flexBasis: 0 }}>
                            {isAdmin ? (
                                <>
                                    <Nav.Link as={Link} to="/establecimientos" className="mx-2 fw-semibold">Establecimientos</Nav.Link>
                                    <Nav.Link as={Link} to="/caravanas" className="mx-2 fw-semibold">Caravanas</Nav.Link>
                                </>
                            ) : rol === 'PENDIENTE' ? (
                                <>
                                </>
                            ) : (
                                <>
                                    {isProductor && (
                                        <>
                                            <Nav.Link as={Link} to="/establecimientos" className="mx-2 fw-semibold">Establecimientos</Nav.Link>
                                            <Nav.Link as={Link} to="/empleados" className="mx-2 fw-semibold">Empleados</Nav.Link>
                                        </>
                                    )}
                                    <Nav.Link as={Link} to="/animales" className="mx-2 fw-semibold">Animales</Nav.Link>
                                    {isVeterinario && (
                                        <Nav.Link as={Link} to="/diagnosticos" className="mx-2 fw-semibold">Diagnósticos</Nav.Link>
                                    )}
                                </>
                            )}
                        </Nav>

                        <Navbar.Brand as={Link} to={rol === 'PENDIENTE' ? '/invitaciones' : getDashboardRoute()} className="mx-0 d-none d-lg-block p-0 align-self-start" style={{ zIndex: 10 }}>
                            <div className="brand-trapezoid">
                                <img src={logoImg} alt="VigIA" className="navbar-logo" />
                            </div>
                        </Navbar.Brand>

                        <Nav className="ms-auto align-items-center flex-grow-1 justify-content-lg-start ps-lg-4 mb-3 mb-lg-0" style={{ flexBasis: 0 }}>
                            {isAdmin ? (
                                <>
                                    <Nav.Link as={Link} to="/usuarios" className="mx-2 fw-semibold">Usuarios</Nav.Link>
                                    <Nav.Link as={Link} to="/invitaciones" className="mx-2 fw-semibold">Invitaciones</Nav.Link>
                                </>
                            ) : rol === 'PENDIENTE' ? (
                                <>
                                    <Nav.Link as={Link} to="/invitaciones" className="mx-2 fw-semibold">Invitaciones</Nav.Link>
                                </>
                            ) : (
                                <>
                                    <Nav.Link as={Link} to="/dashboard" className="mx-2 fw-semibold">Dashboard</Nav.Link>
                                    <Nav.Link as={Link} to="/alertas" className="mx-2 fw-semibold">Alertas</Nav.Link>
                                    <Nav.Link as={Link} to="/invitaciones" className="mx-2 fw-semibold">Invitaciones</Nav.Link>
                                </>
                            )}
                        </Nav>
                    </Navbar.Collapse>
                </Container>
            </Navbar>
        </>
    );
}

export default NavScroll;