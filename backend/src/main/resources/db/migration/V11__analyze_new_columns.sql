-- V9·V10에서 새로 넣은 열(archived_at·pinned_at·vanish …)은 통계가 없어서, "archived_at is null"을 0.5%만 맞는다고 보고
-- 릴스 추천 쿼리 계획이 틀어졌다(75ms → 1.7초). 기존 DB에서도 바로 통계를 만든다
analyze posts;
analyze messages;
analyze conversation_members;
analyze conversations;
