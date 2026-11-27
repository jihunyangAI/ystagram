// 가짜 데이터 사진·릴스 영상을 Supabase Storage(공개 버킷)에 한 번 올린다.
//
//   SUPABASE_URL=https://xxxx.supabase.co SUPABASE_KEY=<비밀 키> node deploy/upload-seed-media.mjs
//
// seed-media/photos/** → 버킷의 seed/**, seed-media/reels/** → reels/** (서버의 /media/seed, /media/reels 주소와 같음)
// 이미 있는 파일은 건너뛴다(409). 버킷이 없으면 공개 버킷으로 만든다.
import { readdir, readFile } from 'node:fs/promises'
import { join, relative, extname, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', 'seed-media')
const URL_ = (process.env.SUPABASE_URL ?? '').replace(/\/$/, '')
const KEY = process.env.SUPABASE_KEY ?? ''
const BUCKET = process.env.SUPABASE_BUCKET ?? 'media'
if (!URL_ || !KEY) {
  console.error('SUPABASE_URL, SUPABASE_KEY 환경 변수가 필요합니다')
  process.exit(1)
}
// 새 형식 비밀 키(sb_secret_…)는 apikey 머리글로만, 예전 service_role 키(JWT)는 둘 다
const auth = KEY.startsWith('sb_') ? { apikey: KEY } : { Authorization: `Bearer ${KEY}`, apikey: KEY }
const TYPES = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.mp4': 'video/mp4' }

async function ensureBucket() {
  const r = await fetch(`${URL_}/storage/v1/bucket/${BUCKET}`, { headers: auth })
  if (r.ok) return
  const c = await fetch(`${URL_}/storage/v1/bucket`, {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: BUCKET, name: BUCKET, public: true, file_size_limit: 50 * 1024 * 1024 }),
  })
  if (!c.ok) throw new Error(`버킷 만들기 실패 ${c.status}: ${await c.text()}`)
  console.log(`공개 버킷 "${BUCKET}"을 만들었습니다`)
}

async function* walk(dir) {
  for (const e of await readdir(dir, { withFileTypes: true })) {
    if (e.name.startsWith('.')) continue
    const p = join(dir, e.name)
    if (e.isDirectory()) yield* walk(p)
    else if (TYPES[extname(e.name).toLowerCase()]) yield p
  }
}

const jobs = []
for (const [local, prefix] of [['photos', 'seed'], ['reels', 'reels']]) {
  for await (const f of walk(join(ROOT, local))) jobs.push([f, `${prefix}/${relative(join(ROOT, local), f).replaceAll('\\', '/')}`])
}

await ensureBucket()
let done = 0, skipped = 0, failed = 0
async function upload([file, key]) {
  for (let attempt = 1; ; attempt++) {
    try {
      const r = await fetch(`${URL_}/storage/v1/object/${BUCKET}/${key}`, {
        method: 'POST',
        headers: { ...auth, 'Content-Type': TYPES[extname(file).toLowerCase()], 'Cache-Control': 'max-age=2592000' },
        body: await readFile(file),
      })
      if (r.ok) done++
      else if (r.status === 409 || (r.status === 400 && (await r.text()).includes('exists'))) skipped++
      else throw new Error(`${r.status} ${await r.text()}`)
      return
    } catch (e) {
      if (attempt >= 3) {
        failed++
        console.error('실패', key, e.message)
        return
      }
      await new Promise((res) => setTimeout(res, 1000 * attempt))
    }
  }
}
// 동시에 6개씩
let i = 0
await Promise.all(Array.from({ length: 6 }, async () => {
  while (i < jobs.length) {
    await upload(jobs[i++])
    const n = done + skipped + failed
    if (n % 100 === 0) console.log(`${n}/${jobs.length}`)
  }
}))
console.log(`끝: 올림 ${done}, 이미 있음 ${skipped}, 실패 ${failed} (전체 ${jobs.length})`)
process.exit(failed ? 1 : 0)
