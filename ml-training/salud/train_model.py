"""
train_model.py (Salud)
=======================
Entrena el modelo HÍBRIDO de detección de desvíos sanitarios por perfil
individual: features estadísticas de desvío (features_salud.py, perfil de
calibración de 7 días por RF03) + un clasificador de ensamble de árboles
(RandomForestClassifier) sobre esas features, en vez de una red neuronal
sobre la secuencia cruda.

Por qué RandomForest y no una LSTM (a diferencia de ml-training/celo):
1. La entrada ya no es "una secuencia cruda de 288 pasos poblacional",
   es un vector fijo de features de desvío YA relativas al individuo. No
   hay una dependencia secuencial que una red recurrente deba aprender:
   el trabajo de "recordar la línea base" lo hace features_salud.py, no
   la red.
2. Reproducibilidad: un ensamble de árboles tiene, para el mismo dataset
   y los mismos hiperparámetros, muchísima menos varianza entre semillas
   de inicialización que una red neuronal entrenada con descenso de
   gradiente estocástico. Esto es exactamente lo que se pidió ("lo más
   reproducible posible").
3. class_weight='balanced' es soporte nativo de scikit-learn para este
   estimador (a diferencia de gradient boosting), así que no hace falta
   reimplementar la lógica de pesos manual que sí fue necesaria para la
   LSTM de celo.

Aun así, se mantiene el mismo estándar de rigor metodológico que
ml-training/celo/train_multiseed.py: partición por animal
(GroupShuffleSplit, sin fuga de información), semillas fijas, y 5
corridas con distinta semilla de inicialización del estimador sobre la
MISMA partición de datos, reportando media ± desvío estándar.

Salida del modelo: severidad 0-3 (igual que la versión anterior), más un
PR-AUC / umbral óptimo para la métrica binaria "desvío detectado"
(severidad > 0), siguiendo el mismo criterio de celo (curva
Precisión-Recall en vez de accuracy, umbral por defecto de 0.5 vs. umbral
óptimo de F1) para que ambos microservicios reporten resultados
comparables entre sí.

Uso:
    python3 train_model.py --dataset-dir dataset --models-dir models
"""

import argparse
import json
import os
import random
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    precision_recall_curve,
    auc,
)
import joblib

from features_salud import (
    FEATURE_NAMES,
    CALIB_DIAS_MINIMO,
    compute_baseline,
    compute_features,
    ErrorCalibracionInsuficiente,
)

SAMPLES_PER_DAY = 288
CALIB_TICKS = CALIB_DIAS_MINIMO * SAMPLES_PER_DAY


def set_all_seeds(seed: int):
    random.seed(seed)
    np.random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)


def build_feature_table(dataset_dir: Path):
    """Reconstruye, por animal, el perfil de calibración (primeros
    CALIB_TICKS puntos) y una fila de features por cada día completo de
    evaluación posterior, con su etiqueta de severidad (máxima severidad
    real alcanzada ese día)."""
    print("Cargando dataset crudo (parquet)...")
    df = pd.read_parquet(dataset_dir / "telemetria.parquet")
    df = df.sort_values(by=["caravana_id", "tick"])

    X_list, y_list, groups_list = [], [], []
    animales_descartados = 0

    for caravana_id, g in df.groupby("caravana_id"):
        g = g.reset_index(drop=True)
        if len(g) < CALIB_TICKS + SAMPLES_PER_DAY:
            # No hay ni calibración completa ni un solo día de evaluación.
            animales_descartados += 1
            continue

        calib = g.iloc[:CALIB_TICKS]
        try:
            baseline = compute_baseline(
                calib["moveState"].to_numpy(),
                calib["latitud"].to_numpy(),
                calib["longitud"].to_numpy(),
            )
        except ErrorCalibracionInsuficiente:
            animales_descartados += 1
            continue

        evaluacion = g.iloc[CALIB_TICKS:]
        n_dias_eval = len(evaluacion) // SAMPLES_PER_DAY

        for i in range(n_dias_eval):
            dia = evaluacion.iloc[i * SAMPLES_PER_DAY:(i + 1) * SAMPLES_PER_DAY]
            feats = compute_features(
                dia["moveState"].to_numpy(),
                dia["latitud"].to_numpy(),
                dia["longitud"].to_numpy(),
                baseline,
            )
            label = int(dia["severidad"].max())
            X_list.append(feats)
            y_list.append(label)
            groups_list.append(caravana_id)

    if animales_descartados:
        print(f"Advertencia: {animales_descartados} animales descartados por no cubrir "
              f"{CALIB_DIAS_MINIMO} días de calibración + al menos 1 día de evaluación.")

    X = np.array(X_list)
    y = np.array(y_list)
    groups = np.array(groups_list)
    return X, y, groups


