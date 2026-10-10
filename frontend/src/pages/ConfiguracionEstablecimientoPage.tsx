import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Form, InputGroup } from 'react-bootstrap';
import { SEVERIDAD } from '../utils/severidadColores';
import { getConfigAnestro, updateConfigAnestro } from '../api/establecimientosApi';
import type { ConfigAnestroResponse } from '../api/establecimientosApi';
import { useAuthStore } from '../store/authStore';
import './ConfiguracionEstablecimientoPage.css';

// ── Validación ────────────────────────────────────────────────────────────────

const schema = z.object({
  diasDeteccionAnestro: z
    .number({ message: 'Debe ser un número entero' })
    .int('Debe ser un número entero')
    .min(1, 'Debe ser al menos 1 día')
    .max(365, 'No puede superar los 365 días'),
  severidadAnestro: z.enum(['BAJA', 'MEDIA', 'ALTA']),
});

type FormData = z.infer<typeof schema>;

const DEFAULT_DIAS = 45;
const DEFAULT_SEVERIDAD = 'MEDIA';

// Texto sobre el color plano de la rampa: oscuro sobre verde/amarillo, blanco sobre rojo (contraste AA).
const SEVERIDAD_OPCION = {
  BAJA:  { label: 'Baja',  texto: '#111827' },
  MEDIA: { label: 'Media', texto: '#111827' },
  ALTA:  { label: 'Alta',  texto: '#ffffff' },
} as const;

export default function ConfiguracionEstablecimientoPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const rol = useAuthStore((s) => s.rol);
  // El veterinario no tiene acceso a /establecimientos (llega desde el Navbar): vuelve a la página anterior.
  const veListado = rol === 'PRODUCTOR';
  const volver = () => (veListado ? navigate('/establecimientos') : navigate(-1));

  const [config, setConfig] = useState<ConfigAnestroResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [errorGlobal, setErrorGlobal] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isDirty },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      diasDeteccionAnestro: DEFAULT_DIAS,
      severidadAnestro: DEFAULT_SEVERIDAD,
    },
  });

  useEffect(() => {
    const numId = Number(id);
    if (!id || isNaN(numId)) { navigate('/'); return; }

    const fetchData = async () => {
      try {
        // Usa el endpoint dedicado GET /establecimientos/{id}/anestro
        const data = await getConfigAnestro(numId);
        setConfig(data);
        reset({
          diasDeteccionAnestro: data.diasDeteccionAnestro ?? DEFAULT_DIAS,
          severidadAnestro: (data.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA') ?? DEFAULT_SEVERIDAD,
        });
      } catch (err) {
        const axiosError = err as { response?: { status?: number } };
        if (axiosError.response?.status === 403) {
          setErrorGlobal('No tenés permiso para ver la configuración de este establecimiento.');
        } else {
          setErrorGlobal('Error al cargar la configuración.');
        }
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id, navigate, reset]);

  const onSubmit = async (data: FormData) => {
    if (!config) return;
    setSaving(true);
    setErrorGlobal(null);
    try {
      // Usa el endpoint dedicado PUT /establecimientos/{id}/anestro
      const actualizado = await updateConfigAnestro(config.establecimientoId, {
        diasDeteccionAnestro: data.diasDeteccionAnestro,
        severidadAnestro: data.severidadAnestro,
      });
      setConfig(actualizado);
      reset({
        diasDeteccionAnestro: actualizado.diasDeteccionAnestro,
        severidadAnestro: actualizado.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA',
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      const axiosError = err as { response?: { data?: { message?: string }; status?: number } };
      if (axiosError.response?.status === 403) {
        setErrorGlobal('No tenés permiso para editar la configuración de este establecimiento.');
      } else {
        setErrorGlobal(axiosError.response?.data?.message || 'Error al guardar los cambios.');
      }
    } finally {
      setSaving(false);
    }
  };

  // ── Acceso restringido (guard de UI — el backend también lo valida) ─────────

  if (rol !== 'PRODUCTOR' && rol !== 'VETERINARIO') {
    return (
      <div className="cfg-page">
        <div className="cfg-access-denied">
          <i className="bi bi-lock-fill cfg-lock-icon" />
          <h3>Acceso restringido</h3>
          <p>Solo productores y veterinarios pueden editar la configuración de anestro.</p>
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

  if (errorGlobal && !config) {
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
        <span className="cfg-breadcrumb-current">{config?.nombreEstablecimiento}</span>
        <span className="cfg-breadcrumb-sep"><i className="bi bi-chevron-right" /></span>
        <span className="cfg-breadcrumb-current">Configuración de Anestro</span>
      </div>

      {/* Header */}
      <h1 className="cfg-title">Configuración de Anestro</h1>

      {/* Error global */}
      {errorGlobal && (
        <div className="cfg-alert cfg-alert-error">
          <i className="bi bi-exclamation-circle-fill me-2" />
          {errorGlobal}
        </div>
      )}

      {/* Toast */}
      <div className={`cfg-toast ${saved ? 'cfg-toast-visible' : ''}`}>
        <i className="bi bi-check-circle-fill me-2" />
        Cambios guardados correctamente
      </div>

      {/* Formulario */}
      <form onSubmit={handleSubmit(onSubmit)} noValidate>

        {/* ── Card única ────────────────────────────────────────────────── */}
        <div className="cfg-card">

          {/* Fila superior: días | divisor | prioridad */}
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

        {/* Info: mismo estilo que el aviso de calibración de AnimalDetailPage */}
        <Alert variant="info" className="d-flex align-items-center shadow-sm mb-4" style={{ borderLeft: '5px solid #0dcaf0' }}>
          <i className="bi bi-info-circle-fill fs-4 me-3 text-info"></i>
          <div>
            <h5 className="alert-heading mb-1" style={{ fontSize: '0.95rem' }}>¿Qué controla este parámetro?</h5>
            <p className="mb-0" style={{ fontSize: '0.85rem' }}>
              Define cuántos días consecutivos sin actividad de ciclo reproductivo
              deben transcurrir para que el sistema genere una alerta de anestro.
              El valor por defecto es <strong>{DEFAULT_DIAS} días</strong>.
            </p>
          </div>
        </Alert>

        {/* Acciones */}
        <div className="cfg-actions">
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
            disabled={saving || !isDirty}
          >
            {saving ? <><span className="cfg-spinner-sm" />Guardando…</> : 'Guardar Cambios'}
          </button>
        </div>
      </form>
    </div>
  );
}
