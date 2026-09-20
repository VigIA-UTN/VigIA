import { useMemo, useState } from 'react';
import DateRangeCalendar from './DateRangeCalendar';
import HeatmapLegend from './HeatmapLegend';
import { defaultHeatFilter, lastDaysRange, thisMonthRange } from './heatmapFilters';
import type { HeatAnimal, HeatFilter, HeatRodeo, HeatScope } from './types';
import './heatmap.css';

const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const fmt = (t: number) => {
    const d = new Date(t);
    return `${String(d.getDate()).padStart(2, '0')} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}`;
};

const PRESETS = [
    { label: '7 días', range: () => lastDaysRange(7) },
    { label: '30 días', range: () => lastDaysRange(30) },
    { label: 'Este mes', range: () => thisMonthRange() },
];

const TABS: { id: HeatScope; label: string }[] = [
    { id: 'todo', label: 'Todo el rodeo' },
    { id: 'rodeo', label: 'Rodeo' },
    { id: 'animal', label: 'Animal' },
];

interface Props {
    enabled: boolean;
    onEnabledChange: (on: boolean) => void;
    filter: HeatFilter;
    onFilterChange: (f: HeatFilter) => void;
    rodeos: HeatRodeo[];
    animals: HeatAnimal[];
    loading?: boolean;
    error?: string | null;
    /** number of points currently drawn, used for the empty state */
    pointCount: number;
}

