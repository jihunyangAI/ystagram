import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { Toggle } from '../../components/post/CaptionField'
import { Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api, ApiError } from '../../lib/api'
import { ME_KEY } from '../../lib/auth'
import type { EditableProfile, Me } from '../../lib/types'

/** 정사각형 가운데를 잘라 512px JPEG로 */
async function squareCrop(file: File): Promise<Blob> {
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image()
      i.onload = () => resolve(i)
      i.onerror = () => reject(new Error('열 수 없는 사진입니다'))
      i.src = url
    })
    const side = Math.min(img.naturalWidth, img.naturalHeight)
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 512
    canvas
      .getContext('2d')!
      .drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, 512, 512)
    return await new Promise((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('사진을 만들지 못했습니다'))), 'image/jpeg', 0.9),
    )
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** 프로필 편집 (/accounts/edit) */
export function EditProfilePage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, isLoading } = useQuery({
    queryKey: ['my-profile'],
    queryFn: () => api.get<EditableProfile>('/api/me/profile'),
    gcTime: 0,
  })
  const [form, setForm] = useState({ fullName: '', username: '', bio: '', pronouns: '', website: '' })
  const [photoMenu, setPhotoMenu] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (data) {
      setForm({
        fullName: data.me.user.fullName ?? '',
        username: data.me.user.username,
        bio: data.bio ?? '',
        pronouns: data.pronouns ?? '',
        website: data.website ?? '',
      })
    }
  }, [data])

  const applyMe = (me: Me) => {
    qc.setQueryData(ME_KEY, me)
    qc.invalidateQueries({ queryKey: ['profile'] })
    qc.invalidateQueries({ queryKey: ['feed'] })
  }

  const save = useMutation({
    mutationFn: () => api.patch<EditableProfile>('/api/me/profile', form),
    onSuccess: (res) => {
      applyMe(res.me)
      qc.setQueryData(['my-profile'], res)
      toast('프로필이 저장되었습니다')
      navigate(`/${res.me.user.username}`)
    },
    onError: (e) => toast(e.message),
  })

  const avatar = useMutation({
    mutationFn: async (file: File | null) => {
      if (!file) return api.delete<Me>('/api/me/avatar')
      const form = new FormData()
      form.append('file', await squareCrop(file), 'avatar.jpg')
      return api.upload<Me>('PUT', '/api/me/avatar', form)
    },
    onSuccess: (me) => {
      applyMe(me)
      qc.setQueryData<EditableProfile>(['my-profile'], (p) => (p ? { ...p, me } : p))
      toast('프로필 사진을 바꿨습니다')
    },
    onError: (e) => toast(e.message),
  })

  if (isLoading || !data) return <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>

  const fieldError = save.error instanceof ApiError ? save.error.code : null
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: k === 'username' ? e.target.value.toLowerCase() : e.target.value }))

  return (
    <div className="mx-auto w-full max-w-[640px]">
      <header className="sticky top-0 z-20 flex h-11 items-center border-b border-border bg-bg px-2 md:hidden">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-1">
          <ChevronLeft size={28} />
        </button>
        <h1 className="flex-1 pr-9 text-center font-semibold">프로필 편집</h1>
      </header>

      <form
        className="space-y-6 px-4 py-6 md:py-10"
        onSubmit={(e) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <h1 className="hidden text-xl font-bold md:block">프로필 편집</h1>

        <div className="flex items-center gap-4 rounded-2xl bg-surface-2 p-4">
          <Avatar user={data.me.user} size={56} />
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{data.me.user.username}</p>
            <p className="truncate text-sm text-muted">{data.me.user.fullName}</p>
          </div>
          <button type="button" className="btn btn-primary" disabled={avatar.isPending} onClick={() => setPhotoMenu(true)}>
            {avatar.isPending ? '올리는 중…' : '사진 변경'}
          </button>
        </div>

        <Field label="이름" hint="이름은 14일 동안 최대 두 번까지 변경할 수 있습니다. (연구용 데모에서는 제한 없음)">
          <input className="input" value={form.fullName} maxLength={64} onChange={set('fullName')} />
        </Field>
        <Field
          label="사용자 이름"
          hint="영문 소문자·숫자·마침표·밑줄, 30자까지"
          error={fieldError === 'INVALID_USERNAME' || fieldError === 'USERNAME_TAKEN' ? save.error?.message : undefined}
        >
          <input className="input" value={form.username} maxLength={30} onChange={set('username')} autoCapitalize="none" />
        </Field>
        <Field label="웹사이트" error={fieldError === 'INVALID_WEBSITE' ? save.error?.message : undefined}>
          <input className="input" value={form.website} maxLength={200} onChange={set('website')} placeholder="https://" />
        </Field>
        <Field label="소개" error={fieldError === 'BIO_TOO_LONG' ? save.error?.message : undefined}>
          <textarea className="input min-h-20 resize-none" value={form.bio} maxLength={150} onChange={set('bio')} />
          <span className="block text-right text-xs text-muted">{form.bio.length}/150</span>
        </Field>
        <Field label="대명사" hint="예: 그녀, 그, 그들 — 팔로워에게만 보이게 하는 설정은 M3">
          <input className="input" value={form.pronouns} maxLength={30} onChange={set('pronouns')} />
        </Field>

        <div className="flex justify-end">
          <button type="submit" className="btn btn-primary min-w-28" disabled={save.isPending}>
            {save.isPending ? '저장 중…' : '제출'}
          </button>
        </div>
      </form>

      <PrivacySection />

      <Modal open={photoMenu} onClose={() => setPhotoMenu(false)} bare title="프로필 사진 바꾸기">
        <p className="border-b border-border py-6 text-center text-lg font-semibold">프로필 사진 바꾸기</p>
        <SheetButton
          bold
          onClick={() => {
            setPhotoMenu(false)
            fileInput.current?.click()
          }}
        >
          <span className="text-primary">사진 업로드</span>
        </SheetButton>
        {data.me.user.avatarUrl && (
          <SheetButton
            danger
            onClick={() => {
              setPhotoMenu(false)
              avatar.mutate(null)
            }}
          >
            현재 사진 삭제
          </SheetButton>
        )}
        <SheetButton onClick={() => setPhotoMenu(false)}>취소</SheetButton>
      </Modal>
      <input
        ref={fileInput}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) avatar.mutate(file)
          e.target.value = ''
        }}
      />
    </div>
  )
}

