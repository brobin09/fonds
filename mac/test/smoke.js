// Run: xvfb-run -a npx electron --no-sandbox test/smoke.js <sample.zip> <outdir>
const { app, BrowserWindow } = require('electron');
const path = require('path'); const fs = require('fs');
const [zip, out] = process.argv.slice(-2);
app.setPath('userData', fs.mkdtempSync('/tmp/fonds-ud-'));
process.argv.push(); 
require('../main.js');
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
app.whenReady().then(async () => {
  await wait(1500);
  const win = BrowserWindow.getAllWindows()[0];
  const js = (c) => win.webContents.executeJavaScript(c);
  const shot = async (n) => fs.writeFileSync(path.join(out, n + '.png'), (await win.webContents.capturePage()).toPNG());
  const errors = []; win.webContents.on('console-message', (_e, lvl, msg) => { if (lvl >= 2) errors.push(msg); });
  await shot('1-empty');
  win.webContents.send('open-package', zip); await wait(1500);
  await shot('2-gallery');
  await js(`document.querySelectorAll('.tile')[0].click()`); await wait(600); await shot('3-item');
  await js(`[...document.querySelectorAll('button')].find(b=>b.textContent==='List').click()`); await wait(400); await shot('4-list');
  await js(`document.querySelectorAll('.row')[1].click()`); await wait(500); await shot('5-draft');
  await js(`[...document.querySelectorAll('.draft button')][0].click()`); await wait(800);
  const title = await js(`document.querySelector('#detail input').value`);
  await js(`[...document.querySelectorAll('.tab')].find(b=>b.textContent==='Finding aid').click()`); await wait(1200); await shot('6-aid');
  await js(`[...document.querySelectorAll('.tab')].find(b=>b.textContent==='Details').click()`); await wait(500); await shot('7-details');
  console.log(JSON.stringify({ title, errors, hasLib: await js(`document.querySelectorAll('.lib').length`) }));
  app.exit(0);
});
