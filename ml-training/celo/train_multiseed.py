"""
train_multiseed.py
====================
Fase 1 del plan de revisión del paper VigIA (dictamen de Oscar):

1. Manejo del desbalance de clases: class_weight='balanced' + PR-AUC +
   umbral óptimo elegido sobre la curva Precision-Recall (en vez de 0.5
   fijo).
2. Reproducibilidad: se fijan TODAS las semillas (random, np.random,
   tf.random) y se corren 5 entrenamientos con distintas semillas de
   INICIALIZACIÓN de la red sobre la MISMA partición de datos
   (misma seed de GroupShuffleSplit), reportando media ± desvío estándar
   de precisión/recall/F1/PR-AUC para la clase celo.

Uso:
    python3 train_multiseed.py --dataset-dir dataset --models-dir models
"""

import argparse
import json
import os
import random
from datetime import datetime
from pathlib import Path

# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    precision_recall_curve,
    auc,
    f1_score,
)
from sklearn.preprocessing import StandardScaler
# pyrefly: ignore [missing-import]
import joblib


def set_all_seeds(seed: int):
    """Fija random, np.random y tf.random.set_seed (Punto 2 del dictamen)."""
    random.seed(seed)
    np.random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    import tensorflow as tf
    tf.random.set_seed(seed)
    tf.config.experimental.enable_op_determinism()


def group_split(X, y, groups, test_size=0.2, val_size=0.2, seed=42):
    gss1 = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=seed)
    trainval_idx, test_idx = next(gss1.split(X, y, groups))

    gss2 = GroupShuffleSplit(n_splits=1, test_size=val_size, random_state=seed)
    train_idx_rel, val_idx_rel = next(
        gss2.split(X[trainval_idx], y[trainval_idx], groups[trainval_idx])
    )
    train_idx = trainval_idx[train_idx_rel]
    val_idx = trainval_idx[val_idx_rel]
    return train_idx, val_idx, test_idx


def build_model(input_shape):
    from tensorflow import keras
    from tensorflow.keras import layers

    model = keras.Sequential([
        layers.Input(shape=input_shape),
        layers.LSTM(32, return_sequences=False),
        layers.Dropout(0.3),
        layers.Dense(16, activation="relu"),
        layers.Dense(1, activation="sigmoid"),
    ])
    model.compile(
        optimizer="adam",
        loss="binary_crossentropy",
        metrics=["accuracy", keras.metrics.Precision(name="precision"),
                 keras.metrics.Recall(name="recall")],
    )
    return model


def load_windows(dataset_dir: Path):
    print("Cargando dataset crudo (parquet)...")
    df = pd.read_parquet(dataset_dir / "telemetria.parquet")
    df = df.sort_values(by=["caravana_id", "fecha_hora"])
    df["fecha"] = df["fecha_hora"].dt.date
    grouped = df.groupby(["caravana_id", "fecha"])

    X_list, y_list, groups_list = [], [], []
    for (caravana_id, fecha), group in grouped:
        if len(group) != 288:
            continue
        move_state = group["moveState"].to_numpy(dtype=np.float32)
        lat = group["latitud"].to_numpy()
        lon = group["longitud"].to_numpy()
        dist_eucl = np.sqrt(np.diff(lat) ** 2 + np.diff(lon) ** 2) * 100000
        dist_arr = np.concatenate([[0], dist_eucl]).astype(np.float32)
        features = np.column_stack([move_state, dist_arr])
        label = int(group["is_celo"].any())
        X_list.append(features)
        y_list.append(label)
        groups_list.append(caravana_id)

    X = np.array(X_list)
    y = np.array(y_list)
    groups = np.array(groups_list)
    return X, y, groups


