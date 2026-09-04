import { useState } from 'react';
import { intervenirAlerta } from '../api/alertaApi';
import type { AlertaResponse } from '../api/alertaApi';

interface IntervencionFormPanelProps {
  alerta: AlertaResponse;
  /** Nombre o alias del animal para mostrar en el header */
  animalName?: string;
  onClose: () => void;
  onSuccess: (alertaActualizada: AlertaResponse) => void;
  /** 'absolute' posiciona relativo al ancestro; 'fixed' relativo al viewport */
  mode?: 'absolute' | 'fixed';
}

export default function IntervencionFormPanel({
  alerta,
  animalName,
  onClose,
  onSuccess,
  mode = 'absolute',
}: IntervencionFormPanelProps) {
  const [isClosing, setIsClosing] = useState(false);
  const [descripcion, setDescripcion] = useState('');
  const [fechaHora, setFechaHora] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const displayName = animalName ?? `Vaca #${alerta.animalId}`;

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => onClose(), 280);
  };

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      const resultado = await intervenirAlerta(alerta.id, {
        descripcion: descripcion.trim() || undefined,
        fechaHora: fechaHora || undefined,
      });
      onSuccess(resultado);
      handleClose();
    } catch (err) {
      const e = err as { response?: { data?: string | { message?: string } } };
      const data = e?.response?.data;
      const msg = typeof data === 'string' ? data : data?.message;
      setError(msg || 'Ocurrió un error al registrar la intervención.');
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: mode === 'fixed' ? 'fixed' : 'absolute',
        top: 0,
        right: 0,
        bottom: 0,
        width: 'min(480px, 100vw)',
        maxWidth: '100vw',
        backgroundColor: '#FAFAFA',
        zIndex: 1060,
        boxShadow: '-4px 0 16px rgba(0,0,0,0.25)',
        display: 'flex',
        flexDirection: 'column',
        borderTopLeftRadius: '12px',
        borderBottomLeftRadius: '12px',
        border: '3px solid #13D4FB',
        borderRight: 'none',
        animation: isClosing
          ? 'slideOutRight 0.28s ease-in forwards'
          : 'slideInRight 0.3s ease-out forwards',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        className="p-4 bg-white border-bottom"
        style={{ zIndex: 10, borderRadius: '12px 0 0 0' }}
      >
        <div className="d-flex justify-content-between align-items-start mb-2">
          <button
            className="btn btn-link text-secondary p-0 border-0"
            onClick={handleClose}
            title="Cerrar sin registrar"
          >
            <i className="bi bi-x-lg fs-5"></i>
          </button>
        </div>

        <div className="d-flex align-items-center gap-3 mt-2">
          <div
            style={{
              width: '42px',
              height: '42px',
              borderRadius: '10px',
              backgroundColor: '#EEF2FF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <i className="bi bi-clipboard2-pulse-fill fs-5" style={{ color: '#6366F1' }}></i>
          </div>
          <div style={{ minWidth: 0 }}>
            <h2
              className="m-0 fw-bold text-break"
              style={{ color: '#1E3A5F', fontSize: '1.25rem' }}
            >
              Registrar intervención
            </h2>
            <p className="m-0 text-muted" style={{ fontSize: '0.85rem' }}>
              {displayName} &mdash; Alerta #{alerta.id}
            </p>
          </div>
        </div>
      </div>

      {/* Body */}
      <div
        className="p-4 d-flex flex-column gap-4 flex-grow-1 intervencion-scrollbar"
        style={{ overflowY: 'auto' }}
      >
        {/* Aviso: campos opcionales */}
        <div
          className="d-flex gap-3 align-items-start p-3 rounded-3"
          style={{
            backgroundColor: '#EEF2FF',
            border: '1px solid #C7D2FE',
            color: '#1E3A5F',
          }}
        >
          <i className="bi bi-info-circle-fill fs-5 mt-1" style={{ color: '#6366F1' }}></i>
          <p className="m-0" style={{ fontSize: '0.9rem' }}>
            Todos los campos son <strong>opcionales</strong>. Podés confirmar la alerta sin
            completar ninguno.
          </p>
        </div>

        {/* Campo: Descripción */}
        <div>
          <h6
            className="fw-bold mb-3 d-flex align-items-center gap-2"
            style={{ color: '#1E3A5F' }}
          >
            <i className="bi bi-chat-left-text"></i> Descripción
          </h6>
          <div className="card border-0 shadow-sm rounded-4">
            <div className="card-body p-3">
              <div className="position-relative">
                <textarea
                  className="form-control rounded-4 shadow-sm border-0"
                  rows={4}
                  placeholder="Describí la intervención realizada (tratamiento, observaciones, etc.)..."
                  value={descripcion}
                  onChange={(e) => setDescripcion(e.target.value)}
                  maxLength={500}
                  style={{ resize: 'none', paddingRight: '15px', paddingBottom: '28px' }}
                />
                <small
                  className="text-muted position-absolute"
                  style={{ bottom: '10px', right: '15px', fontSize: '0.75rem' }}
                >
                  {descripcion.length}/500
                </small>
              </div>
            </div>
          </div>
        </div>

        {/* Campo: Fecha y hora */}
        <div>
          <h6
            className="fw-bold mb-3 d-flex align-items-center gap-2"
            style={{ color: '#1E3A5F' }}
          >
            <i className="bi bi-calendar-event"></i> Fecha y hora de intervención
          </h6>
          <div className="card border-0 shadow-sm rounded-4">
            <div className="card-body p-3">
              <input
                type="datetime-local"
                className="form-control rounded-3 border-0"
                value={fechaHora}
                onChange={(e) => setFechaHora(e.target.value)}
                style={{ fontSize: '0.95rem', color: '#374151' }}
              />
              <small className="text-muted d-block mt-2" style={{ fontSize: '0.78rem' }}>
                Si no se indica, se registrará la fecha y hora actuales.
              </small>
            </div>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div
            className="d-flex align-items-center gap-2 p-3 rounded-3"
            style={{
              backgroundColor: '#FEF2F2',
              border: '1px solid #FECACA',
              color: '#B91C1C',
            }}
          >
            <i className="bi bi-exclamation-triangle-fill fs-5"></i>
            <span style={{ fontSize: '0.88rem' }}>{error}</span>
          </div>
        )}
      </div>

      {/* Footer */}
      <div
        className="p-4 bg-white border-top d-flex gap-3"
        style={{ zIndex: 10, borderRadius: '0 0 0 12px' }}
      >
        <button
          className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
          style={{
            color: '#6B7280',
            backgroundColor: 'white',
            border: '1px solid #D1D5DB',
          }}
          onClick={handleClose}
          disabled={loading}
        >
          <i className="bi bi-x-circle"></i> Cancelar
        </button>

        <button
          className="btn rounded-3 fw-semibold flex-grow-1 py-2 d-flex justify-content-center align-items-center gap-2"
          style={{
            backgroundColor: '#198754',
            color: 'white',
            border: '1px solid #198754',
          }}
          onClick={handleSubmit}
          disabled={loading}
        >
          {loading ? (
            <>
              <div className="spinner-border spinner-border-sm" role="status" />
              &nbsp;Confirmando...
            </>
          ) : (
            <>
              <i className="bi bi-check2-circle"></i>
              {descripcion || fechaHora ? 'Confirmar con intervención' : 'Confirmar alerta'}
            </>
          )}
        </button>
      </div>

      <style>{`
        @keyframes slideInRight {
          from { opacity: 0; transform: translateX(20px); }
          to   { opacity: 1; transform: translateX(0); }
        }
        @keyframes slideOutRight {
          from { opacity: 1; transform: translateX(0); }
          to   { opacity: 0; transform: translateX(20px); }
        }
        .intervencion-scrollbar::-webkit-scrollbar { width: 12px; }
        .intervencion-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .intervencion-scrollbar::-webkit-scrollbar-thumb {
          background-color: #13D4FB;
          border-radius: 10px;
          border: 3px solid transparent;
          background-clip: padding-box;
        }
        .intervencion-scrollbar::-webkit-scrollbar-thumb:hover { background-color: #0A96B1; }
      `}</style>
    </div>
  );
}
