import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderRoutes, stubFetch } from '../../test/render';
import { SeatFinderPage } from './SeatFinderPage';

const routes = [{ path: '/seat', element: <SeatFinderPage /> }];
const status = { published: true, publishedAt: '2026-11-20T10:00:00Z', firstExam: '2026-12-01', lastExam: '2026-12-10', exams: 120 };

describe('SeatFinderPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('looks up a roll number and lists rooms and seats', async () => {
    const fetchMock = stubFetch({
      '/api/public/status': { body: status },
      '/api/public/seat': {
        body: {
          roll: '24CS001',
          exams: [{ courseCode: 'CS101', courseName: 'Programming', slot: '2026-12-01-MORNING', date: '2026-12-01', session: 'MORNING', roomCode: 'LH-1', building: 'Main', seat: 'B4' }],
        },
      },
    });
    renderRoutes(routes, '/seat');
    expect(await screen.findByText(/Exams run from/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Roll number'), ' 24cs001 ');
    await userEvent.click(screen.getByRole('button', { name: /Find/ }));

    expect(await screen.findByText('CS101')).toBeInTheDocument();
    expect(screen.getByText(/LH-1/)).toBeInTheDocument();
    expect(screen.getByText('B4')).toBeInTheDocument();
    const seatCall = fetchMock.mock.calls.map(([url]) => String(url)).find((u) => u.startsWith('/api/public/seat'));
    expect(seatCall).toBe('/api/public/seat?roll=24cs001');
  });

  it('explains an unknown roll number and a rate-limited network', async () => {
    stubFetch({
      '/api/public/status': { body: { published: false, publishedAt: null, firstExam: null, lastExam: null, exams: 0 } },
      '/api/public/seat': { status: 404, body: { status: 404, code: 'not_found', detail: 'No exams.' } },
    });
    renderRoutes(routes, '/seat');
    expect(await screen.findByText('The exam timetable has not been published yet.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Roll number'), 'zz999');
    await userEvent.click(screen.getByRole('button', { name: /Find/ }));
    expect(await screen.findByText(/No exams found for “ZZ999”/)).toBeInTheDocument();

    stubFetch({ '/api/public/seat': { status: 429, body: { status: 429, code: 'rate_limited', detail: 'Slow down.' } } });
    await userEvent.click(screen.getByRole('button', { name: /Find/ }));
    expect(await screen.findByText(/Too many searches from this network/)).toBeInTheDocument();
  });
});
