import type { AlertaResponse } from './alertaApi';

/**
 * Una sola conexión SSE de alertas por establecimiento en cada pestaña, compartida por todos los
 * componentes que escuchan alertas (mapa, sidebar, histórico, ventana de inseminación).
 *
 * El front le habla al backend por HTTP/1.1 y el navegador admite 6 conexiones por host sumando
 * todas las pestañas: con un EventSource por componente se agotaban y los requests a la API
 * quedaban colgados en pending.
 */

export interface SuscriptorAlertas {
  onAlerta: (alerta: AlertaResponse) => void;
  /** Volvió la conexión después de un corte. El replay del backend solo trae alertas nuevas, no cambios. */
  onReconectado?: () => void;
}

interface Conexion {
  source: EventSource;
  suscriptores: Set<SuscriptorAlertas>;
}

const conexiones = new Map<string, Conexion>();

/** Devuelve la función que da de baja la suscripción; la conexión se cierra con el último suscriptor. */
export function suscribirAlertas(establecimientoId: number, token: string | null,
  suscriptor: SuscriptorAlertas): () => void {
  const clave = `${establecimientoId}|${token ?? ''}`;
  let conexion = conexiones.get(clave);
  if (!conexion) {
    conexion = abrirConexion(clave, establecimientoId, token);
    conexiones.set(clave, conexion);
  }
  conexion.suscriptores.add(suscriptor);

  const actual = conexion;
  return () => {
    actual.suscriptores.delete(suscriptor);
    if (actual.suscriptores.size === 0) {
      actual.source.close();
      if (conexiones.get(clave) === actual) conexiones.delete(clave);
    }
  };
}

function abrirConexion(clave: string, establecimientoId: number, token: string | null): Conexion {
  const API_URL = import.meta.env.VITE_API_URL as string;
  let url = `${API_URL}/alertas/stream?establecimientoId=${establecimientoId}`;
  if (token) {
    url += `&token=${token}`;
  }

  const source = new EventSource(url);
  const suscriptores = new Set<SuscriptorAlertas>();
  let reconectando = false;

  // Cada suscriptor por separado: si uno falla, los demás igual reciben la alerta.
  const avisar = (aviso: (s: SuscriptorAlertas) => void) => {
    [...suscriptores].forEach((s) => {
      try {
        aviso(s);
      } catch (error) {
        console.error('Error procesando evento SSE de alertas', error);
      }
    });
  };

  source.onmessage = (event) => {
    let alerta: AlertaResponse;
    try {
      alerta = JSON.parse(event.data) as AlertaResponse;
    } catch (error) {
      console.error('Evento SSE de alertas inválido', error);
      return;
    }
    avisar((s) => s.onAlerta(alerta));
  };

  source.onopen = () => {
    if (reconectando) avisar((s) => s.onReconectado?.());
    reconectando = false;
  };

  // EventSource reintenta solo ante cortes de red; si queda CLOSED (p. ej. 401/403) no vuelve, y se
  // la saca del mapa para que el próximo suscriptor abra una nueva.
  source.onerror = (error) => {
    reconectando = true;
    console.error('SSE de alertas: conexión cortada', error);
    if (source.readyState === EventSource.CLOSED && conexiones.get(clave)?.source === source) {
      conexiones.delete(clave);
    }
  };

  return { source, suscriptores };
}
