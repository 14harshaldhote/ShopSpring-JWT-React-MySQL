import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { api, startSession } from '../../api/client';
import { fieldErrors } from '../../api/errors';
import { ErrorAlert } from '../../components/Feedback';
import { PasswordField, PasswordRules } from '../../components/PasswordField';
import { checkPassword } from '../../utils/passwordRules';
import AuthCard from './AuthCard';
import OtpStep from './OtpStep';
import OAuthButtons from './OAuthButtons';

const NAME = /^\p{L}[\p{L} .'-]{0,59}$/u;

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [challenge, setChallenge] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const set = (field) => (event) => setForm((f) => ({ ...f, [field]: event.target.value }));
  const serverErrors = fieldErrors(error);
  const names = [form.firstName.trim(), form.lastName.trim()];
  const passwordOk = checkPassword(form.password, form.email.trim(), names).ok;
  const nameError = (value) => (value && !NAME.test(value.trim()) ? 'Use letters only (up to 60).' : null);
  const canSubmit =
    !busy &&
    NAME.test(form.firstName.trim()) &&
    NAME.test(form.lastName.trim()) &&
    form.email.includes('@') &&
    passwordOk;

  const submit = async (event) => {
    event.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post('/auth/register', {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      setChallenge(data);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const verify = async (code) => {
    const { data } = await api.post('/auth/register/verify', { challengeId: challenge.challengeId, code });
    startSession(data);
    navigate('/', { replace: true });
  };

  const resend = async () => {
    const { data } = await api.post('/auth/otp/resend', { challengeId: challenge.challengeId });
    setChallenge(data);
  };

  if (challenge) {
    return (
      <AuthCard title="Verify your email" subtitle="One last step: prove the inbox is yours.">
        <OtpStep
          challenge={challenge}
          onVerify={verify}
          onResend={resend}
          onBack={() => setChallenge(null)}
          verifyLabel="Verify and create account"
        />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="We'll email you a code to confirm your address."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-indigo-600" data-testid="register-to-login">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="First name"
            value={form.firstName}
            onChange={set('firstName')}
            autoComplete="given-name"
            required
            fullWidth
            error={Boolean(nameError(form.firstName) || serverErrors.firstName)}
            helperText={nameError(form.firstName) || serverErrors.firstName}
            slotProps={{ htmlInput: { 'data-testid': 'register-firstName', maxLength: 60 } }}
          />
          <TextField
            label="Last name"
            value={form.lastName}
            onChange={set('lastName')}
            autoComplete="family-name"
            required
            fullWidth
            error={Boolean(nameError(form.lastName) || serverErrors.lastName)}
            helperText={nameError(form.lastName) || serverErrors.lastName}
            slotProps={{ htmlInput: { 'data-testid': 'register-lastName', maxLength: 60 } }}
          />
        </div>
        <TextField
          label="Email"
          type="email"
          value={form.email}
          onChange={set('email')}
          autoComplete="email"
          required
          fullWidth
          error={Boolean(serverErrors.email)}
          helperText={serverErrors.email}
          slotProps={{ htmlInput: { 'data-testid': 'register-email', maxLength: 254 } }}
        />
        <PasswordField
          label="Password"
          value={form.password}
          onChange={set('password')}
          autoComplete="new-password"
          required
          testId="register-password"
        />
        <PasswordRules password={form.password} email={form.email.trim()} names={names} />
        <ErrorAlert error={error} data-testid="register-error" />
        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={!canSubmit}
          data-testid="register-submit"
        >
          {busy ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
      <OAuthButtons />
    </AuthCard>
  );
}
