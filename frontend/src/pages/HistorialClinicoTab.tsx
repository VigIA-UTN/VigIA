import { useEffect, useMemo, useRef, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { getHistorialClinico, type HistorialEvento, type HistorialTipo } from '../api/historialClinicoApi';
import DateRangeCalendar from '../components/heatmap/DateRangeCalendar';
import { dayStart, lastDaysRange } from '../components/heatmap/heatmapFilters';
import '../components/heatmap/heatmap.css';
import './HistorialClinicoTab.css';

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const TIPOS: [HistorialTipo, string][] = [['alerta', 'Alertas'], ['diagnostico', 'Diagnósticos'], ['intervencion', 'Intervenciones']];
const PRESETS: [string, string][] = [['30', '30 días'], ['90', '90 días'], ['365', '12 meses'], ['todo', 'Todo']];
const ESTADO = { SANO: ['SANO', '#00BA4A'], EN_OBSERVACION: ['EN OBSERVACIÓN', '#FFC700'], ENFERMO: ['ENFERMO', '#D50000'] } as const;
const ORIGEN = { AUTOMATICO: ['Automático', 'bi-cpu'], MANUAL: ['Manual', 'bi-person-fill'], CARGA_MASIVA: ['Carga masiva', 'bi-upload'] } as const;
const INTV_ICON: Record<string, string> = { TRATAMIENTO: 'bi-capsule', VACUNACION: 'bi-shield-plus', REVISION_VETERINARIA: 'bi-search-heart', INSEMINACION: 'bi-eyedropper', OTRO: 'bi-wrench' };

const pad = (n: number) => String(n).padStart(2, '0');
const fmtDia = (t: number) => { const d = new Date(t); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`; };
const fmtFecha = (iso: string) => { const [d, t] = iso.split('T'); const [y, m, dd] = d.split('-'); return `${dd}/${m}/${y} ${t} h`; };

function TipoIcon({ ev, size = 40 }: { ev: HistorialEvento; size?: number }) {
    const cls = ev.tipo === 'alerta' ? `hc-node hc-node-${ev.sev === 'CRITICO' ? 'critico' : 'borde'}`
        : ev.tipo === 'diagnostico' ? 'hc-node hc-node-diag' : 'hc-node hc-node-intv';
    const icon = ev.tipo === 'alerta' ? 'bi-bell-fill' : ev.tipo === 'diagnostico' ? 'bi-clipboard2-pulse-fill' : (INTV_ICON[ev.sub ?? 'OTRO'] ?? INTV_ICON.OTRO);
    return <div className={cls} style={{ width: size, height: size }}><i className={`bi ${icon}`} /></div>;
}

export default function HistorialClinicoTab({ animalId }: { animalId: number }) {
    const hoy = dayStart(new Date());
    const [eventos, setEventos] = useState<HistorialEvento[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    const [tipos, setTipos] = useState<Record<HistorialTipo, boolean>>({ alerta: true, diagnostico: true, intervencion: true });
    const [preset, setPreset] = useState('todo');
    const [desde, setDesde] = useState<number | null>(null); // null = sin límite inferior
    const [hasta, setHasta] = useState(hoy);
    const [calOpen, setCalOpen] = useState(false);
    const [calKey, setCalKey] = useState(0); // recentra el calendario tras un preset
    const calRef = useRef<HTMLDivElement>(null);
    const [open, setOpen] = useState<Record<string, boolean>>({});

    useEffect(() => {
        let cancelled = false;
        getHistorialClinico(animalId)
            .then(data => {
                if (cancelled) return;
                setEventos(data);
                if (data.length) setOpen({ [data[0].id]: true });
            })
            .catch(() => { if (!cancelled) setError(true); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [animalId]);

    useEffect(() => {
        if (!calOpen) return;
        const close = (e: MouseEvent) => { if (!calRef.current?.contains(e.target as Node)) setCalOpen(false); };
        document.addEventListener('mousedown', close);
        return () => document.removeEventListener('mousedown', close);
    }, [calOpen]);

    const selectPreset = (p: string) => {
        setPreset(p);
        setHasta(hoy);
        setCalKey(k => k + 1);
        setDesde(p === 'todo' ? null : lastDaysRange(Number(p)).from);
    };

    const { porFecha, visibles, grupos } = useMemo(() => {
        const porFecha = eventos.filter(e => {
            const d = dayStart(new Date(e.fecha));
            return (desde == null || d >= desde) && d <= hasta;
        });
        const visibles = porFecha.filter(e => tipos[e.tipo]);
        const map = new Map<string, HistorialEvento[]>();
        visibles.forEach(e => { const k = e.fecha.slice(0, 7); map.set(k, [...(map.get(k) ?? []), e]); });
        const grupos = [...map.entries()].map(([k, items]) => {
            const [y, m] = k.split('-');
            const n = MESES[Number(m) - 1];
            return { key: k, label: `${n[0].toUpperCase()}${n.slice(1)} ${y}`, items };
        });
        return { porFecha, visibles, grupos };
    }, [eventos, tipos, desde, hasta]);

    const allOpen = visibles.length > 0 && visibles.every(e => open[e.id]);
    const hayFiltros = !Object.values(tipos).every(Boolean) || preset !== 'todo' || hasta !== hoy;

    const limpiar = () => { setTipos({ alerta: true, diagnostico: true, intervencion: true }); selectPreset('todo'); };

    if (loading) return <div className="text-center py-5"><Spinner animation="border" style={{ color: '#1E3A5F' }} /></div>;
    if (error) return <p className="text-muted text-center py-5">No se pudo cargar el historial clínico.</p>;

    return (
        <div className="hc-root">
            <div className="hc-card hc-filters">
                <div className="hc-filters-row">
                    <div className="hc-field">
                        <div className="hc-label">Tipo de evento</div>
                        <div className="hc-chips">
                            {TIPOS.map(([k, l]) => (
                                <button key={k} type="button" aria-pressed={tipos[k]} className={`hc-chip${tipos[k] ? ' on' : ''}`}
                                    onClick={() => setTipos(t => ({ ...t, [k]: !t[k] }))}>
                                    <span className={`hc-chip-icon hc-chip-${k}`}>
                                        <i className={`bi ${k === 'alerta' ? 'bi-bell-fill' : k === 'diagnostico' ? 'bi-clipboard2-pulse-fill' : 'bi-capsule'}`} />
                                    </span>
                                    <span>{l}</span>
                                    <span className="hc-chip-count">{porFecha.filter(e => e.tipo === k).length}</span>
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="hc-field">
                        <div className="hc-label">Período</div>
                        <div className="hc-period">
                            <div className="hc-presets">
                                {PRESETS.map(([k, l]) => (
                                    <button key={k} type="button" className={preset === k ? 'on' : ''} onClick={() => selectPreset(k)}>{l}</button>
                                ))}
                            </div>
                            <div className="hc-dates" ref={calRef}>
                                <button type="button" className="hc-date-btn" aria-expanded={calOpen} onClick={() => setCalOpen(o => !o)}>
                                    <i className="bi bi-calendar3" />
                                    {desde == null ? `Todo → ${fmtDia(hasta)}` : `${fmtDia(desde)} → ${fmtDia(hasta)}`}
                                </button>
                                {calOpen && (
                                    <div className="hm-panel hc-cal-pop">
                                        <DateRangeCalendar key={calKey} from={desde ?? hasta} to={hasta}
                                            onChange={(f, t) => { setDesde(f); setHasta(t); setPreset('custom'); if (f !== t) setCalOpen(false); }} />
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
                <div className="hc-filters-foot">
                    <div>Mostrando {visibles.length} de {eventos.length} eventos</div>
                    <div className="hc-links">
                        {hayFiltros && <button type="button" className="underline" onClick={limpiar}>Limpiar filtros</button>}
                        <button type="button" disabled={!visibles.length}
                            onClick={() => setOpen(allOpen ? {} : Object.fromEntries(visibles.map(e => [e.id, true])))}>
                            <i className={`bi ${allOpen ? 'bi-arrows-collapse' : 'bi-arrows-expand'}`} /> {allOpen ? 'Contraer todo' : 'Expandir todo'}
                        </button>
                    </div>
                </div>
            </div>

            {visibles.length === 0 && (
                <div className="hc-card hc-empty">
                    <i className="bi bi-calendar-x" />
                    <div className="hc-empty-title">No hay eventos para los filtros seleccionados.</div>
                    <div>Ampliá el período o activá otro tipo de evento.</div>
                </div>
            )}

            {grupos.map(g => (
                <section key={g.key} className="hc-group">
                    <div className="hc-row hc-month">
                        <div />
                        <div className="hc-month-dot"><span /></div>
                        <h3>{g.label}</h3>
                    </div>
                    {g.items.map(ev => {
                        const isOpen = !!open[ev.id];
                        const est = ev.estado ? ESTADO[ev.estado] : null;
                        const [orgLabel, orgIcon] = ORIGEN[ev.origen];
                        return (
                            <div key={ev.id} className="hc-row">
                                <div className="hc-hora">{ev.fecha.slice(11)}</div>
                                <div className="hc-rail"><TipoIcon ev={ev} /></div>
                                <div className="hc-body">
                                    <div className="hc-card hc-event">
                                        <button type="button" className="hc-event-head" aria-expanded={isOpen}
                                            onClick={() => setOpen(o => ({ ...o, [ev.id]: !o[ev.id] }))}>
                                            <div className="hc-event-main">
                                                <div className="hc-event-title">
                                                    {ev.tipo === 'alerta' && <span className={`hc-tag hc-tag-${ev.sev === 'CRITICO' ? 'critico' : 'borde'}`}>ALERTA · {ev.sev}</span>}
                                                    {ev.tipo === 'diagnostico' && <span className="hc-tag hc-tag-diag">DIAGNÓSTICO</span>}
                                                    {ev.tipo === 'intervencion' && <span className="hc-tag hc-tag-intv">INTERVENCIÓN</span>}
                                                    <span className="hc-titulo">{ev.titulo}</span>
                                                    {est && <span className="hc-estado"><span style={{ background: est[1] }} />{est[0]}</span>}
                                                </div>
                                                <div className="hc-meta">
                                                    <span><i className="bi bi-clock" />{fmtFecha(ev.fecha)}</span>
                                                    <span>
                                                        <i className={`bi ${ev.origen === 'AUTOMATICO' ? 'bi-cpu' : 'bi-person'}`} />{ev.usuario}
                                                        {ev.rol && <small>{ev.rol}</small>}
                                                    </span>
                                                    <span className="hc-origen"><i className={`bi ${orgIcon}`} />{orgLabel}</span>
                                                </div>
                                            </div>
                                            <i className={`bi ${isOpen ? 'bi-chevron-up' : 'bi-chevron-down'} hc-chevron`} />
                                        </button>
                                        {isOpen && (
                                            <div className="hc-detail">
                                                <p>{ev.resumen}</p>
                                                {!!ev.detalles?.length && (
                                                    <div className="hc-detalles">
                                                        {ev.detalles.map(([k, v]) => (
                                                            <div key={k}><div className="hc-label-sm">{k}</div><div>{v}</div></div>
                                                        ))}
                                                    </div>
                                                )}
                                                <div className="hc-fuente">
                                                    <div className="hc-fuente-title">Dato de origen</div>
                                                    <div className="hc-fuente-line"><i className={`bi ${orgIcon}`} /><strong>{orgLabel}</strong><span>{ev.fuente}</span></div>
                                                    <div className="hc-fuente-reg">
                                                        Registrado el {fmtFecha(ev.fechaRegistro)} por {ev.usuario}
                                                        {ev.fechaRegistro !== ev.fecha && ` · el evento ocurrió el ${fmtFecha(ev.fecha)}`}
                                                    </div>
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </section>
            ))}
        </div>
    );
}
