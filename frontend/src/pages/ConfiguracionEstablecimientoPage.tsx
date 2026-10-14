import { useState, useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm, useWatch, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Form, InputGroup } from 'react-bootstrap';
import { MapContainer, TileLayer, Marker, ZoomControl, useMap, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import MapInvalidator from '../components/MapInvalidator';
import { PIN_NORMAL } from '../utils/animalPins';
import { SEVERIDAD } from '../utils/severidadColores';
import {
  getConfigAnestro, updateConfigAnestro,
  getConfigAislamiento, updateConfigAislamiento,
  getEstablecimientos, updateEstablecimiento,
} from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
import { useAuthStore } from '../store/authStore';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import {
  schema, SECCIONES,
  DEFAULT_DIAS, DEFAULT_SEVERIDAD,
  AISLAMIENTO_MIN, AISLAMIENTO_MAX, DEFAULT_AISLAMIENTO,
} from './configuracionEstablecimientoSchema';
import type { FormData, SeccionId } from './configuracionEstablecimientoSchema';
import './ConfiguracionEstablecimientoPage.css';

type Severidad = FormData['severidadAnestro'];

// Texto sobre el color plano de la rampa: oscuro sobre verde/amarillo, blanco sobre rojo (contraste AA).
const SEVERIDAD_OPCION = {
  BAJA:  { label: 'Baja',  texto: '#111827' },
  MEDIA: { label: 'Media', texto: '#111827' },
  ALTA:  { label: 'Alta',  texto: '#ffffff' },
} as const;

function mensajeError(err: unknown, accion: 'ver' | 'editar') {
  const e = err as { response?: { status?: number; data?: { message?: string } } };
  if (e.response?.status === 403) return `No tenés permiso para ${accion} la configuración de este establecimiento.`;
  return e.response?.data?.message || (accion === 'ver' ? 'Error al cargar la configuración.' : 'Error al guardar los cambios.');
}

// Centro del mapa si el establecimiento no tiene coordenadas válidas al cargar.
const CENTRO_DEFAULT: [number, number] = [-34.6037, -58.3816];

// Click en el mapa → nuevas coordenadas.
function SelectorUbicacion({ onElegir }: { onElegir: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onElegir(e.latlng.lat, e.latlng.lng) });
  return null;
}

// Si la posición queda fuera de la vista (p. ej. se tipeó otra coordenada), mueve el mapa hasta ella.
function SeguirPosicion({ lat, lng }: { lat?: number; lng?: number }) {
  const map = useMap();
  useEffect(() => {
    if (lat == null || lng == null) return;
    if (!map.getBounds().contains([lat, lng])) map.panTo([lat, lng]);
  }, [map, lat, lng]);
  return null;
}

// El Navbar carga la lista de establecimientos una sola vez: se actualiza a mano tras renombrar/mover.
function sincronizarStore(r: EstablecimientoResponse) {
  const { establecimientos, setEstablecimientos } = useEstablecimientoStore.getState();
  setEstablecimientos(establecimientos.map((e) => (
    e.id === r.id ? { ...e, nombre: r.nombre, latitud: r.latitud, longitud: r.longitud } : e
  )));
}

// Mismo estilo que el aviso de calibración de AnimalDetailPage.
function InfoParametro({ children }: { children: ReactNode }) {
  return (
    <Alert variant="info" className="d-flex align-items-center shadow-sm mb-0" style={{ borderLeft: '5px solid #0dcaf0' }}>
      <i className="bi bi-info-circle-fill fs-4 me-3 text-info"></i>
      <div>
        <h5 className="alert-heading mb-1" style={{ fontSize: '0.95rem' }}>¿Qué controla este parámetro?</h5>
        <p className="mb-0" style={{ fontSize: '0.85rem' }}>{children}</p>
      </div>
    </Alert>
  );
}

