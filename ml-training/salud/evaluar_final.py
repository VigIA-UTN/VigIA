"""
evaluar_final.py (Salud)
========================
Evaluación final multi-semilla del modelo de salud sobre el conjunto de
PRUEBA (caso ML02 de la planilla de Casos de Prueba, hoja Ciclo 1).

No reentrena: carga los 5 modelos (init<seed>/) de un run de
train_model.py, reconstruye la MISMA partición por animal (split_seed) y
predice como lo hace ml-salud en producción: severidad = argmax de
predict_proba y desvio_detectado = severidad > 0. Sin umbral elegido a
mano (el de train_model.py se elegía sobre prueba y el servicio no lo usa).

Reporta, por semilla y como media ± desvío:
- Severidad 0-3: precisión, recall y F1 por clase, F1 macro y matriz 4x4.
- Desvío detectado (binario): precisión, recall, F1 y matriz 2x2.

Además verifica que el modelo empaquetado en ml-salud/app/models/ sea uno
de los evaluados (mismo .joblib y mismas predicciones sobre prueba) y que
ml-salud use el mismo features_salud.py.

Sale con código 1 si el modelo empaquetado no coincide o si la F1 media de
desvío queda por debajo de --f1-min.

Uso (desde ml-training/salud, con el venv de ml-training):
    python evaluar_final.py
    python evaluar_final.py --run-dir models/20260918_195113_split42
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import joblib
from sklearn.metrics import confusion_matrix, f1_score, precision_recall_fscore_support

SALUD_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SALUD_DIR.parent))

import evaluacion_common as ec  # noqa: E402
from train_model import build_feature_table, group_split  # noqa: E402

CLASES = [0, 1, 2, 3]
ETIQUETAS_SEV = ["Normal (0)", "Baja (1)", "Media (2)", "Alta (3)"]
ETIQUETAS_BIN = ["sin desvío", "desvío"]
MODELO = "modelo_salud_rf.joblib"


def metricas(y_true, y_pred):
    p, r, f1, _ = precision_recall_fscore_support(
        y_true, y_pred, labels=CLASES, average=None, zero_division=0)
    t_bin, p_bin = (y_true > 0).astype(int), (y_pred > 0).astype(int)
    pb, rb, fb, _ = precision_recall_fscore_support(
        t_bin, p_bin, labels=[1], average=None, zero_division=0)
    return {
        "severidad": {
            "por_clase": {str(c): {"precision": float(p[i]), "recall": float(r[i]), "f1": float(f1[i])}
                          for i, c in enumerate(CLASES)},
            "f1_macro": float(f1_score(y_true, y_pred, labels=CLASES, average="macro", zero_division=0)),
            "matriz_confusion": confusion_matrix(y_true, y_pred, labels=CLASES).tolist(),
        },
        "desvio": {
            "precision": float(pb[0]), "recall": float(rb[0]), "f1": float(fb[0]),
            "matriz_confusion": confusion_matrix(t_bin, p_bin, labels=[0, 1]).tolist(),
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=str, default=None,
                        help="Run de train_model.py a evaluar (default: el más reciente de models/)")
    parser.add_argument("--dataset-dir", type=str, default=str(SALUD_DIR / "dataset"))
    parser.add_argument("--servicio-dir", type=str,
                        default=str(SALUD_DIR.parent.parent / "ml-salud" / "app" / "models"),
                        help="Carpeta con el modelo empaquetado en el microservicio")
    parser.add_argument("--split-seed", type=int, default=42)
    parser.add_argument("--f1-min", type=float, default=0.50,
                        help="F1 media mínima de 'desvío detectado' para dar el caso por OK")
    parser.add_argument("--out-dir", type=str, default=str(ec.RESULTADOS_DIR / "salud"))
    args = parser.parse_args()

    run_dir = Path(args.run_dir) if args.run_dir else ec.ultimo_run(SALUD_DIR / "models")
    servicio_dir = Path(args.servicio_dir)
    out_dir = Path(args.out_dir)
    seed_dirs = sorted(run_dir.glob("init*"), key=lambda p: int(p.name[4:]))
    print(f"Run evaluado: {run_dir.name} ({len(seed_dirs)} semillas)")

    X, y, groups = build_feature_table(Path(args.dataset_dir))
    _, test_idx = group_split(X, y, groups, seed=args.split_seed)
    X_test, y_test = X[test_idx], y[test_idx]
    n_por_sev = {str(c): int((y_test == c).sum()) for c in CLASES}
    print(f"Test: {len(y_test)} días, por severidad {n_por_sev}")

    por_semilla, pred_test, hashes = [], {}, {}
    for seed_dir in seed_dirs:
        seed = int(seed_dir.name[4:])
        clf = joblib.load(seed_dir / MODELO)
        # clf.predict = clases_[argmax(predict_proba)], lo mismo que hace ml-salud
        pred_test[seed] = clf.predict(X_test)
        hashes[seed] = ec.sha256_archivo(seed_dir / MODELO)
        m = metricas(y_test, pred_test[seed])
        with open(seed_dir / "run_summary.json", encoding="utf-8") as f:
            cm_train = json.load(f).get("test_confusion_matrix_multiclase")
        m["consistente_con_entrenamiento"] = cm_train == m["severidad"]["matriz_confusion"]
        por_semilla.append({"init_seed": seed, **m})
        print(f"  init{seed}: desvío P={m['desvio']['precision']:.3f} R={m['desvio']['recall']:.3f} "
              f"F1={m['desvio']['f1']:.3f} | F1 macro sev={m['severidad']['f1_macro']:.3f}")

    agregado = {
        "severidad": {
            "por_clase": {str(c): {k: ec.media_desvio([r["severidad"]["por_clase"][str(c)][k] for r in por_semilla])
                                   for k in ("precision", "recall", "f1")} for c in CLASES},
            "f1_macro": ec.media_desvio([r["severidad"]["f1_macro"] for r in por_semilla]),
            "matriz_confusion": ec.agregar_matrices([r["severidad"]["matriz_confusion"] for r in por_semilla]),
        },
        "desvio": {
            **{k: ec.media_desvio([r["desvio"][k] for r in por_semilla]) for k in ("precision", "recall", "f1")},
            "matriz_confusion": ec.agregar_matrices([r["desvio"]["matriz_confusion"] for r in por_semilla]),
        },
    }

    # --- Modelo empaquetado en ml-salud ---
    modelo_srv = servicio_dir / MODELO
    hash_srv = ec.sha256_archivo(modelo_srv)
    seed_srv = ec.buscar_coincidencia(hash_srv, hashes)
    pred_srv = joblib.load(modelo_srv).predict(X_test)
    predicciones_identicas = seed_srv is not None and bool(np.array_equal(pred_srv, pred_test[seed_srv]))
    features_srv = servicio_dir.parent / "features_salud.py"
    features_identicas = (features_srv.exists()
                          and ec.sha256_archivo(features_srv) == ec.sha256_archivo(SALUD_DIR / "features_salud.py"))
    coincide = seed_srv is not None and predicciones_identicas and features_identicas
    verificacion = {
        "modelo_servicio": ec.ruta_legible(modelo_srv),
        "sha256_servicio": hash_srv,
        "sha256_por_semilla": {f"init{s}": h for s, h in hashes.items()},
        "semilla_coincidente": seed_srv,
        "predicciones_test_identicas": predicciones_identicas,
        "features_salud_identico": features_identicas,
        "coincide": coincide,
    }
    desplegado = metricas(y_test, pred_srv)
    print(f"Empaquetado en ml-salud: init{seed_srv} | predicciones idénticas={predicciones_identicas} "
          f"| features_salud.py idéntico={features_identicas}")

    cumple_f1 = agregado["desvio"]["f1"]["mean"] >= args.f1_min
    criterio = {"f1_min_desvio": args.f1_min, "f1_media_desvio": agregado["desvio"]["f1"]["mean"],
                "cumple_f1": cumple_f1, "modelo_coincide": coincide, "cumple": cumple_f1 and coincide}

    ec.guardar_json({
        "modelo": "salud (RandomForest)",
        "comando": ec.comando_invocado(),
        "run_evaluado": run_dir.name,
        "split_seed": args.split_seed,
        "conjunto_prueba": {"n": int(len(y_test)), "por_severidad": n_por_sev,
                            "animales": int(len(np.unique(groups[test_idx])))},
        "decision": "argmax de predict_proba (igual que ml-salud); desvío = severidad > 0",
        "desvio_estandar": "muestral (n-1)",
        "por_semilla": por_semilla,
        "agregado": agregado,
        "modelo_desplegado": {"init_seed": seed_srv, **desplegado},
        "verificacion_empaquetado": verificacion,
        "criterio": criterio,
        "versiones": ec.versiones("numpy", "sklearn"),
    }, out_dir / "metricas.json")

    n_sem = len(por_semilla)
    ec.graficar_matriz(agregado["desvio"]["matriz_confusion"], ETIQUETAS_BIN,
                       f"Salud - desvío detectado, test ({n_sem} semillas)",
                       out_dir / "matriz_confusion_desvio.png")
    ec.graficar_matriz(agregado["severidad"]["matriz_confusion"], ETIQUETAS_SEV,
                       f"Salud - severidad, test ({n_sem} semillas)",
                       out_dir / "matriz_confusion_severidad.png")

    sev = agregado["severidad"]["por_clase"]
    filas_sev = "\n".join(
        f"| {ETIQUETAS_SEV[c]} | {n_por_sev[str(c)]} | {ec.fmt(sev[str(c)]['precision'])} "
        f"| {ec.fmt(sev[str(c)]['recall'])} | {ec.fmt(sev[str(c)]['f1'])} |" for c in CLASES)
    filas_sem = "\n".join(
        f"| init{r['init_seed']} | {r['desvio']['precision']:.3f} | {r['desvio']['recall']:.3f} "
        f"| {r['desvio']['f1']:.3f} | {r['severidad']['f1_macro']:.3f} | {r['desvio']['matriz_confusion']} |"
        for r in por_semilla)
    d = agregado["desvio"]
    md = f"""# Salud (RandomForest): evaluación final sobre prueba

