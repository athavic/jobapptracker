import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Data is considered fresh for 30s, so switching between views does not
      // refetch on every mount.
      staleTime: 30_000,
      // Retrying a 4xx is pointless - the request itself is the problem.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status < 500) return false
        return failureCount < 2
      },
    },
  },
})
