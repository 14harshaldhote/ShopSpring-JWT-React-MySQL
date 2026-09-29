import { createTheme } from '@mui/material/styles';

// System fonts only: nothing is loaded from a third-party font CDN (the CSP allows 'self' only).
const fontFamily = [
  'Inter',
  'system-ui',
  '-apple-system',
  '"Segoe UI"',
  'Roboto',
  '"Helvetica Neue"',
  'Arial',
  'sans-serif',
].join(',');

export const theme = createTheme({
  palette: {
    primary: { main: '#4f46e5' },
    secondary: { main: '#0f766e' },
    background: { default: '#f8fafc' },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily,
    button: { textTransform: 'none', fontWeight: 600 },
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { border: '1px solid #e2e8f0' } } },
    MuiAppBar: { styleOverrides: { root: { border: 'none', borderBottom: '1px solid #e2e8f0' } } },
    MuiMenu: { styleOverrides: { paper: { border: '1px solid #e2e8f0' } } },
    MuiTableCell: { styleOverrides: { head: { fontWeight: 600, color: '#475569' } } },
  },
});
