import { useParams } from 'react-router'
import { Hash, MapPin } from 'lucide-react'
import { GridTiles, InfiniteGrid } from '../../components/post/Grid'
import { formatCount } from '../../lib/format'
import { usePlacePage, useTagPage } from '../../lib/social'
import { TopicHeader } from './SearchPage'

/** 해시태그 페이지: 인기 게시물 9개 + 최근 게시물. 볼 수 없는(비공개) 계정의 글은 빠진다 */
export function TagPage() {
  const { tag = '' } = useParams()
  const { data, isError, error } = useTagPage(tag)
  if (isError) return <p className="p-10 text-center text-sm text-muted">{error.message}</p>
  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <TopicHeader icon={<Hash size={40} />} title={`#${data?.name ?? tag}`} subtitle={`게시물 ${formatCount(data?.postCount ?? 0)}개`} />
      <Section title="인기 게시물">{data && <GridTiles items={data.top} />}</Section>
      <Section title="최근 게시물">
        <InfiniteGrid queryKey={['tag', tag]} url={`/api/tags/${encodeURIComponent(tag)}/posts`} empty={<Empty />} />
      </Section>
    </div>
  )
}

export function PlacePage() {
  const { id = '' } = useParams()
  const { data, isError, error } = usePlacePage(id)
  if (isError) return <p className="p-10 text-center text-sm text-muted">{error.message}</p>
  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <TopicHeader
        icon={<MapPin size={40} />}
        title={data?.name ?? '장소'}
        subtitle={`${data?.city ? `${data.city} · ` : ''}게시물 ${formatCount(data?.postCount ?? 0)}개`}
      />
      <Section title="인기 게시물">{data && <GridTiles items={data.top} />}</Section>
      <Section title="최근 게시물">
        <InfiniteGrid queryKey={['place', id]} url={`/api/locations/${id}/posts`} empty={<Empty />} />
      </Section>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-6">
      <h2 className="px-4 pb-3 text-sm font-semibold text-muted md:px-0">{title}</h2>
      {children}
    </section>
  )
}

function Empty() {
  return <p className="py-10 text-center text-sm text-muted">볼 수 있는 게시물이 없습니다.</p>
}
