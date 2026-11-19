import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="px-6 py-16 text-center">
      <h1 className="text-xl font-bold">죄송합니다. 페이지를 사용할 수 없습니다.</h1>
      <p className="mt-4 text-sm text-muted">
        클릭하신 링크가 잘못되었거나 페이지가 삭제되었습니다.{' '}
        <Link to="/" className="text-primary">
          Yangstagram으로 돌아가기
        </Link>
      </p>
    </div>
  )
}
