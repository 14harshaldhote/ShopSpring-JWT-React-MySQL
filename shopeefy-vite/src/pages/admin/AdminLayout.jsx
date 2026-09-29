import { NavLink, Outlet } from 'react-router';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import Inventory2Outlined from '@mui/icons-material/Inventory2Outlined';
import AddBoxOutlined from '@mui/icons-material/AddBoxOutlined';
import PeopleOutlined from '@mui/icons-material/PeopleOutlined';
import GppMaybeOutlined from '@mui/icons-material/GppMaybeOutlined';

const LINKS = [
  { to: '/admin', label: 'Dashboard', icon: DashboardOutlined, testId: 'admin-nav-dashboard', end: true },
  { to: '/admin/orders', label: 'Orders', icon: ReceiptLongOutlined, testId: 'admin-nav-orders' },
  { to: '/admin/products', label: 'Products', icon: Inventory2Outlined, testId: 'admin-nav-products', end: true },
  { to: '/admin/products/new', label: 'Add product', icon: AddBoxOutlined, testId: 'admin-nav-new-product' },
  { to: '/admin/users', label: 'Customers', icon: PeopleOutlined, testId: 'admin-nav-users' },
  { to: '/admin/security-events', label: 'Security events', icon: GppMaybeOutlined, testId: 'admin-nav-security' },
];

export default function AdminLayout() {
  return (
    <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
      <aside>
        <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-500">Admin</p>
        <nav className="flex gap-1 overflow-x-auto lg:flex-col" data-testid="admin-nav">
          {LINKS.map(({ to, label, icon: Icon, testId, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              data-testid={testId}
              className={({ isActive }) =>
                `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? 'bg-indigo-600 text-white' : 'text-slate-700 hover:bg-slate-100'
                }`
              }
            >
              <Icon fontSize="small" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <section className="min-w-0">
        <Outlet />
      </section>
    </div>
  );
}
