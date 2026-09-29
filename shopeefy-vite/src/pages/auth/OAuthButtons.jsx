import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import LoginOutlined from '@mui/icons-material/LoginOutlined';
import { useApi } from '../../hooks/useApi';

/**
 * One button per provider the server has configured. Each is a plain browser navigation to the
 * backend, which runs the OAuth2 + PKCE flow; no token ever passes through this page's URL.
 */
export default function OAuthButtons() {
  const { data } = useApi('/auth/providers');
  // [OWASP A01:2025] Only follow the backend's own authorization endpoints.
  const providers = (data?.oauth2 ?? []).filter(
    (p) => typeof p.url === 'string' && p.url.startsWith('/oauth2/authorization/'),
  );
  if (providers.length === 0) return null;
  return (
    <div className="mt-6 space-y-3" data-testid="oauth-providers">
      <Divider sx={{ fontSize: 13, color: 'text.secondary' }}>or continue with</Divider>
      {providers.map((provider) => (
        <Button
          key={provider.id}
          variant="outlined"
          fullWidth
          startIcon={<LoginOutlined />}
          onClick={() => window.location.assign(provider.url)}
          data-testid={`oauth-${provider.id}`}
        >
          {provider.name}
        </Button>
      ))}
    </div>
  );
}
