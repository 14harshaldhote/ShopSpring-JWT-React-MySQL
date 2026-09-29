import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { api } from '../api/client';
import { sessionChanged } from './authSlice';

// Totals always come from the server; the client never computes or sends a price. [OWASP A06:2025]
export const fetchCart = createAsyncThunk('cart/fetch', async () => (await api.get('/api/cart/')).data);

/** Cart mutations return the whole cart; pages call the API and hand the result to cartReceived. */
export const cartApi = {
  add: ({ productId, size, quantity }) => api.put('/api/cart/add', { productId, size, quantity }),
  setQuantity: (itemId, quantity) => api.put(`/api/cart_items/${itemId}`, { quantity }),
  remove: (itemId) => api.delete(`/api/cart_items/${itemId}`),
};

const cartSlice = createSlice({
  name: 'cart',
  initialState: { cart: null, loaded: false },
  reducers: {
    cartReceived(state, action) {
      state.cart = action.payload;
      state.loaded = true;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchCart.fulfilled, (state, action) => {
        state.cart = action.payload;
        state.loaded = true;
      })
      .addCase(sessionChanged, (state, action) => {
        if (!action.payload) {
          state.cart = null;
          state.loaded = false;
        }
      });
  },
});

export const { cartReceived } = cartSlice.actions;
export default cartSlice.reducer;

export const selectCart = (state) => state.cart.cart;
export const selectCartLoaded = (state) => state.cart.loaded;
export const selectCartCount = (state) => state.cart.cart?.totalItem ?? 0;
