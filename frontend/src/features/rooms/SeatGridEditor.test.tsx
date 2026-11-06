import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SeatGridEditor } from './SeatGridEditor';

describe('SeatGridEditor', () => {
  it('renders every seat and reports clicks by label', async () => {
    const onToggle = vi.fn();
    render(<SeatGridEditor rows={2} columns={3} blocked={new Set(['B3'])} onToggle={onToggle} />);
    expect(screen.getAllByRole('gridcell')).toHaveLength(6);
    expect(screen.getByRole('gridcell', { name: 'Seat B3 (blocked)' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(screen.getByRole('gridcell', { name: 'Seat A2' }));
    expect(onToggle).toHaveBeenCalledWith('A2');
  });
});
