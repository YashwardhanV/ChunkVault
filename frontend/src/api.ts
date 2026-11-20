import type {
  ChunkingStrategy, Comparison, FileDetail, FileSummary, GcResult, GcStatus,
  PageResponse, StorageStats, UploadResult, VerifyResult,
} from './types'

const API = import.meta.env.VITE_API_BASE ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, init)
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(body.message ?? `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  files: (page = 0) => request<PageResponse<FileSummary>>(`/api/files?page=${page}&size=20`),
  file: (id: string, version?: number) => request<FileDetail>(
    `/api/files/${id}${version ? `?version=${version}` : ''}`,
  ),
  stats: () => request<StorageStats>('/api/storage/stats'),
  gcStatus: () => request<GcStatus>('/api/storage/gc/status'),
  gc: () => request<GcResult>('/api/storage/gc', { method: 'POST' }),
  verifyAll: () => request<VerifyResult>('/api/storage/verify', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scope: 'ALL_CHUNKS' }),
  }),
  compare: (id: string, from: number, to: number) => request<Comparison>(
    `/api/files/${id}/versions/compare?from=${from}&to=${to}`,
  ),
  upload: (file: File, strategy: ChunkingStrategy, fileId?: string) => {
    const body = new FormData()
    body.append('file', file)
    const path = fileId ? `/api/files/${fileId}/versions` : '/api/files'
    return request<UploadResult>(`${path}?strategy=${strategy}`, { method: 'POST', body })
  },
  delete: (id: string) => request<void>(`/api/files/${id}`, { method: 'DELETE' }),
  downloadUrl: (id: string, version?: number) => `${API}/api/files/${id}${
    version ? `/versions/${version}` : ''
  }/download`,
}
