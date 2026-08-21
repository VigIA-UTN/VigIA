import { useState, useEffect, useRef, useCallback } from 'react';
import { getVentanaInseminacion, archivarAlertaPorVencimiento } from '../api/alertaApi';
import type { VentanaInseminacionResponse, AlertaResponse } from '../api/alertaApi';
import { useAuthStore } from '../store/authStore';

/**
 * Hook que consulta el endpoint GET /api/v1/alertas/{id}/ventana
 * y se actualiza de forma reactiva al recibir eventos SSE sobre la alerta.
 *
 * Mientras la ventana esta ACTIVA, ademas corre un ticker de 1 segundo
 * que decrementa tiempoRestanteSegundos localmente para que el countdown
 * se vea fluido sin depender del intervalo de polling.
 *
 * Cuando la ventana pasa a VENCIDA el ticker se detiene y el componente
 * puede reaccionar al cambio de estado.
 */

// Cuando la ventana pasa a VENCIDA el ticker se detiene y el componente
// puede reaccionar al cambio de estado.

export interface UseVentanaInseminacionResult {
  ventana: VentanaInseminacionResponse | null;
  /** Segundos localmente decrementados (fluido entre polls). */
  segundosRestantes: number;
  loading: boolean;
  error: string | null;
}

export function useVentanaInseminacion(
  alertaId: number | null | undefined,
  establecimientoId: number | null | undefined,
  /** Omitir el hook si la alerta no es de celo o no tiene ventana */
  enabled: boolean = true,
): UseVentanaInseminacionResult {
  const [ventana, setVentana] = useState<VentanaInseminacionResponse | null>(null);
  const [segundosRestantes, setSegundosRestantes] = useState<number>(0);
  // Si alertaId o enabled no estan definidos, no hay nada que cargar
  const [loading, setLoading] = useState<boolean>(!!alertaId && enabled);
  const [error, setError] = useState<string | null>(null);
  const { token } = useAuthStore();

  // Refs para evitar closures obsoletas
  const tickerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const isMountedRef = useRef(true);

  // Limpia el ticker de 1 segundo
  const clearTicker = useCallback(() => {
    if (tickerRef.current !== null) {
      clearInterval(tickerRef.current);
      tickerRef.current = null;
    }
  }, []);

  // Inicia el ticker que decrementa segundos cada segundo
  const startTicker = useCallback((initialSeconds: number, initialPct: number, estado: 'ACTIVA' | 'FUTURA') => {
    clearTicker();
    if (initialSeconds <= 0) {
      if (estado === 'FUTURA') {
         // Si FUTURA llegó a 0, significa que ahora es ACTIVA. Refrescamos.
         setVentana(prev => prev ? { ...prev, estado: 'ACTIVA', tiempoRestanteSegundos: 64800, porcentajeTranscurrido: 0 } : prev);
      }
      return;
    }

    // Calculamos la duración total real de la ventana para que el porcentaje progrese correctamente
    let duracionTotal = initialSeconds;
    if (estado === 'ACTIVA' && initialPct >= 0 && initialPct < 100) {
      duracionTotal = initialSeconds / (1 - (initialPct / 100));
    }

    let remaining = initialSeconds;
    tickerRef.current = setInterval(() => {
      remaining -= 1;
      if (!isMountedRef.current) return;
      if (remaining <= 0) {
        clearTicker();
        if (estado === 'FUTURA') {
          // La ventana futura acaba de comenzar (ahora es ACTIVA)
          // Refrescamos desde el backend o cambiamos estado manual
          setVentana(prev => prev ? { ...prev, estado: 'ACTIVA', tiempoRestanteSegundos: 64800, porcentajeTranscurrido: 0 } : prev);
          // Reiniciamos con startTicker pero no lo tenemos directamente sin causar bucles,
          // asi que idealmente disparamos fetchVentana(), pero no lo tenemos en dependencias acá.
          // El próximo poll lo arreglará.
        } else {
          // Fuerza el estado local a VENCIDA antes del siguiente poll
          setVentana(prev =>
            prev ? { ...prev, tiempoRestanteSegundos: 0, estado: 'VENCIDA', proximaAVencer: false, porcentajeTranscurrido: 100 } : prev,
          );
          setSegundosRestantes(0);

          // Disparar archivado automático on-demand en el backend si tenemos el id
          if (alertaId && establecimientoId) {
            archivarAlertaPorVencimiento(alertaId, establecimientoId).catch(err => {
              console.error('Error al archivar alerta por vencimiento:', err);
            });
          }
        }
      } else {
        setSegundosRestantes(remaining);
        if (estado === 'ACTIVA') {
          // Actualiza porcentaje localmente usando la duracionTotal estimada
          setVentana(prev => {
            if (!prev || prev.estado === 'VENCIDA') return prev;
            const pct = Math.min(100, Math.max(0, 100 - (remaining / duracionTotal) * 100));
            return {
              ...prev,
              tiempoRestanteSegundos: remaining,
              porcentajeTranscurrido: pct,
              proximaAVencer: remaining < 7_200,
            };
          });
        }
      }
    }, 1_000);
  }, [clearTicker, alertaId, establecimientoId]);

  const fetchVentana = useCallback(async () => {
    if (!alertaId || !establecimientoId || !enabled) return;

    try {
      const data = await getVentanaInseminacion(alertaId, establecimientoId);
      if (!isMountedRef.current) return;
      setVentana(data);
      setError(null);

      if (data.estado === 'ACTIVA') {
        setSegundosRestantes(data.tiempoRestanteSegundos);
        startTicker(data.tiempoRestanteSegundos, data.porcentajeTranscurrido, 'ACTIVA');
      } else if (data.estado === 'FUTURA') {
        // En el backend, tiempoRestanteSegundos es el tiempo hasta el FIN.
        // Asumiendo que la ventana es de 18 horas (64800s), calculamos el tiempo hasta el INICIO:
        const tiempoRestanteInicio = Math.max(0, data.tiempoRestanteSegundos - 64800);
        setSegundosRestantes(tiempoRestanteInicio);
        startTicker(tiempoRestanteInicio, 0, 'FUTURA');
      } else {
        clearTicker();
        setSegundosRestantes(0);
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      const e = err as { response?: { data?: string | { message?: string } }; message?: string };
      const responseData = e?.response?.data;
      const msg = typeof responseData === 'string' ? responseData : responseData?.message ?? e?.message ?? 'Error al obtener ventana de inseminacion.';
      setError(msg);
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  }, [alertaId, establecimientoId, enabled, startTicker, clearTicker]);

  useEffect(() => {
    isMountedRef.current = true;
    if (!alertaId || !establecimientoId || !enabled) {
      return;
    }

    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchVentana();

    const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';
    let sseUrl = `${API_URL}/alertas/stream?establecimientoId=${establecimientoId}`;
    if (token) {
      sseUrl += `&token=${token}`;
    }

    const eventSource = new EventSource(sseUrl);

    eventSource.onmessage = (event) => {
      if (!isMountedRef.current) return;
      try {
        const updatedAlerta = JSON.parse(event.data) as AlertaResponse;
        if (updatedAlerta.id === alertaId) {
          fetchVentana();
        }
      } catch (err) {
        console.error('Error parseando SSE en useVentanaInseminacion', err);
      }
    };

    eventSource.onerror = () => {
      // EventSource tries to reconnect automatically
    };

    return () => {
      isMountedRef.current = false;
      eventSource.close();
      clearTicker();
    };
  }, [alertaId, establecimientoId, enabled, fetchVentana, clearTicker, token]);

  return { ventana, segundosRestantes, loading, error };
}

/** Formatea segundos como "HH:mm:ss" */
export function formatCountdown(totalSeconds: number): string {
  if (totalSeconds <= 0) return '00:00:00';
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  return [h, m, s].map(v => String(v).padStart(2, '0')).join(':');
}