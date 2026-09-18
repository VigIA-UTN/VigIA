# Dashboard Ideas

Product/design specification for the VigIA dashboard redesign.
Written for: the team implementing the dashboard. Domain terms are kept in Spanish because they match the code (`Alerta`, `RodeoLote`, `Caravana`, `EstadoReproductivo`, …).

---

## Executive Summary

VigIA is a **precision-livestock alerting system**. Collars (`Caravana`, LoRaWAN via TTN) push a reading every ~5 minutes (`Telemetria`: lat, lon, `move_state`, battery). Two ML services turn that stream into alerts:

- **Celo** (`ml-celo`, LSTM): "this cow is in heat, inseminate between +6h and +24h" (`Alerta.ventanaInicioIns/FinIns`).
- **Salud** (`ml-salud`): "this animal deviates from her own 7-day baseline" (`Alerta.posibleCausa`, `confianzaModelo`, `serieComparativaJson`).

A third source, the **geofence** (`GeofenceVerificationService`, PostGIS), emits `FUERA_DE_PERIMETRO` alerts (always severity ALTA, 6 h cooldown).

Around that sit the manual/operational records: `EstadoReproductivo` (history, with `fechaUltimoParto`, `fechaUltimaInseminacion`), `EstadoSalud` (vet revisions), `Intervencion` (tratamiento, vacunación, revisión, inseminación), `RegistroPeso`, `RodeoLote` (paddock/lote groups) and `Perimetro` (polygons per lote).

**What the dashboard should do:** answer "what do I have to do today, and what is about to go wrong?" The system's value is *time-critical action* (a heat window lasts ~18 h; a sick animal is only cheap to treat early; a collar that went silent means an animal is unwatched). So the dashboard should be an **action queue plus a "can I trust my monitoring" indicator**, with trends and reproductive performance as second-level context. It should *not* be a census of the herd.

**Three findings that shape the design:**

1. **The current dashboard is nearly all celo counters** and computes them client-side by downloading every animal and every celo alert (`DashboardPage.tsx`). Its "Próximos a celo" tile is not a prediction: it counts animals with a celo alert 1–5 days ago. Its "geoespacial" tile shows 0 for real perimeter breaches because `AlertaServiceImpl.getResumen` only counts `ABIGEATO`, and nothing in the code ever *creates* an `ABIGEATO` alert (`ml-geo` is configured via `ML_GEO_URL` but never called). `FUERA_DE_PERIMETRO` is the only working geo alert.
2. **Most of the valuable metrics are derivable but not exposed.** Time-to-response, missed heat windows, days open, collar coverage, per-lote incidence, activity trends: all computable from existing tables, none has an endpoint.
3. **Some data does not exist**, and the dashboard must not pretend it does: animal **sex/category**, **mortality date and cause**, **calves/offspring**, **pregnancy diagnosis**, **disease type**, weather, feed/water, costs.

---

## Available Data

Reliability legend: **High** = written automatically or validated; **Medium** = manual entry, may be sparse or stale; **Low** = exists but unused/untrustworthy.

