// 가짜 데이터용 사진 내려받기 (기획서 §8.4)
//
//   node seed-media/download.mjs          # 300장 (기본)
//   node seed-media/download.mjs 600      # 장수 지정
//
// 출처: Lorem Picsum (https://picsum.photos) — 원본은 Unsplash 사진이며 Unsplash License로 무료 사용 가능.
// API 키가 필요 없다. 받은 사진은 seed-media/photos/ 에 저장되고 Git에는 올라가지 않는다(.gitignore).
// 이미 받은 파일은 건너뛰므로 여러 번 실행해도 된다.

import { mkdir, writeFile, access } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const COUNT = Number(process.argv[2] ?? 300)
const ROOT = dirname(fileURLToPath(import.meta.url))
const OUT = join(ROOT, 'photos')
const CONCURRENCY = 6

// 인스타그램 게시물 비율: 세로 4:5가 가장 흔하고, 정사각형·가로가 섞인다
const RATIOS = [
  { name: '4:5', w: 1080, h: 1350, tw: 480, th: 600, weight: 7 },
  { name: '1:1', w: 1080, h: 1080, tw: 480, th: 480, weight: 2 },
  { name: '1.91:1', w: 1080, h: 566, tw: 480, th: 252, weight: 1 },
]

function ratioFor(index) {
  // 결정적으로 배분 (10장마다 4:5 7장, 1:1 2장, 가로 1장)
  const slot = index % 10
  return slot < 7 ? RATIOS[0] : slot < 9 ? RATIOS[1] : RATIOS[2]
}

async function exists(path) {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

async function fetchRetry(url, tries = 3) {
  for (let i = 1; ; i++) {
    try {
      const res = await fetch(url, { redirect: 'follow' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res
    } catch (e) {
      if (i >= tries) throw e
      await new Promise((r) => setTimeout(r, 800 * i))
    }
  }
}

async function download(url, path) {
  if (await exists(path)) return false
  const res = await fetchRetry(url)
  await writeFile(path, Buffer.from(await res.arrayBuffer()))
  return true
}

async function main() {
  await mkdir(OUT, { recursive: true })

  // 사진 목록 (작가 이름 포함)
  const list = []
  for (let page = 1; list.length < COUNT; page++) {
    const res = await fetchRetry(`https://picsum.photos/v2/list?page=${page}&limit=100`)
    const items = await res.json()
    if (items.length === 0) break
    list.push(...items)
  }
  const photos = list.slice(0, COUNT)
  console.log(`사진 ${photos.length}장을 받습니다 → ${OUT}`)

  const manifest = []
  let done = 0
  let fetched = 0
  let next = 0
  async function worker() {
    while (next < photos.length) {
      const index = next++
      const p = photos[index]
      const r = ratioFor(index)
      const file = `${p.id}.jpg`
      const thumb = `${p.id}_t.jpg`
      try {
        if (await download(`https://picsum.photos/id/${p.id}/${r.w}/${r.h}.jpg`, join(OUT, file))) fetched++
        await download(`https://picsum.photos/id/${p.id}/${r.tw}/${r.th}.jpg`, join(OUT, thumb))
        manifest[index] = { file, thumb, width: r.w, height: r.h, ratio: r.name, author: p.author, source: p.url }
      } catch (e) {
        console.warn(`  건너뜀 id=${p.id}: ${e.message}`)
      }
      done++
      if (done % 25 === 0 || done === photos.length) console.log(`  ${done}/${photos.length}`)
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker))

  const ok = manifest.filter(Boolean)
  await writeFile(join(OUT, 'manifest.json'), JSON.stringify(ok, null, 1))

  const credits = [
    '# 가짜 데이터 사진 출처',
    '',
    `아래 ${ok.length}장은 [Lorem Picsum](https://picsum.photos)에서 받은 사진입니다.`,
    '원본은 [Unsplash](https://unsplash.com)에 올라온 사진이며 [Unsplash License](https://unsplash.com/license)에 따라 무료로 쓸 수 있습니다.',
    '연구용 데모의 가짜 게시물에만 쓰며, 사진 속 인물·장소는 가짜 사용자와 관계가 없습니다.',
    '',
    '| 파일 | 작가 | 원본 |',
    '|---|---|---|',
    ...ok.map((m) => `| ${m.file} | ${m.author} | ${m.source} |`),
    '',
  ].join('\n')
  await writeFile(join(ROOT, 'CREDITS.md'), credits)

  console.log(`완료: ${ok.length}장 (새로 받음 ${fetched}장). manifest.json, CREDITS.md 작성`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
