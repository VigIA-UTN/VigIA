import { create } from 'zustand';

// Reemplazo de window.alert / window.confirm con modals propios.
// Uso: `await showAlert('mensaje')` o `if (!(await showConfirm('¿Seguro?'))) return;`
// El <DialogHost /> montado en App renderiza el primer diálogo de la cola.

export type DialogVariant = 'info' | 'success' | 'error' | 'warning';

export interface DialogOptions {
  title?: string;
  variant?: DialogVariant;
  confirmText?: string;
  cancelText?: string;
  // Pinta el botón de confirmación en rojo (acciones destructivas)
  danger?: boolean;
}

export interface DialogRequest extends DialogOptions {
  id: number;
  kind: 'alert' | 'confirm';
  message: string;
  resolve: (value: boolean) => void;
}

interface DialogState {
  queue: DialogRequest[];
  push: (req: DialogRequest) => void;
  close: (id: number, value: boolean) => void;
}

let nextId = 1;

export const useDialogStore = create<DialogState>()((set, get) => ({
  queue: [],

  push: (req) => set((state) => ({ queue: [...state.queue, req] })),

  close: (id, value) => {
    const req = get().queue.find((d) => d.id === id);
    if (!req) return;
    set((state) => ({ queue: state.queue.filter((d) => d.id !== id) }));
    req.resolve(value);
  },
}));

export function showAlert(message: string, options: DialogOptions = {}): Promise<void> {
  return new Promise((resolve) => {
    useDialogStore.getState().push({
      ...options,
      id: nextId++,
      kind: 'alert',
      message,
      resolve: () => resolve(),
    });
  });
}

export function showConfirm(message: string, options: DialogOptions = {}): Promise<boolean> {
  return new Promise((resolve) => {
    useDialogStore.getState().push({
      ...options,
      id: nextId++,
      kind: 'confirm',
      message,
      resolve,
    });
  });
}
