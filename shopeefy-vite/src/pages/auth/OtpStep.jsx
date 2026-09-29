import { useEffect, useRef, useState } from 'react';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import MarkEmailReadOutlined from '@mui/icons-material/MarkEmailReadOutlined';
import { ErrorAlert } from '../../components/Feedback';

const timeFormat = new Intl.DateTimeFormat('en-IN', { timeStyle: 'short' });

function ResendButton({ seconds, onResend }) {
  const [left, setLeft] = useState(Math.max(0, Math.ceil(seconds)));
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (left <= 0) return undefined;
    const timer = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [left]);

  const click = async () => {
    setBusy(true);
    try {
      await onResend();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button onClick={click} disabled={left > 0 || busy} size="small" data-testid="otp-resend">
      {left > 0 ? `Resend code in ${left}s` : busy ? 'Sending…' : 'Resend code'}
    </Button>
  );
}

/**
 * Second step of sign-in, sign-up and password reset: the 6-digit code sent by email.
 * The server allows 5 attempts per code and says how many are left.       [OWASP A07:2025]
 */
export default function OtpStep({
  challenge,
  onVerify,
  onResend,
  onBack,
  verifyLabel = 'Verify',
  canSubmit = true,
  children,
}) {
  const [code, setCode] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const inputRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const submit = async (event) => {
    event.preventDefault();
    if (code.length !== 6 || !canSubmit || busy) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await onVerify(code);
    } catch (err) {
      setError(err);
      setCode('');
      inputRef.current?.focus();
    } finally {
      setBusy(false);
    }
  };

  const resend = async () => {
    setError(null);
    setNotice(null);
    try {
      await onResend();
      setCode('');
      setNotice('We sent you a new code. Earlier codes no longer work.');
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="space-y-4" data-testid="otp-step">
      <div className="flex gap-3 rounded-lg bg-indigo-50 p-3 text-sm text-indigo-900">
        <MarkEmailReadOutlined fontSize="small" className="mt-0.5 shrink-0" />
        <p>
          Enter the 6-digit code we sent to <strong data-testid="otp-destination">{challenge.destination}</strong>.
          {challenge.expiresAt && <> It works until {timeFormat.format(new Date(challenge.expiresAt))}.</>}
        </p>
      </div>

      <TextField
        label="Verification code"
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        inputRef={inputRef}
        fullWidth
        autoComplete="one-time-code"
        slotProps={{
          htmlInput: {
            inputMode: 'numeric',
            pattern: '[0-9]*',
            maxLength: 6,
            'data-testid': 'otp-input',
            style: { fontSize: 26, letterSpacing: '0.5em', textAlign: 'center', fontFamily: 'ui-monospace, monospace' },
          },
        }}
      />

      {children}

      <ErrorAlert error={error} data-testid="otp-error" />
      {notice && <Alert severity="success">{notice}</Alert>}

      <Button
        type="submit"
        variant="contained"
        size="large"
        fullWidth
        disabled={code.length !== 6 || !canSubmit || busy}
        data-testid="otp-verify"
      >
        {busy ? 'Checking…' : verifyLabel}
      </Button>

      <div className="flex items-center justify-between">
        <Button size="small" color="inherit" onClick={onBack}>
          Back
        </Button>
        <ResendButton key={challenge.expiresAt} seconds={challenge.resendAfterSeconds ?? 60} onResend={resend} />
      </div>
    </form>
  );
}
