import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Container, Spinner, Alert, Modal, Form, Button, Toast, Dropdown } from 'react-bootstrap';
import { cargarAnimalesExcel, cargarEstadosExcel, crearAnimal, getAnimales, desactivarAnimal, vincularCaravana, desvincularCaravana, asignarCaravanasAleatoriamente, descargarAsignacionesCaravanasExcel, ESTADO_REPRODUCTIVO_LABEL, ESTADO_SALUD_LABEL } from '../api/animalesApi';
import type { AnimalResponse, AnimalRequest, CargaMasivaResponse, CargaMasivaEstadoResponse } from '../api/animalesApi';
import { getRodeoLotes } from '../api/rodeoLotesApi';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';
import { getCaravanasDisponibles } from '../api/caravanasApi';
import type { CaravanaResponse } from '../api/caravanasApi';
import { compararParaManga, formatBateria, formatUltimaSenial, labelCaravanaOpcion, nombreSensorAnimal } from '../utils/caravanaTelemetria';
import { hoyIso } from '../utils/fechas';
import { guardarArchivo } from '../utils/descargas';
import './AnimalesPage.css';
import './EstablecimientosPage.css';
import { crearRodeo, type RodeoRequest } from '../api/rodeoApi';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import { showAlert, showConfirm } from '../store/dialogStore';
import { getAlertaParaAnimal } from '../api/alertaApi';
import type { AlertaResponse } from '../api/alertaApi';
import AlertDetailSidebar from '../components/AlertDetailSidebar';
import IntervencionFormPanel from '../components/IntervencionFormPanel';

function formatFecha(iso: string | null) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    if (d) {
        return `${d.substring(0, 2)}/${m}/${y}`;
    }
    return `${m}/${y}`;
}

function estadoClass(e: string) {
    if (e === 'ACTIVO') return 'badge-activo';
    if (e === 'CON_ALERTA') return 'badge-con-alerta';
    return 'badge-inactivo';
}