- Run evaluado: `{run_dir.name}` (split_seed {args.split_seed}, semillas {', '.join(str(r['init_seed']) for r in por_semilla)})
- Prueba: {len(y_test)} días-animal, {len(np.unique(groups[test_idx]))} animales que no están en entrenamiento
- Decisión igual a ml-salud: severidad = argmax de `predict_proba`, desvío detectado = severidad > 0 (sin umbral ajustado)
- Media ± desvío estándar muestral (n-1) entre semillas

## Desvío detectado (binario)

| Métrica | Media ± desvío |
|---|---|
| Precisión | {ec.fmt(d['precision'])} |
| Recall | {ec.fmt(d['recall'])} |
| F1 | {ec.fmt(d['f1'])} |

{ec.tabla_matriz_md(d['matriz_confusion'], ETIQUETAS_BIN)}

## Severidad (0-3)

| Clase | Casos en prueba | Precisión | Recall | F1 |
|---|---|---|---|---|
{filas_sev}

F1 macro: {ec.fmt(agregado['severidad']['f1_macro'])}

{ec.tabla_matriz_md(agregado['severidad']['matriz_confusion'], ETIQUETAS_SEV)}

## Por semilla

| Semilla | Precisión desvío | Recall desvío | F1 desvío | F1 macro severidad | Matriz desvío [[TN, FP], [FN, TP]] |
|---|---|---|---|---|---|
{filas_sem}

