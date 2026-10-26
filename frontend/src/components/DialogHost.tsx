import { Modal, Button } from 'react-bootstrap';
import { useDialogStore } from '../store/dialogStore';
import type { DialogVariant } from '../store/dialogStore';
import './DialogHost.css';

const ICONOS: Record<DialogVariant, { icon: string; color: string }> = {
  info: { icon: 'bi-info-circle-fill', color: '#4DD0E1' },
  success: { icon: 'bi-check-circle-fill', color: '#8CC63F' },
  error: { icon: 'bi-x-circle-fill', color: '#ff6b6b' },
  warning: { icon: 'bi-exclamation-triangle-fill', color: '#FFC700' },
};

const TITULOS_DEFAULT: Record<DialogVariant, string> = {
  info: 'Aviso',
  success: 'Listo',
  error: 'Error',
  warning: 'Confirmar acción',
};

export default function DialogHost() {
  const dialog = useDialogStore((s) => s.queue[0]);
  const close = useDialogStore((s) => s.close);

  if (!dialog) return null;

  const variant: DialogVariant = dialog.variant ?? (dialog.kind === 'confirm' ? 'warning' : 'info');
  const { icon, color } = ICONOS[variant];
  const titulo = dialog.title ?? TITULOS_DEFAULT[variant];
  const esConfirm = dialog.kind === 'confirm';

  // Cerrar con la X, Esc o click afuera equivale a "Cancelar" en un confirm
  const handleHide = () => close(dialog.id, false);

  return (
    <Modal
      key={dialog.id}
      show
      onHide={handleHide}
      centered
      dialogClassName="modal-dialog-vigia"
      backdrop={esConfirm ? 'static' : true}
    >
      <Modal.Header closeButton>
        <Modal.Title>{titulo}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <div className="text-center">
          <i className={`bi ${icon} dialog-vigia-icon`} style={{ color }} />
          <p className="dialog-vigia-message mb-0">{dialog.message}</p>
        </div>
      </Modal.Body>
      <Modal.Footer className="gap-3">
        {esConfirm ? (
          <>
            <Button className="btn-dialog-vigia btn-dialog-vigia-cancel" onClick={handleHide}>
              {dialog.cancelText ?? 'Cancelar'}
            </Button>
            <Button
              className={`btn-dialog-vigia ${dialog.danger ? 'btn-dialog-vigia-danger' : 'btn-dialog-vigia-ok'}`}
              onClick={() => close(dialog.id, true)}
              autoFocus
            >
              {dialog.confirmText ?? 'Confirmar'}
            </Button>
          </>
        ) : (
          <Button className="btn-dialog-vigia btn-dialog-vigia-ok" onClick={() => close(dialog.id, true)} autoFocus>
            {dialog.confirmText ?? 'Aceptar'}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
}
