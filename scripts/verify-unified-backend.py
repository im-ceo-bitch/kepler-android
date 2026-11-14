#!/usr/bin/env python3
"""Static routing audit; no production writes, secrets, or network dependencies."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/iumrah/beta'
read=lambda p:(src/p).read_text()
config=read('core/config/AppConfig.kt')
api=read('core/network/APIClient.kt')
analytics=read('core/analytics/IumrahIntelligence.kt')
flow=read('data/advisor/UmrahFlowService.kt')
monitor=read('core/network/IumrahConnectivityMonitor.kt')
assert 'API_BASE_URL = "https://iumrah-backend.helloiumrah.workers.dev"' in config
assert 'AppConfig.apiUrl(path)' in api and 'private val baseUrl' not in api
assert '.followRedirects(false)' in api and '.followSslRedirects(false)' in api
assert 'AppConfig.INTELLIGENCE_EVENTS_URL' in analytics and 'instanceFollowRedirects = false' in analytics
assert 'AppConfig.HEALTH_URL' in monitor and '.optBoolean("ok", false)' in monitor
assert 'ANON_KEY' not in flow and '/rest/v1/' not in flow and 'offlineCatalog' in flow
# All service API path literals use the shared /api prefix. Keep their existing contracts.
paths=set()
for p in src.rglob('*.kt'):
    code=p.read_text()
    assert 'supabase.co/rest/v1' not in code, p
    assert not re.search(r'https://[^"\s]+\.workers\.dev/api/',code),p
    if p.is_relative_to(src/'data'):
        paths.update(re.findall(r'"(/api/[^"\n]+)"',code))
assert len(paths)>70, len(paths)
print(f'Static audit: {len(paths)} unique API path literals, common URL resolver and no direct Supabase REST calls')
