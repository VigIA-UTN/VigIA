"""
evaluar_final.py (Celo)
=======================
Evaluación final multi-semilla del modelo de celo sobre el conjunto de
PRUEBA (caso ML02 de la planilla de Casos de Prueba, hoja Ciclo 1).

No reentrena: carga los 5 modelos (init<seed>/) de un run de
train_multiseed.py, reconstruye la MISMA partición (split_seed) y, para cada
semilla, elige el umbral de F1 sobre VALIDACIÓN y lo aplica sin tocar a
prueba (el mismo criterio que el entrenamiento). Reporta precisión, recall,
F1, PR-AUC y matriz de confusión por semilla y su media ± desvío.

Además verifica que el modelo empaquetado en ml-celo/app/models/ sea uno de
los evaluados: mismo model.weights.h5 dentro del .keras, mismo scaler y
mismas probabilidades sobre todo el conjunto de prueba. Y lo evalúa con el
CELO_THRESHOLD con el que corre el servicio.

Sale con código 1 si el modelo empaquetado no coincide o si la F1 media
queda por debajo de --f1-min.

Uso (desde ml-training/celo, con el venv de ml-training):
    python evaluar_final.py
    python evaluar_final.py --run-dir models/20261027_105620_split42
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import joblib
from sklearn.metrics import (
    auc,
    confusion_matrix,
    precision_recall_curve,
    precision_recall_fscore_support,
)

CELO_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(CELO_DIR.parent))

import evaluacion_common as ec  # noqa: E402
from train_multiseed import best_threshold_from_pr, group_split, load_windows  # noqa: E402

ETIQUETAS = ["sin celo", "celo"]
MODELO = "modelo_celo_lstm.keras"
SCALER = "scaler_celo.joblib"
PESOS = "model.weights.h5"


def escalar(scaler, X):
    return scaler.transform(X.reshape(-1, X.shape[-1])).reshape(X.shape)


def metricas_binarias(y_true, y_proba, umbral):
    y_pred = (y_proba > umbral).astype(int)
    p, r, f1, _ = precision_recall_fscore_support(
        y_true, y_pred, labels=[1], average=None, zero_division=0)
    precisions, recalls, _ = precision_recall_curve(y_true, y_proba)
    return {
        "umbral": float(umbral),
        "precision": float(p[0]),
        "recall": float(r[0]),
        "f1": float(f1[0]),
        "pr_auc": float(auc(recalls, precisions)),
        "matriz_confusion": confusion_matrix(y_true, y_pred, labels=[0, 1]).tolist(),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=str, default=None,
                        help="Run de train_multiseed.py a evaluar (default: el más reciente de models/)")
    parser.add_argument("--dataset-dir", type=str, default=str(CELO_DIR / "dataset"))
    parser.add_argument("--servicio-dir", type=str,
                        default=str(CELO_DIR.parent.parent / "ml-celo" / "app" / "models"),
                        help="Carpeta con el modelo empaquetado en el microservicio")
    parser.add_argument("--umbral-servicio", type=float, default=0.947,
                        help="CELO_THRESHOLD con el que corre ml-celo (infra/docker-compose.yml)")
    parser.add_argument("--split-seed", type=int, default=42)
    parser.add_argument("--f1-min", type=float, default=0.70,
                        help="F1 media mínima de la clase celo para dar el caso por OK")
    parser.add_argument("--out-dir", type=str, default=str(ec.RESULTADOS_DIR / "celo"))
    args = parser.parse_args()

    import tensorflow as tf

    run_dir = Path(args.run_dir) if args.run_dir else ec.ultimo_run(CELO_DIR / "models")
    servicio_dir = Path(args.servicio_dir)
    out_dir = Path(args.out_dir)
    seed_dirs = sorted(run_dir.glob("init*"), key=lambda p: int(p.name[4:]))
    print(f"Run evaluado: {run_dir.name} ({len(seed_dirs)} semillas)")

    X, y, groups = load_windows(Path(args.dataset_dir))
    _, val_idx, test_idx = group_split(X, y, groups, seed=args.split_seed)
    X_val_raw, y_val = X[val_idx], y[val_idx]
    X_test_raw, y_test = X[test_idx], y[test_idx]
    print(f"Val: {len(y_val)} ({int(y_val.sum())} celo) | Test: {len(y_test)} ({int(y_test.sum())} celo)")

    # La partición reconstruida tiene que ser la misma del entrenamiento.
    with open(run_dir / "summary_5seeds.json", encoding="utf-8") as f:
        summary_train = json.load(f)
    if summary_train["aggregate"]["y_test"] != y_test.tolist():
        sys.exit("La partición de prueba reconstruida no coincide con la del entrenamiento "
                 "(¿cambió el dataset o el split_seed?).")
    train_por_semilla = {r["init_seed"]: r for r in summary_train["per_run"]}

    por_semilla, proba_test, hash_pesos = [], {}, {}
    for seed_dir in seed_dirs:
        seed = int(seed_dir.name[4:])
        model = tf.keras.models.load_model(seed_dir / MODELO)
        scaler = joblib.load(seed_dir / SCALER)
        proba_val = model.predict(escalar(scaler, X_val_raw), verbose=0).ravel()
        proba_test[seed] = model.predict(escalar(scaler, X_test_raw), verbose=0).ravel()
        umbral, _, _, _, _ = best_threshold_from_pr(y_val, proba_val)
        m = metricas_binarias(y_test, proba_test[seed], umbral)
        hash_pesos[seed] = ec.sha256_miembro_zip(seed_dir / MODELO, PESOS)

        ref = train_por_semilla.get(seed, {})
        m["consistente_con_entrenamiento"] = bool(
            np.isclose(m["umbral"], ref.get("best_threshold", np.nan), atol=1e-6)
            and np.isclose(m["f1"], ref.get("f1_best", np.nan), atol=1e-6))
        por_semilla.append({"init_seed": seed, **m})
        print(f"  init{seed}: umbral(val)={umbral:.4f}  P={m['precision']:.3f} R={m['recall']:.3f} "
              f"F1={m['f1']:.3f} PR-AUC={m['pr_auc']:.3f}  cm={m['matriz_confusion']}")

    agregado = {k: ec.media_desvio([r[k] for r in por_semilla])
                for k in ("precision", "recall", "f1", "pr_auc", "umbral")}
    agregado["matriz_confusion"] = ec.agregar_matrices([r["matriz_confusion"] for r in por_semilla])

    # --- Modelo empaquetado en ml-celo ---
    modelo_srv = servicio_dir / MODELO
    hash_srv = ec.sha256_miembro_zip(modelo_srv, PESOS)
    seed_srv = ec.buscar_coincidencia(hash_srv, hash_pesos)
    hash_scaler_srv = ec.sha256_archivo(servicio_dir / SCALER)
    hash_scaler_run = ec.sha256_archivo(run_dir / SCALER)
    model_srv = tf.keras.models.load_model(modelo_srv)
    scaler_srv = joblib.load(servicio_dir / SCALER)
    proba_srv = model_srv.predict(escalar(scaler_srv, X_test_raw), verbose=0).ravel()
    predicciones_identicas = seed_srv is not None and bool(np.array_equal(proba_srv, proba_test[seed_srv]))
    max_dif = (float(np.max(np.abs(proba_srv - proba_test[seed_srv]))) if seed_srv is not None else None)
    coincide = seed_srv is not None and hash_scaler_srv == hash_scaler_run and predicciones_identicas
    verificacion = {
        "modelo_servicio": ec.ruta_legible(modelo_srv),
        "sha256_pesos_servicio": hash_srv,
        "sha256_pesos_por_semilla": {f"init{s}": h for s, h in hash_pesos.items()},
        "semilla_coincidente": seed_srv,
        "scaler_coincide": hash_scaler_srv == hash_scaler_run,
        "sha256_scaler": hash_scaler_srv,
        "predicciones_test_identicas": predicciones_identicas,
        "max_diferencia_probabilidad": max_dif,
        "coincide": coincide,
    }
    desplegado = metricas_binarias(y_test, proba_srv, args.umbral_servicio)
    print(f"Empaquetado en ml-celo: init{seed_srv} | scaler ok={verificacion['scaler_coincide']} "
          f"| predicciones idénticas={predicciones_identicas}")
    print(f"  con CELO_THRESHOLD={args.umbral_servicio}: P={desplegado['precision']:.3f} "
          f"R={desplegado['recall']:.3f} F1={desplegado['f1']:.3f} cm={desplegado['matriz_confusion']}")

    cumple_f1 = agregado["f1"]["mean"] >= args.f1_min
    criterio = {"f1_min": args.f1_min, "f1_media": agregado["f1"]["mean"],
                "cumple_f1": cumple_f1, "modelo_coincide": coincide, "cumple": cumple_f1 and coincide}

    ec.guardar_json({
        "modelo": "celo (LSTM)",
        "comando": ec.comando_invocado(),
        "run_evaluado": run_dir.name,
        "split_seed": args.split_seed,
        "conjunto_prueba": {"n": int(len(y_test)), "positivos": int(y_test.sum()),
                            "animales": int(len(np.unique(groups[test_idx])))},
        "conjunto_validacion": {"n": int(len(y_val)), "positivos": int(y_val.sum())},
        "umbral_elegido_en": "validacion",
        "desvio": "muestral (n-1)",
        "por_semilla": por_semilla,
        "agregado": agregado,
        "modelo_desplegado": {"init_seed": seed_srv, "celo_threshold": args.umbral_servicio, **desplegado},
        "verificacion_empaquetado": verificacion,
        "criterio": criterio,
        "versiones": ec.versiones("numpy", "sklearn", "tensorflow", "keras"),
    }, out_dir / "metricas.json")

    ec.graficar_matriz(agregado["matriz_confusion"], ETIQUETAS,
                       f"Celo - test, media ± desvío ({len(por_semilla)} semillas)",
                       out_dir / "matriz_confusion.png")

    filas = "\n".join(
        f"| init{r['init_seed']} | {r['umbral']:.4f} | {r['precision']:.3f} | {r['recall']:.3f} "
        f"| {r['f1']:.3f} | {r['pr_auc']:.3f} | {r['matriz_confusion']} |" for r in por_semilla)
    md = f"""# Celo (LSTM): evaluación final sobre prueba

