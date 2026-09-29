import { useState } from 'react';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { ErrorAlert, Loading, PageTitle } from '../../components/Feedback';
import { useApi } from '../../hooks/useApi';
import { formatDateTime, fullName, humanize } from '../../utils/format';

export default function AdminUsers() {
  const users = useApi('/api/admin/users');
  const [filter, setFilter] = useState('');
  const query = filter.trim().toLowerCase();
  const rows = (users.data ?? []).filter(
    (u) => !query || u.email.toLowerCase().includes(query) || fullName(u).toLowerCase().includes(query),
  );

  return (
    <div data-testid="admin-users">
      <PageTitle subtitle={users.data ? `${users.data.length} accounts, newest first` : null}>Customers</PageTitle>
      <TextField
        size="small"
        placeholder="Filter by name or email"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        sx={{ mb: 2, width: { xs: '100%', sm: 320 } }}
        slotProps={{ htmlInput: { maxLength: 100 } }}
      />
      <ErrorAlert error={users.error} />
      {users.loading && <Loading />}
      {users.data && (
        <TableContainer component={Paper}>
          <Table size="small" data-testid="admin-users-table">
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Role</TableCell>
                <TableCell>Sign-up</TableCell>
                <TableCell>Email verified</TableCell>
                <TableCell>Created</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((user) => (
                <TableRow key={user.id} hover data-testid="admin-user-row">
                  <TableCell>{user.id}</TableCell>
                  <TableCell>{fullName(user)}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={humanize(user.role)}
                      color={user.role === 'ADMIN' ? 'secondary' : 'default'}
                    />
                  </TableCell>
                  <TableCell>{user.authProvider === 'LOCAL' ? 'Email' : humanize(user.authProvider)}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={user.emailVerified ? 'Verified' : 'Pending'}
                      color={user.emailVerified ? 'success' : 'warning'}
                    />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(user.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </div>
  );
}
