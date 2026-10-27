import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Input } from '../components/ui/Input';
import { Brand } from '../components/ui/Brand';
import { ErrorBox } from '../components/ui/Feedback';
import { Eye, EyeOff } from 'lucide-react';
import { Spinner } from '../components/ui/Feedback';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      await login({ email, password });
      navigate('/dashboard');
    } catch (err) {
      const data = (err as { response?: { data?: { message?: string } } }).response?.data;
      setError(data?.message ?? 'Could not sign you in. Check your email and password.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-ink-50">
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <div className="mb-8 flex justify-center">
            <Brand to="/" />
          </div>
          <Card className="p-6 sm:p-8">
            <h1 className="font-display text-2xl font-semibold text-ink-900">Sign in to MedBook</h1>
            <p className="mt-1 text-sm text-ink-500">
              Patients, doctors, and administrators all use the same login.
            </p>

            <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4" noValidate>
              <Input
                label="Email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
              <Input
                label="Password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Your password"
                endAdornment={
                  <InputAdornment position="end">
                    <IconButton
                      size="small"
                      edge="end"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                    >
                      {showPassword
                        ? <EyeOff className="h-4 w-4" aria-hidden="true" />
                        : <Eye className="h-4 w-4" aria-hidden="true" />}
                    </IconButton>
                  </InputAdornment>
                }
              />

              {error && <ErrorBox message={error} />}

              <Button type="submit" disabled={isLoading} className="w-full">
                {isLoading && <Spinner className="text-white" />}
                {isLoading ? 'Signing in…' : 'Sign In'}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-ink-600">
              New to MedBook?{' '}
              <Link to="/register" className="font-medium text-brand-700 hover:text-brand-800 hover:underline">
                Create an account
              </Link>
            </p>
            <p className="mt-2 text-center text-xs text-ink-400">
              Patient accounts can be created here. Doctor accounts are created by a clinic
              administrator.
            </p>
          </Card>
        </div>
      </main>
    </div>
  );
}
