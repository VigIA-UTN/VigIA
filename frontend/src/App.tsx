import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import MapPage from './pages/MapPage';
import ProtectedRoute from './components/ProtectedRoute';
import EstablecimientosPage from './pages/EstablecimientosPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MapPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/establecimientos"
          element={
            <ProtectedRoute>
              <EstablecimientosPage />
            </ProtectedRoute>
          }
        />
        {/* Cualquier ruta no encontrada redirige al root */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
