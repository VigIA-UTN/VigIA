import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Form } from 'react-bootstrap';
import { getDiagnosticos } from '../api/diagnosticoApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import './AnimalesPage.css';
import './EstablecimientosPage.css';

function formatFecha(iso: string) {
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString([], { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export default function DiagnosticosPage() {
    const navigate = useNavigate();
    const { establecimientos } = useEstablecimientoStore();
    const [searchTerm, setSearchTerm] = useState('');
    const [establecimiento, setEstablecimiento] = useState('Todos los establecimientos');

    const rows = useMemo(
        () => getDiagnosticos({ search: searchTerm, establecimiento }),
        [searchTerm, establecimiento]
    );

    return (
        <div className="vigia-page-container">
            <Container>
                <div className="vigia-page-header animales-header-mobile">
                    <h2 className="vigia-page-title">Diagnósticos</h2>
                    <span className="text-muted">
                        {rows.length} diagnóstico{rows.length === 1 ? '' : 's'}
                    </span>
                </div>

                <div className="mb-3 mt-3 d-flex gap-3 flex-wrap align-items-center">
                    <Form.Control
                        type="text"
                        placeholder="Buscá por nombre o caravana"
                        value={searchTerm}
                        onChange={e => setSearchTerm(e.target.value)}
                        style={{ maxWidth: '400px' }}
                    />
                    <Form.Select
                        value={establecimiento}
                        onChange={e => setEstablecimiento(e.target.value)}
                        style={{ maxWidth: '320px' }}
                    >
                        <option>Todos los establecimientos</option>
                        {establecimientos.map(e => (
                            <option key={e.id} value={e.nombre}>{e.nombre}</option>
                        ))}
                    </Form.Select>
                </div>

                <div className="vigia-table-wrapper animales-table-wrapper">
                    <table className="vigia-table">
                        <thead>
                            <tr>
                                <th>Fecha</th>
                                <th>Animal</th>
                                <th className="col-mobile-hide">Establecimiento</th>
                                <th>Diagnóstico</th>
                                <th className="col-mobile-hide">Indicación o tratamiento</th>
                                <th className="col-mobile-hide">Veterinario</th>
                                <th className="text-center">Alerta</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rows.length === 0 ? (
                                <tr>
                                    <td colSpan={7} className="text-center py-4 text-muted">
                                        No hay diagnósticos que coincidan con la búsqueda. Probá con otro nombre o establecimiento.
                                    </td>
                                </tr>
                            ) : (
                                rows.map(r => (
                                    <tr key={r.id}>
                                        <td>{formatFecha(r.fechaRegistro)}</td>
                                        <td>
                                            <div className="d-flex flex-column gap-1">
                                                <span>{r.animalAlias}</span>
                                                <span className="text-muted" style={{ fontSize: '0.85rem' }}>{r.animalCaravanaOficial}</span>
                                            </div>
                                        </td>
                                        <td className="col-mobile-hide">{r.establecimientoNombre || '—'}</td>
                                        <td className="text-truncate" style={{ maxWidth: '260px' }}>{r.diagnostico}</td>
                                        <td className="col-mobile-hide text-truncate" style={{ maxWidth: '300px' }}>{r.tratamiento}</td>
                                        <td className="col-mobile-hide">{r.veterinarioNombre}</td>
                                        <td className="text-center">
                                            <a
                                                href={`/animales/${r.animalId}`}
                                                onClick={(e) => { e.preventDefault(); navigate(`/animales/${r.animalId}`); }}
                                                style={{ color: '#1E3A5F', fontWeight: 600, textDecoration: 'none' }}
                                            >
                                                #{r.alertaId}
                                            </a>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </Container>
        </div>
    );
}
