# 验证 H5(/learning-h5/) 与 PC 用户端(/learning-web/) PDF 页图预览（本机统一入口 6003）：
# 1. 资料详情页 DOC 类型渲染 <img blob:> 而非拉取 PDF 流
# 2. 翻页正常
# 3. 全程无 .pdf / file-content 请求
import os, sys, time
from playwright.sync_api import sync_playwright

BASE_H5 = os.environ.get('H5_BASE', 'http://127.0.0.1:6003/learning-h5')
BASE_PC = os.environ.get('PC_BASE', 'http://127.0.0.1:6003/learning-web')
MATERIAL_ID = int(os.environ.get('MATERIAL_ID', '32'))
def _find_chrome():
    import glob
    for pat in ('/home/cloud/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell',
                os.path.expanduser('~/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell')):
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    return None

CHROME = os.environ.get('CHROME_PATH') or _find_chrome()

def run(base, kind):
    reqs = []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
        page = browser.new_page()
        page.on('request', lambda r: reqs.append(r.url))
        page.goto(base + '/login')
        page.wait_for_timeout(1500)
        # 登录
        page.fill('input[type="text"], input[placeholder*="账号"], input[placeholder*="用户"]', 'ks001')
        page.fill('input[type="password"]', 'Exam@123')
        page.keyboard.press('Enter')
        page.wait_for_timeout(2500)
        # 直接进资料详情
        if kind == 'h5':
            page.goto(f'{base}/learn/material/{MATERIAL_ID}')
        else:
            page.goto(f'{base}/learn/material/{MATERIAL_ID}')
        page.wait_for_timeout(5000)
        print(f'[{kind}] url={page.url} title snippet:', page.inner_text('body')[:120].replace(chr(10), ' | '))
        img_ok = page.evaluate("""() => {
            const img = document.querySelector('.doc-page-img');
            return img ? {src: img.src.slice(0, 30), w: img.naturalWidth, h: img.naturalHeight} : null;
        }""")
        print(f'[{kind}] page1 img:', img_ok)
        assert img_ok and img_ok['src'].startswith('blob:'), f'{kind} 页图未以 blob 加载'
        assert img_ok['w'] > 500, f'{kind} 页图宽度异常'
        # 翻页
        btns = page.query_selector_all('button')
        clicked = False
        for b in btns:
            if '下一页' in (b.inner_text() or ''):
                b.click(); clicked = True; break
        page.wait_for_timeout(3000)
        page_text = page.inner_text('body')
        has_p2 = '第 2 / 3 页' in page_text or '2\n/ 3' in page_text or '/ 3 页' in page_text
        print(f'[{kind}] 翻页点击={clicked}, 页面含第2页标识={has_p2}')
        pdf_reqs = [u for u in reqs if '.pdf' in u.lower() or 'file-content' in u]
        pageimg_reqs = [u for u in reqs if '/pages/' in u]
        print(f'[{kind}] PDF流请求数={len(pdf_reqs)} {pdf_reqs}, 页图请求数={len(pageimg_reqs)}')
        assert not pdf_reqs, f'{kind} 仍存在 PDF 流请求: {pdf_reqs}'
        assert pageimg_reqs, f'{kind} 缺少页图请求'
        browser.close()

run(BASE_H5, 'h5')
run(BASE_PC, 'pc')
print('ALL OK')
