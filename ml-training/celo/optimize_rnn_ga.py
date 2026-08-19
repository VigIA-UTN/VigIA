"""
Genetic Algorithm for Recurrent Neural Network (RNN) Architecture Optimization
Task: VigIA - Oestrus (Celo) Detection from Accelerometer Time-Series Data

This script automates Neural Architecture Search (NAS) using a Genetic Algorithm (GA).
It optimizes RNN structure (cell type, layer count, layer sizes, regularization, dropout, dense layers, optimizer, learning rate)
balancing classification performance (Precision, Recall, F1 score) and model simplicity (minimizing parameter count).
"""

import os
import sys
import copy
import json
import time
import random
import argparse
import numpy as np

# Suppress Keras / TF info messages
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.callbacks import EarlyStopping
from sklearn.model_selection import StratifiedKFold
from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support

# Define Gene Search Space
GENE_SPACE = {
    "cell_type": ["SimpleRNN", "GRU", "LSTM"],
    "num_rnn_layers": [1, 2, 3],
    "rnn_units_l1": [16, 32, 64, 128],
    "rnn_units_l2": [8, 16, 32, 64],
    "rnn_units_l3": [8, 16, 32],
    "dropout_rate": [0.0, 0.1, 0.2, 0.3, 0.4],
    "use_batch_norm": [True, False],
    "num_dense_layers": [0, 1, 2],
    "dense_units_l1": [8, 16, 32, 64],
    "dense_units_l2": [8, 16, 32],
    "learning_rate": [0.0005, 0.001, 0.005],
    "optimizer": ["adam", "rmsprop"]
}


def random_chromosome():
    """Generates a random chromosome (RNN architecture definition)."""
    return {gene: random.choice(values) for gene, values in GENE_SPACE.items()}


def build_keras_model(chromosome, input_shape):
    """
    Dynamically builds a Keras Sequential RNN model from a chromosome representation.
    """
    cell_type = chromosome["cell_type"]
    num_rnn = chromosome["num_rnn_layers"]
    dropout = chromosome["dropout_rate"]
    use_bn = chromosome["use_batch_norm"]

    cell_map = {
        "SimpleRNN": tf.keras.layers.SimpleRNN,
        "GRU": tf.keras.layers.GRU,
        "LSTM": tf.keras.layers.LSTM
    }
    cell_cls = cell_map[cell_type]

    model = Sequential(name=f"GA_{cell_type}_RNN")
    model.add(tf.keras.layers.Input(shape=input_shape))

    units_list = [
        chromosome["rnn_units_l1"],
        chromosome["rnn_units_l2"],
        chromosome["rnn_units_l3"]
    ][:num_rnn]

    for i, units in enumerate(units_list):
        is_last_rnn = (i == len(units_list) - 1)
        model.add(cell_cls(
            units=units,
            return_sequences=not is_last_rnn,
            name=f"rnn_layer_{i+1}"
        ))
        if use_bn:
            model.add(tf.keras.layers.BatchNormalization(name=f"bn_rnn_{i+1}"))
        if dropout > 0.0:
            model.add(tf.keras.layers.Dropout(dropout, name=f"drop_rnn_{i+1}"))

    num_dense = chromosome["num_dense_layers"]
    dense_units_list = [
        chromosome["dense_units_l1"],
        chromosome["dense_units_l2"]
    ][:num_dense]

    for j, d_units in enumerate(dense_units_list):
        model.add(tf.keras.layers.Dense(d_units, activation="relu", name=f"dense_{j+1}"))
        if use_bn:
            model.add(tf.keras.layers.BatchNormalization(name=f"bn_dense_{j+1}"))
        if dropout > 0.0:
            model.add(tf.keras.layers.Dropout(dropout, name=f"drop_dense_{j+1}"))

    # Output layer
    model.add(tf.keras.layers.Dense(1, activation="sigmoid", name="output_layer"))

    lr = chromosome["learning_rate"]
    opt = tf.keras.optimizers.Adam(learning_rate=lr) if chromosome["optimizer"] == "adam" else tf.keras.optimizers.RMSprop(learning_rate=lr)

    model.compile(
        optimizer=opt,
        loss="binary_crossentropy",
        metrics=["accuracy"]
    )
    return model


