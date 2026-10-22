import { NavLink } from 'react-router-dom';

import { Icon } from './Icon.jsx';
import { Avatar } from './Avatar.jsx';
import { ThemeToggle } from './ThemeToggle.jsx';
import { useNotificationStore } from '../state/notifications.js';
import { useSessionStore } from '../state/session.js';

/**
 * Primary navigation.
 *
 * On desktop it is a narrow rail beside the conversation list; on mobile it becomes the bottom bar, which
 * is the navigation model people already have on a phone. Either way it is real navigation with real
 * links — keyboard and screen-reader users get the same destinations in the same order.
 */
export function NavRail({ onSignOut }) {
  const user = useSessionStore((state) => state.user);
  const unread = useNotificationStore((state) => state.unreadCount);

  return (
    <nav className="nav-rail" aria-label="Primary">
      <div className="nav-rail__top">
        <span className="brand" aria-label="REALCHAT">
          <span className="brand__dot" aria-hidden="true" />
        </span>

        <NavLink
          to="/"
          end
          className="icon-btn"
          aria-label="Conversations"
          title="Conversations"
        >
          <Icon name="chat" size={20} />
        </NavLink>

        <NavLink
          to="/notifications"
          className={({ isActive }) => `icon-btn icon-btn--badged${isActive ? ' active' : ''}`}
          aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
          title="Notifications"
        >
          <Icon name="bell" size={20} />
          {unread > 0 ? (
            <span className="nav-rail__badge" aria-hidden="true">
              {unread > 9 ? '9+' : unread}
            </span>
          ) : null}
        </NavLink>
      </div>

      <div className="nav-rail__bottom">
        <ThemeToggle />
        <NavLink to="/settings" className="icon-btn" aria-label="Settings" title="Settings">
          <Icon name="settings" size={20} />
        </NavLink>
        {user ? (
          <NavLink to={`/u/${user.username}`} className="icon-btn" aria-label="Your profile" title="Your profile">
            <Avatar user={user} size={26} />
          </NavLink>
        ) : null}
        <button type="button" className="icon-btn" onClick={onSignOut} aria-label="Sign out" title="Sign out">
          <Icon name="logout" size={20} />
        </button>
      </div>
    </nav>
  );
}

export default NavRail;