export default function AnimalesPage() {
    const navigate = useNavigate();
    const rol = useAuthStore((s) => s.rol);
    const puedeGestionarCaravanas = rol !== 'VETERINARIO';
    // Alta/baja de animales, rodeos y cargas masivas: el backend solo se las permite al productor.
    const esProductor = rol === 'PRODUCTOR';
    const selectedId = useEstablecimientoStore((s) => s.selectedId);
    const [animales, setAnimales] = useState<AnimalResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [searchParams, setSearchParams] = useSearchParams();
    const [activeTab, setActiveTab] = useState<'general' | 'celo' | 'salud'>(
        searchParams.get('tab') === 'salud' ? 'salud' : 'general'
    );
    const [searchTerm, setSearchTerm] = useState('');
    // Filtros que llegan desde el dashboard: ?rodeo=<id> (resumen ejecutivo) e ?ids=1,2,3&motivo=... (listas de excepciones).
    const rodeoFiltro = Number(searchParams.get('rodeo')) || null;
    const idsParam = searchParams.get('ids');
    const idsFiltro = idsParam ? new Set(idsParam.split(',').map(Number).filter(Boolean)) : null;

    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [formSuccess, setFormSuccess] = useState<string | null>(null);
    const [form, setForm] = useState<Partial<AnimalRequest>>({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO', pesoInicial: null });
    const [rodeoLotes, setRodeoLotes] = useState<RodeoLoteResponse[]>([]);

    const [createRodeoModal, setCreateRodeoModal] = useState(false);
    const [submittingRodeo, setSubmittingRodeo] = useState(false);
    const [formErrorRodeo, setFormErrorRodeo] = useState<string | null>(null);
    const [formRodeo, setFormRodeo] = useState<Partial<RodeoRequest>>({ nombre: '' });

    const [uploadModal, setUploadModal] = useState(false);
    const [submittingUpload, setSubmittingUpload] = useState(false);
    const [formErrorUpload, setFormErrorUpload] = useState<string | null>(null);
    const [fileUpload, setFileUpload] = useState<File | null>(null);
    const [reporteCarga, setReporteCarga] = useState<CargaMasivaResponse | null>(null);

    const [uploadEstadosModal, setUploadEstadosModal] = useState(false);
    const [submittingUploadEstados, setSubmittingUploadEstados] = useState(false);
    const [formErrorUploadEstados, setFormErrorUploadEstados] = useState<string | null>(null);
    const [fileUploadEstados, setFileUploadEstados] = useState<File | null>(null);
    const [reporteCargaEstados, setReporteCargaEstados] = useState<CargaMasivaEstadoResponse | null>(null);

    const [sortField, setSortField] = useState<string | null>(null);
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    const [vincularModalAnimal, setVincularModalAnimal] = useState<AnimalResponse | null>(null);
    const [caravanasDisponibles, setCaravanasDisponibles] = useState<CaravanaResponse[]>([]);
    const [selectedCaravanaId, setSelectedCaravanaId] = useState<number | null>(null);
    const [submittingCaravana, setSubmittingCaravana] = useState(false);
    const [formErrorCaravana, setFormErrorCaravana] = useState<string | null>(null);
    const [desvinculandoId, setDesvinculandoId] = useState<number | null>(null);
    const [desvincularModalAnimal, setDesvincularModalAnimal] = useState<AnimalResponse | null>(null);
    const [formErrorDesvincular, setFormErrorDesvincular] = useState<string | null>(null);
    const [undoToast, setUndoToast] = useState<{ animalId: number; caravanaId: number; alias: string } | null>(null);

    const [asignarAutoModal, setAsignarAutoModal] = useState(false);
    const [asignandoAuto, setAsignandoAuto] = useState(false);
    const [loteAsignarAuto, setLoteAsignarAuto] = useState<number | null>(null);
    const [formErrorAsignarAuto, setFormErrorAsignarAuto] = useState<string | null>(null);
    const [asignacionAutoToast, setAsignacionAutoToast] = useState<string | null>(null);
    const [resultadoAsignacion, setResultadoAsignacion] = useState<AnimalResponse[] | null>(null);
    const [descargandoAsignacion, setDescargandoAsignacion] = useState(false);
    const [errorDescargaAsignacion, setErrorDescargaAsignacion] = useState<string | null>(null);
    const [exportandoSensores, setExportandoSensores] = useState(false);

    // Alert detail overlay
    const [selectedAlertaForAnimal, setSelectedAlertaForAnimal] = useState<AlertaResponse | null>(null);
    const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
    const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);
    const [loadingAlerta, setLoadingAlerta] = useState<number | null>(null); // stores the animalId being loaded

    const fetchAnimales = async () => {
        try {
            const data = await getAnimales(selectedId || undefined);
            setAnimales(data);
        } catch (err) {
            console.error(err);
        }
    };

    const handleVerAlerta = async (animal: AnimalResponse) => {
        if (!selectedId) return;
        setLoadingAlerta(animal.id);
        try {
            const alerta = await getAlertaParaAnimal(selectedId, animal.id);
            if (alerta) {
                setSelectedAlertaForAnimal(alerta);
            } else {
                // Fallback: synthesize a minimal AlertaResponse so the detail panel
                // can still open and show what info is available from the backend.
                const fallback: AlertaResponse = {
                    id: 0,
                    animalId: animal.id,
                    tipo: 'CELO',
                    estado: animal.estadoActual,
                    tsGeneracion: new Date().toISOString(),
                    accionRecomendada: "Realizar inseminación artificial dentro de la ventana óptima.",
                };
                setSelectedAlertaForAnimal(fallback);
            }
        } catch (err) {
            console.error('Error al cargar alerta del animal:', err);
        } finally {
            setLoadingAlerta(null);
        }
    };

    const handleAlertaActionComplete = (alertaId: number, nuevoEstado: 'CONFIRMADA' | 'DESCARTADA') => {
        if (alertaId === 0) return; // fallback mock — no backend update needed
        // Optimistically update the animal's estado if the alert was resolved
        if (nuevoEstado === 'CONFIRMADA' || nuevoEstado === 'DESCARTADA') {
            setAnimales(prev =>
                prev.map(a =>
                    a.id === selectedAlertaForAnimal?.animalId
                        ? { ...a, estadoActual: 'ACTIVO' }
                        : a
                )
            );
        }
    };

    const handleDarDeBaja = async (id: number) => {
        const confirmado = await showConfirm('¿Estás seguro de que querés dar de baja a este animal?', {
            title: 'Dar de baja animal',
            confirmText: 'Sí, dar de baja',
            danger: true,
        });
        if (!confirmado) return;
        try {
            await desactivarAnimal(id);
            setAnimales(prev => prev.filter(a => a.id !== id));
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } } };
            showAlert(e.response?.data?.message || 'Error al dar de baja el animal.', { variant: 'error' });
        }
    };

    const openVincularModal = async (animal: AnimalResponse) => {
        setVincularModalAnimal(animal);
        setSelectedCaravanaId(null);
        setFormErrorCaravana(null);
        try {
            const disponibles = await getCaravanasDisponibles(selectedId || animal.establecimientoId);
            setCaravanasDisponibles(disponibles);
        } catch {
            setCaravanasDisponibles([]);
        }
    };

    const handleVincularSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!vincularModalAnimal || !selectedCaravanaId) {
            setFormErrorCaravana('Seleccioná una caravana disponible.');
            return;
        }
        setFormErrorCaravana(null);
        setSubmittingCaravana(true);
        try {
            const actualizado = await vincularCaravana(vincularModalAnimal.id, selectedCaravanaId);
            setAnimales(prev => prev.map(a => a.id === actualizado.id ? actualizado : a));
            setVincularModalAnimal(null);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorCaravana(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al vincular la caravana.');
        } finally {
            setSubmittingCaravana(false);
        }
    };

    const openDesvincularModal = (animal: AnimalResponse) => {
        setFormErrorDesvincular(null);
        setDesvincularModalAnimal(animal);
    };

    const handleConfirmDesvincular = async () => {
        const animal = desvincularModalAnimal;
        if (!animal || !animal.caravanaId) return;
        const caravanaId = animal.caravanaId;
        setDesvinculandoId(animal.id);
        setFormErrorDesvincular(null);
        try {
            const actualizado = await desvincularCaravana(animal.id);
            setAnimales(prev => prev.map(a => a.id === actualizado.id ? actualizado : a));
            setDesvincularModalAnimal(null);
            setUndoToast({ animalId: animal.id, caravanaId, alias: animal.alias || animal.idCaravanaOficial || 'el animal' });
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorDesvincular(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al desvincular la caravana.');
        } finally {
            setDesvinculandoId(null);
        }
    };

    const handleUndoDesvincular = async () => {
        if (!undoToast) return;
        const { animalId, caravanaId } = undoToast;
        setUndoToast(null);
        try {
            const actualizado = await vincularCaravana(animalId, caravanaId);
            setAnimales(prev => prev.map(a => a.id === actualizado.id ? actualizado : a));
        } catch {
            showAlert('No se pudo deshacer la acción. La caravana ya podría estar vinculada a otro animal.', { variant: 'error' });
        }
    };

    // Mismo criterio que el backend: activos, sin caravana y, si se eligió, del lote.
    const contarSinCaravana = (loteId: number | null) => animales.filter(a =>
        a.establecimientoId === selectedId && !a.caravanaId && a.estadoActual !== 'INACTIVO'
        && (loteId === null || a.rodeoLoteId === loteId)
    ).length;
    const animalesSinCaravana = contarSinCaravana(loteAsignarAuto);
    const caravanasAAsignar = Math.min(animalesSinCaravana, caravanasDisponibles.length);
    const animalesSinCubrir = animalesSinCaravana - caravanasAAsignar;
    const nombreLoteAsignarAuto = rodeoLotes.find(l => l.id === loteAsignarAuto)?.nombre;

    const openAsignarAutoModal = async () => {
        if (!selectedId) return;
        setFormErrorAsignarAuto(null);
        setLoteAsignarAuto(null);
        const [disponibles, lotes] = await Promise.all([
            getCaravanasDisponibles(selectedId).catch(() => [] as CaravanaResponse[]),
            getRodeoLotes(selectedId).catch(() => [] as RodeoLoteResponse[]),
        ]);
        setCaravanasDisponibles(disponibles);
        setRodeoLotes(lotes.filter(l => l.activo));
        setAsignarAutoModal(true);
    };

    const handleConfirmAsignarAuto = async () => {
        if (!selectedId) return;
        setFormErrorAsignarAuto(null);
        setAsignandoAuto(true);
        try {
            const asignados = await asignarCaravanasAleatoriamente(selectedId, loteAsignarAuto ?? undefined);
            const porId = new Map(asignados.map(a => [a.id, a]));
            setAnimales(prev => prev.map(a => porId.get(a.id) ?? a));
            setAsignarAutoModal(false);
            if (asignados.length === 0) {
                // Otro usuario se llevó las caravanas entre que se abrió el modal y se confirmó.
                setAsignacionAutoToast('No se asignó ninguna caravana.');
            } else {
                setErrorDescargaAsignacion(null);
                setResultadoAsignacion([...asignados].sort(compararParaManga));
            }
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorAsignarAuto(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al asignar las caravanas.');
        } finally {
            setAsignandoAuto(false);
        }
    };

    const handleDescargarAsignacion = async () => {
        if (!selectedId || !resultadoAsignacion) return;
        setErrorDescargaAsignacion(null);
        setDescargandoAsignacion(true);
        try {
            const excel = await descargarAsignacionesCaravanasExcel(selectedId, resultadoAsignacion.map(a => a.id));
            guardarArchivo(excel, `asignacion-caravanas-${hoyIso()}.xlsx`);
        } catch {
            setErrorDescargaAsignacion('No se pudo descargar el Excel. Probá de nuevo.');
        } finally {
            setDescargandoAsignacion(false);
        }
    };

    useEffect(() => {
        (async () => {
            try {
                if (!selectedId) { setError('No tenés establecimientos asociados.'); return; }
                const animales = await getAnimales(selectedId);
                setAnimales(animales);
            } catch (err) {
                const e = err as { response?: { data?: { message?: string } } };
                setError(e.response?.data?.message || 'Error al cargar animales.');
            } finally {
                setLoading(false);
            }
        })();
    }, [selectedId]);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setForm(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormError('No hay establecimiento seleccionado.');
            return;
        }
        if (!form.idCaravanaOficial?.trim()) {
            setFormError('Ingresá el identificador del animal.');
            return;
        }
        if (!form.fechaNacimiento) {
            setFormError('Ingresá la fecha de nacimiento.');
            return;
        }
        if (form.fechaNacimiento > hoyIso()) {
            setFormError('La fecha de nacimiento no puede ser futura.');
            return;
        }
        if (!form.rodeoLoteId) {
            setFormError('Seleccioná un lote.');
            return;
        }
        setFormError(null);
        setFormSuccess(null);
        setSubmitting(true);
        try {
            const nuevo = await crearAnimal({ ...form, establecimientoId: selectedId } as AnimalRequest);
            setAnimales(prev => [nuevo, ...prev]);
            setFormSuccess('Animal registrado exitosamente.');
            setTimeout(() => {
                setShowModal(false);
                setFormSuccess(null);
            }, 1200);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormError(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al crear el animal.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleChangeRodeo = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
        const { name, value } = e.target;
        setFormRodeo(prev => ({ ...prev, [name]: value || null }));
    };

    const handleSubmitRodeo = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) return;
        setFormErrorRodeo(null);
        setSubmittingRodeo(true);
        try {
            const nuevo = await crearRodeo({ ...formRodeo, establecimientoId: selectedId } as RodeoRequest);
            setRodeoLotes(prev => [nuevo, ...prev]);
            setCreateRodeoModal(false);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorRodeo(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al crear el rodeo.');
        } finally {
            setSubmittingRodeo(false);
        }
    };

    const handleChangeUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setFileUpload(file ?? null);
    };

    const handleSubmitUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormErrorUpload('No hay establecimiento seleccionado.');
            return;
        }
        setFormErrorUpload(null);
        setSubmittingUpload(true);
        try {
            const result = await cargarAnimalesExcel(fileUpload as File, selectedId);
            setReporteCarga(result);
            setAnimales(prev => [...prev, ...result.animales]);
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorUpload(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al cargar los animales.');
        } finally {
            setSubmittingUpload(false);
        }
    };

    const openUploadModal = async () => {
        try {
            const lotes = await getRodeoLotes(selectedId || undefined);
            setRodeoLotes(lotes.filter(l => l.activo));
        } catch { setRodeoLotes([]); }
        setUploadModal(true);
    };

    const handleCloseUploadModal = () => {
        setUploadModal(false);
        setReporteCarga(null);
        setFileUpload(null);
        setFormErrorUpload(null);
    };

    const handleChangeUploadEstados = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setFileUploadEstados(file ?? null);
    };

    const handleSubmitUploadEstados = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedId) {
            setFormErrorUploadEstados('No hay establecimiento seleccionado.');
            return;
        }
        if (!fileUploadEstados) return;
        setFormErrorUploadEstados(null);
        setSubmittingUploadEstados(true);
        try {
            const result = await cargarEstadosExcel(fileUploadEstados, selectedId);
            setReporteCargaEstados(result);
            await fetchAnimales();
        } catch (err) {
            const e = err as { response?: { data?: { message?: string } | string } };
            setFormErrorUploadEstados(typeof e.response?.data === 'string' ? e.response.data : e.response?.data?.message || 'Error al cargar los estados.');
        } finally {
            setSubmittingUploadEstados(false);
        }
    };

    const handleCloseUploadEstadosModal = () => {
        setUploadEstadosModal(false);
        setReporteCargaEstados(null);
        setFileUploadEstados(null);
        setFormErrorUploadEstados(null);
    };

    const handleSort = (field: string) => {
        if (sortField === field) {
            if (sortDirection === 'asc') {
                setSortDirection('desc');
            } else {
                setSortField(null);
                setSortDirection('asc');
            }
        } else {
            setSortField(field);
            setSortDirection('asc');
        }
    };

    const filteredAnimales = animales.filter(a => {
        // Filter by selected establecimiento
        if (selectedId && a.establecimientoId !== selectedId) return false;
        if (activeTab === 'salud' && a.estadoSalud !== 'EN_OBSERVACION' && a.estadoSalud !== 'ENFERMO') return false;
        if (rodeoFiltro && a.rodeoLoteId !== rodeoFiltro) return false;
        if (idsFiltro && !idsFiltro.has(a.id)) return false;
        if (!searchTerm) return true;
        const q = searchTerm.toLowerCase();
        const matchEstado = (v: string | null | undefined, labels: Record<string, string>) =>
            !!v && (v.toLowerCase().includes(q) || (labels[v] ?? '').toLowerCase().includes(q));
        return (
            (a.idCaravanaOficial?.toLowerCase().includes(q) ?? false) ||
            (a.alias?.toLowerCase().includes(q) ?? false) ||
            (a.rodeoLoteNombre?.toLowerCase().includes(q) ?? false) ||
            (a.estadoActual?.toLowerCase().includes(q) ?? false) ||
            matchEstado(a.estadoReproductivo, ESTADO_REPRODUCTIVO_LABEL) ||
            matchEstado(a.estadoSalud, ESTADO_SALUD_LABEL)
        );
    });

    // Exportar sensores respeta lo que se ve en la lista (pestaña, rodeo, lista del dashboard y búsqueda).
    const idsConSensorEnLista = filteredAnimales.filter(a => a.caravanaId).map(a => a.id);

    const handleExportarSensores = async () => {
        if (!selectedId || idsConSensorEnLista.length === 0) return;
        setExportandoSensores(true);
        try {
            const excel = await descargarAsignacionesCaravanasExcel(selectedId, idsConSensorEnLista);
            guardarArchivo(excel, `sensores-${hoyIso()}.xlsx`);
        } catch {
            showAlert('No se pudo exportar la planilla de sensores. Probá de nuevo.', { variant: 'error' });
        } finally {
            setExportandoSensores(false);
        }
    };

    const sortedAnimales = [...filteredAnimales].sort((a, b) => {
        if (!sortField) return 0;
        let valA = a[sortField as keyof AnimalResponse] as string | number | null;
        let valB = b[sortField as keyof AnimalResponse] as string | number | null;
        if (valA === null || valA === undefined) valA = '';
        if (valB === null || valB === undefined) valB = '';

        if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
        if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
        return 0;
    });

    const openModal = async () => {
        setForm({ idCaravanaOficial: '', alias: '', fechaNacimiento: '', raza: '', rodeoLoteId: null, estadoActual: 'ACTIVO', pesoInicial: null, caravanaId: null });
        setFormError(null);
        setFormSuccess(null);
        const [lotes, disponibles] = await Promise.all([
            getRodeoLotes(selectedId || undefined).catch(() => [] as RodeoLoteResponse[]),
            puedeGestionarCaravanas && selectedId
                ? getCaravanasDisponibles(selectedId).catch(() => [] as CaravanaResponse[])
                : Promise.resolve([] as CaravanaResponse[]),
        ]);
        setRodeoLotes(lotes.filter(l => l.activo));
        setCaravanasDisponibles(disponibles);
        setShowModal(true);
    };

    return (
        <>
            <div className="vigia-page-container">
                <Container>
                    <div className="vigia-page-header animales-header-mobile">
                        <div className="d-flex align-items-center gap-2 flex-wrap">
                            <h2 className="vigia-page-title">Animales</h2>
                            <button className={`btn-filter${activeTab === 'general' ? ' active' : ''}`} onClick={() => setActiveTab('general')}>
                                <i className="bi bi-list" /> General
                            </button>
                            <button className={`btn-filter${activeTab === 'celo' ? ' active' : ''}`} onClick={() => setActiveTab('celo')}>
                                <i className="bi bi-search" /> Celo
                            </button>
                            <button className={`btn-filter${activeTab === 'salud' ? ' active' : ''}`} onClick={() => setActiveTab('salud')}>
                                <i className="bi bi-heart" /> Salud
                            </button>
                        </div>

                    </div>
                    <div>
                        {(esProductor || (puedeGestionarCaravanas && selectedId)) && (
                            <div className="d-flex justify-content-end align-items-center gap-2">
                                {esProductor && (
                                    <Button className="btn-vigia-success" onClick={openModal}>
                                        <i className="bi bi-plus-lg" /> Animal
                                    </Button>
                                )}
                                <Dropdown align="end">
                                    {/* bsPrefix propio: saca el caret de .dropdown-toggle */}
                                    <Dropdown.Toggle as="button" bsPrefix="animales-more-btn" title="Más acciones" aria-label="Más acciones">
                                        {exportandoSensores
                                            ? <Spinner as="span" animation="border" size="sm" />
                                            : <i className="bi bi-three-dots-vertical" />}
                                    </Dropdown.Toggle>
                                    <Dropdown.Menu className="est-menu">
                                        {esProductor && (
                                            <>
                                                <Dropdown.Header>Crear</Dropdown.Header>
                                                <Dropdown.Item as="button" onClick={() => setCreateRodeoModal(true)}>
                                                    Rodeo
                                                    <i className="bi bi-plus-lg" />
                                                </Dropdown.Item>
                                                <Dropdown.Header>Importar Excel</Dropdown.Header>
                                                <Dropdown.Item as="button" onClick={openUploadModal}>
                                                    Cargar animales
                                                    <i className="bi bi-upload" />
                                                </Dropdown.Item>
                                                <Dropdown.Item as="button" onClick={() => setUploadEstadosModal(true)}>
                                                    Cargar estados
                                                    <i className="bi bi-upload" />
                                                </Dropdown.Item>
                                            </>
                                        )}
                                        {puedeGestionarCaravanas && selectedId && (
                                            <>
                                                <Dropdown.Header>Caravanas</Dropdown.Header>
                                                <Dropdown.Item as="button" onClick={openAsignarAutoModal}>
                                                    Asignar caravanas
                                                    <i className="bi bi-shuffle" />
                                                </Dropdown.Item>
                                                <Dropdown.Item
                                                    as="button"
                                                    onClick={handleExportarSensores}
                                                    disabled={exportandoSensores || idsConSensorEnLista.length === 0}
                                                    title={idsConSensorEnLista.length === 1
                                                        ? 'Descarga la planilla del animal con sensor que estás viendo'
                                                        : `Descarga la planilla de los ${idsConSensorEnLista.length} animales con sensor que estás viendo`}
                                                >
                                                    Exportar sensores ({idsConSensorEnLista.length})
                                                    <i className="bi bi-file-earmark-excel" />
                                                </Dropdown.Item>
                                            </>
                                        )}
                                    </Dropdown.Menu>
                                </Dropdown>
                            </div>
                        )}
                    </div>

                    <div className="mb-3 mt-3 d-flex align-items-center gap-2 flex-wrap">
                        <Form.Control
                            type="text"
                            placeholder="Buscar por identificador, nombre o lote..."
                            value={searchTerm}
                            onChange={e => setSearchTerm(e.target.value)}
                            style={{ maxWidth: '400px' }}
                        />
                        {idsFiltro && (
                            <Button
                                variant="outline-secondary"
                                size="sm"
                                onClick={() => setSearchParams({})}
                                title="Quitar filtro"
                            >
                                {searchParams.get('motivo') || 'Lista del dashboard'} ({idsFiltro.size})
                                <i className="bi bi-x-lg ms-1" />
                            </Button>
                        )}
                        {rodeoFiltro && (
                            <Button
                                variant="outline-secondary"
                                size="sm"
                                onClick={() => setSearchParams({})}
                                title="Quitar filtro de rodeo"
                            >
                                Rodeo: {rodeoLotes.find(l => l.id === rodeoFiltro)?.nombre ?? `#${rodeoFiltro}`}
                                <i className="bi bi-x-lg ms-1" />
                            </Button>
                        )}
                    </div>

                    {error && <Alert variant="danger" className="mt-3">{error}</Alert>}

                    {
                        loading ? (
                            <div className="text-center mt-5">
                                <Spinner animation="border" style={{ color: '#1E3A5F' }} />
                            </div>
                        ) : (
                            <div className="vigia-table-wrapper animales-table-wrapper">
                                <table className="vigia-table">
                                    <thead>
                                        <tr>
                                            <th onClick={() => handleSort('idCaravanaOficial')} style={{ cursor: 'pointer' }}>
                                                <div className="d-flex align-items-center justify-content-center">
                                                    ID <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'idCaravanaOficial' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                </div>
                                            </th>
                                            <th onClick={() => handleSort('alias')} style={{ cursor: 'pointer' }}>
                                                <div className="d-flex align-items-center justify-content-center">
                                                    Nombre <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'alias' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                </div>
                                            </th>
                                            <th className="col-mobile-hide text-center">Caravana</th>
                                            {activeTab === 'celo' ? (
                                                <>
                                                    <th onClick={() => handleSort('estadoReproductivo')} style={{ cursor: 'pointer' }}>
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            <span className="d-none d-sm-inline">Estado Reproductivo</span>
                                                            <span className="d-sm-none">Reproductivo</span>
                                                            <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'estadoReproductivo' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                    <th onClick={() => handleSort('fechaUltimoParto')} style={{ cursor: 'pointer' }} className="col-mobile-hide">
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Fecha Último Parto <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'fechaUltimoParto' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                    <th onClick={() => handleSort('fechaUltimaInseminacion')} style={{ cursor: 'pointer' }} className="col-mobile-hide">
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Fecha Última Inseminación <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px' }}>{sortField === 'fechaUltimaInseminacion' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                </>
                                            ) : (
                                                <>
                                                    <th onClick={() => handleSort('rodeoLoteNombre')} style={{ cursor: 'pointer' }} className="col-mobile-hide">
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Rodeo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'rodeoLoteNombre' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                    <th onClick={() => handleSort('estadoActual')} style={{ cursor: 'pointer' }}>
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Estado <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'estadoActual' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                    <th onClick={() => handleSort('estadoSalud')} style={{ cursor: 'pointer' }} className="col-mobile-hide">
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Salud <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'estadoSalud' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                    <th onClick={() => handleSort('estadoReproductivo')} style={{ cursor: 'pointer' }} className="col-mobile-hide">
                                                        <div className="d-flex align-items-center justify-content-center">
                                                            Reproductivo <span style={{ width: '16px', display: 'inline-block', marginLeft: '4px', textAlign: 'left' }}>{sortField === 'estadoReproductivo' && (sortDirection === 'asc' ? '↑' : '↓')}</span>
                                                        </div>
                                                    </th>
                                                </>
                                            )}
                                            <th className="text-center">Acciones</th>
                                        </tr >
                                    </thead >
                                    <tbody>
                                        {sortedAnimales.length === 0 ? (
                                            <tr>
                                                <td colSpan={8} className="text-center py-4 text-muted">
                                                    No se encontraron animales.
                                                </td>
                                            </tr >
                                        ) : (
                                            sortedAnimales.map(a => (
                                                <tr key={a.id} className="animal-row">
                                                    <td className="fw-medium">{a.idCaravanaOficial || '—'}</td>
                                                    <td>{a.alias || '—'}</td>
                                                    <td className="col-mobile-hide">
                                                        {a.caravanaId ? (
                                                            <div className="d-flex align-items-center justify-content-between gap-2">
                                                                <div className="text-truncate" style={{ minWidth: 0 }}>
                                                                    <div className="d-flex align-items-center gap-2">
                                                                        <span className="text-truncate">{a.caravanaAlias || a.caravanaDevEui}</span>
                                                                        {(() => {
                                                                            const bateria = formatBateria(a.caravanaBateria);
                                                                            return (
                                                                                <span style={{ fontSize: '0.75rem', color: bateria.amber ? '#8a6a00' : '#2f7d3b', flexShrink: 0 }}>
                                                                                    {bateria.amber && <span className="bateria-dot" />}
                                                                                    {bateria.text}
                                                                                </span>
                                                                            );
                                                                        })()}
                                                                    </div>
                                                                    {(() => {
                                                                        const senial = formatUltimaSenial(a.caravanaUltimaSenial);
                                                                        return (
                                                                            <div title={senial.title} style={{ fontSize: '0.7rem', color: senial.amber ? '#8a6a00' : '#9aa5b1' }}>
                                                                                señal {senial.text}
                                                                            </div>
                                                                        );
                                                                    })()}
                                                                </div>
                                                                {puedeGestionarCaravanas && (
                                                                    <Button
                                                                        variant="outline-secondary"
                                                                        size="sm"
                                                                        className="flex-shrink-0"
                                                                        disabled={desvinculandoId === a.id}
                                                                        onClick={() => openDesvincularModal(a)}
                                                                        title="Desvincular"
                                                                    >
                                                                        {desvinculandoId === a.id ? <Spinner as="span" animation="border" size="sm" /> : <i className="bi bi-x-lg"></i>}
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        ) : (
                                                            <div className="d-flex align-items-center justify-content-between gap-2">
                                                                <span className="text-muted">Sin vincular</span>
                                                                {puedeGestionarCaravanas && (
                                                                    <Button variant="vigia-outline" size="sm" className="flex-shrink-0" onClick={() => openVincularModal(a)}>
                                                                        Vincular
                                                                    </Button>
                                                                )}
                                                            </div>
                                                        )}
                                                    </td>
                                                    {activeTab === 'celo' ? (
                                                        <>
                                                            <td className="text-center">
                                                                {a.estadoReproductivo ? (
                                                                    <span className={`estado-badge-celo ${a.estadoReproductivo === 'GESTANTE' ? 'celo-gestante' :
                                                                        a.estadoReproductivo === 'CELO' ? 'celo-celo' :
                                                                            a.estadoReproductivo === 'VACIA' ? 'celo-vacia' : 'celo-otro'
                                                                        }`}>{ESTADO_REPRODUCTIVO_LABEL[a.estadoReproductivo] ?? a.estadoReproductivo}</span>
                                                                ) : '—'}
                                                            </td>
                                                            <td className="col-mobile-hide">{formatFecha(a.fechaUltimoParto ?? null)}</td>
                                                            <td className="col-mobile-hide">{formatFecha(a.fechaUltimaInseminacion ?? null)}</td>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <td className="col-mobile-hide">{a.rodeoLoteNombre || '—'}</td>
                                                            <td className="text-center"><span className={`estado-badge ${estadoClass(a.estadoActual)}`}>{a.estadoActual}</span></td>
                                                            <td className="text-center col-mobile-hide">
                                                                {a.estadoSalud ? (
                                                                    <span className={`badge ${a.estadoSalud === 'SANO' ? 'bg-success' :
                                                                        a.estadoSalud === 'ENFERMO' ? 'bg-danger' :
                                                                            a.estadoSalud === 'EN_OBSERVACION' ? 'bg-warning text-dark' : 'bg-secondary'
                                                                        }`}>{ESTADO_SALUD_LABEL[a.estadoSalud] ?? a.estadoSalud}</span>
                                                                ) : '—'}
                                                            </td>
                                                            <td className="text-center col-mobile-hide">
                                                                {a.estadoReproductivo ? (
                                                                    <span className={`badge ${a.estadoReproductivo === 'GESTANTE' ? 'bg-success' :
                                                                        a.estadoReproductivo === 'CELO' ? 'bg-danger' :
                                                                            a.estadoReproductivo === 'VACIA' ? 'bg-secondary' : 'bg-info'
                                                                        }`}>{ESTADO_REPRODUCTIVO_LABEL[a.estadoReproductivo] ?? a.estadoReproductivo}</span>
                                                                ) : '—'}
                                                            </td>
                                                        </>
                                                    )}
                                                    <td className="text-center">
                                                        <div className="d-flex justify-content-center gap-2">
                                                            <Button variant="vigia-outline" size="sm" onClick={() => navigate(`/animales/${a.id}`)}>
                                                                <i className="bi bi-eye"></i> Ver
                                                            </Button>
                                                            {a.estadoActual === 'CON_ALERTA' && (
                                                                <Button
                                                                    variant="outline-warning"
                                                                    size="sm"
                                                                    title="Ver detalle de alerta"
                                                                    disabled={loadingAlerta === a.id}
                                                                    onClick={() => handleVerAlerta(a)}
                                                                >
                                                                    {loadingAlerta === a.id
                                                                        ? <Spinner as="span" animation="border" size="sm" />
                                                                        : <><i className="bi bi-bell-fill"></i> Alerta</>}
                                                                </Button>
                                                            )}
                                                            {esProductor && (
                                                                <Button variant="outline-danger" size="sm" onClick={() => handleDarDeBaja(a.id)}>
                                                                    <i className="bi bi-trash"></i>
                                                                </Button>
                                                            )}
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody >
                                </table >
                            </div >
                        )
                    }
                </Container >

                <Modal show={showModal} onHide={() => setShowModal(false)} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Nuevo Animal</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formError && <Alert variant="danger">{formError}</Alert>}
                        {formSuccess && <Alert variant="success">{formSuccess}</Alert>}
                        <Form onSubmit={handleSubmit} id="form-nuevo-animal">
                            <Form.Group className="mb-3">
                                <Form.Label>Identificador*</Form.Label>
                                <Form.Control name="idCaravanaOficial" type="text" placeholder="Ej: #F4829" value={form.idCaravanaOficial ?? ''} onChange={handleChange} required />
                            </Form.Group>
                            <Form.Group className="mb-3">
                                <Form.Label>Nombre</Form.Label>
                                <Form.Control name="alias" type="text" placeholder="Ej: Lola" value={form.alias ?? ''} onChange={handleChange} />
                            </Form.Group>
                            <Form.Group className="mb-3">
                                <Form.Label>Fecha de Nacimiento*</Form.Label>
                                <Form.Control name="fechaNacimiento" type="date" max={hoyIso()} value={form.fechaNacimiento ?? ''} onChange={handleChange} required />
                            </Form.Group>
                            <Form.Group className="mb-3">
                                <Form.Label>Raza</Form.Label>
                                <Form.Control name="raza" type="text" placeholder="Ej: Holstein" value={form.raza ?? ''} onChange={handleChange} />
                            </Form.Group>
                            <Form.Group className="mb-3">
                                <Form.Label>Lote*</Form.Label>
                                <Form.Select
                                    name="rodeoLoteId"
                                    value={form.rodeoLoteId ?? ''}
                                    onChange={handleChange}
                                    required
                                >
                                    <option value="">Seleccioná una opción</option>
                                    {rodeoLotes.map(l => (
                                        <option key={l.id} value={l.id}>{l.nombre}</option>
                                    ))}
                                </Form.Select>
                            </Form.Group>
                            <Form.Group className="mb-3">
                                <Form.Label>Peso inicial (kg)</Form.Label>
                                <Form.Control
                                    name="pesoInicial"
                                    type="number"
                                    min="0"
                                    step="0.1"
                                    placeholder="Ej: 320.5 (opcional)"
                                    value={form.pesoInicial ?? ''}
                                    onChange={e => setForm(prev => ({ ...prev, pesoInicial: e.target.value ? parseFloat(e.target.value) : null }))}
                                />
                            </Form.Group>
                            {puedeGestionarCaravanas && (
                                <Form.Group className="mb-3">
                                    <Form.Label>Caravana</Form.Label>
                                    <Form.Select
                                        name="caravanaId"
                                        value={form.caravanaId ?? ''}
                                        onChange={e => setForm(prev => ({ ...prev, caravanaId: e.target.value ? Number(e.target.value) : null }))}
                                        disabled={caravanasDisponibles.length === 0}
                                    >
                                        <option value="">Sin caravana (vincular después)</option>
                                        {caravanasDisponibles.map(c => (
                                            <option key={c.id} value={c.id}>{labelCaravanaOpcion(c)}</option>
                                        ))}
                                    </Form.Select>
                                    {caravanasDisponibles.length === 0 && (
                                        <div className="mt-2" style={{ color: '#cde', fontSize: '0.85rem' }}>
                                            No hay caravanas disponibles en este establecimiento.
                                        </div>
                                    )}
                                </Form.Group>
                            )}
                        </Form>
                    </Modal.Body>
                    <Modal.Footer>
                        <Button type="submit" form="form-nuevo-animal" disabled={submitting} className="btn-guardar-animal">
                            {submitting ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                        </Button>
                    </Modal.Footer>
                </Modal>

                <Modal show={createRodeoModal} onHide={() => setCreateRodeoModal(false)} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Nuevo Rodeo</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorRodeo && <Alert variant="danger">{formErrorRodeo}</Alert>}
                        <Form onSubmit={handleSubmitRodeo} id="form-nuevo-rodeo">
                            <Form.Group className="mb-3">
                                <Form.Label>Nombre*</Form.Label>
                                <Form.Control name="nombre" type="text" placeholder="Ej: #F4829" value={formRodeo.nombre ?? ''} onChange={handleChangeRodeo} required />
                            </Form.Group>
                        </Form>
                    </Modal.Body>
                    <Modal.Footer>
                        <Button type="submit" form="form-nuevo-rodeo" disabled={submittingRodeo} className="btn-guardar-animal">
                            {submittingRodeo ? <Spinner as="span" animation="border" size="sm" /> : 'Guardar'}
                        </Button>
                    </Modal.Footer>
                </Modal>

                <Modal show={uploadModal} onHide={handleCloseUploadModal} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Cargar Animales con Excel</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorUpload && <Alert variant="danger">{formErrorUpload}</Alert>}
                        {reporteCarga ? (
                            <div>
                                <Alert variant={reporteCarga.fallidos > 0 ? "warning" : "success"}>
                                    {reporteCarga.fallidos > 0 ? "Carga fallida. Se encontraron errores y no se guardó ningún registro." : "¡Carga procesada correctamente!"}
                                </Alert>
                                <ul>
                                    <li><strong>Procesados:</strong> {reporteCarga.procesados}</li>
                                    <li><strong>Exitosos:</strong> {reporteCarga.exitosos}</li>
                                    <li><strong>Fallidos:</strong> {reporteCarga.fallidos}</li>
                                </ul>
                                {reporteCarga.errores && reporteCarga.errores.length > 0 && (
                                    <div className="mt-3">
                                        <h6 className="fw-bold">Detalle de errores:</h6>
                                        <div style={{ maxHeight: '150px', overflowY: 'auto' }}>
                                            <ul className="text-danger mb-0" style={{ fontSize: '0.9em' }}>
                                                {reporteCarga.errores.map((err, i) => (
                                                    <li key={i}>{err}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <Form onSubmit={handleSubmitUpload} id="form-cargar-animales">
                                <div className="mb-3 text-white" style={{ fontSize: '0.9em' }}>
                                    <p className="mb-1">El archivo debe contener las siguientes columnas en este orden:</p>
                                    <ul>
                                        <li><strong>IdCaravana</strong> (obligatorio, no puede repetirse)</li>
                                        <li><strong>Alias</strong> (opcional, no puede repetirse)</li>
                                        <li><strong>FechaNacimiento</strong> (obligatoria, formato YYYY-MM-DD, no puede ser futura)</li>
                                        <li><strong>Raza</strong> (opcional)</li>
                                        <li><strong>RodeoLote</strong> (obligatorio, nombre de un lote existente y activo del establecimiento)</li>
                                    </ul>
                                    <Alert variant={rodeoLotes.length > 0 ? 'info' : 'warning'} className="py-2 mb-2">
                                        <i className="bi bi-info-circle me-1" />
                                        La carga <strong>no crea lotes nuevos</strong>: cada animal debe indicar un lote que ya exista y esté activo.
                                        Si falta alguno, crealo antes con el botón <strong>+ Rodeo</strong>.
                                        <div className="mt-1">
                                            {rodeoLotes.length > 0
                                                ? <>Lotes disponibles: <strong>{rodeoLotes.map(l => l.nombre).join(', ')}</strong></>
                                                : <strong>Este establecimiento todavía no tiene lotes activos.</strong>}
                                        </div>
                                    </Alert>
                                    <p className="mb-2">Las filas vacías se ignoran.</p>
                                    <div className="d-flex gap-3 mb-3">
                                        <a href="/template_animales.csv" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                            <i className="bi bi-filetype-csv me-1" /> Descargar template CSV
                                        </a>
                                        <a href="/template_animales.xlsx" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                            <i className="bi bi-filetype-xlsx me-1" /> Descargar template Excel
                                        </a>
                                    </div>
                                </div>
                                <Form.Group controlId="formUploadFile" className="mb-3">
                                    <Form.Label>Archivo*</Form.Label>
                                    <Form.Control name="file" type="file" accept=".xlsx,.xls,.csv" onChange={handleChangeUpload} required />
                                </Form.Group>
                            </Form>
                        )}
                    </Modal.Body>
                    <Modal.Footer>
                        {reporteCarga ? (
                            <Button onClick={handleCloseUploadModal} className="btn-guardar-animal">
                                Cerrar
                            </Button>
                        ) : (
                            <Button type="submit" form="form-cargar-animales" disabled={submittingUpload} className="btn-guardar-animal">
                                {submittingUpload ? <Spinner as="span" animation="border" size="sm" /> : 'Subir Archivo'}
                            </Button>
                        )}
                    </Modal.Footer>
                </Modal>
                <Modal show={uploadEstadosModal} onHide={handleCloseUploadEstadosModal} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Cargar Estados Reproductivos</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorUploadEstados && <Alert variant="danger">{formErrorUploadEstados}</Alert>}
                        {reporteCargaEstados ? (
                            <div>
                                <Alert variant={reporteCargaEstados.fallidos > 0 ? "warning" : "success"}>Carga procesada</Alert>
                                <ul>
                                    <li><strong>Procesados:</strong> {reporteCargaEstados.procesados}</li>
                                    <li><strong>Exitosos:</strong> {reporteCargaEstados.exitosos}</li>
                                    <li><strong>Fallidos:</strong> {reporteCargaEstados.fallidos}</li>
                                </ul>
                                {reporteCargaEstados.errores && reporteCargaEstados.errores.length > 0 && (
                                    <div style={{ maxHeight: '150px', overflowY: 'auto' }}>
                                        <strong>Errores:</strong>
                                        <ul className="mb-0 text-danger" style={{ fontSize: '0.9em' }}>
                                            {reporteCargaEstados.errores.map((e, idx) => (
                                                <li key={idx}>Fila {e.fila}: {e.error}</li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <Form onSubmit={handleSubmitUploadEstados} id="form-cargar-estados">
                                <div className="mb-3 text-white" style={{ fontSize: '0.9em' }}>
                                    <p className="mb-1">El archivo debe contener las siguientes columnas en este orden:</p>
                                    <ul>
                                        <li><strong>IdCaravana</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                        <li><strong>Alias</strong> (al menos IdCaravana o Alias debe estar presente)</li>
                                        <li><strong>Estado</strong> (VACIA, GESTANTE, CELO, ANESTRO)</li>
                                        <li><strong>FechaUltimoParto</strong> (opcional, formato YYYY-MM-DD)</li>
                                        <li><strong>FechaUltimaInseminacion</strong> (opcional, formato YYYY-MM-DD)</li>
                                    </ul>
                                    <div className="d-flex gap-3 mb-3">
                                        <a href="/template_estados.csv" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                            <i className="bi bi-filetype-csv me-1" /> Descargar template CSV
                                        </a>
                                        <a href="/template_estados.xlsx" download className="d-inline-block" style={{ color: '#8CC63F', fontWeight: '600' }}>
                                            <i className="bi bi-filetype-xlsx me-1" /> Descargar template Excel
                                        </a>
                                    </div>
                                </div>
                                <Form.Group className="mb-3">
                                    <Form.Label>Archivo*</Form.Label>
                                    <Form.Control name="file" type="file" accept=".xlsx,.xls,.csv" onChange={handleChangeUploadEstados} required />
                                </Form.Group>
                            </Form>
                        )}
                    </Modal.Body>
                    <Modal.Footer>
                        {reporteCargaEstados ? (
                            <Button onClick={handleCloseUploadEstadosModal} className="btn-guardar-animal">
                                Cerrar
                            </Button>
                        ) : (
                            <Button type="submit" form="form-cargar-estados" disabled={submittingUploadEstados} className="btn-guardar-animal">
                                {submittingUploadEstados ? <Spinner as="span" animation="border" size="sm" /> : 'Subir Archivo'}
                            </Button>
                        )}
                    </Modal.Footer>
                </Modal>

                <Modal show={!!vincularModalAnimal} onHide={() => setVincularModalAnimal(null)} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Vincular caravana</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorCaravana && <Alert variant="danger">{formErrorCaravana}</Alert>}
                        <Form onSubmit={handleVincularSubmit} id="form-vincular-caravana">
                            <Form.Group className="mb-3">
                                <Form.Label>Caravana disponible*</Form.Label>
                                <Form.Select
                                    value={selectedCaravanaId ?? ''}
                                    onChange={(e) => setSelectedCaravanaId(e.target.value ? Number(e.target.value) : null)}
                                >
                                    <option value="">Seleccioná una opción</option>
                                    {caravanasDisponibles.map(c => (
                                        <option key={c.id} value={c.id}>{labelCaravanaOpcion(c)}</option>
                                    ))}
                                </Form.Select>
                                {caravanasDisponibles.length === 0 && (
                                    <div className="mt-2" style={{ color: '#cde', fontSize: '0.85rem' }}>
                                        No hay caravanas disponibles en este establecimiento.
                                    </div>
                                )}
                            </Form.Group>
                        </Form>
                    </Modal.Body>
                    <Modal.Footer>
                        <Button type="submit" form="form-vincular-caravana" disabled={submittingCaravana || caravanasDisponibles.length === 0} className="btn-guardar-animal">
                            {submittingCaravana ? <Spinner as="span" animation="border" size="sm" /> : 'Vincular'}
                        </Button>
                    </Modal.Footer>
                </Modal>

                <Modal show={!!desvincularModalAnimal} onHide={() => { setDesvincularModalAnimal(null); setFormErrorDesvincular(null); }} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Desvincular caravana</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorDesvincular && <Alert variant="danger">{formErrorDesvincular}</Alert>}
                        <p className="text-center mb-0" style={{ color: '#cde' }}>
                            ¿Estás seguro de que querés desvincular la caravana de{' '}
                            <strong style={{ color: 'white' }}>
                                {desvincularModalAnimal?.alias || desvincularModalAnimal?.idCaravanaOficial || 'este animal'}
                            </strong>?
                        </p>
                    </Modal.Body>
                    <Modal.Footer className="gap-3">
                        <Button
                            className="btn-guardar-animal"
                            style={{ flex: 1 }}
                            onClick={() => setDesvincularModalAnimal(null)}
                            disabled={desvinculandoId === desvincularModalAnimal?.id}
                        >
                            Cancelar
                        </Button>
                        <Button
                            className="btn-confirmar-desvincular"
                            style={{ flex: 1 }}
                            onClick={handleConfirmDesvincular}
                            disabled={desvinculandoId === desvincularModalAnimal?.id}
                        >
                            {desvinculandoId === desvincularModalAnimal?.id ? <Spinner as="span" animation="border" size="sm" /> : 'Sí, desvincular'}
                        </Button>
                    </Modal.Footer>
                </Modal>

                <Modal show={asignarAutoModal} onHide={() => setAsignarAutoModal(false)} centered dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Asignar caravanas automáticamente</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {formErrorAsignarAuto && <Alert variant="danger">{formErrorAsignarAuto}</Alert>}
                        <Form.Group className="mb-3">
                            <Form.Label>Rodeo / Lote</Form.Label>
                            <Form.Select
                                value={loteAsignarAuto ?? ''}
                                onChange={e => setLoteAsignarAuto(e.target.value ? Number(e.target.value) : null)}
                                disabled={asignandoAuto}
                            >
                                <option value="">Todos los lotes ({contarSinCaravana(null)} sin caravana)</option>
                                {rodeoLotes.map(l => (
                                    <option key={l.id} value={l.id}>{l.nombre} ({contarSinCaravana(l.id)} sin caravana)</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                        <div style={{ color: '#cde' }}>
                            <p className="mb-2">
                                Animales sin caravana{nombreLoteAsignarAuto ? ` en ${nombreLoteAsignarAuto}` : ''}: <strong style={{ color: 'white' }}>{animalesSinCaravana}</strong>
                                <br />
                                Caravanas disponibles: <strong style={{ color: 'white' }}>{caravanasDisponibles.length}</strong>
                            </p>
                            {caravanasAAsignar === 0 ? (
                                <p className="mb-0">
                                    {animalesSinCaravana === 0
                                        ? nombreLoteAsignarAuto
                                            ? `Todos los animales de ${nombreLoteAsignarAuto} ya tienen una caravana vinculada.`
                                            : 'Todos los animales ya tienen una caravana vinculada.'
                                        : 'No hay caravanas disponibles en este establecimiento.'}
                                </p>
                            ) : (
                                <p className="mb-0">
                                    Se van a vincular <strong style={{ color: 'white' }}>{caravanasAAsignar}</strong> caravana{caravanasAAsignar === 1 ? '' : 's'} al azar.
                                    {animalesSinCubrir > 0 && (
                                        <> {animalesSinCubrir === 1 ? '1 animal va' : `${animalesSinCubrir} animales van`} a quedar sin caravana.</>
                                    )}
                                </p>
                            )}
                        </div>
                    </Modal.Body>
                    <Modal.Footer className="gap-3">
                        <Button
                            className="btn-guardar-animal"
                            style={{ flex: 1 }}
                            onClick={() => setAsignarAutoModal(false)}
                            disabled={asignandoAuto}
                        >
                            Cancelar
                        </Button>
                        <Button
                            className="btn-guardar-animal"
                            style={{ flex: 1 }}
                            onClick={handleConfirmAsignarAuto}
                            disabled={asignandoAuto || caravanasAAsignar === 0}
                        >
                            {asignandoAuto ? <Spinner as="span" animation="border" size="sm" /> : 'Asignar'}
                        </Button>
                    </Modal.Footer>
                </Modal>

                <Modal show={!!resultadoAsignacion} onHide={() => setResultadoAsignacion(null)} centered size="lg" dialogClassName="modal-nuevo-animal">
                    <Modal.Header closeButton>
                        <Modal.Title>Caravanas asignadas</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        {errorDescargaAsignacion && <Alert variant="danger">{errorDescargaAsignacion}</Alert>}
                        <p className="mb-3" style={{ color: '#cde' }}>
                            {resultadoAsignacion?.length === 1 ? 'Se asignó 1 caravana' : `Se asignaron ${resultadoAsignacion?.length ?? 0} caravanas`} al azar.
                            Colocá cada sensor en el animal indicado: si queda en otro, sus datos se registran a nombre del animal equivocado.
                            Descargá la planilla para llevarla al campo.
                        </p>
                        <div className="vigia-table-wrapper asignacion-resultado-wrapper">
                            <table className="vigia-table">
                                <thead>
                                    <tr>
                                        <th>Caravana oficial</th>
                                        <th>Alias</th>
                                        <th className="col-mobile-hide">Rodeo / Lote</th>
                                        <th>Sensor</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {resultadoAsignacion?.map(a => (
                                        <tr key={a.id}>
                                            <td className="fw-medium">{a.idCaravanaOficial || '—'}</td>
                                            <td>{a.alias || '—'}</td>
                                            <td className="col-mobile-hide">{a.rodeoLoteNombre || '—'}</td>
                                            <td className="fw-medium">{nombreSensorAnimal(a)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Modal.Body>
                    <Modal.Footer className="gap-3">
                        <Button
                            className="btn-guardar-animal"
                            style={{ flex: 1 }}
                            onClick={() => setResultadoAsignacion(null)}
                            disabled={descargandoAsignacion}
                        >
                            Cerrar
                        </Button>
                        <Button
                            className="btn-guardar-animal"
                            style={{ flex: 1 }}
                            onClick={handleDescargarAsignacion}
                            disabled={descargandoAsignacion}
                        >
                            {descargandoAsignacion
                                ? <Spinner as="span" animation="border" size="sm" />
                                : <><i className="bi bi-file-earmark-excel" /> Descargar Excel</>}
                        </Button>
                    </Modal.Footer>
                </Modal>
            </div>

            {/* Alert detail overlay — rendered at page root so position:fixed works correctly */}
            {selectedAlertaForAnimal && (
                <AlertDetailSidebar
                    alerta={selectedAlertaForAnimal}
                    mode="fixed"
                    onClose={() => setSelectedAlertaForAnimal(null)}
                    onActionComplete={handleAlertaActionComplete}
                    onOpenIntervencion={() => {
                        setIntervencionAlerta(selectedAlertaForAnimal);
                        setIsIntervencionOpen(true);
                    }}
                />
            )}

            {isIntervencionOpen && intervencionAlerta && (
                <IntervencionFormPanel
                    alerta={intervencionAlerta}
                    mode="fixed"
                    onClose={() => setIsIntervencionOpen(false)}
                    onSuccess={() => {
                        setIsIntervencionOpen(false);
                        fetchAnimales();
                    }}
                />
            )}

            {/* Undo toast — estilo snackbar de Gmail, disponible 5s tras desvincular */}
            <div className="undo-toast-container">
                <Toast
                    show={!!undoToast}
                    onClose={() => setUndoToast(null)}
                    delay={5000}
                    autohide
                    className="undo-toast"
                >
                    <Toast.Body className="d-flex align-items-center justify-content-between gap-3">
                        <span>Caravana desvinculada de {undoToast?.alias}</span>
                        <Button variant="link" className="undo-toast-btn p-0" onClick={handleUndoDesvincular}>
                            Deshacer
                        </Button>
                    </Toast.Body>
                </Toast>
                <Toast
                    show={!!asignacionAutoToast}
                    onClose={() => setAsignacionAutoToast(null)}
                    delay={4000}
                    autohide
                    className="undo-toast"
                >
                    <Toast.Body>{asignacionAutoToast}</Toast.Body>
                </Toast>
            </div>
        </>
    );
}

