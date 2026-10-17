// Tráfico de la prueba de carga (ver README). Los escenarios se eligen con ESCENARIOS, separados por coma:
//   sensores  25.000 sensores a su ritmo real: un GNSS cada INTERVALO_S y un heartbeat por hora (modelo abierto:
//             siguen mandando aunque el servidor se atrase, como los sensores de verdad).
//   rafaga    los mismos uplinks, con la tasa subiendo desde RAFAGA_DESDE/s hasta MAX/s en RAMPA; se corta solo
//             cuando se degrada. Es alternativo a "sensores": los dos numeran igual sus uplinks.
//   clientes_streams     cada productor con 3 streams SSE abiertos, como la página del mapa: 2 de alertas
//                        (el sidebar y el mapa) y 1 de telemetría del mapa.
//   clientes_navegacion  cada productor recarga animales, mapa y alertas cada PENSAR_S (60 s), con jitter.
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';

const CLIENTES = Number(__ENV.CLIENTES || 500);
const SENSORES_POR_CLIENTE = Number(__ENV.SENSORES_POR_CLIENTE || 50);
const SENSORES = CLIENTES * SENSORES_POR_CLIENTE;
const INTERVALO_S = Number(__ENV.INTERVALO_S || 300);
const RATE = Number(__ENV.RATE || 1);
const DURACION = __ENV.DURACION || '30m';
const RAFAGA_DESDE = Number(__ENV.RAFAGA_DESDE || 90);
const MAX = Number(__ENV.MAX || 2000);
const RAMPA = __ENV.RAMPA || '10m';
const PENSAR_S = Number(__ENV.PENSAR_S || 60);
const ESCENARIOS = (__ENV.ESCENARIOS || 'sensores').split(',');
const CON_CLIENTES = ESCENARIOS.some((e) => e.startsWith('clientes_'));

if (ESCENARIOS.includes('sensores') && ESCENARIOS.includes('rafaga')) {
    throw new Error('sensores y rafaga son alternativos: numeran igual sus uplinks y se pisarían los f_cnt');
}

const escenarios = {
    sensores: {
        executor: 'constant-arrival-rate',
        exec: 'sensor',
        rate: Math.round(SENSORES * RATE),
        timeUnit: `${INTERVALO_S}s`,
        duration: DURACION,
        // Con 50 iniciales, k6 descartaba iteraciones cuando la latencia subía (RATE=2: 128 de 19.872).
        preAllocatedVUs: 200,
        maxVUs: 1000,
    },
    rafaga: {
        executor: 'ramping-arrival-rate',
        exec: 'sensor',
        startRate: RAFAGA_DESDE,
        timeUnit: '1s',
        stages: [{ target: MAX, duration: RAMPA }],
        preAllocatedVUs: 200,
        maxVUs: Number(__ENV.MAX_VUS || 2000),
    },
    clientes_streams: {
        executor: 'constant-vus',
        exec: 'stream',
        vus: CLIENTES * 3,
        duration: DURACION,
        gracefulStop: '5s',
    },
    clientes_navegacion: {
        executor: 'constant-vus',
        exec: 'navegar',
        vus: CLIENTES,
        duration: DURACION,
    },
};

