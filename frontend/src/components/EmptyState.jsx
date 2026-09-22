import { Icon } from './Icon.jsx';

/**
 * Empty states.
 *
 * Every list in the product can legitimately be empty, and each one says what to do next rather than
 * just "no data" — an empty conversation list with no action is a dead end.
 */
export function EmptyState({ icon = 'chat', title, description, action }) {
  return (
    <div className="empty-state">
      <div className="empty-state__icon">
        <Icon name={icon} size={32} />
      </div>
      <h3>{title}</h3>
      {description ? <p className="text-sm">{description}</p> : null}
      {action}
    </div>
  );
}

export default EmptyState;
