import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { restoreSession } from '../api/client';

// Only the user profile is kept here; the access token never enters the store. [OWASP A07:2025]
export const bootstrapSession = createAsyncThunk('auth/bootstrap', () => restoreSession(), {
  condition: (_, { getState }) => getState().auth.status === 'idle',
});

const authSlice = createSlice({
  name: 'auth',
  initialState: { status: 'idle', user: null },
  reducers: {
    sessionChanged(state, action) {
      state.user = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(bootstrapSession.pending, (state) => {
        state.status = 'loading';
      })
      .addCase(bootstrapSession.fulfilled, (state, action) => {
        state.status = 'ready';
        state.user = action.payload ?? state.user;
      })
      .addCase(bootstrapSession.rejected, (state) => {
        state.status = 'ready';
      });
  },
});

export const { sessionChanged } = authSlice.actions;
export default authSlice.reducer;

export const selectUser = (state) => state.auth.user;
export const selectAuthReady = (state) => state.auth.status === 'ready';
export const selectIsAdmin = (state) => state.auth.user?.role === 'ADMIN';