| Domain | Source (code) | What it holds | Reliability / caveats |
|---|---|---|---|
| **Telemetría** | table `telemetria`; ingest in `backend-worker` `IngestaController` (TTN webhook) | `fecha_hora`, `latitud`, `longitud`, `move_state` (activity index; ≥7 = high activity, ≤1 = rest per `features_salud.py`), `bateria`, `estado_inferencia_celo/salud` | **High** when the collar transmits. Indexed by caravana, not animal (a re-linked collar mixes histories). Retention policy unknown. Per pending.md, a DevEUI case mismatch silently drops uplinks, and `estadoSensor` is never updated by ingest. |
| **Caravana (collar)** | `caravanas` (V4–V7) | `dev_eui`, `estado_sensor` (DISPONIBLE/VINCULADO/INACTIVO), `ultima_senial`, `bateria`, `alias`, `establecimiento_id`, `fecha_asignacion` | **High** for `ultima_senial` and `bateria` (updated each uplink). `estado_sensor` is admin-managed, not signal-derived. |
| **Alerta** | `alertas`; created by `CeloDetectorStrategyImpl`, `SaludDetectorStrategyImpl`, `GeofenceVerificationService` | `disparador` (CELO/SALUD/FUERA_DE_PERIMETRO/ABIGEATO), `severidad`, `estado`, `ts_generacion`, `ts_confirmacion`, `usuario_confirmacion`, `motivo_descarte`, `confianza_modelo`, `posible_causa`, `ventana_*_ins`, `serie_comparativa_json` | **High** for timestamps/state. Celo severity is always ALTA, so severity does not discriminate for celo. `ABIGEATO` has no producer. `VENCIDA` is only a computed state of the insemination window (`AlertaServiceImpl` ~L601), not a stored `EstadoAlerta`. Celo alerts unattended after `ventana_fin_ins` are auto-set `ARCHIVADA` with `motivo_descarte="oportunidad_no_aprovechada"` (hourly job `AlertaArchivadorService`): a reliable "missed heat" signal. `motivo_descarte` is otherwise free text. |
| **Intervención** | `intervenciones` (V2) | `tipo` (TRATAMIENTO, VACUNACION, REVISION_VETERINARIA, INSEMINACION, OTRO), `descripcion`, `interventor`, `fecha_hora`, optional `alerta_id` (unique: one intervention per alert) | **Medium**: manual. INSEMINACION interventions also write `fechaUltimaInseminacion` into `EstadoReproductivo` (`AlertaServiceImpl` ~L827). |
| **Estado reproductivo** | `estados_reproductivos` (append-only history, latest row wins) | `estado` (VACIA, GESTANTE, CELO, ANESTRO, POSIBLE_CELO, INSEMINADA, PARIDA), `fecha_ultimo_parto`, `fecha_ultima_inseminacion`, `origen` (MANUAL/CARGA_MASIVA/AUTOMATICO), `fecha_registro` | **Medium**. Manual/CSV plus automatic POSIBLE_CELO and CELO on alert confirmation. History allows event reconstruction, but no pregnancy-diagnosis date or result exists. |
| **Estado de salud** | `estados_salud` | `estado` (SANO/EN_OBSERVACION/ENFERMO), `fecha_revision`, `diagnostico_notas`, `tratamiento_indicado` (free text), vet, `origen` | **Medium**: manual by vet. Free text means no per-disease aggregation. |
| **Peso** | `registros_peso` | `fecha`, `valor_kg` per animal | **Low–Medium**: manual, likely sparse. |
| **Animal** | `animales` | `raza`, `fecha_nacimiento`, `rodeo_lote_id`, `estado_actual` (ACTIVO/INACTIVO; `CON_ALERTA` is defined but never referenced), `fecha_alta`, `caravana_id`, `alias`, `id_caravana_oficial` | **High** for lote/raza/birth date. **No sex, category, mother, or baja date/reason.** |
| **Calibración de salud** | `perfiles_referencia`; `AnimalResponse.enCalibracion/diasRestantesCalibracion` | Per-animal baseline; the first 7 days (`vigia.salud.calibracion-dias`) produce no health alerts; recalibrated every 30 days | **High**. Animals in calibration are *blind* for health monitoring. |
| **Perímetro / geo** | `perimetro` (PostGIS `Polygon`), `perimetro_lote` | Polygon per establecimiento, assigned to lotes | **High**. Polygon area is computable, so stocking density is derivable. |
| **Establecimiento / usuarios** | `establecimientos`, `empleado`, `usuarios`, `invitaciones` | Farm lat/lon, staff roles (ADMINISTRADOR, PRODUCTOR, EMPLEADO, VETERINARIO), employee link dates | **High**. Users can have several establecimientos (the UI has a selector). |
| **Existing read endpoints** | `AlertaController`, `AnimalController`, … | `/alertas/resumen`, `/alertas` (filters), `/alertas/stream` (SSE), `/animales/mapa` + `/mapa/stream`, `/{id}/metricas`, `/{id}/indicadores-salud`, `/{id}/ventana-inseminacion`, `/{id}/linea-tiempo-alertas`, `/{id}/historial-pesos` | Mostly per-animal or list endpoints. **There are no aggregate endpoints**, so every dashboard metric below needs one. |
| **ML services not wired** | `ml-geo` (`/geo/analyze`: cohesion index, isolation alert, density heatmap) | Declared, never called by backend | **Low**: do not build UI on it until wired. |
| **Simulated data** | `simulador/`, `DataInitializer` | Demo animals VIGIA-ARG-001 (celo) and 002 (salud) | Dev data only. Real deployments will have far fewer alerts than the demo. Design empty states well. |

---

## Must Have

### Idea 1: "Requiere atención hoy" (unified action queue)

