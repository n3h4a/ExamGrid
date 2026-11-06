import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RoomPlan, SlotLoad } from '../../api/types';
import { renderWithClient, signIn, stubFetch } from '../../test/render';
import { SeatingView } from './SeatingView';

const slots: SlotLoad[] = [
  { slot: '2026-12-01-MORNING', exams: 2, rooms: 1, candidates: 3 },
  { slot: '2026-12-01-AFTERNOON', exams: 0, rooms: 0, candidates: 0 },
  { slot: '2026-12-02-MORNING', exams: 1, rooms: 1, candidates: 1 },
];
const room: RoomPlan = {
  slot: '2026-12-01-MORNING', roomCode: 'LH-1', building: 'Main', rows: 2, columns: 3, blockedSeats: ['B2'], capacity: 5,
  seats: [
    { row: 0, column: 0, label: 'A1', roll: '24CS001', courseCode: 'CS101' },
    { row: 0, column: 1, label: 'A2', roll: '24EC001', courseCode: 'EC201' },
    { row: 1, column: 0, label: 'B1', roll: '24EC002', courseCode: 'EC201' },
  ],
  invigilators: ['F001'],
  courses: ['CS101', 'EC201'],
};

describe('SeatingView', () => {
  beforeEach(() => signIn());
  afterEach(() => vi.unstubAllGlobals());

  it('draws the seat grid of each room with rolls, blocked seats and invigilators', async () => {
    const fetchMock = stubFetch({ '/api/schedule-runs/3/rooms': { body: [room] } });
    renderWithClient(<SeatingView runId={3} slots={slots} />);

    expect(await screen.findByText('24CS001')).toBeInTheDocument();
    expect(screen.getByText('3/5 seats · invigilators F001')).toBeInTheDocument();
    expect(screen.getByTitle('A2 · 24EC001 · EC201')).toBeInTheDocument();
    expect(screen.getByTitle('B2')).toHaveTextContent('×');
    expect(screen.getAllByRole('option')).toHaveLength(2); // empty slots are not offered
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/schedule-runs/3/rooms?slot=2026-12-01-MORNING');

    await userEvent.selectOptions(screen.getByLabelText('Slot'), '2026-12-02-MORNING');
    expect(String(fetchMock.mock.calls.at(-1)![0])).toBe('/api/schedule-runs/3/rooms?slot=2026-12-02-MORNING');
  });
});
