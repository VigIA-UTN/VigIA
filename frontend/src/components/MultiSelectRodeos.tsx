import { useState, useRef, useEffect } from 'react';
import type { RodeoLoteResponse } from '../api/rodeoLotesApi';

interface MultiSelectRodeosProps {
  rodeos: RodeoLoteResponse[];
  selectedIds: number[];
  onChange: (selectedIds: number[]) => void;
}

export default function MultiSelectRodeos({
  rodeos,
  selectedIds,
  onChange,
}: MultiSelectRodeosProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Cerrar dropdown al hacer click afuera
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleToggle = (id: number) => {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter(selectedId => selectedId !== id));
    } else {
      onChange([...selectedIds, id]);
    }
  };

  const allSelected = rodeos.length > 0 && selectedIds.length === rodeos.length;

  const handleToggleAll = () => {
    if (allSelected) {
      onChange([]);
    } else {
      onChange(rodeos.map(r => r.id));
    }
  };

  // Texto a mostrar en el trigger
  const selectedRodeos = rodeos.filter(r => selectedIds.includes(r.id));
  const displayText = selectedRodeos.length === 0
    ? 'Selecciona rodeos'
    : selectedRodeos.map(r => r.nombre).join(', ');

  return (
    <div ref={containerRef} style={{ position: 'relative', width: '100%' }}>
      <div
        onClick={() => setIsOpen(prev => !prev)}
        className="d-flex justify-content-between align-items-center"
        style={{
          backgroundColor: '#FFFFFF',
          border: '1px solid #D0D5DD',
          borderRadius: '8px',
          padding: '10px 14px',
          cursor: 'pointer',
          userSelect: 'none',
          boxShadow: '0 1px 2px rgba(16, 24, 40, 0.05)',
        }}
      >
        <span
          style={{
            color: selectedRodeos.length === 0 ? '#667085' : '#1D2939',
            fontSize: '0.95rem',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            paddingRight: '8px',
          }}
        >
          {displayText}
        </span>
        <i
          className={`bi bi-chevron-${isOpen ? 'up' : 'down'}`}
          style={{ color: '#667085', fontSize: '0.9rem', transition: 'transform 0.2s' }}
        ></i>
      </div>

      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            left: 0,
            right: 0,
            backgroundColor: '#FFFFFF',
            border: '1px solid #E4E7EC',
            borderRadius: '8px',
            boxShadow: '0 4px 16px rgba(16, 24, 40, 0.12)',
            zIndex: 1100,
            maxHeight: '220px',
            overflowY: 'auto',
            padding: '6px',
          }}
        >
          {rodeos.length === 0 ? (
            <div style={{ padding: '8px 12px', color: '#667085', fontSize: '0.85rem' }}>
              No hay rodeos disponibles
            </div>
          ) : (
            <>
              <div
                onClick={handleToggleAll}
                className="d-flex align-items-center gap-2"
                style={{
                  padding: '8px 12px',
                  borderRadius: '6px',
                  cursor: 'pointer',
                  backgroundColor: allSelected ? '#F0F9FF' : 'transparent',
                  transition: 'background-color 0.15s',
                  borderBottom: '1px solid #E4E7EC',
                  marginBottom: '4px'
                }}
                onMouseEnter={e => {
                  if (!allSelected) (e.currentTarget as HTMLElement).style.backgroundColor = '#F9FAFB';
                }}
                onMouseLeave={e => {
                  if (!allSelected) (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                }}
              >
                <input
                  type="checkbox"
                  checked={allSelected}
                  onChange={() => {}} // controlado por onClick del row
                  style={{
                    accentColor: '#13D4FB',
                    cursor: 'pointer',
                    width: '16px',
                    height: '16px',
                  }}
                />
                <span style={{ fontSize: '0.9rem', color: '#344054', fontWeight: allSelected ? 600 : 500 }}>
                  Seleccionar todos los rodeos
                </span>
              </div>
              {rodeos.map(rodeo => {
              const isChecked = selectedIds.includes(rodeo.id);
              return (
                <div
                  key={rodeo.id}
                  onClick={() => handleToggle(rodeo.id)}
                  className="d-flex align-items-center gap-2"
                  style={{
                    padding: '8px 12px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    backgroundColor: isChecked ? '#F0F9FF' : 'transparent',
                    transition: 'background-color 0.15s',
                  }}
                  onMouseEnter={e => {
                    if (!isChecked) (e.currentTarget as HTMLElement).style.backgroundColor = '#F9FAFB';
                  }}
                  onMouseLeave={e => {
                    if (!isChecked) (e.currentTarget as HTMLElement).style.backgroundColor = 'transparent';
                  }}
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => {}} // controlado por onClick del row
                    style={{
                      accentColor: '#13D4FB',
                      cursor: 'pointer',
                      width: '16px',
                      height: '16px',
                    }}
                  />
                  <span style={{ fontSize: '0.9rem', color: '#344054', fontWeight: isChecked ? 600 : 400 }}>
                    {rodeo.nombre}
                  </span>
                </div>
              );
            })}
            </>
          )}
        </div>
      )}
    </div>
  );
}
