import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { ApiError, download } from '../../api/client';
import { portalApi } from '../../api/endpoints';
import { Button, EmptyState, ErrorBanner, PageHeader, Spinner } from '../../components/ui';
import { ExamList } from './ExamList';

export function MyExamsPage() {
  const exams = useQuery({ queryKey: ['my-exams'], queryFn: portalApi.myExams, retry: false });
  const [downloadError, setDownloadError] = useState<unknown>(null);
  if (exams.isPending) return <Spinner />;
  if (exams.error instanceof ApiError && exams.error.status === 404) {
    return (
      <>
        <PageHeader title="My exams" />
        <EmptyState title="The timetable is not published yet">Your exams, rooms and seats appear here as soon as the exam cell publishes them.</EmptyState>
      </>
    );
  }
  if (exams.isError) return <ErrorBanner error={exams.error} />;
  const data = exams.data;
  return (
    <>
      <PageHeader
        title="My exams"
        description={`${data.name} · ${data.roll}`}
        actions={
          <Button onClick={() => download('/api/students/me/hall-ticket', `hall-ticket-${data.roll}.pdf`).catch(setDownloadError)}>
            <Download className="size-4" /> Hall ticket (PDF)
          </Button>
        }
      />
      <ErrorBanner error={downloadError} title="Could not download the hall ticket" />
      {data.exams.length === 0 ? <EmptyState title="No exams for you in this timetable" /> : <ExamList exams={data.exams} />}
    </>
  );
}