- **What it shows:** One ranked list of everything that needs a human decision now: pending alerts of all types plus collar failures, each row = animal (alias/caravana), lote, type, severity, age of the alert, recommended action, and a one-click "Atender" that opens the existing `AlertDetailSidebar`/intervention form.
- **Why it matters:** The rancher's first question is "what do I do today?". Today they must open the alert page and mentally merge three alert types. The list must rank by urgency, not just severity: a celo alert with 3 h left in its window outranks a salud alert from yesterday.
- **Data source:** `alertas` (`estado=PENDIENTE`, `disparador`, `severidad`, `ts_generacion`, `ventana_fin_ins`, `accion_recomendada`, `posible_causa`), animal/lote join. Existing `AlertaRepository.findByFiltros` already sorts by severity then recency.
- **Calculation:** `urgencia`: celo = hours left to `ventana_fin_ins` (ascending); salud = severity then age; perímetro = age (cooldown makes them re-fire every 6 h if unresolved); collar silent = see Idea 4. Group by animal so one animal with three alerts is one row.
- **Visualization:** Table/list with type icon, severity chip, a live countdown for celo, age badge (red beyond 24 h). Top 10 with "ver todas".
- **Filters:** Establecimiento (existing selector), rodeo/lote, type, severity.
- **Action/insight enabled:** Go straight to the animal that must be inseminated/inspected/found. Reduces alert-page browsing.
- **Data availability:** Exists directly (alerts) and derived (ranking).
- **Complexity:** **Low–Medium**. Reuses `/alertas`; add lote and window fields and a per-animal group.

### Idea 2: Ventanas de inseminación abiertas

- **What it shows:** Cows currently in a heat window, with time left until it closes, plus a compact "oportunidades perdidas" count (windows that expired unattended).
- **Why it matters:** This is the system's headline value. A missed window costs ~21 days of delay per cow. The window logic is already computed (`VentanaInseminacionResponse`: `tiempoRestanteSegundos`, `porcentajeTranscurrido`, `proximaAVencer`).
- **Data source:** `alertas` where `disparador=CELO`, `estado=PENDIENTE`, `ventana_inicio_ins/ventana_fin_ins`; missed = `estado=ARCHIVADA AND motivo_descarte='oportunidad_no_aprovechada'`.
- **Calculation:** Phase per alert: *aún no abierta* (<+6 h from generation), *óptima* (open), *por vencer* (`proximaAVencer`). Missed windows = count of archived-by-timeout alerts over the last 30 days ÷ celo alerts not discarded in the same period.
- **Visualization:** Horizontal progress bars (one per cow) showing window elapsed, colored by phase; a small KPI "Ventanas perdidas (30 d)" with the previous 30 d as comparison.
- **Filters:** Lote; period for missed windows.
- **Action/insight enabled:** "Call the inseminator for these 3 cows within 5 h." Missed-window rate tells the manager whether staffing/response is the problem.
- **Data availability:** Exists directly.
- **Complexity:** **Low**. Note: the current dashboard's "Próximos a celo" tile should be removed; it is not a forecast.

### Idea 3: Animales a revisar (sanidad)

- **What it shows:** Animals with an open salud alert or a vet-recorded `ENFERMO`/`EN_OBSERVACION` status, with `posibleCausa`, alert severity, model confidence, days since alert, and whether anyone has intervened.
- **Why it matters:** Early disease detection is the second pillar. The ML flags deviation from the animal's own baseline, so the value is catching a problem before visual signs. The dashboard must show *who has not been looked at yet*.
- **Data source:** `alertas` (SALUD, PENDIENTE/CONFIRMADA), `estados_salud` latest per animal, `intervenciones` (by `alerta_id` or animal, after `ts_generacion`).
- **Calculation:** `sin atender` = PENDIENTE salud alerts with age > threshold (24 h suggested; the detector re-checks only after 24 h anyway). `Bajo seguimiento` = latest `EstadoSalud` in {EN_OBSERVACION, ENFERMO}. Days in that state = today − `fecha_revision`.
- **Visualization:** Two-part list: "Alertas sin atender" and "En observación/enfermos", with a mini sparkline of the animal's `serieActividad` if cheap to fetch.
- **Filters:** Lote, severity, posibleCausa.
- **Action/insight enabled:** Send the vet to the highest-severity un-attended animals first; spot animals stuck in EN_OBSERVACION for weeks without re-review.
- **Data availability:** Exists directly (alerts, statuses); "stuck in observation" is derived.
- **Complexity:** **Medium** (join of three sources).

### Idea 4: Cobertura y salud de los collares

