import { maxSamePaper, rowLabel, seatLabel } from './seats';

describe('seat labels', () => {
  it('match the solver: spreadsheet-style rows, 1-based benches', () => {
    expect(seatLabel(0, 0)).toBe('A1');
    expect(seatLabel(1, 3)).toBe('B4');
    expect(rowLabel(25)).toBe('Z');
    expect(rowLabel(26)).toBe('AA');
  });

  it('computes the largest checkerboard class like the solver', () => {
    expect(maxSamePaper(4, 4, new Set())).toBe(8);
    expect(maxSamePaper(5, 7, new Set())).toBe(18);
    // Blocking two seats of the same colour shrinks that class.
    expect(maxSamePaper(2, 2, new Set(['A1', 'B2']))).toBe(2);
    expect(maxSamePaper(3, 3, new Set(['A1', 'A3', 'C1']))).toBe(4);
  });
});
