import { MapContainer, TileLayer, Marker, Popup, useMap, ZoomControl } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useEffect, useState } from 'react';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import AlertSidebar from '../components/AlertSidebar';
import { type AnimalMapa, getAnimalesMapa } from '../api/animalesApi';
import { type AlertaResumenResponse, getResumenAlertas } from '../api/alertaApi';

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

const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

function MapUpdater({ center }: { center: [number, number] }) {
  const map = useMap();
  const [lat, lng] = center;
  useEffect(() => {
    map.flyTo([lat, lng], map.getZoom(), { duration: 1.5 });
  }, [lat, lng, map]);
  return null;
}

export default function MapPage() {
  const { establecimientos, selectedId } = useEstablecimientoStore();
  const [isAlertsOpen, setIsAlertsOpen] = useState(false);
  const [animales, setAnimales] = useState<AnimalMapa[]>([]);
  const [resumenAlertas, setResumenAlertas] = useState<AlertaResumenResponse | null>(null);

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

  const activeEstablecimiento = establecimientos.find(e => e.id === selectedId);

  const animalConPosicion = animales.find(a => a.latitud != null && a.longitud != null);

  const center: [number, number] = animalConPosicion
    ? [animalConPosicion.latitud!, animalConPosicion.longitud!]
    : activeEstablecimiento?.latitud != null && activeEstablecimiento?.longitud != null
      ? [activeEstablecimiento.latitud, activeEstablecimiento.longitud]
      : CENTRO_CAMPO;


  return (
    <div style={{ height: '100%', width: '100%', margin: 0, padding: 0, position: 'relative', overflow: 'hidden' }}>
      <MapContainer center={center} zoom={15} zoomControl={false} style={{ height: '100%', width: '100%', zIndex: 1 }}>
        <ZoomControl position="bottomright" />
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapUpdater center={center} />
        {animales.map(animal => {
          if (animal.latitud == null || animal.longitud == null) return null;
          return (
            <Marker
              key={animal.id}
              position={[animal.latitud, animal.longitud]}
              icon={animal.sinDatosRecientes ? staleIcon : defaultIcon}
            >
              <Popup>
                <div>
                  <strong>{animal.identificador || `Animal #${animal.id}`}</strong>
                  <br />
                  <small>
                    Última posición:{' '}
                    {animal.tsUltimaPosicion
                      ? new Date(animal.tsUltimaPosicion.endsWith('Z') ? animal.tsUltimaPosicion : animal.tsUltimaPosicion + 'Z').toLocaleString()
                      : 'N/A'}
                  </small>
                  {animal.sinDatosRecientes && (
                    <div style={{ marginTop: '5px' }}>
                      <span className="badge bg-danger">Sin datos recientes</span>
                    </div>
                  )}
                </div>
              </Popup>
            </Marker>
          );
        })}
      </MapContainer>

      {/* Botón de Alertas */}
      {!isAlertsOpen && (
        <button
          onClick={() => setIsAlertsOpen(true)}
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
      {isAlertsOpen && (
        <AlertSidebar onClose={() => setIsAlertsOpen(false)} />
      )}
    </div>
  );
}