- **What it shows:** "How much of my herd is actually being watched": % of animals with a working, recent collar, and the exception list: animals without a collar, collars silent for > N hours, low battery, animals still in the 7-day calibration.
- **Why it matters:** Every alert exists only if the collar transmits. A silent collar means *no celo detection and no health alerts for that animal*, invisibly. The rancher must know their blind spots before trusting an empty alert list. Today this is buried in per-animal indicators.
- **Data source:** `animales.caravana_id`, `caravanas.ultima_senial`, `caravanas.bateria`, `estado_sensor`, `AnimalResponse.enCalibracion/diasRestantesCalibracion`.
- **Calculation:** Eligible = `estadoActual != INACTIVO`. Monitored = has caravana, `ultima_senial` within 6 h (the threshold the code already uses for the movement indicator, `HORAS_SIN_DATOS_MOVIMIENTO`; the map uses 15 min for "live"), and not in calibration. Coverage % = monitored ÷ eligible. Battery threshold is not defined in code: propose ≤20%, make configurable.
- **Visualization:** One KPI (coverage %) with a stacked bar (monitored / calibrating / silent / no collar / low battery) and an exception table under it.
- **Filters:** Lote.
- **Action/insight enabled:** "6 collars silent since yesterday, 3 with <15% battery: go check them." Also explains why the alert list is quiet.
- **Data availability:** Exists directly. Caveat: `estado_sensor` isn't signal-derived, so use `ultima_senial` instead.
- **Complexity:** **Low** (single aggregate query).

### Idea 5: Mapa vivo con perímetros y fugas

- **What it shows:** Last known position of each animal, perimeter polygons per lote, animals outside their polygon highlighted, animals with stale position greyed out.
- **Why it matters:** "Where are problems occurring?": the only place where location context is visible. Out-of-perimeter is the actionable location event.
- **Data source:** `GET /animales/mapa` (+ SSE `/mapa/stream`), `AnimalMapaResponse` (`sinDatosRecientes`, `alertasAnimal`), `/perimetros`, `alertas` with `FUERA_DE_PERIMETRO`. The Leaflet map already exists in `MapPage.tsx`.
- **Calculation:** Marker color = highest pending-alert severity; ring on animals with an open `FUERA_DE_PERIMETRO` alert; grey if `sinDatosRecientes`.
- **Visualization:** Map (compact on the dashboard, "expand" to `MapPage`). **Fix required:** include `FUERA_DE_PERIMETRO` in `getResumen` (currently only `ABIGEATO`).
- **Filters:** Lote, alert type.
- **Action/insight enabled:** Find a stray animal on the map instead of by reading coordinates.
- **Data availability:** Exists directly. Only lotes with an active perimeter are checked; animals without lote or perimeter are never flagged (unmonitored, not "safe").
- **Complexity:** **Low–Medium** (component reuse).

### Idea 6: Embudo reproductivo (estado del rodeo reproductivo)

- **What it shows:** Distribution of the latest `EstadoReproductivo` across reproductive animals (VACIA / CELO+POSIBLE_CELO / INSEMINADA / GESTANTE / PARIDA / ANESTRO), as a pipeline, with lists of the exceptions that need a decision.
- **Why it matters:** This is the reproductive picture in one glance, but its value is in the exceptions: *VACIA long after parto*, *INSEMINADA long ago and still not confirmed*, *ANESTRO*. A plain "total animales" number is not useful; the *stage counts and the stuck cases* are.
- **Data source:** latest `estados_reproductivos` per animal (already loaded via `AnimalResponse.estadoReproductivo/fechaUltimoParto/fechaUltimaInseminacion`).
- **Calculation:**
  - Population = non-INACTIVO animals with age ≥ 1 year (as the current dashboard assumes; **sex is not recorded**, so males will be included: see Future).
  - *Días abiertos* = today − `fechaUltimoParto` for VACIA/CELO/INSEMINADA (no confirmed gestation).
  - *Pendiente de diagnóstico* = INSEMINADA with `today − fechaUltimaInseminacion` > ~30–35 d (biological convention; configurable).
  - *Sin servicio* = VACIA with `days open` > threshold (e.g., 60 d) and no INSEMINADA since.
- **Visualization:** Horizontal stacked bar (pipeline) + 2–3 exception tables under it. One color per state, no pie.
- **Filters:** Lote, raza, age range.
- **Action/insight enabled:** "14 cows are empty more than 90 days after calving: check nutrition or schedule service." "9 inseminated 35+ days ago need pregnancy check."
- **Data availability:** Exists directly. **Completeness depends on manual updates or CSV uploads**; show "sin estado registrado" as its own segment so gaps are visible rather than hidden.
- **Complexity:** **Medium** (new aggregate endpoint).

---

## Useful

