import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import { errorMessage } from '../api/errors';

export function ErrorAlert({ error, fallback, sx, ...rest }) {
  if (!error) return null;
  return (
    <Alert severity="error" sx={sx} {...rest}>
      {errorMessage(error, fallback)}
    </Alert>
  );
}

export function Loading({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-slate-500" role="status">
      <CircularProgress size={22} />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
      <p className="text-lg font-semibold text-slate-800">{title}</p>
      {children && <div className="max-w-md text-sm text-slate-500">{children}</div>}
      {action}
    </div>
  );
}

export function PageTitle({ children, subtitle, action }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{children}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