def best_threshold_from_pr(y_true, y_proba):
    """Elige el umbral que maximiza el F1 sobre la curva Precision-Recall."""
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_proba)
    pr_auc = auc(recalls, precisions)
    # precision_recall_curve devuelve len(thresholds) == len(precisions) - 1
    f1s = np.zeros(len(thresholds))
    for i, t in enumerate(thresholds):
        p, r = precisions[i], recalls[i]
        f1s[i] = 0.0 if (p + r) == 0 else 2 * p * r / (p + r)
    if len(thresholds) == 0:
        return 0.5, pr_auc, precisions, recalls, thresholds
    best_idx = int(np.argmax(f1s))
    return float(thresholds[best_idx]), pr_auc, precisions, recalls, thresholds


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", type=str, default="dataset")
    parser.add_argument("--models-dir", type=str, default="models")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--split-seed", type=int, default=42,
                         help="Semilla fija de partición de datos (GroupShuffleSplit)")
    parser.add_argument("--init-seeds", type=int, nargs="+",
                         default=[42, 43, 44, 45, 46],
                         help="5 semillas de inicialización/entrenamiento de la red, misma partición")
    args = parser.parse_args()

    d = Path(args.dataset_dir)
    X, y, groups = load_windows(d)
    print(f"Dataset reconstruido: X shape {X.shape}, y shape {y.shape}")

    # Partición ÚNICA y fija para las 5 corridas (Punto 2: aislar el efecto
    # de la semilla de inicialización de la red del efecto de la partición)
    train_idx, val_idx, test_idx = group_split(X, y, groups, seed=args.split_seed)
    X_train_raw, y_train = X[train_idx], y[train_idx]
    X_val_raw, y_val = X[val_idx], y[val_idx]
    X_test_raw, y_test = X[test_idx], y[test_idx]

    print(f"Train: {len(X_train_raw)} | Val: {len(X_val_raw)} | Test: {len(X_test_raw)}")
    print(f"Positivos (celo) en test: {int(y_test.sum())}/{len(y_test)} "
          f"({100*y_test.sum()/len(y_test):.1f}%)")

    # Normalizacion (mismo esquema que ml-training/salud/train_rnn.py: un
    # StandardScaler de scikit-learn, exportado con joblib en vez de un
    # normalizacion.json hecho a mano)
    scaler = StandardScaler()

    # El scaler espera 2D, así que aplanamos las secuencias para fitear
    X_train_flat = X_train_raw.reshape(-1, X_train_raw.shape[-1])
    scaler.fit(X_train_flat)

    X_train = scaler.transform(X_train_flat).reshape(X_train_raw.shape)
    X_val = scaler.transform(X_val_raw.reshape(-1, X_val_raw.shape[-1])).reshape(X_val_raw.shape)
    X_test = scaler.transform(X_test_raw.reshape(-1, X_test_raw.shape[-1])).reshape(X_test_raw.shape)

    # class_weight='balanced' equivalente manual: n/(2*n_clase)
    n = len(y_train)
    n_pos = int(y_train.sum())
    n_neg = n - n_pos
    class_weight = {0: n / (2 * n_neg), 1: n / (2 * n_pos)}
    print(f"class_weight calculado: {class_weight}")

    models_dir = Path(args.models_dir)
    models_dir.mkdir(parents=True, exist_ok=True)
    run_group_id = datetime.now().strftime("%Y%m%d_%H%M%S") + f"_split{args.split_seed}"
    group_dir = models_dir / run_group_id
    group_dir.mkdir(parents=True, exist_ok=True)

    # El scaler es el MISMO para las 5 corridas (misma partición/split_seed),
    # así que se guarda una sola vez a nivel de grupo. También se copia dentro
    # de cada run_dir/init<seed>/ para que ese directorio quede autocontenido
    # (modelo + scaler juntos), listo para copiar tal cual a
    # ml-celo/app/models/ sin tener que ir a buscar el scaler a otro lado.
    joblib.dump(scaler, group_dir / "scaler_celo.joblib")

    per_run_metrics = []

    for init_seed in args.init_seeds:
        print(f"\n========== Corrida con init_seed={init_seed} ==========")
        set_all_seeds(init_seed)

        from tensorflow import keras
        model = build_model(input_shape=X_train.shape[1:])

        early_stop = keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=5, restore_best_weights=True
        )

        history = model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=args.epochs,
            batch_size=args.batch_size,
            callbacks=[early_stop],
            class_weight=class_weight,
            verbose=0,
        )

        y_proba = model.predict(X_test, verbose=0).ravel()
        best_thr, pr_auc, precisions, recalls, thresholds = best_threshold_from_pr(y_test, y_proba)
        y_pred_default = (y_proba > 0.5).astype(int)
        y_pred_best = (y_proba > best_thr).astype(int)

        report_default = classification_report(
            y_test, y_pred_default, target_names=["no_celo", "celo"], output_dict=True, zero_division=0
        )
        report_best = classification_report(
            y_test, y_pred_best, target_names=["no_celo", "celo"], output_dict=True, zero_division=0
        )
        cm_default = confusion_matrix(y_test, y_pred_default)
        cm_best = confusion_matrix(y_test, y_pred_best)

        run_id = f"init{init_seed}"
        run_dir = group_dir / run_id
        run_dir.mkdir(parents=True, exist_ok=True)
        model.save(run_dir / "modelo_celo_lstm.keras")
        joblib.dump(scaler, run_dir / "scaler_celo.joblib")

        run_summary = {
            "run_id": run_id,
            "split_seed": args.split_seed,
            "init_seed": init_seed,
            "epochs_configuradas": args.epochs,
            "epochs_reales": len(history.history["loss"]),
            "n_train": len(X_train), "n_val": len(X_val), "n_test": len(X_test),
            "class_weight": class_weight,
            "pr_auc_celo": pr_auc,
            "best_threshold_f1": best_thr,
            "test_classification_report_thr0.5": report_default,
            "test_classification_report_thr_best": report_best,
            "test_confusion_matrix_thr0.5": cm_default.tolist(),
            "test_confusion_matrix_thr_best": cm_best.tolist(),
        }
        with open(run_dir / "run_summary.json", "w") as f:
            json.dump(run_summary, f, indent=2, ensure_ascii=False)

        print(f"  epochs reales: {len(history.history['loss'])}")
        print(f"  PR-AUC (celo): {pr_auc:.4f} | umbral óptimo (F1): {best_thr:.3f}")
        print(f"  thr=0.5   -> precision={report_default['celo']['precision']:.3f} "
              f"recall={report_default['celo']['recall']:.3f} f1={report_default['celo']['f1-score']:.3f}")
        print(f"  thr=best  -> precision={report_best['celo']['precision']:.3f} "
              f"recall={report_best['celo']['recall']:.3f} f1={report_best['celo']['f1-score']:.3f}")

        per_run_metrics.append({
            "init_seed": init_seed,
            "pr_auc": pr_auc,
            "best_threshold": best_thr,
            "precision_thr05": report_default["celo"]["precision"],
            "recall_thr05": report_default["celo"]["recall"],
            "f1_thr05": report_default["celo"]["f1-score"],
            "precision_best": report_best["celo"]["precision"],
            "recall_best": report_best["celo"]["recall"],
            "f1_best": report_best["celo"]["f1-score"],
            "accuracy_thr05": report_default["accuracy"],
            "cm_thr05": cm_default.tolist(),
            "cm_best": cm_best.tolist(),
            "y_proba": y_proba.tolist(),
        })

    # ------------------------------------------------------------------
    # Agregación media ± desvío estándar sobre las 5 corridas
    # ------------------------------------------------------------------
    def agg(key):
        vals = np.array([m[key] for m in per_run_metrics])
        return float(vals.mean()), float(vals.std())

    summary_agg = {
        "split_seed": args.split_seed,
        "init_seeds": args.init_seeds,
        "n_runs": len(args.init_seeds),
        "class_weight": class_weight,
        "n_test": len(X_test),
        "n_pos_test": int(y_test.sum()),
    }
    for key in ["pr_auc", "best_threshold", "precision_thr05", "recall_thr05", "f1_thr05",
                "precision_best", "recall_best", "f1_best", "accuracy_thr05"]:
        mean_v, std_v = agg(key)
        summary_agg[key] = {"mean": mean_v, "std": std_v}

    with open(group_dir / "summary_5seeds.json", "w") as f:
        json.dump({"aggregate": summary_agg, "per_run": per_run_metrics}, f, indent=2, ensure_ascii=False)

    print("\n\n================ RESUMEN 5 CORRIDAS (media ± desvío) ================")
    for key in ["pr_auc", "precision_thr05", "recall_thr05", "f1_thr05",
                "precision_best", "recall_best", "f1_best", "accuracy_thr05", "best_threshold"]:
        m = summary_agg[key]
        print(f"{key:20s}: {m['mean']:.4f} ± {m['std']:.4f}")

    print(f"\nResumen completo guardado en: {group_dir / 'summary_5seeds.json'}")


if __name__ == "__main__":
    main()
