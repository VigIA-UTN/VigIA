import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import './LandingPage.css';

// SVGs elegantes e inline para rendimiento óptimo y nitidez
const Icons = {
  ShieldCheck: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  ),
  MapPin: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0" />
      <circle cx="12" cy="10" r="3" />
    </svg>
  ),
  Activity: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2" />
    </svg>
  ),
  Moon: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401" />
    </svg>
  ),
  Sprout: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 9.536V7a4 4 0 0 1 4-4h1.5a.5.5 0 0 1 .5.5V5a4 4 0 0 1-4 4 4 4 0 0 0-4 4c0 2 1 3 1 5a5 5 0 0 1-1 3" />
      <path d="M4 9a5 5 0 0 1 8 4 5 5 0 0 1-8-4" />
      <path d="M5 21h14" />
    </svg>
  ),
  HeartPulse: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5" />
      <path d="M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27" />
    </svg>
  ),
  Users: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
  BellRing: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.268 21a2 2 0 0 0 3.464 0" />
      <path d="M22 8c0-2.3-.8-4.3-2-6" />
      <path d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326" />
      <path d="M4 2C2.8 3.7 2 5.7 2 8" />
    </svg>
  ),
  Radar: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M19.07 4.93A10 10 0 0 0 6.99 3.34" />
      <path d="M4 6h.01" />
      <path d="M2.29 9.62A10 10 0 1 0 21.31 8.35" />
      <path d="M16.24 7.76A6 6 0 1 0 8.23 16.67" />
      <circle cx="12" cy="12" r="2" />
      <path d="m13.41 10.59 5.66-5.66" />
    </svg>
  ),
  FileCheck: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.5 22H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.706.706l3.588 3.588A2.4 2.4 0 0 1 20 8v6" />
      <path d="M14 2v5a1 1 0 0 0 1 1h5" />
      <path d="m14 20 2 2 4-4" />
    </svg>
  ),
  Radio: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16.247 7.761a6 6 0 0 1 0 8.478" />
      <path d="M19.075 4.933a10 10 0 0 1 0 14.134" />
      <path d="M4.925 19.067a10 10 0 0 1 0-14.134" />
      <path d="M7.753 16.239a6 6 0 0 1 0-8.478" />
      <circle cx="12" cy="12" r="2" />
    </svg>
  ),
  Sun: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2" />
      <path d="M12 20v2" />
      <path d="m4.93 4.93 1.41 1.41" />
      <path d="m17.66 17.66 1.41 1.41" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="m6.34 17.66-1.41 1.41" />
      <path d="m19.07 4.93-1.41 1.41" />
    </svg>
  ),
  BrainCircuit: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z" />
      <path d="M9 13a4.5 4.5 0 0 0 3-4" />
      <path d="M6 18a4 4 0 0 1-1.967-.516" />
      <path d="M12 13h4" />
      <path d="M12 18h6a2 2 0 0 1 2 2v1" />
      <path d="M12 8h8" />
      <path d="M16 8V5a2 2 0 0 1 2-2" />
      <circle cx="16" cy="13" r=".5" />
      <circle cx="18" cy="3" r=".5" />
      <circle cx="20" cy="21" r=".5" />
      <circle cx="20" cy="8" r=".5" />
    </svg>
  ),
  Cpu: () => (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <rect x="8" y="8" width="8" height="8" rx="1" />
      <path d="M12 20v2" />
      <path d="M12 2v2" />
      <path d="M17 20v2" />
      <path d="M17 2v2" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="M7 20v2" />
      <path d="M7 2v2" />
    </svg>
  ),
  Check: () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  ),
  Mail: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect width="20" height="16" x="2" y="4" rx="2" />
      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
    </svg>
  ),
  ArrowUp: () => (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <line x1="12" y1="19" x2="12" y2="5" />
      <polyline points="5 12 12 5 19 12" />
    </svg>
  ),
  Menu: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="4" y1="6" x2="20" y2="6" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="18" x2="20" y2="18" />
    </svg>
  ),
  X: () => (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  )
};

