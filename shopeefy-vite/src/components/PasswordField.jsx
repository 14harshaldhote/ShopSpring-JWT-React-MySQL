import { useState } from 'react';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import CheckCircle from '@mui/icons-material/CheckCircle';
import RadioButtonUnchecked from '@mui/icons-material/RadioButtonUnchecked';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import { checkPassword } from '../utils/passwordRules';

export function PasswordField({ testId, autoComplete = 'current-password', ...props }) {
  const [visible, setVisible] = useState(false);
  return (
    <TextField
      type={visible ? 'text' : 'password'}
      autoComplete={autoComplete}
      fullWidth
      {...props}
      slotProps={{
        htmlInput: { 'data-testid': testId, maxLength: 256 },
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? 'Hide password' : 'Show password'}
                onClick={() => setVisible((v) => !v)}
                edge="end"
                size="small"
              >
                {visible ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}

function Rule({ ok, children, neutral = false }) {
  const Icon = neutral ? InfoOutlined : ok ? CheckCircle : RadioButtonUnchecked;
  const color = neutral ? 'text-slate-500' : ok ? 'text-emerald-600' : 'text-slate-500';
  return (
    <li className={`flex items-center gap-2 ${color}`}>
      <Icon sx={{ fontSize: 16 }} />
      <span>{children}</span>
    </li>
  );
}

/**
 * The same rules the server enforces (NIST 800-63B style: length over complexity). The common /
 * breached-password check can only be done by the server.                  [OWASP A07:2025]
 */
export function PasswordRules({ password, email, names }) {
  const result = checkPassword(password, email, names);
  return (
    <ul className="space-y-1 text-xs" data-testid="password-rules" aria-live="polite">
      <Rule ok={result.length}>10 to 128 characters (a short phrase works well)</Rule>
      <Rule ok={result.personal}>Doesn’t contain your email name or your name</Rule>
      <Rule neutral>Not a common or previously breached password (checked when you submit)</Rule>
    </ul>
  );
}
