"""
train_rnn.py (Salud)
====================
Entrena una RNN (LSTM) para clasificar desvíos de salud en 4 niveles de severidad:
0: Normal, 1: Baja, 2: Media, 3: Alta.

Basado en ventanas de 24hs. Implementa manejo de clases desbalanceadas.
Exporta el modelo Keras (.keras) y el escalador (.joblib) para compatibilidad.
"""

import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.utils.class_weight import compute_class_weight
import joblib

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

def build_model(input_shape, num_classes=4):
    from tensorflow import keras
    from tensorflow.keras import layers

    model = keras.Sequential([
        layers.Input(shape=input_shape),
        layers.LSTM(32, return_sequences=False),
        layers.Dropout(0.3),
        layers.Dense(16, activation="relu"),
        layers.Dense(num_classes, activation="softmax"),
    ])
    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-dir", type=str, default="dataset")
    parser.add_argument("--models-dir", type=str, default="models", help="Carpeta raíz donde se guardan los pesos")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--run-name", type=str, default=None)
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
            continue

        move_state = group["moveState"].to_numpy(dtype=np.float32)

        lat = group["latitud"].to_numpy()
        lon = group["longitud"].to_numpy()
        dist_eucl = np.sqrt(np.diff(lat) ** 2 + np.diff(lon) ** 2) * 100000
        dist_arr = np.concatenate([[0], dist_eucl]).astype(np.float32)

        features = np.column_stack([move_state, dist_arr])

        # La severidad del día es la máxima severidad alcanzada en el día
        label = int(group["severidad"].max())

        X_list.append(features)
        y_list.append(label)
        groups_list.append(caravana_id)

    X = np.array(X_list)
    y = np.array(y_list)
    groups = np.array(groups_list)

    print(f"Dataset reconstruido: X shape {X.shape}, y shape {y.shape}")
    print(f"Distribución de clases (ventanas de 24hs):")
    unique, counts = np.unique(y, return_counts=True)
    for u, c in zip(unique, counts):
        print(f"  Severidad {u}: {c}")

    train_idx, val_idx, test_idx = group_split(X, y, groups, seed=args.seed)
    X_train, y_train = X[train_idx], y[train_idx]
    X_val, y_val = X[val_idx], y[val_idx]
    X_test, y_test = X[test_idx], y[test_idx]

    # Calcular class weights usando los datos de train
    classes = np.unique(y_train)
    weights = compute_class_weight('balanced', classes=classes, y=y_train)
    class_weight_dict = {int(c): float(w) for c, w in zip(classes, weights)}
    print(f"Pesos de clase para entrenamiento: {class_weight_dict}")

    # Normalizacion
    from sklearn.preprocessing import StandardScaler
    scaler = StandardScaler()
    
    # El scaler espera 2D, así que aplanamos las secuencias para fitear
    X_train_flat = X_train.reshape(-1, X_train.shape[-1])
    scaler.fit(X_train_flat)
    
    # Aplicar transformación
    X_train = scaler.transform(X_train_flat).reshape(X_train.shape)
    X_val = scaler.transform(X_val.reshape(-1, X_val.shape[-1])).reshape(X_val.shape)
    X_test = scaler.transform(X_test.reshape(-1, X_test.shape[-1])).reshape(X_test.shape)

    from tensorflow import keras

    model = build_model(input_shape=X_train.shape[1:], num_classes=len(unique))
    model.summary()

    early_stop = keras.callbacks.EarlyStopping(
        monitor="val_loss", patience=5, restore_best_weights=True
    )

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        class_weight=class_weight_dict,
        callbacks=[early_stop],
        verbose=2,
    )

    y_pred_proba = model.predict(X_test)
    y_pred = np.argmax(y_pred_proba, axis=1)
    
    target_names = ["Normal (0)", "Baja (1)", "Media (2)", "Alta (3)"]
    # Es posible que alguna clase no esté en el test set, ajustamos target_names
    test_classes = np.unique(np.concatenate((y_test, y_pred)))
    target_names_test = [target_names[i] for i in test_classes]

    print("\n=== Evaluación en test ===")
    report_str = classification_report(y_test, y_pred, target_names=target_names_test)
    report_dict = classification_report(y_test, y_pred, target_names=target_names_test, output_dict=True)
    print(report_str)
    cm = confusion_matrix(y_test, y_pred)
    print("Matriz de confusión:\n", cm)

    # Guardado versionado
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_id = f"{timestamp}_seed{args.seed}"
    if args.run_name:
        run_id = f"{run_id}_{args.run_name}"

    run_dir = Path(args.models_dir) / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    # Guardar red neuronal en keras
    model.save(run_dir / "modelo_salud_lstm.keras")
    
    # Guardar preprocesador en joblib
    joblib.dump(scaler, run_dir / "scaler_salud.joblib")

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
        "feature_order": ["moveState", "distancia_recorrida"],
        "input_shape": list(X_train.shape[1:]),
        "test_classification_report": report_dict,
        "test_confusion_matrix": cm.tolist(),
        "class_weights": class_weight_dict
    }
    with open(run_dir / "run_summary.json", "w") as f:
        json.dump(run_summary, f, indent=2, ensure_ascii=False)

    with open(Path(args.models_dir) / "latest.json", "w") as f:
        json.dump({"latest_run_id": run_id, "path": str(run_dir)}, f, indent=2)

    import shutil
    export_dir = Path(__file__).resolve().parent.parent.parent / "ml-salud" / "app" / "models"
    export_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(run_dir / "modelo_salud_lstm.keras", export_dir / "modelo_salud_lstm.keras")
    shutil.copy(run_dir / "scaler_salud.joblib", export_dir / "scaler_salud.joblib")

    print(f"\nCorrida guardada en: {run_dir}/")
    print(f"Modelo exportado exitosamente a: {export_dir}")

if __name__ == "__main__":
    main()