def group_split(X, y, groups, test_size=0.2, seed=42):
    gss = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=seed)
    train_idx, test_idx = next(gss.split(X, y, groups))
    return train_idx, test_idx


def best_threshold_from_pr(y_true_bin, y_proba):
    precisions, recalls, thresholds = precision_recall_curve(y_true_bin, y_proba)
    pr_auc = auc(recalls, precisions)
    if len(thresholds) == 0:
        return 0.5, pr_auc
    f1s = np.zeros(len(thresholds))
    for i, t in enumerate(thresholds):
        p, r = precisions[i], recalls[i]
        f1s[i] = 0.0 if (p + r) == 0 else 2 * p * r / (p + r)
    best_idx = int(np.argmax(f1s))
    return float(thresholds[best_idx]), pr_auc


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", type=str, default="dataset")
    parser.add_argument("--models-dir", type=str, default="models")
    parser.add_argument("--split-seed", type=int, default=42,
                         help="Semilla fija de partición de datos (GroupShuffleSplit)")
    parser.add_argument("--init-seeds", type=int, nargs="+", default=[42, 43, 44, 45, 46],
                         help="5 semillas del estimador, misma partición")
    parser.add_argument("--n-estimators", type=int, default=300)
    parser.add_argument("--max-depth", type=int, default=8)
    args = parser.parse_args()

    d = Path(args.dataset_dir)
    X, y, groups = build_feature_table(d)
    print(f"Dataset de features reconstruido: X shape {X.shape}, y shape {y.shape}, "
          f"animales únicos = {len(np.unique(groups))}")
    unique, counts = np.unique(y, return_counts=True)
    print("Distribución de clases (días de evaluación):")
    for u, c in zip(unique, counts):
        print(f"  Severidad {u}: {c}")

    train_idx, test_idx = group_split(X, y, groups, seed=args.split_seed)
    X_train, y_train = X[train_idx], y[train_idx]
    X_test, y_test = X[test_idx], y[test_idx]
    print(f"Train: {len(X_train)} | Test: {len(X_test)} "
          f"(animales train={len(np.unique(groups[train_idx]))}, test={len(np.unique(groups[test_idx]))})")

    models_dir = Path(args.models_dir)
    models_dir.mkdir(parents=True, exist_ok=True)
    run_group_id = datetime.now().strftime("%Y%m%d_%H%M%S") + f"_split{args.split_seed}"
    group_dir = models_dir / run_group_id
    group_dir.mkdir(parents=True, exist_ok=True)

    per_run_metrics = []
    target_names_all = ["Normal (0)", "Baja (1)", "Media (2)", "Alta (3)"]

    for init_seed in args.init_seeds:
        print(f"\n========== Corrida con init_seed={init_seed} ==========")
        set_all_seeds(init_seed)

        clf = RandomForestClassifier(
            n_estimators=args.n_estimators,
            max_depth=args.max_depth,
            class_weight="balanced",
            random_state=init_seed,
            n_jobs=-1,
        )
        clf.fit(X_train, y_train)

        y_pred = clf.predict(X_test)
        y_proba = clf.predict_proba(X_test)  # columnas en el orden de clf.classes_

        # Métrica binaria "desvío detectado" (severidad > 0), comparable a
        # la clase "celo" del otro modelo: PR-AUC + umbral óptimo de F1.
        clase0_idx = list(clf.classes_).index(0) if 0 in clf.classes_ else None
        if clase0_idx is not None:
            desvio_proba = 1.0 - y_proba[:, clase0_idx]
        else:
            desvio_proba = np.ones(len(y_test))
        y_test_bin = (y_test > 0).astype(int)
        best_thr, pr_auc_desvio = best_threshold_from_pr(y_test_bin, desvio_proba)
        y_pred_bin_default = (desvio_proba > 0.5).astype(int)
        y_pred_bin_best = (desvio_proba > best_thr).astype(int)

        test_classes = np.unique(np.concatenate((y_test, y_pred)))
        target_names_test = [target_names_all[i] for i in test_classes]
        report_multiclase = classification_report(
            y_test, y_pred, labels=test_classes, target_names=target_names_test,
            output_dict=True, zero_division=0,
        )
        cm_multiclase = confusion_matrix(y_test, y_pred, labels=[0, 1, 2, 3])

        report_bin_default = classification_report(
            y_test_bin, y_pred_bin_default, target_names=["sin_desvio", "desvio"],
            output_dict=True, zero_division=0,
        )
        report_bin_best = classification_report(
            y_test_bin, y_pred_bin_best, target_names=["sin_desvio", "desvio"],
            output_dict=True, zero_division=0,
        )

        run_id = f"init{init_seed}"
        run_dir = group_dir / run_id
        run_dir.mkdir(parents=True, exist_ok=True)
        joblib.dump(clf, run_dir / "modelo_salud_rf.joblib")

        run_summary = {
            "run_id": run_id,
            "split_seed": args.split_seed,
            "init_seed": init_seed,
            "n_train": len(X_train),
            "n_test": len(X_test),
            "n_estimators": args.n_estimators,
            "max_depth": args.max_depth,
            "test_classification_report_multiclase": report_multiclase,
            "test_confusion_matrix_multiclase": cm_multiclase.tolist(),
            "pr_auc_desvio": pr_auc_desvio,
            "best_threshold_desvio_f1": best_thr,
            "test_classification_report_desvio_thr0.5": report_bin_default,
            "test_classification_report_desvio_thr_best": report_bin_best,
        }
        with open(run_dir / "run_summary.json", "w") as f:
            json.dump(run_summary, f, indent=2, ensure_ascii=False)

        f1_macro = report_multiclase["macro avg"]["f1-score"]
        acc = report_multiclase["accuracy"]
        print(f"  Multiclase -> accuracy={acc:.3f} f1_macro={f1_macro:.3f}")
        print(f"  Desvío (bin) -> PR-AUC={pr_auc_desvio:.4f} umbral_optimo={best_thr:.3f} "
              f"precision={report_bin_best['desvio']['precision']:.3f} "
              f"recall={report_bin_best['desvio']['recall']:.3f} "
              f"f1={report_bin_best['desvio']['f1-score']:.3f}")

        per_run_metrics.append({
            "init_seed": init_seed,
            "accuracy_multiclase": acc,
            "f1_macro_multiclase": f1_macro,
            "pr_auc_desvio": pr_auc_desvio,
            "best_threshold_desvio": best_thr,
            "precision_desvio_thr05": report_bin_default["desvio"]["precision"],
            "recall_desvio_thr05": report_bin_default["desvio"]["recall"],
            "f1_desvio_thr05": report_bin_default["desvio"]["f1-score"],
            "precision_desvio_best": report_bin_best["desvio"]["precision"],
            "recall_desvio_best": report_bin_best["desvio"]["recall"],
            "f1_desvio_best": report_bin_best["desvio"]["f1-score"],
        })

    def agg(key):
        vals = np.array([m[key] for m in per_run_metrics])
        return float(vals.mean()), float(vals.std())

    summary_agg = {
        "split_seed": args.split_seed,
        "init_seeds": args.init_seeds,
        "n_runs": len(args.init_seeds),
        "n_test": len(X_test),
        "n_desvio_test": int(y_test_bin.sum()),
        "feature_names": FEATURE_NAMES,
    }
    for key in ["accuracy_multiclase", "f1_macro_multiclase", "pr_auc_desvio", "best_threshold_desvio",
                "precision_desvio_thr05", "recall_desvio_thr05", "f1_desvio_thr05",
                "precision_desvio_best", "recall_desvio_best", "f1_desvio_best"]:
        mean_v, std_v = agg(key)
        summary_agg[key] = {"mean": mean_v, "std": std_v}

    with open(group_dir / "summary_5seeds.json", "w") as f:
        json.dump({"aggregate": summary_agg, "per_run": per_run_metrics}, f, indent=2, ensure_ascii=False)

    print("\n\n================ RESUMEN 5 CORRIDAS (media ± desvío) ================")
    for key in ["accuracy_multiclase", "f1_macro_multiclase", "pr_auc_desvio",
                "precision_desvio_best", "recall_desvio_best", "f1_desvio_best", "best_threshold_desvio"]:
        m = summary_agg[key]
        print(f"{key:28s}: {m['mean']:.4f} ± {m['std']:.4f}")

    # Se exporta a producción el modelo de la primera semilla del grupo, ya
    # que las 5 corridas reportadas son sobre la MISMA partición y sirven
    # para caracterizar varianza, no para elegir "la mejor" (ver mismo
    # criterio documentado en ml-training/celo/train_multiseed.py).
    export_run = group_dir / f"init{args.init_seeds[0]}"
    import shutil
    export_dir = Path(__file__).resolve().parent.parent.parent / "ml-salud" / "app" / "models"
    export_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(export_run / "modelo_salud_rf.joblib", export_dir / "modelo_salud_rf.joblib")
    shutil.copy(Path(__file__).resolve().parent / "features_salud.py", export_dir.parent / "features_salud.py")

    with open(models_dir / "latest.json", "w") as f:
        json.dump({"latest_run_group": run_group_id, "path": str(group_dir),
                    "exported_from": str(export_run)}, f, indent=2)

    print(f"\nResumen completo guardado en: {group_dir / 'summary_5seeds.json'}")
    print(f"Modelo exportado a: {export_dir}")


if __name__ == "__main__":
    main()
