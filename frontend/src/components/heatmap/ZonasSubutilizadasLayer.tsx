import { Pane, Rectangle } from 'react-leaflet';
import type { ZonaSubutilizada } from './types';

const PINK = '#EE5A9C';

/**
 * Map child: outlines the under-used zones, one rectangle per grid cell.
 * Lives in its own pane just under the overlay pane (400) so the heat layer and perimeters draw on top.
 */
export default function ZonasSubutilizadasLayer({ zonas }: { zonas: ZonaSubutilizada[] }) {
    return (
        <Pane name="zonas-subutilizadas" style={{ zIndex: 399 }}>
            {zonas.flatMap(z => z.cells.map((bounds, i) => (
                <Rectangle
                    key={`${z.id}-${i}`}
                    bounds={bounds}
                    interactive={false}
                    pathOptions={{ color: PINK, weight: 1, dashArray: '4 4', fillColor: PINK, fillOpacity: 0.3 }}
                />
            )))}
        </Pane>
    );
}