// Vara del webhook, del timeout por defecto de TTN (5 s): ver plan-carga.md.
const umbrales = {
    sensores: {
        'http_req_failed{scenario:sensores}': ['rate<0.001'],
        'http_req_duration{scenario:sensores}': ['p(99)<1000', 'max<5000'],
        'checks{scenario:sensores}': ['rate>0.999'],
    },
    rafaga: {
        'http_req_failed{scenario:rafaga}': [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
        'http_req_duration{scenario:rafaga}': [{ threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '30s' }],
    },
    // Uno por endpoint, así el resumen trae el p95 de cada uno. Vara de los clientes: p95 < 2 s.
    clientes_navegacion: {
        'http_req_duration{name:animales}': ['p(95)<2000'],
        'http_req_duration{name:mapa}': ['p(95)<2000'],
        'http_req_duration{name:alertas}': ['p(95)<2000'],
        'http_req_failed{scenario:clientes_navegacion}': ['rate<0.01'],
    },
    clientes_streams: {},
};

export const options = {
    scenarios: Object.fromEntries(ESCENARIOS.map((e) => [e, escenarios[e]])),
    thresholds: Object.assign({}, ...ESCENARIOS.map((e) => umbrales[e])),
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    setupTimeout: '3m',
    // Cada VU conserva su cookie de sesión como un navegador. Si k6 las borrara en cada iteración, la API crearía una
    // sesión HTTP nueva por reconexión y por recarga, que el navegador no crea.
    noCookiesReset: true,
};

// BASE: segundo epoch del arranque, el mismo para todos los VUs. Crece entre corridas, así el filtro de frames
// duplicados del worker (f_cnt <= último) nunca descarta un uplink simulado.
// Con clientes, además, el token y el establecimiento de cada productor de la semilla (carga-001 ... carga-500,
// contraseña productor123): 500 logins, de a 50 en paralelo, y después el establecimiento de cada uno, de a uno. La
// API responde con JSESSIONID, y requests simultáneos de usuarios distintos con la misma cookie mezclan las
// identidades (ver plan-carga.md, "Noticed"): en paralelo, cada productor recibía el establecimiento de otro.
export function setup() {
    const datos = { base: Math.floor(Date.now() / 1000), inicioMs: Date.now(), clientes: [] };
    if (!CON_CLIENTES) return datos;
    for (let desde = 1; desde <= CLIENTES; desde += 50) {
        const n = Array.from({ length: Math.min(50, CLIENTES - desde + 1) }, (_, i) => desde + i);
        const logins = http.batch(n.map((k) => ['POST', `${__ENV.API_URL}/auth/login`,
            JSON.stringify({ email: `carga-${String(k).padStart(3, '0')}@vigia.dev`, password: 'productor123' }),
            { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }]));
        for (const login of logins) {
            const token = login.json('token');
            const ests = http.get(`${__ENV.API_URL}/establecimientos`, {
                headers: { Authorization: `Bearer ${token}` },
                tags: { name: 'establecimientos' },
            });
            datos.clientes.push({ token, est: ests.json('0.id') });
        }
    }
    return datos;
}

// La misma fórmula que trayectoria.sql (y la grilla de establecimientos de seed.sql), así el tráfico continúa el
// historial sembrado. g: índice global del sensor (LT-00001 es 0); t: tick de 5 minutos desde epoch.
function posicion(g, t) {
    const n = Math.floor(g / SENSORES_POR_CLIENTE); // establecimiento, desde 0
    const d = g % SENSORES_POR_CLIENTE;
    const lat0 = -35 - Math.floor(n / 25) * 0.05;
    const lon0 = -60 - (n % 25) * 0.05;
    const a1 = (2 * Math.PI * t) / 87.6 + d * 0.37;
    const a2 = (2 * Math.PI * t) / 34.8 + d * 1.13;
    return {
        lat: lat0 + (Math.floor(d / 10) - 2) * 0.0003 + 0.000195 * Math.sin(a1) + 0.0000443 * Math.sin(a2),
        lon: lon0 + ((d % 10) - 4.5) * 0.0004 + 0.000195 * Math.cos(a1) + 0.0000443 * Math.cos(a2),
    };
}

// Como el pastoreo normal del simulador: 35 % reposo (0-1), 60 % normal (2-6), 5 % alta actividad (7-9).
function moveState() {
    const r = Math.random();
    if (r < 0.35) return Math.floor(Math.random() * 2);
    if (r < 0.95) return 2 + Math.floor(Math.random() * 5);
    return 7 + Math.floor(Math.random() * 3);
}

const params = {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${__ENV.TTN_API_KEY}` },
    timeout: '10s',
    tags: { name: 'uplink' },
};

// El webhook responde 200 también cuando ignora el uplink (frame duplicado, dispositivo desconocido...): el cuerpo
// "OK" es lo que dice que se procesó.
function enviar(devEui, fCnt, decoded) {
    const cuerpo = JSON.stringify({
        end_device_ids: { dev_eui: devEui },
        uplink_message: { f_cnt: fCnt, received_at: new Date().toISOString(), decoded_payload: decoded },
    });
    const r = http.post(`${__ENV.WORKER_URL}/api/v1/ingesta/uplink`, cuerpo, params);
    check(r, { 'uplink procesado': (res) => res.status === 200 && res.body === 'OK' });
}

// Iteración i: el sensor i % SENSORES en su ronda i / SENSORES. A ritmo 1× cada sensor manda cada INTERVALO_S.
export function sensor(data) {
    const i = exec.scenario.iterationInTest;
    const g = i % SENSORES;
    const ronda = Math.floor(i / SENSORES);
    const devEui = `LT-${String(g + 1).padStart(5, '0')}`;
    const p = posicion(g, Date.now() / 1000 / 300);
    enviar(devEui, data.base + ronda * 2, {
        type: 'GNSSPosition',
        latitude: p.lat,
        longitude: p.lon,
        moveState: moveState(),
        battery: '89.5%',
    });
    // Un heartbeat por hora por sensor, repartidos entre rondas para que sean ~6,9/s parejos y no todos juntos.
    if ((ronda + g) % 12 === 0) {
        enviar(devEui, data.base + ronda * 2 + 1, { type: 'Heartbeat', moveState: moveState(), battery: '89.4%' });
    }
}

// Cada VU de clientes atiende siempre al mismo productor (y en streams, al mismo stream): el índice de su primera
// iteración, que en constant-vus arrancan todas juntas, una por VU.
// ponytail: si una primera iteración termina antes de que arranquen las demás, dos VUs pueden quedar con el mismo
// productor; con streams de 30 min y una espera inicial en navegación no pasa.
const tickets = {};
function miTicket() {
    const escenario = exec.scenario.name;
    if (tickets[escenario] === undefined) tickets[escenario] = exec.scenario.iterationInTest;
    return tickets[escenario];
}

// Un GET largo por stream: del lado del servidor ocupa lo mismo que un EventSource (la conexión, el SseEmitter y su
// heartbeat de 20 s). El servidor lo cierra a los 30 min y el VU reconecta, como el navegador.
export function stream(data) {
    const t = miTicket();
    const c = data.clientes[t % CLIENTES];
    const alertas = Math.floor(t / CLIENTES) < 2;
    http.get(`${__ENV.API_URL}${alertas ? '/alertas/stream' : '/animales/mapa/stream'}?establecimientoId=${c.est}`, {
        headers: { Authorization: `Bearer ${c.token}` },
        timeout: `${segundos(DURACION) + 60}s`,
        responseType: 'none',
        tags: { name: alertas ? 'stream_alertas' : 'stream_mapa' },
    });
    sleep(1); // si el stream se cae enseguida, que no reconecte en un loop
}

export function navegar(data) {
    const c = data.clientes[miTicket() % CLIENTES];
    if (exec.vu.iterationInScenario === 0) sleep(Math.random() * PENSAR_S); // que los 500 no entren juntos
    for (const [nombre, ruta] of [['animales', '/animales'], ['mapa', '/animales/mapa'], ['alertas', '/alertas']]) {
        const r = http.get(`${__ENV.API_URL}${ruta}?establecimientoId=${c.est}`, {
            headers: { Authorization: `Bearer ${c.token}` },
            responseType: 'none',
            tags: { name: nombre },
        });
        check(r, { 'navegación 2xx': (res) => res.status >= 200 && res.status < 300 });
    }
    sleep(PENSAR_S * (0.5 + Math.random()));
}

// En la ráfaga, la tasa a la que llegó cuando terminó o se cortó (la rampa es lineal).
export function teardown(data) {
    if (!ESCENARIOS.includes('rafaga')) return;
    const transcurridoS = (Date.now() - data.inicioMs) / 1000;
    const rampaS = segundos(RAMPA);
    const tasa = RAFAGA_DESDE + (MAX - RAFAGA_DESDE) * Math.min(1, transcurridoS / rampaS);
    console.log(`rafaga: ${Math.round(transcurridoS)} s de ${rampaS} s de rampa, tasa alcanzada ~${Math.round(tasa)}/s`);
}

function segundos(duracion) {
    const m = /^(\d+)(s|m|h)$/.exec(duracion);
    return Number(m[1]) * { s: 1, m: 60, h: 3600 }[m[2]];
}
