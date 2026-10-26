import { MapContainer, TileLayer, Marker, useMap, ZoomControl, Polygon, Popup, useMapEvents } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import './MapPage.css';
import L from 'leaflet';
import axios from 'axios';
import { Modal, Button, Alert } from 'react-bootstrap';
import { useEffect, useMemo, useState, useRef } from 'react';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import AlertSidebar from '../components/AlertSidebar';
import PerimetroSidebar from '../components/PerimetroSidebar';
import { type AnimalMapa, getAnimalesMapa, getAnimales } from '../api/animalesApi';
import { type AlertaResumenResponse, type AlertaResponse, aplicarAlertaEnAnimales, getResumenAlertas } from '../api/alertaApi';
import { useAlertasStream } from '../hooks/useAlertasStream';
import {
  getPerimetros,
  activarPerimetro,
  desactivarPerimetro,
  eliminarPerimetro,
  actualizarPerimetro,
  crearPerimetro,
  type PerimetroResponse,
  type CoordenadaDTO,
} from '../api/perimetrosApi';
import { getRodeoLotes, type RodeoLoteResponse } from '../api/rodeoLotesApi';
import { insertPointInClosestEdge } from '../utils/mapGeometryUtils';
import AnimalDetailSidebar from '../components/AnimalDetailSidebar';
import AlertDetailSidebar from '../components/AlertDetailSidebar';
import IntervencionFormPanel from '../components/IntervencionFormPanel';
import HeatmapLayer from '../components/heatmap/HeatmapLayer';
import HeatmapPanel from '../components/heatmap/HeatmapPanel';
import PerimetroRodeoLayer from '../components/heatmap/PerimetroRodeoLayer';
import { useHeatPoints } from '../components/heatmap/useHeatPoints';
import { defaultHeatFilter, perimetrosDeRodeo } from '../components/heatmap/heatmapFilters';
import type { HeatAnimal, HeatFilter } from '../components/heatmap/types';
import { determinarIcono } from '../utils/animalPins';
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const vertexIcon = L.divIcon({
  className: 'custom-vertex-icon',
  html: `<div style="
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background-color: #00FFF6;
    border: 3px solid #FFFFFF;
    box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    cursor: grab;
  "></div>`,
  iconSize: [16, 16],
  iconAnchor: [8, 8],
});

function MapEditorEvents({
  isEditing,
  onMapClick,
}: {
  isEditing: boolean;
  onMapClick: (lat: number, lng: number) => void;
}) {
  useMapEvents({
    click(e) {
      if (isEditing) {
        onMapClick(e.latlng.lat, e.latlng.lng);
      }
    },
  });
  return null;
}

const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

function MapRefCapture({ onMap }: { onMap: (map: L.Map) => void }) {
  const map = useMap();
  useEffect(() => {
    onMap(map);
  }, [map, onMap]);
  return null;
}

function MapUpdater({ center, trigger }: { center: [number, number], trigger: { lat: number, lng: number, t: number } | null }) {
  const map = useMap();
  const [lat, lng] = center;
  useEffect(() => {
    map.flyTo([lat, lng], map.getZoom(), { duration: 1.5 });
  }, [lat, lng, map]);

  useEffect(() => {
    if (trigger) {
      map.flyTo([trigger.lat, trigger.lng], Math.max(map.getZoom(), 16), { duration: 1.5 });
    }
  }, [trigger, map]);

  return null;
}

