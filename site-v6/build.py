"""Build one offline HTML file; source assets remain separate in the ZIP."""
from pathlib import Path
import base64, json, mimetypes, re, argparse
root = Path(__file__).resolve().parent

def build(output):
    assets = {}
    for p in (root / 'assets').rglob('*'):
        if p.is_file() and p.suffix.lower() in {'.png','.jpg','.jpeg','.webp','.svg'}:
            mime=mimetypes.guess_type(p.name)[0] or 'application/octet-stream'
            assets[p.relative_to(root).as_posix()]='data:'+mime+';base64,'+base64.b64encode(p.read_bytes()).decode('ascii')
    html=(root/'index.html').read_text('utf-8')
    def css(m):
        content=(root/m.group(1)).read_text('utf-8')
        for key,value in assets.items():
            content=content.replace('url('+key+')','url('+value+')').replace('url("'+key+'")','url("'+value+'")')
        return '<style>'+content+'</style>'
    html=re.sub(r'<link rel="stylesheet" href="([^"]+)">',css,html)
    def script(m):
        s=(root/m.group(1)).read_text('utf-8').replace('</script','<\\/script')
        return '<script>'+s+'</script>'
    html=re.sub(r'<script defer src="([^"]+)"></script>',script,html)
    payload=json.dumps(assets,ensure_ascii=True,separators=(',',':'))
    html=html.replace('<head>','<head><script>window.CM_ASSETS='+payload+';</script>',1)
    html=html.replace('href="assets/img/icon.png"','href="'+assets['assets/img/icon.png']+'"')
    Path(output).write_text(html,'utf-8')
    print(str(output),len(html.encode('utf-8')),'bytes; assets',len(assets))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--output',default=str(root.parent/'CopiMine_Prototype_v5.html'))
    build(parser.parse_args().output)