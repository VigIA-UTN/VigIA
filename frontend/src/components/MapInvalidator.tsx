import { useEffect } from 'react';
import { useMap } from 'react-leaflet';

// keep the map in sync with its container (tab becoming visible, expand/collapse, window resize).
// Debounced: invalidateSize redraws tiles and the heat canvas, so it must run once after the resize settles, not per frame.
// smooth: sync on every frame instead (ResizeObserver fires once per frame), so the centre stays put while the container
// animates its size. Only for maps without heavy layers; moveend (tile reload) is still debounced by Leaflet.
export default function MapInvalidator({ smooth = false }: { smooth?: boolean }) {
    const map = useMap();
    useEffect(() => {
        let timer: number | undefined;
        const observer = new ResizeObserver(() => {
            if (smooth) {
                map.invalidateSize({ animate: false, debounceMoveend: true });
                return;
            }
            window.clearTimeout(timer);
            timer = window.setTimeout(() => map.invalidateSize({ animate: false }), 120);
        });
        observer.observe(map.getContainer());
        return () => { window.clearTimeout(timer); observer.disconnect(); };
    }, [map, smooth]);
    return null;
}
