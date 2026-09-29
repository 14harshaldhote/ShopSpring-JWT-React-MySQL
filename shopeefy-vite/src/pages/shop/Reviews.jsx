import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Link } from 'react-router';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Rating from '@mui/material/Rating';
import TextField from '@mui/material/TextField';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import { api } from '../../api/client';
import { errorMessage, statusOf } from '../../api/errors';
import { ErrorAlert } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { selectUser } from '../../store/authSlice';
import { formatDate } from '../../utils/format';

const REVIEW_MAX = 1000;

/** A 403 here means "you haven't bought this", which is information, not a failure. */
function SubmitResult({ error, success, testId }) {
  if (success) {
    return (
      <Alert severity="success" data-testid={testId}>
        {success}
      </Alert>
    );
  }
  if (!error) return null;
  return (
    <Alert severity={statusOf(error) === 403 ? 'info' : 'error'} data-testid={testId}>
      {statusOf(error) === 403
        ? errorMessage(error, 'Only customers who bought this product can review or rate it.')
        : errorMessage(error)}
    </Alert>
  );
}

function RatingSummary({ summary }) {
  const count = Number(summary?.count ?? 0);
  const average = Number(summary?.average ?? 0);
  return (
    <div className="space-y-3" data-testid="rating-summary">
      <div className="flex items-center gap-3">
        <span className="text-4xl font-bold">{count ? average.toFixed(1) : '–'}</span>
        <div>
          <Rating value={average} precision={0.1} readOnly />
          <p className="text-sm text-slate-500">
            {count} rating{count === 1 ? '' : 's'}
          </p>
        </div>
      </div>
      <div className="space-y-1">
        {[5, 4, 3, 2, 1].map((stars) => {
          const n = Number(summary?.distribution?.[stars] ?? 0);
          return (
            <div key={stars} className="flex items-center gap-2 text-xs text-slate-600">
              <span className="w-6">{stars}★</span>
              <LinearProgress
                variant="determinate"
                value={count ? (n / count) * 100 : 0}
                sx={{ flex: 1, height: 6, borderRadius: 3 }}
              />
              <span className="w-6 text-right">{n}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function RateForm({ productId, onRated }) {
  const [value, setValue] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const { data } = await api.post('/api/ratings/create', { productId, rating: value });
      onRated(data);
      setSuccess('Thanks! Your rating is saved.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-2">
      <p className="text-sm font-semibold">Rate this product</p>
      <div className="flex items-center gap-3">
        <Rating value={value} onChange={(_, v) => setValue(v ?? 0)} data-testid="rating-input" />
        <Button size="small" variant="outlined" disabled={!value || busy} onClick={submit} data-testid="rating-submit">
          Submit rating
        </Button>
      </div>
      <SubmitResult error={error} success={success} testId="rating-message" />
    </div>
  );
}

function ReviewForm({ productId, onReviewed }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const { data } = await api.post('/api/reviews/create', { productId, review: text.trim() });
      onReviewed(data);
      setText('');
      setSuccess('Thanks! Your review is published.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-2">
      <TextField
        label="Write a review"
        multiline
        minRows={3}
        fullWidth
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, REVIEW_MAX))}
        helperText={`${text.length}/${REVIEW_MAX}`}
        slotProps={{ htmlInput: { maxLength: REVIEW_MAX, 'data-testid': 'review-input' } }}
      />
      <Button type="submit" variant="contained" disabled={!text.trim() || busy} data-testid="review-submit">
        Post review
      </Button>
      <SubmitResult error={error} success={success} testId="review-message" />
    </form>
  );
}

export default function Reviews({ productId }) {
  const user = useSelector(selectUser);
  const ratings = useApi(`/api/ratings/product/${productId}`);
  const reviews = useApi(`/api/reviews/product/${productId}`);

  return (
    <section id="reviews" className="grid gap-8 lg:grid-cols-[320px_1fr]">
      <div className="space-y-6">
        <h2 className="text-xl font-bold">Ratings & reviews</h2>
        <RatingSummary summary={ratings.data} />
        {user ? (
          <Paper sx={{ p: 2 }} className="space-y-5">
            <RateForm productId={productId} onRated={ratings.setData} />
            <ReviewForm
              productId={productId}
              onReviewed={(review) => reviews.setData((list) => [review, ...(list ?? [])])}
            />
            <p className="text-xs text-slate-500">Only customers who bought this product can rate or review it.</p>
          </Paper>
        ) : (
          <p className="text-sm text-slate-600">
            <Link to="/login" state={{ from: `/product/${productId}` }} className="font-semibold text-indigo-600">
              Sign in
            </Link>{' '}
            to rate or review products you've bought.
          </p>
        )}
      </div>
      <div className="space-y-3" data-testid="reviews-list">
        <ErrorAlert error={reviews.error} />
        {reviews.data?.length === 0 && <p className="text-sm text-slate-500">No reviews yet.</p>}
        {reviews.data?.map((review) => (
          <article
            key={review.id}
            className="rounded-xl border border-slate-200 bg-white p-4"
            data-testid="review-item"
          >
            <div className="flex items-center justify-between text-sm">
              <span className="font-semibold">{review.author}</span>
              <span className="text-slate-500">{formatDate(review.createdAt)}</span>
            </div>
            {/* Review text is user input: rendered as text only, React escapes it. [OWASP A05:2025] */}
            <p className="mt-2 whitespace-pre-line break-words text-sm text-slate-700">{review.review}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
