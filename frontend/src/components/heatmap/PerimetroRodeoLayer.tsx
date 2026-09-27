import { Polygon } from 'react-leaflet';
import type { PerimetroResponse } from '../../api/perimetrosApi';
import './heatmap.css';

const PINK = '#EE5A9C';

/**
 * Map child: highlights perimeters with a pink outline and a diagonal hatch fill.
 * Leaflet can't hatch on its own, so the pattern lives in a zero-size SVG and the `.hm-peri-poly` CSS rule
 * (`fill: url(#hm-peri-hatch)`) applies it to the polygons. Must live inside a react-leaflet MapContainer.
 */
export default function PerimetroRodeoLayer({ perimetros }: { perimetros: PerimetroResponse[] }) {
    return (
        <>
            <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true" focusable="false">
                <defs>
                    <pattern id="hm-peri-hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(135)">
                        <rect width="7" height="7" fill={PINK} fillOpacity=".12" />
                        <rect width="3" height="7" fill={PINK} fillOpacity=".55" />
                    </pattern>
                </defs>
            </svg>
            {perimetros.map(p => (
                <Polygon
                    key={p.id}
                    positions={p.coordenadas.map(c => [c.latitud, c.longitud] as [number, number])}
                    interactive={false}
                    // className must be a direct prop: Leaflet reads it only when it creates the path, while pathOptions are applied afterwards
                    className="hm-peri-poly"
                    pathOptions={{ color: PINK, weight: 3, fillOpacity: 1 }}
                />
            ))}
        </>
    );
}
