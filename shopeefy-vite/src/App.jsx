import { Suspense, lazy, useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Route, Routes } from 'react-router';
import Layout from './layout/Layout';
import { GuestOnly, RequireAdmin, RequireAuth } from './components/RouteGuards';
import { bootstrapSession, selectUser } from './store/authSlice';
import { fetchCart } from './store/cartSlice';
import Home from './pages/shop/Home';
import ProductList from './pages/shop/ProductList';
import Search from './pages/shop/Search';
import ProductDetails from './pages/shop/ProductDetails';
import Cart from './pages/shop/Cart';
import Checkout from './pages/shop/Checkout';
import OrderPayment from './pages/shop/OrderPayment';
import PaymentResult from './pages/shop/PaymentResult';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import ForgotPassword from './pages/auth/ForgotPassword';
import OAuthCallback from './pages/auth/OAuthCallback';
import Orders from './pages/account/Orders';
import OrderDetails from './pages/account/OrderDetails';
import Security from './pages/account/Security';
import NotFound from './pages/NotFound';
import { Loading } from './components/Feedback';

// The admin area is only downloaded by admins who open it.
const AdminLayout = lazy(() => import('./pages/admin/AdminLayout'));
const Dashboard = lazy(() => import('./pages/admin/Dashboard'));
const AdminOrders = lazy(() => import('./pages/admin/AdminOrders'));
const AdminProducts = lazy(() => import('./pages/admin/AdminProducts'));
const CreateProduct = lazy(() => import('./pages/admin/CreateProduct'));
const AdminUsers = lazy(() => import('./pages/admin/AdminUsers'));
const SecurityEvents = lazy(() => import('./pages/admin/SecurityEvents'));

export default function App() {
  const dispatch = useDispatch();
  const userId = useSelector(selectUser)?.id;

  // Restore the session from the HttpOnly refresh cookie once, so a reload keeps you signed in.
  useEffect(() => {
    dispatch(bootstrapSession());
  }, [dispatch]);

  useEffect(() => {
    if (userId) dispatch(fetchCart());
  }, [dispatch, userId]);

  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="products" element={<ProductList />} />
          <Route path="products/:category" element={<ProductList />} />
          <Route path="search" element={<Search />} />
          <Route path="product/:productId" element={<ProductDetails />} />
          <Route path="oauth2/callback" element={<OAuthCallback />} />

          <Route element={<GuestOnly />}>
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route path="forgot-password" element={<ForgotPassword />} />
          </Route>

          <Route element={<RequireAuth />}>
            <Route path="cart" element={<Cart />} />
            <Route path="checkout" element={<Checkout />} />
            <Route path="checkout/:orderId" element={<OrderPayment />} />
            <Route path="payment/:orderId" element={<PaymentResult />} />
            <Route path="account/orders" element={<Orders />} />
            <Route path="account/orders/:orderId" element={<OrderDetails />} />
            <Route path="account/security" element={<Security />} />
          </Route>

          <Route path="admin" element={<RequireAdmin />}>
            <Route element={<AdminLayout />}>
              <Route index element={<Dashboard />} />
              <Route path="orders" element={<AdminOrders />} />
              <Route path="products" element={<AdminProducts />} />
              <Route path="products/new" element={<CreateProduct />} />
              <Route path="users" element={<AdminUsers />} />
              <Route path="security-events" element={<SecurityEvents />} />
            </Route>
          </Route>

          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
