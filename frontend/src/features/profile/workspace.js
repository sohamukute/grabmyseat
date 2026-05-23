const rolePanels = [['ROLE_ADMIN', 'admin'], ['ROLE_ORGANIZER', 'organizer'], ['ROLE_STAFF', 'staff']];

export function roleWorkspace(roles = []) {
  return rolePanels.find(([role]) => roles.includes(role))?.[1] ?? 'customer';
}
