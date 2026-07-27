import { MapContainer, TileLayer, Marker, Popup, useMap, ZoomControl } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useEffect, useState } from 'react';
import { useEstablecimientoStore } from '../store/establecimientoStore';
import AlertSidebar from '../components/AlertSidebar';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

function MapUpdater({ center }: { center: [number, number] }) {
  const map = useMap();
  const [lat, lng] = center;
  useEffect(() => {
    map.flyTo([lat, lng], map.getZoom(), { duration: 1.5});
  }, [lat, lng, map]);
  return null;
}

export default function MapPage() {
  const { establecimientos, selectedId } = useEstablecimientoStore();
  const [isAlertsOpen, setIsAlertsOpen] = useState(false);

  const activeEstablecimiento = establecimientos.find(e => e.id === selectedId);

  const center: [number, number] = activeEstablecimiento?.latitud != null && activeEstablecimiento?.longitud != null
    ? [activeEstablecimiento.latitud, activeEstablecimiento.longitud]
    : CENTRO_CAMPO;

  const popupText = activeEstablecimiento ? activeEstablecimiento.nombre : 'Punto de referencia del campo (placeholder)';

  return (
    <div style={{ height: '100%', width: '100%', margin: 0, padding: 0, position: 'relative', overflow: 'hidden' }}>
      <MapContainer center={center} zoom={15} zoomControl={false} style={{ height: '100%', width: '100%', zIndex: 1 }}>
        <ZoomControl position="bottomright" />
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <MapUpdater center={center} />
        <Marker position={center}>
          <Popup>{popupText}</Popup>
        </Marker>
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

      {/* Sidebar de Alertas */}
      {isAlertsOpen && (
        <AlertSidebar onClose={() => setIsAlertsOpen(false)} />
      )}
    </div>
  );
}
