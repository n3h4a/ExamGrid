/** Seat-label helpers mirroring the solver's SeatPosition (row 0 = "A", column 0 = "1"). */

export function rowLabel(row: number): string {
  let r = row;
  let label = '';
  do {
    label = String.fromCharCode(65 + (r % 26)) + label;
    r = Math.floor(r / 26) - 1;
  } while (r >= 0);
  return label;
}

export function seatLabel(row: number, column: number): string {
  return `${rowLabel(row)}${column + 1}`;
}

/**
 * The most candidates of one paper a room can hold without two of them sitting side by side:
 * the larger colour class of the checkerboard, counting only usable seats.
 */
export function maxSamePaper(rows: number, columns: number, blocked: Set<string>): number {
  const classes = [0, 0];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < columns; c++) {
      if (!blocked.has(seatLabel(r, c))) classes[(r + c) % 2]++;
    }
  }
  return Math.max(classes[0], classes[1]);
}
