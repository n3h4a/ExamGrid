import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { homeFor, useAuth } from '../../auth/useAuth';
import { Button, ErrorBanner, TextInput } from '../../components/ui';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user.role)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const signedIn = await login(username, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? homeFor(signedIn.role), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-900 via-brand-700 to-brand-500 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center justify-center gap-3 text-white">
          <img src="/favicon.svg" alt="" className="size-10" />
          <div>
            <p className="text-2xl font-semibold tracking-tight">ExamGrid</p>
            <p className="text-sm text-brand-100">Exam timetabling, seating and invigilation</p>
          </div>
        </div>
        <form onSubmit={submit} className="space-y-4 rounded-xl bg-white p-6 shadow-xl">
          <h1 className="text-lg font-semibold">Sign in</h1>
          <ErrorBanner error={error} title="Could not sign in" />
          <TextInput label="Username" autoComplete="username" required value={username} onChange={(e) => setUsername(e.target.value)} />
          <TextInput label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          <Button type="submit" className="w-full" loading={busy}>
            Sign in
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-brand-100">
          Looking for your seat?{' '}
          <Link to="/seat" className="font-medium text-white underline underline-offset-4">
            Find my seat
          </Link>
        </p>
      </div>
    </div>
  );
}
