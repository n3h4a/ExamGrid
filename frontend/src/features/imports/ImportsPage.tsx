import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, FileSpreadsheet, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { download } from '../../api/client';
import { importApi } from '../../api/endpoints';
import type { ImportKind, ImportMode, ImportResult } from '../../api/types';
import { Button, Card, Checkbox, ErrorBanner, PageHeader, Pagination, SelectInput, Table, Td, Th } from '../../components/ui';
import { formatBytes, formatDateTime, formatNumber } from '../../lib/format';
import { ImportReportView } from './ImportReportView';
import { ImportStatusBadge } from './ImportStatusBadge';

const KINDS: { kind: ImportKind; label: string; help: string }[] = [
  { kind: 'courses', label: 'Courses', help: 'code, name, department, hard, sessions' },
  { kind: 'rooms', label: 'Rooms', help: 'code, building, rows, columns, blocked, priority' },
  { kind: 'enrolments', label: 'Enrolments', help: 'roll, course, name, department' },
  { kind: 'students', label: 'Students', help: 'roll, name, department, email' },
  { kind: 'faculty', label: 'Faculty', help: 'code, name, email, department, teaches, unavailable' },
];

/**
 * Upload wizard: choose a type, pick a CSV/XLSX, see the dry-run report, then commit the
 * very same file. Nothing is written until the user confirms a clean report.
 */
export function ImportsPage() {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [kind, setKind] = useState<ImportKind>('courses');
  const [replace, setReplace] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [page, setPage] = useState(0);
  const history = useQuery({ queryKey: ['imports', page], queryFn: () => importApi.list(page, 15) });
  const mode: ImportMode = kind === 'enrolments' && replace ? 'REPLACE' : 'MERGE';

  const upload = useMutation({
    mutationFn: ({ dryRun }: { dryRun: boolean }) => importApi.upload(kind, file!, dryRun, mode),
    onSuccess: (data) => {
      setResult(data);
      void queryClient.invalidateQueries({ queryKey: ['imports'] });
      if (data.batch.status === 'COMMITTED') {
        void queryClient.invalidateQueries({ queryKey: ['catalog-summary'] });
        void queryClient.invalidateQueries({ queryKey: ['courses'] });
        void queryClient.invalidateQueries({ queryKey: ['rooms'] });
        void queryClient.invalidateQueries({ queryKey: ['faculty'] });
        void queryClient.invalidateQueries({ queryKey: ['students'] });
      }
    },
  });

  function choose(f: File | null) {
    setFile(f);
    setResult(null);
    upload.reset();
  }

  const committed = result?.batch.status === 'COMMITTED';
  const canCommit = result?.batch.status === 'VALIDATED';
  const help = KINDS.find((k) => k.kind === kind)!;

  return (
    <>
      <PageHeader
        title="Imports"
        description="Upload CSV or Excel exports. Every file is checked first and imported only if all rows are valid; the original is kept for the record."
      />
      <Card title="Upload a file">
        <div className="grid gap-4 md:grid-cols-[14rem_1fr]">
          <div className="space-y-4">
            <SelectInput
              label="What does the file contain?"
              value={kind}
              onChange={(e) => {
                setKind(e.target.value as ImportKind);
                choose(null);
              }}
            >
              {KINDS.map((k) => (
                <option key={k.kind} value={k.kind}>
                  {k.label}
                </option>
              ))}
            </SelectInput>
            <p className="text-xs text-slate-500">Columns: {help.help}</p>
            <Button variant="secondary" onClick={() => download(`/api/imports/templates/${kind}`, `${kind}-template.csv`)}>
              <Download className="size-4" /> Template
            </Button>
            {kind === 'enrolments' && (
              <Checkbox label="Replace all enrolments" checked={replace} onChange={(e) => { setReplace(e.target.checked); setResult(null); }} />
            )}
          </div>
          <div>
            <div
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                choose(e.dataTransfer.files[0] ?? null);
              }}
              className="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 px-6 py-10 text-center"
            >
              <FileSpreadsheet className="size-8 text-slate-400" />
              <p className="mt-2 text-sm text-slate-700">{file ? <span className="font-medium">{file.name}</span> : 'Drop a .csv or .xlsx file here'}</p>
              {file && <p className="text-xs text-slate-500">{formatBytes(file.size)}</p>}
              <input ref={input} type="file" accept=".csv,.xlsx,text/csv" className="sr-only" aria-label="Choose file" onChange={(e) => choose(e.target.files?.[0] ?? null)} />
              <Button variant="secondary" className="mt-4" onClick={() => input.current?.click()}>
                Choose file
              </Button>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button disabled={!file} loading={upload.isPending && upload.variables?.dryRun} onClick={() => upload.mutate({ dryRun: true })}>
                <Upload className="size-4" /> Check file
              </Button>
              {canCommit && (
                <Button variant={mode === 'REPLACE' ? 'danger' : 'primary'} loading={upload.isPending && !upload.variables?.dryRun} onClick={() => upload.mutate({ dryRun: false })}>
                  Import {formatNumber(result!.report.acceptedRows)} rows
                </Button>
              )}
            </div>
          </div>
        </div>
        <div className="mt-6 space-y-4">
          <ErrorBanner error={upload.error} title="Upload failed" />
          {committed && <p className="text-sm font-medium text-emerald-700">Imported. Batch #{result!.batch.id}, SHA-256 {result!.batch.sha256.slice(0, 12)}…</p>}
          {result && <ImportReportView report={result.report} />}
        </div>
      </Card>

      <Card title="Import history" className="mt-6">
        <Table>
          <thead>
            <tr>
              <Th>#</Th>
              <Th>When</Th>
              <Th>Type</Th>
              <Th>File</Th>
              <Th>Rows</Th>
              <Th>Errors</Th>
              <Th>Status</Th>
              <Th>By</Th>
              <Th />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {history.data?.items.map((b) => (
              <tr key={b.id}>
                <Td className="tabular-nums text-slate-500">{b.id}</Td>
                <Td>{formatDateTime(b.createdAt)}</Td>
                <Td className="capitalize">{b.kind.toLowerCase()}</Td>
                <Td className="font-mono text-xs">{b.filename}</Td>
                <Td className="tabular-nums">{formatNumber(b.totalRows)}</Td>
                <Td className="tabular-nums">{b.errorCount}</Td>
                <Td>
                  <ImportStatusBadge status={b.status} dryRun={b.dryRun} />
                </Td>
                <Td>{b.createdBy}</Td>
                <Td>
                  <button className="text-sm text-brand-600 hover:underline" onClick={() => download(`/api/imports/${b.id}/file`, b.filename)}>
                    Original
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
        {history.data && <Pagination page={page} totalPages={history.data.totalPages} onPage={setPage} />}
      </Card>
    </>
  );
}