def evaluate_chromosome(chromosome, X, y, n_splits=3, epochs=15, batch_size=32):
    """
    Evaluates a chromosome using Stratified K-Fold Cross-Validation.
    Returns fitness score, mean precision, mean recall, mean F1, and total parameters.
    """
    input_shape = (X.shape[1], X.shape[2])
    
    # 1. Count trainable parameters
    try:
        temp_model = build_keras_model(chromosome, input_shape)
        params_count = temp_model.count_params()
        del temp_model
        tf.keras.backend.clear_session()
    except Exception as e:
        return {
            "fitness": 0.0,
            "precision": 0.0,
            "recall": 0.0,
            "f1": 0.0,
            "params": 9999999,
            "error": str(e)
        }

    # 2. Stratified K-Fold evaluation
    skf = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    precisions, recalls, f1s = [], [], []

    for fold, (train_idx, val_idx) in enumerate(skf.split(X, y)):
        X_tr, y_tr = X[train_idx], y[train_idx]
        X_va, y_va = X[val_idx], y[val_idx]

        try:
            model = build_keras_model(chromosome, input_shape)
            early_stop = EarlyStopping(monitor='val_loss', patience=4, restore_best_weights=True, verbose=0)
            model.fit(
                X_tr, y_tr,
                validation_data=(X_va, y_va),
                epochs=epochs,
                batch_size=batch_size,
                callbacks=[early_stop],
                verbose=0
            )

            y_pred_prob = model.predict(X_va, verbose=0)
            y_pred = (y_pred_prob > 0.5).astype(int).ravel()

            # Precision, Recall, F1 for positive class (celo=1)
            p, r, f1, _ = precision_recall_fscore_support(y_va, y_pred, average='binary', zero_division=0)
            precisions.append(p)
            recalls.append(r)
            f1s.append(f1)
        except Exception:
            precisions.append(0.0)
            recalls.append(0.0)
            f1s.append(0.0)
        finally:
            tf.keras.backend.clear_session()

    mean_precision = float(np.mean(precisions))
    mean_recall = float(np.mean(recalls))
    mean_f1 = float(np.mean(f1s))

    # Simplicity penalty: penalize logarithmic order of magnitude of parameter count
    # E.g., 5,000 params -> log10(5001) ~ 3.7; 100,000 params -> log10(100001) ~ 5.0
    simplicity_penalty = 0.02 * np.log10(params_count + 1)

    # Combined Fitness: High Precision, High Recall, High F1 minus simplicity penalty
    fitness = (0.4 * mean_precision) + (0.4 * mean_recall) + (0.2 * mean_f1) - simplicity_penalty
    fitness = float(max(0.0, fitness))

    return {
        "fitness": fitness,
        "precision": mean_precision,
        "recall": mean_recall,
        "f1": mean_f1,
        "params": params_count
    }


def tournament_selection(population, evaluations, k=3):
    """Selects one parent using tournament selection."""
    selected_indices = random.sample(range(len(population)), k)
    best_idx = max(selected_indices, key=lambda idx: evaluations[idx]["fitness"])
    return copy.deepcopy(population[best_idx])


def crossover(parent1, parent2, p_crossover=0.8):
    """Uniform crossover between two parent chromosomes."""
    if random.random() > p_crossover:
        return copy.deepcopy(parent1), copy.deepcopy(parent2)

    child1, child2 = {}, {}
    for gene in GENE_SPACE.keys():
        if random.random() < 0.5:
            child1[gene] = parent1[gene]
            child2[gene] = parent2[gene]
        else:
            child1[gene] = parent2[gene]
            child2[gene] = parent1[gene]
    return child1, child2


def mutate(chromosome, p_mutation=0.15):
    """Mutates random genes in the chromosome."""
    mutated = copy.deepcopy(chromosome)
    for gene, options in GENE_SPACE.items():
        if random.random() < p_mutation:
            mutated[gene] = random.choice(options)
    return mutated