/** 계정 공개 범위 (분석 문서 §13). 공개로 바꾸면 대기 중인 팔로우 요청이 모두 승인된다 */
function PrivacySection() {
  const qc = useQueryClient()
  const { data } = useQuery({ queryKey: ['privacy'], queryFn: () => api.get<{ isPrivate: boolean }>('/api/me/privacy') })
  const [confirm, setConfirm] = useState<boolean | null>(null)
  const save = useMutation({
    mutationFn: (isPrivate: boolean) => api.put<{ isPrivate: boolean }>('/api/me/privacy', { isPrivate }),
    onSuccess: (r) => {
      qc.setQueryData(['privacy'], r)
      qc.invalidateQueries({ queryKey: ME_KEY })
      qc.invalidateQueries({ queryKey: ['profile'] })
      qc.invalidateQueries({ queryKey: ['notif-unread'] })
      toast(r.isPrivate ? '비공개 계정으로 바꿨습니다' : '공개 계정으로 바꿨습니다')
      setConfirm(null)
    },
    onError: (e) => toast(e.message),
  })
  if (!data) return null
  return (
    <section className="mt-10 border-t border-border pt-6">
      <h2 className="mb-4 text-lg font-bold">계정 공개 범위</h2>
      <Toggle
        checked={data.isPrivate}
        onChange={(v) => setConfirm(v)}
        label="비공개 계정"
        help="비공개 계정이면 회원님이 승인한 팔로워만 사진과 동영상을 볼 수 있습니다. 공개로 바꾸면 대기 중인 요청이 모두 승인됩니다."
      />
      <Modal open={confirm !== null} onClose={() => setConfirm(null)} title={confirm ? '비공개 계정으로 전환할까요?' : '공개 계정으로 전환할까요?'}>
        <div className="space-y-3 p-5 text-sm">
          <p className="text-muted">
            {confirm
              ? '승인한 팔로워만 회원님의 사진과 동영상을 볼 수 있습니다. 기존 팔로워에게는 영향이 없습니다.'
              : '누구나 회원님의 사진과 동영상을 볼 수 있게 됩니다. 대기 중인 팔로우 요청이 모두 승인됩니다.'}
          </p>
          <button
            className="btn btn-primary w-full"
            disabled={save.isPending}
            onClick={() => confirm !== null && save.mutate(confirm)}
          >
            {confirm ? '비공개로 전환' : '공개로 전환'}
          </button>
        </div>
      </Modal>
    </section>
  )
}

function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1.5 block font-semibold">{label}</span>
      {children}
      {error ? <span className="mt-1 block text-xs text-danger">{error}</span> : hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  )
}
