import { Link } from 'react-router-dom';
import { Brand } from '../components/ui/Brand';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import {
  CalendarPlus, Stethoscope, CalendarClock, ShieldCheck, ArrowRight, CheckCircle2,
} from 'lucide-react';

/**
 * Public homepage. Factual copy only — MedBook is a clinic scheduling demo:
 * it books appointments, shows real availability, and protects accounts with
 * JWT sign-in. No invented certifications or usage numbers.
 */

const FEATURES = [
  {
    icon: <CalendarPlus className="h-5 w-5" aria-hidden="true" />,
    title: 'Book in a few clicks',
    text: 'Pick a specialty, choose your doctor, and reserve a time that fits your week.',
  },
  {
    icon: <Stethoscope className="h-5 w-5" aria-hidden="true" />,
    title: 'Browse real doctors',
    text: 'See each doctor’s specialty, experience and consultation fee before you decide.',
  },
  {
    icon: <CalendarClock className="h-5 w-5" aria-hidden="true" />,
    title: 'Honest availability',
    text: 'The schedule you see comes straight from the clinic — no stale PDFs, no phone tag.',
  },
  {
    icon: <ShieldCheck className="h-5 w-5" aria-hidden="true" />,
    title: 'Your account, your visits',
    text: 'Sign in securely and manage your own appointments. Only you can see or change them.',
  },
];

const STEPS = [
  {
    title: 'Create your account',
    text: 'Register with your email and basic details — takes under a minute.',
  },
  {
    title: 'Choose doctor and time',
    text: 'Filter by specialty, compare doctors, and pick from real availability.',
  },
  {
    title: 'Show up',
    text: 'Your booking is confirmed instantly. Cancel or review it any time.',
  },
];

export function HomePage() {
  return (
    <div className="min-h-screen bg-ink-50">
      {/* Header */}
      <header className="border-b border-ink-100 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
          <Brand to="/" />
          <nav aria-label="Account" className="flex items-center gap-2">
            <Link to="/login">
              <Button variant="ghost" size="sm">Sign in</Button>
            </Link>
            <Link to="/register">
              <Button size="sm">Create account</Button>
            </Link>
          </nav>
        </div>
      </header>

      <main>
        {/* Hero */}
        <section className="relative overflow-hidden">
          {/* quiet brand wash behind the headline */}
          <div aria-hidden="true" className="absolute inset-x-0 top-0 h-72 bg-gradient-to-b from-brand-50 to-transparent" />
          <div className="relative mx-auto max-w-6xl px-4 py-16 sm:px-6 sm:py-24">
            <p className="mb-3 text-sm font-semibold uppercase tracking-widest text-brand-600">
              Clinic appointments, organized
            </p>
            <h1 className="max-w-2xl font-display text-4xl font-semibold leading-tight tracking-tight text-ink-950 sm:text-5xl">
              Book your doctor. See the schedule. Skip the phone queue.
            </h1>
            <p className="mt-4 max-w-xl text-lg text-ink-600">
              MedBook is a digital clinic front desk: browse doctors by specialty, book a
              real opening in their week, and manage your visits from one place.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/register">
                <Button size="lg">
                  Book an appointment
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="outline" size="lg">Sign in</Button>
              </Link>
            </div>
          </div>
        </section>

        {/* Value */}
        <section aria-labelledby="features-heading" className="mx-auto max-w-6xl px-4 py-12 sm:px-6">
          <h2 id="features-heading" className="sr-only">What MedBook does</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {FEATURES.map((feature) => (
              <Card key={feature.title} className="p-5" hover>
                <div className="mb-3 inline-flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                  {feature.icon}
                </div>
                <h3 className="font-medium text-ink-900">{feature.title}</h3>
                <p className="mt-1 text-sm text-ink-600">{feature.text}</p>
              </Card>
            ))}
          </div>
        </section>

        {/* How it works — a real sequence, so numbered steps are meaningful here */}
        <section aria-labelledby="how-heading" className="border-y border-ink-100 bg-white">
          <div className="mx-auto max-w-6xl px-4 py-14 sm:px-6">
            <h2 id="how-heading" className="font-display text-2xl font-semibold text-ink-900">
              How it works
            </h2>
            <ol className="mt-8 grid gap-8 sm:grid-cols-3">
              {STEPS.map((step, index) => (
                <li key={step.title} className="relative">
                  <span
                    aria-hidden="true"
                    className="mb-4 inline-flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 font-display text-base font-semibold text-white"
                  >
                    {index + 1}
                  </span>
                  <h3 className="font-medium text-ink-900">{step.title}</h3>
                  <p className="mt-1 text-sm text-ink-600">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* Trust */}
        <section aria-labelledby="trust-heading" className="mx-auto max-w-6xl px-4 py-14 sm:px-6">
          <div className="grid items-center gap-8 md:grid-cols-2">
            <div>
              <h2 id="trust-heading" className="font-display text-2xl font-semibold text-ink-900">
                A calmer way to see your doctor
              </h2>
              <p className="mt-3 text-ink-600">
                MedBook keeps scheduling simple and transparent: one place for your
                appointments, clear fees before you book, and a record of every visit
                change. The clinic controls the schedule; you control your bookings.
              </p>
              <ul className="mt-5 space-y-2 text-sm text-ink-700">
                {[
                  'Secure sign-in with your own account',
                  'Clear consultation fees before you confirm',
                  'Cancellation with a reason, recorded in your history',
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" aria-hidden="true" />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
            {/* A quiet product glimpse built from real UI primitives — no stock photos */}
            <Card className="p-6">
              <p className="text-xs font-semibold uppercase tracking-wider text-ink-400">Your next visit</p>
              <div className="mt-4 flex items-center justify-between gap-4">
                <div>
                  <p className="font-medium text-ink-900">Dr. Maya Chen</p>
                  <p className="text-sm text-ink-500">Cardiology</p>
                </div>
                <span className="inline-flex items-center rounded-full bg-info-100 px-2.5 py-0.5 text-xs font-medium text-info-700">
                  Scheduled
                </span>
              </div>
              <div className="mt-4 rounded-field border border-ink-100 bg-ink-50 p-3 text-sm text-ink-700">
                <p>Monday, 28 September</p>
                <p className="text-ink-500">09:00 – 09:30 · Fee $320</p>
              </div>
            </Card>
          </div>
        </section>

        {/* CTA */}
        <section className="mx-auto max-w-6xl px-4 pb-16 sm:px-6">
          <div className="rounded-card bg-brand-700 px-6 py-12 text-center shadow-card sm:px-12">
            <h2 className="font-display text-2xl font-semibold text-white sm:text-3xl">
              Ready when you are
            </h2>
            <p className="mx-auto mt-2 max-w-md text-brand-100">
              Create a free account and book your first visit in a few clicks.
            </p>
            <div className="mt-6 flex justify-center gap-3">
              <Link to="/register">
                <Button size="lg" className="bg-white text-brand-700 hover:bg-brand-50">
                  Create account
                </Button>
              </Link>
              <Link to="/login">
                <Button size="lg" variant="ghost" className="text-white hover:bg-brand-600">
                  Sign in
                </Button>
              </Link>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-ink-100 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 text-sm text-ink-500 sm:flex-row sm:px-6">
          <Brand to="/" />
          <p>A student-built clinic scheduling project. Demo data only.</p>
        </div>
      </footer>
    </div>
  );
}
