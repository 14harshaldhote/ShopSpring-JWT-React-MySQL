import { useState } from 'react';
import { useSelector } from 'react-redux';
import { Link, NavLink, useNavigate, useSearchParams } from 'react-router';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Badge from '@mui/material/Badge';
import Avatar from '@mui/material/Avatar';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import Divider from '@mui/material/Divider';
import InputBase from '@mui/material/InputBase';
import ShoppingBagOutlined from '@mui/icons-material/ShoppingBagOutlined';
import SearchIcon from '@mui/icons-material/Search';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import AdminPanelSettingsOutlined from '@mui/icons-material/AdminPanelSettingsOutlined';
import Logout from '@mui/icons-material/Logout';
import KeyboardArrowDown from '@mui/icons-material/KeyboardArrowDown';
import { logout } from '../api/client';
import { selectUser } from '../store/authSlice';
import { selectCartCount } from '../store/cartSlice';
import { DEPARTMENTS } from '../config/catalog';

function Brand() {
  return (
    <Link to="/" className="flex items-center gap-2 text-lg font-bold text-slate-900" data-testid="nav-home">
      <img src="/favicon.svg" alt="" className="h-7 w-7" />
      <span>ShopSpring</span>
    </Link>
  );
}

function DepartmentMenu({ department }) {
  const [anchor, setAnchor] = useState(null);
  const navigate = useNavigate();
  const go = (path) => {
    setAnchor(null);
    navigate(path);
  };
  return (
    <>
      <Button
        color="inherit"
        endIcon={<KeyboardArrowDown />}
        onClick={(e) => setAnchor(e.currentTarget)}
        data-testid={`nav-dept-${department.id}`}
      >
        {department.name}
      </Button>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        {department.categories.map((c) => (
          <MenuItem key={c.id} onClick={() => go(`/products/${c.id}`)} data-testid={`nav-category-${c.id}`}>
            {c.name}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}

function SearchBox() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get('q') ?? '');
  const submit = (event) => {
    event.preventDefault();
    const q = query.trim();
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`);
  };
  return (
    <form
      onSubmit={submit}
      role="search"
      className="flex min-w-0 flex-1 items-center rounded-lg border border-slate-200 bg-slate-50 px-3 focus-within:border-indigo-400 focus-within:bg-white md:max-w-md"
    >
      <SearchIcon fontSize="small" className="text-slate-400" />
      <InputBase
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search products"
        sx={{ ml: 1, flex: 1, fontSize: 14, py: 0.5 }}
        slotProps={{ input: { 'aria-label': 'Search products', maxLength: 100, 'data-testid': 'search-input' } }}
      />
    </form>
  );
}

function AccountMenu({ user }) {
  const [anchor, setAnchor] = useState(null);
  const navigate = useNavigate();
  const close = () => setAnchor(null);
  const go = (path) => {
    close();
    navigate(path);
  };
  const signOut = async () => {
    close();
    try {
      await logout();
    } finally {
      navigate('/');
    }
  };
  const initials = `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase() || '?';
  return (
    <>
      <IconButton onClick={(e) => setAnchor(e.currentTarget)} data-testid="nav-account-menu" aria-label="Account menu">
        <Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main', fontSize: 14 }}>{initials}</Avatar>
      </IconButton>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <div className="px-4 py-2">
          <p className="text-sm font-semibold">
            {user.firstName} {user.lastName}
          </p>
          <p className="text-xs text-slate-500" data-testid="nav-account-email">
            {user.email}
          </p>
        </div>
        <Divider />
        <MenuItem onClick={() => go('/account/orders')} data-testid="nav-orders">
          <ListItemIcon>
            <ReceiptLongOutlined fontSize="small" />
          </ListItemIcon>
          My orders
        </MenuItem>
        <MenuItem onClick={() => go('/account/security')} data-testid="nav-security">
          <ListItemIcon>
            <ShieldOutlined fontSize="small" />
          </ListItemIcon>
          Account security
        </MenuItem>
        {user.role === 'ADMIN' && (
          <MenuItem onClick={() => go('/admin')} data-testid="nav-admin">
            <ListItemIcon>
              <AdminPanelSettingsOutlined fontSize="small" />
            </ListItemIcon>
            Admin
          </MenuItem>
        )}
        <Divider />
        <MenuItem onClick={signOut} data-testid="nav-logout">
          <ListItemIcon>
            <Logout fontSize="small" />
          </ListItemIcon>
          Sign out
        </MenuItem>
      </Menu>
    </>
  );
}

export default function Navbar() {
  const user = useSelector(selectUser);
  const cartCount = useSelector(selectCartCount);
  return (
    <AppBar position="sticky" color="inherit" elevation={0} sx={{ bgcolor: 'rgba(255,255,255,0.95)' }}>
      <Toolbar className="mx-auto w-full max-w-7xl gap-3" sx={{ flexWrap: 'wrap', py: { xs: 1, md: 0 } }}>
        <Brand />
        <nav className="hidden items-center md:flex">
          {DEPARTMENTS.map((d) => (
            <DepartmentMenu key={d.id} department={d} />
          ))}
          <Button component={NavLink} to="/products" color="inherit" data-testid="nav-all-products">
            All
          </Button>
        </nav>
        <SearchBox />
        <div className="ml-auto flex items-center gap-1">
          {user ? (
            <>
              <IconButton component={Link} to="/cart" aria-label="Cart" data-testid="nav-cart">
                <Badge badgeContent={cartCount} color="primary" data-testid="nav-cart-count">
                  <ShoppingBagOutlined />
                </Badge>
              </IconButton>
              <AccountMenu user={user} />
            </>
          ) : (
            <>
              <Button component={Link} to="/login" data-testid="nav-login">
                Sign in
              </Button>
              <Button component={Link} to="/register" variant="contained" data-testid="nav-register">
                Create account
              </Button>
            </>
          )}
        </div>
      </Toolbar>
    </AppBar>
  );
}
