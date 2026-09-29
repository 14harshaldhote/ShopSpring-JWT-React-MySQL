import { Fragment, useState } from 'react';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import Collapse from '@mui/material/Collapse';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Tooltip from '@mui/material/Tooltip';
import KeyboardArrowDown from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowRight from '@mui/icons-material/KeyboardArrowRight';
import LinkOutlined from '@mui/icons-material/LinkOutlined';
import Refresh from '@mui/icons-material/Refresh';
import { api } from '../../api/client';
import { OutcomeChip, SeverityChip } from '../../components/Chips';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { formatDateTime, humanize } from '../../utils/format';
import { describeUserAgent } from '../../utils/userAgent';

const SEVERITIES = ['INFO', 'WARN', 'HIGH', 'CRITICAL'];

/**
 * Every event is hash-chained to the one before it on the server, so editing or deleting a row
 * breaks the chain. "Verify" recomputes the whole chain.                    [OWASP A09:2025]
 */
function ChainCheck() {
  const [state, setState] = useState({ busy: false, result: null, error: null });

  const verify = async () => {
    setState({ busy: true, result: null, error: null });
    try {
      const { data } = await api.get('/api/admin/security-events/verify');
      setState({ busy: false, result: data, error: null });
    } catch (error) {
      setState({ busy: false, result: null, error });
    }
  };

  const { busy, result, error } = state;
  return (
    <div className="space-y-3">
      <Button
        variant="contained"
        startIcon={<LinkOutlined />}
        onClick={verify}
        disabled={busy}
        data-testid="verify-chain"
      >
        {busy ? 'Verifying…' : 'Verify audit chain'}
      </Button>
      <ErrorAlert error={error} />
      {result && (
        <Alert
          severity={result.valid ? 'success' : 'error'}
          data-testid="verify-chain-result"
          data-valid={result.valid ? 'true' : 'false'}
        >
          <AlertTitle>{result.valid ? 'Audit chain intact' : 'Tampering detected'}</AlertTitle>
          {result.valid
            ? `All ${result.eventsChecked} events were re-hashed and every link matches: no record has been changed or removed.`
            : `The chain breaks at event #${result.firstBrokenEventId} (${result.eventsChecked} events verified before it). That record or one before it was altered or deleted.`}
        </Alert>
      )}
    </div>
  );
}

function EventRow({ event }) {
  const [open, setOpen] = useState(false);
  return (
    <Fragment>
      <TableRow hover data-testid="security-event-row" data-severity={event.severity} data-type={event.type}>
        <TableCell padding="checkbox">
          <IconButton
            size="small"
            onClick={() => setOpen((v) => !v)}
            aria-label={open ? 'Hide details' : 'Show details'}
          >
            {open ? <KeyboardArrowDown fontSize="small" /> : <KeyboardArrowRight fontSize="small" />}
          </IconButton>
        </TableCell>
        <TableCell sx={{ color: 'text.secondary' }}>{event.id}</TableCell>
        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(event.createdAt)}</TableCell>
        <TableCell>
          <SeverityChip severity={event.severity} />
        </TableCell>
        <TableCell sx={{ fontWeight: 500 }}>{humanize(event.type)}</TableCell>
        <TableCell>
          <OutcomeChip outcome={event.outcome} />
        </TableCell>
        <TableCell sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {event.email ?? (event.userId ? `user #${event.userId}` : '—')}
        </TableCell>
        <TableCell sx={{ fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>{event.ipAddress ?? '—'}</TableCell>
        <TableCell
          sx={{
            maxWidth: 260,
            color: 'text.secondary',
            fontSize: 12,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          <Tooltip title={event.detail ?? ''}>
            <span>{event.detail ?? ''}</span>
          </Tooltip>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={9} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} unmountOnExit>
            <dl className="grid gap-x-6 gap-y-2 py-3 text-xs sm:grid-cols-[120px_1fr]">
              <dt className="font-semibold text-slate-500">Request</dt>
              <dd className="font-mono">{event.request ?? '—'}</dd>
              <dt className="font-semibold text-slate-500">User</dt>
              <dd>
                {event.userId ? `#${event.userId}` : '—'} {event.email ?? ''}
              </dd>
              <dt className="font-semibold text-slate-500">Device</dt>
              <dd>{event.userAgent ? `${describeUserAgent(event.userAgent)} (${event.userAgent})` : '—'}</dd>
              <dt className="font-semibold text-slate-500">Detail</dt>
              <dd className="break-words">{event.detail ?? '—'}</dd>
              <dt className="font-semibold text-slate-500">Chain hash</dt>
              <dd className="break-all font-mono">{event.hash ?? '—'}</dd>
            </dl>
          </Collapse>
        </TableCell>
      </TableRow>
    </Fragment>
  );
}

export default function SecurityEvents() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const events = useApi('/api/admin/security-events', { page, size });
  const data = events.data;
  const counts = Object.fromEntries(
    SEVERITIES.map((s) => [s, data?.content.filter((e) => e.severity === s).length ?? 0]),
  );

  return (
    <div className="space-y-5" data-testid="admin-security-events">
      <PageTitle
        subtitle="Tamper-evident audit trail: sign-ins, failures, lockouts, token reuse, admin actions and payment checks."
        action={
          <Button startIcon={<Refresh />} onClick={events.reload} data-testid="security-events-refresh">
            Refresh
          </Button>
        }
      >
        Security events
      </PageTitle>

      <Paper sx={{ p: 3 }}>
        <ChainCheck />
      </Paper>

      <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600">
        <span>On this page:</span>
        {SEVERITIES.map((s) => (
          <span key={s} className="flex items-center gap-1">
            <SeverityChip severity={s} /> {counts[s]}
          </span>
        ))}
      </div>

      <ErrorAlert error={events.error} />
      {events.loading && !data && <Loading />}
      {data && (
        <Paper>
          <TableContainer>
            <Table size="small" data-testid="security-events-table">
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox" />
                  <TableCell>#</TableCell>
                  <TableCell>Time</TableCell>
                  <TableCell>Severity</TableCell>
                  <TableCell>Event</TableCell>
                  <TableCell>Outcome</TableCell>
                  <TableCell>User</TableCell>
                  <TableCell>IP address</TableCell>
                  <TableCell>Detail</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((event) => (
                  <EventRow key={event.id} event={event} />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={Math.min(page, Math.max(0, data.totalPages - 1))}
            onPageChange={(_, p) => setPage(p)}
            rowsPerPage={size}
            onRowsPerPageChange={(e) => {
              setSize(Number(e.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[25, 50, 100]}
            data-testid="security-events-pagination"
          />
        </Paper>
      )}
      <p className="text-xs text-slate-500">
        Severity: INFO routine · WARN worth a look · HIGH likely attack (admin is emailed) · CRITICAL confirmed attack
        or integrity failure.
      </p>
    </div>
  );
}