## Modelo empaquetado en ml-salud

- `ml-salud/app/models/{MODELO}` coincide con **init{seed_srv}**: mismo archivo (sha256 `{hash_srv[:16]}…`), predicciones idénticas sobre los {len(y_test)} días de prueba y mismo `features_salud.py`: **{'sí' if coincide else 'NO'}**
- Ese modelo: desvío P {desplegado['desvio']['precision']:.3f}, R {desplegado['desvio']['recall']:.3f}, F1 {desplegado['desvio']['f1']:.3f}, matriz {desplegado['desvio']['matriz_confusion']}

## Criterio ML02

F1 media de desvío ≥ {args.f1_min} y modelo empaquetado coincidente: **{'cumple' if criterio['cumple'] else 'NO cumple'}** (F1 media {d['f1']['mean']:.3f}).

Comando: `cd ml-training/salud && {ec.comando_invocado()}`
"""
    (out_dir / "resumen.md").write_text(md, encoding="utf-8")
    print(f"\nResultados en {out_dir}")

    if not coincide:
        sys.exit("FALLA: el modelo empaquetado en ml-salud no coincide con ninguno de los evaluados.")
    if not cumple_f1:
        sys.exit(f"FALLA: F1 media de desvío {d['f1']['mean']:.3f} < {args.f1_min}.")
    print("OK: modelo empaquetado coincidente y F1 media de desvío sobre el mínimo.")


if __name__ == "__main__":
    main()
