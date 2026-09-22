import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { MessageComposer } from './MessageComposer.jsx';
import { MAX_MESSAGE_LENGTH } from '../utils/validation.js';

/**
 * The composer carries the app's most-used interaction, so the keyboard contract is tested explicitly:
 * Enter sends, Shift+Enter does not, and an over-long message cannot be sent at all.
 */
describe('MessageComposer', () => {
  function setup(props = {}) {
    const onSend = vi.fn();
    render(<MessageComposer conversationId="c1" onSend={onSend} {...props} />);
    return { onSend, user: userEvent.setup() };
  }

  it('sends on Enter and clears the field', async () => {
    const { onSend, user } = setup();
    const input = screen.getByLabelText('Message');

    await user.type(input, 'hello there{Enter}');

    expect(onSend).toHaveBeenCalledWith('hello there');
    expect(input).toHaveValue('');
  });

  it('inserts a newline instead of sending on Shift+Enter', async () => {
    const { onSend, user } = setup();
    const input = screen.getByLabelText('Message');

    await user.type(input, 'line one{Shift>}{Enter}{/Shift}line two');

    expect(onSend).not.toHaveBeenCalled();
    expect(input).toHaveValue('line one\nline two');
  });

  it('refuses to send whitespace and explains why', async () => {
    const { onSend, user } = setup();
    const input = screen.getByLabelText('Message');

    await user.type(input, '   {Enter}');

    expect(onSend).not.toHaveBeenCalled();
    expect(await screen.findByRole('alert')).toHaveTextContent(/type a message/i);
  });

  it('shortens an over-long paste instead of letting the server reject it', async () => {
    const { user } = setup();
    const input = screen.getByLabelText('Message');

    await user.click(input);
    await user.paste('a'.repeat(MAX_MESSAGE_LENGTH + 500));

    expect(input).toHaveValue('a'.repeat(MAX_MESSAGE_LENGTH));
    expect(screen.getByRole('alert')).toHaveTextContent(/shortened/i);
  });

  it('disables the send button when the text exceeds the limit', () => {
    setup();
    const input = screen.getByLabelText('Message');

    fireEvent.change(input, { target: { value: 'a'.repeat(MAX_MESSAGE_LENGTH + 1) } });

    expect(screen.getByRole('button', { name: /send message/i })).toBeDisabled();
    expect(screen.getByText(`${MAX_MESSAGE_LENGTH + 1}/${MAX_MESSAGE_LENGTH}`)).toBeInTheDocument();
  });

  it('explains why sending is unavailable when the conversation is closed', () => {
    setup({ disabled: true, disabledReason: 'This person does not accept messages.' });
    expect(screen.getByText(/does not accept messages/i)).toBeInTheDocument();
    expect(screen.queryByLabelText('Message')).not.toBeInTheDocument();
  });
});