/** Filter panel for the heatmap. Presentational: state lives in the host, data fetching in useHeatPoints. */
export default function HeatmapPanel({
    enabled, onEnabledChange, filter, onFilterChange, rodeos, animals, loading, error, pointCount,
}: Props) {
    const [query, setQuery] = useState('');
    // remounts the calendar (and re-centers its view) after presets / reset, never while picking a range
    const [calKey, setCalKey] = useState(0);
    const set = (patch: Partial<HeatFilter>) => onFilterChange({ ...filter, ...patch });

    const rodeoById = useMemo(() => new Map(rodeos.map(r => [r.id, r])), [rodeos]);
    const countByRodeo = useMemo(() => {
        const m = new Map<number, number>();
        animals.forEach(a => a.rodeoId != null && m.set(a.rodeoId, (m.get(a.rodeoId) ?? 0) + 1));
        return m;
    }, [animals]);

    const selectedRodeo = filter.rodeoId != null ? rodeoById.get(filter.rodeoId) : undefined;
    const selectedAnimal = animals.find(a => a.id === filter.animalId);

    const summary = filter.scope === 'todo'
        ? `${animals.length} animales`
        : filter.scope === 'rodeo'
            ? (selectedRodeo ? `${countByRodeo.get(selectedRodeo.id) ?? 0} animales` : 'Seleccioná un rodeo')
            : (selectedAnimal ? `Caravana ${selectedAnimal.caravana}` : 'Seleccioná un animal');

    const q = query.replace('#', '').trim().toLowerCase();
    const animalRows = animals.filter(a =>
        (filter.rodeoId == null || a.rodeoId === filter.rodeoId) &&
        (!q || a.caravana.toLowerCase().includes(q) || (a.alias ?? '').toLowerCase().includes(q)));

    const setScope = (scope: HeatScope) => {
        setQuery('');
        set({ scope, animalId: scope === 'animal' ? filter.animalId : null });
    };

    const reset = () => {
        setQuery('');
        setCalKey(k => k + 1);
        onFilterChange(defaultHeatFilter(rodeos[0]?.id ?? null));
    };

    return (
        <div className="hm-panel" role="dialog" aria-label="Mapa de calor">
            <div className="hm-head">
            <div className="hm-row">
                <div className="hm-title">Mapa de Calor</div>
                <button
                    type="button" role="switch" aria-checked={enabled} aria-label="Activar HeatMap"
                    className={`hm-switch${enabled ? ' on' : ''}`} onClick={() => onEnabledChange(!enabled)}
                >
                    <span className="hm-knob" />
                </button>
            </div>
            <div className="hm-sep" />
            </div>

            <div className={`hm-body${enabled ? '' : ' off'}`}>
                <section>
                    <div className="hm-label-row">
                        <div className="hm-label">ALCANCE</div>
                        <div className="hm-hint">{summary}</div>
                    </div>
                    <div className="hm-tabs" role="tablist">
                        {TABS.map(t => (
                            <button key={t.id} type="button" role="tab" aria-selected={filter.scope === t.id}
                                className={filter.scope === t.id ? 'active' : ''} onClick={() => setScope(t.id)}>
                                {t.label}
                            </button>
                        ))}
                    </div>
                </section>

                {filter.scope === 'animal' && (
                    <>
                        <input className="hm-input" placeholder="Buscar por caravana #" value={query}
                            onChange={e => setQuery(e.target.value)} />
                        {selectedRodeo && (
                            <div className="hm-row" style={{ justifyContent: 'flex-start' }}>
                                <button type="button" className="hm-chip" onClick={() => set({ rodeoId: null, animalId: null })}>
                                    {selectedRodeo.nombre} <span aria-hidden>✕</span>
                                </button>
                                <span className="hm-hint">filtrando por rodeo</span>
                            </div>
                        )}
                    </>
                )}

                <div className="hm-list">
                    {filter.scope === 'animal'
                        ? animalRows.map(a => (
                            <button key={a.id} type="button" className={`hm-item${filter.animalId === a.id ? ' sel' : ''}`}
                                onClick={() => set({ animalId: a.id })}>
                                <span className="hm-item-text">
                                    <span className="hm-item-main">{a.caravana}{a.alias ? ` · ${a.alias}` : ''}</span>
                                    <span className="hm-item-sub">{a.rodeoId != null ? rodeoById.get(a.rodeoId)?.nombre ?? '' : 'Sin rodeo'}</span>
                                </span>
                            </button>
                        ))
                        : rodeos.map(r => {
                            const selectable = filter.scope === 'rodeo';
                            return (
                                <div key={r.id} className={`hm-item${selectable && filter.rodeoId === r.id ? ' sel' : ''}`}>
                                    <button type="button" className="hm-item-btn" disabled={!selectable}
                                        onClick={() => set({ rodeoId: r.id, animalId: null })}>
                                        <span className="hm-item-text">
                                            <span className="hm-item-main">{r.nombre}</span>
                                            <span className="hm-item-sub">{countByRodeo.get(r.id) ?? 0} animales</span>
                                        </span>
                                    </button>
                                    {selectable && (
                                        <button type="button" className="hm-icon-btn" aria-label="Ver animales"
                                            onClick={() => { setQuery(''); set({ scope: 'animal', rodeoId: r.id, animalId: null }); }}>›</button>
                                    )}
                                </div>
                            );
                        })}
                    {(filter.scope === 'animal' ? animalRows.length : rodeos.length) === 0 && (
                        <div className="hm-hint" style={{ padding: '8px 4px' }}>Sin resultados.</div>
                    )}
                </div>

                <section>
                    <div className="hm-label-row">
                        <div className="hm-label">PERÍODO</div>
                        <div className="hm-hint">{fmt(filter.from)} → {fmt(filter.to)}</div>
                    </div>
                    <div className="hm-presets">
                        {PRESETS.map(p => (
                            <button key={p.label} type="button" className="hm-chip"
                                onClick={() => { setCalKey(k => k + 1); set(p.range()); }}>{p.label}</button>
                        ))}
                    </div>
                    <DateRangeCalendar key={calKey} from={filter.from} to={filter.to}
                        onChange={(from, to) => set({ from, to })} />
                </section>

                <div className="hm-row">
                    <HeatmapLegend dark />
                    <button type="button" className="hm-reset" onClick={reset}>Restablecer</button>
                </div>

                <div className="hm-status" aria-live="polite">
                    {error ? <span className="err">{error}</span>
                        : loading ? 'Cargando…'
                            : enabled && pointCount === 0 ? 'Sin datos para el filtro elegido.' : ''}
                </div>
            </div>
        </div>
    );
}
