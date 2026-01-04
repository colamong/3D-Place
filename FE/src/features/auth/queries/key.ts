export const authKeys = {
  all: ['auth'] as const,
  state: () => ['auth', 'state'] as const,
};

