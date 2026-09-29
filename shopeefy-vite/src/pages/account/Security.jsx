import { useState } from 'react';
import { useSelector } from 'react-redux';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import ComputerOutlined from '@mui/icons-material/ComputerOutlined';
import PhoneIphoneOutlined from '@mui/icons-material/PhoneIphoneOutlined';
import Refresh from '@mui/icons-material/Refresh';
import VerifiedOutlined from '@mui/icons-material/VerifiedOutlined';
import { api } from '../../api/client';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { PasswordField, PasswordRules } from '../../components/PasswordField';
import { useApi } from '../../hooks/useApi';
import { selectUser } from '../../store/authSlice';
import { formatDate, formatDateTime, humanize, timeAgo } from '../../utils/format';
import { checkPassword } from '../../utils/passwordRules';
import { describeAuthMethod, describeUserAgent } from '../../utils/userAgent';

function Section({ title, subtitle, action, children, testId }) {
  return (
    <Paper sx={{ p: { xs: 2, sm: 3 } }} data-testid={testId}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">{title}</h2>
          {subtitle && <p className="text-sm text-slate-500">{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
    </Paper>
  );
}

function Profile({ user }) {
  const facts = [
    ['Name', `${user.firstName} ${user.lastName}`],
    ['Email', user.email],
    ['Role', user.role === 'ADMIN' ? 'Administrator' : 'Customer'],
    ['Account created with', user.authProvider === 'LOCAL' ? 'Email and password' : humanize(user.authProvider)],
    ['Member since', formatDate(user.createdAt)],
  ];
  return (
    <Section title="Profile" testId="security-profile">
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        {facts.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</dt>
            <dd className="mt-0.5 flex items-center gap-2 text-sm text-slate-900">
              {value}
              {label === 'Email' && user.emailVerified && (
                <Chip size="small" color="success" variant="outlined" icon={<VerifiedOutlined />} label="Verified" />
              )}
            </dd>
          </div>
        ))}
      </dl>
    </Section>
  );
}

