"""
train_baseline_rnn.py (v2)
============================
Entrena una RNN (LSTM) baseline sobre el dataset generado por
generate_dataset.py v2, para detectar celo a partir de ventanas de 24hs.

Cambios respecto a v1:
- Suma pitch_deg e is_monta como features (ahora la ventana es
  (288, 7): accel_x, accel_y, accel_z, magnitud, distancia_recorrida,
  pitch_deg, is_monta), aprovechando la señal de orientación/monta que
  generate_dataset.py v2 ya calcula por tick.
- Los pesos, la normalización y las métricas de cada corrida se guardan en
  una carpeta versionada bajo --models-dir (default "models/"), con un
  nombre que incluye timestamp + seed, y un run_summary.json al lado con
  hiperparámetros y métricas de test. Ver docstring al final del archivo
  para la justificación de este esquema mientras no hay nada desplegado.

Requisitos: pip install tensorflow scikit-learn numpy pandas pyarrow

Uso:
    python3 train_baseline_rnn.py --dataset-dir dataset --models-dir models
"""

import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import classification_report, confusion_matrix


def add_magnitude(X: np.ndarray) -> np.ndarray:
    # Calcula la magnitud solo de los ejes x, y, z (los primeros 3)
    mag = np.sqrt((X[..., :3] ** 2).sum(axis=-1, keepdims=True))
    return np.concatenate([X, mag], axis=-1)


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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", type=str, default="dataset")
    parser.add_argument("--models-dir", type=str, default="models",
                         help="Carpeta raíz donde se guardan los pesos versionados por corrida")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--run-name", type=str, default=None,
                         help="Nombre opcional para identificar la corrida (ej. 'v2-pitch-monta')")
    args = parser.parse_args()

    d = Path(args.dataset_dir)

    print("Cargando dataset crudo (parquet)...")
    df = pd.read_parquet(d / "telemetria.parquet")

    df = df.sort_values(by=["caravana_id", "fecha_hora"])
    df["fecha"] = df["fecha_hora"].dt.date
    grouped = df.groupby(["caravana_id", "fecha"])

    X_list = []
    y_list = []
    groups_list = []

    print("Reconstruyendo secuencias...")
    for (caravana_id, fecha), group in grouped:
        if len(group) != 288:
            continue  # Descartar días incompletos

        accel = group[["accel_x", "accel_y", "accel_z"]].to_numpy(dtype=np.float32)

        lat = group["latitud"].to_numpy()
        lon = group["longitud"].to_numpy()
        dist_eucl = np.sqrt(np.diff(lat) ** 2 + np.diff(lon) ** 2) * 100000
        dist_arr = np.concatenate([[0], dist_eucl]).astype(np.float32)

        # pitch_deg e is_monta: presentes desde generate_dataset.py v2.
        # Si el dataset viene de una versión vieja sin estas columnas,
        # se completan en cero para no romper compatibilidad hacia atrás.
        if "pitch_deg" in group.columns:
            pitch = group["pitch_deg"].to_numpy(dtype=np.float32)
        else:
            pitch = np.zeros(288, dtype=np.float32)

        if "is_monta" in group.columns:
            monta = group["is_monta"].to_numpy(dtype=np.float32)
        else:
            monta = np.zeros(288, dtype=np.float32)

        # accel(3) + distancia(1) + pitch(1) + monta(1) = 6 features
        # (add_magnitude() suma una 7ma: la magnitud de accel)
        features = np.column_stack([accel, dist_arr, pitch, monta])

        label = int(group["is_celo"].any())

        X_list.append(features)
        y_list.append(label)
        groups_list.append(caravana_id)

    X = np.array(X_list)
    y = np.array(y_list)
    groups = np.array(groups_list)

    print(f"Dataset reconstruido: X shape {X.shape}, y shape {y.shape}")

    X = add_magnitude(X)  # (n, 288, 6) -> (n, 288, 7)

    print("\n--- INSTRUCCIONES DE SPLIT DE DATOS ---")
    print("Se utiliza GroupShuffleSplit agrupando por caravana_id, para que")
    print("los animales usados en Train NUNCA aparezcan en Val ni en Test.")
    print("---------------------------------------\n")

    train_idx, val_idx, test_idx = group_split(X, y, groups, seed=args.seed)
    X_train, y_train = X[train_idx], y[train_idx]
    X_val, y_val = X[val_idx], y[val_idx]
    X_test, y_test = X[test_idx], y[test_idx]

    print(f"Datos de Entrenamiento: {len(X_train)} días/ventanas")
    print(f"Datos de Validación: {len(X_val)} días/ventanas")
    print(f"Datos de Prueba: {len(X_test)} días/ventanas")

    # Normalizacion con estadisticas SOLO de train.
    # NOTA: is_monta es binaria (0/1); normalizarla con z-score no es
    # incorrecto (el modelo igual aprende), pero si prefieren mantenerla
    # binaria interpretable, se puede excluir del z-score y concatenarla
    # sin normalizar. Se deja normalizada por simplicidad/consistencia.
    mean = X_train.mean(axis=(0, 1), keepdims=True)
    std = X_train.std(axis=(0, 1), keepdims=True) + 1e-8
    X_train = (X_train - mean) / std
    X_val = (X_val - mean) / std
    X_test = (X_test - mean) / std

    from tensorflow import keras

    model = build_model(input_shape=X_train.shape[1:])
    model.summary()

    early_stop = keras.callbacks.EarlyStopping(
        monitor="val_loss", patience=5, restore_best_weights=True
    )

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=[early_stop],
        verbose=2,
    )

    y_pred_proba = model.predict(X_test)
    y_pred = (y_pred_proba > 0.5).astype(int).ravel()
    print("\n=== Evaluación en test (animales nunca vistos en train/val) ===")
    report = classification_report(y_test, y_pred, target_names=["no_celo", "celo"], output_dict=True)
    print(classification_report(y_test, y_pred, target_names=["no_celo", "celo"]))
    cm = confusion_matrix(y_test, y_pred)
    print("Matriz de confusión:\n", cm)

    # ------------------------------------------------------------------
    # Guardado versionado de la corrida (pesos + normalización + métricas)
    # ------------------------------------------------------------------
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_id = f"{timestamp}_seed{args.seed}"
    if args.run_name:
        run_id = f"{run_id}_{args.run_name}"

    run_dir = Path(args.models_dir) / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    model.save(run_dir / "modelo_celo_lstm.keras")
    with open(run_dir / "normalizacion.json", "w") as f:
        json.dump({"mean": mean.tolist(), "std": std.tolist()}, f)

    run_summary = {
        "run_id": run_id,
        "timestamp": timestamp,
        "seed": args.seed,
        "epochs_configuradas": args.epochs,
        "epochs_reales": len(history.history["loss"]),
        "batch_size": args.batch_size,
        "n_train": len(X_train),
        "n_val": len(X_val),
        "n_test": len(X_test),
        "feature_order": ["accel_x", "accel_y", "accel_z", "distancia_recorrida",
                           "pitch_deg", "is_monta", "magnitud_accel"],
        "input_shape": list(X_train.shape[1:]),
        "test_classification_report": report,
        "test_confusion_matrix": cm.tolist(),
    }
    with open(run_dir / "run_summary.json", "w") as f:
        json.dump(run_summary, f, indent=2, ensure_ascii=False)

    # Puntero al último modelo entrenado, para no tener que buscar la carpeta
    # a mano cuando otro script (ej. de inferencia) necesite cargar "el último"
    with open(Path(args.models_dir) / "latest.json", "w") as f:
        json.dump({"latest_run_id": run_id, "path": str(run_dir)}, f, indent=2)

    print(f"\nCorrida guardada en: {run_dir}/")
    print(f"  - modelo_celo_lstm.keras")
    print(f"  - normalizacion.json (necesario para inferencia)")
    print(f"  - run_summary.json (hiperparámetros y métricas, para comparar corridas)")
    print(f"Puntero actualizado en: {Path(args.models_dir) / 'latest.json'}")


if __name__ == "__main__":
    main()