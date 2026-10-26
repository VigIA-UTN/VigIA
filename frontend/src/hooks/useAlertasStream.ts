import { useEffect, useRef } from 'react';
import type { AlertaResponse } from '../api/alertaApi';
import { suscribirAlertas } from '../api/alertaStream';
import { useAuthStore } from '../store/authStore';

/**
 * Escucha las alertas en tiempo real del establecimiento sobre la conexión SSE compartida
 * (ver alertaStream.ts). Los callbacks se leen siempre en su última versión, así cambiar filtros
 * u otro estado del componente no reabre la conexión.
 */
export function useAlertasStream(
  establecimientoId: number | null | undefined,
  onAlerta: (alerta: AlertaResponse) => void,
  onReconectado?: () => void,
  habilitado: boolean = true,
): void {
  const token = useAuthStore((s) => s.token);
  const onAlertaRef = useRef(onAlerta);
  const onReconectadoRef = useRef(onReconectado);

  useEffect(() => {
    onAlertaRef.current = onAlerta;
    onReconectadoRef.current = onReconectado;
  });

  useEffect(() => {
    if (!establecimientoId || !habilitado) return;
    return suscribirAlertas(establecimientoId, token, {
      onAlerta: (alerta) => onAlertaRef.current(alerta),
      onReconectado: () => onReconectadoRef.current?.(),
    });
  }, [establecimientoId, token, habilitado]);
}