function Sessions({ sessions, onChanged }) {
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const others = sessions.data?.filter((s) => !s.current) ?? [];

  const run = async (key, request, message) => {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      await request();
      setNotice(message);
      onChanged();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Section
      title="Where you're signed in"
      subtitle="Sign out any device you don't recognise. Its access stops within seconds."
      testId="sessions-section"
      action={
        <Button
          variant="outlined"
          color="error"
          disabled={others.length === 0 || busy !== null}
          onClick={() =>
            run('others', () => api.post('/api/users/me/sessions/revoke-others'), 'Every other device was signed out.')
          }
          data-testid="sessions-revoke-others"
        >
          Sign out all other devices
        </Button>
      }
    >
      <ErrorAlert error={sessions.error || error} sx={{ mb: 2 }} />
      {notice && (
        <Alert severity="success" sx={{ mb: 2 }} data-testid="sessions-notice">
          {notice}
        </Alert>
      )}
      {sessions.loading && <Loading />}
      <ul className="divide-y divide-slate-100" data-testid="sessions-list">
        {sessions.data?.map((session) => {
          const mobile = /Mobile|Android|iPhone|iPad/.test(session.userAgent ?? '');
          const Icon = mobile ? PhoneIphoneOutlined : ComputerOutlined;
          return (
            <li
              key={session.id}
              className="flex flex-wrap items-center gap-4 py-3"
              data-testid="session-item"
              data-current={session.current ? 'true' : 'false'}
            >
              <Icon className="text-slate-400" />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <Tooltip title={session.userAgent ?? ''}>
                    <span className="font-medium">{describeUserAgent(session.userAgent)}</span>
                  </Tooltip>
                  {session.current && (
                    <Chip size="small" color="primary" label="This device" data-testid="session-current" />
                  )}
                </div>
                <p className="text-sm text-slate-600">
                  {session.ipAddress ?? 'Unknown IP'} · {describeAuthMethod(session.authMethod)}
                </p>
                <p className="text-xs text-slate-500">
                  Signed in {formatDateTime(session.createdAt)} · Last active{' '}
                  {timeAgo(session.lastUsedAt ?? session.createdAt)} · Expires {formatDate(session.expiresAt)}
                </p>
              </div>
              {!session.current && (
                <Button
                  size="small"
                  color="error"
                  disabled={busy !== null}
                  onClick={() =>
                    run(
                      session.id,
                      () => api.delete(`/api/users/me/sessions/${session.id}`),
                      'That device was signed out.',
                    )
                  }
                  data-testid="session-revoke"
                >
                  {busy === session.id ? 'Signing out…' : 'Sign out'}
                </Button>
              )}
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

const EVENT_TONE = {
  LOGIN_SUCCESS: 'success',
  OAUTH2_LOGIN_SUCCESS: 'success',
  REGISTRATION_COMPLETED: 'success',
  LOGIN_FAILURE: 'warning',
  OTP_VERIFY_FAILURE: 'warning',
  OAUTH2_LOGIN_FAILURE: 'warning',
  OTP_EXHAUSTED: 'error',
  ACCOUNT_LOCKED: 'error',
  REFRESH_TOKEN_REUSE: 'error',
  PASSWORD_CHANGED: 'info',
  PASSWORD_RESET_COMPLETED: 'info',
  SESSION_REVOKED: 'info',
};

function Events({ events }) {
  return (
    <Section
      title="Recent security activity"
      subtitle="Your last 20 sign-ins, failed attempts, password changes and sign-outs."
      testId="security-events-section"
      action={
        <Button startIcon={<Refresh />} onClick={events.reload} size="small">
          Refresh
        </Button>
      }
    >
      <ErrorAlert error={events.error} />
      {events.loading && <Loading />}
      {events.data?.length === 0 && <p className="text-sm text-slate-500">No activity yet.</p>}
      {events.data?.length > 0 && (
        <TableContainer>
          <Table size="small" data-testid="security-events-list">
            <TableHead>
              <TableRow>
                <TableCell>Event</TableCell>
                <TableCell>When</TableCell>
                <TableCell>IP address</TableCell>
                <TableCell>Device</TableCell>
                <TableCell>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.data.map((event, index) => (
                <TableRow key={`${event.createdAt}-${index}`} data-testid="security-event" data-type={event.type}>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={EVENT_TONE[event.type] ?? 'default'}
                      label={humanize(event.type)}
                    />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title={formatDateTime(event.createdAt)}>
                      <span>{timeAgo(event.createdAt)}</span>
                    </Tooltip>
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>
                    {event.ipAddress ?? '—'}
                  </TableCell>
                  <TableCell>{event.userAgent ? describeUserAgent(event.userAgent) : '—'}</TableCell>
                  <TableCell sx={{ color: 'text.secondary', fontSize: 12 }}>{event.detail ?? ''}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Section>
  );
}

function ChangePassword({ user, onChanged }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);

  const names = [user.firstName, user.lastName];
  const valid = current && checkPassword(next, user.email, names).ok && next === confirm;

  const submit = async (event) => {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    setError(null);
    setDone(false);
    try {
      await api.post('/api/users/me/password', { currentPassword: current, newPassword: next });
      setCurrent('');
      setNext('');
      setConfirm('');
      setDone(true);
      onChanged();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Section
      title="Change password"
      subtitle="You stay signed in here; every other device is signed out."
      testId="change-password-section"
    >
      <form onSubmit={submit} className="grid max-w-xl gap-4">
        <PasswordField
          label="Current password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          testId="change-password-current"
        />
        <PasswordField
          label="New password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          autoComplete="new-password"
          testId="change-password-new"
        />
        <PasswordField
          label="Repeat new password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password"
          error={confirm.length > 0 && confirm !== next}
          helperText={confirm.length > 0 && confirm !== next ? "The passwords don't match." : ' '}
          testId="change-password-confirm"
        />
        <PasswordRules password={next} email={user.email} names={names} />
        <ErrorAlert error={error} data-testid="change-password-error" />
        {done && (
          <Alert severity="success" data-testid="change-password-success">
            Password changed. Your other devices were signed out.
          </Alert>
        )}
        <div>
          <Button type="submit" variant="contained" disabled={!valid || busy} data-testid="change-password-submit">
            {busy ? 'Saving…' : 'Change password'}
          </Button>
        </div>
      </form>
    </Section>
  );
}

export default function Security() {
  const user = useSelector(selectUser);
  const sessions = useApi('/api/users/me/sessions');
  const events = useApi('/api/users/me/security-events');
  const refreshAll = () => {
    sessions.reload();
    events.reload();
  };

  return (
    <div className="space-y-5" data-testid="account-security">
      <PageTitle subtitle="Your profile, signed-in devices and recent account activity.">Account security</PageTitle>
      <Profile user={user} />
      <Sessions sessions={sessions} onChanged={refreshAll} />
      <Events events={events} />
      <ChangePassword user={user} onChanged={refreshAll} />
    </div>
  );
}
