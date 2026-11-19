import { useEffect, useState } from 'react'

const EVENT = 'yangstagram:toast'

/** 화면 아래에 잠깐 뜨는 안내 메시지. 어디서든 toast('메시지')로 호출. */
export function toast(message: string) {
  window.dispatchEvent(new CustomEvent<string>(EVENT, { detail: message }))
}

export function Toaster() {
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    let timer: number | undefined
    const onToast = (e: Event) => {
      setMessage((e as CustomEvent<string>).detail)
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setMessage(null), 2600)
    }
    window.addEventListener(EVENT, onToast)
    return () => {
      window.removeEventListener(EVENT, onToast)
      window.clearTimeout(timer)
    }
  }, [])

  if (!message) return null
  return (
    <div
      role="status"
      className="fixed bottom-20 left-1/2 z-50 w-max max-w-[90vw] -translate-x-1/2 rounded-lg bg-text px-4 py-2.5 text-sm text-bg shadow-lg md:bottom-8"
    >
      {message}
    </div>
  )
}