### Idea 7: Partos esperados (calendario de partos)

- **What it shows:** GESTANTE animals with an estimated calving date, by week for the next 60–90 days.
- **Why it matters:** Lets the rancher plan labor, paddock moves and vet cover for calving; also flags overdue animals.
- **Data source:** latest `EstadoReproductivo` = GESTANTE with `fecha_ultima_inseminacion`.
- **Calculation:** expected date = `fechaUltimaInseminacion` + ~283 d (bovine gestation length: a biological constant, not stored data; tune by raza if needed). Overdue = expected + 10 d and no PARIDA record.
- **Visualization:** Weekly bar chart (count per week) + list of next 14 days.
- **Filters:** Lote, raza.
- **Action/insight enabled:** "23 calvings in the next 3 weeks, 6 in the next 7 days."
- **Data availability:** Derived; depends on `fechaUltimaInseminacion` being filled for GESTANTE rows (true after an INSEMINACION intervention or complete CSV; may be missing when GESTANTE was entered manually without a date). Show a "sin fecha" count.
- **Complexity:** **Low**.

### Idea 8: Tendencia de alertas vs. período anterior

- **What it shows:** Alerts per week by type (celo, salud, perímetro) for the last 8–12 weeks, with the current vs. previous 4 weeks change.
- **Why it matters:** Distinguishes noise from a real change: a doubling of health alerts in one lote is an outbreak signal; a drop in celo alerts can mean collars failing, not cows cycling.
- **Data source:** `alertas.ts_generacion`, `disparador`, lote via animal.
- **Calculation:** Count per ISO week per `disparador`; delta % vs prior period; normalize per 100 monitored animals to compare lotes.
- **Visualization:** Small multiples (one line/bar chart per type, same x-axis), delta badge. Not stacked; series have different meanings.
- **Filters:** Lote, period (4/8/12 weeks).
- **Action/insight enabled:** "Health alerts up 60% vs last month in Lote Norte."
- **Data availability:** Exists directly. **Complexity:** **Low–Medium**.

### Idea 9: Respuesta y calidad de las alertas

- **What it shows:** Median time from `ts_generacion` to `ts_confirmacion`, % of alerts attended, % discarded (proxy for false positives) per type, and pending backlog age.
- **Why it matters:** Tells the owner whether the team is acting on alerts, and tells the team whether the models are worth trusting (a 70% discard rate for salud says recalibrate or ignore).
- **Data source:** `alertas` (`estado`, `ts_generacion`, `ts_confirmacion`, `usuario_confirmacion`, `motivo_descarte`, `confianza_modelo`), `intervenciones`.
- **Calculation:** response time = `ts_confirmacion − ts_generacion` for CONFIRMADA/DESCARTADA; confirmation rate = CONFIRMADA ÷ (CONFIRMADA + DESCARTADA); auto-archived rate for celo. Note archiver sets `ts_confirmacion` on auto-archive: exclude `ARCHIVADA` from response-time medians or they will be inflated with fake responses.
- **Visualization:** 3 KPIs per type + histogram of response time.
- **Filters:** Type, period, user (`usuario_confirmacion`).
- **Action/insight enabled:** Detect that night alerts are never attended; decide whether to tighten or relax model thresholds.
- **Data availability:** Derived; "discard = false positive" is an approximation.
- **Complexity:** **Medium**.

### Idea 10: Comparativa por lote (rodeo)

- **What it shows:** A table with one row per `RodeoLote`: animals, collar coverage, alerts per 100 animals (30 d), open salud alerts, % vacías, out-of-perimeter events.
- **Why it matters:** Locates *where* the problems are; per-lote normalization prevents big lotes from always looking worst.
- **Data source:** all of the above grouped by `animales.rodeo_lote_id`.
- **Calculation:** `alertas_30d ÷ animales_monitoreados × 100`; ranked, with conditional coloring against the establecimiento mean.
- **Visualization:** Sortable table with in-cell bars; each row links to a lote-filtered view.
- **Filters:** Period.
- **Action/insight enabled:** Drill from "Lote Sur has 3× the health alerts" to its animals; feeds the paddock/pasture conversation.
- **Data availability:** Derived. **Complexity:** **Medium**.

### Idea 11: Tiempo desde la última intervención / revisión

