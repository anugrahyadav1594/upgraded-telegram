import { useThemeStore, THEMES } from '../state/theme.js';
import { Icon } from './Icon.jsx';

const LABELS = {
  light: 'Light theme',
  dark: 'Dark theme',
  system: 'Match system theme',
};

const ICONS = { light: 'sun', dark: 'moon', system: 'monitor' };

/** Compact three-way toggle. "System" is a first-class choice, not a hidden default. */
export function ThemeToggle() {
  const theme = useThemeStore((state) => state.theme);
  const setTheme = useThemeStore((state) => state.setTheme);

  return (
    <div className="row" role="group" aria-label="Colour theme">
      {THEMES.map((option) => (
        <button
          key={option}
          type="button"
          className="icon-btn"
          aria-pressed={theme === option}
          aria-label={LABELS[option]}
          title={LABELS[option]}
          onClick={() => setTheme(option)}
        >
          <Icon name={ICONS[option]} size={18} />
        </button>
      ))}
    </div>
  );
}

export default ThemeToggle;
