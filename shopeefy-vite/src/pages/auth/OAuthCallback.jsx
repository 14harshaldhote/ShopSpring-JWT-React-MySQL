import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { restoreSession } from '../../api/client';
import { Loading } from '../../components/Feedback';

/**
 * The backend lands here after a provider sign-in, having set the refresh cookie. The access token
 * is fetched with POST /auth/refresh; it is never passed in the URL.        [OWASP A07:2025]
 */
export default function OAuthCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    let active = true;
    restoreSession().then((user) => {
      if (active) navigate(user ? '/' : '/login?error=oauth2', { replace: true });
    });
    return () => {
      active = false;
    };
  }, [navigate]);

  return <Loading label="Finishing sign-in…" />;
}
