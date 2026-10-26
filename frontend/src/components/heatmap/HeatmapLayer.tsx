import L from 'leaflet'; // sets window.L, which leaflet.heat patches
import 'leaflet.heat';
import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import { HEAT_GRADIENT, type HeatLatLngTuple } from './types';

// leaflet.heat hardcodes `t.getPanes().overlayPane.removeChild(this._canvas)`.
// But we move its canvas to a custom 'heat' pane (so it renders above perimeter polygons).
// When the layer or the map unmounts, that hardcoded overlayPane.removeChild call throws:
// "NotFoundError: Failed to execute 'removeChild' on 'Node': The node to be removed is not a child of this node."
// We patch HeatLayer.prototype.onRemove once so it safely removes the canvas from its actual parentNode.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const HeatLayerClass = (L as any).HeatLayer;
if (HeatLayerClass && !HeatLayerClass.prototype._patchedSafeRemove) {
    HeatLayerClass.prototype.onRemove = function (map: L.Map) {
        if (this._canvas && this._canvas.parentNode) {
            this._canvas.parentNode.removeChild(this._canvas);
        }
        const targetMap = map || this._map;
        if (targetMap) {
            targetMap.off('moveend', this._reset, this);
            if (targetMap.options && targetMap.options.zoomAnimation) {
                targetMap.off('zoomanim', this._animateZoom, this);
            }
        }
    };
    HeatLayerClass.prototype._patchedSafeRemove = true;
}

// leaflet.heat patches L globally — no named exports
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createHeatLayer = (points: HeatLatLngTuple[], opts: object) => (window as any).L.heatLayer(points, opts);

/** Map child: renders `points` as a heat layer. Must live inside a react-leaflet MapContainer. */
export default function HeatmapLayer({ points }: { points: HeatLatLngTuple[] }) {
    const map = useMap();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const layerRef = useRef<any>(null);

    useEffect(() => {
        if (!map) return;

        const pane = map.getPane('heat') ?? map.createPane('heat');
        pane.style.zIndex = '450'; // overlayPane is 400, markers 600
        pane.style.pointerEvents = 'none';

        if (!layerRef.current) {
            const layer = createHeatLayer(points, {
                radius: 28,
                blur: 20,
                maxZoom: 17,
                gradient: HEAT_GRADIENT,
            }).addTo(map);

            if (layer._canvas && layer._canvas.parentNode !== pane) {
                pane.appendChild(layer._canvas);
            }
            layerRef.current = layer;
        } else {
            layerRef.current.setLatLngs(points);
        }
    }, [map, points]);

    useEffect(() => {
        return () => {
            if (layerRef.current && map) {
                map.removeLayer(layerRef.current);
                layerRef.current = null;
            }
        };
    }, [map]);

    return null;
}
