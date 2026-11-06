import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { TimetableEntry } from '../../api/types';
import { TimetableGrid } from './TimetableGrid';

const entry = (courseCode: string, date: string, session: 'MORNING' | 'AFTERNOON', department = 'CS'): TimetableEntry => ({
  courseCode,
  courseName: courseCode,
  department,
  slot: `${date}-${session}`,
  date,
  session,
  candidates: 40,
  rooms: ['R101'],
});

describe('TimetableGrid', () => {
  it('places exams by day and session and dims non-matching courses when filtering', async () => {
    render(
      <TimetableGrid entries={[entry('CS301', '2026-12-07', 'MORNING'), entry('MA201', '2026-12-07', 'AFTERNOON', 'MA'), entry('CS302', '2026-12-08', 'MORNING')]} />,
    );
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText('Morning')).toBeInTheDocument();
    expect(screen.getByText('Afternoon')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Highlight'), 'cs3');
    expect(screen.getByText('MA201').className).toContain('opacity-25');
    expect(screen.getByText('CS301').className).toContain('ring-brand-700');
  });
});
