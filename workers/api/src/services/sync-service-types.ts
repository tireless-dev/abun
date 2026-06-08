export interface BatchRequest<T> {
  items: T[];
}

export interface PullResponse<T> {
  items: T[];
  next_cursor: number;
  has_more: boolean;
}

export interface MutableSyncEntity {
  hlc_map?: Record<string, string>;
  dirty_fields?: string[];
  accepted_fields?: string[] | null;
  rejected_fields?: string[] | null;
  server_version?: number;
  server_updated_at?: string | null;
  created_at?: string | null;
}

export interface MutableSyncService<T extends MutableSyncEntity> {
  list(userId: string, cursor: number, limit: number): Promise<PullResponse<T>> | PullResponse<T>;
  push(userId: string, items: T[]): Promise<T[]> | T[];
}