def run_genetic_algorithm(X, y, pop_size=8, generations=6, n_splits=3, epochs=15, batch_size=32):
    """
    Executes the Genetic Algorithm optimization loop.
    """
    print("\n" + "=" * 70)
    print(f"  GENETIC ALGORITHM FOR RNN ARCHITECTURE SEARCH")
    print(f"  • Population Size: {pop_size} | Generations: {generations} | CV Folds: {n_splits}")
    print(f"  • Fast Evaluation Epochs per fold: {epochs}")
    print("=" * 70)

    # 1. Initialize random population
    population = [random_chromosome() for _ in range(pop_size)]
    history = []
    best_overall_individual = None
    best_overall_eval = {"fitness": -1.0}

    for gen in range(1, generations + 1):
        gen_start_time = time.time()
        print(f"\n--- Generation {gen}/{generations} ---")

        # Evaluate population
        evaluations = []
        for i, chromo in enumerate(population):
            eval_res = evaluate_chromosome(chromo, X, y, n_splits=n_splits, epochs=epochs, batch_size=batch_size)
            evaluations.append(eval_res)
            print(f"  [Indiv {i+1:2d}/{pop_size}] Type: {chromo['cell_type']:9s} | Layers: RNN({chromo['num_rnn_layers']})/Dense({chromo['num_dense_layers']}) "
                  f"| Params: {eval_res['params']:6d} | Prec: {eval_res['precision']:.4f} | Rec: {eval_res['recall']:.4f} | F1: {eval_res['f1']:.4f} | Fitness: {eval_res['fitness']:.4f}")

        # Find best in current generation
        best_gen_idx = int(np.argmax([e["fitness"] for e in evaluations]))
        best_gen_chromo = population[best_gen_idx]
        best_gen_eval = evaluations[best_gen_idx]

        if best_gen_eval["fitness"] > best_overall_eval["fitness"]:
            best_overall_individual = copy.deepcopy(best_gen_chromo)
            best_overall_eval = copy.deepcopy(best_gen_eval)

        elapsed = time.time() - gen_start_time
        print(f"  --> Gen {gen} Best: Fitness={best_gen_eval['fitness']:.4f} | Precision={best_gen_eval['precision']:.4f} | Recall={best_gen_eval['recall']:.4f} | F1={best_gen_eval['f1']:.4f} | Params={best_gen_eval['params']} ({elapsed:.1f}s)")

        history.append({
            "generation": gen,
            "best_fitness": best_gen_eval["fitness"],
            "best_precision": best_gen_eval["precision"],
            "best_recall": best_gen_eval["recall"],
            "best_f1": best_gen_eval["f1"],
            "best_params": best_gen_eval["params"],
            "best_chromosome": best_gen_chromo
        })

        if gen == generations:
            break

        # Generate next generation
        new_population = []
        
        # Elitism: retain top 2 individuals
        sorted_indices = np.argsort([e["fitness"] for e in evaluations])[::-1]
        for elite_idx in sorted_indices[:2]:
            new_population.append(copy.deepcopy(population[elite_idx]))

        # Breed remaining population
        while len(new_population) < pop_size:
            p1 = tournament_selection(population, evaluations)
            p2 = tournament_selection(population, evaluations)
            c1, c2 = crossover(p1, p2)
            c1 = mutate(c1)
            c2 = mutate(c2)
            new_population.append(c1)
            if len(new_population) < pop_size:
                new_population.append(c2)

        population = new_population

    print("\n" + "=" * 70)
    print("  OPTIMIZATION COMPLETED!")
    print("=" * 70)
    print("Best Architecture Found:")
    print(json.dumps(best_overall_individual, indent=2))
    print(f"Metrics: Fitness={best_overall_eval['fitness']:.4f} | Precision={best_overall_eval['precision']:.4f} | Recall={best_overall_eval['recall']:.4f} | F1={best_overall_eval['f1']:.4f} | Trainable Params={best_overall_eval['params']}")

    return best_overall_individual, best_overall_eval, history


def train_and_save_final_model(best_chromosome, X, y, output_model_path="optimal_ga_rnn.keras"):
    """
    Trains the optimal architecture found by GA on full train split and tests performance on test split.
    Saves trained model to disk.
    """
    from sklearn.model_selection import train_test_split

    print("\n" + "=" * 70)
    print("  TRAINING FINAL OPTIMAL RNN MODEL (Full Training Set)")
    print("=" * 70)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )

    input_shape = (X_train.shape[1], X_train.shape[2])
    model = build_keras_model(best_chromosome, input_shape)

    model.summary()

    early_stopping = EarlyStopping(
        monitor='val_loss',
        patience=15,
        restore_best_weights=True,
        verbose=1
    )

    history = model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=50,
        batch_size=32,
        callbacks=[early_stopping],
        verbose=1
    )

    test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
    y_pred_prob = model.predict(X_test, verbose=0)
    y_pred = (y_pred_prob > 0.5).astype(int).ravel()

    print("\nFinal Test Set Results:")
    print(f"  • Test Loss:     {test_loss:.4f}")
    print(f"  • Test Accuracy: {test_acc * 100:.2f}%")

    print("\nClassification Report:")
    print(classification_report(y_test, y_pred, target_names=['no_celo', 'celo']))

    cm = confusion_matrix(y_test, y_pred)
    print("Confusion Matrix:")
    print(f"  TN={cm[0,0]}  FP={cm[0,1]}")
    print(f"  FN={cm[1,0]}  TP={cm[1,1]}")

    model.save(output_model_path)
    print(f"\nOptimal model saved to '{output_model_path}'.")


def main():
    parser = argparse.ArgumentParser(description="GA RNN Architecture Optimization")
    parser.add_argument("--pop_size", type=int, default=8, help="Population size for GA")
    parser.add_argument("--generations", type=int, default=6, help="Number of generations")
    parser.add_argument("--folds", type=int, default=3, help="K-Fold Cross Validation splits")
    parser.add_argument("--epochs", type=int, default=15, help="Training epochs per fold during GA search")
    args = parser.parse_args()

    # Load dataset
    print("[1/2] Loading X.npy and y.npy...")
    X = np.load("X.npy")
    y = np.load("y.npy")
    print(f"  • X shape: {X.shape}, y shape: {y.shape}")

    # Run GA search
    best_chrom, best_eval, history = run_genetic_algorithm(
        X, y,
        pop_size=args.pop_size,
        generations=args.generations,
        n_splits=args.folds,
        epochs=args.epochs
    )

    # Save GA search history to JSON
    with open("ga_results.json", "w") as f:
        json.dump({
            "best_chromosome": best_chrom,
            "best_evaluation": best_eval,
            "search_history": history
        }, f, indent=2)
    print("GA search history saved to 'ga_results.json'.")

    # Train best model fully and save keras file
    train_and_save_final_model(best_chrom, X, y, output_model_path="optimal_ga_rnn.keras")


if __name__ == "__main__":
    main()
