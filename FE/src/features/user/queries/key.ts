export const userKeys = {
  all: ['user'] as const,
  me: () => ['user', 'me'] as const,
  byId: (userId: string) => ['user', 'byId', userId] as const,
  publicByHandle: (handle: string) =>
    ['user', 'public', 'handle', handle] as const,
  identities: (userId: string) => ['user', 'identities', userId] as const,
  events: (
    userId: string,
    params?: {
      limit?: number;
      beforeCreatedExclusive?: string;
      beforeUuidExclusive?: string;
    },
  ) => ['user', 'events', userId, params ?? {}] as const,
};
