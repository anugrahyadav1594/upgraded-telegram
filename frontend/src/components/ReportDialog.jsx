import { useState } from 'react';

import { moderationApi, REPORT_REASONS } from '../api/moderation.js';
import { Modal } from './Modal.jsx';
import { useNotificationStore } from '../state/notifications.js';

/**
 * Reporting.
 *
 * Deliberately explicit: the user picks a reason and may add detail, and the dialog states plainly that
 * the reported person is not told. Nothing is submitted automatically, and the reporter cannot see
 * anybody else's reports.
 */
export function ReportDialog({ open, onClose, targetType, targetUserId, targetMessageId, targetLabel }) {
  const [reason, setReason] = useState('SPAM');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);
  const pushToast = useNotificationStore((state) => state.pushToast);

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await moderationApi.report({
        targetType,
        targetUserId: targetType === 'USER' ? targetUserId : undefined,
        targetMessageId: targetType === 'MESSAGE' ? targetMessageId : undefined,
        reason,
        description: description.trim() || undefined,
      });
      setDone(true);
      pushToast({ title: 'Report submitted', body: 'A moderator will review it.', tone: 'info' });
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setSubmitting(false);
    }
  }

  function close() {
    setReason('SPAM');
    setDescription('');
    setDone(false);
    setError(null);
    onClose();
  }

  return (
    <Modal open={open} title="Report" onClose={close}>
      {done ? (
        <div className="stack">
          <p>Thanks — your report has been recorded. {targetLabel} will not be told who reported them.</p>
          <div className="modal__actions">
            <button type="button" className="btn" onClick={close}>
              Close
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={submit}>
          <p className="text-sm muted">
            Reporting <strong>{targetLabel}</strong>. Reports are reviewed by a moderator; we never contact
            the reported account automatically.
          </p>
          <fieldset>
            <legend>Reason</legend>
            {REPORT_REASONS.map((option) => (
              <label key={option.value} className="checkbox">
                <input
                  type="radio"
                  name="reason"
                  value={option.value}
                  checked={reason === option.value}
                  onChange={() => setReason(option.value)}
                />
                <span className="checkbox__text">{option.label}</span>
              </label>
            ))}
          </fieldset>
          <div className="field">
            <label className="field__label" htmlFor="report-description">
              Anything else? (optional)
            </label>
            <textarea
              id="report-description"
              rows={3}
              maxLength={1000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Add context that would help a moderator…"
            />
            <span className="field__hint">{description.length}/1000</span>
          </div>
          {error ? (
            <p className="alert alert--error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="modal__actions">
            <button type="button" className="btn btn--secondary" onClick={close} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn--danger" disabled={submitting}>
              {submitting ? 'Sending…' : 'Submit report'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}

export default ReportDialog;
