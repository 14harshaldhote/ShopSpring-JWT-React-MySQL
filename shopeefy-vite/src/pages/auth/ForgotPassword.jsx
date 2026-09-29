import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { api } from '../../api/client';
import { ErrorAlert } from '../../components/Feedback';
import { PasswordField, PasswordRules } from '../../components/PasswordField';
import { checkPassword } from '../../utils/passwordRules';
import AuthCard from './AuthCard';
import OtpStep from './OtpStep';

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [challenge, setChallenge] = useState(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const requestCode = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post('/auth/password/forgot', { email: email.trim() });
      setChallenge(data);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const reset = async (code) => {
    await api.post('/auth/password/reset', { challengeId: challenge.challengeId, code, newPassword: password });
    navigate('/login', {
      replace: true,
      state: { notice: 'Your password was changed and every device was signed out. Sign in with your new password.' },
    });
  };

  const resend = async () => {
    const { data } = await api.post('/auth/otp/resend', { challengeId: challenge.challengeId });
    setChallenge(data);
  };

  const passwordOk = checkPassword(password, email.trim()).ok;
  const mismatch = confirm.length > 0 && confirm !== password;

  if (challenge) {
    return (
      <AuthCard title="Choose a new password" subtitle="Enter the code from your email and a new password.">
        <OtpStep
          challenge={challenge}
          onVerify={reset}
          onResend={resend}
          onBack={() => setChallenge(null)}
          verifyLabel="Change password"
          canSubmit={passwordOk && confirm === password}
        >
          <PasswordField
            label="New password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            testId="reset-password"
          />
          <PasswordField
            label="Repeat new password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            error={mismatch}
            helperText={mismatch ? "The passwords don't match." : ' '}
            testId="reset-password-confirm"
          />
          <PasswordRules password={password} email={email.trim()} names={[]} />
        </OtpStep>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Reset your password"
      subtitle="If an account uses this email, we'll send it a one-time code."
      footer={
        <Link to="/login" className="font-semibold text-indigo-600">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={requestCode} className="space-y-4">
        <TextField
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
          fullWidth
          slotProps={{ htmlInput: { 'data-testid': 'forgot-email', maxLength: 254 } }}
        />
        <ErrorAlert error={error} />
        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={busy || !email}
          data-testid="forgot-submit"
        >
          {busy ? 'Sending…' : 'Send code'}
        </Button>
      </form>
    </AuthCard>
  );
}