export default function LandingPage() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const rol = useAuthStore((s) => s.rol);

  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [showScrollTop, setShowScrollTop] = useState(false);

  // Form State
  const [formData, setFormData] = useState({
    nombre: '',
    establecimiento: '',
    email: '',
    cabezas: '',
    plan: 'Intermedio',
    mensaje: ''
  });
  const [formSubmitted, setFormSubmitted] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Scroll listener for sticky navbar & back to top
  useEffect(() => {
    const handleScroll = () => {
      const offset = window.scrollY;
      setScrolled(offset > 40);
      setShowScrollTop(offset > 400);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToSection = (id: string) => {
    setMobileMenuOpen(false);
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleSelectPlan = (planName: string) => {
    setFormData((prev) => ({
      ...prev,
      plan: planName,
      mensaje: prev.mensaje || `Hola, me interesa conocer más sobre el Plan ${planName} para mi establecimiento.`
    }));
    scrollToSection('contacto');
  };

  const handleSubmitContact = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    // Simulación de envío exitoso
    setTimeout(() => {
      setIsSubmitting(false);
      setFormSubmitted(true);
    }, 900);
  };

  const teamMembers = [
    { name: 'Santiago Mendez', role: 'CEO', img: '/team/santiago-mendez.jpg' },
    { name: 'Iñaki Joaquín Lorences', role: 'Técnico de Campo', img: '/team/inaki-lorences.jpg' },
    { name: 'Julián Cardoso Landin', role: 'Técnico de Campo', img: '/team/julian-cardoso.jpg' },
    { name: 'Matías Gabriel Jastrebow', role: 'Asesor Contable y Legal', img: '/team/matias-jastrebow.jpg' },
    { name: 'Tomás Agustín Fabrizio', role: 'Asesor Contable y Legal', img: '/team/tomas-fabrizio.png' }
  ];

  return (
    <div className="landing-container">
      {/* ── HEADER / NAVBAR ── */}
      <header className={`landing-header ${scrolled ? 'scrolled' : ''}`}>
        <div className="landing-nav-inner">
          <a href="#inicio" className="landing-brand" onClick={(e) => { e.preventDefault(); scrollToSection('inicio'); }}>
            <img src="/logo.png" alt="VigIA" className="landing-brand-text" />
          </a>

          <nav className="landing-nav-links" aria-label="Navegación principal">
            <a href="#problema" className="landing-nav-link" onClick={(e) => { e.preventDefault(); scrollToSection('problema'); }}>
              El problema
            </a>
            <a href="#solucion" className="landing-nav-link" onClick={(e) => { e.preventDefault(); scrollToSection('solucion'); }}>
              Solución
            </a>
            <a href="#tecnologia" className="landing-nav-link" onClick={(e) => { e.preventDefault(); scrollToSection('tecnologia'); }}>
              Tecnología
            </a>
            <a href="#planes" className="landing-nav-link" onClick={(e) => { e.preventDefault(); scrollToSection('planes'); }}>
              Planes
            </a>
            <a href="#quienes-somos" className="landing-nav-link" onClick={(e) => { e.preventDefault(); scrollToSection('quienes-somos'); }}>
              Quiénes somos
            </a>
          </nav>

          <div className="landing-nav-actions">
            {isAuthenticated ? (
              <button
                type="button"
                className="landing-btn-primary"
                onClick={() => navigate(rol === 'ADMINISTRADOR' ? '/establecimientos' : '/dashboard')}
              >
                Ir al Dashboard
              </button>
            ) : (
              <>
                <Link to="/login" className="landing-btn-login">
                  Iniciar sesión
                </Link>
                <button
                  type="button"
                  className="landing-btn-primary"
                  onClick={() => scrollToSection('contacto')}
                >
                  Solicitar demo
                </button>
              </>
            )}

            <button
              type="button"
              className="landing-mobile-toggle"
              aria-label="Abrir menú"
              onClick={() => setMobileMenuOpen(true)}
            >
              <Icons.Menu />
            </button>
          </div>
        </div>
      </header>

      {/* ── MOBILE DRAWER ── */}
      <div
        className={`landing-drawer-overlay ${mobileMenuOpen ? 'open' : ''}`}
        onClick={() => setMobileMenuOpen(false)}
      />
      <div className={`landing-drawer ${mobileMenuOpen ? 'open' : ''}`}>
        <div className="landing-drawer-header">
          <div className="landing-brand">
            <img src="/vigia-logo.png" alt="VigIA" className="landing-brand-icon" />
            <img src="/Logo 2R.png" alt="VigIA" className="landing-brand-text" />
          </div>
          <button
            type="button"
            className="landing-drawer-close"
            onClick={() => setMobileMenuOpen(false)}
            aria-label="Cerrar menú"
          >
            <Icons.X />
          </button>
        </div>
        <div className="landing-drawer-nav">
          <a href="#inicio" onClick={(e) => { e.preventDefault(); scrollToSection('inicio'); }}>Inicio</a>
          <a href="#problema" onClick={(e) => { e.preventDefault(); scrollToSection('problema'); }}>El problema</a>
          <a href="#solucion" onClick={(e) => { e.preventDefault(); scrollToSection('solucion'); }}>Solución</a>
          <a href="#tecnologia" onClick={(e) => { e.preventDefault(); scrollToSection('tecnologia'); }}>Tecnología</a>
          <a href="#planes" onClick={(e) => { e.preventDefault(); scrollToSection('planes'); }}>Planes</a>
          <a href="#quienes-somos" onClick={(e) => { e.preventDefault(); scrollToSection('quienes-somos'); }}>Quiénes somos</a>
          <a href="#contacto" onClick={(e) => { e.preventDefault(); scrollToSection('contacto'); }}>Contacto</a>
        </div>
        <div className="landing-drawer-footer">
          {isAuthenticated ? (
            <button
              type="button"
              className="landing-btn-primary w-100"
              onClick={() => navigate('/dashboard')}
            >
              Ir a mi panel
            </button>
          ) : (
            <>
              <Link to="/login" className="landing-btn-secondary w-100" style={{ textAlign: 'center' }}>
                Iniciar sesión
              </Link>
              <Link to="/register" className="landing-btn-primary w-100" style={{ textAlign: 'center' }}>
                Registrarse
              </Link>
            </>
          )}
        </div>
      </div>

      <main>
        {/* ── HERO SECTION ── */}
        <section id="inicio" className="landing-hero">
          <div className="landing-hero-bg">
            <img src="/campo-vacas-dia.png" alt="Ganado pastando en el campo durante el día" />
          </div>
          <div className="landing-hero-overlay" />

          <div className="landing-hero-content">
            <span className="landing-badge">
              Monitoreo ganadero con Inteligencia Artificial
            </span>

            <h1 className="landing-hero-title">
              <span>Lo que no se</span>
              <img src="/texto-vigila.png" alt="vigila" />
              <span><strong>se pierde.</strong></span>
            </h1>

            <p className="landing-hero-subtitle">
              Protegé tu rodeo las 24 horas. VigIA combina sensores biométricos, GPS e IA para alertarte ante robos,
              detectar celo y problemas de salud a tiempo, y cumplir con el SENASA sin tocar el teclado.
            </p>

            <div className="landing-hero-buttons">
              <button
                type="button"
                className="landing-btn-accent"
                onClick={() => scrollToSection('contacto')}
              >
                Solicitar demo
              </button>
              <button
                type="button"
                className="landing-btn-secondary"
                onClick={() => scrollToSection('solucion')}
              >
                Conocer la solución
              </button>
              {!isAuthenticated && (
                <Link to="/login" className="landing-btn-secondary" style={{ background: 'rgba(255,255,255,0.15)', color: '#fff', borderColor: 'rgba(255,255,255,0.3)' }}>
                  Ingresar al sistema
                </Link>
              )}
            </div>

            <div className="landing-hero-features">
              <div className="landing-hero-feature-item">
                <span className="landing-hero-feature-icon">
                  <Icons.ShieldCheck />
                </span>
                <span>Blindaje patrimonial 24/7</span>
              </div>
              <div className="landing-hero-feature-item">
                <span className="landing-hero-feature-icon">
                  <Icons.MapPin />
                </span>
                <span>Perímetros virtuales en tiempo real</span>
              </div>
              <div className="landing-hero-feature-item">
                <span className="landing-hero-feature-icon">
                  <Icons.Activity />
                </span>
                <span>Detección temprana de celo y salud</span>
              </div>
            </div>
          </div>
        </section>

        {/* ── STATS SECTION ── */}
        <section className="landing-stats-section">
          <div className="landing-stats-grid">
            <div className="landing-stat-card">
              <div className="landing-stat-number">24/7</div>
              <div className="landing-stat-label">Monitoreo en tiempo real</div>
            </div>
            <div className="landing-stat-card">
              <div className="landing-stat-number">10–15 km</div>
              <div className="landing-stat-label">Alcance por gateway LoRaWAN</div>
            </div>
            <div className="landing-stat-card">
              <div className="landing-stat-number">21 días</div>
              <div className="landing-stat-label">Que cuesta un celo no detectado</div>
            </div>
            <div className="landing-stat-card">
              <div className="landing-stat-number">IP67</div>
              <div className="landing-stat-label">Hardware solar resistente al campo</div>
            </div>
          </div>
        </section>

        {/* ── EL PROBLEMA ── */}
        <section id="problema" className="landing-section">
          <div className="landing-section-inner">
            <div className="landing-section-header">
              <span className="landing-section-tag">El problema</span>
              <h2 className="landing-section-title">La ganadería argentina enfrenta cuatro crisis simultáneas</h2>
              <p className="landing-section-desc">
                A esto se suma la presión legal del SENASA (Resoluciones 71/2024 y 40/2026), que obliga a la identificación
                electrónica individual del ganado.
              </p>
            </div>

            <div className="landing-problem-banner">
              <img src="/vacas-rodeo.png" alt="Rodeo de ganado en campo abierto al atardecer" />
              <div className="landing-problem-banner-overlay">
                <p className="landing-problem-banner-text">
                  Miles de hectáreas extensivas, expuestas día y noche, sin control humano permanente.
                </p>
              </div>
            </div>

            <div className="landing-problem-grid">
              <div className="landing-card">
                <div className="landing-card-icon">
                  <Icons.Moon />
                </div>
                <h3 className="landing-card-title">Abigeato nocturno</h3>
                <p className="landing-card-text">
                  El delito rural destruye capital de noche, cuando el campo no tiene control humano y el robo se descubre al día siguiente.
                </p>
              </div>

              <div className="landing-card">
                <div className="landing-card-icon">
                  <Icons.Sprout />
                </div>
                <h3 className="landing-card-title">Celo no detectado</h3>
                <p className="landing-card-text">
                  Cada evento reproductivo no identificado a tiempo significa 21 días vacíos de producción y rentabilidad perdida.
                </p>
              </div>

              <div className="landing-card">
                <div className="landing-card-icon">
                  <Icons.HeartPulse />
                </div>
                <h3 className="landing-card-title">Salud detectada tarde</h3>
                <p className="landing-card-text">
                  Los problemas sanitarios del rodeo se identifican demasiado tarde, aumentando la mortalidad y los costos ocultos.
                </p>
              </div>

              <div className="landing-card">
                <div className="landing-card-icon">
                  <Icons.Users />
                </div>
                <h3 className="landing-card-title">Escasez de mano de obra</h3>
                <p className="landing-card-text">
                  El éxodo rural deja miles de hectáreas extensivas sin control humano activo y sin capacidad de supervisión.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* ── LA SOLUCIÓN ── */}
        <section id="solucion" className="landing-section alt-bg">
          <div className="landing-section-inner">
            <div className="landing-solution-split">
              <div>
                <span className="landing-section-tag">La solución</span>
                <h2 className="landing-section-title">Un ecosistema integral de hardware robusto e IA</h2>
                <p className="landing-section-desc">
                  Dispositivos con sensores biométricos y GPS autónomos, carcasa IP67 y alimentación solar,
                  conectados a una plataforma analítica que transforma el comportamiento del rodeo en decisiones rentables.
                </p>

                <div className="landing-solution-features">
                  <div className="landing-solution-card">
                    <div className="landing-card-icon" style={{ background: 'rgba(148, 193, 30, 0.16)', color: '#1E3A5F' }}>
                      <Icons.ShieldCheck />
                    </div>
                    <h3 className="landing-card-title">Blindaje patrimonial</h3>
                    <p className="landing-card-text">
                      Perímetros virtuales con alertas inmediatas ante robos. Un solo animal recuperado paga el servicio.
                    </p>
                  </div>

                  <div className="landing-solution-card">
                    <div className="landing-card-icon" style={{ background: 'rgba(148, 193, 30, 0.16)', color: '#1E3A5F' }}>
                      <Icons.BellRing />
                    </div>
                    <h3 className="landing-card-title">Tu empleado que no duerme</h3>
                    <p className="landing-card-text">
                      Monitoreo del comportamiento del rodeo las 24 horas en campo abierto, sin depender de personal físico.
                    </p>
                  </div>

                  <div className="landing-solution-card">
                    <div className="landing-card-icon" style={{ background: 'rgba(148, 193, 30, 0.16)', color: '#1E3A5F' }}>
                      <Icons.Radar />
                    </div>
                    <h3 className="landing-card-title">Inteligencia productiva</h3>
                    <p className="landing-card-text">
                      Detección automática de celo y problemas de salud mediante IA, para decidir con datos en tiempo real.
                    </p>
                  </div>

                  <div className="landing-solution-card">
                    <div className="landing-card-icon" style={{ background: 'rgba(148, 193, 30, 0.16)', color: '#1E3A5F' }}>
                      <Icons.FileCheck />
                    </div>
                    <h3 className="landing-card-title">SENASA sin tocar el teclado</h3>
                    <p className="landing-card-text">
                      Reporta de forma nativa y automática altas, bajas y movimientos a la base oficial del SENASA.
                    </p>
                  </div>
                </div>
              </div>

              <div className="landing-solution-device">
                <img
                  src="/device-collar.png"
                  alt="Dispositivo VigIA con sensores, GPS, carcasa IP67 y panel solar"
                />
                <div className="landing-device-badges">
                  <span className="landing-device-badge">Carcasa IP67</span>
                  <span className="landing-device-badge">Panel Solar Integrado</span>
                  <span className="landing-device-badge">GPS + Acelerómetro</span>
                  <span className="landing-device-badge">LoRaWAN 15km</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── TECNOLOGÍA ── */}
        <section id="tecnologia" className="landing-section">
          <div className="landing-section-inner">
            <div className="landing-tech-split">
              <div className="landing-tech-visual">
                <img
                  src="/dashboard-mockup.png"
                  alt="Plataforma analítica de VigIA con mapa, salud y detección de celo"
                />
              </div>

              <div>
                <span className="landing-section-tag">Tecnología</span>
                <h2 className="landing-section-title">Tecnología madura, lista para el campo</h2>
                <p className="landing-section-desc">
                  VigIA se apoya en tecnologías probadas que permiten monitorear el rodeo en tiempo real con bajo
                  costo operativo y amplia cobertura rural.
                </p>

                <div className="landing-tech-list">
                  <div className="landing-tech-item">
                    <div className="landing-tech-icon">
                      <Icons.Radio />
                    </div>
                    <div className="landing-tech-info">
                      <h4>Redes LoRaWAN propias</h4>
                      <p>Gateways de 10–15 km de alcance y conectividad a costo cero (red TTN) para autonomía total en campo abierto.</p>
                    </div>
                  </div>

                  <div className="landing-tech-item">
                    <div className="landing-tech-icon">
                      <Icons.Sun />
                    </div>
                    <div className="landing-tech-info">
                      <h4>Alimentación solar</h4>
                      <p>Carcasa IP67 y paneles solares para operar de forma continua en entornos rurales adversos.</p>
                    </div>
                  </div>

                  <div className="landing-tech-item">
                    <div className="landing-tech-icon">
                      <Icons.BrainCircuit />
                    </div>
                    <div className="landing-tech-info">
                      <h4>Inteligencia Artificial</h4>
                      <p>Procesamiento del comportamiento del rodeo para detectar celo, anomalías de salud y eventos críticos.</p>
                    </div>
                  </div>

                  <div className="landing-tech-item">
                    <div className="landing-tech-icon">
                      <Icons.Cpu />
                    </div>
                    <div className="landing-tech-info">
                      <h4>IoT y sensores biométricos</h4>
                      <p>Captura, transmisión y análisis de datos en tiempo real desde cada cabeza de ganado.</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── PLANES ── */}
        <section id="planes" className="landing-section alt-bg">
          <div className="landing-section-inner">
            <div className="landing-section-header centered">
              <span className="landing-section-tag">Planes</span>
              <h2 className="landing-section-title">Suscripción que crece con tu rodeo</h2>
              <p className="landing-section-desc">
                Modelo SaaS por cabeza de ganado, diseñado para adaptarse a las necesidades de cada establecimiento.
              </p>
            </div>

            <div className="landing-plans-grid">
              {/* Plan Básico */}
              <div className="landing-plan-card">
                <h3 className="landing-plan-name">Básico</h3>
                <p className="landing-plan-desc">Geolocalización y cumplimiento</p>

                <ul className="landing-plan-features">
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Geolocalización del rodeo 24/7</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Perímetros virtuales y alertas de robo</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Integración automática con el SENASA</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Soporte por canales digitales</span>
                  </li>
                </ul>

                <button
                  type="button"
                  className="landing-btn-secondary"
                  onClick={() => handleSelectPlan('Básico')}
                >
                  Solicitar cotización
                </button>
              </div>

              {/* Plan Intermedio (Featured) */}
              <div className="landing-plan-card featured">
                <span className="landing-plan-badge">Más elegido</span>
                <h3 className="landing-plan-name">Intermedio</h3>
                <p className="landing-plan-desc">Suma monitoreo de salud</p>

                <ul className="landing-plan-features">
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Todo lo del plan Básico</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Monitoreo del estado de salud por cabeza</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Alertas tempranas de anomalías sanitarias</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Reportes de bienestar animal</span>
                  </li>
                </ul>

                <button
                  type="button"
                  className="landing-btn-primary"
                  onClick={() => handleSelectPlan('Intermedio')}
                >
                  Solicitar cotización
                </button>
              </div>

              {/* Plan Superior */}
              <div className="landing-plan-card">
                <h3 className="landing-plan-name">Superior</h3>
                <p className="landing-plan-desc">Eficiencia reproductiva completa</p>

                <ul className="landing-plan-features">
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Todo lo del plan Intermedio</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Detección temprana de celo con IA</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Optimización del intervalo parto-concepción</span>
                  </li>
                  <li className="landing-plan-feature">
                    <span className="landing-plan-check"><Icons.Check /></span>
                    <span>Analítica productiva avanzada</span>
                  </li>
                </ul>

                <button
                  type="button"
                  className="landing-btn-secondary"
                  onClick={() => handleSelectPlan('Superior')}
                >
                  Solicitar cotización
                </button>
              </div>
            </div>

            <div className="landing-plans-financing">
              💡 <strong>Financiación disponible:</strong> El hardware inicial es 100% financiable mediante líneas de crédito agroindustrial locales.
            </div>
          </div>
        </section>

        {/* ── QUIÉNES SOMOS ── */}
        <section id="quienes-somos" className="landing-section">
          <div className="landing-section-inner">
            <div className="landing-section-header centered">
              <span className="landing-section-tag">Quiénes somos</span>
              <h2 className="landing-section-title">El equipo detrás de VigIA</h2>
              <p className="landing-section-desc">
                Somos un equipo de desarrolladores, técnicos de campo y especialistas del agro que une tecnología
                y conocimiento rural. Promovemos una marca que no promete innovación: promete resultados.
              </p>
            </div>

            <div className="landing-team-banner">
              <img src="/campo-vacas-dia.png" alt="Ganado pastando en la llanura pampeana argentina" />
              <div className="landing-team-banner-overlay" />
            </div>

            <div className="landing-team-grid">
              {teamMembers.map((member) => (
                <div key={member.name} className="landing-team-card">
                  <div className="landing-team-avatar-wrapper">
                    <img src={member.img} alt={`Foto de perfil de ${member.name}`} />
                  </div>
                  <h3 className="landing-team-name">{member.name}</h3>
                  <p className="landing-team-role">{member.role}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── CONTACTO ── */}
        <section id="contacto" className="landing-section alt-bg">
          <div className="landing-section-inner">
            <div className="landing-contact-split">
              <div>
                <span className="landing-section-tag">Contacto</span>
                <h2 className="landing-section-title">Recuperá lo que el campo te cuesta</h2>
                <p className="landing-section-desc">
                  Dejanos tus datos y un asesor te contactará para coordinar una demo en tu establecimiento.
                  Atendemos la zona núcleo ganadera de Buenos Aires y La Pampa.
                </p>

                <div className="landing-contact-info-box">
                  <div className="landing-contact-info-item">
                    <div className="landing-contact-icon">
                      <Icons.Mail />
                    </div>
                    <div>
                      <div className="landing-contact-info-title">Email de contacto</div>
                      <a href="mailto:contacto@vigia.com.ar" className="landing-contact-info-val" style={{ textDecoration: 'none' }}>
                        contacto@vigia.com.ar
                      </a>
                    </div>
                  </div>

                  <div className="landing-contact-info-item">
                    <div className="landing-contact-icon">
                      <Icons.MapPin />
                    </div>
                    <div>
                      <div className="landing-contact-info-title">Zona de Cobertura</div>
                      <div className="landing-contact-info-val">Buenos Aires y La Pampa</div>
                    </div>
                  </div>
                </div>

                <div style={{ marginTop: '28px', padding: '18px 20px', background: 'rgba(30, 58, 95, 0.04)', borderRadius: '14px', border: '1px solid rgba(30, 58, 95, 0.1)' }}>
                  <p style={{ margin: 0, fontSize: '0.9rem', color: '#64748B' }}>
                    ¿Ya tenés una cuenta en VigIA?{' '}
                    <Link to="/login" style={{ color: '#1E3A5F', fontWeight: 600 }}>
                      Iniciá sesión aquí
                    </Link>
                  </p>
                </div>
              </div>

              {/* Formulario de Contacto */}
              <div className="landing-contact-form-card">
                {formSubmitted ? (
                  <div className="landing-form-success">
                    <Icons.Check />
                    <div>
                      <strong>¡Mensaje recibido!</strong>
                      <p style={{ margin: '4px 0 0 0', fontSize: '0.88rem' }}>
                        Un asesor de VigIA se pondrá en contacto a la brevedad para coordinar la demo.
                      </p>
                    </div>
                  </div>
                ) : (
                  <form onSubmit={handleSubmitContact}>
                    <div className="landing-form-grid two-cols">
                      <div className="landing-form-group">
                        <label className="landing-form-label" htmlFor="landing-nombre">Nombre</label>
                        <input
                          id="landing-nombre"
                          className="landing-form-input"
                          type="text"
                          required
                          placeholder="Tu nombre"
                          value={formData.nombre}
                          onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
                        />
                      </div>
                      <div className="landing-form-group">
                        <label className="landing-form-label" htmlFor="landing-establecimiento">Establecimiento</label>
                        <input
                          id="landing-establecimiento"
                          className="landing-form-input"
                          type="text"
                          placeholder="Nombre del campo"
                          value={formData.establecimiento}
                          onChange={(e) => setFormData({ ...formData, establecimiento: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="landing-form-grid two-cols" style={{ marginTop: '16px' }}>
                      <div className="landing-form-group">
                        <label className="landing-form-label" htmlFor="landing-email">Email</label>
                        <input
                          id="landing-email"
                          className="landing-form-input"
                          type="email"
                          required
                          placeholder="tu@email.com"
                          value={formData.email}
                          onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                        />
                      </div>
                      <div className="landing-form-group">
                        <label className="landing-form-label" htmlFor="landing-cabezas">Cabezas de ganado</label>
                        <input
                          id="landing-cabezas"
                          className="landing-form-input"
                          type="number"
                          placeholder="Ej: 500"
                          value={formData.cabezas}
                          onChange={(e) => setFormData({ ...formData, cabezas: e.target.value })}
                        />
                      </div>
                    </div>

                    <div className="landing-form-group" style={{ marginTop: '16px' }}>
                      <label className="landing-form-label" htmlFor="landing-plan">Plan de interés</label>
                      <select
                        id="landing-plan"
                        className="landing-form-select"
                        value={formData.plan}
                        onChange={(e) => setFormData({ ...formData, plan: e.target.value })}
                      >
                        <option value="Básico">Plan Básico (Geolocalización + SENASA)</option>
                        <option value="Intermedio">Plan Intermedio (Monitoreo de salud)</option>
                        <option value="Superior">Plan Superior (Eficiencia reproductiva e IA)</option>
                      </select>
                    </div>

                    <div className="landing-form-group" style={{ marginTop: '16px' }}>
                      <label className="landing-form-label" htmlFor="landing-mensaje">Mensaje</label>
                      <textarea
                        id="landing-mensaje"
                        className="landing-form-textarea"
                        rows={4}
                        placeholder="Contanos sobre tu rodeo o tus necesidades de monitoreo"
                        value={formData.mensaje}
                        onChange={(e) => setFormData({ ...formData, mensaje: e.target.value })}
                      />
                    </div>

                    <button
                      type="submit"
                      disabled={isSubmitting}
                      className="landing-btn-primary"
                      style={{ width: '100%', marginTop: '24px', padding: '14px' }}
                    >
                      {isSubmitting ? 'Enviando...' : 'Solicitar demo'}
                    </button>
                  </form>
                )}
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* ── FOOTER ── */}
      <footer className="landing-footer">
        <div className="landing-footer-inner">
          <div className="landing-footer-grid">
            <div>
              <div className="landing-brand">
                <img src="/logo.png" alt="VigIA" className="landing-brand-text" />
              </div>
              <p className="landing-footer-brand-desc">
                VigIA: vigilancia constante potenciada por Inteligencia Artificial. Lo que no se Vigila, se pierde.
              </p>
            </div>

            <div>
              <h4 className="landing-footer-title">Producto</h4>
              <ul className="landing-footer-links">
                <li><a href="#solucion" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('solucion'); }}>Solución</a></li>
                <li><a href="#tecnologia" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('tecnologia'); }}>Tecnología</a></li>
                <li><a href="#planes" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('planes'); }}>Planes</a></li>
              </ul>
            </div>

            <div>
              <h4 className="landing-footer-title">Empresa</h4>
              <ul className="landing-footer-links">
                <li><a href="#quienes-somos" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('quienes-somos'); }}>Quiénes somos</a></li>
                <li><a href="#problema" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('problema'); }}>El problema</a></li>
                <li><a href="#contacto" className="landing-footer-link" onClick={(e) => { e.preventDefault(); scrollToSection('contacto'); }}>Contacto</a></li>
              </ul>
            </div>

            <div>
              <h4 className="landing-footer-title">Plataforma</h4>
              <ul className="landing-footer-links">
                <li><Link to="/login" className="landing-footer-link">Iniciar sesión</Link></li>
                <li><Link to="/register" className="landing-footer-link">Registrarse</Link></li>
                <li><a href="/privacy.html" target="_blank" rel="noreferrer" className="landing-footer-link">Política de Privacidad</a></li>
              </ul>
            </div>
          </div>

          <div className="landing-footer-bottom">
            <p>© {new Date().getFullYear()} VigIA. Todos los derechos reservados.</p>
            <p>Buenos Aires y La Pampa, Argentina</p>
          </div>
        </div>
      </footer>

      {/* ── BOTÓN FLOTANTE VOLVER ARRIBA ── */}
      <button
        type="button"
        className={`landing-scroll-top ${showScrollTop ? 'visible' : ''}`}
        onClick={() => scrollToSection('inicio')}
        aria-label="Volver arriba"
      >
        <Icons.ArrowUp />
      </button>
    </div>
  );
}
