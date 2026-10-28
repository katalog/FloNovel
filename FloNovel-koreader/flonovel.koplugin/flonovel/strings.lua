local en = {
    chapters = "Chapter navigation", list = "Chapters", next_chapter = "Next chapter",
    previous_chapter = "Previous chapter", next_part = "Next chapter subdivision",
    previous_part = "Previous chapter subdivision", divisions = "Subdivisions per chapter",
    unavailable = "FloNovel could not map this position exactly: %1",
    no_chapters = "No ## chapter markers found.", disabled = "Chapter navigation is disabled.",
    not_txt = "Open a UTF-8 TXT book to use FloNovel chapter navigation.",
    too_large = "This book exceeds the 16 MiB mapping limit.",
    files = "Dropbox file synchronization", position = "Reading position sharing",
    sync_files = "Synchronize Dropbox files now", sync_position = "Share reading position now",
    connect = "Check connection and load shared secret", setup = "Configure the connection with tools/connect.py on your PC, then copy config.json to KOReader settings/flonovel/.",
    busy = "FloNovel is already synchronizing.", syncing = "FloNovel: synchronizing… Tap to cancel.",
    connected = "Connection verified. You can enable file synchronization and position sharing independently.",
    files_done = "Downloaded: %1; deleted on Dropbox: %2; recovered: %3; deferred: %4.",
    position_done = "Reading position requests sent: %1; deferred: %2.",
    remote_position = "Another device has a later position (%1 characters). Jump there?",
    network_failed = "FloNovel synchronization deferred: %1", cancelled = "Synchronization cancelled; unfinished work will be retried.",
    reset_scope = "Reset synchronization state for a changed account or library",
    reset_confirm = "Reset file bases and unsent position requests? Existing books are preserved. Untracked local files will be deferred at the next synchronization.",
    deletion_confirm = "FloNovel paused because %1. This plan deletes or recovers %2 files. Continue after rechecking this exact plan?",
    preformatted = "Set TXT layout to preformatted (as-is), then reopen this book.",
    status = "Synchronization status", status_ok = "No synchronization error has been recorded in this session.",
}
local ko = {
    chapters = "챕터 이동", list = "챕터 목록", next_chapter = "다음 챕터",
    previous_chapter = "이전 챕터", next_part = "다음 챕터 분할점",
    previous_part = "이전 챕터 분할점", divisions = "챕터당 분할 수",
    unavailable = "FloNovel에서 이 위치를 정확히 대응할 수 없습니다: %1",
    no_chapters = "## 챕터 마커를 찾지 못했습니다.", disabled = "챕터 이동이 꺼져 있습니다.",
    not_txt = "FloNovel 챕터 이동을 사용하려면 UTF-8 TXT 책을 여세요.",
    too_large = "이 책은 위치 변환의 16 MiB 제한을 초과합니다.",
    files = "Dropbox 파일 동기화", position = "읽은 위치 공유",
    sync_files = "지금 Dropbox 파일 동기화", sync_position = "지금 읽은 위치 공유",
    connect = "연결 확인 및 공유 시크릿 읽기", setup = "PC에서 tools/connect.py로 연결한 뒤 config.json을 KOReader의 settings/flonovel/에 복사하세요.",
    busy = "FloNovel 동기화가 진행 중입니다.", syncing = "FloNovel 동기화 중… 화면을 누르면 취소합니다.",
    connected = "연결을 확인했습니다. 파일 동기화와 위치 공유를 각각 켤 수 있습니다.",
    files_done = "다운로드: %1; Dropbox 삭제: %2; 복구 보관: %3; 보류: %4.",
    position_done = "위치 전송: %1; 보류: %2.",
    remote_position = "다른 기기에 더 뒤의 읽은 위치가 있습니다(%1자). 이동할까요?",
    network_failed = "FloNovel 동기화 보류: %1", cancelled = "동기화를 취소했습니다. 미완료 작업은 다음에 재시도합니다.",
    reset_scope = "계정·서재 변경 후 동기화 상태 초기화",
    reset_confirm = "파일 기준 상태와 미전송 위치 요청을 초기화할까요? 책은 보존합니다. 다음 동기화에서 추적하지 않은 로컬 파일은 보류합니다.",
    deletion_confirm = "%1 사유로 중단했습니다. 삭제 또는 복구 보관할 파일은 %2개입니다. 동일한 계획인지 다시 검사한 뒤 진행할까요?",
    preformatted = "TXT 레이아웃을 원문 그대로로 설정한 뒤 책을 다시 여세요.",
    status = "동기화 상태", status_ok = "이번 실행에서 기록된 동기화 오류가 없습니다.",
}
return function(key, ...)
    local language = G_reader_settings and G_reader_settings:readSetting("language") or "en"
    local value = (language:match("^ko") and ko[key]) or en[key] or key
    local args = { ... }
    value = value:gsub("%%([1-9])", function(index) return tostring(args[tonumber(index)] or ("%" .. index)) end)
    return value
end