- **What it shows:** Animals ordered by days since last `REVISION_VETERINARIA`, `VACUNACION` or `TRATAMIENTO`, and animals with an active health status but no intervention recorded.
- **Why it matters:** Prevents animals being forgotten. The system has no vaccination schedule, so the dashboard cannot say "overdue", but it can say "not vaccinated in 200 days".
- **Data source:** `intervenciones.tipo/fecha_hora`, `estados_salud.fecha_revision`.
- **Calculation:** `max(fecha_hora)` per animal per tipo; days since. User-configurable staleness thresholds.
- **Visualization:** Table sorted descending with a grey "never" bucket.
- **Filters:** Tipo de intervención, lote.
- **Action/insight enabled:** Build the next vaccination/revision round list.
- **Data availability:** Derived; only meaningful if interventions are being recorded (manual). **Complexity:** **Low**.

---

## Advanced

### Idea 12: Actividad del rodeo: alerta temprana por lote

- **What it shows:** Lote-level average activity (`move_state`) and % time at rest (`move_state ≤ 1`) over the last 7 days vs. the lote's own 30-day norm.
- **Why it matters:** Individual alerts detect one sick animal; a *herd-wide* drop or spike (heat stress, water outage, disease spread, predator/disturbance) shows up first in aggregate activity. The data is already collected.
- **Data source:** `telemetria.move_state`, `fecha_hora`, joined caravana→animal→lote.
- **Calculation:** Daily mean and rest% per lote (downsample; raw is 288 rows/animal/day). Flag when today deviates > 2σ from the lote's trailing 30 d, reusing the sigma logic of the per-animal indicator (`SIGMA_AMARILLO/ROJO_MOVIMIENTO`).
- **Visualization:** Line chart per lote with a shaded normal band; anomaly markers.
- **Filters:** Lote, window.
- **Action/insight enabled:** "Lote Norte's activity dropped 25% since Tuesday with no individual alert yet: check water/heat."
- **Data availability:** Derived. **Needs a pre-aggregation** (daily rollup table or materialized view) to avoid scanning millions of rows. **Complexity:** **High**.

### Idea 13: Reincidencia y animales problemáticos

- **What it shows:** Animals with ≥2 salud alerts in 30 days, repeated `posibleCausa`, repeated discard of the same animal, or repeated out-of-perimeter events.
- **Why it matters:** Recurrence signals a chronic problem (or a defective collar producing noise). Repeated `FUERA_DE_PERIMETRO` for the same lote suggests a broken fence.
- **Data source:** `alertas` grouped by animal/lote and `posible_causa`.
- **Calculation:** counts per animal in a sliding window; per-lote perimeter-exit counts.
- **Visualization:** Ranked table with count badges; for perimeter, a point density on the map.
- **Filters:** Type, window.
- **Action/insight enabled:** Cull/re-evaluate a chronic animal; inspect the fence at the exit point.
- **Data availability:** Derived. **Complexity:** **Medium**.

### Idea 14: Desempeño reproductivo

- **What it shows:** Heat-detection follow-through and outcomes: % of confirmed celo alerts followed by an INSEMINACION within the window; inseminated → GESTANTE conversion; average days open; calving interval.
- **Why it matters:** Converts alerts into the reproductive KPIs that a rancher actually manages by.
- **Data source:** `alertas` (CELO/CONFIRMADA), `intervenciones` (INSEMINACION with `fecha_hora`), `estados_reproductivos` history (state transitions, `fecha_ultimo_parto` changes).
- **Calculation:** follow-through = celo CONFIRMADA with an INSEMINACION intervention (`alerta_id` link) ÷ celo CONFIRMADA. Conversion = animals INSEMINADA that later have GESTANTE with the same `fechaUltimaInseminacion` ÷ inseminated (approximate; no pregnancy-test date exists, only the date the state was recorded). Calving interval = difference between consecutive distinct `fecha_ultimo_parto` values in the history.
- **Visualization:** Small KPI set with period comparison; funnel from "celo detected → attended → inseminated → gestante".
- **Filters:** Lote, raza, period.
- **Action/insight enabled:** Locate the leak in the funnel (detected but not inseminated vs. inseminated but not conceived).
- **Data availability:** Derived, **only as reliable as manual state updates**; show sample size (n) beside each rate and hide rates when n is small.
- **Complexity:** **High** (history reconstruction).

### Idea 15: Ubicación de los eventos (dónde ocurren)

- **What it shows:** Heatmap of where celo, salud and perimeter alerts occurred.
- **Why it matters:** Answers "where are problems occurring?" with real geography (a corner of the paddock, near a water source, near a fence).
- **Data source:** `alertas.ts_generacion` joined to the nearest `telemetria` row of that animal's caravana; `leaflet.heat` is already in `ActividadTab.tsx`.
- **Calculation:** position at `ts_generacion` ± 5 min; weight by severity.
- **Visualization:** Heatmap layer over the perimeter map, toggle per alert type.
- **Filters:** Type, period, lote.
- **Action/insight enabled:** Find persistent hotspots (e.g., a boggy area with repeated lameness-type deviations).
- **Data availability:** Derived. **Complexity:** **Medium–High**.

