import Container from 'react-bootstrap/Container';
import Nav from 'react-bootstrap/Nav';
import Navbar from 'react-bootstrap/Navbar';
import logoImg from '../assets/logo.png';
import 'bootstrap/dist/css/bootstrap.min.css';

function NavScroll() {
    return (
        <Navbar expand="lg" style={{ backgroundColor: '#1E3A5F' }} variant="dark" className='p-0'>
            <Container fluid className="justify-content-center">
                <Navbar.Toggle aria-controls="navbarScroll" className="me-2" />

                <Navbar.Brand href="#" className="d-lg-none mx-auto p-0">
                    <div className="brand-trapezoid">
                        <img src={logoImg} alt="VigIA" className="navbar-logo" />
                    </div>
                </Navbar.Brand>

                <Navbar.Collapse id="navbarScroll" className="justify-content-center">
                    <Nav
                        className="mt-2 my-lg-0 align-items-center"
                        style={{ maxHeight: '120px' }}
                        navbarScroll
                    >
                        <Nav.Link href="#action1" className="mx-2">Mapa</Nav.Link>
                        <Nav.Link href="#action2" className="mx-2">Alerta</Nav.Link>
                        <Nav.Link href="#action3" className="mx-2">Cargas</Nav.Link>
                    </Nav>

                    <Navbar.Brand href="#" className="mx-4 d-none d-lg-block p-0">
                        <div className="brand-trapezoid">
                            <img src={logoImg} alt="VigIA" className="navbar-logo" />
                        </div>
                    </Navbar.Brand>

                    <Nav
                        className="mb-2 my-lg-0 align-items-center"
                        style={{ maxHeight: '100px' }}
                        navbarScroll
                    >
                        <Nav.Link href="#action4" className="mx-2">Animales</Nav.Link>
                        <Nav.Link href="#action5" className="mx-2">Establecimiento</Nav.Link>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}

export default NavScroll;