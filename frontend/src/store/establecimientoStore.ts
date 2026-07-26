import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export interface EstablecimientoInfo {
  id: number;
  nombre: string;
  activo: boolean;
  latitud?: number;
  longitud?: number;
}

interface EstablecimientoState {
  establecimientos: EstablecimientoInfo[];
  selectedId: number | null;

  setEstablecimientos: (list: EstablecimientoInfo[]) => void;
  setSelectedId: (id: number) => void;
  clear: () => void;
}

export const useEstablecimientoStore = create<EstablecimientoState>()(
  persist(
    (set) => ({
      establecimientos: [],
      selectedId: null,

      setEstablecimientos: (list) =>
        set((state) => {
          // If nothing is selected yet, or the current selection is not in the new list,
          // auto-select the first active one.
          const activeList = list.filter((e) => e.activo);
          const currentStillValid = state.selectedId != null && activeList.some((e) => e.id === state.selectedId);
          return {
            establecimientos: list,
            selectedId: currentStillValid ? state.selectedId : (activeList[0]?.id ?? null),
          };
        }),

      setSelectedId: (id) => set({ selectedId: id }),

      clear: () => set({ establecimientos: [], selectedId: null }),
    }),
    {
      name: 'vigia-establecimiento',
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
