import { useState, useEffect } from 'react';
import { Container, Spinner, Alert, Button, Form } from 'react-bootstrap';
import './AnimalesPage.css';
import { getAnimales, removerAnimal, type AnimalResponse } from '../api/animalesApi';

export default function AnimalesPage() {
    //Falta finalizar los filtros y agregar vistas de celo, salud, modales de creación de animal, rodeo y carga de CSV
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [togglingId, setTogglingId] = useState<number | null>(null);
    const [rodeos, setRodeos] = useState<string[]>([]);

    const [searchTerm, setSearchTerm] = useState('');
    const [rodeoFilter, setRodeoFilter] = useState('');

    useEffect(() => {
        const fetchAnimales = async () => {
            try {
                setLoading(true);
                setError(null);
                const data = await getAnimales();
                const rodeos = [...new Set(data.map(e => e.rodeoLoteNombre))];
                setAnimales(data);
                setRodeos(rodeos);
            } catch (err) {
                const axiosError = err as { response?: { data?: { message?: string } } };
                setError(axiosError.response?.data?.message || 'Error al cargar animales');
            } finally {
                setLoading(false);
            }
        };

        fetchAnimales();
    }, []);

    const handleRemoverAnimal = async (id: number, establecimientoId: number) => {
        if (!window.confirm('¿Estás seguro de que querés quitar a este animal del establecimiento?')) {
            return;
        }

        try {
            setTogglingId(id);
            await removerAnimal(id);
            setAnimales(prev => prev.filter(e => !(e.id === id && e.establecimientoId === establecimientoId)));
        } catch (err) {
            const axiosError = err as { response?: { data?: { message?: string } } };
            alert(axiosError.response?.data?.message || 'Error al remover el animal');
        } finally {
            setTogglingId(null);
        }
    };

    const filteredAnimales = animales.filter(e => {
        const matchesSearch = searchTerm === '' ||
            `${e.alias}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
            (e.idCaravanaOficial && e.idCaravanaOficial.toLowerCase().includes(searchTerm.toLowerCase())) ||
            (e.raza && e.raza.toLowerCase().includes(searchTerm.toLowerCase()))

        const matchesRodeo = rodeoFilter === '' || e.rodeoLoteNombre === rodeoFilter;
        return matchesSearch && matchesRodeo;
    });

    return (
        <div className="animales-container">
            <Container>
                <h2 className="animales-title">Animales</h2>

                <div className="d-flex flex-column flex-md-row justify-content-between mb-4 gap-3">
                    <Form.Control
                        type="text"
                        placeholder="Buscar por nombre, rodeo o establecimiento..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '400px' }}
                    />
                    <Form.Select
                        value={rodeoFilter}
                        onChange={(e) => setRodeoFilter(e.target.value)}
                        style={{ maxWidth: '250px' }}
                    >
                        <option value="">Todos los rodeos</option>
                        {rodeos.map((rodeo) => (
                            <option key={rodeo} value={rodeo}>{rodeo}</option>
                        ))}
                    </Form.Select>
                </div>

                {error && <Alert variant="danger">{error}</Alert>}

                {loading ? (
                    <div className="text-center mt-5">
                        <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                    </div>
                ) : (
                    <div className="animales-table-wrapper">
                        <table className="animales-table">
                            <thead>
                                <tr>
                                    <th>Identificador</th>
                                    <th>Nombre</th>
                                    <th>Fecha Nacimiento</th>
                                    <th>Raza</th>
                                    <th>Rodeo</th>
                                    <th>Estado</th>
                                    <th>Acciones</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredAnimales.length === 0 ? (
                                    <tr>
                                        <td colSpan={7} className="text-center py-4 text-muted">
                                            No se encontraron animales que coincidan con la búsqueda.
                                        </td>
                                    </tr>
                                ) : (
                                    filteredAnimales.map((u) => (
                                        <tr key={`${u.id}-${u.establecimientoId}`}>
                                            <td className="fw-medium text-dark">
                                                {u.idCaravanaOficial}
                                            </td>
                                            <td>{u.alias}</td>
                                            <td>{u.fechaNacimiento}</td>
                                            <td>{u.raza}</td>
                                            <td>{u.rodeoLoteNombre}</td>
                                            <td>{u.estadoActual}</td>
                                            <td>
                                                <Button
                                                    variant="outline-danger"
                                                    size="sm"
                                                    className="border-0 fw-semibold"
                                                    onClick={() => u.establecimientoId && handleRemoverAnimal(u.id, u.establecimientoId)}
                                                    disabled={togglingId === u.id}
                                                >
                                                    {togglingId === u.id ? (
                                                        <Spinner as="span" animation="border" size="sm" role="status" aria-hidden="true" />
                                                    ) : (
                                                        'Quitar'
                                                    )}
                                                </Button>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </Container>
        </div>
    );
}
