import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api,
  unwrap,
  type ApplicationStatus,
  type CreateApplicationBody,
  type UpdateApplicationBody,
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
  events: (id: number) => [...applicationKeys.all, 'events', id] as const,
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

/**
 * One application's history.
 *
 * Sitting under `applicationKeys.all` is what makes this correct for free: the
 * mutations below already invalidate that key, so changing a status refetches
 * the timeline without any of them naming it. A separate top-level key would
 * work until the day someone added a mutation and forgot the second invalidate.
 *
 * `enabled` keeps it from firing until a row is actually open - the dialog is
 * the only caller, and fetching a timeline nobody asked to see would be one
 * request per row on screen.
 */
export function useApplicationEvents(id: number | null) {
  return useQuery({
    queryKey: applicationKeys.events(id ?? 0),
    queryFn: () =>
      unwrap(
        api.GET('/api/v1/applications/{id}/events', {
          params: { path: { id: id as number } },
        }),
      ),
    enabled: id != null,
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

export function useUpdateApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateApplicationBody }) =>
      unwrap(
        api.PATCH('/api/v1/applications/{id}', {
          params: { path: { id } },
          body,
        }),
      ),
    onSuccess: () => {
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

/**
 * Puts an archived application back on the board.
 *
 * There is no `/unarchive` endpoint to call - PATCH already accepts
 * `archived: false`, and every other field left out means "leave alone", so
 * this body says exactly one thing.
 *
 * Note what it does NOT do: un-archiving writes no event, because
 * ApplicationEventType has no value for it. That is a deliberate gap on the
 * server side rather than something missing here - see
 * ApplicationService.setArchived. The timeline will show an Archived line with
 * no matching Restored line, and that is expected.
 */
export function useRestoreApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) =>
      unwrap(
        api.PATCH('/api/v1/applications/{id}', {
          params: { path: { id } },
          body: { archived: false },
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: applicationKeys.all })
    },
  })
}

/**
 * Really deletes. Archive is the one you almost always want.
 *
 * The row's events go with it - application_event has ON DELETE CASCADE - so
 * this removes the history too, and with it whatever the row contributed to the
 * funnel stats. That is why it sits behind a confirmation and archive does not.
 */
export function useDeleteApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) =>
      unwrap(api.DELETE('/api/v1/applications/{id}', { params: { path: { id } } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: applicationKeys.all })
    },
  })
}
