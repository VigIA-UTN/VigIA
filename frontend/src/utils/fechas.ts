/** 'YYYY-MM-DD' (o 'YYYY-MM') -> 'DD/MM/YYYY' (o 'MM/YYYY'); '—' si no hay valor. */
export function formatFechaLocal(iso: string | null | undefined) {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    if (d) return `${d.substring(0, 2)}/${m}/${y}`;
    return `${m}/${y}`;
}

export function formatDateTime(dt: string | null | undefined) {
    if (!dt) return '—';
    return new Date(dt).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
