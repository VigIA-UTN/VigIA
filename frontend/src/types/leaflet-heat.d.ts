declare module 'leaflet.heat' {
    import * as L from 'leaflet';

    type HeatLatLngTuple = [number, number, number?];

    interface HeatLayerOptions {
        minOpacity?: number;
        maxZoom?: number;
        max?: number;
        radius?: number;
        blur?: number;
        gradient?: Record<number, string>;
    }

    interface HeatLayer extends L.Layer {
        setLatLngs(latlngs: HeatLatLngTuple[]): this;
        addLatLng(latlng: HeatLatLngTuple): this;
        setOptions(options: HeatLayerOptions): this;
        redraw(): this;
    }

    function heatLayer(latlngs: HeatLatLngTuple[], options?: HeatLayerOptions): HeatLayer;

    export { heatLayer, HeatLayer, HeatLayerOptions, HeatLatLngTuple };
}