### Idea 16: Carga animal y uso del campo

- **What it shows:** Animals per hectare per perimeter, and (from positions) which parts of the perimeter are used vs. ignored.
- **Why it matters:** Overgrazing/undergrazing is a core management lever; the polygon and positions are both already stored.
- **Data source:** `perimetro.area` (PostGIS `ST_Area(geography)`), animals per lote, `telemetria` positions.
- **Calculation:** `animales_en_lote ÷ ha`; a grid of position counts within the polygon over 14–30 days → underused cells. (This is what `ml-geo` intended but is not wired; compute in PostGIS instead of depending on it.)
- **Visualization:** KPI per perimeter + density grid on the map.
- **Filters:** Perimeter/lote, period.
- **Action/insight enabled:** Rotate cattle out of an overused paddock; open unused sections.
- **Data availability:** Derived. Ignores forage quality and weather. **Complexity:** **High**.

### Idea 17: Ganancia de peso (GDP)

- **What it shows:** Average daily gain per animal/lote from `registros_peso`, highlighting animals losing or not gaining weight.
- **Why it matters:** Weight loss is one of the earliest productivity signals and can corroborate a salud alert.
- **Data source:** `registros_peso` (`fecha`, `valor_kg`).
- **Calculation:** `(peso_último − peso_anterior) ÷ días` per animal; lote median; require ≥2 weighings ≥14 days apart.
- **Visualization:** Dot plot per lote (each dot = animal) with median line.
- **Filters:** Lote, raza, period.
- **Action/insight enabled:** Identify animals to supplement or examine.
- **Data availability:** Derived; **sparsest data in the system** (fully manual). Show only when coverage is adequate, else hide.
- **Complexity:** **Medium**.

---

## Future / Requires Additional Data

| Idea | Value | What is missing | Notes |
|---|---|---|---|
| **Abigeato (theft) detection** | High | A producer of `ABIGEATO` alerts. `ml-geo` exists but is never called. | Needs wiring `ml-geo` (or a rule: animals leaving the perimeter *together at night*). Until then, `FUERA_DE_PERIMETRO` is the only real geo signal and must be presented as such. |
| **Aislamiento del rebaño (cohesion index)** | Medium | Same `ml-geo` wiring | An isolated animal is an early sickness/calving sign. Endpoint spec already exists (`indice_cohesion`, `alerta_aislamiento`). |
| **Reproductive KPIs restricted to females** | High | **Sex / category** on `Animal` (vaca, vaquillona, toro, ternero) | Without it, "monitored" includes bulls and steers. Every reproductive denominator is inflated. Recommend adding first. |
| **Mortalidad y bajas** | High | `fecha_baja` and `motivo_baja` on `Animal` (`INACTIVO` has neither date nor cause) | Needed for mortality rate, culling, and sanitary incidence. |
| **Terneros / natalidad / destete** | High | Calf records linked to the dam, birth outcome, weaning weight | Only `fecha_nacimiento` exists; no dam-offspring link or calving outcome. |
| **Diagnóstico de preñez** | Medium | Pregnancy-test date and result | Makes "inseminated → conceived" exact instead of approximate. |
| **Enfermedades por tipo / tratamientos estructurados** | Medium | Structured diagnosis and treatment catalogs (currently free text) | Enables incidence by disease, withdrawal periods. |
| **Estrés térmico / clima** | Medium | Weather source (temperature, humidity) | Would explain herd-wide activity drops (Idea 12). Do not fake with a constant. |
| **Pasturas, agua, alimentación** | Medium | Manual inputs or sensors | Not in the model. |
| **Costos y rentabilidad** | Medium | Financial data | Not in the model. |
| **Calendario sanitario** | Medium | Vaccination schedule per category | Needed for real "overdue" (Idea 11 only shows "days since"). |
| **Condición corporal / producción de leche** | Low–Medium | New measurements | The system is heat/health/location focused; do not assume milk records. |

---

## Recommended Dashboard Structures

### Structure A: "Centro de mando diario" (recommended default)

For the person opening the app in the morning or in the field (productor, empleado, veterinario).

