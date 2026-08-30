import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  unwrap,
  type ApplicationStatus,
  type CreateApplicationBody,
} from '../../api/client'

export interface ApplicationFilters {
  status?: ApplicationStatus
  company?: string
  includeArchived?: boolean
  page?: number
  size?: number
}

/**
 * Query keys in one place.
 *
 * The nesting matters: invalidating `all` also invalidates every `list(...)`
 * under it, so a mutation does not have to know which filters are on screen.
 */
export const applicationKeys = {
  all: ['applications'] as const,
  lists: () => [...applicationKeys.all, 'list'] as const,
  list: (filters: ApplicationFilters) => [...applicationKeys.lists(), filters] as const,
}

export function useApplications(filters: ApplicationFilters) {
  return useQuery({
    queryKey: applicationKeys.list(filters),
    queryFn: () =>
      unwrap(
        api.GET('/api/v1/applications', {
          params: {
            query: {
              status: filters.status,
              company: filters.company || undefined,
              includeArchived: filters.includeArchived,
              page: filters.page ?? 0,
              size: filters.size ?? 20,
              sort: ['createdAt,desc'],
            },
          },
        }),
      ),
    // Keeps the previous page on screen while the next one loads, instead of
    // flashing an empty table on every filter change.
    placeholderData: (previous) => previous,
  })
}

export function useCreateApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (body: CreateApplicationBody) =>
      unwrap(api.POST('/api/v1/applications', { body })),
    onSuccess: () => {
      // Refetch the list so the new row appears. Without this the cache still
      // holds the old page and nothing visibly happens.
      void queryClient.invalidateQueries({ queryKey: applicationKeys.all })
    },
  })
}

export function useChangeStatus() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: ApplicationStatus }) =>
      unwrap(
        api.POST('/api/v1/applications/{id}/status', {
          params: { path: { id } },
          body: { status },
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: applicationKeys.all })
    },
  })
}

export function useArchiveApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) =>
      unwrap(api.POST('/api/v1/applications/{id}/archive', { params: { path: { id } } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: applicationKeys.all })
    },
  })
}
