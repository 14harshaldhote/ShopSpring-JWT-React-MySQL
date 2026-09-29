import Paper from '@mui/material/Paper';

export default function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="flex justify-center py-6 sm:py-10">
      <Paper className="w-full max-w-md" sx={{ p: { xs: 3, sm: 4 } }}>
        <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        <div className="mt-6">{children}</div>
        {footer && (
          <div className="mt-6 border-t border-slate-100 pt-4 text-center text-sm text-slate-600">{footer}</div>
        )}
      </Paper>
    </div>
  );
}
