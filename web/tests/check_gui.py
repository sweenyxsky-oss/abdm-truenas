import asyncio, json
from pathlib import Path
from playwright.async_api import async_playwright, expect

ROOT = Path(__file__).parent

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={'width': 1280, 'height': 1800})
        page = await context.new_page()
        errors = []
        requests = []
        page.on('pageerror', lambda e: errors.append(str(e)))

        # Build comprehensive dataset for regression testing
        downloads = [
            {'id': 1, 'name': 'TrueNAS-SCALE-26.iso', 'size': 2400000000, 'percent': 43, 'speed': 12500000, 'eta': 90, 'status': 'Downloading', 'queueId': 0, 'queueName': 'Main', 'categoryId': 1, 'dateAdded': 1791536400000, 'downloadLink': 'https://example.com/file.iso', 'folder': '/downloads'},
            {'id': 2, 'name': 'ABDM-release.zip', 'size': 85000000, 'percent': 100, 'speed': 0, 'eta': 0, 'status': 'Completed', 'queueId': 0, 'queueName': 'Main', 'categoryId': 2, 'dateAdded': 1791536100000},
            {'id': 3, 'name': 'Music.flac', 'size': 45000000, 'percent': 14, 'speed': 0, 'eta': -1, 'status': 'Paused', 'queueId': None, 'queueName': None, 'categoryId': 3, 'dateAdded': 1791536000000}
        ]
        # Add tasks 4-25 to test multi-page pagination (10 per page -> 3 pages)
        for i in range(4, 26):
            downloads.append({
                'id': i,
                'name': f'File-{i:02d}.zip',
                'size': 10000000 * i,
                'percent': 100 if i % 2 == 0 else 50,
                'speed': 0 if i % 2 == 0 else 500000,
                'eta': 0 if i % 2 == 0 else 120,
                'status': 'Completed' if i % 2 == 0 else 'Downloading',
                'queueId': 5 if i > 15 else 0,
                'queueName': 'Work' if i > 15 else 'Main',
                'categoryId': 2,
                'dateAdded': 1791530000000 + i * 1000
            })

        queues = [
            {'id': 0, 'name': 'Main', 'active': 1, 'queued': 1, 'total': 15, 'running': True, 'items': [1, 2] + list(range(4, 16)), 'activeDays': ['MONDAY'], 'schedulerEnabled': False},
            {'id': 5, 'name': 'Work', 'active': 0, 'queued': 2, 'total': 10, 'running': False, 'items': list(range(16, 26)), 'activeDays': ['MONDAY'], 'schedulerEnabled': False}
        ]
        categories = [{'id': i, 'name': name, 'acceptedFileTypes': [ext]} for i, (name, ext) in enumerate([('Applications', 'iso'), ('Compressed', 'zip'), ('Music', 'flac'), ('Videos', 'mp4'), ('Documents', 'pdf'), ('Pictures', 'png')], 1)]

        async def handler(route):
            path = route.request.url.split('8091')[-1].split('?')[0]
            requests.append((route.request.method, path))
            if path == '/ping': data = 'pong'
            elif path == '/downloads': data = downloads
            elif path == '/queues': data = queues
            elif path == '/categories': data = categories
            elif path == '/settings': data = {'downloadFolder': '/downloads', 'apiEnabled': True}
            elif path == '/browser/session': data = {'enabled': True, 'ready': True, 'token': 'test-token'}
            elif path.endswith('/parts'): data = []
            else: data = {}
            await route.fulfill(status=200, content_type='application/json', body=json.dumps(data))

        await page.route('**/*', lambda r: r.continue_() if '/js/' in r.request.url or '/css/' in r.request.url or '/assets/' in r.request.url or r.request.url.endswith(':8091/') else handler(r))
        await page.goto('http://localhost:8091/', wait_until='networkidle')
        await page.screenshot(path=str(ROOT / 'desktop.png'))

        # Verify initial page 1 item count
        assert await page.locator('.file-name').count() == 10

        # Verify Pagination stability across polling (does not jump back to page 1)
        await page.get_by_role('button', name='2', exact=True).click()
        p2_item = await page.locator('.file-name').first.inner_text()
        await page.wait_for_timeout(2000)
        assert await page.locator('.file-name').first.inner_text() == p2_item, "Pagination reset back to page 1 on background poll!"

        # Back to page 1
        await page.get_by_role('button', name='1', exact=True).click()

        # Filtering by Finished / All
        await page.get_by_role('button', name='Finished', exact=True).click()
        finished_count = await page.locator('.file-name').count()
        assert finished_count > 0
        statuses = await page.locator('.status-cell').all_inner_texts()
        assert all('Finished' in s for s in statuses)

        await page.get_by_role('button', name='All', exact=True).click()

        # Search
        await page.get_by_role('textbox', name='Search downloads', exact=True).fill('TrueNAS')
        assert await page.locator('.file-name').count() == 1
        await page.wait_for_timeout(1700)
        assert await page.get_by_role('textbox', name='Search downloads', exact=True).input_value() == 'TrueNAS'
        await page.get_by_role('textbox', name='Search downloads', exact=True).fill('')

        # Item actions
        await page.get_by_role('checkbox', name='Select TrueNAS-SCALE-26.iso', exact=True).check()
        await page.get_by_role('button', name='Pause', exact=True).click()
        assert ('POST', '/downloads/1/pause') in requests

        # Add URL Dialog: Check default queue is Main and URL input clears on subsequent opens
        await page.get_by_role('button', name='Add URL', exact=True).click()
        await expect(page.locator('#addDialog')).to_be_visible()
        await page.screenshot(path=str(ROOT / 'add-dialog.png'))
        assert await page.locator('#queueInput').input_value() == '0', "Main queue (value=0) should be default!"
        await page.locator('#urlInput').fill('https://example.com/test.zip')
        await page.get_by_role('button', name='Add download', exact=True).click()
        assert ('POST', '/start-headless-download') in requests

        # Reopen Add Dialog: URL input must be empty (cleared)
        await page.get_by_role('button', name='Add URL', exact=True).click()
        await expect(page.locator('#addDialog')).to_be_visible()
        assert await page.locator('#urlInput').input_value() == '', "Previous URL was not cleared in Add dialog!"
        await page.locator('#addDialog').get_by_role('button', name='Cancel').click()

        # Queue Sub-Navigation: Finished / Unfinished per Queue
        main_finished = page.locator('button[title="Main: Finished"]')
        await main_finished.click()
        assert all('Finished' in s for s in await page.locator('.status-cell').all_inner_texts())
        main_unfinished = page.locator('button[title="Main: Unfinished"]')
        await main_unfinished.click()
        assert not any('Finished' in s for s in await page.locator('.status-cell').all_inner_texts())

        # Queue Status Bar Pills
        await page.locator('.queue-status-pills .queue-pill:has-text("All")').click()
        await page.locator('.queue-pill-clear').click()

        # Details Dialog
        await page.get_by_role('button', name='TrueNAS-SCALE-26.iso', exact=True).click()
        await expect(page.locator('#detailsDialog')).to_be_visible()
        await page.locator('#detailsDialog').get_by_role('button', name='Close', exact=True).click()

        # Other views navigation
        for view in ['queue', 'categories', 'scheduler', 'settings', 'browser']:
            await page.evaluate('(v)=>navigateTo(v)', view)
            await page.screenshot(path=str(ROOT / (view + '.png')))

        await page.get_by_role('button', name='Restart Firefox', exact=True).click()
        assert any(path == '/browser/restart' for _, path in requests)
        await page.evaluate("navigateTo('dashboard')")

        # Theme toggle
        await page.get_by_role('button', name='Switch theme').click()
        await page.screenshot(path=str(ROOT / 'light.png'))
        await page.get_by_role('button', name='Switch theme').click()

        # Mobile layout & horizontal scroll
        await page.set_viewport_size({'width': 390, 'height': 844})
        await page.screenshot(path=str(ROOT / 'phone.png'))
        dims = await page.locator('.home-sidebar').evaluate('(el)=>({width:el.clientWidth,scroll:el.scrollWidth,height:el.clientHeight})')
        assert dims['scroll'] > dims['width']
        assert dims['height'] < 90
        await page.locator('.home-sidebar').evaluate('(el)=>el.scrollLeft=150')
        await page.evaluate('render()')
        assert await page.locator('.home-sidebar').evaluate('(el)=>el.scrollLeft') == 150
        assert await page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')

        # iPad layout
        await page.set_viewport_size({'width': 820, 'height': 1180})
        await page.screenshot(path=str(ROOT / 'ipad.png'))
        assert await page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')

        assert not errors, errors
        print('PASS: desktop, light theme, phone/iPad, horizontal filters, search, selection, pause, add, details, every view, Firefox restart, pagination stability, default queue, cleared URL, queue finished/unfinished; no runtime errors.')
        await browser.close()

asyncio.run(main())
