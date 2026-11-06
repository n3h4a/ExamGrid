import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import type { ImportReport } from '../../api/types';
import { Badge, Table, Td, Th } from '../../components/ui';
import { formatNumber } from '../../lib/format';

const CHANGE_LABELS: Record<string, string> = {
  created: 'Created',
  updated: 'Updated',
  unchanged: 'Unchanged',
  enrolmentsAdded: 'Enrolments added',
  alreadyEnrolled: 'Already enrolled',
  enrolmentsRemoved: 'Enrolments removed',
  studentsCreated: 'New students',
  studentsUpdated: 'Students updated',
  courses: 'Courses referenced',
  seats: 'Seats',
  teachingLinks: 'Teaching links',
  unavailableSlots: 'Unavailable slots',
};

/** The row-by-row validation report of an upload, plus what a commit changes. */
export function ImportReportView({ report }: { report: ImportReport }) {
  const ok = report.errorCount === 0;
  const destructive = (report.changes.enrolmentsRemoved ?? 0) > 0;
  return (
    <div className="space-y-4">
      <div className={`flex items-start gap-3 rounded-lg p-4 text-sm ring-1 ${ok ? 'bg-emerald-50 text-emerald-900 ring-emerald-200' : 'bg-red-50 text-red-900 ring-red-200'}`}>
        {ok ? <CheckCircle2 className="mt-0.5 size-5 shrink-0" /> : <XCircle className="mt-0.5 size-5 shrink-0" />}
        <div>
          <p className="font-medium">
            {ok
              ? `${formatNumber(report.acceptedRows)} of ${formatNumber(report.totalRows)} rows are valid.`
              : `${formatNumber(report.errorCount)} error(s) in ${formatNumber(report.totalRows)} rows. Nothing was imported.`}
          </p>
          <p className="mt-0.5 opacity-80">
            {ok ? 'Review the changes below.' : 'Fix the rows listed below in your spreadsheet and upload it again.'}
            {report.warningCount > 0 && ` ${report.warningCount} warning(s): those rows are skipped.`}
          </p>
        </div>
      </div>

      {ok && Object.keys(report.changes).length > 0 && (
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {Object.entries(report.changes).map(([key, value]) => (
            <div key={key} className={`rounded-lg p-3 ring-1 ${key === 'enrolmentsRemoved' && value > 0 ? 'bg-amber-50 ring-amber-200' : 'bg-slate-50 ring-slate-200'}`}>
              <dt className="text-xs text-slate-500">{CHANGE_LABELS[key] ?? key}</dt>
              <dd className="text-lg font-semibold tabular-nums">{formatNumber(value)}</dd>
            </div>
          ))}
        </dl>
      )}
      {ok && destructive && (
        <p className="flex items-center gap-2 text-sm text-amber-800">
          <AlertTriangle className="size-4" /> Replace mode removes {formatNumber(report.changes.enrolmentsRemoved)} enrolment(s) that are not in this file.
        </p>
      )}

      {report.issues.length > 0 && (
        <Table>
          <thead>
            <tr>
              <Th>Row</Th>
              <Th>Column</Th>
              <Th>Severity</Th>
              <Th>Problem</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {report.issues.map((issue, i) => (
              <tr key={i}>
                <Td className="tabular-nums">{issue.row}</Td>
                <Td className="font-mono text-xs">{issue.column ?? '—'}</Td>
                <Td>
                  <Badge tone={issue.severity === 'ERROR' ? 'red' : 'amber'}>{issue.severity.toLowerCase()}</Badge>
                </Td>
                <Td className="whitespace-normal">{issue.message}</Td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {report.issuesTruncated && <p className="text-xs text-slate-500">Only the first {report.issues.length} issues are shown.</p>}
    </div>
  );
}
