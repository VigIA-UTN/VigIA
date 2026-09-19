import { useState } from 'react';
import { dayStart } from './heatmapFilters';

const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
const DOWS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

interface Props {
    from: number;
    to: number;
    onChange: (from: number, to: number) => void;
}

/** Two-month range picker: first click sets the start, second the end (order-insensitive). */
export default function DateRangeCalendar({ from, to, onChange }: Props) {
    const [view, setView] = useState(() => new Date(from));
    const [pendingStart, setPendingStart] = useState<number | null>(null);
    const today = dayStart(new Date());

    const pick = (t: number) => {
        if (pendingStart == null) {
            setPendingStart(t);
            onChange(t, t);
        } else {
            onChange(Math.min(pendingStart, t), Math.max(pendingStart, t));
            setPendingStart(null);
        }
    };

    const shift = (n: number) => setView(v => new Date(v.getFullYear(), v.getMonth() + n, 1));

    const month = (offset: number) => {
        const first = new Date(view.getFullYear(), view.getMonth() + offset, 1);
        const y = first.getFullYear(), m = first.getMonth();
        const lead = (first.getDay() + 6) % 7;
        const days = new Date(y, m + 1, 0).getDate();
        return { y, m, lead, days };
    };
    const months = [month(0), month(1)];

    return (
        <div>
            <div className="hm-cal-head">
                <button type="button" className="hm-icon-btn" aria-label="Mes anterior" onClick={() => shift(-1)}>‹</button>
                <div className="hm-cal-titles">
                    {months.map(({ y, m }) => <div key={`${y}-${m}`}>{MONTHS[m]} {y}</div>)}
                </div>
                <button type="button" className="hm-icon-btn" aria-label="Mes siguiente" onClick={() => shift(1)}>›</button>
            </div>
            <div className="hm-cal-months">
                {months.map(({ y, m, lead, days }) => (
                    <div key={`${y}-${m}`}>
                        <div className="hm-cal-grid">
                            {DOWS.map((d, i) => <div key={i} className="hm-cal-dow">{d}</div>)}
                        </div>
                        <div className="hm-cal-grid">
                            {Array.from({ length: lead }, (_, i) => <span key={`b${i}`} />)}
                            {Array.from({ length: days }, (_, i) => {
                                const t = dayStart(new Date(y, m, i + 1));
                                const end = t === from || t === to;
                                const inRange = t > from && t < to;
                                const cls = ['hm-day', end && 'hm-day-end', inRange && 'hm-day-in', !end && t === today && 'hm-day-today']
                                    .filter(Boolean).join(' ');
                                return <button key={i} type="button" className={cls} onClick={() => pick(t)}>{i + 1}</button>;
                            })}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