export default function MapPage() {
  const mapRef = useRef<L.Map | null>(null);
  const { establecimientos, selectedId } = useEstablecimientoStore();
  const [isAlertsOpen, setIsAlertsOpen] = useState(false);
  const [selectedAlertaMap, setSelectedAlertaMap] = useState<AlertaResponse | null>(null);
  const [animales, setAnimales] = useState<AnimalMapa[]>([]);
  const [resumenAlertas, setResumenAlertas] = useState<AlertaResumenResponse | null>(null);
  const [selectedAnimalMap, setSelectedAnimalMap] = useState<AnimalMapa | null>(null);
  const [flyToTrigger, setFlyToTrigger] = useState<{ lat: number, lng: number, t: number } | null>(null);
  const [isIntervencionOpen, setIsIntervencionOpen] = useState(false);
  // Guarda la alerta que dispara la intervención para que no se pierda cuando selectedAlertaMap se limpie
  const [intervencionAlerta, setIntervencionAlerta] = useState<AlertaResponse | null>(null);

  // Estados del módulo de Perímetros
  const [isPerimetrosOpen, setIsPerimetrosOpen] = useState(false);
  const [perimetros, setPerimetros] = useState<PerimetroResponse[]>([]);
  const [rodeos, setRodeos] = useState<RodeoLoteResponse[]>([]);
  const [loadingPerimetros, setLoadingPerimetros] = useState(false);
  const [hoveredPerimetroId, setHoveredPerimetroId] = useState<number | null>(null);
  const [editingPerimetro, setEditingPerimetro] = useState<PerimetroResponse | null>(null);
  const [coordenadasEditadas, setCoordenadasEditadas] = useState<CoordenadaDTO[]>([]);
  const [isSavingPerimetro, setIsSavingPerimetro] = useState(false);
  const [perimetroAEliminar, setPerimetroAEliminar] = useState<PerimetroResponse | null>(null);
  const [showConfirmDeletePerimetro, setShowConfirmDeletePerimetro] = useState(false);
  const [isDeletingPerimetro, setIsDeletingPerimetro] = useState(false);
  const [errorEliminarPerimetro, setErrorEliminarPerimetro] = useState<string | null>(null);

  // Mapa de calor
  const [heatEnabled, setHeatEnabled] = useState(false);
  const [isHeatPanelOpen, setIsHeatPanelOpen] = useState(false);
  const [heatFilterRaw, setHeatFilterRaw] = useState<HeatFilter>(() => defaultHeatFilter(null));
  const [heatAnimals, setHeatAnimals] = useState<HeatAnimal[]>([]);

  useEffect(() => {
    let isMounted = true;
    const fetchPerimetrosYRodeos = async () => {
      try {
        if (selectedId) {
          setLoadingPerimetros(true);
          const [perimetrosData, rodeosData] = await Promise.all([
            getPerimetros(selectedId),
            getRodeoLotes(selectedId),
          ]);
          if (isMounted) {
            setPerimetros(perimetrosData);
            setRodeos(rodeosData);
          }
        } else {
          await Promise.resolve();
          if (isMounted) {
            setPerimetros([]);
            setRodeos([]);
          }
        }
      } catch (err) {
        console.error("Error fetching perímetros o rodeos", err);
      } finally {
        if (isMounted) {
          setLoadingPerimetros(false);
        }
      }
    };
    fetchPerimetrosYRodeos();
    return () => {
      isMounted = false;
    };
  }, [selectedId]);

  useEffect(() => {
    let isMounted = true;
    getAnimales(selectedId ?? undefined)
      .then(list => {
        if (!isMounted) return;
        setHeatAnimals(list.map(a => ({
          id: a.id,
          caravana: a.idCaravanaOficial ?? `#${a.id}`,
          alias: a.alias,
          rodeoId: a.rodeoLoteId,
        })));
      })
      .catch(err => console.error('Error fetching animales para mapa de calor', err));
    return () => { isMounted = false; };
  }, [selectedId]);

  useEffect(() => {
    const fetchDatos = async () => {
      try {
        if (selectedId) {
          const animalesData = await getAnimalesMapa(selectedId);
          setAnimales(animalesData);
          const resumenData = await getResumenAlertas(selectedId);
          setResumenAlertas(resumenData);
        } else {
          const animalesData = await getAnimalesMapa();
          setAnimales(animalesData);
          setResumenAlertas(null);
        }
      } catch (err) {
        console.error(err);
      }
    };
    fetchDatos();
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId) return;

    let isMounted = true;
    const token = useAuthStore.getState().token;

    // Telemetry SSE
    const API_URL = import.meta.env.VITE_API_URL as string;
    let sseUrlTelemetria = `${API_URL}/animales/mapa/stream?establecimientoId=${selectedId}`;
    if (token) {
      sseUrlTelemetria += `&token=${token}`;
    }
    const eventSourceTelemetria = new EventSource(sseUrlTelemetria);

    eventSourceTelemetria.onmessage = (event) => {
      if (!isMounted) return;
      try {
        const telemetriaEvent = JSON.parse(event.data);
        setAnimales(prevAnimales => {
          return prevAnimales.map(animal => {
            if (animal.id === telemetriaEvent.animalId) {
              return {
                ...animal,
                latitud: telemetriaEvent.latitud,
                longitud: telemetriaEvent.longitud,
                tsUltimaPosicion: telemetriaEvent.fechaHora,
                sinDatosRecientes: false // Se actualiza el indicador al llegar nueva data
              };
            }
            return animal;
          });
        });
      } catch (error) {
        console.error('Error parseando evento SSE de telemetría', error);
      }
    };

    eventSourceTelemetria.onerror = (error) => {
      console.error('SSE Error de telemetría', error);
      // No lo cerramos: EventSource reintenta solo; cerrarlo deja el mapa congelado hasta recargar
    };

    return () => {
      isMounted = false;
      eventSourceTelemetria.close();
    };
  }, [selectedId]);

  // Alertas SSE (conexión compartida con el sidebar y el detalle)
  useAlertasStream(selectedId, (newAlerta) => {
    // Actualizar las alertas de cada animal al que le corresponde (en aislamiento, todos los listados)
    setAnimales(prevAnimales => aplicarAlertaEnAnimales(prevAnimales, newAlerta));

    // Los eventos salen recién cuando la alerta ya está commiteada: el resumen se puede pedir al instante.
    if (!selectedId) return;
    getResumenAlertas(selectedId)
      .then(resumenData => setResumenAlertas(resumenData))
      .catch(e => console.error('Error refrescando resumen de alertas', e));
  });

  const activeEstablecimiento = establecimientos.find(e => e.id === selectedId);
  const animalConPosicion = animales.find(a => a.latitud != null && a.longitud != null);
  const center: [number, number] = activeEstablecimiento?.latitud != null && activeEstablecimiento?.longitud != null
    ? [activeEstablecimiento.latitud, activeEstablecimiento.longitud]
    : animalConPosicion
      ? [animalConPosicion.latitud!, animalConPosicion.longitud!]
      : CENTRO_CAMPO;

  // El rodeo elegido por defecto (o uno que ya no existe en este establecimiento) cae al primero disponible
  const heatFilter = useMemo<HeatFilter>(() => {
    const invalid = heatFilterRaw.rodeoId != null && !rodeos.some(r => r.id === heatFilterRaw.rodeoId);
    if (invalid || (heatFilterRaw.scope === 'rodeo' && heatFilterRaw.rodeoId == null)) {
      return { ...heatFilterRaw, rodeoId: rodeos[0]?.id ?? null, animalId: invalid ? null : heatFilterRaw.animalId };
    }
    return heatFilterRaw;
  }, [heatFilterRaw, rodeos]);
  const { points: heatPoints, loading: heatLoading, error: heatError } = useHeatPoints(
    heatFilter,
    { establecimientoId: selectedId ?? null },
    heatEnabled,
  );
  // Perímetros activos del rodeo elegido: el toggle del panel los resalta (y oculta su contorno celeste habitual)
  const rodeoPerimetros = useMemo(
    () => perimetrosDeRodeo(perimetros, heatFilter.scope === 'rodeo' ? heatFilter.rodeoId : null),
    [perimetros, heatFilter.scope, heatFilter.rodeoId],
  );
  const perimetroNames = useMemo(() => rodeoPerimetros.map(p => p.nombre), [rodeoPerimetros]);
  const highlightedPerimetros = heatEnabled && heatFilter.perimetro
    ? rodeoPerimetros.filter(p => p.id !== editingPerimetro?.id)
    : [];

  const handleToggleActivoPerimetro = async (perimetro: PerimetroResponse) => {
    const nuevoEstado = !perimetro.activo;
    setPerimetros(prev =>
      prev.map(p => (p.id === perimetro.id ? { ...p, activo: nuevoEstado } : p))
    );
    try {
      if (nuevoEstado) {
        await activarPerimetro(perimetro.id);
      } else {
        await desactivarPerimetro(perimetro.id);
      }
    } catch (err) {
      console.error("Error al cambiar estado activo del perímetro", err);
      setPerimetros(prev =>
        prev.map(p => (p.id === perimetro.id ? { ...p, activo: !nuevoEstado } : p))
      );
    }
  };

  const handleToggleAllPerimetros = async () => {
    const todosActivos = perimetros.every(p => p.activo);
    const nuevoEstadoTodos = !todosActivos;

    // Actualizar UI inmediatamente
    setPerimetros(prev =>
      prev.map(p => ({ ...p, activo: nuevoEstadoTodos }))
    );

    try {
      // Activar todos
      if (nuevoEstadoTodos) {
        await Promise.all(perimetros.map(p => activarPerimetro(p.id)));
      }
      // Desactivar todos
      else {
        await Promise.all(perimetros.map(p => desactivarPerimetro(p.id)));
      }
    } catch (err) {
      console.error("Error al cambiar estado de todos los perímetros", err);
      // Revertir si falla
      setPerimetros(prev =>
        prev.map(p => ({ ...p, activo: !nuevoEstadoTodos }))
      );
    }
  };

  const handleStartEditPerimetro = (perimetro: PerimetroResponse) => {
    setEditingPerimetro(perimetro);
    setCoordenadasEditadas(perimetro.coordenadas ? [...perimetro.coordenadas] : []);
  };

  const handleCancelEditPerimetro = () => {
    setEditingPerimetro(null);
    setCoordenadasEditadas([]);
  };

  const handleCreateNewPerimetro = () => {
    if (!selectedId) return;

    let cLat = center[0];
    let cLng = center[1];
    let deltaLat = 0.003;
    let deltaLng = 0.003;

    if (mapRef.current) {
      const map = mapRef.current;
      const mapSize = map.getSize();
      // Si el sidebar de perímetros está abierto y hay suficiente ancho, centramos en la parte visible del mapa
      const sidebarWidth = isPerimetrosOpen && mapSize.x > 600 ? 350 : 0;
      const visibleCenterPoint = L.point(
        sidebarWidth + (mapSize.x - sidebarWidth) / 2,
        mapSize.y / 2
      );
      const visualCenter = map.containerPointToLatLng(visibleCenterPoint);
      cLat = visualCenter.lat;
      cLng = visualCenter.lng;

      const bounds = map.getBounds();
      const latSpan = Math.abs(bounds.getNorth() - bounds.getSouth());
      const lngSpan = Math.abs(bounds.getEast() - bounds.getWest());

      // Dimensionar el polígono inicial proporcional a la vista actual (~24% del span visible)
      if (latSpan > 0 && lngSpan > 0) {
        deltaLat = Math.min(Math.max(latSpan * 0.12, 0.0003), 0.02);
        deltaLng = Math.min(Math.max(lngSpan * 0.12, 0.0003), 0.02);
      }
    }

    const defaultCoords: CoordenadaDTO[] = [
      { latitud: cLat + deltaLat, longitud: cLng - deltaLng },
      { latitud: cLat + deltaLat, longitud: cLng + deltaLng },
      { latitud: cLat - deltaLat, longitud: cLng + deltaLng },
      { latitud: cLat - deltaLat, longitud: cLng - deltaLng },
    ];
    const nuevo: PerimetroResponse = {
      id: 0,
      establecimientoId: selectedId,
      establecimientoNombre: activeEstablecimiento?.nombre || '',
      nombre: `Perímetro #${perimetros.length + 1}`,
      descripcion: '',
      activo: true,
      loteIds: [],
      coordenadas: defaultCoords,
      distanciaToleranciaMetros: 0,
      zonaEstricta: false,
    };
    setEditingPerimetro(nuevo);
    setCoordenadasEditadas(defaultCoords);
  };

  const handleSaveEditPerimetro = async (nombre: string, loteIds: number[], zonaEstricta: boolean, distanciaToleranciaMetros: number, descripcion?: string) => {
    if (!editingPerimetro || !selectedId) return;
    setIsSavingPerimetro(true);
    try {
      if (editingPerimetro.id === 0) {
        const saved = await crearPerimetro({
          nombre,
          descripcion,
          establecimientoId: selectedId,
          loteIds,
          coordenadas: coordenadasEditadas,
          distanciaToleranciaMetros,
          zonaEstricta,
        });
        setPerimetros(prev => [...prev, saved]);
      } else {
        const updated = await actualizarPerimetro(editingPerimetro.id, {
          nombre,
          descripcion,
          establecimientoId: selectedId,
          loteIds,
          coordenadas: coordenadasEditadas,
          distanciaToleranciaMetros,
          zonaEstricta,
        });
        setPerimetros(prev => prev.map(p => (p.id === updated.id ? updated : p)));
      }
      setEditingPerimetro(null);
      setCoordenadasEditadas([]);
    } catch (err) {
      console.error("Error al guardar perímetro", err);
      throw err;
    } finally {
      setIsSavingPerimetro(false);
    }
  };

  const handleRequestDeletePerimetro = (perimetro: PerimetroResponse) => {
    setErrorEliminarPerimetro(null);
    setPerimetroAEliminar(perimetro);
    setShowConfirmDeletePerimetro(true);
  };

  const handleCloseDeletePerimetro = () => {
    if (isDeletingPerimetro) return;
    setShowConfirmDeletePerimetro(false);
  };

  const handleConfirmDeletePerimetro = async () => {
    if (!perimetroAEliminar) return;
    const perimetro = perimetroAEliminar;
    setIsDeletingPerimetro(true);
    setErrorEliminarPerimetro(null);
    try {
      await eliminarPerimetro(perimetro.id);
      setPerimetros(prev => prev.filter(p => p.id !== perimetro.id));
      if (hoveredPerimetroId === perimetro.id) setHoveredPerimetroId(null);
      if (editingPerimetro?.id === perimetro.id) handleCancelEditPerimetro();
      setShowConfirmDeletePerimetro(false);
    } catch (err) {
      console.error("Error al eliminar perímetro", err);
      setErrorEliminarPerimetro(
        axios.isAxiosError(err) && typeof err.response?.data === 'string'
          ? err.response.data
          : 'No se pudo eliminar el perímetro.'
      );
    } finally {
      setIsDeletingPerimetro(false);
    }
  };

  return (
    <div style={{ height: '100%', width: '100%', margin: 0, padding: 0, position: 'relative', overflow: 'hidden' }}>
      <MapContainer
        ref={mapRef}
        center={center}
        zoom={15}
        maxZoom={22}
        zoomControl={false}
        style={{ height: '100%', width: '100%', zIndex: 1 }}
      >
        <MapRefCapture onMap={(m) => { mapRef.current = m; }} />
        <ZoomControl position="bottomright" />
        <TileLayer
          attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
          url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          maxZoom={22}
          maxNativeZoom={18}
        />
        <MapUpdater center={center} trigger={flyToTrigger} />
        {heatEnabled && <HeatmapLayer points={heatPoints} />}

        {/* Captura de clicks en el mapa para agregar vértices en modo edición */}
        <MapEditorEvents
          isEditing={Boolean(editingPerimetro)}
          onMapClick={(lat, lng) => {
            setCoordenadasEditadas(prev => insertPointInClosestEdge(prev, { lat, lng }));
          }}
        />

        {/* Capa de Perímetros Activos (Contorno continuo celeste #00FFF6 con relleno 20% opacidad) */}
        {perimetros.map(p => {
          if (!p.activo || (editingPerimetro && editingPerimetro.id === p.id)) return null;
          if (highlightedPerimetros.some(h => h.id === p.id)) return null;
          const isHovered = hoveredPerimetroId === p.id;
          const positions: [number, number][] = p.coordenadas.map(c => [c.latitud, c.longitud]);
          return (
            <Polygon
              key={`activo-${p.id}`}
              positions={positions}
              pathOptions={{
                color: '#00FFF6',
                weight: isHovered ? 4 : 3,
                fillColor: '#00FFF6',
                fillOpacity: 0.2,
              }}
            />
          );
        })}

        {/* Perímetros del rodeo resaltados desde el panel de mapa de calor (rosa con rayado) */}
        {highlightedPerimetros.length > 0 && <PerimetroRodeoLayer perimetros={highlightedPerimetros} />}

        {/* Capa de Perímetro en Hover (Línea de contorno punteada #00FFF6 sin relleno) */}
        {hoveredPerimetroId && !editingPerimetro && (() => {
          const hoveredP = perimetros.find(p => p.id === hoveredPerimetroId);
          if (!hoveredP) return null;
          if (!hoveredP.activo) {
            const positions: [number, number][] = hoveredP.coordenadas.map(c => [c.latitud, c.longitud]);
            return (
              <Polygon
                key={`hover-${hoveredP.id}`}
                positions={positions}
                pathOptions={{
                  color: '#00FFF6',
                  weight: 2.5,
                  dashArray: '8, 8',
                  fill: false,
                }}
              />
            );
          }
          return null;
        })()}

        {/* Capa de Perímetro en Edición interactiva */}
        {editingPerimetro && (
          <>
            {coordenadasEditadas.length >= 3 && (
              <Polygon
                positions={coordenadasEditadas.map(c => [c.latitud, c.longitud])}
                pathOptions={{
                  color: '#00FFF6',
                  weight: 3,
                  fillColor: '#00FFF6',
                  fillOpacity: 0.25,
                  dashArray: '4, 4',
                }}
              />
            )}

            {/* Vértices arrastrables con eliminación dual: click derecho directo o click izquierdo con popup */}
            {coordenadasEditadas.map((c, idx) => (
              <Marker
                key={`vertex-${idx}`}
                position={[c.latitud, c.longitud]}
                icon={vertexIcon}
                draggable={true}
                eventHandlers={{
                  dragend: (e) => {
                    const newLatLng = e.target.getLatLng();
                    setCoordenadasEditadas(prev => {
                      const copy = [...prev];
                      copy[idx] = { latitud: newLatLng.lat, longitud: newLatLng.lng };
                      return copy;
                    });
                  },
                  contextmenu: (e) => {
                    // Click derecho: eliminación instantánea si quedan más de 3
                    L.DomEvent.stopPropagation(e);
                    if (coordenadasEditadas.length > 3) {
                      setCoordenadasEditadas(prev => prev.filter((_, i) => i !== idx));
                    }
                  },
                }}
              >
                <Popup>
                  <div style={{ textAlign: 'center', padding: '4px' }}>
                    <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}>
                      Vértice #{idx + 1}
                    </div>
                    {coordenadasEditadas.length > 3 ? (
                      <button
                        onClick={() => {
                          setCoordenadasEditadas(prev => prev.filter((_, i) => i !== idx));
                        }}
                        className="btn btn-sm btn-danger py-1 px-2"
                        style={{ fontSize: '0.78rem' }}
                      >
                        <i className="bi bi-trash3-fill"></i> Eliminar punto
                      </button>
                    ) : (
                      <span style={{ fontSize: '0.75rem', color: '#666' }}>
                        Mínimo 3 vértices requeridos
                      </span>
                    )}
                  </div>
                </Popup>
              </Marker>
            ))}
          </>
        )}

        {/* Animales en el mapa */}
        {animales.map(animal => {
          if (animal.latitud == null || animal.longitud == null) return null;

          return (
            <Marker
              key={animal.id}
              position={[animal.latitud, animal.longitud]}
              icon={determinarIcono(animal, selectedAnimalMap?.id === animal.id)}
              zIndexOffset={selectedAnimalMap?.id === animal.id ? 1000 : 0}
              eventHandlers={{
                click: () => {
                  setSelectedAnimalMap(animal);
                },
              }}
            />
          );
        })}
      </MapContainer>

      {/* Botones flotantes: Alertas / Perímetros / Mapa de calor */}
      {!isAlertsOpen && !isPerimetrosOpen && (
        <>
          <div className="map-fabs">
            <button
              className="map-fab"
              style={{ '--w': '150px' } as React.CSSProperties}
              aria-label="Alertas"
              onClick={() => {
                setSelectedAlertaMap(null);
                setIsPerimetrosOpen(false);
                setEditingPerimetro(null);
                setIsHeatPanelOpen(false);
                setIsAlertsOpen(true);
              }}
            >
              <i className="bi bi-bell-fill map-fab-icon"></i>
              <span className="map-fab-label">Alertas</span>
            </button>
            <button
              className="map-fab"
              style={{ '--w': '170px' } as React.CSSProperties}
              aria-label="Perímetros"
              onClick={() => {
                setSelectedAlertaMap(null);
                setIsAlertsOpen(false);
                setIsHeatPanelOpen(false);
                setIsPerimetrosOpen(true);
              }}
            >
              <i className="bi bi-bounding-box-circles map-fab-icon"></i>
              <span className="map-fab-label">Perímetros</span>
            </button>
            <button
              className="map-fab"
              style={{ '--w': '200px' } as React.CSSProperties}
              aria-label="Mapa de calor"
              aria-expanded={isHeatPanelOpen}
              onClick={() => setIsHeatPanelOpen(open => !open)}
            >
              <span className="map-fab-icon"><span className="map-fab-dot" /></span>
              <span className="map-fab-label">
                Mapa de calor
                {heatEnabled && <span className="map-fab-on">ON</span>}
              </span>
            </button>
          </div>
          {/* Siempre montado para poder animar entrada y salida; visibility con delay evita foco/clicks cuando está cerrado */}
          {(
            <div
              style={{
                position: 'absolute', top: '80px', bottom: '20px', left: '20px', zIndex: 1000, pointerEvents: 'none',
                opacity: isHeatPanelOpen ? 1 : 0,
                transform: isHeatPanelOpen ? 'translateX(0)' : 'translateX(-16px)',
                visibility: isHeatPanelOpen ? 'visible' : 'hidden',
                transition: `opacity 200ms ease, transform 200ms ease, visibility 0s linear ${isHeatPanelOpen ? '0s' : '200ms'}`,
              }}
            >
              <HeatmapPanel
                enabled={heatEnabled}
                onEnabledChange={setHeatEnabled}
                filter={heatFilter}
                onFilterChange={setHeatFilterRaw}
                rodeos={rodeos.map(r => ({ id: r.id, nombre: r.nombre }))}
                animals={heatAnimals}
                loading={heatLoading}
                error={heatError}
                pointCount={heatPoints.length}
                perimetroNames={perimetroNames}
              />
            </div>
          )}
        </>
      )}

      {/* Resumen de Alertas Cards */}
      {resumenAlertas && (
        <div className="resumen-alertas">
          {/* Tarjeta Reproductivo */}
          <div className="card text-center" style={{ minWidth: '100px', backgroundColor: 'rgba(255, 255, 255, 0.9)', boxShadow: '0 4px 6px rgba(0,0,0,0.2)' }}>
            <div className="card-body p-2">
              <h6 className="card-title text-muted mb-1" style={{ fontSize: '0.8rem' }}>Reproductivo</h6>
              <h4 className="card-text mb-0 fw-bold" style={{ color: '#0d6efd' }}>{resumenAlertas.reproductivo}</h4>
            </div>
          </div>

          {/* Tarjeta Sanitario */}
          <div className="card text-center" style={{ minWidth: '100px', backgroundColor: 'rgba(255, 255, 255, 0.9)', boxShadow: '0 4px 6px rgba(0,0,0,0.2)' }}>
            <div className="card-body p-2">
              <h6 className="card-title text-muted mb-1" style={{ fontSize: '0.8rem' }}>Sanitario</h6>
              <h4 className="card-text mb-0 fw-bold" style={{ color: '#198754' }}>{resumenAlertas.sanitario}</h4>
            </div>
          </div>

          {/* Tarjeta Geoespacial */}
          <div className="card text-center" style={{ minWidth: '100px', backgroundColor: 'rgba(255, 255, 255, 0.9)', boxShadow: '0 4px 6px rgba(0,0,0,0.2)' }}>
            <div className="card-body p-2">
              <h6 className="card-title text-muted mb-1" style={{ fontSize: '0.8rem' }}>Geoespacial</h6>
              <h4 className="card-text mb-0 fw-bold" style={{ color: '#dc3545' }}>{resumenAlertas.geoespacial}</h4>
            </div>
          </div>
        </div>
      )}

      {/* Sidebar de Alertas */}
      {isAlertsOpen ? (
        <AlertSidebar onClose={() => setIsAlertsOpen(false)} externalAlert={selectedAlertaMap} />
      ) : null}

      {/* Sidebar de Perímetros */}
      {isPerimetrosOpen ? (
        <PerimetroSidebar
          onClose={() => {
            setIsPerimetrosOpen(false);
            setEditingPerimetro(null);
            setCoordenadasEditadas([]);
          }}
          perimetros={perimetros}
          rodeos={rodeos}
          loading={loadingPerimetros}
          onToggleActivo={handleToggleActivoPerimetro}
          onToggleAll={handleToggleAllPerimetros}
          onStartEdit={handleStartEditPerimetro}
          onCancelEdit={handleCancelEditPerimetro}
          onSaveEdit={handleSaveEditPerimetro}
          onDelete={handleRequestDeletePerimetro}
          onCreateNew={handleCreateNewPerimetro}
          editingPerimetro={editingPerimetro}
          coordenadasEditadas={coordenadasEditadas}
          onHoverStart={(id) => setHoveredPerimetroId(id)}
          onHoverEnd={() => setHoveredPerimetroId(null)}
          isSaving={isSavingPerimetro}
        />
      ) : null}

      {/* Modal Confirmar Eliminación de Perímetro */}
      <Modal
        show={showConfirmDeletePerimetro}
        onHide={handleCloseDeletePerimetro}
        onExited={() => {
          setPerimetroAEliminar(null);
          setErrorEliminarPerimetro(null);
        }}
        centered
      >
        <Modal.Header closeButton={!isDeletingPerimetro}>
          <Modal.Title>Eliminar Perímetro</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          ¿Estás seguro de que deseas eliminar el perímetro <strong>{perimetroAEliminar?.nombre}</strong>? Esta acción no se puede deshacer.
          {errorEliminarPerimetro && (
            <Alert variant="danger" className="mt-3 mb-0">
              {errorEliminarPerimetro}
            </Alert>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={handleCloseDeletePerimetro} disabled={isDeletingPerimetro}>
            Cancelar
          </Button>
          <Button variant="danger" onClick={handleConfirmDeletePerimetro} disabled={isDeletingPerimetro}>
            {isDeletingPerimetro ? 'Eliminando...' : 'Eliminar'}
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Sidebar de Intervención */}
      {isIntervencionOpen && intervencionAlerta && (
        <IntervencionFormPanel
          alerta={intervencionAlerta}
          onClose={() => setIsIntervencionOpen(false)}
          onSuccess={() => setIsIntervencionOpen(false)}
        />
      )}

      {/* Sidebar de Detalle de Animal */}
      {selectedAnimalMap && (
        <AnimalDetailSidebar
          animalMapa={selectedAnimalMap}
          onClose={() => setSelectedAnimalMap(null)}
          onAlertClick={(alerta) => setSelectedAlertaMap(alerta)}
          onLocateClick={() => {
            if (selectedAnimalMap.latitud != null && selectedAnimalMap.longitud != null) {
              setFlyToTrigger({ lat: selectedAnimalMap.latitud, lng: selectedAnimalMap.longitud, t: Date.now() });
            }
          }}
        />
      )}

      {/* Sidebar de Detalle de Alerta Independiente (si no está abierto el AlertSidebar general) */}
      {selectedAlertaMap && !isAlertsOpen && (
        <AlertDetailSidebar
          alerta={selectedAlertaMap}
          onClose={() => setSelectedAlertaMap(null)}
          onActionComplete={(id, estado) => {
            setAnimales(prev => prev.map(a => ({
              ...a,
              alertasAnimal: a.alertasAnimal.map(al => al.id === id ? { ...al, estado } : al)
            })));
          }}
          onOpenIntervencion={() => {
            setIntervencionAlerta(selectedAlertaMap);
            setIsIntervencionOpen(true);
          }}
        />
      )}

    </div>
  );
}