export default function ConfiguracionEstablecimientoPage() {
  const { id } = useParams<{ id: string }>();
  const numId = Number(id);
  const navigate = useNavigate();
  const rol = useAuthStore((s) => s.rol);
  const esAdmin = rol === 'ADMINISTRADOR';
  // Mismos roles que el backend: PUT /establecimientos/{id} es ADMIN/PRODUCTOR; anestro/aislamiento, PRODUCTOR/VETERINARIO.
  const editaDatos = esAdmin || rol === 'PRODUCTOR';
  const veAlertas = !esAdmin;
  // El veterinario no tiene acceso a /establecimientos (llega desde el Navbar): vuelve a la página anterior.
  const veListado = rol === 'PRODUCTOR' || esAdmin;
  const volver = () => (veListado ? navigate('/establecimientos') : navigate(-1));

  const [nombreGuardado, setNombreGuardado] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [errorGlobal, setErrorGlobal] = useState<string | null>(null);
  const [activa, setActiva] = useState<SeccionId>('general');
  const [mapaAmpliado, setMapaAmpliado] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    resetField,
    setValue,
    control,
    formState: { errors, dirtyFields },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    mode: 'onTouched',
    defaultValues: {
      nombre: '',
      diasDeteccionAnestro: DEFAULT_DIAS,
      severidadAnestro: DEFAULT_SEVERIDAD,
      umbralAislamientoM: DEFAULT_AISLAMIENTO,
    },
  });
  const [latitud, longitud, umbral] = useWatch({ control, name: ['latitud', 'longitud', 'umbralAislamientoM'] });

  useEffect(() => {
    if (!id || isNaN(numId)) { navigate('/'); return; }

    const fetchData = async () => {
      try {
        // GET /establecimientos devuelve todos al admin y solo los propios al resto.
        const [est, anestro, aislamiento] = await Promise.all([
          getEstablecimientos().then((lista) => lista.find((e) => e.id === numId)),
          veAlertas ? getConfigAnestro(numId) : null,
          veAlertas ? getConfigAislamiento(numId) : null,
        ]);
        if (!est) {
          setErrorGlobal('No se encontró el establecimiento o no tenés acceso.');
          return;
        }
        setNombreGuardado(est.nombre);
        reset({
          nombre: est.nombre,
          latitud: est.latitud,
          longitud: est.longitud,
          diasDeteccionAnestro: anestro?.diasDeteccionAnestro ?? DEFAULT_DIAS,
          severidadAnestro: (anestro?.severidadAnestro as Severidad) ?? DEFAULT_SEVERIDAD,
          umbralAislamientoM: aislamiento?.umbralAislamientoM ?? DEFAULT_AISLAMIENTO,
        });
      } catch (err) {
        setErrorGlobal(mensajeError(err, 'ver'));
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id, numId, veAlertas, navigate, reset]);

  // Mientras dura el scroll suave de irA, guarda cómo terminarlo; el observer no toca la sección activa.
  const saltando = useRef<(() => void) | null>(null);

  // Resalta en el menú la sección que está a la vista.
  useEffect(() => {
    if (loading) return;
    const visibles = new Set<string>();
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((e) => (e.isIntersecting ? visibles.add(e.target.id) : visibles.delete(e.target.id)));
      const primera = SECCIONES.find((s) => visibles.has(`cfg-${s.id}`));
      if (primera && !saltando.current) setActiva(primera.id);
    }, { rootMargin: '-140px 0px -60% 0px' });
    SECCIONES.forEach((s) => { const el = document.getElementById(`cfg-${s.id}`); if (el) observer.observe(el); });
    return () => observer.disconnect();
  }, [loading]);

  const secciones = SECCIONES.filter((s) => veAlertas || !s.soloConfigAlertas).map((s) => ({
    ...s,
    conError: s.fields.some((f) => errors[f]),
    sucia: s.fields.some((f) => dirtyFields[f]),
  }));
  const sucias = secciones.filter((s) => s.sucia);
  const puedeGuardar = !saving && sucias.length > 0;
  const estaSucia = (seccionId: SeccionId) => sucias.some((s) => s.id === seccionId);

  const irA = (seccionId: SeccionId) => {
    saltando.current?.(); // un click durante otro salto lo reemplaza
    setActiva(seccionId);
    const terminar = () => {
      window.removeEventListener('scrollend', terminar);
      window.clearTimeout(corte);
      saltando.current = null;
    };
    // scrollend no llega si no hubo scroll (ya estaba en esa sección) o si el navegador no lo soporta.
    const corte = window.setTimeout(terminar, 1200);
    window.addEventListener('scrollend', terminar);
    saltando.current = terminar;
    document.getElementById(`cfg-${seccionId}`)?.scrollIntoView({ behavior: 'smooth' });
  };

  const onSubmit = async (data: FormData) => {
    setSaving(true);
    setErrorGlobal(null);
    // Solo se envían las secciones con cambios; cada una se resetea por separado
    // para que, si una falla, siga marcada como pendiente.
    const tareas: Promise<unknown>[] = [];
    if (estaSucia('general') || estaSucia('ubicacion')) {
      // El endpoint recibe nombre y coordenadas juntos.
      tareas.push(updateEstablecimiento(numId, {
        nombre: data.nombre,
        latitud: data.latitud,
        longitud: data.longitud,
      }).then((r) => {
        resetField('nombre', { defaultValue: r.nombre });
        resetField('latitud', { defaultValue: r.latitud });
        resetField('longitud', { defaultValue: r.longitud });
        setNombreGuardado(r.nombre);
        sincronizarStore(r);
      }));
    }
    if (estaSucia('anestro')) {
      tareas.push(updateConfigAnestro(numId, {
        diasDeteccionAnestro: data.diasDeteccionAnestro,
        severidadAnestro: data.severidadAnestro,
      }).then((r) => {
        resetField('diasDeteccionAnestro', { defaultValue: r.diasDeteccionAnestro });
        resetField('severidadAnestro', { defaultValue: r.severidadAnestro as Severidad });
      }));
    }
    if (estaSucia('aislamiento')) {
      tareas.push(updateConfigAislamiento(numId, { umbralAislamientoM: data.umbralAislamientoM })
        .then((r) => resetField('umbralAislamientoM', { defaultValue: r.umbralAislamientoM })));
    }
    const rechazo = (await Promise.allSettled(tareas))
      .find((r): r is PromiseRejectedResult => r.status === 'rejected');
    if (rechazo) {
      setErrorGlobal(mensajeError(rechazo.reason, 'editar'));
    } else {
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    }
    setSaving(false);
  };

  // ── Acceso restringido (guard de UI — el backend también lo valida) ─────────

  if (!esAdmin && rol !== 'PRODUCTOR' && rol !== 'VETERINARIO') {
    return (
      <div className="cfg-page">
        <div className="cfg-access-denied">
          <i className="bi bi-lock-fill cfg-lock-icon" />
          <h3>Acceso restringido</h3>
          <p>Solo administradores, productores y veterinarios pueden editar la configuración del establecimiento.</p>
          <button className="cfg-btn-back" onClick={() => navigate(-1)}>
            <i className="bi bi-arrow-left me-2" />Volver
          </button>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="cfg-page">
        <div className="cfg-loading">
          <div className="cfg-spinner" />
          <p>Cargando configuración…</p>
        </div>
      </div>
    );
  }

  if (errorGlobal && !nombreGuardado) {
    return (
      <div className="cfg-page">
        <div className="cfg-error-state">
          <i className="bi bi-exclamation-triangle-fill cfg-error-icon" />
          <p>{errorGlobal}</p>
          <button className="cfg-btn-back" onClick={() => navigate(-1)}>
            <i className="bi bi-arrow-left me-2" />Volver
          </button>
        </div>
      </div>
    );
  }

  const coordsValidas = schema.shape.latitud.safeParse(latitud).success
    && schema.shape.longitud.safeParse(longitud).success;
  const posicion: [number, number] | null = coordsValidas ? [latitud, longitud] : null;
  // Mismo redondeo que el step de los inputs (6 decimales ≈ 10 cm).
  const elegirUbicacion = (lat: number, lng: number) => {
    setValue('latitud', Number(lat.toFixed(6)), { shouldDirty: true, shouldValidate: true });
    setValue('longitud', Number(lng.toFixed(6)), { shouldDirty: true, shouldValidate: true });
  };

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="cfg-page">

      {/* Breadcrumb */}
      <div className="cfg-breadcrumb">
        {veListado ? (
          <button className="cfg-breadcrumb-link" onClick={volver}>
            Establecimientos
          </button>
        ) : (
          <span className="cfg-breadcrumb-current">Establecimientos</span>
        )}
        <span className="cfg-breadcrumb-sep"><i className="bi bi-chevron-right" /></span>
        <span className="cfg-breadcrumb-current">{nombreGuardado}</span>
        <span className="cfg-breadcrumb-sep"><i className="bi bi-chevron-right" /></span>
        <span className="cfg-breadcrumb-current">Configuración</span>
      </div>

      <h1 className="cfg-title">Configuración del establecimiento</h1>

      {/* Toast */}
      <div className={`cfg-toast ${saved ? 'cfg-toast-visible' : ''}`}>
        <i className="bi bi-check-circle-fill me-2" />
        Cambios guardados correctamente
      </div>

      <div className="cfg-layout">

        {/* Menú de secciones */}
        <nav className="cfg-nav">
          <div className="cfg-nav-title">SECCIONES</div>
          {secciones.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`cfg-nav-item ${activa === s.id ? 'cfg-nav-item--active' : ''}`}
              onClick={() => irA(s.id)}
            >
              <i className={`bi ${s.icon}`} />
              <span className="cfg-nav-label">{s.label}</span>
              {s.conError
                ? <i className="bi bi-exclamation-circle-fill cfg-nav-error" />
                : s.sucia && <span className="cfg-dot" title="Cambios sin guardar" />}
            </button>
          ))}
        </nav>

        <form className="cfg-form" onSubmit={handleSubmit(onSubmit)} noValidate>

          {/* Error global */}
          {errorGlobal && (
            <div className="cfg-alert cfg-alert-error">
              <i className="bi bi-exclamation-circle-fill me-2" />
              {errorGlobal}
            </div>
          )}

          {/* ── General ───────────────────────────────────────────────────── */}
          <section id="cfg-general" className="cfg-section">
            <div className="cfg-card">
              <div className="cfg-col">
                <div className="cfg-col-header">
                  <div className="cfg-col-icon cfg-col-icon--teal">
                    <i className="bi bi-building" />
                  </div>
                  <div>
                    <h2 className="cfg-card-title">Datos generales</h2>
                    <p className="cfg-card-subtitle">Cómo se identifica el establecimiento en VigIA</p>
                  </div>
                  {!editaDatos && <span className="cfg-badge-info">Solo el productor puede editar estos datos</span>}
                </div>
                <div className="cfg-field cfg-field--narrow">
                  <label className="cfg-label" htmlFor="nombre">Nombre</label>
                  <Form.Control
                    id="nombre"
                    type="text"
                    maxLength={80}
                    {...register('nombre')}
                    readOnly={!editaDatos}
                    isInvalid={!!errors.nombre}
                  />
                  <Form.Control.Feedback type="invalid">{errors.nombre?.message}</Form.Control.Feedback>
                </div>
              </div>
            </div>
          </section>

          {/* ── Ubicación ─────────────────────────────────────────────────── */}
          <section id="cfg-ubicacion" className="cfg-section">
            <div className="cfg-card">
              <div className="cfg-col">
                <div className="cfg-col-header">
                  <div className="cfg-col-icon cfg-col-icon--blue">
                    <i className="bi bi-geo-alt-fill" />
                  </div>
                  <div>
                    <h2 className="cfg-card-title">Ubicación</h2>
                    <p className="cfg-card-subtitle">Coordenadas del casco del establecimiento</p>
                  </div>
                  {!editaDatos && <span className="cfg-badge-info">Solo el productor puede editar estos datos</span>}
                </div>
                <div className="cfg-grid">
                  {(['latitud', 'longitud'] as const).map((campo) => (
                    <div className="cfg-field" key={campo}>
                      <label className="cfg-label" htmlFor={campo}>{campo === 'latitud' ? 'Latitud' : 'Longitud'}</label>
                      <InputGroup hasValidation>
                        <Form.Control
                          id={campo}
                          type="number"
                          step="0.000001"
                          {...register(campo, { valueAsNumber: true })}
                          readOnly={!editaDatos}
                          isInvalid={!!errors[campo]}
                        />
                        <InputGroup.Text>°</InputGroup.Text>
                        <Form.Control.Feedback type="invalid">{errors[campo]?.message}</Form.Control.Feedback>
                      </InputGroup>
                    </div>
                  ))}
                </div>
              </div>
              <div className={`cfg-map-preview${mapaAmpliado ? ' cfg-map-preview--ampliado' : ''}${editaDatos ? ' cfg-map-preview--editable' : ''}`}>
                {/* Siempre visible: aunque las coordenadas tipeadas sean inválidas, se puede elegir la ubicación con un click. */}
                <MapContainer
                  center={posicion ?? CENTRO_DEFAULT}
                  zoom={14}
                  zoomControl={false}
                  attributionControl={false}
                  scrollWheelZoom={false}
                  style={{ height: '100%' }}
                >
                  <ZoomControl position="bottomright" />
                  <TileLayer
                    attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
                    url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                  />
                  {/* interactive=false: el click sobre el pin también llega al mapa */}
                  {posicion && <Marker position={posicion} icon={PIN_NORMAL} interactive={false} />}
                  {editaDatos && <SelectorUbicacion onElegir={elegirUbicacion} />}
                  <SeguirPosicion lat={posicion?.[0]} lng={posicion?.[1]} />
                  <MapInvalidator smooth />
                </MapContainer>
                <button
                  type="button"
                  className="cfg-map-expand"
                  onClick={() => setMapaAmpliado((v) => !v)}
                  aria-label={mapaAmpliado ? 'Reducir mapa' : 'Ampliar mapa'}
                  aria-pressed={mapaAmpliado}
                  title={mapaAmpliado ? 'Reducir mapa' : 'Ampliar mapa'}
                >
                  <i className={`bi ${mapaAmpliado ? 'bi-fullscreen-exit' : 'bi-arrows-fullscreen'}`} />
                </button>
                <span className="cfg-map-note">
                  {editaDatos ? 'Hacé click en el mapa para elegir la ubicación' : 'Vista previa de la ubicación'}
                </span>
              </div>
              {/* Crédito de Esri fuera del mapa (attributionControl desactivado), igual que en ActividadTab */}
              <p className="cfg-map-credit">
                Imágenes: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP y la comunidad de usuarios de GIS.
              </p>
            </div>
          </section>

          {/* Anestro y aislamiento: no aplican al administrador (el backend le devuelve 403). */}
          {veAlertas && (<>
            {/* ── Anestro ───────────────────────────────────────────────────── */}
            <section id="cfg-anestro" className="cfg-section">
              <div className="cfg-card">
                <div className="cfg-card-body">

                  {/* Columna: días */}
                  <div className="cfg-col">
                    <div className="cfg-col-header">
                      <div className="cfg-col-icon cfg-col-icon--blue">
                        <i className="bi bi-calendar-check" />
                      </div>
                      <div>
                        <h2 className="cfg-card-title">Días sin ciclo</h2>
                        <p className="cfg-card-subtitle">Umbral de detección de anestro</p>
                      </div>
                    </div>

                    <div className="cfg-field">
                      <label className="cfg-label" htmlFor="diasDeteccionAnestro">
                        Cantidad de días
                      </label>
                      <InputGroup hasValidation>
                        <Form.Control
                          id="diasDeteccionAnestro"
                          type="number"
                          min={1}
                          max={365}
                          placeholder={String(DEFAULT_DIAS)}
                          {...register('diasDeteccionAnestro', { valueAsNumber: true })}
                          isInvalid={!!errors.diasDeteccionAnestro}
                        />
                        <InputGroup.Text>días</InputGroup.Text>
                        <Form.Control.Feedback type="invalid">
                          {errors.diasDeteccionAnestro?.message}
                        </Form.Control.Feedback>
                      </InputGroup>
                    </div>
                  </div>

                  {/* Divisor vertical */}
                  <div className="cfg-divider" />

                  {/* Columna: prioridad */}
                  <div className="cfg-col">
                    <div className="cfg-col-header">
                      <div className="cfg-col-icon cfg-col-icon--amber">
                        <i className="bi bi-bell-fill" />
                      </div>
                      <div>
                        <h2 className="cfg-card-title">Prioridad de la alerta</h2>
                        <p className="cfg-card-subtitle">Nivel de severidad al dispararse</p>
                      </div>
                    </div>

                    <div className="cfg-field">
                      <label className="cfg-label">Nivel de prioridad</label>
                      <Controller
                        control={control}
                        name="severidadAnestro"
                        render={({ field }) => (
                          <div className="cfg-toggle-group">
                            {(['BAJA', 'MEDIA', 'ALTA'] as const).map((nivel) => {
                              const opcion = SEVERIDAD_OPCION[nivel];
                              const isActive = field.value === nivel;
                              return (
                                <button
                                  key={nivel}
                                  type="button"
                                  className={`cfg-toggle-pill ${isActive ? 'cfg-toggle-pill--active' : ''}`}
                                  style={isActive ? { backgroundColor: SEVERIDAD[nivel].bar, color: opcion.texto } : {}}
                                  onClick={() => field.onChange(nivel)}
                                >
                                  {opcion.label}
                                </button>
                              );
                            })}
                          </div>
                        )}
                      />
                      {errors.severidadAnestro && (
                        <span className="cfg-field-error">
                          <i className="bi bi-exclamation-circle me-1" />
                          {errors.severidadAnestro.message}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              <InfoParametro>
                Define cuántos días consecutivos sin actividad de ciclo reproductivo
                deben transcurrir para que el sistema genere una alerta de anestro.
                El valor por defecto es <strong>{DEFAULT_DIAS} días</strong>.
              </InfoParametro>
            </section>

            {/* ── Aislamiento ───────────────────────────────────────────────── */}
            <section id="cfg-aislamiento" className="cfg-section">
              <div className="cfg-card">
                <div className="cfg-col">
                  <div className="cfg-col-header">
                    <div className="cfg-col-icon cfg-col-icon--red">
                      <i className="bi bi-bounding-box-circles" />
                    </div>
                    <div>
                      <h2 className="cfg-card-title">Umbral de aislamiento</h2>
                      <p className="cfg-card-subtitle">Distancia al rodeo para considerar a un animal aislado</p>
                    </div>
                  </div>

                  <div className="cfg-field cfg-field--narrow">
                    <label className="cfg-label" htmlFor="umbralAislamientoM">
                      Distancia mínima al rodeo
                    </label>
                    <InputGroup hasValidation>
                      <Form.Control
                        id="umbralAislamientoM"
                        type="number"
                        min={AISLAMIENTO_MIN}
                        max={AISLAMIENTO_MAX}
                        placeholder={String(DEFAULT_AISLAMIENTO)}
                        {...register('umbralAislamientoM', { valueAsNumber: true })}
                        isInvalid={!!errors.umbralAislamientoM}
                      />
                      <InputGroup.Text>metros</InputGroup.Text>
                      <Form.Control.Feedback type="invalid">
                        {errors.umbralAislamientoM?.message}
                      </Form.Control.Feedback>
                    </InputGroup>
                    <div className="cfg-hint-row">
                      <span>Entre {AISLAMIENTO_MIN} y {AISLAMIENTO_MAX} m</span>
                      {umbral !== DEFAULT_AISLAMIENTO && (
                        <button
                          type="button"
                          className="cfg-link-btn"
                          onClick={() => setValue('umbralAislamientoM', DEFAULT_AISLAMIENTO, { shouldDirty: true, shouldValidate: true })}
                        >
                          <i className="bi bi-arrow-counterclockwise me-1" />
                          Restablecer a {DEFAULT_AISLAMIENTO} m
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              <InfoParametro>
                Define a qué distancia del resto del rodeo tiene que permanecer un animal
                para que el sistema lo considere aislado y genere una alerta de aislamiento.
                Un valor menor detecta antes, pero puede generar más falsas alarmas.
                El valor por defecto es <strong>{DEFAULT_AISLAMIENTO} metros</strong>.
              </InfoParametro>
            </section>

          </>)}

          {/* Barra de acciones */}
          <div className="cfg-savebar">
            <span className="cfg-savebar-status">
              {sucias.length > 0 && <span className="cfg-dot" />}
              {sucias.length > 0
                ? `Cambios sin guardar en ${sucias.length} ${sucias.length === 1 ? 'sección' : 'secciones'}: ${sucias.map((s) => s.label).join(', ')}`
                : 'Sin cambios pendientes'}
            </span>
            <button
              type="button"
              className="cfg-btn cfg-btn-cancel"
              onClick={volver}
            >
              Volver
            </button>
            <button
              type="submit"
              className="cfg-btn cfg-btn-save"
              disabled={!puedeGuardar}
            >
              {saving ? <><span className="cfg-spinner-sm" />Guardando…</> : 'Guardar Cambios'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
