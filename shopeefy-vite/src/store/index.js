import { configureStore } from '@reduxjs/toolkit';
import { onSessionChange } from '../api/client';
import authReducer, { sessionChanged } from './authSlice';
import cartReducer from './cartSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    cart: cartReducer,
  },
});

// Keep the store in step with the API client (sign-in, refresh, sign-out, expired session).
onSessionChange((user) => store.dispatch(sessionChanged(user)));
