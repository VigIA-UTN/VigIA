"""
train_baseline_rnn.py
======================
Entrena una RNN (LSTM) baseline sobre el dataset generado por
generate_dataset.py, para detectar celo a partir de ventanas de 24hs de
aceleracion (x,y,z).

Requisitos: pip install tensorflow scikit-learn numpy pandas

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
    mag = np.sqrt((X ** 2).sum(axis=-1, keepdims=True))
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
    X = np.load(d / "X.npy")
    y = np.load(d / "y.npy")
    metadata = pd.read_csv(d / "metadata.csv")
    groups = metadata["caravana_id"].to_numpy()

    X = add_magnitude(X)  # (n, 288, 4)

    train_idx, val_idx, test_idx = group_split(X, y, groups, seed=args.seed)
    X_train, y_train = X[train_idx], y[train_idx]
    X_val, y_val = X[val_idx], y[val_idx]
    X_test, y_test = X[test_idx], y[test_idx]

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
