import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { useAuthStore } from '../store/authStore';
import { useNavigate } from 'react-router-dom';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

export default function MapPage() {
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div style={{ height: '100vh', width: '100vw', margin: 0, padding: 0, position: 'relative' }}>
      <button
        onClick={handleLogout}
        style={{
          position: 'absolute',
          top: 16,
          right: 16,
          zIndex: 1000,
          background: '#1E3A5F',
          color: '#fff',
          border: 'none',
          borderRadius: 8,
          padding: '8px 18px',
          cursor: 'pointer',
          fontFamily: 'Outfit, sans-serif',
          fontWeight: 600,
        }}
      >
        Cerrar sesión
      </button>
      <MapContainer center={CENTRO_CAMPO} zoom={15} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Marker position={CENTRO_CAMPO}>
          <Popup>Punto de referencia del campo (placeholder)</Popup>
        </Marker>
      </MapContainer>
    </div>
  );
}