- Run evaluado: `{run_dir.name}` (split_seed {args.split_seed}, semillas {', '.join(str(r['init_seed']) for r in por_semilla)})
- Prueba: {len(y_test)} ventanas de 24 h ({int(y_test.sum())} con celo), {len(np.unique(groups[test_idx]))} animales que no están en entrenamiento ni en validación
- Umbral de decisión de cada semilla: el que maximiza F1 en **validación**, aplicado sin cambios a prueba
- Media ± desvío estándar muestral (n-1) entre semillas

## Clase celo

| Métrica | Media ± desvío |
|---|---|
| Precisión | {ec.fmt(agregado['precision'])} |
| Recall | {ec.fmt(agregado['recall'])} |
| F1 | {ec.fmt(agregado['f1'])} |
| PR-AUC | {ec.fmt(agregado['pr_auc'])} |
| Umbral (val) | {ec.fmt(agregado['umbral'], 4)} |

## Matriz de confusión (conteos, media ± desvío)

{ec.tabla_matriz_md(agregado['matriz_confusion'], ETIQUETAS)}

## Por semilla

| Semilla | Umbral | Precisión | Recall | F1 | PR-AUC | Matriz [[TN, FP], [FN, TP]] |
|---|---|---|---|---|---|---|
{filas}

## Modelo empaquetado en ml-celo

