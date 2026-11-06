import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NewRunForm } from './NewRunForm';

describe('NewRunForm', () => {
  it('submits the solver defaults with the edited values', async () => {
    const onSubmit = vi.fn();
    render(<NewRunForm onSubmit={onSubmit} busy={false} error={null} />);

    await userEvent.type(screen.getByLabelText('Label'), 'Draft 2');
    await userEvent.selectOptions(screen.getByLabelText('Optimiser'), 'TABU');
    const sameDay = screen.getByLabelText('Same day');
    await userEvent.clear(sameDay);
    await userEvent.type(sameDay, '25');
    await userEvent.click(screen.getByRole('button', { name: 'Show advanced settings' }));
    await userEvent.click(screen.getByLabelText(/Diagonal neighbours/));
    await userEvent.click(screen.getByRole('button', { name: /Start/ }));

    expect(onSubmit).toHaveBeenCalledWith({
      label: 'Draft 2',
      optimizer: 'TABU',
      timeLimitSeconds: 60,
      maxIterations: 1_000_000,
      seed: 42,
      weights: { sameDay: 25, backToBack: 3, hardGap: 5, hardMinGapDays: 2 },
      studentsPerInvigilator: 30,
      diagonalAdjacency: true,
    });
  });
});
