export const clanKeys = {
  all: ['clan'] as const,
  public: (params?: Record<string, unknown>) =>
    ['clan', 'public', params ?? {}] as const,
  byId: (clanId: string) => ['clan', 'byId', clanId] as const,
  byHandle: (handle: string) => ['clan', 'byHandle', handle] as const,
  byOwner: (ownerId: string) => ['clan', 'byOwner', ownerId] as const,
  byMember: (userId: string) => ['clan', 'byMember', userId] as const,
  members: (clanId: string) => ['clan', 'members', clanId] as const,
  me: () => ['clan', 'me'] as const,
  detailView: (clanId: string) => ['clan', 'detail', clanId] as const,
  joinRequests: (clanId: string) => ['clan', 'joinRequests', clanId] as const,
};
