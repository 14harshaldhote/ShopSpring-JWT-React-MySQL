import { Link } from 'react-router';
import Button from '@mui/material/Button';
import { EmptyState } from '../components/Feedback';

export default function NotFound() {
  return (
    <EmptyState
      title="Page not found"
      action={
        <Button component={Link} to="/" variant="contained">
          Back to the shop
        </Button>
      }
    >
      The page you asked for doesn't exist or has moved.
    </EmptyState>
  );
}
