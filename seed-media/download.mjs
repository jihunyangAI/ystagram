// 가짜 데이터용 주제별 사진 내려받기 (기획서 §8.3)
//
//   node seed-media/download.mjs          # 사진 대상마다 최대 20장
//
// 출처: Openverse (https://openverse.org, WordPress 재단) 검색 API — API 키 없이 쓸 수 있다.
//  - StockSnap·rawpixel의 CC0(퍼블릭 도메인 기증) 사진만 받는다 → 출처 표시 없이 자유롭게 쓸 수 있음.
//  - 키 없는 사용 한도: 1분 20회, 하루 200회. 검색 결과는 photos/.cache/ 에 저장해 두므로
//    다시 실행하면 API를 부르지 않고 빠진 파일만 받는다.
//
// 결과: seed-media/photos/<주제>/<id>.jpg + manifest.json(주제·대상·한국어 이름 포함) + credits.json
// 사진은 Git에 올리지 않는다(.gitignore). 사진마다 "무엇을 찍었는지"(대상)를 남겨서 시드가 캡션을 사진에 맞춘다.

import { mkdir, writeFile, readFile, access, readdir, rm } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = dirname(fileURLToPath(import.meta.url))
const OUT = join(ROOT, 'photos')
const CACHE = join(OUT, '.cache')
const PER_SUBJECT = 20
const UA = { 'User-Agent': 'yangstagram-research-seed/1.0 (fake data for a local research demo)' }

/**
 * 주제(Topic enum) → 사진 대상 [검색어, 한국어 이름]. 한국어 이름은 캡션의 {item} 자리에 들어간다.
 * 검색어는 짧을수록 결과가 많다 (두 단어 넘으면 결과가 거의 없음).
 */
export const SUBJECTS = {
  FOOD: [['burger', '수제버거'], ['pizza', '피자'], ['pasta', '파스타'], ['sushi', '초밥'], ['ramen', '라멘'],
    ['steak', '스테이크'], ['brunch', '브런치'], ['noodles', '국수'], ['tacos', '타코'], ['salad', '샐러드']],
  CAFE: [['latte', '라떼'], ['coffee', '커피'], ['cafe', '카페'], ['croissant', '크루아상'], ['cake', '케이크'],
    ['donut', '도넛'], ['dessert', '디저트'], ['macaron', '마카롱']],
  TRAVEL: [['beach', '바다'], ['paris', '파리'], ['tokyo', '도쿄'], ['airplane', '비행기'], ['hotel', '호텔'],
    ['venice', '베네치아'], ['island', '섬'], ['suitcase', '여행 가방']],
  FASHION: [['outfit', '오늘 착장'], ['fashion', '룩'], ['sneakers', '운동화'], ['handbag', '가방'],
    ['jacket', '자켓'], ['sunglasses', '선글라스']],
  FITNESS: [['gym', '헬스장'], ['running', '러닝'], ['yoga', '요가'], ['workout', '운동'], ['dumbbell', '덤벨']],
  PETS: [['dog', '댕댕이'], ['puppy', '아기 강아지'], ['cat', '냥이'], ['kitten', '아기 고양이'], ['pug', '퍼그']],
  PHOTO: [['camera', '카메라'], ['sunset', '노을'], ['neon', '네온사인'], ['street', '골목'], ['skyline', '야경']],
  MUSIC: [['concert', '공연'], ['guitar', '기타'], ['vinyl', 'LP'], ['headphones', '헤드폰'], ['piano', '피아노']],
  DEV: [['laptop', '노트북'], ['code', '코드'], ['desk', '책상 세팅'], ['keyboard', '키보드']],
  PARENTING: [['baby', '아기'], ['toddler', '우리 아이'], ['kids', '아이들'], ['family', '가족']],
  BEAUTY: [['makeup', '메이크업'], ['skincare', '스킨케어'], ['lipstick', '립스틱'], ['manicure', '네일'], ['perfume', '향수']],
  BOOKS: [['books', '책'], ['reading', '독서'], ['library', '도서관'], ['bookstore', '서점']],
  ART: [['painting', '그림'], ['drawing', '드로잉'], ['gallery', '전시'], ['watercolor', '수채화']],
  NATURE: [['forest', '숲'], ['mountain', '산'], ['flowers', '꽃'], ['lake', '호수'], ['hiking', '등산']],
  SPORTS: [['soccer', '축구'], ['basketball', '농구'], ['stadium', '직관'], ['tennis', '테니스'], ['surfing', '서핑']],
  GAMING: [['gaming', '게임'], ['controller', '패드'], ['arcade', '오락실'], ['console', '게임기']],
  MOVIE: [['cinema', '영화관'], ['popcorn', '팝콘'], ['film', '영화']],
  COOKING: [['cooking', '요리'], ['baking', '베이킹'], ['kitchen', '주방'], ['bread', '빵']],
  INTERIOR: [['bedroom', '침실'], ['interior', '인테리어'], ['sofa', '소파'], ['houseplants', '식물']],
  CAR: [['car', '차'], ['roadtrip', '로드트립'], ['vintage car', '클래식카'], ['motorcycle', '오토바이']],
}

