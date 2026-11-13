import json, zipfile, sys, io
import zlib, struct
out = sys.argv[1]
def img(label, c):
    w, h = 60, 78
    raw = b''.join(b'\x00' + bytes(c) * w for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
def item(seq, title, date, creator, recipient, pages, **kw):
    d = dict(sequence=seq, title=title, date=date, dateSort=date, type='Letter', creator=creator, recipient=recipient, summary=kw.get('summary', ''),
             people=kw.get('people', []), places=kw.get('places', []), organizations=[], subjects=kw.get('subjects', []), handwritten=False, reviewed=kw.get('reviewed', False),
             draftedBy='nano', note='', pendingDrafts=kw.get('drafts', {}), source=dict(repository='', collection='', location=''), pages=[])
    for i, t in enumerate(pages, 1):
        d['pages'].append(dict(sequence=i, text=t, textSource='ocr', image=f'images/box-01/folder-001/item-{seq:03d}-p{i:02d}.jpg'))
    return d
data = dict(format='fonds-1', exported='2026-11-12',
  collection=dict(title='Sample Papers', creator='Jane Q. Doe', repository='NCCU Archives', identifier='MC.001', dates='1921-1934', extent='1 box', abstract='Letters of a Durham teacher.', scopeContent='', bioHist='', arrangement='', accessRestrict='Open.', useRestrict='', preferredCitation='', processingInfo='', pendingDrafts=dict(scopeContent='A draft scope note.')),
  series=[dict(number=1, title='Correspondence', scopeNote='Letters.', id=7)],
  folders=[dict(box=1, folder=1, label='Letters 1921', title='Letters, 1921', dates='1921', scopeNote='Early letters.', seriesId=7, note='', pendingDrafts={}, items=[
      item(1, 'Letter to mother', '1921-05-03', 'Jane Doe', 'Mary Doe', ['Dear Mother, the train to Durham was late.', 'Yours, Jane'], people=['Mary Doe'], places=['Durham'], summary='Jane describes arriving in Durham.', reviewed=True),
      item(2, 'Untitled note', '', '', '', ['Remember the sermon on Sunday.'], drafts=dict(title='Note about a sermon', docType='Note', people='Rev. Smith; Mary Doe'))])])
with zipfile.ZipFile(out, 'w') as z:
    z.writestr('data.json', json.dumps(data))
    z.writestr('README.txt', 'x')
    z.writestr('images/box-01/folder-001/item-001-p01.jpg', img('item1 p1', (240, 230, 200)))
    z.writestr('images/box-01/folder-001/item-001-p02.jpg', img('item1 p2', (230, 220, 190)))
    z.writestr('images/box-01/folder-001/item-002-p01.jpg', img('item2 p1', (210, 220, 230)))