- **Above the fold (left ~⅔):** **Idea 1**, the action queue, as the visual anchor, with **Idea 2** heat-window bars pinned directly above it when any window is open. On the right: a single **trust strip** from Idea 4 (coverage %, "N collars silent") so the rancher can judge whether an empty queue is good news.
- **Key KPIs (max 4, small):** Pending alerts by type (with `FUERA_DE_PERIMETRO` included), windows open now, animals bajo observación, monitoring coverage. No "total animals".
- **Prominent:** the queue; the compact live map (Idea 5) beside or below it.
- **Secondary (below the fold):** embudo reproductivo (Idea 6), 8-week trend (Idea 8), lote comparison (Idea 10).
- **Drill-down:** every row/segment links to the existing pages (alert detail sidebar, `AnimalDetailPage` timeline, `MapPage`, `AnimalesPage` pre-filtered by lote/estado). Lote is a global filter carried along the click path.
- **Live behavior:** subscribe to `/alertas/stream` (SSE) so new alerts push into the queue.

### Structure B: "Salud y reproducción" (manager / veterinario)

For weekly herd-management review.

- **Above the fold:** left = embudo reproductivo (Idea 6) with its exception lists; right = sanidad (Idea 3). Top strip: 4 KPIs, namely días abiertos promedio, ventanas perdidas (30 d) vs. previous, alertas de salud sin atender, animales en calibración.
- **Prominent:** Partos esperados (Idea 7) and reproductive performance funnel (Idea 14), then reincidencia (Idea 13).
- **Secondary:** GDP (Idea 17, only if data exists), tiempo desde última intervención (Idea 11), response/quality (Idea 9).
- **Drill-down:** funnel segment → animal list → animal detail (states history, intervenciones timeline).
- **Requires:** the backend aggregate endpoints for reproduction; heavy reliance on manual data completeness, so each panel must show its data-completeness ("87% of animals have a recorded state").

### Structure C: "Resumen ejecutivo del establecimiento" (owner, multi-farm)

For an owner checking in briefly, possibly across several establecimientos.

- **Above the fold:** one row per establecimiento (or lote when a single farm is selected) with 5 columns: coverage %, pending alerts, alerts/100 animals vs. previous period, ventanas perdidas, % vacías > 90 d; each cell colored only when it deviates from a threshold. This is Idea 10 promoted to the hero.
- **Prominent:** the 8-week alert trend (Idea 8) and response-time KPIs (Idea 9), because the owner mostly wants to know "is the team acting on things, and is it getting better or worse?"
- **Secondary:** herd activity early warning (Idea 12, advanced), map of hotspots (Idea 15), carga animal (Idea 16).
- **Drill-down:** row → Structure A filtered to that establecimiento/lote.
- **Caveat:** the establecimiento selector is currently global; a multi-farm row needs one aggregate call per establecimiento or a new endpoint.

---

## Final Recommendations

**Build first (in order):**

1. **Backend aggregation endpoints.** Nothing on the dashboard should be computed by downloading all animals/alerts in the browser (as `DashboardPage.tsx` does today). Suggest `GET /api/v1/dashboard/atencion`, `/cobertura`, `/reproduccion`, `/tendencias` scoped by `establecimientoId` (+ optional `loteId`).
2. **Fix `getResumen` so `FUERA_DE_PERIMETRO` is counted**, and stop presenting `ABIGEATO` as if it were detectable.
3. **Ideas 1, 2, 4 and 3** (attention queue, heat windows, monitoring coverage, sanidad): they are built on data that is automatic, timestamped and reliable, and they map directly to "what do I do today".
4. **Ideas 5 and 6** (live map, reproductive funnel), then the Useful tier.

**Design principles derived from what the data actually is:**

- **Time-critical first.** Heat windows and unattended alerts age by the hour; sort by remaining time, not by count.
- **Always surface trust in the data**: coverage of collars, animals in calibration, animals with no recorded reproductive state. An empty panel must be distinguishable from "no data".
- **Manual data is sparse**: weights, reproductive states, health statuses and interventions depend on people typing. Show sample sizes (n) and hide derived rates when n is small instead of drawing a confident chart.
- **Normalize before comparing** (per 100 animals, per lote) and always show the previous period next to the current one.
- **Drop:** total animal counts, the "próximos a celo" tile, the 7/30-day celo counters as standalone tiles (replace with the trend chart), decorative pies.

**Quick wins for data quality that unlock more dashboard value:** add **sex/category** to `Animal`; add **baja date/reason**; normalize **DevEUI** casing (silent uplink loss shrinks coverage without anyone noticing, per `pending.md`); wire (or drop) **ml-geo**.
