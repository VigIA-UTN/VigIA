"""
train_baseline_rnn.py
======================
Entrena una RNN (LSTM) baseline sobre el dataset generado por
generate_dataset.py, para detectar celo a partir de ventanas de 24hs de
aceleracion (x,y,z).

Requisitos: pip install tensorflow scikit-learn numpy pandas pyarrow

Uso:
    python3 train_baseline_rnn.py --dataset-dir dataset

Que hace, paso a paso:
  1. Carga X (n_samples, 288, 3), y y metadata (para saber a que animal
     pertenece cada muestra).
  2. Separa train/val/test AGRUPANDO POR ANIMAL (caravana_id), para que
     ningun animal aparezca en mas de un conjunto (evita data leakage).
  3. Agrega la magnitud sqrt(x^2+y^2+z^2) como 4ta feature.
  4. Normaliza (z-score) usando SOLO estadisticas del set de train.
  5. Arma una LSTM chica (1 capa + dropout) con salida sigmoide.
  6. Entrena con early stopping sobre val_loss.
  7. Evalua en test: accuracy, precision, recall, F1, matriz de confusion.
"""

import argparse
import json
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
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    d = Path(args.dataset_dir)
    
    print("Cargando dataset crudo (parquet)...")
    df = pd.read_parquet(d / "telemetria.parquet")
    
    # Asegurarnos de que esta ordenado por caravana_id y fecha_hora
    df = df.sort_values(by=["caravana_id", "fecha_hora"])
    
    # Agrupar por caravana y por fecha (día)
    df["fecha"] = df["fecha_hora"].dt.date
    grouped = df.groupby(["caravana_id", "fecha"])
    
    X_list = []
    y_list = []
    groups_list = []
    
    print("Reconstruyendo secuencias...")
    for (caravana_id, fecha), group in grouped:
        if len(group) != 288:
            continue # Descartar días incompletos
            
        accel = group[["accel_x", "accel_y", "accel_z"]].to_numpy(dtype=np.float32)
        
        # Calcular distancia_recorrida (aproximación euclidiana simple escalada)
        # Podria usarse Haversine, pero como es generada de forma plana, sirve de proxy.
        lat = group["latitud"].to_numpy()
        lon = group["longitud"].to_numpy()
        dist_eucl = np.sqrt(np.diff(lat)**2 + np.diff(lon)**2) * 100000 # Escalar a "metros" aprox
        dist_arr = np.concatenate([[0], dist_eucl]).astype(np.float32)
        
        # Juntar aceleración y distancia como 4 features
        features = np.column_stack([accel, dist_arr])
        
        # Si en cualquier momento del día el animal estuvo en celo, se etiqueta el día entero
        label = int(group["is_celo"].any())
        
        X_list.append(features)
        y_list.append(label)
        groups_list.append(caravana_id)
        
    X = np.array(X_list)
    y = np.array(y_list)
    groups = np.array(groups_list)
    
    print(f"Dataset reconstruido: X shape {X.shape}, y shape {y.shape}")

    X = add_magnitude(X)  # Pasa a (n, 288, 5) porque ya traia 4 features

    print("\n--- INSTRUCCIONES DE SPLIT DE DATOS ---")
    print("El modelo requiere Datos de Entrenamiento, Validación y Prueba.")
    print("Para evitar el 'data leakage' (que el modelo memorice a un animal),")
    print("se utiliza `GroupShuffleSplit` agrupando por `caravana_id`.")
    print("De esta forma, los animales usados en Train NUNCA aparecen en Test.")
    print("---------------------------------------\n")

    train_idx, val_idx, test_idx = group_split(X, y, groups, seed=args.seed)
    X_train, y_train = X[train_idx], y[train_idx]
    X_val, y_val = X[val_idx], y[val_idx]
    X_test, y_test = X[test_idx], y[test_idx]

    print(f"Datos de Entrenamiento: {len(X_train)} días/ventanas")
    print(f"Datos de Validación: {len(X_val)} días/ventanas")
    print(f"Datos de Prueba: {len(X_test)} días/ventanas")

    # Normalizacion con estadisticas SOLO de train
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

    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=[early_stop],
        verbose=2,
    )

    y_pred = (model.predict(X_test) > 0.5).astype(int).ravel()
    print("\n=== Evaluación en test (animales nunca vistos en train/val) ===")
    print(classification_report(y_test, y_pred, target_names=["no_celo", "celo"]))
    print("Matriz de confusión:\n", confusion_matrix(y_test, y_pred))

    model.save("modelo_celo_lstm.keras")
    with open("normalizacion.json", "w") as f:
        json.dump({"mean": mean.tolist(), "std": std.tolist()}, f)
    print("\nModelo guardado en modelo_celo_lstm.keras")
    print("Parámetros de normalización guardados en normalizacion.json (necesarios para inferencia)")


if __name__ == "__main__":
    main()
