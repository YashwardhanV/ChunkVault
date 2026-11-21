export type ChunkingStrategy = 'FIXED_SIZE' | 'CONTENT_DEFINED'

export interface StorageStats {
  logicalBytes: number
  physicalBytes: number
  deduplicatedBytes: number
  deduplicationRatio: number
  totalFiles: number
  totalFileVersions: number
  totalChunksReferenced: number
  uniqueChunks: number
  duplicateChunksAvoided: number
}

export interface FileSummary {
  id: string
  filename: string
  latestVersion: number
  logicalSize: number
  exclusivePhysicalBytes: number
  versions: number
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Version {
  id: string
  versionNumber: number
  totalSize: number
  fileHash: string
  contentType: string
  chunkCount: number
  chunkingStrategy: ChunkingStrategy
  createdAt: string
}

export interface ManifestChunk {
  index: number
  offset: number
  size: number
  hash: string
  referenceCount: number
  shared: boolean
}

export interface FileDetail {
  id: string
  filename: string
  createdAt: string
  versions: Version[]
  selectedVersion: Version
  manifest: ManifestChunk[]
}

export interface Comparison {
  fromVersion: number
  toVersion: number
  reusedChunks: number
  totalTargetChunks: number
  reuseRatio: number
  targetManifest: Array<{ index: number; hash: string; size: number; reused: boolean }>
}

export interface UploadResult {
  fileId: string
  versionId: string
  versionNumber: number
  filename: string
  totalSize: number
  fileHash: string
  chunkCount: number
  chunkingStrategy: ChunkingStrategy
  duplicateChunksReused: number
  createdAt: string
}

export interface GcStatus {
  candidateChunks: number
  bytesReclaimable: number
  lastRunAt: string | null
  lastChunksDeleted: number | null
  lastBytesReclaimed: number | null
  lastFailures: number | null
}

export interface GcResult {
  chunksScanned: number
  chunksDeleted: number
  orphanFilesDeleted: number
  bytesReclaimed: number
  failures: number
  completedAt: string
}

export interface VerifyItem {
  targetType: 'CHUNK' | 'FILE'
  target: string
  status: 'HEALTHY' | 'MISSING' | 'CORRUPTED'
  expectedHash: string
  actualHash: string | null
  size: number
  message: string
}

export interface VerifyResult {
  healthy: number
  missing: number
  corrupted: number
  items: VerifyItem[]
  completedAt: string
}
