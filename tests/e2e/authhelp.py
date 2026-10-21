# Shared by the e2e tests: the program asks for a password on every start (12.77).
PASSWORD='Тест-пароль-2026'
async def login(pg,login='admin',password=PASSWORD):
    await pg.wait_for_selector('#vAuth input',timeout=20000)
    title=await pg.inner_text('#vAuth h1')
    if 'Первый запуск' in title:
        ins=pg.locator('#vAuth input')
        await ins.nth(0).fill(login);await ins.nth(2).fill(password);await ins.nth(3).fill(password)
    else:
        ins=pg.locator('#vAuth input')
        if await ins.count()>1: await ins.nth(0).fill(login)
        await ins.last.fill(password)
    await pg.click('#vAuth button[type=submit]')
    await pg.wait_for_selector('#vAuth',state='detached',timeout=20000)
