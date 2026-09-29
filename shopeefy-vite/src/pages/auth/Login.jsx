import { useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { api, startSession } from '../../api/client';
import { ErrorAlert } from '../../components/Feedback';
import { PasswordField } from '../../components/PasswordField';
import { safeReturnPath } from '../../utils/navigation';
import AuthCard from './AuthCard';
import OtpStep from './OtpStep';
import OAuthButtons from './OAuthButtons';

const OAUTH_ERRORS = {
  oauth2: "Signing in with that provider didn't work. Please try again, or use your email and password.",
  oauth2_email:
    "Your provider account doesn't have a verified email address, so we can't sign you in with it. Verify your email with the provider, or sign in with your password.",
};

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const oauthError = OAUTH_ERRORS[params.get('error')];
  const notice = location.state?.notice;
  const returnTo = safeReturnPath(location.state?.from);

  const submitPassword = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const { data } = await api.post('/auth/login', { email: email.trim(), password });
      setChallenge(data);
      setPassword('');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  const verify = async (code) => {
    const { data } = await api.post('/auth/login/verify', { challengeId: challenge.challengeId, code });
    startSession(data);
    navigate(returnTo, { replace: true });
  };

  const resend = async () => {
    const { data } = await api.post('/auth/otp/resend', { challengeId: challenge.challengeId });
    setChallenge(data);
  };

  if (challenge) {
    return (
      <AuthCard title="Check your email" subtitle="Step 2 of 2: confirm it's really you.">
        <OtpStep
          challenge={challenge}
          onVerify={verify}
          onResend={resend}
          onBack={() => setChallenge(null)}
          verifyLabel="Verify and sign in"
        />
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Sign in"
      subtitle="Step 1 of 2: your email and password. We'll then email you a one-time code."
      footer={
        <>
          New to ShopSpring?{' '}
          <Link to="/register" className="font-semibold text-indigo-600" data-testid="login-to-register">
            Create an account
          </Link>
        </>
      }
    >
      {oauthError && (
        <Alert severity="error" sx={{ mb: 2 }} data-testid="oauth-error">
          {oauthError}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mb: 2 }} data-testid="login-notice">
          {notice}
        </Alert>
      )}
      <form onSubmit={submitPassword} className="space-y-4">
        <TextField
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="username"
          required
          fullWidth
          slotProps={{ htmlInput: { 'data-testid': 'login-email', maxLength: 254 } }}
        />
        <PasswordField
          label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          testId="login-password"
        />
        <div className="text-right text-sm">
          <Link to="/forgot-password" className="text-indigo-600" data-testid="login-forgot">
            Forgot password?
          </Link>
        </div>
        <ErrorAlert error={error} data-testid="login-error" />
        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={busy || !email || !password}
          data-testid="login-submit"
        >
          {busy ? 'Checking…' : 'Continue'}
        </Button>
      </form>
      <OAuthButtons />
    </AuthCard>
  );
}
