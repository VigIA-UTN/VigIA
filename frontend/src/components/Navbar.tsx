import { useEffect } from 'react';
import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import Form from 'react-bootstrap/Form';
import { Link } from 'react-router-dom';
import logoImg from '../assets/logo.png';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { getEstablecimientos } from '../api/establecimientosApi';
import 'bootstrap/dist/css/bootstrap.min.css';

function NavScroll() {
    const { rol, logout } = useAuthStore();
    const isAdmin = rol === 'ADMINISTRADOR';

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
                    data.map((e) => ({ id: e.id, nombre: e.nombre, activo: e.activo }))
                );
            } catch {
                // silently fail — the pages will show their own errors
            }
        })();
    }, [isAdmin, rol, setEstablecimientos]);

    const activeEstablecimientos = establecimientos.filter((e) => e.activo);

    const getDashboardRoute = () => {
        if (isAdmin) return '/establecimientos';
        return '/';
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
        <Navbar expand="lg" style={{ backgroundColor: '#1E3A5F' }} variant="dark" className='p-0'>
            <Container fluid className="px-0 px-lg-3 position-relative">
                <Navbar.Toggle aria-controls="navbarScroll" className="ms-3 my-2" />

                <Navbar.Brand as={Link} to={getDashboardRoute()} className="d-lg-none mx-auto p-0 position-relative" style={{ top: 0 }}>
                    <div className="brand-trapezoid" style={{ padding: '8px 20px' }}>
                        <img src={logoImg} alt="VigIA" className="navbar-logo" style={{ maxHeight: '35px' }} />
                    </div>
                </Navbar.Brand>

                {/* Botón Logout + Selector (Desktop) posicionado absoluto a la derecha */}
                <div className="d-none d-lg-flex align-items-center gap-2 position-absolute end-0 pe-4" style={{ top: '50%', transform: 'translateY(-50%)', zIndex: 20 }}>
                    {establecimientoSelector}
                    <button className="btn btn-outline-light btn-sm" onClick={logout}>
                        <i className="bi bi-box-arrow-right me-1"></i> Cerrar sesión
                    </button>
                </div>

                <Navbar.Collapse id="navbarScroll" className="text-center">
                    <Nav className="me-auto align-items-center flex-grow-1 justify-content-lg-end pe-lg-4 mb-2 mb-lg-0 mt-3 mt-lg-0" style={{ flexBasis: 0 }}>
                        {isAdmin ? (
                            <>
                                <Nav.Link as={Link} to="/establecimientos" className="mx-2 fw-semibold">Establecimientos</Nav.Link>
                                <Nav.Link as={Link} to="/usuarios" className="mx-2 fw-semibold">Usuarios</Nav.Link>
                            </>
                        ) : (
                            <>
                                <Nav.Link as={Link} to="/establecimientos" className="mx-2 fw-semibold">Establecimientos</Nav.Link>
                                <Nav.Link as={Link} to="/empleados" className="mx-2 fw-semibold">Empleados</Nav.Link>
                                <Nav.Link as={Link} to="/animales" className="mx-2 fw-semibold">Animales</Nav.Link>
                            </>
                        )}
                    </Nav>

                    <Navbar.Brand as={Link} to={getDashboardRoute()} className="mx-0 d-none d-lg-block p-0 align-self-start" style={{ zIndex: 10 }}>
                        <div className="brand-trapezoid">
                            <img src={logoImg} alt="VigIA" className="navbar-logo" />
                        </div>
                    </Navbar.Brand>

                    <Nav className="ms-auto align-items-center flex-grow-1 justify-content-lg-start ps-lg-4 mb-3 mb-lg-0" style={{ flexBasis: 0 }}>
                        {isAdmin ? (
                            <>
                                <Nav.Link as={Link} to="/invitaciones" className="mx-2 fw-semibold">Invitaciones</Nav.Link>
                            </>
                        ) : (
                            <>
                                <Nav.Link as={Link} to="/alertas" className="mx-2 fw-semibold">Alertas</Nav.Link>
                                <Nav.Link as={Link} to="/cargas" className="mx-2 fw-semibold">Cargas</Nav.Link>
                                <Nav.Link as={Link} to="/invitaciones" className="mx-2 fw-semibold">Invitaciones</Nav.Link>
                                <Nav.Link as={Link} to="/" className="mx-2 fw-semibold">Mapa</Nav.Link>
                            </>
                        )}

                        {/* Selector + Logout (Mobile) */}
                        <div className="d-lg-none mt-3 mb-2 w-100 d-flex flex-column align-items-center gap-2">
                            {establecimientoSelector}
                            <button className="btn btn-outline-danger btn-sm" onClick={logout}>
                                <i className="bi bi-box-arrow-right me-1"></i> Cerrar sesión
                            </button>
                        </div>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}

export default NavScroll;