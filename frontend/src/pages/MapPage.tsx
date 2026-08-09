import { MapContainer, TileLayer, Marker, useMap, ZoomControl } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useEffect, useState } from 'react';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import { useAuthStore } from '../store/authStore';
import AlertSidebar from '../components/AlertSidebar';
import { type AnimalMapa, getAnimalesMapa } from '../api/animalesApi';
import { type AlertaResumenResponse, type AlertaResponse, getResumenAlertas } from '../api/alertaApi';
import AnimalDetailSidebar from '../components/AnimalDetailSidebar';
import AlertDetailSidebar from '../components/AlertDetailSidebar';
// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const staleIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-grey.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-grey.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const defaultIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-blue.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const alertaBajaIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-yellow.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-yellow.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const alertaMediaIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-orange.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-orange.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const alertaAltaIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png',
  iconRetinaUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

function MapUpdater({ center, trigger }: { center: [number, number], trigger: {lat: number, lng: number, t: number} | null }) {
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

function determinarIcono(animal: AnimalMapa) {
  if (animal.sinDatosRecientes) return staleIcon;
  if (animal.alertasAnimal.length === 0) return defaultIcon;
  if (animal.alertasAnimal.some(a => a.severidad !== null && a.severidad === 'ALTA')) return alertaAltaIcon;
  if (animal.alertasAnimal.some(a => a.severidad !== null && a.severidad === 'MEDIA')) return alertaMediaIcon;
  if (animal.alertasAnimal.some(a => a.severidad !== null && a.severidad === 'BAJA')) return alertaBajaIcon;
  return defaultIcon;
}

export default function MapPage() {
  const { establecimientos, selectedId } = useEstablecimientoStore();
  const [isAlertsOpen, setIsAlertsOpen] = useState(false);
  const [selectedAlertaMap, setSelectedAlertaMap] = useState<AlertaResponse | null>(null);
  const [animales, setAnimales] = useState<AnimalMapa[]>([]);
  const [resumenAlertas, setResumenAlertas] = useState<AlertaResumenResponse | null>(null);
  const [selectedAnimalMap, setSelectedAnimalMap] = useState<AnimalMapa | null>(null);
  const [flyToTrigger, setFlyToTrigger] = useState<{lat: number, lng: number, t: number} | null>(null);

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
    let sseUrlTelemetria = `http://localhost:8080/api/v1/animales/mapa/stream?establecimientoId=${selectedId}`;
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
      eventSourceTelemetria.close();
    };

    // Alertas SSE
    let sseUrlAlertas = `http://localhost:8080/api/v1/alertas/stream?establecimientoId=${selectedId}`;
    if (token) {
      sseUrlAlertas += `&token=${token}`;
    }
    const eventSourceAlertas = new EventSource(sseUrlAlertas);

    eventSourceAlertas.onmessage = async (event) => {
      if (!isMounted) return;
      try {
        const newAlerta = JSON.parse(event.data) as AlertaResponse;
        
        // Actualizar el array de alertas del animal
        setAnimales(prevAnimales => {
          return prevAnimales.map(animal => {
            if (animal.id === newAlerta.animalId) {
              if (animal.alertasAnimal.some(a => a.id === newAlerta.id)) {
                return animal;
              }
              return {
                ...animal,
                alertasAnimal: [newAlerta, ...animal.alertasAnimal]
              };
            }
            return animal;
          });
        });

        // Si la alerta es PENDIENTE, actualizamos el resumen
        if (newAlerta.estado === 'PENDIENTE') {
          const resumenData = await getResumenAlertas(selectedId);
          if (isMounted) setResumenAlertas(resumenData);
        }

      } catch (error) {
        console.error('Error parseando evento SSE de alertas', error);
      }
    };

    eventSourceAlertas.onerror = (error) => {
      console.error('SSE Error de alertas', error);
      // eventSourceAlertas.close(); // No lo cerramos acá porque EventSource reintenta automáticamente
    };

    return () => {
      isMounted = false;
      eventSourceTelemetria.close();
      eventSourceAlertas.close();
    };
  }, [selectedId]);

  const activeEstablecimiento = establecimientos.find(e => e.id === selectedId);

  const animalConPosicion = animales.find(a => a.latitud != null && a.longitud != null);

  const center: [number, number] = activeEstablecimiento?.latitud != null && activeEstablecimiento?.longitud != null
    ? [activeEstablecimiento.latitud, activeEstablecimiento.longitud]
    : animalConPosicion
      ? [animalConPosicion.latitud!, animalConPosicion.longitud!]
      : CENTRO_CAMPO;


  return (
    <div style={{ height: '100%', width: '100%', margin: 0, padding: 0, position: 'relative', overflow: 'hidden' }}>
      <MapContainer center={center} zoom={15} zoomControl={false} style={{ height: '100%', width: '100%', zIndex: 1 }}>
        <ZoomControl position="bottomright" />
        <TileLayer
          attribution='Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community'
          url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        />
        <MapUpdater center={center} trigger={flyToTrigger} />
        {animales.map(animal => {
          if (animal.latitud == null || animal.longitud == null) return null;
          return (
            <Marker
              key={animal.id}
              position={[animal.latitud, animal.longitud]}
              icon={determinarIcono(animal)}
              eventHandlers={{
                click: () => {
                  setSelectedAnimalMap(animal);
                },
              }}
            />
          );
        })}
      </MapContainer>

      {/* Botón de Alertas */}
      {!isAlertsOpen && (
        <button
          onClick={() => { setSelectedAlertaMap(null); setIsAlertsOpen(true) }}
          className="btn d-flex align-items-center gap-2 fw-bold"
          style={{
            position: 'absolute',
            top: '20px',
            left: '20px',
            zIndex: 1000,
            backgroundColor: '#1E3A5F',
            color: 'white',
            borderColor: '#13D4FB',
            borderWidth: '2px',
            borderRadius: '10px',
            padding: '8px 20px',
            boxShadow: '0 4px 6px rgba(0,0,0,0.3)'
          }}
        >
          <i className="bi bi-bell-fill" style={{ color: '#13D4FB' }}></i> Alertas
        </button>
      )}

      {/* Resumen de Alertas Cards */}
      {resumenAlertas && (
        <div
          style={{
            position: 'absolute',
            top: '20px',
            right: '20px',
            zIndex: 1000,
            display: 'flex',
            gap: '10px'
          }}
        >
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
            // Actualización local opcional, el SSE de alertas/telemetría debería refrescar
            setAnimales(prev => prev.map(a => ({
              ...a,
              alertasAnimal: a.alertasAnimal.map(al => al.id === id ? { ...al, estado } : al)
            })));
          }}
        />
      )}

    </div>
  );
}
