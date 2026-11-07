// 学生录音在本机的暂存：录完立即写入浏览器 IndexedDB，服务器确认收到后才删除。
// 断网、刷新页面、浏览器崩溃后重新进入面试，会先把没交上去的录音补交，学生不用重答。
// 存不了（隐私模式、存储被禁用）时静默跳过，不影响正常上传。

const DB_NAME = "interview-recordings";
const STORE = "pending";

export interface PendingRecording {
  key: string;
  audio: Blob;
  /** 录音对应的题目内容，补交前核对，避免把重考前的旧录音交到新抽的题上 */
  tag: string;
  savedAt: number;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
      if (typeof indexedDB === "undefined") {
        reject(new Error("indexedDB unavailable"));
        return;
      }
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains(STORE)) {
          req.result.createObjectStore(STORE, { keyPath: "key" });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    }).catch((error) => {
      dbPromise = null;
      throw error;
    });
  }
  return dbPromise;
}

// 浏览器存储偶尔会卡住（磁盘满、被其他标签页占用），最多等 3 秒，绝不阻塞答题
const TIMEOUT_MS = 3000;

function run<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest): Promise<T | undefined> {
  const timeout = new Promise<undefined>((resolve) => setTimeout(() => resolve(undefined), TIMEOUT_MS));
  return Promise.race([timeout, runInDb<T>(mode, action)]);
}

function runInDb<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest): Promise<T | undefined> {
  return openDb()
    .then(
      (db) =>
        new Promise<T | undefined>((resolve, reject) => {
          const tx = db.transaction(STORE, mode);
          const req = action(tx.objectStore(STORE));
          tx.oncomplete = () => resolve(req.result as T);
          tx.onerror = () => reject(tx.error);
          tx.onabort = () => reject(tx.error);
        })
    )
    .catch(() => undefined);
}

/** 一段录音的键：学生 + 面试 + 题位（defense 或 题号-追问序号） */
export function recordingKey(userId: string | number, assessmentId: string | number, slot: string) {
  return `${userId}|${assessmentId}|${slot}`;
}

export function savePending(key: string, audio: Blob, tag: string) {
  return run<void>("readwrite", (store) => store.put({ key, audio, tag, savedAt: Date.now() }));
}

/** 取出与当前题目一致、且是近期录下的暂存录音 */
export async function loadPending(key: string, tag: string, maxAgeMs = 12 * 3600 * 1000) {
  const item = await run<PendingRecording>("readonly", (store) => store.get(key));
  if (!item || item.tag !== tag || Date.now() - item.savedAt > maxAgeMs || !item.audio?.size) return undefined;
  return item;
}

export function removePending(key: string) {
  return run<void>("readwrite", (store) => store.delete(key));
}

/** 删除某个学生在某场面试的全部暂存录音（面试提交后调用） */
export function clearPending(userId: string | number, assessmentId: string | number) {
  const prefix = recordingKey(userId, assessmentId, "");
  return run<void>("readwrite", (store) =>
    store.delete(IDBKeyRange.bound(prefix, prefix + "￿"))
  );
}
