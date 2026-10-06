import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { getEstablecimientos, updateConfiguracionAnestro } from '../api/establecimientosApi';
import type { EstablecimientoResponse } from '../api/establecimientosApi';
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

const SEVERIDAD_CONFIG: Record<string, { label: string; color: string; bg: string; icon: string }> = {
  BAJA:  { label: 'Baja',  color: '#2B7295', bg: '#e8f4f8', icon: 'bi-bell' },
  MEDIA: { label: 'Media', color: '#c47c0a', bg: '#fef3c7', icon: 'bi-bell-fill' },
  ALTA:  { label: 'Alta',  color: '#c0392b', bg: '#fee2e2', icon: 'bi-bell-slash-fill' },
};

export default function ConfiguracionEstablecimientoPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const rol = useAuthStore((s) => s.rol);

  const [establecimiento, setEstablecimiento] = useState<EstablecimientoResponse | null>(null);
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
        const lista = await getEstablecimientos();
        const found = lista.find((e) => e.id === numId);
        if (!found) { setErrorGlobal('No se encontró el establecimiento.'); setLoading(false); return; }
        setEstablecimiento(found);
        reset({
          diasDeteccionAnestro: found.diasDeteccionAnestro ?? DEFAULT_DIAS,
          severidadAnestro: (found.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA') ?? DEFAULT_SEVERIDAD,
        });
      } catch {
        setErrorGlobal('Error al cargar el establecimiento.');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id, navigate, reset]);

  const onSubmit = async (data: FormData) => {
    if (!establecimiento) return;
    setSaving(true);
    setErrorGlobal(null);
    try {
      const actualizado = await updateConfiguracionAnestro(establecimiento.id, data);
      setEstablecimiento(actualizado);
      reset({
        diasDeteccionAnestro: actualizado.diasDeteccionAnestro,
        severidadAnestro: actualizado.severidadAnestro as 'BAJA' | 'MEDIA' | 'ALTA',
      });
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      // El backend no devuelve { message }: el 403 trae texto plano y el 400 de validación el body default de Spring
      const status = (err as { response?: { status?: number } }).response?.status;
      setErrorGlobal(status === 403
        ? 'No tenés permisos para modificar este establecimiento.'
        : 'Error al guardar los cambios.');
    } finally {
      setSaving(false);
    }
  };

  // ── Acceso restringido ────────────────────────────────────────────────────

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

  if (errorGlobal && !establecimiento) {
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
        <button className="cfg-breadcrumb-link" onClick={() => navigate('/establecimientos')}>
          Establecimientos
        </button>
        <span className="cfg-breadcrumb-sep"><i className="bi bi-chevron-right" /></span>
        <span className="cfg-breadcrumb-current">{establecimiento?.nombre}</span>
        <span className="cfg-breadcrumb-sep"><i className="bi bi-chevron-right" /></span>
        <span className="cfg-breadcrumb-current">Configuración de Anestro</span>
      </div>

      {/* Header */}
      <div className="cfg-header">
        <i className="bi bi-gear-fill cfg-header-gear" />
        <div className="cfg-header-text">
          <h1 className="cfg-title">Configuración de Anestro</h1>
          <p className="cfg-subtitle">
            <i className="bi bi-building me-1" />
            {establecimiento?.nombre}
          </p>
        </div>
      </div>

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
                <div className={`cfg-input-wrapper ${errors.diasDeteccionAnestro ? 'cfg-input-wrapper--error' : ''}`}>
                  <input
                    id="diasDeteccionAnestro"
                    type="number"
                    className="cfg-input"
                    min={1}
                    max={365}
                    placeholder={String(DEFAULT_DIAS)}
                    {...register('diasDeteccionAnestro', { valueAsNumber: true })}
                  />
                  <span className="cfg-input-suffix">días</span>
                </div>
                {errors.diasDeteccionAnestro && (
                  <span className="cfg-field-error">
                    <i className="bi bi-exclamation-circle me-1" />
                    {errors.diasDeteccionAnestro.message}
                  </span>
                )}
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
                        const cfg = SEVERIDAD_CONFIG[nivel];
                        const isActive = field.value === nivel;
                        return (
                          <button
                            key={nivel}
                            type="button"
                            className={`cfg-toggle-pill ${isActive ? 'cfg-toggle-pill--active' : ''}`}
                            style={isActive ? { backgroundColor: cfg.bg, color: cfg.color, borderColor: cfg.color } : {}}
                            onClick={() => field.onChange(nivel)}
                          >
                            <i
                              className={`bi ${cfg.icon} cfg-toggle-icon`}
                              style={isActive ? { color: cfg.color } : {}}
                            />
                            {cfg.label}
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

          {/* Fila inferior: caja de info a todo el ancho */}
          <div className="cfg-card-footer">
            <div className="cfg-help-box">
              <i className="bi bi-info-circle-fill cfg-help-icon" />
              <div>
                <p className="cfg-help-title">¿Qué controla este parámetro?</p>
                <p className="cfg-help-text">
                  Define cuántos días consecutivos sin actividad de ciclo reproductivo
                  deben transcurrir para que el sistema genere una alerta de anestro.
                  El valor por defecto es <strong>{DEFAULT_DIAS} días</strong>.
                </p>
              </div>
            </div>
          </div>

        </div>

        {/* Acciones */}
        <div className="cfg-actions">
          <button
            type="button"
            className="cfg-btn cfg-btn-cancel"
            onClick={() => navigate('/establecimientos')}
          >
            <i className="bi bi-arrow-left me-2" />
            Cancelar
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
