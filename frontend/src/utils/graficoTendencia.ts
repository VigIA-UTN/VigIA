/** Valores de las líneas horizontales del gráfico de tendencia, sin repetidos: con máximo 1, la mitad redondeada también da 1. */
export function valoresGrillaY(maxVal: number): number[] {
    return [...new Set([0, Math.round(maxVal / 2), maxVal])];
}
