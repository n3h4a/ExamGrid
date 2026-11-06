import { render, screen } from '@testing-library/react';
import type { ImportReport } from '../../api/types';
import { ImportReportView } from './ImportReportView';

const base: ImportReport = {
  kind: 'ENROLMENTS',
  mode: 'MERGE',
  totalRows: 4,
  acceptedRows: 2,
  errorCount: 0,
  warningCount: 0,
  issues: [],
  issuesTruncated: false,
  changes: { enrolmentsAdded: 2, enrolmentsRemoved: 0 },
};

describe('ImportReportView', () => {
  it('lists every issue with row, column and severity when rejected, without change tiles', () => {
    render(
      <ImportReportView
        report={{
          ...base,
          acceptedRows: 0,
          errorCount: 1,
          warningCount: 1,
          issues: [
            { row: 4, column: 'course', severity: 'ERROR', message: 'Unknown course ZZ999.' },
            { row: 3, column: null, severity: 'WARNING', message: 'Duplicate of row 2; skipped.' },
          ],
        }}
      />,
    );
    expect(screen.getByText(/Nothing was imported/)).toBeInTheDocument();
    expect(screen.getByText('Unknown course ZZ999.')).toBeInTheDocument();
    expect(screen.getByText('error')).toBeInTheDocument();
    expect(screen.getByText('warning')).toBeInTheDocument();
    expect(screen.queryByText('Enrolments added')).not.toBeInTheDocument();
  });

  it('summarises a clean file and warns about destructive replace mode', () => {
    render(<ImportReportView report={{ ...base, mode: 'REPLACE', changes: { enrolmentsAdded: 2, enrolmentsRemoved: 37 } }} />);
    expect(screen.getByText('2 of 4 rows are valid.')).toBeInTheDocument();
    expect(screen.getByText('Enrolments added')).toBeInTheDocument();
    expect(screen.getByText(/removes 37 enrolment/)).toBeInTheDocument();
  });
});