/**
 * rawpixel에는 박물관 소장품(옛 그림·판화·포스터)이 사진으로 섞여 있어서 뺀다:
 * 작가(기관) 이름이 붙었거나 제목이 작품처럼 보이면 제외. StockSnap은 모두 사진이라 그대로 쓴다.
 */
const ARTWORK =
  /museum|gallery|drawing|watercolou?r|painting|illustration|vintage|antique|engraving|lithograph|album|print|poster|\(\d{4}|\d{4}\)|sketch|portrait of|woodblock|ukiyo|archive|library of congress/i
function looksLikePhoto(r) {
  // 스톡 사진은 제목에 Free·photo·image가 붙는다 (박물관 소장품은 작품 제목)
  return r.source === 'stocksnap' || (!r.creator && !ARTWORK.test(r.title ?? '') && /(free|photo|image)/i.test(r.title ?? ''))
}

async function exists(path) {
  try {
    await access(path)
    return true
  } catch {
    return false
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let lastApiCall = 0

/** 검색 (결과는 캐시). 1분 20회 한도를 넘지 않게 3.2초 간격 */
async function search(query) {
  const cacheFile = join(CACHE, `${query.replace(/[^a-z0-9]+/gi, '_')}.json`)
  if (await exists(cacheFile)) return JSON.parse(await readFile(cacheFile, 'utf8'))
  const wait = lastApiCall + 3200 - Date.now()
  if (wait > 0) await sleep(wait)
  lastApiCall = Date.now()
  const url =
    'https://api.openverse.org/v1/images/?' +
    new URLSearchParams({
      q: query,
      page_size: String(PER_SUBJECT),
      source: 'stocksnap,rawpixel',
      license: 'cc0,pdm',
      category: 'photograph',
      mature: 'false',
    })
  const res = await fetch(url, { headers: UA })
  if (res.status === 429) throw new Error('Openverse 하루 호출 한도(200회)를 넘었습니다. 내일 다시 실행하면 이어서 받습니다.')
  if (!res.ok) throw new Error(`검색 실패 ${query}: HTTP ${res.status}`)
  const json = await res.json()
  const results = json.results.map((r) => ({
    id: r.id,
    url: r.url,
    width: r.width,
    height: r.height,
    title: r.title,
    creator: r.creator,
    license: r.license,
    source: r.source,
    landing: r.foreign_landing_url,
  }))
  await writeFile(cacheFile, JSON.stringify(results))
  return results
}

async function download(url, path) {
  if (await exists(path)) return false
  for (let i = 1; ; i++) {
    try {
      const res = await fetch(url, { headers: UA, redirect: 'follow' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const buf = Buffer.from(await res.arrayBuffer())
      if (buf.length < 5000) throw new Error('파일이 너무 작음')
      await writeFile(path, buf)
      return true
    } catch (e) {
      if (i >= 3) throw e
      await sleep(800 * i)
    }
  }
}

/** 받은 크기 (원본 비율 그대로 960~1024px로 줄여 보내 준다) */
function scaled(w, h, url) {
  const target = url.includes('rawpixel') ? 1024 : 960
  if (url.includes('/img-thumbs/960w/')) return [960, Math.round((h * 960) / w)]
  const s = target / Math.max(w, h)
  return s < 1 ? [Math.round(w * s), Math.round(h * s)] : [w, h]
}

/** 화면 비율 묶음 (캐러셀은 같은 묶음끼리): 세로 4:5, 정사각 1:1, 가로 1.91:1 */
function ratioOf(w, h) {
  const r = w / h
  return r < 0.9 ? '4:5' : r < 1.25 ? '1:1' : '1.91:1'
}

async function main() {
  await mkdir(CACHE, { recursive: true })
  const manifest = []
  const credits = []
  const seen = new Set()
  let fetched = 0
  const jobs = []

  for (const [topic, subjects] of Object.entries(SUBJECTS)) {
    await mkdir(join(OUT, topic.toLowerCase()), { recursive: true })
    for (const [query, label] of subjects) {
      let results
      try {
        results = await search(query)
      } catch (e) {
        console.warn(`  ${e.message}`)
        if (String(e.message).includes('한도')) break
        continue
      }
      let n = 0
      for (const r of results) {
        if (seen.has(r.id) || !r.url || !r.width || !r.height) continue
        seen.add(r.id)
        const file = `${topic.toLowerCase()}/${r.id}.jpg`
        if (!looksLikePhoto(r)) {
          await rm(join(OUT, file), { force: true })
          continue
        }
        const [w, h] = scaled(r.width, r.height, r.url)
        jobs.push({ r, file, entry: { file, thumb: file, width: w, height: h, ratio: ratioOf(w, h), topic, subject: query, label } })
        n++
      }
      console.log(`${topic.padEnd(10)} ${query.padEnd(12)} ${n}장`)
    }
  }

  // 사진 파일은 CDN(StockSnap·rawpixel)에서 받으므로 API 한도와 상관없다. 6개씩 동시에
  let next = 0
  let done = 0
  async function worker() {
    while (next < jobs.length) {
      const job = jobs[next++]
      try {
        if (await download(job.r.url, join(OUT, job.file))) fetched++
        manifest.push(job.entry)
        credits.push({ file: job.file, title: job.r.title, creator: job.r.creator, license: job.r.license, source: job.r.source, url: job.r.landing })
      } catch (e) {
        console.warn(`  건너뜀 ${job.file}: ${e.message}`)
      }
      if (++done % 100 === 0) console.log(`  ${done}/${jobs.length}`)
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker))

  manifest.sort((a, b) => a.file.localeCompare(b.file))
  credits.sort((a, b) => a.file.localeCompare(b.file))
  await writeFile(join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 1))
  await writeFile(join(OUT, 'credits.json'), JSON.stringify(credits, null, 1))

  // 목록에서 빠진 사진(검색어를 바꿨거나 걸러진 것)과 예전 방식(Lorem Picsum, 숫자.jpg) 파일 정리
  let removed = 0
  const listed = new Set(manifest.map((m) => m.file))
  for (const topic of Object.keys(SUBJECTS)) {
    const dir = topic.toLowerCase()
    for (const f of await readdir(join(OUT, dir))) {
      if (!listed.has(`${dir}/${f}`)) {
        await rm(join(OUT, dir, f))
        removed++
      }
    }
  }
  for (const f of await readdir(OUT)) {
    if (/^\d+(_t)?\.jpg$/.test(f)) {
      await rm(join(OUT, f))
      removed++
    }
  }
  console.log(`완료: ${manifest.length}장 (새로 받음 ${fetched}장, 예전 사진 ${removed}개 정리). manifest.json, credits.json 작성`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