- `ml-celo/app/models/{MODELO}` coincide con **init{seed_srv}**: mismo `{PESOS}` (sha256 `{hash_srv[:16]}…`), mismo scaler (`{hash_scaler_srv[:16]}…`), probabilidades idénticas sobre las {len(y_test)} ventanas de prueba: **{'sí' if coincide else 'NO'}**
- Con el umbral del servicio (`CELO_THRESHOLD={args.umbral_servicio}`): precisión {desplegado['precision']:.3f}, recall {desplegado['recall']:.3f}, F1 {desplegado['f1']:.3f}, matriz {desplegado['matriz_confusion']}

## Criterio ML02

F1 media ≥ {args.f1_min} y modelo empaquetado coincidente: **{'cumple' if criterio['cumple'] else 'NO cumple'}** (F1 media {agregado['f1']['mean']:.3f}).

Comando: `cd ml-training/celo && {ec.comando_invocado()}`
"""
    (out_dir / "resumen.md").write_text(md, encoding="utf-8")
    print(f"\nResultados en {out_dir}")

    if not coincide:
        sys.exit("FALLA: el modelo empaquetado en ml-celo no coincide con ninguno de los evaluados.")
    if not cumple_f1:
        sys.exit(f"FALLA: F1 media {agregado['f1']['mean']:.3f} < {args.f1_min}.")
    print("OK: modelo empaquetado coincidente y F1 media sobre el mínimo.")


if __name__ == "__main__":
    main()
