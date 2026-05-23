import { AdminPanel } from '../admin/admin-panel.jsx';
import { OrganizerPanel } from '../organizer/organizer-panel.jsx';
import { StaffPanel } from '../staff/staff-panel.jsx';
import { ProfilePanel } from './profile.jsx';
import { roleWorkspace } from './workspace.js';

export function Workspace({ authenticated, roles }) {
  if (!authenticated) return <ProfilePanel authenticated={false} />;
  const panel = roleWorkspace(roles);
  if (panel === 'admin') return <AdminPanel />;
  if (panel === 'organizer') return <OrganizerPanel />;
  if (panel === 'staff') return <StaffPanel />;
  return <ProfilePanel authenticated />;
}
