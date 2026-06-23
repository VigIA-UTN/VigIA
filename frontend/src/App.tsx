import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import "leaflet/dist/leaflet.css";

// Coordenada de referencia provisoria; reemplazar por la ubicación real del campo.
const CENTRO_CAMPO: [number, number] = [-34.6037, -58.3816];

function App() {
  return (
    <div style={{ height: "100vh", width: "100%" }}>
      <MapContainer center={CENTRO_CAMPO} zoom={15} style={{ height: "100%", width: "100%" }}>
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

export default App;
