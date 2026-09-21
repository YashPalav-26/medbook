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

/**
 * Registration page, consistent with the login card. Fields exactly match the
 * backend RegisterRequest — no role selection, nothing invented. Grouped into
 * "About you" and "Account security", with password requirements up front.
 */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    dateOfBirth: '',
    password: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleChange = (field: keyof typeof formData, value: string) => {
    setFormData((current) => ({ ...current, [field]: value }));
  };

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!formData.firstName.trim()) errors.firstName = 'First name is required';
    if (!formData.lastName.trim()) errors.lastName = 'Last name is required';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) errors.email = 'Enter a valid email';
    if (!/^\+?[0-9\s-]{1,20}$/.test(formData.phoneNumber)) errors.phoneNumber = 'Enter a valid phone number';
    if (!formData.dateOfBirth) errors.dateOfBirth = 'Date of birth is required';
    if (formData.password.length < 8) errors.password = 'Password must be at least 8 characters';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!validate()) return;
    setIsLoading(true);
    try {
      await register({
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName,
        lastName: formData.lastName,
        phoneNumber: formData.phoneNumber,
        dateOfBirth: formData.dateOfBirth,
      });
      navigate('/dashboard');
    } catch (err) {
      const data = (err as { response?: { data?: { message?: string; fieldErrors?: { field: string; message: string }[] } } }).response?.data;
      if (data?.fieldErrors?.length) {
        setFieldErrors(
          Object.fromEntries(data.fieldErrors.map((fe) => [fe.field, fe.message])),
        );
      } else {
        setError(data?.message ?? 'Could not create your account. Please review your details.');
      }
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-ink-50">
      <main className="flex flex-1 items-center justify-center px-4 py-12">
        <div className="w-full max-w-lg">
          <div className="mb-8 flex justify-center">
            <Brand to="/" />
          </div>
          <Card className="p-6 sm:p-8">
            <h1 className="font-display text-2xl font-semibold text-ink-900">Create your account</h1>
            <p className="mt-1 text-sm text-ink-500">
              One account to book and manage your clinic visits.
            </p>

            <form onSubmit={handleSubmit} className="mt-6 space-y-5" noValidate>
              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold uppercase tracking-wider text-ink-400">
                  About you
                </legend>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input
                    label="First name"
                    type="text"
                    autoComplete="given-name"
                    required
                    value={formData.firstName}
                    onChange={(e) => handleChange('firstName', e.target.value)}
                    error={fieldErrors.firstName}
                  />
                  <Input
                    label="Last name"
                    type="text"
                    autoComplete="family-name"
                    required
                    value={formData.lastName}
                    onChange={(e) => handleChange('lastName', e.target.value)}
                    error={fieldErrors.lastName}
                  />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input
                    label="Phone number"
                    type="tel"
                    autoComplete="tel"
                    required
                    helper="Digits, spaces or dashes, optional +"
                    value={formData.phoneNumber}
                    onChange={(e) => handleChange('phoneNumber', e.target.value)}
                    error={fieldErrors.phoneNumber}
                  />
                  <Input
                    label="Date of birth"
                    type="date"
                    autoComplete="bday"
                    required
                    value={formData.dateOfBirth}
                    onChange={(e) => handleChange('dateOfBirth', e.target.value)}
                    error={fieldErrors.dateOfBirth}
                  />
                </div>
              </fieldset>

              <fieldset className="space-y-4">
                <legend className="text-sm font-semibold uppercase tracking-wider text-ink-400">
                  Account security
                </legend>
                <Input
                  label="Email"
                  type="email"
                  autoComplete="email"
                  required
                  helper="You will use this to sign in."
                  value={formData.email}
                  onChange={(e) => handleChange('email', e.target.value)}
                  error={fieldErrors.email}
                />
                <div className="relative">
                  <Input
                    label="Password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    required
                    helper="At least 8 characters."
                    value={formData.password}
                    onChange={(e) => handleChange('password', e.target.value)}
                    error={fieldErrors.password}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-3 top-[2.1rem] text-ink-400 hover:text-ink-600"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </fieldset>

              {error && <ErrorBox message={error} />}

              <Button type="submit" disabled={isLoading} className="w-full">
                {isLoading && <Spinner className="text-white" />}
                {isLoading ? 'Creating account…' : 'Create account'}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-ink-600">
              Already have an account?{' '}
              <Link to="/login" className="font-medium text-brand-700 hover:underline">
                Sign in
              </Link>
            </p>
          </Card>
        </div>
      </main>
    </div>
  );
}
